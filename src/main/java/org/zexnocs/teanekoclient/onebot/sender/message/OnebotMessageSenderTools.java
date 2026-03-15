package org.zexnocs.teanekoclient.onebot.sender.message;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageListBuilder;
import org.zexnocs.teanekoapp.sender.api.sender_box.IEasyMessageSenderBuilder;
import org.zexnocs.teanekoapp.sender.api.sender_box.IForwardMessageSenderBuilder;
import org.zexnocs.teanekoapp.sender.api.sender_box.IMessageSenderTools;
import org.zexnocs.teanekoclient.onebot.utils.OnebotMessageListBuilder;

/**
 * 符合 Onebot 规范的消息发送器。
 *
 * @author zExNocs
 * @date 2026/03/04
 * @since 4.0.12
 */
@RequiredArgsConstructor
@Component("Onebot-MessageSender")
public class OnebotMessageSenderTools implements IMessageSenderTools {
    /// 私人消息发送器
    private final PrivateForwardMessageSender privateForwardMessageSender;
    private final PrivateMessageSender privateMessageSender;

    /// 群组消息发送器
    private final GroupForwardMessageSender groupForwardMessageSender;
    private final GroupMessageSender groupMessageSender;

    /**
     * 获取一个
     * {@link ITeaNekoMessageListBuilder}
     * 用于快速构造消息列表。
     *
     * @return {@link ITeaNekoMessageListBuilder }
     */
    @Override
    public ITeaNekoMessageListBuilder getMsgListBuilder() {
        return OnebotMessageListBuilder.builder();
    }

    /**
     * 获取一个 {@link IForwardMessageSenderBuilder}，用于构建 node 消息。
     *
     * @param data 要回复的消息数据
     * @return 转发消息构建器
     */
    @Override
    public IForwardMessageSenderBuilder getForwardBuilder(String token, ITeaNekoMessageData data) {
        var userData = data.getUserData();
        return switch (data.getMessageType()) {
            case PRIVATE, PRIVATE_TEMP -> privateForwardMessageSender.getBuilder(token, data);
            case GROUP -> groupForwardMessageSender.getBuilder(token, data);
            default -> null;
        };
    }

    /**
     * 获取一个 {@link IEasyMessageSenderBuilder}，用于构造一般 message 信息并发送。
     *
     * @param data 要回复的消息数据
     * @return 一般消息构建器
     */
    @Override
    public IEasyMessageSenderBuilder getEasyBuilder(String token, ITeaNekoMessageData data) {
        return switch (data.getMessageType()) {
            case PRIVATE, PRIVATE_TEMP -> privateMessageSender.getBuilder(token, data);
            case GROUP -> groupMessageSender.getBuilder(token, data);
            default -> null;
        };
    }
}
