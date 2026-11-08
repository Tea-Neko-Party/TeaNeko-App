package org.zexnocs.teanekoplugin.contract.core;

import org.junit.jupiter.api.Test;
import org.zexnocs.teanekocore.command.CommandScanner;
import org.zexnocs.teanekocore.command.api.Command;
import org.zexnocs.teanekocore.command.api.DefaultCommand;
import org.zexnocs.teanekocore.event.AbstractEvent;
import org.zexnocs.teanekocore.event.core.EventHandler;
import org.zexnocs.teanekocore.event.core.EventHandlerScanner;
import org.zexnocs.teanekocore.event.core.EventListener;
import org.zexnocs.teanekocore.framework.pair.IndependentPair;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekocore.plugin.PluginAccess;
import org.zexnocs.teanekocore.plugin.PluginDescriptor;
import org.zexnocs.teanekocore.plugin.PluginOwned;
import org.zexnocs.teanekocore.plugin.PluginRegistry;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IBeanScanner;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IClassScanner;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 验证早期目录初始化与插件端口晚注入的顺序差异不遗漏归属。
 * <br>2026/11/07：晚注入复用已发布快照，不重新发现 Bean 或事件类型。
 * <br>2026/11/08：区分宿主方法已登记为无归属与目标、方法尚未登记的拒绝状态。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
class PluginOwnershipInitializationTest {
    /**
     * 已登记的宿主方法无需插件令牌，未登记目标和方法仍明确拒绝。
     * @throws NoSuchMethodException 当标准对象方法定位失败时
     */
    @Test
    void registeredUnownedMethodsRemainUsableAndUnknownMethodsStayRejected() throws NoSuchMethodException {
        var plugins = registry();
        var access = new PluginAccess(plugins);
        var target = new Object();
        var method = Object.class.getMethod("toString");
        var unknown = Object.class.getMethod("hashCode");
        assertThrows(IllegalStateException.class, () -> access.owner(target, method));
        assertNull(access.register(target, method));
        assertNull(access.owner(target, method));
        assertNull(access.capture(target, method));
        try (var call = access.open(target, method)) {
            assertNull(call);
        }
        assertTrue(access.enabled(target, method));
        plugins.setEnabled("test.ownership", false);
        assertTrue(access.enabled(target, method));
        assertThrows(IllegalStateException.class, () -> access.owner(new Object(), method));
        var failure = assertThrows(IllegalStateException.class, () -> access.owner(target, unknown));
        assertTrue(failure.getMessage().contains("java.lang.Object#hashCode"));
    }

    /**
     * 命令扫描先完成时，注入归属端口仍登记原目标，停用马上生效。
     * @throws Exception 当夹具入口定位失败时
     */
    @Test
    void lateCommandInjectionRegistersPublishedTargetsWithoutDiscovery() throws Exception {
        var target = new OwnedCommand();
        var beans = mock(IBeanScanner.class);
        when(beans.getBeansWithAnnotation(Command.class)).thenReturn(Map.of("owned",
                new IndependentPair<>(OwnedCommand.class.getAnnotation(Command.class), target)));
        doReturn(OwnedCommand.class).when(beans).getBeanClass(target);
        var scanner = new CommandScanner(mock(ILogger.class), beans);
        scanner.init();
        clearInvocations(beans);
        var registry = registry();
        var access = new PluginAccess(registry);
        var method = OwnedCommand.class.getMethod("run");
        assertThrows(IllegalStateException.class, () -> access.owner(target, method));
        scanner.installPlugins(access);
        assertEquals("test.ownership", access.owner(target, method));
        registry.setEnabled("test.ownership", false);
        assertFalse(access.enabled(target, method));
        verifyNoInteractions(beans);
    }

    /**
     * 事件扫描先完成时，注入归属端口只登记已有监听列表。
     * @throws Exception 当夹具方法定位失败时
     */
    @Test
    void lateEventInjectionRegistersPublishedHandlersWithoutDiscovery() throws Exception {
        var target = new OwnedListener();
        var beans = mock(IBeanScanner.class);
        var classes = mock(IClassScanner.class);
        when(beans.getBeansWithAnnotation(EventListener.class)).thenReturn(Map.of("owned",
                new IndependentPair<>(OwnedListener.class.getAnnotation(EventListener.class), target)));
        var scanner = new EventHandlerScanner(beans, mock(ILogger.class), classes);
        scanner.init();
        clearInvocations(beans, classes);
        var access = new PluginAccess(registry());
        var method = OwnedListener.class.getMethod("receive", SampleEvent.class);
        assertThrows(IllegalStateException.class, () -> access.owner(target, method));
        scanner.installPlugins(access);
        assertEquals("test.ownership", access.owner(target, method));
        verifyNoInteractions(beans, classes);
    }

    /** @return 独立启用且不创建业务资源的插件目录 */
    private PluginRegistry registry() {
        return new PluginRegistry(List.of(() -> new PluginDescriptor("test.ownership", "1", 1, Set.of())));
    }

    /**
     * 有明确插件归属的真实异步命令。
     * @author zExNocs
     * @date 2026/11/07
     * @since 4.9.0-l
     * @version 4.9.0-l
     */
    @Command("/ownership")
    @PluginOwned("test.ownership")
    public static class OwnedCommand {
        /** @return 已完成的无副作用阶段 */
        @DefaultCommand
        public CompletionStage<Void> run() {
            return CompletableFuture.completedStage(null);
        }
    }

    /**
     * 只需静态声明的事件数据。
     * @author zExNocs
     * @date 2026/11/07
     * @since 4.9.0-l
     * @version 4.9.0-l
     */
    public static class SampleEvent extends AbstractEvent<String> {
        /** 创建不含敏感内容的事件。 */
        public SampleEvent() {
            super("归属测试");
        }
    }

    /**
     * 有明确插件归属的真实监听器。
     * @author zExNocs
     * @date 2026/11/07
     * @since 4.9.0-l
     * @version 4.9.0-l
     */
    @EventListener
    @PluginOwned("test.ownership")
    public static class OwnedListener {
        /** @param event 本次无副作用事件 */
        @EventHandler
        public void receive(SampleEvent event) {
        }
    }
}
