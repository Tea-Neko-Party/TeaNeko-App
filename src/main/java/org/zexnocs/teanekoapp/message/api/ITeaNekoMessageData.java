package org.zexnocs.teanekoapp.message.api;

import org.jspecify.annotations.NonNull;
import org.zexnocs.teanekoapp.client.api.ITeaNekoClient;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * 用于传递的消息信息接口，以便在 Tea Neko 服务器中进行处理。
 * 包含消息内容和消息发送者信息、消息来源客户端信息等元数据。
 *
 * @author zExNocs
 * @date 2026/02/21
 * @since 4.0.8
 */
public interface ITeaNekoMessageData {
    /**
     * 获取消息的发送时间戳。
     *
     * @return 消息的发送时间戳
     */
    @NonNull
    ZonedDateTime getTime();

    /**
     * 获取消息的唯一 ID
     *
     * @return 消息的唯一 ID
     */
    @NonNull
    String getMessageId();

    /**
     * 获取消息内容列表
     *
     * @return 消息内容列表，每个元素表示消息的一部分，可以是文本、图片、表情等不同类型的消息内容
     */
    @NonNull
    List<ITeaNekoMessage> getMessages();

    /**
     * 获取消息的类型。
     * 包括私人消息、群组消息和群组的临时对话等不同类型。
     *
     * @return {@link TeaNekoMessageType} 消息类型
     */
    @NonNull
    TeaNekoMessageType getMessageType();

    /**
     * 消息发送者的元信息。
     *
     * @return 消息发送者的元信息。
     */
    @NonNull
    ITeaNekoUserData getUserData();

    /**
     * 获取消息来源客户端信息。
     * 包括客户端支持的信息发送器。
     *
     * @return 消息来源客户端信息。
     */
    @NonNull
    ITeaNekoClient getClient();
}
