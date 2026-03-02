package org.zexnocs.teanekoclient.onebot.core;

import lombok.Setter;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.zexnocs.teanekoapp.client.api.IClient;
import org.zexnocs.teanekocore.event.interfaces.IEvent;
import org.zexnocs.teanekocore.event.interfaces.IEventService;
import org.zexnocs.teanekocore.logger.ILogger;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * onebot 客户端类。
 *
 * @author zExNocs
 * @date 2026/02/28
 * @since 4.0.12
 */
@Component
public class OnebotClient extends TextWebSocketHandler implements IClient {
    private static final String TAG = "Onebot WebSocket Server";

    // 存储所有连接的会话。注意：现在存的是被装饰过的、线程安全的 Session
    private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    // 用于异步发送消息的线程池
    private final ExecutorService sendExecutor = Executors.newVirtualThreadPerTaskExecutor();

    private final ILogger logger;
    private final IEventService eventService;
    private final ObjectMapper objectMapper;

    @Setter
    private boolean canAcceptConnections = true;

    @Autowired
    public OnebotClient(ILogger logger, IEventService eventService, ObjectMapper objectMapper) {
        this.logger = logger;
        this.eventService = eventService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) throws Exception {
        if (!canAcceptConnections) {
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
        logger.info(TAG, "连接新的 session: %s。总连接个数: %d".formatted(safeSession.getId(), sessions.size()));
    }

    @Override
    protected void handleTextMessage(@NonNull WebSocketSession session, TextMessage message) {
        String payload = message.getPayload();
        logger.debug(TAG, "Received message from %s: %s".formatted(session.getId(), payload));

        // 异步处理接收到的消息，防止阻塞 WebSocket 的 IO 接收线程
        CompletableFuture.runAsync(() -> handle(payload), sendExecutor)
                .exceptionally(ex -> {
                    logger.error(TAG, "消息处理异常: " + ex.getMessage(), ex);
                    return null;
                });
    }

    @Override
    public IEvent<?> handle(String information) {
        /* todo
        try {
            var rootNode = objectMapper.readTree(information);
            // 解析成一般 post type 消息
            if(rootNode.has("post_type") || rootNode.has("message_type")) {
                return new PostReceiveEvent(information);
            }
            // 否则尝试解析成 response 消息
            if(rootNode.has("status") && rootNode.has("echo")) {
                return new ResponseReceiveEvent(information);
            }

            // 如果都不匹配，报错并返回 null
            logger.errorWithReport(ReceiveEvent.class.getName(),
                    "未知的信息类型: " + information);
            return null;
        } catch(Exception e) {
            logger.errorWithReport(ReceiveEvent.class.getName(),
                    "JSON 解析失败: " + information + ", 错误: " + e.getMessage());
            return null;
        }

         */
        return null;
    }

    /**
     * 多线程异步发送消息到所有连接的客户端。
     */
    @Override
    public void send(String message) {
        if (sessions.isEmpty()) {
            logger.warn(TAG, "没有可用客户端发送信息: " + message);
            return;
        }

        TextMessage textMessage = new TextMessage(message);

        // 遍历所有 session 并进行多线程异步发送 (Fan-out)
        sessions.values().forEach(session -> {
            if (session.isOpen()) {
                CompletableFuture.runAsync(() -> {
                    try {
                        session.sendMessage(textMessage);
                    } catch (IOException e) {
                        logger.error(TAG, "发送消息到 session %s 失败: %s".formatted(session.getId(), e.getMessage()), e);
                        removeSession(session);
                    }
                }, sendExecutor);
            } else {
                removeSession(session);
            }
        });
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
        removeSession(session);
        logger.info(TAG, "客户端断开连接: %s, 原因: %s。剩余数量: %d".formatted(session.getId(), status, sessions.size()));
    }

    @Override
    public void handleTransportError(@NonNull WebSocketSession session, @NonNull Throwable exception) {
        logger.error(TAG, "WebSocket error on session %s: %s".formatted(session.getId(), exception.getMessage()), exception);
        removeSession(session);
    }

    /**
     * 安全移除 Session 的辅助方法
     */
    private void removeSession(WebSocketSession session) {
        sessions.remove(session.getId());
        if (session.isOpen()) {
            try {
                session.close();
            } catch (IOException ignored) {
                // 忽略关闭时的异常
            }
        }
    }
}