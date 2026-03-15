package org.zexnocs.teanekoclient.onebot.sender.message;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageListBuilder;
import org.zexnocs.teanekoapp.response.api.IMessageSendResponseData;
import org.zexnocs.teanekoapp.sender.api.sender_box.IEasyMessageSenderBuilder;
import org.zexnocs.teanekoapp.sender.interfaces.ISenderService;
import org.zexnocs.teanekoclient.onebot.core.OnebotClient;
import org.zexnocs.teanekoclient.onebot.data.response.params.OnebotMessageSendResponseData;
import org.zexnocs.teanekoclient.onebot.data.send.params.message.GroupMsgSendParamsData;
import org.zexnocs.teanekoclient.onebot.event.sent.OnebotMessageSentEvent;
import org.zexnocs.teanekoclient.onebot.sender.AbstractOnebotSender;
import org.zexnocs.teanekoclient.onebot.utils.OnebotMessageFailSendHandler;
import org.zexnocs.teanekoclient.onebot.utils.OnebotMessageListBuilder;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;

/**
 * 群消息发送器，负责发送群消息。
 * <br>4.1.3: 新增使用 groupId 获取 builder 的方法，方便没有 repliedData 的情况下发送消息。
 *
 * @author zExNocs
 * @date 2026/03/04
 * @since 4.0.12
 * @version 4.1.3
 */
@Component("Onebot-GroupMessageSender")
public class GroupMessageSender extends AbstractOnebotSender<GroupMsgSendParamsData, OnebotMessageSendResponseData> {
    /// onebot 消息发送失败处理器，用于处理发送失败的情况，例如记录日志、重试等
    private final OnebotMessageFailSendHandler onebotMessageFailSendHandler;

    /**
     * 构造函数，初始化发送器。
     *
     * @param senderService 发送服务实例，用于将发送数据推送给客户端，并处理响应数据
     * @param client        要推送数据的客户端
     * @param mapper        mapper，建议使用 {@code @Qualifier("onebotObjectMapper") } 的 mapper
     */
    @Autowired
    public GroupMessageSender(ISenderService senderService,
                              OnebotClient client,
                              @Qualifier("onebotObjectMapper") ObjectMapper mapper,
                              OnebotMessageFailSendHandler onebotMessageFailSendHandler) {
        super(senderService, client, mapper);
        this.onebotMessageFailSendHandler = onebotMessageFailSendHandler;
    }

    /**
     * 使用 {@link ITeaNekoMessageData} 获取一个 {@link GroupEasyMessageSenderBuilder}，用于构建一般 message 信息并发送。
     *
     * @param data 要回复的消息数据
     */
    public GroupEasyMessageSenderBuilder getBuilder(String token, @NonNull ITeaNekoMessageData data) {
        return new GroupEasyMessageSenderBuilder(token, OnebotMessageListBuilder.builder(), data);
    }

    /**
     * 使用群号获取一个 {@link GroupEasyMessageSenderBuilder}，用于构建一般 message 信息并发送。
     *
     * @param groupId 要发送消息的群号，如果没有 repliedData，则需要提供 groupId 来发送消息
     */
    public GroupEasyMessageSenderBuilder getBuilder(String token, String groupId) {
        return new GroupEasyMessageSenderBuilder(token, OnebotMessageListBuilder.builder(), groupId);
    }

    /**
     * onebot 的 group 消息发送器构建器，用于构建一个发送器实例，并提供相关的发送配置，例如发送延迟、重试次数等。
     *
     * @author zExNocs
     * @date 2026/03/05
     * @since 4.0.12
     */
    @Accessors(chain = true)
    public class GroupEasyMessageSenderBuilder implements IEasyMessageSenderBuilder {
        /// 发送器所处的环境
        private final String token;

        /// 消息构建器
        @Getter
        private final ITeaNekoMessageListBuilder messageListBuilder;

        /// 当前发送器所回复的发送数据对象，用于获取发送相关的信息
        @Getter
        @Nullable
        private final ITeaNekoMessageData repliedData;

        /// 如果没有 repliedData，则需要提供 groupId 来发送消息
        private final String groupId;

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
         * 使用 data 的构造函数，初始化构建器。
         *
         * @param messageListBuilder 消息列表构建器，用于构建要发送的消息列表
         * @param repliedData        要回复的消息数据对象，用于获取发送相关的信息，例如发送环境等
         */
        public GroupEasyMessageSenderBuilder(String token,
                                             ITeaNekoMessageListBuilder messageListBuilder,
                                             @NonNull ITeaNekoMessageData repliedData) {
            this.messageListBuilder = messageListBuilder;
            this.repliedData = repliedData;
            this.groupId = repliedData.getUserData().getGroupId();
            this.token = token;
        }

        /**
         * 使用 group ID 的构造函数，初始化构建器。
         *
         * @param messageListBuilder 消息列表构建器，用于构建要发送的消息列表
         * @param groupId            要发送消息的群号，如果没有 repliedData，则需要提供 groupId 来发送消息
         */
        public GroupEasyMessageSenderBuilder(String token,
                                             ITeaNekoMessageListBuilder messageListBuilder,
                                             String groupId) {
            this.messageListBuilder = messageListBuilder;
            this.repliedData = null;
            this.groupId = groupId;
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
        public TaskFuture<? extends IMessageSendResponseData> sendWithFuture() {
            var messages = messageListBuilder.build();
            var paramsData = GroupMsgSendParamsData.builder()
                    .groupId(Long.parseLong(groupId))
                    .messageList(messages)
                    .build();
            var sendData = buildSendData(paramsData);
            var future = GroupMessageSender.this.sendWithFuture(
                    new OnebotMessageSentEvent<>(token, sendData, repliedData),
                    delay, retryCount, retryInterval);
            if(recordFailed) {
                future = onebotMessageFailSendHandler.recordFailed(GroupMessageSender.class.getSimpleName(), messages, future);
            }
            return future.thenApply(r -> {
                if(r.isSuccess()) {
                    var resultData = r.getResult();
                    return resultData == null ? null :
                            (resultData.isEmpty() ? null : resultData.getFirst());
                } else {
                    return null;
                }
            });
        }
    }
}
