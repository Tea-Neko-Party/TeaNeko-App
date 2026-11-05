package org.zexnocs.teanekocore.command;

import org.junit.jupiter.api.Test;
import org.zexnocs.teanekocore.command.api.Command;
import org.zexnocs.teanekocore.command.api.CommandPermission;
import org.zexnocs.teanekocore.command.api.ManagementCommand;
import org.zexnocs.teanekocore.command.api.SubCommand;
import org.zexnocs.teanekocore.command.interfaces.ICommandPermissionManager;
import org.zexnocs.teanekocore.command.interfaces.ICommandScopeManager;
import org.zexnocs.teanekocore.framework.description.Description;
import org.zexnocs.teanekocore.framework.pair.IndependentPair;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekocore.plugin.PluginAccess;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IBeanScanner;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 验证目录复用真实注册与来源检查，管理说明不会成为普通成员的补全授权。
 *
 * <br>2026/11/04：验证显示名称独立于命令前缀和原权限。
 *
 * <br>2026/11/04：验证主指令默认规则和方法有效权限组的独立投影。
 * <br>2026/11/05：验证重复目录查询不调用底层 Bean 扫描器，类型说明仍正确投影。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.7.5-l
 * @version 4.8.2-l
 */
class CommandCatalogTest {
    /**
     * 2026/11/04：作用域、权限、原始数据类型与管理来源共同限定补全目录。
     * 2026/11/05：初始化后禁止目录读取重新发现 Bean，保留原类型说明及实时授权。
     */
    @Test
    void completionUsesOriginalRulesWhileGlobalCatalogKeepsDetailedDocumentation() {
        var bean = new Example();
        var beans = mock(IBeanScanner.class);
        when(beans.getBeansWithAnnotation(Command.class)).thenReturn(Map.of("example",
                new IndependentPair<>(Example.class.getAnnotation(Command.class), bean)));
        doReturn(Example.class).when(beans).getBeanClass(bean);
        var scanner = new CommandScanner(mock(ILogger.class), beans);
        scanner.init();
        var permissions = mock(ICommandPermissionManager.class);
        var scopes = mock(ICommandScopeManager.class);
        var plugins = mock(PluginAccess.class);
        when(plugins.enabled(any(), any())).thenReturn(true);
        when(scopes.inScope(any(), any(SubCommand.class), any())).thenReturn(true);
        when(permissions.hasPermission(any(), any(SubCommand.class), any())).thenReturn(true);
        var catalog = new CommandCatalog(scanner, permissions, scopes, plugins);
        clearInvocations(beans);
        var context = CommandData.builder().rawData(new Object()).permission(CommandPermission.MEMBER).build();
        var allowed = catalog.list(context, () -> {});
        assertEquals(List.of("echo"), allowed.stream().map(CommandCatalog.Entry::subcommand).toList());
        assertEquals("/example echo <text>", allowed.getFirst().usage());
        // 2026/11/04：中文显示名独立于调用前缀和权限过滤。
        assertEquals("测试目录", allowed.getFirst().displayName());
        // 2026/11/05：父级说明来自发现缓存，而非查询时反射或扫描。
        assertEquals("测试命令的完整说明。", allowed.getFirst().introduction());
        // 2026/11/04：主规则与方法有效规则分开投影，权限组差异不改变原授权服务。
        assertEquals("MEMBER", allowed.getFirst().defaultPermission());
        assertEquals("OWNER", allowed.getFirst().permission());
        assertEquals(List.of("example.default"), allowed.getFirst().defaultPackages());
        assertEquals(List.of("example.echo"), allowed.getFirst().permissionPackages());
        assertEquals(List.of("ICommandClient"), allowed.getFirst().defaultClients());
        assertEquals(allowed.getFirst().defaultClients(), allowed.getFirst().clients());
        assertEquals("需要回显的文字", allowed.getFirst().parameters().getFirst().description());
        assertEquals(3, catalog.list(null, () -> {}).size());
        when(permissions.hasPermission(any(), any(SubCommand.class), any())).thenReturn(false);
        assertTrue(catalog.list(context, () -> {}).isEmpty());
        when(permissions.hasPermission(any(), any(SubCommand.class), any())).thenReturn(true);
        when(scopes.inScope(any(), any(SubCommand.class), any())).thenReturn(false);
        assertTrue(catalog.list(context, () -> {}).isEmpty());
        when(plugins.enabled(any(), any())).thenReturn(false);
        assertTrue(catalog.list(null, () -> {}).isEmpty());
        verifyNoInteractions(beans);
        // 2026/11/05：失败重扫保留完整命令快照，后续查询不自动重试发现。
        doThrow(new IllegalStateException("测试命令发现失败。")).when(beans).getBeanClass(bean);
        assertThrows(IllegalStateException.class, scanner::reload);
        clearInvocations(beans);
        when(plugins.enabled(any(), any())).thenReturn(true);
        assertEquals(3, catalog.list(null, () -> {}).size());
        verifyNoInteractions(beans);
    }

    /**
     * 只有回显入口适合本测试的普通原始数据，其他入口保留各自类型与管理边界。
     *
     * 修改记录：<br>
     * 2026/11/04：声明测试显示名，验证前缀与展示分离。
     *
     * <br>2026/11/04：声明不同成员及权限组，覆盖真实扫描的继承语义。
     *
     * @author zExNocs
     * @date 2026/11/04
     * @since 4.7.5-l
     * @version 4.7.7-l
     */
    @Command(displayName = "测试目录", value = {"/example", "/例子"}, permission = CommandPermission.MEMBER, permissionPackage = "example.default")
    @Description("测试命令的完整说明。")
    public static class Example {
        /**
         * @param text 回显参数
         * @return 已完成的测试阶段
         */
        // 2026/11/04：方法声明不同成员及权限组，用真实扫描核对覆盖规则。
        @SubCommand(value = {"echo", "回显"}, permission = CommandPermission.OWNER, permissionPackage = "example.echo")
        @Description("回显文字；示例：/example echo 你好。")
        public CompletionStage<Void> echo(@Description("需要回显的文字") String text) {
            return CompletableFuture.completedStage(null);
        }

        /**
         * @param data 真实管理上下文
         * @return 已完成的管理阶段
         */
        @SubCommand("manage")
        @ManagementCommand
        public CompletionStage<Void> manage(CommandData<org.zexnocs.teanekocore.command.interfaces.IManagedCommandSource> data) {
            return CompletableFuture.completedStage(null);
        }

        /**
         * @param data 仅接受字符串平台原始数据
         * @return 已完成的测试阶段
         */
        @SubCommand("native")
        public CompletionStage<Void> nativeOnly(CommandData<String> data) {
            return CompletableFuture.completedStage(null);
        }
    }
}
