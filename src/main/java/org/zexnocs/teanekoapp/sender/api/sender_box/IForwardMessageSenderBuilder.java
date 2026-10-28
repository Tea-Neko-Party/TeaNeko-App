package org.zexnocs.teanekoapp.sender.api.sender_box;

import org.zexnocs.teanekoapp.message.api.ITeaNekoContent;
import org.zexnocs.teanekoapp.response.api.IMessageSendResponseData;
import org.zexnocs.teanekoapp.sender.MessageSendStages;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * 用于构建 node 信息的接口。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.9
 * @version 4.5.0-l
 */
public interface IForwardMessageSenderBuilder {
    /**
     * 发送转发消息。
     */
    default void send() {
        sendWithFuture().finish();
    }

    /**
     * 发送转发信息 with future
     *
     * @return 发送结果的 future
     */
    TaskFuture<IMessageSendResponseData> sendWithFuture();

    /**
     * 等待发送回执，发送异常或空回执都会使 Stage 失败。
     *
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    default CompletionStage<Void> sendAsync() {
        return MessageSendStages.awaitResponse(sendWithFuture());
    }

    /**
     * 以分段的方式发送消息。
     *
     * @param partSize 每段的大小
     */
    void sendByPart(int partSize);

    /**
     * 顺序发送全部分段并等待每段回执；失败后停止剩余分段。
     * 空消息列表直接完成，partSize 必须为正数。
     *
     * @param partSize 每个分段包含的最大条目数
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    CompletionStage<Void> sendByPartAsync(int partSize);


    /**
     * 配置发送延迟。
     * 默认没有延迟，即立即发送。
     *
     * @param delay 发送延迟
     * @return 当前构建器，供调用方继续组合后提交
     */
    IForwardMessageSenderBuilder setDelay(Duration delay);

    /**
     * 设置 retry 次数。
     * 默认 8 次。
     *
     * @param count retry 次数
     * @return 当前构建器，供调用方继续组合后提交
     */
    IForwardMessageSenderBuilder setRetryCount(int count);

    /**
     * 设置 retry 间隔。
     * 默认 200ms
     *
     * @param interval retry 间隔
     * @return 当前构建器，供调用方继续组合后提交
     */
    IForwardMessageSenderBuilder setRetryInterval(Duration interval);

    /**
     * 是否启用发送失败后记录到数据库中。
     * 默认开启
     *
     * @param recordFailed 是否记录发送失败的消息到数据库中，默认为 true
     * @return 当前构建器，供调用方继续组合后提交
     */
    IForwardMessageSenderBuilder setRecordFailed(boolean recordFailed);

    /**
     * 获取 bot name
     *
     * @return 当前发送器提供的机器人显示名称
     */
    String getBotName();

    /**
     * 获取 bot ID
     *
     * @return 当前发送器提供的机器人平台标识
     */
    String getBotId();

    /**
     * 设置外显
     *
     * @param prompt 外显文本
     * @return 当前构建器，供调用方继续组合后提交
     */
    IForwardMessageSenderBuilder setPrompt(String prompt);

    /**
     * 设置底下文本
     *
     * @param summary 底下文本
     * @return 当前构建器，供调用方继续组合后提交
     */
    IForwardMessageSenderBuilder setSummary(String summary);

    /**
     * 设置内容
     *
     * @param source 内容
     * @return 当前构建器，供调用方继续组合后提交
     */
    IForwardMessageSenderBuilder setSource(String source);

    /**
     * 使用 bot 作为发送者发送该消息。
     *
     * @param message 消息内容
     * @return 当前构建器，供调用方继续组合后提交
     */
    default IForwardMessageSenderBuilder addBotText(String message) {
        return addText(getBotId(), getBotName(), message);
    }

    /**
     * 添加一群文本
     *
     * @param textList 消息内容列表
     * @return 当前构建器，供调用方继续组合后提交
     */
    default IForwardMessageSenderBuilder addBotAllText(List<String> textList) {
        return addAllText(getBotId(), getBotName(), textList);
    }

    /**
     * 添加一个消息
     *
     * @param messageList 消息内容列表
     * @return 当前构造器实例
     */
    default IForwardMessageSenderBuilder addBotList(List<ITeaNekoContent> messageList) {
        return addList(getBotId(), getBotName(), messageList);
    }

    /**
     * 添加一群消息
     *
     * @param messageListList 消息内容列表
     * @return 当前构建器，供调用方继续组合后提交
     */
    default IForwardMessageSenderBuilder addBotAllList(List<List<ITeaNekoContent>> messageListList) {
        return addAllList(getBotId(), getBotName(), messageListList);
    }

    /**
     * 添加一个简单的消息
     *
     * @param userId   用户 ID
     * @param nickname 用户昵称
     * @param message  消息内容
     * @return 当前构建器，供调用方继续组合后提交
     */
    IForwardMessageSenderBuilder addText(String userId, String nickname, String message);

    /**
     * 添加一群文本
     *
     * @param userId   用户 ID
     * @param nickname 用户昵称
     * @param textList 消息内容列表
     * @return 当前构建器，供调用方继续组合后提交
     */
    default IForwardMessageSenderBuilder addAllText(String userId, String nickname, List<String> textList) {
        for (var item : textList) {
            addText(userId, nickname, item);
        }
        return this;
    }

    /**
     * 添加一个消息
     *
     * @param userId      用户 ID
     * @param nickname    用户昵称
     * @param messageList 消息内容列表
     * @return 当前构造器实例
     */
    IForwardMessageSenderBuilder addList(String userId, String nickname, List<ITeaNekoContent> messageList);

    /**
     * 添加一群消息
     *
     * @param userId          用户 ID
     * @param nickname        用户昵称
     * @param messageListList 消息内容列表
     * @return 当前构建器，供调用方继续组合后提交
     */
    default IForwardMessageSenderBuilder addAllList(String userId, String nickname, List<List<ITeaNekoContent>> messageListList) {
        for (var item : messageListList) {
            addList(userId, nickname, item);
        }
        return this;
    }
}
