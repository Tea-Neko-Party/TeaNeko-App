package org.zexnocs.teanekoplugin.contract.web;

// 2026/11/07：插件归属夹具遵循与生产一致的业务包边界。

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.zexnocs.teanekoapp.client.TeaNekoClientScanner;
import org.zexnocs.teanekoapp.web.client.ClientManagementDirectory;
import org.zexnocs.teanekoapp.web.client.IClientManagementAdapter;
import org.zexnocs.teanekoapp.web.client.TeaNekoClientManagement;
import org.zexnocs.teanekocore.framework.pair.IndependentPair;
import org.zexnocs.teanekocore.plugin.PluginDescriptor;
import org.zexnocs.teanekocore.plugin.PluginOwned;
import org.zexnocs.teanekocore.plugin.PluginRegistry;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IBeanScanner;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 静态客户端声明只在重扫发现，插件与目录换代立即撤销旧调用。
 * <br>2026/11/06：验证失败重扫保持完整目录，不连接平台。
 * <br>2026/11/07：验证实际客户端归属继承、冲突与停用后的异步撤销。
 *
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.4-l
 * @version 4.9.0-l
 */
class ClientManagementDirectoryTest {
    /** 管理适配器未重复注解时继承实际客户端归属，停用再启用不能恢复旧调用。 */
    @Test
    void inheritsActualClientOwnerAndRejectsConflicts() {
        var beans = mock(IBeanScanner.class);
        var adapter = new UnownedAdapter();
        when(beans.getBeansWithAnnotationAndInterface(TeaNekoClientManagement.class, IClientManagementAdapter.class))
                .thenReturn(Map.of("fixture", new IndependentPair<>(
                        UnownedAdapter.class.getAnnotation(TeaNekoClientManagement.class), adapter)));
        doReturn(UnownedAdapter.class).when(beans).getBeanClass(adapter);
        var plugins = new PluginRegistry(List.of(() -> new PluginDescriptor("test.client", "1", 1, Set.of()),
                () -> new PluginDescriptor("other.client", "1", 1, Set.of())));
        var factory = new DefaultListableBeanFactory();
        factory.registerSingleton("plugins", plugins);
        var directory = new ClientManagementDirectory(beans, factory.getBeanProvider(PluginRegistry.class));
        var clients = mock(TeaNekoClientScanner.class);
        when(clients.pluginOwner("fixture")).thenReturn("test.client");
        directory.installClients(clients);
        clearInvocations(beans);
        assertEquals("test.client", directory.list().getFirst().pluginId());
        var guard = directory.guard("fixture", () -> {});
        var result = new CompletableFuture<String>();
        var pending = directory.invoke("fixture", ignored -> result);
        plugins.setEnabled("test.client", false);
        assertTrue(directory.list().isEmpty());
        assertThrows(IllegalStateException.class, guard::run);
        plugins.setEnabled("test.client", true);
        result.complete("迟到结果");
        assertThrows(CompletionException.class, () -> pending.toCompletableFuture().join());
        verifyNoInteractions(beans);
        verify(clients, never()).init();
        verify(clients, never()).reload();

        var declared = new TestAdapter();
        when(beans.getBeansWithAnnotationAndInterface(TeaNekoClientManagement.class, IClientManagementAdapter.class))
                .thenReturn(Map.of("fixture", new IndependentPair<>(
                        TestAdapter.class.getAnnotation(TeaNekoClientManagement.class), declared)));
        doReturn(TestAdapter.class).when(beans).getBeanClass(declared);
        directory.reload();
        when(clients.pluginOwner("fixture")).thenReturn("other.client");
        assertThrows(IllegalStateException.class, directory::list);
        assertThrows(IllegalStateException.class, () -> directory.guard("fixture", () -> {}));
    }

    /** 查询不触发扫描，重启插件也不能让旧权限令牌恢复有效。 */
    @Test
    void cachesDeclarationsButKeepsRevocationLive() {
        var scanner = mock(IBeanScanner.class);
        var adapter = new TestAdapter();
        when(scanner.getBeansWithAnnotationAndInterface(TeaNekoClientManagement.class, IClientManagementAdapter.class))
                .thenReturn(Map.of("fixture", new IndependentPair<>(
                        TestAdapter.class.getAnnotation(TeaNekoClientManagement.class), adapter)));
        doReturn(TestAdapter.class).when(scanner).getBeanClass(adapter);
        var plugins = new PluginRegistry(List.of(() -> new PluginDescriptor("test.client", "1", 1, Set.of())));
        var factory = new DefaultListableBeanFactory();
        factory.registerSingleton("plugins", plugins);
        var directory = new ClientManagementDirectory(scanner, factory.getBeanProvider(PluginRegistry.class));
        clearInvocations(scanner);
        var oldGuard = directory.guard("fixture", () -> {});
        assertEquals(1, directory.list().size());
        oldGuard.run();
        verifyNoInteractions(scanner);
        var response = new CompletableFuture<String>();
        var pending = directory.invoke("fixture", ignored -> response);
        plugins.setEnabled("test.client", false);
        assertTrue(directory.list().isEmpty());
        assertThrows(IllegalStateException.class, oldGuard::run);
        plugins.setEnabled("test.client", true);
        response.complete("迟到响应");
        assertThrows(CompletionException.class, () -> pending.toCompletableFuture().join());
        assertThrows(IllegalStateException.class, oldGuard::run);
        var refreshed = directory.guard("fixture", () -> {});
        when(scanner.getBeansWithAnnotationAndInterface(TeaNekoClientManagement.class, IClientManagementAdapter.class))
                .thenThrow(new IllegalStateException("测试重扫失败"));
        assertThrows(IllegalStateException.class, directory::reload);
        assertEquals(1, directory.list().size());
        refreshed.run();
    }

    /**
     * 未重复声明插件归属的管理适配器，使用实际客户端已发布的归属。
     * @author zExNocs
     * @date 2026/11/07
     * @since 4.9.0-l
     * @version 4.9.0-l
     */
    @TeaNekoClientManagement("fixture")
    private static final class UnownedAdapter implements IClientManagementAdapter {
        /** @return 测试目录状态 */
        @Override
        public View describe() {
            return new View("fixture", "测试客户端", "AVAILABLE", 0, "无外部连接", Instant.now(), false);
        }
    }

    /**
     * 只提供可观察目录，不伪造发送能力。
     * @author zExNocs
     * @date 2026/11/06
     * @since 4.8.4-l
     * @version 4.8.4-l
     */
    @TeaNekoClientManagement("fixture")
    @PluginOwned("test.client")
    private static final class TestAdapter implements IClientManagementAdapter {
        /** @return 测试目录状态 */
        @Override
        public View describe() {
            return new View("fixture", "测试客户端", "AVAILABLE", 0, "无外部连接", Instant.now(), false);
        }
    }
}
