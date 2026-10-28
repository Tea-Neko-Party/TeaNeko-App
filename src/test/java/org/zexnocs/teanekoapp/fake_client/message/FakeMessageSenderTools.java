package org.zexnocs.teanekoapp.fake_client.message;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.zexnocs.teanekoapp.client.tools.IMessageSenderTools;
import org.zexnocs.teanekoapp.fake_client.FakeClient;
import org.zexnocs.teanekoapp.fake_client.FakeMessageSendResponseData;
import org.zexnocs.teanekoapp.fake_client.FakeSendData;
import org.zexnocs.teanekoapp.fake_client.FakeTaskFutures;
import org.zexnocs.teanekoapp.message.DefaultTeaNekoContentListBuilder;
import org.zexnocs.teanekoapp.message.api.ITeaNekoContent;
import org.zexnocs.teanekoapp.message.api.ITeaNekoContentListBuilder;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekoapp.response.api.IMessageSendResponseData;
import org.zexnocs.teanekoapp.sender.MessageSendStages;
import org.zexnocs.teanekoapp.sender.api.sender_box.IEasyMessageSenderBuilder;
import org.zexnocs.teanekoapp.sender.api.sender_box.IForwardMessageSenderBuilder;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.logger.ILogger;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * fake message sender 工具箱
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.2.3
 * @version 4.5.0-l
 */
@Component
@RequiredArgsConstructor
public class FakeMessageSenderTools implements IMessageSenderTools {
    private final NormalMessageSender normalMessageSender;
    private final FakeClient client;
    private final ILogger logger;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 获取一个
     * {@link ITeaNekoContentListBuilder}
     * 用于快速构造消息列表。
     *
     * @return 独立的测试消息内容构建器
     */
    @Override
    public ITeaNekoContentListBuilder getMsgListBuilder() {
        return DefaultTeaNekoContentListBuilder.builder();
    }

    /**
     * 获取一个 {@link IForwardMessageSenderBuilder}，用于构建 node 消息。
     *
     * @param data 要回复的消息数据
     * @param token 原发送链路使用的状态令牌
     * @return 转发消息构建器
     */
    @Override
    public IForwardMessageSenderBuilder getForwardBuilder(String token, ITeaNekoMessageData data) {
        return switch (data.getMessageType()) {
            case PRIVATE, PRIVATE_TEMP -> new ForwardBuilder(
                    token,
                    FakeSendData.TargetType.PRIVATE_FORWARD,
                    data.getUserData().getUserIdInPlatform(),
                    data
            );
            case GROUP -> new ForwardBuilder(
                    token,
                    FakeSendData.TargetType.GROUP_FORWARD,
                    data.getUserData().getGroupId(),
                    data
            );
            default -> null;
        };
    }

    /**
     * 使用 token 获取一个 group
     * {@link IForwardMessageSenderBuilder}
     * ，用于构建 node 消息。
     *
     * @param token 原发送链路使用的状态令牌
     * @param groupId 群组 ID
     * @return 转发消息构建器
     */
    @Override
    public IForwardMessageSenderBuilder getGroupForwardBuilder(String token, String groupId) {
        return new ForwardBuilder(token, FakeSendData.TargetType.GROUP_FORWARD, groupId, null);
    }

    /**
     * 使用 token 获取一个 private
     * {@link IForwardMessageSenderBuilder}
     * ，用于构建 node 消息。
     *
     * @param token 原发送链路使用的状态令牌
     * @param platformId 平台用户 ID
     * @return 转发消息构建器
     */
    @Override
    public IForwardMessageSenderBuilder getPrivateForwardBuilder(String token, String platformId) {
        return new ForwardBuilder(token, FakeSendData.TargetType.PRIVATE_FORWARD, platformId, null);
    }

    /**
     * 获取一个 {@link IEasyMessageSenderBuilder}，用于构造一般 message 信息并发送。
     *
     * @param data 要回复的消息数据
     * @param token 原发送链路使用的状态令牌
     * @return 一般消息构建器
     */
    @Override
    public IEasyMessageSenderBuilder getEasyBuilder(String token, ITeaNekoMessageData data) {
        return normalMessageSender.getBuilder(token, data);
    }

    /**
     * 根据平台 ID 获取 private message sender
     *
     * @param token 原发送链路使用的状态令牌
     * @param platformId 平台用户 ID
     * @return 一般消息构建器
     */
    @Override
    public IEasyMessageSenderBuilder getPrivateBuilder(String token, String platformId) {
        return normalMessageSender.getPrivateBuilder(token, platformId);
    }

    /**
     * 根据平台 ID 获取 group message sender
     *
     * @param token 原发送链路使用的状态令牌
     * @param groupId 群组 ID
     * @return 一般消息构建器
     */
    @Override
    public IEasyMessageSenderBuilder getGroupBuilder(String token, String groupId) {
        return normalMessageSender.getGroupBuilder(token, groupId);
    }

    /**
     * 在测试客户端内组合转发节点和分段发送参数，不访问真实平台。
     *
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
     * @version 4.5.0-l
     */
    private class ForwardBuilder implements IForwardMessageSenderBuilder {
        private final String token;
        private final FakeSendData.TargetType targetType;
        private final String targetId;
        private final @Nullable ITeaNekoMessageData repliedData;
        private final List<FakeSendData.ForwardNode> nodes = new ArrayList<>();
        private String prompt;
        private String summary;
        private String source;
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
        private ForwardBuilder(String token,
                               FakeSendData.TargetType targetType,
                               String targetId,
                               @Nullable ITeaNekoMessageData repliedData) {
            this.token = token;
            this.targetType = targetType;
            this.targetId = targetId;
            this.repliedData = repliedData;
        }

        /**
         * 记录当前转发节点并返回受控回执，供上层真实完成适配使用。
         *
         * @return 承载实际任务完成信号及结果的 TaskFuture；本入口不阻塞等待
         */
        @Override
        public TaskFuture<IMessageSendResponseData> sendWithFuture() {
            IMessageSendResponseData response = sendNodes(nodes);
            return FakeTaskFutures.completed(
                    logger,
                    "fake send forward message " + response.getMessageId(),
                    response
            );
        }

        /**
         * 按原便捷入口提交分段并消费终端失败，测试仍记录每个实际分段。
         *
         * @param partSize 每个分段包含的最大条目数
         * @throws IllegalArgumentException 当参数格式、范围或声明的类型约束不满足时
         */
        @Override
        public void sendByPart(int partSize) {
            if (partSize <= 0) {
                throw new IllegalArgumentException("partSize must be greater than 0");
            }
            for (int start = 0; start < nodes.size(); start += partSize) {
                var end = Math.min(start + partSize, nodes.size());
                sendNodes(nodes.subList(start, end));
            }
        }

        /**
         * 按固定节点顺序组合分段回执，等待末段且在失败后停止余下发送。
         *
         * @param partSize 每个分段包含的最大条目数
         * @return 本用例受控的完成阶段，可由测试推进成功、拒绝或异常
         */
        @Override
        public CompletionStage<Void> sendByPartAsync(int partSize) {
            // FakeClient 的发送记录在 sendNodes 返回前已完成，不模拟额外后台任务。
            return MessageSendStages.sendByPart(nodes, partSize, part -> {
                sendNodes(part);
                return CompletableFuture.completedStage(null);
            });
        }

        /**
         * 将选定节点记录为一次测试发送，保留当前目标和任务参数。
         *
         * @param nodesToSend 本次实际提交的固定转发节点
         * @return 满足本方法约束的 {@code FakeMessageSendResponseData} 结果
         */
        private FakeMessageSendResponseData sendNodes(List<FakeSendData.ForwardNode> nodesToSend) {
            var messageId = client.nextSentMessageId();
            var sendData = new FakeSendData(
                    client,
                    objectMapper,
                    messageId,
                    token,
                    targetType,
                    targetId,
                    List.of(),
                    List.copyOf(nodesToSend),
                    prompt,
                    summary,
                    source,
                    delay,
                    retryCount,
                    retryInterval,
                    recordFailed
            );
            client.send(sendData);
            return new FakeMessageSendResponseData(messageId);
        }

        /**
         * 固定本次发送任务的提交任务前的延迟，供后续真正提交时使用。
         *
         * @param delay 提交任务前的延迟
         * @return 当前构建器，供调用方继续组合后提交
         */
        @Override
        public IForwardMessageSenderBuilder setDelay(Duration delay) {
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
        public IForwardMessageSenderBuilder setRetryCount(int count) {
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
        public IForwardMessageSenderBuilder setRetryInterval(Duration interval) {
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
        public IForwardMessageSenderBuilder setRecordFailed(boolean recordFailed) {
            this.recordFailed = recordFailed;
            return this;
        }

        /**
         * 提供当前测试机器人的展示名称，供转发节点构造使用。
         *
         * @return 按本方法契约生成的文本或标识
         */
        @Override
        public String getBotName() {
            return "Fake Bot";
        }

        /**
         * 提供当前测试机器人的平台标识，供转发节点构造使用。
         *
         * @return 按本方法契约生成的文本或标识
         */
        @Override
        public String getBotId() {
            return "fake-bot";
        }

        /**
         * 固定本次发送任务的经过宿主装配的消息与本次模型选项，供后续真正提交时使用。
         *
         * @param prompt 经过宿主装配的消息与本次模型选项
         * @return 当前构建器，供调用方继续组合后提交
         */
        @Override
        public IForwardMessageSenderBuilder setPrompt(String prompt) {
            this.prompt = prompt;
            return this;
        }

        /**
         * 固定本次发送任务的转发消息摘要，供后续真正提交时使用。
         *
         * @param summary 转发消息摘要
         * @return 当前构建器，供调用方继续组合后提交
         */
        @Override
        public IForwardMessageSenderBuilder setSummary(String summary) {
            this.summary = summary;
            return this;
        }

        /**
         * 固定本次发送任务的待处理的来源，供后续真正提交时使用。
         *
         * @param source 待处理的来源数据
         * @return 当前构建器，供调用方继续组合后提交
         */
        @Override
        public IForwardMessageSenderBuilder setSource(String source) {
            this.source = source;
            return this;
        }

        /**
         * 按指定用户和显示名称追加文本转发节点，不在构建阶段发送。
         *
         * @param userId 原业务边界中的目标用户标识
         * @param nickname 用户在当前来源中的显示名称
         * @param message 需要发送或映射的消息
         * @return 当前构建器，供调用方继续组合后提交
         */
        @Override
        public IForwardMessageSenderBuilder addText(String userId, String nickname, String message) {
            var messageList = DefaultTeaNekoContentListBuilder.builder()
                    .addText(message)
                    .build();
            return addList(userId, nickname, messageList);
        }

        /**
         * 按指定用户和显示名称追加内容列表节点，保持原列表顺序。
         *
         * @param userId 原业务边界中的目标用户标识
         * @param nickname 用户在当前来源中的显示名称
         * @param messageList 保持原始顺序的消息内容列表
         * @return 当前构建器，供调用方继续组合后提交
         */
        @Override
        public IForwardMessageSenderBuilder addList(String userId,
                                                    String nickname,
                                                    List<ITeaNekoContent> messageList) {
            nodes.add(new FakeSendData.ForwardNode(userId, nickname, List.copyOf(messageList)));
            return this;
        }
    }
}
