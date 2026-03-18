package org.zexnocs.teanekoapp.fake_client.message;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.zexnocs.teanekoapp.client.tools.IMessageSenderTools;
import org.zexnocs.teanekoapp.message.DefaultTeaNekoMessageListBuilder;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageListBuilder;
import org.zexnocs.teanekoapp.sender.api.sender_box.IEasyMessageSenderBuilder;
import org.zexnocs.teanekoapp.sender.api.sender_box.IForwardMessageSenderBuilder;

/**
 * fake message sender 工具箱
 *
 * @author zExNocs
 * @date 2026/03/14
 * @since 4.2.3
 */
@Component
@RequiredArgsConstructor
public class FakeMessageSenderTools implements IMessageSenderTools {
    /**
     * 获取一个
     * {@link ITeaNekoMessageListBuilder}
     * 用于快速构造消息列表。
     *
     * @return {@link ITeaNekoMessageListBuilder }
     */
    @Override
    public ITeaNekoMessageListBuilder getMsgListBuilder() {
        return DefaultTeaNekoMessageListBuilder.builder();
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
            case PRIVATE, PRIVATE_TEMP -> null;
            case GROUP -> null;
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
        return null;
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
        return null;
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
            case PRIVATE, PRIVATE_TEMP -> null;
            case GROUP -> null;
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
        return null;
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
        return null;
    }
}
