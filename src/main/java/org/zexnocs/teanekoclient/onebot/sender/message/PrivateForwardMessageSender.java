package org.zexnocs.teanekoclient.onebot.sender.message;

import lombok.Setter;
import lombok.experimental.Accessors;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessage;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekoapp.message.content.NodeTeaNekoContent;
import org.zexnocs.teanekoapp.message.content.TextTeaNekoContent;
import org.zexnocs.teanekoapp.response.api.IMessageSendResponseData;
import org.zexnocs.teanekoapp.sender.api.sender_box.IForwardMessageSenderBuilder;
import org.zexnocs.teanekoapp.sender.interfaces.ISenderService;
import org.zexnocs.teanekoclient.onebot.core.OnebotClient;
import org.zexnocs.teanekoclient.onebot.core.OnebotIdService;
import org.zexnocs.teanekoclient.onebot.data.receive.message.OnebotMessage;
import org.zexnocs.teanekoclient.onebot.data.response.params.OnebotMessageSendResponseData;
import org.zexnocs.teanekoclient.onebot.data.send.params.message.PrivateForwardMessageSendParamsData;
import org.zexnocs.teanekoclient.onebot.event.sent.OnebotMessageSentEvent;
import org.zexnocs.teanekoclient.onebot.sender.AbstractOnebotSender;
import org.zexnocs.teanekoclient.onebot.utils.OnebotMessageFailSendHandler;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 私聊转发消息发送器
 *
 * @author zExNocs
 * @date 2026/03/05
 * @since 4.0.12
 */
@Service("Onebot-PrivateForwardMessageSender")
public class PrivateForwardMessageSender extends AbstractOnebotSender<PrivateForwardMessageSendParamsData, OnebotMessageSendResponseData> {
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
     */
    public PrivateForwardMessageSender(ISenderService senderService,
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
     * @return {@link PrivateForwardMessageBuilder }
     */
    public PrivateForwardMessageBuilder getBuilder(String token, ITeaNekoMessageData data) {
        return new PrivateForwardMessageBuilder(token, data);
    }

    /**
     * 获取一个新的构造器，用于快速添加新的消息。
     * 不应该在多线程中使用同一个实例。
     *
     * @param userId 用户 ID，表示要发送消息的目标用户
     * @return {@link PrivateForwardMessageBuilder }
     */
    public PrivateForwardMessageBuilder getBuilder(String token, long userId) {
        return new PrivateForwardMessageBuilder(token, userId);
    }

    /**
     * 一个简易构造器，用于快速添加新的消息。
     * 不应该在多线程中使用同一个实例。
     *
     * @author zExNocs
     * @date 2026/03/05
     * @since 4.0.12
     */
    @Accessors(chain = true)
    public class PrivateForwardMessageBuilder implements IForwardMessageSenderBuilder {
        /// 发送区域
        private final String token;

        /// tea neko data
        private final ITeaNekoMessageData messageData;

        /// 用户 id
        private final long userId;

        /// 消息列表
        private final List<OnebotMessage> messageList = new ArrayList<>();

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

        public PrivateForwardMessageBuilder(String token, long userId) {
            this.userId = userId;
            this.token = token;
            this.messageData = null;
        }

        public PrivateForwardMessageBuilder(String token, @NonNull ITeaNekoMessageData messageData) {
            this.userId = Long.parseLong(messageData.getUserData().getUserIdInPlatform());
            this.token = token;
            this.messageData = messageData;
        }

        /**
         * 获取 bot name
         *
         * @return bot name
         */
        @Override
        public String getBotName() {
            return PrivateForwardMessageSender.this.BOT_NAME;
        }

        /**
         * 获取 bot ID
         *
         * @return bot ID
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
            var data = PrivateForwardMessageSendParamsData.builder()
                    .userId(userId)
                    .messages(messageList)
                    .prompt(prompt)
                    .source(source)
                    .summary(summary)
                    .build();
            var sendData = PrivateForwardMessageSender.this.buildSendData(data);
            var future = PrivateForwardMessageSender.this.sendWithFuture(
                    new OnebotMessageSentEvent<>(token, sendData, messageData),
                    delay, retryCount, retryInterval);
            if(recordFailed) {
                future = onebotMessageFailSendHandler.recordFailed(PrivateForwardMessageSender.class.getSimpleName(),
                        messageList,
                        future);
            }
            return future.thenApply(r -> {
                if(r.isSuccess()) {
                    return r.getResult().getFirst();
                } else {
                    return null;
                }
            });
        }

        /**
         * 以分段的方式发送消息。
         * @param partSize 每段的大小
         */
        public void sendByPart(int partSize) {
            int totalSize = messageList.size();
            int totalSegments = (totalSize + partSize - 1) / partSize; // 向上取整
            for (int i = 0; i < totalSegments; i++) {
                int start = i * partSize;
                int end = Math.min(start + partSize, totalSize);
                var segment = messageList.subList(start, end);
                var data = PrivateForwardMessageSendParamsData.builder()
                        .userId(userId)
                        .messages(segment)
                        .prompt(prompt)
                        .source(source)
                        .summary(summary)
                        .build();
                var sendData = PrivateForwardMessageSender.this.buildSendData(data);
                PrivateForwardMessageSender.this.send(
                        new OnebotMessageSentEvent<>(token, sendData, messageData),
                        delay, retryCount, retryInterval);
            }
        }

        /**
         * 添加一个简单的消息
         *
         * @param userId   用户 ID
         * @param nickname 用户昵称
         * @param message  消息内容
         * @return {@link IForwardMessageSenderBuilder }
         */
        @Override
        public IForwardMessageSenderBuilder addText(String userId, String nickname, String message) {
            messageList.add(OnebotMessage.builder()
                    .type(NodeTeaNekoContent.TYPE)
                    .content(NodeTeaNekoContent.builder()
                            .userId(userId)
                            .nickname(nickname)
                            .messages(List.of(OnebotMessage.builder()
                                    .type(TextTeaNekoContent.TYPE)
                                    .content(TextTeaNekoContent.builder()
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
        public IForwardMessageSenderBuilder addList(String userId, String nickname, List<ITeaNekoMessage> messageList) {
            this.messageList.add(OnebotMessage.builder()
                    .type(NodeTeaNekoContent.TYPE)
                    .content(NodeTeaNekoContent.builder()
                            .userId(userId)
                            .nickname(nickname)
                            .messages(messageList)
                            .build())
                    .build());
            return this;
        }
    }
}