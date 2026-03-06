package org.zexnocs.teanekoapp.sender.api.sender_box;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessage;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageListBuilder;
import org.zexnocs.teanekoapp.message.api.TeaNekoMessageType;
import org.zexnocs.teanekoapp.response.api.IMessageResponseData;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;

import java.time.Duration;
import java.util.List;

/**
 * 是一个一次性构造消息发送器的构建器接口，提供了构建消息发送器所需的方法。
 * <br> 与 {@link org.zexnocs.teanekoapp.message.api.ITeaNekoMessageListBuilder}
 * 不同的是，前者只是构造 list，后者构造 list 后包装成 {@link org.zexnocs.teanekoapp.sender.api.ISendData} 并推送事件。
 * <br> 该类与 {@link org.zexnocs.teanekoapp.message.api.ITeaNekoMessageListBuilder} 是 combination 依赖关系
 *
 * @author zExNocs
 * @date 2026/03/04
 * @since 4.0.11
 */
public interface IEasyMessageSenderBuilder {

    /**
     * 获取当前发送器的消息构建器，用于构建消息内容。
     *
     * @return {@link ITeaNekoMessageListBuilder } 消息构建器
     */
    ITeaNekoMessageListBuilder getMessageListBuilder();

    /**
     * 获取当前发送器所回复的发送数据对象，用于获取发送相关的信息，例如消息类型、目标 ID 等。
     * <p>用于快速判断发送数据中的信息，例如消息类型、目标 ID 等，以便于根据这些信息来构建不同的消息内容。
     *
     * @return {@link ITeaNekoMessageData} 发送数据对象
     */
    ITeaNekoMessageData getRepliedData();

    /**
     * 根据信息列表发送，并获取 task future 用于后续操作，例如发送一条信息后续发送一条信息。<p>
     * 一般 future 是新创一个 future 来接收到 {@link org.zexnocs.teanekoapp.sender.interfaces.ISenderService} 的结果后使用
     * {@code .whenComplete()} 来完成这个 future。
     *
     * @see TaskFuture
     * @return 发送消息的 future，可以通过该 future 来获取发送结果或者进行后续操作
     */
    TaskFuture<? extends IMessageResponseData> sendWithFuture();

    /**
     * 发送消息。
     */
    default void send() {
        sendWithFuture().finish();
    }

    /**
     * 配置发送延迟。
     * 默认没有延迟，即立即发送。
     *
     * @param delay 发送延迟
     */
    IEasyMessageSenderBuilder setDelay(Duration delay);

    /**
     * 设置 retry 次数。
     * 默认 8 次。
     *
     * @param count retry 次数
     */
    IEasyMessageSenderBuilder setRetryCount(int count);

    /**
     * 设置 retry 间隔。
     * 默认 200ms
     *
     * @param interval retry 间隔
     */
    IEasyMessageSenderBuilder setRetryInterval(Duration interval);

    /**
     * 是否启用发送失败后记录到数据库中。
     * 默认开启
     *
     * @param recordFailed 是否记录发送失败的消息到数据库中，默认为 true
     */
    IEasyMessageSenderBuilder setRecordFailed(boolean recordFailed);

    /**
     * 添加一个构造好的 {@link ITeaNekoMessage}。
     *
     * @param message 已经构造好的消息对象
     * @return 当前的构造器对象，以便于链式调用
     */
    default IEasyMessageSenderBuilder addMessage(@NonNull ITeaNekoMessage message) {
        getMessageListBuilder().addMessage(message);
        return this;
    }

    /**
     * 添加一个构造好的 {@link ITeaNekoMessage} 列表。
     *
     * @param messageList 已经构造好的消息对象列表
     * @return 当前的构造器对象，以便于链式调用
     */
    default IEasyMessageSenderBuilder addMessages(@NonNull List<? extends ITeaNekoMessage> messageList) {
        getMessageListBuilder().addMessages(messageList);
        return this;
    }

    /**
     * 添加一个文本消息。
     *
     * @param text 文本内容
     * @return 当前的构造器对象，以便于链式调用
     */
    default IEasyMessageSenderBuilder addTextMessage(@Nullable String text) {
        getMessageListBuilder().addTextMessage(text);
        return this;
    }

    /**
     * 使用图片路径发送：
     * 1. 本地路径："file://path"，如 "file://D:/a.jpg"。
     * 2. 网络路径：网络路径，如 "http://"。
     * 3. base64："base64://"，如 "base64://base64字符串"。
     *
     * @param imageUrl 图片的 URL 地址
     * @return 当前的构造器对象，以便于链式调用
     */
    default IEasyMessageSenderBuilder addImageMessage(@Nullable String imageUrl) {
        getMessageListBuilder().addImageMessage(imageUrl);
        return this;
    }

    /**
     * 添加一个 at 消息。
     *
     * @param atId 被 at 的用户 ID
     * @return 当前的构造器对象，以便于链式调用
     */
    default IEasyMessageSenderBuilder addAtMessage(@Nullable String atId) {
        getMessageListBuilder().addAtMessage(atId);
        return this;
    }

    /**
     * 添加一个 reply 消息。
     *
     * @param replyId 被回复的消息 ID
     * @return 当前的构造器对象，以便于链式调用
     */
    default IEasyMessageSenderBuilder addReplyMessage(@Nullable String replyId) {
        getMessageListBuilder().addReplyMessage(replyId);
        return this;
    }

    /**
     * 根据 data 添加一个 at 消息。
     *
     * @return 当前的构造器对象，以便于链式调用
     */
    default IEasyMessageSenderBuilder addAtMessage() {
        var data = getRepliedData();
        var senderData = data.getUserData();
        // 如果有 platformId 且是群消息，则添加 at 消息
        if (data.getMessageType().equals(TeaNekoMessageType.GROUP)) {
            var userId = senderData.getUserIdInPlatform();
            getMessageListBuilder().addAtMessage(userId);
        }
        return this;
    }

    /**
     * 根据 data 添加一个 reply 消息。
     *
     * @return 当前的构造器对象，以便于链式调用
     */
    default IEasyMessageSenderBuilder addReplyMessage() {
        getMessageListBuilder().addReplyMessage(getRepliedData().getMessageId());
        return this;
    }

    /**
     * 同时加入 reply 和 text
     *
     * @param text 文本内容
     * @return 当前的构造器对象，以便于链式调用
     */
    default IEasyMessageSenderBuilder addReplyTextMessage(String text) {
        return addReplyMessage().addTextMessage(text);
    }

    /**
     * 同时加入 at 、reply 和 text
     *
     * @param text 文本内容
     * @return 当前的构造器对象，以便于链式调用
     */
    default IEasyMessageSenderBuilder addAtReplyTextMessage(String text) {
        return addReplyMessage().addAtMessage().addTextMessage(text);
    }

    /**
     * 发送 reply + message 消息
     *
     * @param text 文本内容
     */
    default void sendReplyMessage(String text) {
        addReplyTextMessage(text).send();
    }

    /**
     * 发送 at + reply + message 消息
     *
     * @param text 文本内容
     */
    default void sendAtReplyMessage(String text) {
        addAtReplyTextMessage(text).send();
    }

    /**
     * 发送 text 消息
     *
     * @param text 文本内容
     */
    default void sendTextMessage(String text) {
        addTextMessage(text).send();
    }
}
