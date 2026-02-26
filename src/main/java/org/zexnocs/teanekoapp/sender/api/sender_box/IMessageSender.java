package org.zexnocs.teanekoapp.sender.api.sender_box;

import org.zexnocs.teanekoapp.message.api.ITeaNekoMessage;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageBuilder;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekoapp.message.api.TeaNekoMessageType;
import org.zexnocs.teanekoapp.response.api.IMessageResponseData;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;

import java.time.Duration;
import java.util.List;

/**
 * 根据接收到的 {@link ITeaNekoMessageData} 类型来快速发送消息的发送器接口。
 * <p>一般都会实现 {@link org.zexnocs.teanekoapp.sender.api.ISender} 接口。
 *
 * @author zExNocs
 * @date 2026/02/24
 * @since 4.0.9
 */
public interface IMessageSender {
    /**
     * 获取 message builder，用于构建消息内容。
     *
     * @return 消息构建器
     */
    ITeaNekoMessageBuilder getMessageBuilder();

    /**
     * 根据信息列表发送，并获取 task future 用于后续操作，例如发送一条信息后续发送一条信息。
     * 一般 future 是新创一个 future 来接收到 {@link org.zexnocs.teanekoapp.sender.interfaces.ISenderService} 的结果后使用
     * {@code .whenComplete()} 来完成这个 future。
     *
     * @see IMessageResponseData
     * @param messageList 消息内容列表
     * @param data 要回复的消息数据
     * @param delay 延迟发送的时间，单位为毫秒
     * @return 发送消息的 future，可以通过该 future 来获取发送结果或者进行后续操作
     */
    TaskFuture<? extends IMessageResponseData> sendMessageWithFuture(List<ITeaNekoMessage> messageList,
                                                                     ITeaNekoMessageData data,
                                                                     Duration delay);

    /**
     * 根据信息列表发送，并获取 task future 用于后续操作，例如发送一条信息后续发送一条信息。
     *
     * @see IMessageResponseData
     * @param messageList 消息内容列表
     * @param data 要回复的消息数据
     * @return 发送消息的 future，可以通过该 future 来获取发送结果或者进行后续操作
     */
    default TaskFuture<? extends IMessageResponseData> sendMessageWithFuture(List<ITeaNekoMessage> messageList,
                                                                             ITeaNekoMessageData data) {
        return sendMessageWithFuture(messageList, data, Duration.ZERO);
    }

    /**
     * 根据信息列表发送。
     *
     * @param messageList 消息内容列表
     * @param data 要回复的消息数据
     * @param delay 延迟发送的时间，单位为毫秒
     */
    default void sendMessage(List<ITeaNekoMessage> messageList, ITeaNekoMessageData data, Duration delay) {
        sendMessageWithFuture(messageList, data, delay)
                .finish();
    }

    /**
     * 根据信息列表发送。
     *
     * @param messageList 消息内容列表
     * @param data 要回复的消息数据
     */
    default void sendMessage(List<ITeaNekoMessage> messageList, ITeaNekoMessageData data) {
        sendMessage(messageList, data, Duration.ZERO);
    }

    /**
     * 发送文本信息。
     *
     * @param text 消息内容
     * @param data 要回复的消息数据
     */
    default void sendTextMessage(String text, ITeaNekoMessageData data) {
        var messageBuilder = getMessageBuilder();
        sendMessage(messageBuilder
                .addTextMessage(text)
                .build(), data);
    }

    /**
     * 发送 at + reply 消息。
     * 如果是私聊信息，则只会发送 reply 消息，不会发送 at 消息。
     *
     * @param text     消息内容
     * @param data    要回复的消息数据
     */
    default void sendAtReplyMessage(String text, ITeaNekoMessageData data) {
        var senderData = data.getUserData();
        var messageBuilder = getMessageBuilder();
        // 如果有 platformId 且是群消息，则添加 at 消息
        if (data.getMessageType().equals(TeaNekoMessageType.GROUP)) {
            var userId = senderData.getUserIdInPlatform();
            if(userId != null) {
                messageBuilder.addAtMessage(userId);
            }
        }
        sendMessage(messageBuilder
                .addReplyMessage(data.getMessageId())
                .addTextMessage(text)
                .build(), data);
    }

    /**
     * 发送 reply 消息。
     *
     * @param message 消息内容
     * @param data    要回复的消息数据
     */
    default void sendReplyMessage(String message, ITeaNekoMessageData data) {
        sendMessage(getMessageBuilder()
                .addReplyMessage(data.getMessageId())
                .addTextMessage(message)
                .build(), data);
    }

    /**
     * 发送 at 消息。
     * 如果是私聊信息，则只会发送 reply 消息，不会发送 at 消息。
     * @param text     消息内容
     * @param data    要回复的消息数据
     */
    default void sendAtMessage(String text, ITeaNekoMessageData data) {
        var senderData = data.getUserData();
        var messageBuilder = getMessageBuilder();
        // 如果有 platformId 且是群消息，则添加 at 消息
        if (data.getMessageType().equals(TeaNekoMessageType.GROUP)) {
            var userId = senderData.getUserIdInPlatform();
            if (userId != null) {
                messageBuilder.addAtMessage(userId);
            }
        }
        sendMessage(messageBuilder
                .addTextMessage(text)
                .build(), data);
    }

    /**
     * 获取一个 {@link IForwardMessageBuilder}，用于构建 node 消息。
     *
     * @param data 要回复的消息数据
     * @return 转发消息构建器
     */
    IForwardMessageBuilder getForwardBuilder(ITeaNekoMessageData data);
}
