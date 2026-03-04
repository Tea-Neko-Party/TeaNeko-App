package org.zexnocs.teanekoclient.onebot.sender.message;


import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessage;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageListBuilder;
import org.zexnocs.teanekoapp.response.api.IMessageResponseData;
import org.zexnocs.teanekoapp.sender.api.sender_box.IEasyMessageSenderBuilder;
import org.zexnocs.teanekoapp.sender.interfaces.ISenderService;
import org.zexnocs.teanekoclient.onebot.core.OnebotClient;
import org.zexnocs.teanekoclient.onebot.data.response.params.OnebotMessageResponseData;
import org.zexnocs.teanekoclient.onebot.data.send.params.message.PrivateMsgSendParamsData;
import org.zexnocs.teanekoclient.onebot.sender.AbstractOnebotSender;
import org.zexnocs.teanekoclient.onebot.utils.OnebotMessageFailSendHandler;
import org.zexnocs.teanekoclient.onebot.utils.OnebotMessageListBuilder;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskResult;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;

/**
 * 符合 Onebot 协议的私聊消息发送器，负责发送私聊消息。
 *
 * @author zExNocs
 * @date 2026/03/04
 * @since 4.0.12
 */
@Service("Onebot-PrivateMessageSender")
public class PrivateMessageSender extends AbstractOnebotSender<PrivateMsgSendParamsData, OnebotMessageResponseData> {
    /// onebot 消息发送失败处理器，用于处理发送失败的情况，例如记录日志、重试等
    private final OnebotMessageFailSendHandler onebotMessageFailSendHandler;

    /**
     * 构造函数，初始化发送器。
     *
     * @param senderService 发送服务实例，用于将发送数据推送给客户端，并处理响应数据
     * @param client        要推送数据的客户端
     * @param mapper        mapper，建议使用 {@code @Qualifier("onebotObjectMapper") } 的 mapper
     */
    public PrivateMessageSender(ISenderService senderService,
                                OnebotClient client,
                                @Qualifier("onebotObjectMapper") ObjectMapper mapper,
                                OnebotMessageFailSendHandler onebotMessageFailSendHandler) {
        super(senderService, client, mapper);
        this.onebotMessageFailSendHandler = onebotMessageFailSendHandler;
    }


    /**
     * 发送私聊信息
     *
     * @param token        发送器发送环境的标识符
     * @param messageList   消息列表
     * @param userId        用户 ID
     * @param delay         发送延迟
     * @param maxRetryCount 最大重试次数
     * @param retryDelay    重试间隔
     * @return 发送结果的 future，可以通过该 future 来获取发送结果或者进行后续操作
     */
    public TaskFuture<ITaskResult<List<OnebotMessageResponseData>>> sendMessage(String token,
                                                                                List<? extends ITeaNekoMessage> messageList,
                                                                                String userId,
                                                                                Duration delay,
                                                                                int maxRetryCount,
                                                                                Duration retryDelay) {
        return sendWithFuture(token,
                PrivateMsgSendParamsData.builder()
                        .userId(Long.parseLong(userId))
                        .messageList(messageList)
                        .build(),
                delay, maxRetryCount, retryDelay);
    }

    /**
     * 获取一个 {@link PrivateEasyMessageSenderBuilder}，用于构建一般 message 信息并发送。
     *
     * @param data  要回复的消息数据
     * @param token 发送器的 token，用于识别发送环境
     */
    public PrivateEasyMessageSenderBuilder getBuilder(ITeaNekoMessageData data, String token) {
        return new PrivateEasyMessageSenderBuilder(OnebotMessageListBuilder.builder(), data, token);
    }

    /**
     * onebot 的 private 消息发送器构建器，用于构建一个发送器实例，并提供相关的发送配置，例如发送延迟、重试次数等。
     *
     * @author zExNocs
     * @date 2026/03/05
     * @since 4.0.12
     */
    @Accessors(chain = true)
    public class PrivateEasyMessageSenderBuilder implements IEasyMessageSenderBuilder {

        /// 消息构建器
        @Getter
        private final ITeaNekoMessageListBuilder messageListBuilder;

        /// 当前发送器所回复的发送数据对象，用于获取发送相关的信息
        @Getter
        private final ITeaNekoMessageData repliedData;

        /// token
        private final String token;

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
         * 构造函数，初始化构建器。
         *
         * @param messageListBuilder 消息列表构建器，用于构建要发送的消息列表
         * @param repliedData 要回复的消息数据对象，用于获取发送相关的信息，例如发送环境等
         */
        public PrivateEasyMessageSenderBuilder(ITeaNekoMessageListBuilder messageListBuilder,
                                               ITeaNekoMessageData repliedData,
                                               String token) {
            this.messageListBuilder = messageListBuilder;
            this.repliedData = repliedData;
            this.token = token;
        }


        /**
         * 根据信息列表发送，并获取 task future 用于后续操作，例如发送一条信息后续发送一条信息。<p>
         * 一般 future 是新创一个 future 来接收到 {@link ISenderService} 的结果后使用
         * {@code .whenComplete()} 来完成这个 future。
         *
         * @return 发送消息的 future，可以通过该 future 来获取发送结果或者进行后续操作
         * @see TaskFuture
         */
        @Override
        public TaskFuture<? extends IMessageResponseData> sendWithFuture() {
            var messages = messageListBuilder.build();
            var future = PrivateMessageSender.this.sendMessage(
                    token,
                    messages,
                    repliedData.getUserData().getUserIdInPlatform(),
                    delay,
                    retryCount,
                    retryInterval
            );
            if(recordFailed) {
                future = onebotMessageFailSendHandler
                        .recordFailed(GroupMessageSender.class.getSimpleName(), messages, future);
            }
            return future.thenApply(r -> {
                if(r.isSuccess()) {
                    return r.getResult().getFirst();
                } else {
                    return null;
                }
            });
        }
    }
}
