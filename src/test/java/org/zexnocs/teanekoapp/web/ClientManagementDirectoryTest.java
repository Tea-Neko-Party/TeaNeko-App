package org.zexnocs.teanekoapp.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.zexnocs.teanekoapp.web.client.*;
import org.zexnocs.teanekocore.framework.pair.IndependentPair;
import org.zexnocs.teanekocore.plugin.*;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IBeanScanner;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 静态客户端声明只在重扫发现，插件与目录换代立即撤销旧调用。
 * <br>2026/11/06：验证失败重扫保持完整目录，不连接平台。
 *
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.4-l
 * @version 4.8.4-l
 */
class ClientManagementDirectoryTest {
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
