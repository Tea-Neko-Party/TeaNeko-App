package org.zexnocs.teanekoapp.client.tools;

import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageListBuilder;
import org.zexnocs.teanekoapp.sender.api.sender_box.IEasyMessageSenderBuilder;
import org.zexnocs.teanekoapp.sender.api.sender_box.IForwardMessageSenderBuilder;

/**
 * 根据接收到的 {@link ITeaNekoMessageData} 类型来快速发送消息的发送器接口。
 * <p>一般都会实现 {@link org.zexnocs.teanekoapp.sender.api.ISender} 接口。
 *
 * @author zExNocs
 * @date 2026/02/24
 * @since 4.0.9
 */
public interface IMessageSenderTools {
    /**
     * 获取一个
     * {@link ITeaNekoMessageListBuilder}
     * 用于快速构造消息列表。
     *
     * @return {@link ITeaNekoMessageListBuilder }
     */
    default ITeaNekoMessageListBuilder getMsgListBuilder() {
        throw new UnsupportedOperationException("该 client 不支持 msg list builder");
    }

    /**
     * 使用 token 获取一个
     * {@link IForwardMessageSenderBuilder}
     * ，用于构建 node 消息。
     *
     * @param token token
     * @param data  要回复的消息数据
     * @return 转发消息构建器
     */
    default IForwardMessageSenderBuilder getForwardBuilder(String token, ITeaNekoMessageData data) {
        throw new UnsupportedOperationException("该 client 不支持获取转发消息构建器");
    }

    /**
     * 使用 token 获取一个 group
     * {@link IForwardMessageSenderBuilder}
     * ，用于构建 node 消息。
     *
     * @param token token
     * @param groupId 群组 ID
     * @return 转发消息构建器
     */
    default IForwardMessageSenderBuilder getGroupForwardBuilder(String token, String groupId) {
        throw new UnsupportedOperationException("该 client 不支持获取群组转发消息构建器");
    }

    /**
     * 使用 token 获取一个 private
     * {@link IForwardMessageSenderBuilder}
     * ，用于构建 node 消息。
     *
     * @param token token
     * @param platformId 平台用户 ID
     * @return 转发消息构建器
     */
    default IForwardMessageSenderBuilder getPrivateForwardBuilder(String token, String platformId) {
        throw new UnsupportedOperationException("该 client 不支持获取私聊转发消息构建器");
    }

    /**
     * 使用 token 获取一个
     * {@link IEasyMessageSenderBuilder}
     * ，用于构造一般 message 信息并发送。
     *
     * @param token token
     * @param data  要回复的消息数据
     * @return 一般消息构建器
     */
    default IEasyMessageSenderBuilder getEasyBuilder(String token, ITeaNekoMessageData data) {
        throw new UnsupportedOperationException("该 client 不支持获取一般消息构建器");
    }

    /**
     * 根据平台 ID 获取 private message sender
     *
     * @param token token
     * @param platformId 平台用户 ID
     * @return 一般消息构建器
     */
    default IEasyMessageSenderBuilder getPrivateBuilder(String token, String platformId) {
        throw new UnsupportedOperationException("该 client 不支持根据平台 ID 获取私聊发送器");
    }

    /**
     * 根据平台 ID 获取 group message sender
     *
     * @param token token
     * @param groupId 群组 ID
     * @return 一般消息构建器
     */
    default IEasyMessageSenderBuilder getGroupBuilder(String token, String groupId) {
        throw new UnsupportedOperationException("该 client 不支持根据平台 ID 获取群组发送器");
    }

    // ---------------------- 默认 token 获取发射器 ----------------------

    /**
     * 使用默认 token 获取一个 group
     * {@link IForwardMessageSenderBuilder}
     * ，用于构建 node 消息。
     *
     * @param groupId 群组 ID
     * @return 转发消息构建器
     */
    default IForwardMessageSenderBuilder getGroupForwardBuilder(String groupId) {
        return getGroupForwardBuilder("default", groupId);
    }

    /**
     * 使用默认 token 获取一个 private
     * {@link IForwardMessageSenderBuilder}
     * ，用于构建 node 消息。
     *
     * @param platformId 平台用户 ID
     * @return 转发消息构建器
     */
    default IForwardMessageSenderBuilder getPrivateForwardBuilder(String platformId) {
        return getPrivateForwardBuilder("default", platformId);
    }

    /**
     * 使用默认 token 根据平台 ID 获取 private message sender
     *
     * @param platformId 平台用户 ID
     * @return 一般消息构建器
     */
    default IEasyMessageSenderBuilder getPrivateBuilder(String platformId) {
        return getPrivateBuilder("default", platformId);
    }

    /**
     * 使用默认 token 根据平台 ID 获取 group message sender
     *
     * @param groupId 群组 ID
     * @return 一般消息构建器
     */
    default IEasyMessageSenderBuilder getGroupBuilder(String groupId) {
        return getGroupBuilder("default", groupId);
    }

    /**
     * 使用默认 token 获取一个
     * {@link IForwardMessageSenderBuilder}
     * ，用于构建 node 消息。
     *
     * @param data 要回复的消息数据
     * @return 转发消息构建器
     */
    default IForwardMessageSenderBuilder getForwardBuilder(ITeaNekoMessageData data) {
        return getForwardBuilder("default", data);
    }

    /**
     * 使用默认 token 获取一个
     * {@link IEasyMessageSenderBuilder}
     * ，用于构造一般 message 信息并发送。
     *
     * @param data 要回复的消息数据
     * @return 一般消息构建器
     */
    default IEasyMessageSenderBuilder getEasyBuilder(ITeaNekoMessageData data) {
        return getEasyBuilder("default", data);
    }
}
