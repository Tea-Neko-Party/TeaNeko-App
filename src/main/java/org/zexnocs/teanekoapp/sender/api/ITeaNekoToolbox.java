package org.zexnocs.teanekoapp.sender.api;

import org.jspecify.annotations.NonNull;
import org.zexnocs.teanekoapp.sender.api.sender_box.IGetMessageSender;
import org.zexnocs.teanekoapp.sender.api.sender_box.IMessageSender;
import org.zexnocs.teanekoapp.sender.api.sender_box.IPlatformUserGetSender;

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
     * @param token 发送器 token，用于表示发送者的身份
     * @return {@link IMessageSender }
     */
    IMessageSender getMessageSender(@NonNull String token);

    /**
     * 获取根据消息 ID 获取消息的发送器工具。
     *
     * @param token 发送器 token，用于表示发送者的身份
     * @return {@link IGetMessageSender }
     */
    IGetMessageSender getGetMsgSender(@NonNull String token);

    /**
     * 获取平台用户信息的发送器
     *
     * @param token 发送器 token，用于表示发送者的身份
     * @return 获取平台用户信息的发送器
     */
    IPlatformUserGetSender getPlatformUserGetSender(@NonNull String token);

    // =======================================================================
    // 以下是默认 token 的重载方法，方便适配器实现类直接使用默认 token 来获取发送器工具。
    // =======================================================================

    /**
     * 默认 token
     */
    private static String defaultToken() {
        return "default";
    }

    /**
     * 获取消息发送器工具。
     *
     * @return {@link IMessageSender }
     */
    default IMessageSender getMessageSender() {
        return getMessageSender(defaultToken());
    }

    /**
     * 获取平台用户信息的发送器
     *
     * @return 获取平台用户信息的发送器
     */
    default IPlatformUserGetSender getPlatformUserGetSender() {
        return getPlatformUserGetSender(defaultToken());
    }

    /**
     * 获取根据消息 ID 获取消息的发送器工具。
     *
     * @return {@link IGetMessageSender }
     */
    default IGetMessageSender getGetMsgSender() {
        return getGetMsgSender(defaultToken());
    }
}
