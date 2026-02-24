package org.zexnocs.teanekoapp.sender.api;

import org.zexnocs.teanekoapp.sender.api.sender_box.IGetMessageSender;
import org.zexnocs.teanekoapp.sender.api.sender_box.IMessageSender;

/**
 * Tea Neko 发送器工具箱
 * <p>提供了基础的消息发送器工具，以便于适配器实现类可以直接使用这些工具来发送消息给 Tea Neko 服务器，而不需要关心底层的通信细节。
 *
 * @author zExNocs
 * @date 2026/02/23
 * @since 4.0.9
 */
public interface ITeaNekoSenderBox {
    /**
     * 获取消息发送器工具。
     *
     * @return {@link IMessageSender }
     */
    IMessageSender getMessageSender();

    /**
     * 获取根据消息 ID 获取消息的发送器工具。
     *
     * @return {@link IGetMessageSender }
     */
    IGetMessageSender getGetMsgSender();
}
