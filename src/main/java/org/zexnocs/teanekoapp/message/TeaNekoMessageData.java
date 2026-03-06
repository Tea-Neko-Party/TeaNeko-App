package org.zexnocs.teanekoapp.message;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.zexnocs.teanekoapp.client.api.ITeaNekoClient;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessage;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekoapp.message.api.ITeaNekoUserData;
import org.zexnocs.teanekoapp.message.api.TeaNekoMessageType;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * TeaNekoMessageData 的一个实现类。
 *
 * @author zExNocs
 * @date 2026/02/26
 * @since 4.0.9
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class TeaNekoMessageData implements ITeaNekoMessageData {
    /// 消息的发送时间戳
    protected ZonedDateTime time;

    /// 消息的唯一 ID
    protected String messageId;

    /// 消息内容列表
    protected List<? extends ITeaNekoMessage> messages;

    /// 获取消息的类型。
    /// 包括私人消息、群组消息和群组的临时对话等不同类型。
    protected TeaNekoMessageType messageType;

    /// 区域 ID，表示发送环境的 ID
    protected String scopeId;

    /// 消息发送者的元信息
    protected ITeaNekoUserData userData;

    /// 获取消息来源客户端信息。
    /// 包括客户端支持的信息发送器。
    protected ITeaNekoClient client;
}
