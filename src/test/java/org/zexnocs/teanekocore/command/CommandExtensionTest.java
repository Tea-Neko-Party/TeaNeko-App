package org.zexnocs.teanekocore.command;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.zexnocs.teanekocore.command.api.Command;
import org.zexnocs.teanekocore.command.api.CommandExtension;
import org.zexnocs.teanekocore.command.api.DefaultCommand;
import org.zexnocs.teanekocore.command.api.SubCommand;
import org.zexnocs.teanekocore.command.event.CommandExecuteEvent;
import org.zexnocs.teanekocore.command.interfaces.ICommandExecutor;
import org.zexnocs.teanekocore.framework.pair.HashPair;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IBeanScanner;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 验证插件子命令保留真实处理实例，冲突重扫不会破坏已发布目录。
 * <br>2026/11/07：覆盖扩展路由及完整快照发布。
 * <br>2026/11/07：真实 Spring 组件扫描覆盖共享前缀的业务贡献，防止 Bean 命名冲突。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
class CommandExtensionTest {
    /** 真实生产组件扫描允许多个插件共享命令前缀；只排除测试夹具，不覆盖 Bean 或手动指定名称。 */
    @Test
    void springScanningKeepsSharedPrefixSeparateFromBeanNames() {
        var beans = new DefaultListableBeanFactory();
        beans.setAllowBeanDefinitionOverriding(false);
        var scanner = new ClassPathBeanDefinitionScanner(beans);
        // 2026/11/07：扫描全部生产组件，避免手工登记 Bean 的夹具跳过真实命名冲突。
        String production = CommandExtension.class.getProtectionDomain().getCodeSource().getLocation().toExternalForm();
        scanner.addExcludeFilter((metadata, factory) ->
                !metadata.getResource().getURL().toExternalForm().startsWith(production));
        scanner.scan("org.zexnocs");
        assertTrue(beans.containsBeanDefinition("automationCommands"));
        assertTrue(beans.containsBeanDefinition("cognitionCommands"));
        assertFalse(beans.containsBeanDefinition("/agent"));
    }

    /** 子命令执行器收到贡献实例，根别名共享同一完整目录。 */
    @Test void dispatchesToActualContribution() {
        var root = new Root();
        var extension = new Extension();
        var beans = beans(root, extension);
        var scanner = new CommandScanner(mock(ILogger.class), beans);
        scanner.init();
        var map = scanner.getPrefixCommand("/root");
        var method = map.getSubCommand("business").getSecond();
        assertSame(extension, map.target(method));
        assertSame(map, scanner.getPrefixCommand("/alias"));
        var executor = mock(ICommandExecutor.class);
        var data = CommandData.builder().rawData(new Object()).build();
        var args = new Object[0];
        new CommandExecuteEvent(executor, data, map, "business", method, args)._afterNotify();
        verify(executor).execute(extension, method, args, map.getCommandAnnotation().taskNamespace());
    }

    /** 重复业务子命令拒绝整个重扫，先前已发布的合法目录仍可读取。 */
    @Test void rejectsConflictsWithoutPublishingPartialDirectory() {
        var root = new Root();
        var extension = new Extension();
        var beans = beans(root, extension);
        var scanner = new CommandScanner(mock(ILogger.class), beans);
        scanner.init();
        var previous = scanner.getPrefixCommand("/root");
        var duplicate = new Extension();
        doReturn(Map.of("first", HashPair.of(Extension.class.getAnnotation(CommandExtension.class), extension),
                "second", HashPair.of(Extension.class.getAnnotation(CommandExtension.class), duplicate)))
                .when(beans).getBeansWithAnnotation(CommandExtension.class);
        doReturn(Extension.class).when(beans).getBeanClass(duplicate);
        assertThrows(IllegalArgumentException.class, scanner::reload);
        assertSame(previous, scanner.getPrefixCommand("/root"));
    }

    /**
     * @param root 原根入口
     * @param extension 独立插件实例
     * @return 只在扫描期返回明确贡献的发现夹具
     */
    private static IBeanScanner beans(Root root, Extension extension) {
        var beans = mock(IBeanScanner.class);
        doReturn(Map.of("root", HashPair.of(Root.class.getAnnotation(Command.class), root)))
                .when(beans).getBeansWithAnnotation(Command.class);
        doReturn(Map.of("extension", HashPair.of(Extension.class.getAnnotation(CommandExtension.class), extension)))
                .when(beans).getBeansWithAnnotation(CommandExtension.class);
        doReturn(Root.class).when(beans).getBeanClass(root);
        doReturn(Extension.class).when(beans).getBeanClass(extension);
        return beans;
    }

    /**
     * 保留框架默认入口的主命令夹具。
     * @author zExNocs
     * @date 2026/11/07
     * @since 4.9.0-l
     * @version 4.9.0-l
     */
    @Command({"/root", "/alias"})
    public static final class Root {
        /** @return 根入口实际完成阶段。 */
        @DefaultCommand public CompletionStage<Void> help() {
            return CompletableFuture.completedStage(null);
        }
    }

    /**
     * 提供独立业务处理的插件夹具。
     * @author zExNocs
     * @date 2026/11/07
     * @since 4.9.0-l
     * @version 4.9.0-l
     */
    @CommandExtension(prefix = "/root")
    public static final class Extension {
        /** @return 业务入口实际完成阶段。 */
        @SubCommand("business") public CompletionStage<Void> execute() {
            return CompletableFuture.completedStage(null);
        }
    }
}
