package org.zexnocs.teanekoclient.onebot.sender.message;

import lombok.Setter;
import lombok.experimental.Accessors;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.zexnocs.teanekoapp.message.api.ITeaNekoContent;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekoapp.message.content.NodeTeaNekoContentPart;
import org.zexnocs.teanekoapp.message.content.TextTeaNekoContentPart;
import org.zexnocs.teanekoapp.response.api.IMessageSendResponseData;
import org.zexnocs.teanekoapp.sender.MessageSendFailedException;
import org.zexnocs.teanekoapp.sender.MessageSendStages;
import org.zexnocs.teanekoapp.sender.api.sender_box.IForwardMessageSenderBuilder;
import org.zexnocs.teanekoapp.sender.interfaces.ISenderService;
import org.zexnocs.teanekoclient.onebot.core.OnebotClient;
import org.zexnocs.teanekoclient.onebot.core.OnebotIdService;
import org.zexnocs.teanekoclient.onebot.data.receive.message.OnebotContent;
import org.zexnocs.teanekoclient.onebot.data.response.params.OnebotMessageSendResponseData;
import org.zexnocs.teanekoclient.onebot.data.send.params.message.GroupForwardMessageSendParamsData;
import org.zexnocs.teanekoclient.onebot.event.sent.OnebotMessageSentEvent;
import org.zexnocs.teanekoclient.onebot.sender.AbstractOnebotSender;
import org.zexnocs.teanekoclient.onebot.utils.OnebotMessageFailSendHandler;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.actuator.task.TaskStages;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

/**
 * 群转发消息发送器
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
@Component("Onebot-GroupForwardMessageSender")
public class GroupForwardMessageSender extends AbstractOnebotSender<GroupForwardMessageSendParamsData, OnebotMessageSendResponseData> {
    /// bot name
    private final String BOT_NAME;

    /// onebot 消息发送失败处理器，用于处理发送失败的情况，例如记录日志、重试等
    private final OnebotMessageFailSendHandler onebotMessageFailSendHandler;

    /// onebot ID 服务，用于获取 bot ID 等相关信息
    private final OnebotIdService onebotIdService;

    /**
     * 构造函数，初始化发送器。
     *
     * @param senderService 发送服务实例，用于将发送数据推送给客户端，并处理响应数据
     * @param client        要推送数据的客户端
     * @param mapper        mapper，建议使用 {@code @Qualifier("onebotObjectMapper") } 的 mapper
     * @param botName 发送显示名称
     * @param onebotMessageFailSendHandler 由装配方提供的 {@code OnebotMessageFailSendHandler} 协作依赖
     * @param onebotIdService 由装配方提供的 {@code OnebotIdService} 协作依赖
     */
    public GroupForwardMessageSender(ISenderService senderService,
                                     OnebotClient client,
                                     @Qualifier("onebotObjectMapper") ObjectMapper mapper,
                                     @Value("${tea-neko.bot.default-name}") String botName,
                                     OnebotMessageFailSendHandler onebotMessageFailSendHandler,
                                     OnebotIdService onebotIdService) {
        super(senderService, client, mapper);
        this.BOT_NAME = botName;
        this.onebotMessageFailSendHandler = onebotMessageFailSendHandler;
        this.onebotIdService = onebotIdService;
    }

    /**
     * 获取一个新的构造器，用于快速添加新的消息。
     * 不应该在多线程中使用同一个实例。
     *
     * @param data 要回复的消息数据
     * @param token 原发送链路使用的状态令牌
     * @return 保持当前目标和原消息来源的发送构建器
     */
    public GroupForwardMessageBuilder getBuilder(String token, @NonNull ITeaNekoMessageData data) {
        return new GroupForwardMessageBuilder(token, data);
    }

    /**
     * 获取一个新的构造器，用于快速添加新的消息。
     * 不应该在多线程中使用同一个实例。
     *
     * @param groupId 群组 ID，表示要发送消息的目标群组
     * @param token 原发送链路使用的状态令牌
     * @return 保持当前目标和原消息来源的发送构建器
     */
    public GroupForwardMessageBuilder getBuilder(String token, long groupId) {
        return new GroupForwardMessageBuilder(token, groupId);
    }

    /**
     * 一个简易构造器，用于快速添加新的消息。
     * 不应该在多线程中使用同一个实例。
     *
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.12
     * @version 4.5.0-l
     */
    @Accessors(chain = true)
    public class GroupForwardMessageBuilder implements IForwardMessageSenderBuilder {
        /// 发送区域
        private final String token;

        /// tea neko data
        @Nullable
        private final ITeaNekoMessageData messageData;

        /// 用户 id
        private final long groupId;

        /// 消息列表
        private final List<OnebotContent> messageList = new ArrayList<>();

        /// 外显
        @Setter
        private String prompt = null;

        /// 底下文本
        @Setter
        private String summary = null;

        /// 内容
        @Setter
        private String source = null;

        /// 发送延迟
        @Setter
        private Duration delay = Duration.ZERO;

        /// retry 次数
        @Setter
        private int retryCount = 8;

        /// retry 间隔
        @Setter
        private Duration retryInterval = Duration.ofMillis(200);

        /// 记录发送失败的情况
        @Setter
        private boolean recordFailed = true;

        /**
         * 固定群聊转发的目标与发送令牌；从原消息创建时继续保留其来源信息。
         *
         * @param token 原发送链路使用的状态令牌
         * @param groupId 目标群组标识
         */
        public GroupForwardMessageBuilder(String token, long groupId) {
            this.token = token;
            this.groupId = groupId;
            this.messageData = null;
        }

        /**
         * 固定群聊转发的目标与发送令牌；从原消息创建时继续保留其来源信息。
         *
         * @param token 原发送链路使用的状态令牌
         * @param messageData 用于本次处理的 {@code messageData} 数据
         * @throws NullPointerException 当必需的输入或依赖为空时
         */
        public GroupForwardMessageBuilder(String token, @NonNull ITeaNekoMessageData messageData) {
            this.token = token;
            this.groupId = Long.parseLong(Objects.requireNonNull(messageData.getUserData().getGroupId()));
            this.messageData = messageData;
        }

        /**
         * 获取 bot name
         *
         * @return 当前发送器提供的机器人显示名称
         */
        @Override
        public String getBotName() {
            return GroupForwardMessageSender.this.BOT_NAME;
        }

        /**
         * 获取 bot ID
         *
         * @return 当前发送器提供的机器人平台标识
         */
        @Override
        public String getBotId() {
            return String.valueOf(onebotIdService.getBotId());
        }

        /**
         * 发送转发信息 with future
         *
         * @return 发送结果的 future
         */
        @Override
        public TaskFuture<IMessageSendResponseData> sendWithFuture() {
            return sendPart(List.copyOf(messageList), sendOptions());
        }

        /**
         * 单次调用的配置快照；异步分段不再读取构造器的可变字段。
         *
         * @param prompt 经过宿主装配的消息与本次模型选项
         * @param source 待处理的来源数据
         * @param summary 转发消息摘要
         * @param delay 提交任务前的延迟
         * @param retryCount 允许的重试次数
         * @param retryInterval 两次重试之间的等待时长
         * @param recordFailed 是否记录任务失败
         * @author zExNocs
         * @date 2026/10/28
         * @since 4.0.0
         * @version 4.5.0-l
         */
        private record SendOptions(String prompt, String source, String summary,
                                   Duration delay, int retryCount, Duration retryInterval,
                                   boolean recordFailed) {}

        /**
         * 复制本次分段发送使用的任务参数，后续构建器修改不会改变已启动链路。
         *
         * @return 满足本方法约束的 {@code SendOptions} 结果
         */
        private SendOptions sendOptions() {
            return new SendOptions(prompt, source, summary, delay, retryCount, retryInterval, recordFailed);
        }

        /**
         * 用固定内容与配置发送一个分段，校验真实回执后才允许继续下一段。
         *
         * @param messages 保持原始顺序的统一消息列表
         * @param options 本次有效模型选项
         * @return 承载实际任务完成信号及结果的 TaskFuture；本入口不阻塞等待
         */
        private TaskFuture<IMessageSendResponseData> sendPart(List<OnebotContent> messages, SendOptions options) {
            var data = GroupForwardMessageSendParamsData.builder()
                    .groupId(groupId)
                    .messages(messages)
                    .prompt(options.prompt())
                    .source(options.source())
                    .summary(options.summary())
                    .build();
            var sendData = GroupForwardMessageSender.this.buildSendData(data);
            var future = GroupForwardMessageSender.this.sendWithFuture(
                    new OnebotMessageSentEvent<>(token, sendData, messageData),
                    options.delay(), options.retryCount(), options.retryInterval());
            if (options.recordFailed()) {
                future = onebotMessageFailSendHandler.recordFailed(GroupForwardMessageSender.class.getSimpleName(),
                        messages, future);
            }
            return future.thenApply(result -> {
                var responses = TaskStages.requireSuccess(result, "Group forward message");
                if (responses == null || responses.isEmpty() || responses.getFirst() == null) {
                    throw new MessageSendFailedException();
                }
                return responses.getFirst();
            });
        }

        /**
         * 兼容原有不等待分段的调用；每段沿用 TaskFuture 的终端错误日志。
         *
         * @param partSize 每个分段包含的最大条目数
         * @throws IllegalArgumentException 当参数格式、范围或声明的类型约束不满足时
         */
        @Override
        public void sendByPart(int partSize) {
            if (partSize <= 0) {
                throw new IllegalArgumentException("partSize must be greater than 0");
            }
            var snapshot = List.copyOf(messageList);
            var options = sendOptions();
            for (int start = 0; start < snapshot.size();) {
                int end = start + Math.min(partSize, snapshot.size() - start);
                sendPart(List.copyOf(snapshot.subList(start, end)), options).finish();
                start = end;
            }
        }

        /**
         * 冻结待发内容后串行发送分段，等待最后一段完成；失败立即停止剩余发送。
         *
         * @param partSize 每个分段包含的最大条目数
         * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
         */
        @Override
        public CompletionStage<Void> sendByPartAsync(int partSize) {
            var options = sendOptions();
            return MessageSendStages.sendByPart(messageList, partSize,
                    part -> MessageSendStages.awaitResponse(sendPart(part, options)));
        }

        /**
         * 添加一个简单的消息
         *
         * @param userId   用户 ID
         * @param nickname 用户昵称
         * @param message  消息内容
         * @return 当前构建器，供调用方继续组合后提交
         */
        @Override
        public IForwardMessageSenderBuilder addText(String userId, String nickname, String message) {
            messageList.add(OnebotContent.builder()
                    .type(NodeTeaNekoContentPart.TYPE)
                    .contentPart(NodeTeaNekoContentPart.builder()
                            .userId(userId)
                            .nickname(nickname)
                            .contents(List.of(OnebotContent.builder()
                                    .type(TextTeaNekoContentPart.TYPE)
                                    .contentPart(TextTeaNekoContentPart.builder()
                                            .text(message)
                                            .build())
                                    .build()))
                            .build())
                    .build());
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
        @Override
        public IForwardMessageSenderBuilder addList(String userId, String nickname, List<ITeaNekoContent> messageList) {
            this.messageList.add(OnebotContent.builder()
                    .type(NodeTeaNekoContentPart.TYPE)
                    .contentPart(NodeTeaNekoContentPart.builder()
                            .userId(userId)
                            .nickname(nickname)
                            .contents(List.copyOf(messageList))
                            .build())
                    .build());
            return this;
        }
    }
}
