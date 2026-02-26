package org.zexnocs.teanekoapp.message.api;

/**
 * 消息类型枚举类，定义了 Tea Neko 服务器中支持的消息类型。
 *
 * @author zExNocs
 * @date 2026/02/21
 * @since 4.0.8
 */
public enum TeaNekoMessageType {
    /// 私人消息
    PRIVATE,

    /// 群组消息
    GROUP,

    /// 群组的临时对话
    PRIVATE_TEMP,
}
