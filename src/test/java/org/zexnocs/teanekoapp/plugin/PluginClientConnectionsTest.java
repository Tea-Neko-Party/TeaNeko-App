package org.zexnocs.teanekoapp.plugin;

import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.TextMessage;
import org.zexnocs.teanekoapp.client.GuardedClientWebSocketHandler;
import org.zexnocs.teanekoapp.client.TeaNekoClientScanner;
import org.zexnocs.teanekoapp.client.api.ITeaNekoClient;
import org.zexnocs.teanekoapp.client.api.TeaNekoClient;
import org.zexnocs.teanekocore.framework.pair.IndependentPair;
import org.zexnocs.teanekocore.plugin.PluginDescriptor;
import org.zexnocs.teanekocore.plugin.PluginRegistry;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 验证客户端连接归属、停用排空和失败后重试，不用网络模拟关闭回调已经完成。
 * <br>2026/11/07：旧连接在插件重启用后仍被拒绝，关闭错误不跳过其他自有连接。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
class PluginClientConnectionsTest {
    /**
     * 停用等待实际客户端关闭回调，重新启用不复活旧连接令牌。
     * @throws Exception 当有界等待或关闭失败时
     */
    @Test
    void drainWaitsForPhysicalCloseAndOldConnectionRemainsRevoked() throws Exception {
        var registry = registry();
        var guard = guard(registry);
        var session = connection(guard, "first");
        registry.setEnabled("provider", false);
        var stopped = guard.stop("provider").toCompletableFuture();
        verify(session).close(CloseStatus.POLICY_VIOLATION);
        assertFalse(stopped.isDone());
        assertFalse(guard.allows(session));
        guard.disconnected(session);
        stopped.get(2, TimeUnit.SECONDS);
        registry.setEnabled("provider", true);
        assertFalse(guard.allows(session));
        var replacement = connection(guard, "replacement");
        assertTrue(guard.allows(replacement));
        guard.disconnected(replacement);
    }

    /**
     * 一条连接关闭失败仍尝试其他连接并等待成功关闭项；失败项保留供显式重试。
     * @throws Exception 当有界等待或关闭失败时
     */
    @Test
    void closingFailureDoesNotSkipOtherConnectionsAndCanRetry() throws Exception {
        var registry = registry();
        var guard = guard(registry);
        var failed = connection(guard, "failed");
        var normal = connection(guard, "normal");
        doThrow(new IOException("测试关闭失败。")).doNothing().when(failed).close(CloseStatus.POLICY_VIOLATION);
        registry.setEnabled("provider", false);
        var first = guard.stop("provider").toCompletableFuture();
        verify(normal).close(CloseStatus.POLICY_VIOLATION);
        assertFalse(first.isDone());
        guard.disconnected(normal);
        assertThrows(ExecutionException.class, () -> first.get(2, TimeUnit.SECONDS));
        var retry = guard.stop("provider").toCompletableFuture();
        assertFalse(retry.isDone());
        guard.disconnected(failed);
        retry.get(2, TimeUnit.SECONDS);
        verify(failed, times(2)).close(CloseStatus.POLICY_VIOLATION);
    }

    /**
     * 网络关闭回调结束后仍等待已进入的协议回调，避免释放其仍在借用的插件依赖。
     * @throws Exception 当有界协议回调等待失败时
     */
    @Test
    void drainIncludesAlreadyEnteredProtocolCallbacks() throws Exception {
        var registry = registry();
        var guard = guard(registry);
        var session = connection(guard, "in-flight", false);
        var delegate = mock(WebSocketHandler.class);
        var handler = new GuardedClientWebSocketHandler(delegate, List.of(guard));
        var entered = new CompletableFuture<Void>();
        var physical = new CompletableFuture<Void>();
        doAnswer(ignored -> {
            entered.complete(null);
            physical.get(2, TimeUnit.SECONDS);
            return null;
        }).when(delegate).handleMessage(any(), any());
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            handler.afterConnectionEstablished(session);
            var receiving = executor.submit(() -> {
                handler.handleMessage(session, new TextMessage("已进入的业务"));
                return null;
            });
            try {
                entered.get(2, TimeUnit.SECONDS);
                registry.setEnabled("provider", false);
                var stopped = guard.stop("provider").toCompletableFuture();
                handler.afterConnectionClosed(session, CloseStatus.NORMAL);
                assertFalse(stopped.isDone());
                physical.complete(null);
                receiving.get(2, TimeUnit.SECONDS);
                stopped.get(2, TimeUnit.SECONDS);
            } finally {
                physical.complete(null);
            }
        }
    }

    /** @return 测试客户端提供者目录 */
    private PluginRegistry registry() {
        return new PluginRegistry(List.of(() -> new PluginDescriptor("provider", "1", 1, Set.of())));
    }

    /**
     * 构造已发布客户端事实，测试不调用发现扫描。
     * @param registry 插件目录
     * @return 真实连接守卫
     */
    private PluginClientConnections guard(PluginRegistry registry) {
        var clients = mock(TeaNekoClientScanner.class);
        when(clients.pluginOwner("extension")).thenReturn("provider");
        when(clients.getPair("extension")).thenAnswer(ignored -> registry.isEnabled("provider")
                ? new IndependentPair<>(mock(TeaNekoClient.class), mock(ITeaNekoClient.class)) : null);
        return new PluginClientConnections(clients, registry);
    }

    /**
     * 通过真实握手逻辑建立服务器代次证明，并登记连接。
     * @param guard 连接守卫
     * @param id 物理连接标识
     * @return 已登记且可观察关闭请求的会话
     */
    private WebSocketSession connection(PluginClientConnections guard, String id) {
        return connection(guard, id, true);
    }

    /**
     * 为直接守卫或完整网络装饰器准备握手会话。
     * @param guard 连接守卫
     * @param id 连接标识
     * @param register 是否立即模拟建立回调
     * @return 持有真实服务器证明的会话
     */
    private WebSocketSession connection(PluginClientConnections guard, String id, boolean register) {
        var attributes = new HashMap<String, Object>();
        assertTrue(guard.beforeHandshake(
                new ServletServerHttpRequest(new MockHttpServletRequest("GET", "/ws/extension")),
                new ServletServerHttpResponse(new MockHttpServletResponse()), mock(WebSocketHandler.class), attributes));
        var session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.getAttributes()).thenReturn(attributes);
        if (register) {
            guard.connected(session);
        }
        return session;
    }
}
