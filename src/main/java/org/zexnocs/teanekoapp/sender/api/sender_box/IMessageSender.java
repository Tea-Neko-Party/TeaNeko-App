package org.zexnocs.teanekoapp.sender.api.sender_box;

import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageListBuilder;

/**
 * 根据接收到的 {@link ITeaNekoMessageData} 类型来快速发送消息的发送器接口。
 * <p>一般都会实现 {@link org.zexnocs.teanekoapp.sender.api.ISender} 接口。
 *
 * @author zExNocs
 * @date 2026/02/24
 * @since 4.0.9
 */
public interface IMessageSender {
    /**
     * 获取一个
     * {@link ITeaNekoMessageListBuilder}
     * 用于快速构造消息列表。
     *
     * @return {@link ITeaNekoMessageListBuilder }
     */
    ITeaNekoMessageListBuilder getMsgListBuilder();

    /**
     * 获取一个 {@link IForwardMessageSenderBuilder}，用于构建 node 消息。
     *
     * @param token 发送器的 token，用于识别发送环境
     * @param data  要回复的消息数据
     * @return 转发消息构建器
     */
    IForwardMessageSenderBuilder getForwardBuilder(String token, ITeaNekoMessageData data);

    /**
     * 获取一个 {@link IEasyMessageSenderBuilder}，用于构造一般 message 信息并发送。
     *
     * @param token 发送器的 token，用于识别发送环境
     * @param data  要回复的消息数据
     * @return 一般消息构建器
     */
    IEasyMessageSenderBuilder getEasyBuilder(String token, ITeaNekoMessageData data);
}
