package org.zexnocs.teanekoapp.message.api;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekocore.command.api.CommandPermission;

import java.util.UUID;

/**
 * Tea Neko 发送者接口，用于获取发送者的信息。
 *
 * @author zExNocs
 * @date 2026/02/21
 */
public interface ITeaNekoSenderData {
    /**
     * 发送者的 UUID
     *
     * @return {@link UUID }
     */
    @NonNull
    UUID getUuid();

    /**
     * 发送者的平台 ID
     * 例如 QQ 号、微信号、Telegram ID 等等，具体取决于消息来源的平台。
     *
     * @return 发送者的平台 ID
     */
    @Nullable
    String getUserIdInPlatform();

    /**
     * 发送者的 nickname，如果没有则返回 "user"
     *
     * @return 发送者的 nickname，如果没有则返回 "user"
     */
    @NonNull
    String getNickname();

    // -------- 如果是群消息 ----------
    /**
     * 发送者在群内的角色。
     * 允许使用范围是 "owner", "admin", "member"，"debug"。
     * 如果是开发者，则返回 "debug"；(在客户端构造 sender data 时注入)
     * 如果是群主，则返回 "owner"；
     * 如果是群管理员，则返回 "admin"；
     * 否则返回 "member"。
     * 如果是私聊，则始终为 "owner"。
     *
     * @return {@link CommandPermission}
     */
    @NonNull
    CommandPermission getRole();

    /**
     * 发送者的群 ID，或者来自群临时对话的群 ID。
     * 如果是私聊消息，则返回 null。
     *
     * @return 发送者的群 ID，如果不是群消息则返回 null
     */
    @Nullable
    String getGroupId();
}
