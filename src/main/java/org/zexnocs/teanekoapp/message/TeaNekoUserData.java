package org.zexnocs.teanekoapp.message;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoapp.message.api.ITeaNekoUserData;
import org.zexnocs.teanekocore.command.api.CommandPermission;

import java.util.UUID;

/**
 * Tea Neko 发送者数据类，实现了 ITeaNekoUserData 接口，用于存储发送者的信息。
 * <br>2026/11/03：角色由真实客户端确定，网页私聊可以采用更严格的普通成员默认值。
 *
 * @author zExNocs
 * @date 2026/02/27
 * @since 4.0.9
 * @version 4.7.0-l
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class TeaNekoUserData implements ITeaNekoUserData {
    /**
     * 发送者的 UUID
     */
    protected @NonNull UUID uuid;

    /**
     * 发送者的平台 ID
     * 例如 QQ 号、微信号、Telegram ID 等等，具体取决于消息来源的平台。
     */
    protected @NonNull String userIdInPlatform;

    /**
     * 发送者的 nickname，如果没有则返回 "user"
     */
    protected @NonNull String nickname;

    /**
     * 发送者在群内的角色。
     * 允许使用范围是 "owner", "admin", "member"，"debug"。
     * 如果是开发者，则返回 "debug"；(在客户端构造 sender data 时注入)
     * 如果是群主，则返回 "owner"；
     * 如果是群管理员，则返回 "admin"；
     * 否则返回 "member"。
     * 2026/11/03：私聊通常为 "owner"，客户端可采用更严格角色；网页管理权限不作为聊天角色来源。
     */
    protected @NonNull CommandPermission role;

    /**
     * 发送者的群 ID，或者来自群临时对话的群 ID。
     * 如果是私聊消息，则返回 null。
     */
    protected @Nullable String groupId;
}
