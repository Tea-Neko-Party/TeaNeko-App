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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

/**
 * 验证目录复用真实注册与来源检查，管理说明不会成为普通成员的补全授权。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.7.5-l
 * @version 4.7.5-l
 */
class CommandCatalogTest {
    /** 2026/11/04：作用域、权限、原始数据类型与管理来源共同限定补全目录。 */
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
        var catalog = new CommandCatalog(scanner, beans, permissions, scopes, plugins);
        var context = CommandData.builder().rawData(new Object()).permission(CommandPermission.MEMBER).build();
        var allowed = catalog.list(context, () -> {});
        assertEquals(List.of("echo"), allowed.stream().map(CommandCatalog.Entry::subcommand).toList());
        assertEquals("/example echo <text>", allowed.getFirst().usage());
        assertEquals("需要回显的文字", allowed.getFirst().parameters().getFirst().description());
        assertEquals(3, catalog.list(null, () -> {}).size());
        when(permissions.hasPermission(any(), any(SubCommand.class), any())).thenReturn(false);
        assertTrue(catalog.list(context, () -> {}).isEmpty());
        when(permissions.hasPermission(any(), any(SubCommand.class), any())).thenReturn(true);
        when(scopes.inScope(any(), any(SubCommand.class), any())).thenReturn(false);
        assertTrue(catalog.list(context, () -> {}).isEmpty());
        when(plugins.enabled(any(), any())).thenReturn(false);
        assertTrue(catalog.list(null, () -> {}).isEmpty());
    }

    /**
     * 只有回显入口适合本测试的普通原始数据，其他入口保留各自类型与管理边界。
     *
     * @author zExNocs
     * @date 2026/11/04
     * @since 4.7.5-l
     * @version 4.7.5-l
     */
    @Command(value = {"/example", "/例子"}, permission = CommandPermission.MEMBER)
    @Description("测试命令的完整说明。")
    public static class Example {
        /**
         * @param text 回显参数
         * @return 已完成的测试阶段
         */
        @SubCommand({"echo", "回显"})
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
