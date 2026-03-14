package org.zexnocs.teanekoapp.fake_client;

import org.springframework.stereotype.Component;
import org.zexnocs.teanekoapp.client.api.IClient;
import org.zexnocs.teanekocore.event.interfaces.IEvent;

/**
 * 假客户端
 *
 * @author zExNocs
 * @date 2026/03/14
 * @since 4.2.3
 */
@Component
public class FakeClient implements IClient {

    /**
     * 发送消息到服务器。
     *
     * @param message 要发送的消息
     */
    @Override
    public void send(String message) {

    }

    /**
     * 从客户端接收消息并处理。
     * 一般就是将 message 解析后包装成事件对象并推送到事件总线中。
     * 一般分为两个主要事件：
     * 1. 消息事件 MessageEvent：用于处理用户发送的消息
     * 2. 响应事件 ResponseEvent：用于处理客户端的响应信息
     *
     * @param message 接收到的消息
     */
    @Override
    public IEvent<?> handle(String message) {
        return null;
    }
}
