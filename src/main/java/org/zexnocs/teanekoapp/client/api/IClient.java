package org.zexnocs.teanekoapp.client.api;

/**
 * 客户端接口，具有发送和接收 String 消息的能力。
 *
 * @author zExNocs
 * @date 2026/02/20
 */
public interface IClient {
    /**
     * 发送消息到服务器。
     *
     * @param message 要发送的消息
     */
    void send(String message);

    /**
     * 从客户端接收消息并处理。
     * 一般就是将 message 解析后包装成事件对象并推送到事件总线中。
     *
     * @param message 接收到的消息
     */
    void handle(String message);
}
