package org.zexnocs.teanekoclient.onebot.sender.message;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.zexnocs.teanekoapp.client.tools.IMessageSenderTools;
import org.zexnocs.teanekoapp.message.api.ITeaNekoContentListBuilder;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekoapp.sender.api.sender_box.IEasyMessageSenderBuilder;
import org.zexnocs.teanekoapp.sender.api.sender_box.IForwardMessageSenderBuilder;
import org.zexnocs.teanekoclient.onebot.utils.OnebotContentListBuilder;

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
     * {@link ITeaNekoContentListBuilder}
     * 用于快速构造消息列表。
     *
     * @return {@link ITeaNekoContentListBuilder }
     */
    @Override
    public ITeaNekoContentListBuilder getMsgListBuilder() {
        return OnebotContentListBuilder.builder();
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
     * 使用 token 获取一个 group
     * {@link IForwardMessageSenderBuilder}
     * ，用于构建 node 消息。
     *
     * @param token   token
     * @param groupId 群组 ID
     * @return 转发消息构建器
     */
    @Override
    public IForwardMessageSenderBuilder getGroupForwardBuilder(String token, String groupId) {
        return groupForwardMessageSender.getBuilder(token, Long.parseLong(groupId));
    }

    /**
     * 使用 token 获取一个 private
     * {@link IForwardMessageSenderBuilder}
     * ，用于构建 node 消息。
     *
     * @param token      token
     * @param platformId 平台用户 ID
     * @return 转发消息构建器
     */
    @Override
    public IForwardMessageSenderBuilder getPrivateForwardBuilder(String token, String platformId) {
        return privateForwardMessageSender.getBuilder(token, Long.parseLong(platformId));
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

    /**
     * 根据平台 ID 获取 private message sender
     *
     * @param token      token
     * @param platformId 平台用户 ID
     * @return 一般消息构建器
     */
    @Override
    public IEasyMessageSenderBuilder getPrivateBuilder(String token, String platformId) {
        return privateMessageSender.getBuilder(token, platformId);
    }

    /**
     * 根据平台 ID 获取 group message sender
     *
     * @param token   token
     * @param groupId 群组 ID
     * @return 一般消息构建器
     */
    @Override
    public IEasyMessageSenderBuilder getGroupBuilder(String token, String groupId) {
        return groupMessageSender.getBuilder(token, groupId);
    }
}
