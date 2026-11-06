package org.zexnocs.teanekoclient.onebot.core;

import lombok.Setter;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.zexnocs.teanekoapp.client.AbstractWebsocketClient;
import org.zexnocs.teanekoapp.client.api.IClient;
import org.zexnocs.teanekoapp.response.ResponseEvent;
import org.zexnocs.teanekoclient.onebot.data.response.OnebotRawResponseData;
import org.zexnocs.teanekoclient.onebot.event.OnebotEventShareComponent;
import org.zexnocs.teanekoclient.onebot.event.PostReceiveEvent;
import org.zexnocs.teanekoclient.onebot.event.teaneko.OnebotConnectEvent;
import org.zexnocs.teanekocore.event.interfaces.IEvent;
import org.zexnocs.teanekocore.event.interfaces.IEventService;
import org.zexnocs.teanekocore.logger.ILogger;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.util.Map;
import java.util.concurrent.*;

/**
 * OneBot 连接管理与异步消息收发，停机时先关闭连接并释放自有执行器。
 * <br>2026/11/03：加入容器生命周期，区分停机关闭握手与运行期传输故障。
 * <br>2026/11/03：为 Web 只读观测提供真实开放连接数，不导出连接句柄。
 * <br>2026/11/03：按原连接绑定自身账号和连接代次，旧缓存不参与平台联系人查询许可。
 * <br>2026/11/03：指令台发送锁定原唯一连接，实际写入前重新校验，不回退广播。
 *
 * <br>2026/11/06：诊断仅记录安全标识、帧长度与异常定位，不复制消息正文。
 *
 * @author zExNocs
 * @date 2026/02/28
 * @since 4.0.12
 * @version 4.8.4-l
 */
@Component
public class OnebotClient extends AbstractWebsocketClient implements IClient, SmartLifecycle, DisposableBean {
    private static final String TAG = "Onebot WebSocket Server";

    /**
     * 2026/11/03：连接数只表达传输事实，不代表身份、健康度或账号路由就绪。
     * @return 此刻仍开放的传输连接数量
     */
    public int getOpenConnectionCount() {
        return stopping ? 0 : (int) sessions.values().stream().filter(WebSocketSession::isOpen).count();
    }

    /// 存储所有连接的会话。注意：现在存的是被装饰过的、线程安全的 Session
    private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    // 2026/11/03：仅在当前实例锁内修改，连接变化使等待中的平台查询失效。
    private final Map<String, Long> connectionSelfIds = new java.util.HashMap<>();
    private long connectionEpoch;

    /**
     * 2026/11/03：只有唯一开放连接且该连接已提供真实自身 ID 时才可查询账号联系人。
     * @return 当前一致身份快照，不包含 session
     */
    public synchronized ConnectionIdentity connectionIdentity() {
        var open = sessions.values().stream().filter(WebSocketSession::isOpen).toList();
        long self = open.size() == 1 ? connectionSelfIds.getOrDefault(open.getFirst().getId(), 0L) : 0;
        return new ConnectionIdentity(connectionEpoch, open.size(), self > 0 ? Long.toString(self) : "",
                !stopping && open.size() == 1 && self > 0);
    }

    /**
     * 2026/11/03：在原连接身份锁内核对并写入固定会话，不遍历其他连接，不重试。
     * @param expected 宿主原来捕获的唯一账号与连接代次
     * @param message 原发送链生成的协议正文
     * @param guard 实际传输副作用前的当前管理授权
     * @throws IllegalStateException 当连接换代、不唯一或写入失败时
     */
    public synchronized void sendPinned(ConnectionIdentity expected, String message, Runnable guard) {
        if (!expected.ready() || !expected.equals(connectionIdentity())) {
            throw new IllegalStateException("OneBot 原连接已失效或账号不再唯一，不会改投其他连接。");
        }
        var session = sessions.values().stream().filter(WebSocketSession::isOpen).findFirst().orElseThrow();
        guard.run();
        try {
            session.sendMessage(new TextMessage(message));
        } catch (IOException failure) {
            reportTransportFailure(session, failure);
            removeSession(session);
            throw new IllegalStateException("OneBot 固定连接写入未确认，不会自动重发。");
        }
    }

    /**
     * 当前连接定位，不替代发送前再次复核。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param epoch 连接或身份变化代次
     * @param connections 开放连接数
     * @param selfId 唯一连接确认的账号
     * @param ready 是否可在原广播发送器上安全进行只读查询
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    public record ConnectionIdentity(long epoch, int connections, String selfId, boolean ready) {}

    /// 用于异步发送消息的线程池
    private final ExecutorService sendExecutor = Executors.newVirtualThreadPerTaskExecutor();

    ///  logger
    private final ILogger logger;

    /// 共享数据组件
    private final OnebotEventShareComponent onebotEventShareComponent;

    @Setter
    private volatile boolean canAcceptConnections = true;

    // 2026/11/03：停机状态在关闭任何会话和执行器之前发布，回调不能误报正常断连。
    private volatile boolean stopping;

    /**
     * 2026/11/03：注入原事件服务和消息解析资源，自有执行器由本客户端生命周期释放。
     * @param logger 原日志服务
     * @param eventService 原事件服务
     * @param onebotEventShareComponent 原消息解析资源
     */
    @Autowired
    public OnebotClient(ILogger logger,
                        IEventService eventService,
                        OnebotEventShareComponent onebotEventShareComponent) {
        super(eventService);
        this.logger = logger;
        this.onebotEventShareComponent = onebotEventShareComponent;
    }

    /**
     * 2026/11/03：会话登记与停机互斥，拒绝停机期间新增连接。
     * @param session 原始连接
     * @throws Exception 当底层会话设置或拒绝握手失败时
     */
    @Override
    public synchronized void afterConnectionEstablished(@NonNull WebSocketSession session) throws Exception {
        if (stopping || !canAcceptConnections) {
            session.close(CloseStatus.SERVICE_RESTARTED);
            return;
        }

        // 设置底层限制
        session.setTextMessageSizeLimit(1024 * 1024);
        session.setBinaryMessageSizeLimit(1024 * 1024);

        // 发送超时时间 (10秒)，溢出缓冲区大小限制 (5MB)。如果超出限制，Session 会被自动关闭并抛出异常。
        WebSocketSession safeSession = new ConcurrentWebSocketSessionDecorator(
                session, 10000, 5 * 1024 * 1024);

        sessions.put(safeSession.getId(), safeSession);
        // 2026/11/03：新连接不继承先前连接的机器人号。
        connectionSelfIds.remove(safeSession.getId());
        connectionEpoch++;
        eventService.pushEvent(new OnebotConnectEvent(safeSession.getId()));
        logger.info(TAG, "连接新的 session: %s。总连接个数: %d".formatted(safeSession.getId(), sessions.size()));
    }

    /**
     * 2026/11/03：借用自有执行器处理接收消息，停机后不再接收新任务。
     * @param session 原始连接
     * @param message 平台文本帧
     */
    @Override
    protected void handleTextMessage(@NonNull WebSocketSession session, TextMessage message) {
        String payload = message.getPayload();
        logger.debug(TAG, "收到会话 %s 的文本帧，字符数=%d".formatted(session.getId(), payload.length()));

        // 异步处理接收到的消息，防止阻塞 WebSocket 的 IO 接收线程
        // 2026/11/03：原帧中的自身账号必须固定在此连接，换号不能复用旧查询。
        executeAsync(() -> {
            if (observeIdentity(session, payload)) {
                _handle(payload);
            }
        });
    }

    /**
     * @param session 原连接
     * @param payload 已接收原协议正文
     * @return 是否可继续原处理
     */
    private boolean observeIdentity(WebSocketSession session, String payload) {
        try {
            var root = onebotEventShareComponent.objectMapper.readTree(payload);
            if (!root.has("self_id") || root.get("self_id").asLong() <= 0) {
                return true;
            }
            long self = root.get("self_id").asLong();
            synchronized (this) {
                if (!sessions.containsKey(session.getId())) {
                    return false;
                }
                Long previous = connectionSelfIds.get(session.getId());
                if (previous == null) {
                    connectionSelfIds.put(session.getId(), self);
                    connectionEpoch++;
                    return true;
                }
                if (previous == self) {
                    return true;
                }
            }
            removeSession(session);
            return false;
        } catch (RuntimeException failure) {
            logger.warn(TAG, "连接元数据无法解析，已跳过当前协议帧。");
            return false;
        }
    }

    /**
     * 2026/11/03：补充协议解析合同，按 OneBot 协议区分平台事件和 API 回执。
     * @param information 原始协议 JSON
     * @return 已解析事件，无法识别时为空
     */
    @Override
    public IEvent<?> handle(String information) {
        var rootNode = onebotEventShareComponent.objectMapper.readTree(information);
        // 解析成一般 post type 消息
        if(rootNode.has("post_type") || rootNode.has("message_type")) {
            return new PostReceiveEvent(information, onebotEventShareComponent);
        }
        // 否则尝试解析成 response 消息
        if(rootNode.has("status") && rootNode.has("echo")) {
            // 解析成 OnebotRawResponseData 对象
            return new ResponseEvent(
                    onebotEventShareComponent.objectMapper.convertValue(rootNode, OnebotRawResponseData.class),
                    OnebotRawResponseData.class
            );
        }

        // 如果都不匹配，报错并返回 null
        logger.errorWithReport(this.getClass().getSimpleName(), "未知的信息类型；字符数=" + information.length());
        return null;
    }

    /**
     * 多线程异步发送消息到所有连接的客户端。
     * 2026/11/03：停机后停止提交，已开始发送的关闭异常按生命周期分类。
     * @param message 待发送协议正文
     */
    @Override
    public void send(String message) {
        if (stopping) { return; }
        if (sessions.isEmpty()) {
            logger.warn(TAG, "没有可用连接发送消息；未记录消息正文。");
            return;
        }

        TextMessage textMessage = new TextMessage(message);

        // 2026/11/03：各连接独立提交发送，关闭期间停止新增工作。
        sessions.values().forEach(session -> {
            if (session.isOpen()) {
                executeAsync(() -> {
                    if (stopping) { return; }
                    try {
                        session.sendMessage(textMessage);
                    } catch (IOException e) {
                        reportTransportFailure(session, e);
                        removeSession(session);
                    }
                });
            } else {
                removeSession(session);
            }
        });
    }

    /**
     * 2026/11/03：补充关闭回调合同，移除会话并保留平台关闭码用于诊断。
     * @param session 原始连接
     * @param status 平台关闭状态
     */
    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
        removeSession(session);
        logger.info(TAG, "客户端断开连接: %s, 原因: %s。剩余数量: %d".formatted(session.getId(), status.getCode(), sessions.size()));
    }

    /**
     * 2026/11/03：只有已进入停机且根因为关闭通道的异常降为调试日志。
     * @param session 原始连接
     * @param exception 原传输异常
     */
    @Override
    public void handleTransportError(@NonNull WebSocketSession session, @NonNull Throwable exception) {
        reportTransportFailure(session, exception);
        removeSession(session);
    }

    /**
     * 2026/11/03：生命周期关闭竞态不向已停止的执行器提交任务，运行期故障仍保留错误诊断。
     * @param action 本客户端拥有的异步工作
     */
    private void executeAsync(Runnable action) {
        if (stopping) { return; }
        try {
            CompletableFuture.runAsync(action, sendExecutor).exceptionally(failure -> {
                logger.error(TAG, "消息处理异常。", failure); return null;
            });
        } catch (RejectedExecutionException failure) {
            if (!stopping) { logger.error(TAG, "消息任务提交失败。", failure); }
        }
    }

    /**
     * 停机关闭握手中已关闭通道属于正常收尾；其他异常不能被一并屏蔽。
     * @param session 失败连接
     * @param failure 传输异常
     */
    private void reportTransportFailure(WebSocketSession session, Throwable failure) {
        if (stopping && isClosedChannel(failure)) {
            logger.debug(TAG, "应用停机期间会话 %s 的通道已关闭。".formatted(session.getId()));
        } else {
            logger.error(TAG, "会话 %s 的 WebSocket 传输失败。".formatted(session.getId()), failure);
        }
    }

    /**
     * 检查有限原因链，不依赖异常正文中的英文信息判断停机。
     * @param failure 原传输异常
     * @return 原因链包含关闭通道时为真
     */
    private static boolean isClosedChannel(Throwable failure) {
        Throwable cause = failure;
        for (int depth = 0; cause != null && depth < 16; depth++, cause = cause.getCause()) {
            if (cause instanceof ClosedChannelException) { return true; }
        }
        return false;
    }

    /**
     * 本实例只在单次容器生命周期中启动，停止后的重启由新容器创建新实例。
     * @throws IllegalStateException 当试图重新启动已释放资源的实例时
     */
    @Override public synchronized void start() {
        if (stopping) { throw new IllegalStateException("已停止的 OneBot 客户端不能重新启动。"); }
    }

    /**
     * 在 Web 服务器销毁前停止接入和异步任务，再主动发送关闭握手；可重复调用。
     */
    @Override public synchronized void stop() {
        if (stopping) { return; }
        stopping = true; canAcceptConnections = false;
        sendExecutor.shutdownNow();
        sessions.values().forEach(this::removeSession);
    }

    /** @return 本实例尚未停止时为真 */
    @Override public boolean isRunning() { return !stopping; }

    /** @return 在 Web 服务器停止阶段之前释放连接的最高阶段 */
    @Override public int getPhase() { return Integer.MAX_VALUE; }

    /** 对未完整启动的容器同样释放资源，不依赖生命周期停止是否已执行。 */
    @Override public void destroy() { stop(); }

    /**
     * 安全移除 Session 的辅助方法
     * 2026/11/03：停机时明确关闭为服务重启，回调可安全重复移除。
     * @param session 待移除连接
     */
    private void removeSession(WebSocketSession session) {
        // 2026/11/03：身份与连接同代撤销，网络关闭不持身份快照锁。
        synchronized (this) {
            if (sessions.remove(session.getId()) != null) {
                connectionSelfIds.remove(session.getId());
                connectionEpoch++;
            }
        }
        if (session.isOpen()) {
            try {
                session.close(stopping ? CloseStatus.SERVICE_RESTARTED : CloseStatus.NORMAL);
            } catch (IOException ignored) {
                // 忽略关闭时的异常
            }
        }
    }
}
