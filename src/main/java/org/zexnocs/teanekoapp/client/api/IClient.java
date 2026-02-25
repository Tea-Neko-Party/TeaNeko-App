package org.zexnocs.teanekoapp.client.api;

import org.zexnocs.teanekoapp.sender.api.ISendData;

/**
 * 客户端接口，具有发送和接收 String 消息的能力。
 *
 * @author zExNocs
 * @date 2026/02/20
 * @since 4.0.8
 */
public interface IClient {
    /**
     * 发送消息到服务器。
     *
     * @param message 要发送的消息
     */
    void send(String message);

    /**
     * 根据发送消息发送到服务器种
     *
     * @param data 发送数据对象
     */
    default void send(ISendData<?> data) {
        send(data.toSendString());
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
    void handle(String message);
}
