package org.zexnocs.teanekoapp.fake_client.message;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.zexnocs.teanekoapp.fake_client.FakeClient;
import org.zexnocs.teanekoapp.fake_client.FakeMessageSendResponseData;
import org.zexnocs.teanekoapp.fake_client.FakeSendData;
import org.zexnocs.teanekoapp.fake_client.FakeTaskFutures;
import org.zexnocs.teanekoapp.message.DefaultTeaNekoContentListBuilder;
import org.zexnocs.teanekoapp.message.api.ITeaNekoContentListBuilder;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekoapp.message.api.TeaNekoMessageType;
import org.zexnocs.teanekoapp.response.api.IMessageSendResponseData;
import org.zexnocs.teanekoapp.sender.api.sender_box.IEasyMessageSenderBuilder;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.logger.ILogger;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;

/**
 * 测试客户端的普通消息发送适配器，复用原发送完成协议。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
@Component
public class NormalMessageSender {
    private final FakeClient client;
    private final ILogger logger;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 通过构造参数固定当前实例的依赖与初始状态，避免执行时再从全局环境查找协作者。
     *
     * @param client 用于本次测试的 {@code client} 数据
     * @param logger 项目日志接口
     */
    public NormalMessageSender(FakeClient client, ILogger logger) {
        this.client = client;
        this.logger = logger;
    }

    /**
     * 根据原消息数据选择测试发送目标，保留发送 token 和引用上下文。
     *
     * @param token 原发送链路使用的状态令牌
     * @param data 本次处理的输入数据
     * @return 当前构建器，供调用方继续组合后提交
     */
    public IEasyMessageSenderBuilder getBuilder(String token, ITeaNekoMessageData data) {
        var userData = data.getUserData();
        if (data.getMessageType() == TeaNekoMessageType.GROUP) {
            return getGroupBuilder(token, userData.getGroupId(), data);
        }
        return getPrivateBuilder(token, userData.getUserIdInPlatform(), data);
    }

    /**
     * 建立指向指定私聊用户的测试构建器，可显式保留引用原消息。
     *
     * @param token 原发送链路使用的状态令牌
     * @param platformId 目标用户的平台标识
     * @return 当前构建器，供调用方继续组合后提交
     */
    public IEasyMessageSenderBuilder getPrivateBuilder(String token, String platformId) {
        return getPrivateBuilder(token, platformId, null);
    }

    /**
     * 建立指向指定群的测试构建器，可显式保留引用原消息。
     *
     * @param token 原发送链路使用的状态令牌
     * @param groupId 目标群组标识
     * @return 当前构建器，供调用方继续组合后提交
     */
    public IEasyMessageSenderBuilder getGroupBuilder(String token, String groupId) {
        return getGroupBuilder(token, groupId, null);
    }

    /**
     * 建立指向指定私聊用户的测试构建器，可显式保留引用原消息。
     *
     * @param token 原发送链路使用的状态令牌
     * @param platformId 目标用户的平台标识
     * @param repliedData 本次发送所引用的原消息
     * @return 当前构建器，供调用方继续组合后提交
     */
    private IEasyMessageSenderBuilder getPrivateBuilder(String token,
                                                       String platformId,
                                                       @Nullable ITeaNekoMessageData repliedData) {
        return new Builder(token, FakeSendData.TargetType.PRIVATE, platformId, repliedData);
    }

    /**
     * 建立指向指定群的测试构建器，可显式保留引用原消息。
     *
     * @param token 原发送链路使用的状态令牌
     * @param groupId 目标群组标识
     * @param repliedData 本次发送所引用的原消息
     * @return 当前构建器，供调用方继续组合后提交
     */
    private IEasyMessageSenderBuilder getGroupBuilder(String token,
                                                     String groupId,
                                                     @Nullable ITeaNekoMessageData repliedData) {
        return new Builder(token, FakeSendData.TargetType.GROUP, groupId, repliedData);
    }

    /**
     * 持有单次模拟发送的目标、引用内容与任务配置。
     *
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
     * @version 4.5.0-l
     */
    private class Builder implements IEasyMessageSenderBuilder {
        private final String token;
        private final FakeSendData.TargetType targetType;
        private final String targetId;
        private final ITeaNekoContentListBuilder messageListBuilder = DefaultTeaNekoContentListBuilder.builder();
        private final @Nullable ITeaNekoMessageData repliedData;
        private Duration delay = Duration.ZERO;
        private int retryCount = 8;
        private Duration retryInterval = Duration.ofMillis(200);
        private boolean recordFailed = true;

        /**
         * 通过构造参数固定当前实例的依赖与初始状态，避免执行时再从全局环境查找协作者。
         *
         * @param token 原发送链路使用的状态令牌
         * @param targetType 用于本次测试的 {@code targetType} 数据
         * @param targetId 原业务规则允许操作的目标标识
         * @param repliedData 本次发送所引用的原消息
         */
        private Builder(String token,
                        FakeSendData.TargetType targetType,
                        String targetId,
                        @Nullable ITeaNekoMessageData repliedData) {
            this.token = token;
            this.targetType = targetType;
            this.targetId = targetId;
            this.repliedData = repliedData;
        }

        /**
         * 提供当前发送器独立的内容构建器，发送前仍由调用方组合消息。
         *
         * @return 满足本方法约束的 {@code ITeaNekoContentListBuilder} 结果
         */
        @Override
        public ITeaNekoContentListBuilder getMessageListBuilder() {
            return messageListBuilder;
        }

        /**
         * 提供本次发送关联的原消息，供引用与提醒内容生成使用。
         *
         * @return 满足本方法约束的 {@code ITeaNekoMessageData} 结果
         */
        @Override
        public @Nullable ITeaNekoMessageData getRepliedData() {
            return repliedData;
        }

        /**
         * 提交当前测试消息并返回真实受控回执，支持上层等待发送完成。
         *
         * @return 承载实际任务完成信号及结果的 TaskFuture；本入口不阻塞等待
         */
        @Override
        public TaskFuture<? extends IMessageSendResponseData> sendWithFuture() {
            var messageId = client.nextSentMessageId();
            var sendData = new FakeSendData(
                    client,
                    objectMapper,
                    messageId,
                    token,
                    targetType,
                    targetId,
                    messageListBuilder.build(),
                    List.of(),
                    null,
                    null,
                    null,
                    delay,
                    retryCount,
                    retryInterval,
                    recordFailed
            );
            client.send(sendData);
            return FakeTaskFutures.completed(
                    logger,
                    "fake send message " + messageId,
                    new FakeMessageSendResponseData(messageId)
            );
        }

        /**
         * 固定本次发送任务的提交任务前的延迟，供后续真正提交时使用。
         *
         * @param delay 提交任务前的延迟
         * @return 当前构建器，供调用方继续组合后提交
         */
        @Override
        public IEasyMessageSenderBuilder setDelay(Duration delay) {
            this.delay = delay;
            return this;
        }

        /**
         * 固定本次发送任务的用于本次测试的 {@code count} ，供后续真正提交时使用。
         *
         * @param count 用于本次测试的 {@code count} 数据
         * @return 当前构建器，供调用方继续组合后提交
         */
        @Override
        public IEasyMessageSenderBuilder setRetryCount(int count) {
            this.retryCount = count;
            return this;
        }

        /**
         * 固定本次发送任务的用于本次测试的 {@code interval} ，供后续真正提交时使用。
         *
         * @param interval 用于本次测试的 {@code interval} 数据
         * @return 当前构建器，供调用方继续组合后提交
         */
        @Override
        public IEasyMessageSenderBuilder setRetryInterval(Duration interval) {
            this.retryInterval = interval;
            return this;
        }

        /**
         * 固定本次发送任务的是否记录任务失败，供后续真正提交时使用。
         *
         * @param recordFailed 是否记录任务失败
         * @return 当前构建器，供调用方继续组合后提交
         */
        @Override
        public IEasyMessageSenderBuilder setRecordFailed(boolean recordFailed) {
            this.recordFailed = recordFailed;
            return this;
        }
    }
}
