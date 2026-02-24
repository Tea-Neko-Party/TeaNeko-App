package org.zexnocs.teanekoapp.sender.api.sender_box;

import org.zexnocs.teanekoapp.message.api.ITeaNekoMessage;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekoapp.response.api.IMessageResponseData;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;

import java.util.List;

/**
 * 根据接收到的 {@link ITeaNekoMessageData} 类型来快速发送消息的发送器接口。
 *
 * @author zExNocs
 * @date 2026/02/24
 * @since 4.0.9
 */
public interface IMessageSender {
    /**
     * 根据信息列表发送，并获取 task future 用于后续操作，例如发送一条信息后续发送一条信息。
     *
     * @see IMessageResponseData
     * @param messageList 消息内容列表
     * @param data 要回复的消息数据
     */
    TaskFuture<List<? extends IMessageResponseData>> sendMessageWithFuture(List<ITeaNekoMessage> messageList, ITeaNekoMessageData data);

    /**
     * 根据信息列表发送。
     *
     * @param messageList 消息内容列表
     * @param data 要回复的消息数据
     */
    void sendMessage(List<ITeaNekoMessage> messageList, ITeaNekoMessageData data);

    /**
     * 发送文本信息。
     *
     * @param text 消息内容
     * @param data 要回复的消息数据
     */
    void sendTextMessage(String text, ITeaNekoMessageData data);

    /**
     * 发送 at + reply 消息。
     * 如果是私聊信息，则只会发送 reply 消息，不会发送 at 消息。
     *
     * @param message 消息内容
     * @param data    要回复的消息数据
     */
    void sendAtReplyMessage(String message, ITeaNekoMessageData data);

    /**
     * 发送 reply 消息。
     *
     * @param message 消息内容
     * @param data    要回复的消息数据
     */
    void sendReplyMessage(String message, ITeaNekoMessageData data);

    /**
     * 获取一个 {@link IForwardMessageBuilder}，用于构建 node 消息。
     *
     * @param data 要回复的消息数据
     * @return 转发消息构建器
     */
    IForwardMessageBuilder getForwardBuilder(ITeaNekoMessageData data);
}
