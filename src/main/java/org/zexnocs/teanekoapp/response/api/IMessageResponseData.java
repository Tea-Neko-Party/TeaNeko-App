package org.zexnocs.teanekoapp.response.api;

/**
 * 用于接收消息后回复消息的数据接口。
 * 主要是获取到发送的 message id
 *
 * @author zExNocs
 * @date 2026/02/24
 * @since 4.0.9
 */
public interface IMessageResponseData {
    /**
     * 获取消息 ID。
     *
     * @return 消息 ID
     */
    String getMessageId();
}
