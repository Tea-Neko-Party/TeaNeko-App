package org.zexnocs.teanekoapp.fake_client.message;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.zexnocs.teanekoapp.message.DefaultTeaNekoMessageListBuilder;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageListBuilder;
import org.zexnocs.teanekoapp.sender.api.sender_box.IEasyMessageSenderBuilder;
import org.zexnocs.teanekoapp.sender.api.sender_box.IForwardMessageSenderBuilder;
import org.zexnocs.teanekoapp.sender.api.sender_box.IMessageSenderTools;

import java.util.Objects;

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
            case PRIVATE, PRIVATE_TEMP -> NormalMessageSender.getBuilder(data, token);
            case GROUP -> groupMessageSender.getBuilder(token, data);
            default -> null;
        };
    }
}
