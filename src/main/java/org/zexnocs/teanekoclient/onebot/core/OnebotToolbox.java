package org.zexnocs.teanekoclient.onebot.core;


import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import org.zexnocs.teanekoapp.sender.api.ITeaNekoToolbox;
import org.zexnocs.teanekoapp.sender.api.sender_box.IGetMessageSender;
import org.zexnocs.teanekoapp.sender.api.sender_box.IMessageSender;
import org.zexnocs.teanekoapp.sender.api.sender_box.IPlatformUserGetSender;

/**
 * onebot 发送器工具箱，提供一些基于 onebot 协议的发送器工具。
 *
 * @author zExNocs
 * @date 2026/03/02
 * @since 4.0.11
 */
@Component
public class OnebotToolbox implements ITeaNekoToolbox {
    /**
     * 获取消息发送器工具。
     *
     * @param token 发送器 token，用于表示发送者的身份
     * @return {@link IMessageSender }
     */
    @Override
    public IMessageSender getMessageSender(@NonNull String token) {
        return null;
    }

    /**
     * 获取根据消息 ID 获取消息的发送器工具。
     *
     * @param token 发送器 token，用于表示发送者的身份
     * @return {@link IGetMessageSender }
     */
    @Override
    public IGetMessageSender getGetMsgSender(@NonNull String token) {
        return null;
    }

    /**
     * 获取平台用户信息的发送器
     *
     * @param token 发送器 token，用于表示发送者的身份
     * @return 获取平台用户信息的发送器
     */
    @Override
    public IPlatformUserGetSender getPlatformUserGetSender(@NonNull String token) {
        return null;
    }
}
