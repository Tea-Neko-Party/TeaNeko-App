package org.zexnocs.teanekoapp.sender.api;

import org.zexnocs.teanekoapp.sender.api.sender_box.*;
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
    IMessageSenderTools getMessageSenderTools();

    /**
     * 获取根据消息 ID 获取消息的发送器工具。
     *
     * @return {@link IGetMessageSender }
     */
    IGetMessageSender getGetMsgSender();

    /**
     * 获取平台用户在指定群组中的信息的发送器。
     *
     * @return 获取平台用户在指定群组中的信息的发送器
     */
    IGetGroupMemberInfoSender getGroupMemberInfoSender();

    /**
     * 获取指定群组中所有成员信息的发送器。
     *
     * @return 获取指定群组中所有成员信息的发送器。
     */
    IGetGroupMemberListSender getGroupMemberListSender();

    /**
     * 获取平台用户信息的发送器
     *
     * @return 获取平台用户信息的发送器
     */
    IPlatformUserGetSender getPlatformUserGetSender();

    /**
     * 获取构造平台用户信息的发送器。
     * 该发射器可以根据平台 ID 构造出用户的信息列表。
     */
    IPlatformUserInfoConstructor getPlatformUserInfoConstructorSender();

    /**
     * 获取符合当前适配器实现类的 logger。
     *
     * @return 符合当前适配器实现类的 logger
     */
    ILogger getLogger();
}
