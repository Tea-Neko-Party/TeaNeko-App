package org.zexnocs.teanekoapp.client.tools;

import org.zexnocs.teanekoapp.sender.api.sender_box.IGetGroupMemberInfoSender;
import org.zexnocs.teanekoapp.sender.api.sender_box.IGetGroupMemberListSender;
import org.zexnocs.teanekoapp.sender.api.sender_box.IGetMessageSender;
import org.zexnocs.teanekoapp.sender.api.sender_box.IPlatformUserGetSender;
import org.zexnocs.teanekocore.logger.ILogger;

/**
 * Tea Neko 工具箱
 * <p>提供了基础的消息发送器工具，以便于适配器实现类可以直接使用这些工具来发送消息给 Tea Neko 服务器，而不需要关心底层的通信细节。
 *
 * @author zExNocs
 * @date 2026/02/23
 * @since 4.0.9
 */
public interface ITeaNekoToolbox {
    /**
     * 获取消息发送器工具。
     *
     * @return {@link IMessageSenderTools }
     */
    default IMessageSenderTools getMessageSenderTools() {
        throw new UnsupportedOperationException("该 client 不支持消息发送器工具。");
    }

    /**
     * 获取根据消息 ID 获取消息的发送器工具。
     *
     * @return {@link IGetMessageSender }
     */
    default IGetMessageSender getGetMsgSender() {
        throw new UnsupportedOperationException("该 client 不支持根据消息 ID 获取消息的发送器工具。");
    }

    /**
     * 获取平台用户在指定群组中的信息的发送器。
     *
     * @return 获取平台用户在指定群组中的信息的发送器
     */
    default IGetGroupMemberInfoSender getGroupMemberInfoSender() {
        throw new UnsupportedOperationException("该 client 不支持获取平台用户在指定群组中的信息的发送器。");
    }

    /**
     * 获取指定群组中所有成员信息的发送器。
     *
     * @return 获取指定群组中所有成员信息的发送器。
     */
    default IGetGroupMemberListSender getGroupMemberListSender() {
        throw new UnsupportedOperationException("该 client 不支持获取指定群组中所有成员信息的发送器。");
    }

    /**
     * 获取平台用户信息的发送器
     *
     * @return 获取平台用户信息的发送器
     */
    default IPlatformUserGetSender getPlatformUserGetSender() {
        throw new UnsupportedOperationException("该 client 不支持获取平台用户信息的发送器。");
    }

    /**
     * 获取构造平台用户信息的发送器。
     * 该发射器可以根据平台 ID 构造出用户的信息列表。
     */
    default IPlatformUserInfoConstructor getPlatformUserInfoConstructorSender() {
        throw new UnsupportedOperationException("该 client 不支持获取构造平台用户信息的发送器。");
    }


    /**
     * 获取可以根据用户平台 ID 获取头像 url 的工具
     *
     * @return 头像 url 工具
     */
    default IAvatarGetter getAvatarGetter() {
        throw new UnsupportedOperationException("该 client 不支持获取头像 url 的工具。");
    }

    /**
     * 群组成员踢出器
     *
     * @return {@link IGroupKickSender }
     */
    default IGroupKickSender getGroupKickSender() {
        throw new UnsupportedOperationException("该 client 不支持群组成员踢出器。");
    }

    /**
     * 获取符合当前适配器实现类的 logger。
     *
     * @return 符合当前适配器实现类的 logger
     */
    ILogger getLogger();

}
