package org.zexnocs.teanekoapp.client;

import org.jspecify.annotations.NonNull;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.handler.WebSocketSessionDecorator;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 在既有客户端外侧统一检查每次入站和实际发送，不修改平台事件或关闭借用实现。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.7.0-l
 */
public final class GuardedClientWebSocketHandler extends WebSocketHandlerDecorator {
    private final List<IClientConnectionGuard> guards;
    private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    /**
     * @param delegate 容器拥有的实际客户端
     * @param guards 已注册宿主策略
     */
    public GuardedClientWebSocketHandler(WebSocketHandler delegate, List<IClientConnectionGuard> guards) {
        super(delegate);
        this.guards = List.copyOf(guards);
    }

    /**
     * @param session 实际新连接
     * @throws Exception 当实际客户端接入失败时
     */
    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) throws Exception {
        var wrapped = new GuardedSession(session);
        if (!allowed(wrapped)) {
            wrapped.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        sessions.put(session.getId(), wrapped);
        try {
            super.afterConnectionEstablished(wrapped);
        } catch (Exception failure) {
            sessions.remove(session.getId());
            try {
                wrapped.close(CloseStatus.SERVER_ERROR);
            } catch (IOException closing) {
                if (closing != failure) {
                    failure.addSuppressed(closing);
                }
            }
            throw failure;
        }
    }

    /**
     * @param session 实际连接
     * @param message 当前帧
     * @throws Exception 当实际处理或关闭失败时
     */
    @Override
    public void handleMessage(WebSocketSession session, @NonNull WebSocketMessage<?> message) throws Exception {
        var wrapped = sessions.get(session.getId());
        if (wrapped == null || !allowed(wrapped)) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        super.handleMessage(wrapped, message);
    }

    /**
     * @param session 原连接
     * @param status 原关闭状态
     * @throws Exception 当原客户端收尾失败时
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, @NonNull CloseStatus status) throws Exception {
        var wrapped = sessions.remove(session.getId());
        super.afterConnectionClosed(wrapped == null ? session : wrapped, status);
    }

    /**
     * @param session 原连接
     * @return 全部当前宿主策略是否通过
     */
    private boolean allowed(WebSocketSession session) {
        try {
            return guards.stream().allMatch(guard -> guard.allows(session));
        } catch (RuntimeException failure) {
            return false;
        }
    }

    /**
     * @param session 原连接
     * @param exception 原传输失败
     * @throws Exception 当实际客户端故障处理失败时
     */
    @Override
    public void handleTransportError(@NonNull WebSocketSession session, @NonNull Throwable exception) throws Exception {
        super.handleTransportError(sessions.getOrDefault(session.getId(), session), exception);
    }

    /**
     * 传给实际客户端保存的连接，异步发送时也必须重新核验。
     *
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    private final class GuardedSession extends WebSocketSessionDecorator {
        /** @param delegate 原网络连接 */
        private GuardedSession(WebSocketSession delegate) {
            super(delegate);
        }

        /**
         * @param message 已准备好的实际帧
         * @throws IOException 当授权已失效或传输失败时
         */
        @Override
        public void sendMessage(@NonNull WebSocketMessage<?> message) throws IOException {
            if (!allowed(this)) {
                close(CloseStatus.POLICY_VIOLATION);
                throw new IOException("机器连接授权已失效，停止实际发送。");
            }
            super.sendMessage(message);
        }
    }
}
