package org.zexnocs.teanekoclient.onebot.sender.message;
import org.springframework.stereotype.Component;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekoapp.sender.api.sender_box.IEasyMessageSenderBuilder;
import org.zexnocs.teanekoapp.sender.api.sender_box.IForwardMessageSenderBuilder;
import org.zexnocs.teanekoapp.sender.api.sender_box.IMessageSender;

import java.util.Objects;

/**
 * 符合 Onebot 规范的消息发送器。
 *
 * @author zExNocs
 * @date 2026/03/04
 * @since 4.0.12
 */
@Component("Onebot-MessageSender")
public class OnebotMessageSender implements IMessageSender {
    private final PrivateForwardMessageSender privateForwardMessageSender;
    private final GroupForwardMessageSender groupForwardMessageSender;
    private final PrivateMessageSender privateMessageSender;
    private final GroupMessageSender groupMessageSender;

    public OnebotMessageSender(PrivateForwardMessageSender privateForwardMessageSender,
                               GroupForwardMessageSender groupForwardMessageSender,
                               PrivateMessageSender privateMessageSender,
                               GroupMessageSender groupMessageSender) {
        this.privateForwardMessageSender = privateForwardMessageSender;
        this.groupForwardMessageSender = groupForwardMessageSender;
        this.privateMessageSender = privateMessageSender;
        this.groupMessageSender = groupMessageSender;
    }

    /**
     * 获取一个 {@link IForwardMessageSenderBuilder}，用于构建 node 消息。
     *
     * @param token 发送器的 token，用于识别发送环境
     * @param data  要回复的消息数据
     * @return 转发消息构建器
     */
    @Override
    public IForwardMessageSenderBuilder getForwardBuilder(String token, ITeaNekoMessageData data) {
        var userData = data.getUserData();
        return switch (data.getMessageType()) {
            case PRIVATE, PRIVATE_TEMP -> privateForwardMessageSender.getBuilder(token,
                    Long.parseLong(Objects.requireNonNull(userData.getUserIdInPlatform())));
            case GROUP -> groupForwardMessageSender.getBuilder(token,
                    Long.parseLong(Objects.requireNonNull(userData.getGroupId())));
            default -> null;
        };
    }

    /**
     * 获取一个 {@link IEasyMessageSenderBuilder}，用于构造一般 message 信息并发送。
     *
     * @param token 发送器的 token，用于识别发送环境
     * @param data  要回复的消息数据
     * @return 一般消息构建器
     */
    @Override
    public IEasyMessageSenderBuilder getEasyBuilder(String token, ITeaNekoMessageData data) {
        return switch (data.getMessageType()) {
            case PRIVATE, PRIVATE_TEMP -> privateMessageSender.getBuilder(data, token);
            case GROUP -> groupMessageSender.getBuilder(data, token);
            default -> null;
        };
    }
}
