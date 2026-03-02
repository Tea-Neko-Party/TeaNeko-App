package org.zexnocs.teanekoapp.client;

import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.zexnocs.teanekoapp.client.api.IClient;
import org.zexnocs.teanekocore.event.interfaces.IEventService;

/**
 * 抽象 websocket 客户端类，用于快速构建客户端，提供一些公共方法和属性。
 *
 * @author zExNocs
 * @date 2026/03/01
 * @since 4.0.11
 */
public abstract class AbstractWebsocketClient extends TextWebSocketHandler implements IClient {
    protected final IEventService eventService;

    public AbstractWebsocketClient(IEventService eventService) {
        this.eventService = eventService;
    }

    /**
     * 用于推送事件到事件系统。
     *
     * @param message 原始消息字符串
     */
    protected void _handle(String message) {
        this.eventService.pushEvent(handle(message));
    }
}
