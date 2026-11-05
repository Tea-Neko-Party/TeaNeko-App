package org.zexnocs.teanekoagent.component.memory.namespace;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.util.Pair;
import org.zexnocs.teanekoapp.web.WebSecurityFileConfig;
import org.zexnocs.teanekoapp.web.client.ChatTargets;
import org.zexnocs.teanekoapp.web.client.ClientManagementDirectory;
import org.zexnocs.teanekoapp.web.client.IClientManagementAdapter;
import org.zexnocs.teanekoapp.web.client.TeaNekoClientManagement;
import org.zexnocs.teanekoapp.web.console.*;
import org.zexnocs.teanekoclient.onebot.core.OnebotManagementAdapter;
import org.zexnocs.teanekoclient.onebot.core.OnebotTeaNekoClient;
import org.zexnocs.teanekocore.command.*;
import org.zexnocs.teanekocore.command.api.*;
import org.zexnocs.teanekocore.command.interfaces.ICommandDispatcher;
import org.zexnocs.teanekocore.file_config.interfaces.IFileConfigService;
import org.zexnocs.teanekocore.framework.pair.IndependentPair;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekocore.plugin.PluginAccess;
import org.zexnocs.teanekocore.plugin.PluginRegistry;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IBeanScanner;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 复用原事务夹具验证指令台认领、实际发送回执及撤权，不连接真实平台或数据库。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * <br>2026/11/05：用已有有界等待工具替代循环休眠，限制单次终态查询等待。
 * <br>2026/11/05：指令台使用原命令缓存，移除运行期 Bean 类型发现依赖。
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.8.2-l
 */
@Timeout(15)
class ClientConsoleIntegrationTest {
    private static final Runnable ALLOWED = () -> {};

    /**
     * @param db 原事务夹具
     * @return 指令台独立域；生产装配继续使用 App 的 GeneralEasyData 实体
     */
    private ConsoleOperationStore store(NamespaceDatabaseFixture db) {
        return new ConsoleOperationStore(db.data.inNamespace("console-test").get("operations"), db.mapper);
    }

    /**
     * 2026/11/05：用已有有界等待工具替代循环休眠，限制单次终态查询等待。
     * @param service 原编排服务
     * @param id 原操作编号
     * @return 实际持久终态；未完成时只读查询，不重放
     * @throws org.awaitility.core.ConditionTimeoutException 当真实异步处理没有在期限内完成时
     */
    private ClientConsole.Receipt terminal(ClientConsoleService service, UUID id) {
        // 2026/11/05：使用既有有界等待工具查询真实终态，避免循环休眠与无限 join。
        return org.awaitility.Awaitility.await("指令台终态")
                .atMost(java.time.Duration.ofSeconds(5))
                .until(() -> service.operation("onebot", "account", "target", id, ALLOWED)
                                .toCompletableFuture().get(1, TimeUnit.SECONDS),
                        receipt -> !Set.of("ACCEPTED", "RUNNING").contains(receipt.status()));
    }

    /**
     * 同编号只有一次发送，受理与实际平台确认分开，重建服务后原样查询而不重放。
     * @throws Exception 当夹具装配或异步处理失败时
     */
    @Test
    void persistsOneExecutionAndWaitsForTheActualMessageId() throws Exception {
        try (var db = new NamespaceDatabaseFixture(); var root = new Root(store(db))) {
            var catalog = root.service.catalog("onebot", "account", "target", ALLOWED).toCompletableFuture().join();
            assertEquals(2, catalog.commands().size());
            var request = new ClientConsole.Request(UUID.randomUUID(), "/console say 测试正文", "connection-1", catalog.observedAt());
            assertEquals("ACCEPTED", root.service.submit("onebot", "account", "target", request, ALLOWED)
                    .toCompletableFuture().join().status());
            var data = root.dispatched.get(5, TimeUnit.SECONDS);
            assertEquals(CommandOrigin.MANAGEMENT, data.getOrigin());
            assertEquals(CommandPermission.MEMBER, data.getPermission());
            assertEquals("web.local-admin", data.getSenderId());
            assertEquals("ACCEPTED", root.service.submit("onebot", "account", "target", request, ALLOWED)
                    .toCompletableFuture().join().status());
            assertThrows(IllegalArgumentException.class, () -> root.service.submit("onebot", "account", "target",
                    new ClientConsole.Request(request.operationId(), "/console say 其他正文", "connection-1", request.observedAt()), ALLOWED));
            root.invocation.complete(null);
            root.sent.get(5, TimeUnit.SECONDS);
            assertEquals("RUNNING", root.service.operation("onebot", "account", "target", request.operationId(), ALLOWED)
                    .toCompletableFuture().join().status());
            root.ack.complete("actual-message-7");
            var source = assertInstanceOf(ConsoleInvocation.class, data.getRawData());
            assertThrows(SecurityException.class, () -> source.print("业务结束后的迟到输出"));
            var result = terminal(root.service, request.operationId());
            assertEquals("COMPLETED", result.status());
            assertEquals(List.of("actual-message-7"), result.confirmedMessages());
            try (var restarted = new Root(store(db))) {
                assertEquals(result, restarted.service.submit("onebot", "account", "target", request, ALLOWED)
                        .toCompletableFuture().join());
                assertFalse(restarted.dispatched.isDone());
            }
            assertEquals(1, root.sends.get());
        }
    }

    /**
     * 排队后撤权不能发送；失主回执、外部主体和过期请求也不能变成新执行。
     * @throws Exception 当夹具装配或异步处理失败时
     */
    @Test
    void revocationAndLostOwnersDoNotReplaySideEffects() throws Exception {
        try (var db = new NamespaceDatabaseFixture(); var root = new Root(store(db))) {
            var valid = new AtomicBoolean(true);
            Runnable guard = () -> {
                if (!valid.get()) {
                    throw new SecurityException("测试模拟会话撤销。");
                }
            };
            var request = new ClientConsole.Request(UUID.randomUUID(), "/console say 不应发送", "connection-1", Instant.now());
            root.service.submit("onebot", "account", "target", request, guard).toCompletableFuture().join();
            root.dispatched.get(5, TimeUnit.SECONDS);
            valid.set(false);
            root.invocation.complete(null);
            assertEquals("INDETERMINATE", terminal(root.service, request.operationId()).status());
            assertEquals(0, root.sends.get());

            var store = store(db);
            var orphan = new ClientConsole.Request(UUID.randomUUID(), "/console", "connection-1", Instant.now());
            var oldInstance = UUID.randomUUID();
            assertTrue(store.claim("actor", "target", "digest", orphan, oldInstance, ALLOWED).toCompletableFuture().join().acquired());
            assertEquals("INDETERMINATE", store.operation("actor", "target", orphan.operationId(), UUID.randomUUID(),
                    () -> false, ALLOWED).toCompletableFuture().join().status());
            assertFalse(store.claim("actor", "target", "digest", orphan, UUID.randomUUID(), ALLOWED)
                    .toCompletableFuture().join().acquired());
            assertThrows(CompletionException.class, () -> store.operation("other-actor", "target", orphan.operationId(),
                    oldInstance, () -> true, ALLOWED).toCompletableFuture().join());
            var expired = new ClientConsole.Request(UUID.randomUUID(), "/console", "connection-1", Instant.now().minusSeconds(400));
            assertThrows(CompletionException.class, () -> store.claim("actor", "target", "digest", expired,
                    oldInstance, ALLOWED).toCompletableFuture().join());
        }
    }

    /**
     * 原参数绑定与真实指令方法，平台只以受控消息确认替换，不增加另一套事务设施。
     *
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     * <br>2026/11/05：指令台只依赖已注册命令数据，移除底层 Bean 类型发现依赖。
     *
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.8.2-l
     */
    private static final class Root implements AutoCloseable {
        final CompletableFuture<CommandData<?>> dispatched = new CompletableFuture<>();
        final CompletableFuture<Void> invocation = new CompletableFuture<>();
        final CompletableFuture<Void> sent = new CompletableFuture<>();
        final CompletableFuture<String> ack = new CompletableFuture<>();
        final AtomicInteger sends = new AtomicInteger();
        final ClientConsoleService service;

        /**
         * 2026/11/05：使用已登记方法查询指令台，不注入底层 Bean 扫描器。
         * @param operations 原实际事务存储
         * @throws Exception 当命令方法或目录装配失败时
         */
        Root(ConsoleOperationStore operations) throws Exception {
            var adapter = mock(IClientManagementAdapter.class);
            var target = new ChatTargets.Resolved("onebot", "account", "target", "onebot-group@123", "测试群", "GROUP", "123", false);
            when(adapter.resolve("account", "target")).thenReturn(CompletableFuture.completedStage(target));
            when(adapter.consoleState(target)).thenReturn(CompletableFuture.completedStage(
                    new ClientConsole.State("READY", "connection-1", "测试连接就绪")));
            when(adapter.consoleLease(eq(target), any())).thenAnswer(call -> {
                Runnable guard = call.getArgument(1);
                return CompletableFuture.completedStage(new ClientConsole.Lease(OnebotTeaNekoClient.class, target,
                        "connection-1", guard, text -> {
                            guard.run();
                            sends.incrementAndGet();
                            sent.complete(null);
                            return ack;
                        }));
            });
            var beans = mock(IBeanScanner.class);
            when(beans.getBeansWithAnnotationAndInterface(TeaNekoClientManagement.class, IClientManagementAdapter.class))
                    .thenReturn(Map.of("onebot", new IndependentPair<>(OnebotManagementAdapter.class
                            .getAnnotation(TeaNekoClientManagement.class), adapter)));
            doReturn(OnebotManagementAdapter.class).when(beans).getBeanClass(adapter);
            var command = new ConsoleCommands();
            var status = ConsoleCommands.class.getMethod("status", CommandData.class);
            var say = ConsoleCommands.class.getMethod("say", CommandData.class, List.class);
            var map = CommandMapData.builder().command(command).commandAnnotation(ConsoleCommands.class.getAnnotation(Command.class))
                    .defaultCommandMethod(status).defaultCommandAnnotation(status.getAnnotation(DefaultCommand.class))
                    .subCommandMap(Map.of("say", Pair.of(say.getAnnotation(SubCommand.class), say))).build();
            var commands = mock(CommandScanner.class);
            when(commands.getPrefixCommand("/console")).thenReturn(map);
            var dispatcher = mock(ICommandDispatcher.class);
            var arguments = new CommandArgumentProcessor(mock(ILogger.class));
            when(dispatcher.dispatch(any(), any(), any())).thenAnswer(call -> {
                CommandData<?> data = call.getArgument(0);
                dispatched.complete(data);
                return invocation.thenCompose(ignored -> {
                    ManagementCommandPolicy.check(data, say);
                    try {
                        var result = say.invoke(command, arguments.process(say, Arrays.copyOfRange(data.getArgs(), 1,
                                data.getArgs().length), data));
                        if (!(result instanceof CompletionStage<?> stage)) {
                            throw new IllegalStateException("测试命令没有返回异步阶段。");
                        }
                        return stage.thenApply(value -> CommandResult.of(CommandResult.Status.COMPLETED));
                    } catch (ReflectiveOperationException
                            | org.zexnocs.teanekocore.command.exception.CommandDataTypeMismatchException failure) {
                        throw new CompletionException("测试调用命令失败。", failure);
                    }
                });
            });
            var files = mock(IFileConfigService.class);
            var security = new WebSecurityFileConfig();
            security.setConsolePrefixes(List.of("/console"));
            when(files.requireCurrent(WebSecurityFileConfig.class)).thenReturn(security);
            ObjectProvider<PluginRegistry> provider = new org.springframework.beans.factory.support.StaticListableBeanFactory()
                    .getBeanProvider(PluginRegistry.class);
            var clients = new ClientManagementDirectory(beans, provider);
            // 2026/11/05：底层扫描器仅供管理适配器初始化，指令台查询读取命令缓存。
            service = new ClientConsoleService(clients, operations, dispatcher, commands, files,
                    new PluginAccess(mock(PluginRegistry.class)), Runnable::run);
        }

        /** 只释放本测试拥有的管理能力。 */
        @Override
        public void close() {
            service.close();
        }
    }
}
