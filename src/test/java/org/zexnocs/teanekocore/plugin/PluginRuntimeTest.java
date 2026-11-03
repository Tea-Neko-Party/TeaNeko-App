package org.zexnocs.teanekocore.plugin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskService;
import org.zexnocs.teanekocore.command.CommandExecutor;
import org.zexnocs.teanekocore.framework.function.MethodCallable;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekocore.plugin.management.ManagementAuthority;
import org.zexnocs.teanekocore.plugin.management.ManagementContext;
import org.zexnocs.teanekocore.plugin.management.ManagementGateway;
import org.zexnocs.teanekocore.plugin.management.PluginCatalogResource;
import org.zexnocs.teanekocore.plugin.operation.OperationPipeline;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 覆盖目录冲突、依赖换代与异步环绕的真实完成边界。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.6.0-l
 * @version 4.6.0-l
 */
class PluginRuntimeTest {
    /** 验证完整目录拒绝重名和依赖环，不产生部分可用目录。 */
    @Test void rejectsConflictingCatalogs() {
        assertThrows(IllegalArgumentException.class, () -> new PluginRegistry(List.of(module("a", Set.of()), module("a", Set.of()))));
        assertThrows(IllegalArgumentException.class, () -> new PluginRegistry(List.of(module("a", Set.of("b")), module("b", Set.of("a")))));
    }
    /** 验证真实 Spring 装配加载启动禁用列表，基础模块仍受保护。 */
    @Test void appliesStartupDisabledList() {
        try (var context = new org.springframework.context.annotation.AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("插件测试",
                    Map.of("tea-neko.plugins.disabled", "a")));
            context.registerBean("pluginA", PluginModule.class, () -> module("a", Set.of()));
            context.register(PluginConfiguration.class, PluginRegistry.class, PluginAccess.class); context.refresh();
            var registry = context.getBean(PluginRegistry.class); assertFalse(registry.isEnabled("a"));
            assertThrows(IllegalArgumentException.class, () -> registry.setEnabled("teaneko.core", false));
        }
    }
    /** 验证无关启停不干扰当前调用，而依赖禁用再启用仍拒绝旧结果。 */
    @Test void rejectsLateResultsAfterDependencyChanges() {
        var plugins = new PluginRegistry(List.of(module("a", Set.of("b")), module("b", Set.of()), module("c", Set.of())));
        var token = plugins.capture("a"); plugins.setEnabled("c", false); plugins.check(token);
        var pending = new CompletableFuture<String>(); var result = plugins.invoke("a", () -> pending).toCompletableFuture();
        plugins.setEnabled("b", false); assertFalse(plugins.isEnabled("a")); plugins.setEnabled("b", true);
        pending.complete("迟到结果"); assertThrows(CompletionException.class, result::join);
    }
    /** 验证异步环绕完整等待下游与后置结果，顺序固定为嵌套调用栈语义。 */
    @Test void composesFinalResultsInStableOrder() {
        var plugins = new PluginRegistry(List.of(module("a", Set.of()))); var sequence = new ArrayList<String>();
        var pending = new CompletableFuture<Integer>();
        var binding = new OperationPipeline.Binding<Integer, Integer>("a", "around", 10, (input, next) -> {
            sequence.add("前置"); return next.execute(input + 1).thenApply(value -> { sequence.add("后置"); return value + 1; });
        });
        try (var control = ExecutionControl.create()) {
            var result = new OperationPipeline<>(plugins, List.of(binding), false).execute(1, control, input -> {
                sequence.add("业务" + input); return pending;
            }).toCompletableFuture();
            assertFalse(result.isDone()); pending.complete(5); assertEquals(6, result.join());
            assertEquals(List.of("前置", "业务2", "后置"), sequence);
        }
    }
    /** 验证错误插件忽略下游阶段也不能提前成功，吞掉重复调用的失败仍被拒绝。 */
    @Test void preventsPrematureCompletionAndRepeatedNext() {
        var pending = new CompletableFuture<Integer>();
        var result = OperationPipeline.around(1, false, (input, next) -> { next.execute(input); return CompletableFuture.completedStage(9); }, input -> pending).toCompletableFuture();
        assertFalse(result.isDone()); pending.complete(2); assertEquals(9, result.join());
        var duplicate = OperationPipeline.around(1, false, (input, next) -> {
            next.execute(input); next.execute(input); return CompletableFuture.completedStage(9);
        }, CompletableFuture::completedStage).toCompletableFuture();
        assertThrows(CompletionException.class, duplicate::join);
    }
    /** 验证管理接口默认拒绝，安装可信授权后只允许授权范围并执行明确类型请求。 */
    @Test void requiresExplicitManagementAuthority() {
        var plugins = new PluginRegistry(List.of(module("teaneko.core", Set.of()))); var beans = new DefaultListableBeanFactory();
        var gateway = new ManagementGateway(plugins, List.of(new PluginCatalogResource(plugins)), beans.getBeanProvider(ManagementAuthority.class));
        try (var control = ExecutionControl.create()) {
            var context = new ManagementContext("管理员", "全局", UUID.randomUUID(), control);
            assertThrows(CompletionException.class, () -> gateway.execute("teaneko.core:plugins", context, new PluginCatalogResource.Query()).toCompletableFuture().join());
            beans.registerSingleton("authority", (ManagementAuthority) (actor, resource) -> actor.scopeRef().equals("全局")
                    ? CompletableFuture.completedStage(null) : CompletableFuture.failedStage(new SecurityException("禁止跨范围查看。")));
            assertEquals(1, gateway.describe(context).toCompletableFuture().join().size());
            var forbidden = new ManagementContext("管理员", "其他群", UUID.randomUUID(), control);
            assertThrows(CompletionException.class, () -> gateway.execute("teaneko.core:plugins", forbidden, new PluginCatalogResource.Query()).toCompletableFuture().join());
            assertEquals(plugins.list(), gateway.execute("teaneko.core:plugins", context, new PluginCatalogResource.Query()).toCompletableFuture().join());
        }
    }
    /**
     * 验证命令在原任务链启动，插件换代后完整业务阶段拒绝迟到成功。
     * @throws NoSuchMethodException 当测试命令缺少公开入口时
     */
    @Test @SuppressWarnings({"unchecked"}) void checksOwnershipUntilCommandCompletion() throws NoSuchMethodException {
        var plugins = new PluginRegistry(List.of(module("a", Set.of()))); var tasks = mock(ITaskService.class); var logger = mock(ILogger.class);
        when(tasks.subscribeWithFuture(anyString(), anyString(), any(MethodCallable.class), any(Class.class))).thenAnswer(call -> {
            try { return TaskFuture.fromStage(logger, "插件命令", CompletableFuture.completedStage(call.<MethodCallable<?>>getArgument(2).call())); }
            catch (Exception failure) { return TaskFuture.fromStage(logger, "插件命令", CompletableFuture.failedStage(failure)); }
        });
        var executor = new CommandExecutor(tasks); executor.installPluginAccess(new PluginAccess(plugins));
        var command = new TestCommand(); var result = executor.execute(command, TestCommand.class.getMethod("run"), new Object[0], "default").toCompletableFuture();
        assertFalse(result.isDone()); plugins.setEnabled("a", false); command.pending.complete("迟到成功");
        assertThrows(CompletionException.class, result::join);
    }

    /**
     * 明确声明插件归属的异步命令。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @author zExNocs
     * @date 2026/11/02
     * @since 4.6.0-l
     * @version 4.6.0-l
     */
    @PluginOwned("a") public static final class TestCommand {
        final CompletableFuture<String> pending = new CompletableFuture<>();
        /** @return 尚未结束的真实业务阶段 */
        public CompletionStage<String> run() { return pending; }
    }
    /**
     * 创建没有资源副作用的测试模块。
     * @param id 插件标识
     * @param dependencies 必需依赖
     * @return 目录入口
     */
    private static PluginModule module(String id, Set<String> dependencies) { return () -> new PluginDescriptor(id, "1", 1, dependencies); }
}
