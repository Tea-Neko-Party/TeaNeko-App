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
 * <br>2026/11/07：向连接守卫交付建立与真实关闭通知，支持插件停用排空。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.9.0-l
 */
public final class GuardedClientWebSocketHandler extends WebSocketHandlerDecorator {
    private final List<IClientConnectionGuard> guards;
    private final ConcurrentHashMap<String, Connection> sessions = new ConcurrentHashMap<>();

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
        // 2026/11/07：连接回调的实际完成也属于资源终态，不能仅凭网络关闭提前排空。
        var connection = new Connection(wrapped);
        if (sessions.putIfAbsent(session.getId(), connection) != null) {
            throw new IllegalStateException("客户端连接标识重复。");
        }
        try {
            // 2026/11/07：登记与最后资格复核连续执行，覆盖停用和连接建立竞争。
            guards.forEach(guard -> guard.connected(wrapped));
            if (!allowed(wrapped)) {
                wrapped.close(CloseStatus.POLICY_VIOLATION);
                return;
            }
            super.afterConnectionEstablished(wrapped);
        } catch (Exception failure) {
            try {
                wrapped.close(CloseStatus.SERVER_ERROR);
            } catch (IOException closing) {
                if (closing != failure) {
                    failure.addSuppressed(closing);
                }
            }
            throw failure;
        } finally {
            connection.leave();
        }
    }

    /**
     * @param session 实际连接
     * @param message 当前帧
     * @throws Exception 当实际处理或关闭失败时
     */
    @Override
    public void handleMessage(WebSocketSession session, @NonNull WebSocketMessage<?> message) throws Exception {
        var connection = sessions.get(session.getId());
        if (connection == null || !connection.enter()) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        try {
            if (!allowed(connection.session)) {
                session.close(CloseStatus.POLICY_VIOLATION);
                return;
            }
            super.handleMessage(connection.session, message);
        } finally {
            connection.leave();
        }
    }

    /**
     * @param session 原连接
     * @param status 原关闭状态
     * @throws Exception 当原客户端收尾失败时
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, @NonNull CloseStatus status) throws Exception {
        var connection = sessions.remove(session.getId());
        // 2026/11/07：原客户端收尾完成后才归还连接生命周期。
        var actual = connection == null ? session : connection.session;
        try {
            super.afterConnectionClosed(actual, status);
        } finally {
            if (connection == null) {
                guards.forEach(guard -> guard.disconnected(actual));
            } else {
                connection.closed();
            }
        }
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
        var connection = sessions.get(session.getId());
        if (connection == null) {
            super.handleTransportError(session, exception);
            return;
        }
        if (!connection.enter()) {
            return;
        }
        try {
            super.handleTransportError(connection.session, exception);
        } finally {
            connection.leave();
        }
    }

    /**
     * 一个网络连接及已进入的同步协议回调；异步自有业务仍由插件生命周期排空。
     * <br>2026/11/07：原关闭回调和所有已进入回调都结束后才通知守卫释放资源。
     *
     * @author zExNocs
     * @date 2026/11/07
     * @since 4.9.0-l
     * @version 4.9.0-l
     */
    private final class Connection {
        private final WebSocketSession session;
        private int running = 1;
        private boolean closed;
        private boolean notified;

        /** @param session 实际借用的守卫会话 */
        private Connection(WebSocketSession session) {
            this.session = session;
        }

        /** @return 连接尚未关闭时登记当前回调并返回真 */
        private synchronized boolean enter() {
            if (closed) {
                return false;
            }
            running++;
            return true;
        }

        /** 释放已经实际返回的协议回调，最后一个回调可以完成停用排空。 */
        private void leave() {
            synchronized (this) {
                running--;
            }
            notifyIfComplete();
        }

        /** 记录底层关闭回调已经完成，但不跳过其他仍在运行的回调。 */
        private void closed() {
            synchronized (this) {
                closed = true;
            }
            notifyIfComplete();
        }

        /** 锁外通知完整结束，避免守卫回调与网络资源锁互相等待。 */
        private void notifyIfComplete() {
            synchronized (this) {
                if (!closed || running != 0 || notified) {
                    return;
                }
                notified = true;
            }
            guards.forEach(guard -> guard.disconnected(session));
        }
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
