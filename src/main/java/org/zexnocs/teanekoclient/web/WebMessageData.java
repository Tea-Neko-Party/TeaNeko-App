package org.zexnocs.teanekoclient.web;

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import lombok.Getter;
import org.zexnocs.teanekoapp.message.TeaNekoUserData;
import org.zexnocs.teanekoapp.message.api.ITeaNekoContent;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekoapp.message.api.ITeaNekoUserData;
import org.zexnocs.teanekoapp.message.api.TeaNekoMessageType;
import org.zexnocs.teanekocore.command.api.CommandPermission;
import org.zexnocs.teanekocore.command.interfaces.ICommandSourceGuard;

import java.time.Instant;
import java.util.List;

/**
 * 服务器铸造的网页原生消息及短暂发送能力，不从浏览器取得角色或 UUID。
 * 历史查询只提供内容，不能因此取得新的发送能力。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.7.0-l
 */
@JsonIgnoreType
public final class WebMessageData implements ITeaNekoMessageData, ICommandSourceGuard {
    @Getter private final Instant time;
    @Getter private final String messageId;
    @Getter private final List<? extends ITeaNekoContent> messages;
    @Getter private final TeaNekoMessageType messageType;
    @Getter private final String scopeId;
    @Getter private final ITeaNekoUserData userData;
    @Getter private final WebTeaNekoClient client;
    @Getter private final String scopeName;
    @Getter private final String senderGroupNickname;
    private final String room;
    private final long revision;
    private final Runnable guard;
    private final Instant deadline;

    /**
     * @param room 原服务端聊天
     * @param message 原已提交消息
     * @param client 原网页客户端
     * @param messages 已验证图片与原片段
     * @param guard 原宿主会话能力，历史查询使用明确拒绝发送的能力
     */
    WebMessageData(WebChat.Room room, WebChat.Message message, WebTeaNekoClient client,
                   List<? extends ITeaNekoContent> messages, Runnable guard) {
        this.room = room.id();
        this.revision = room.revision();
        this.time = message.at();
        this.messageId = message.id();
        this.messages = List.copyOf(messages);
        this.messageType = room.kind().equals("GROUP") ? TeaNekoMessageType.GROUP : TeaNekoMessageType.PRIVATE;
        this.scopeId = room.scope();
        this.scopeName = room.name();
        this.senderGroupNickname = message.card();
        this.client = client;
        this.guard = guard;
        this.deadline = Instant.now().plusSeconds(300);
        this.userData = TeaNekoUserData.builder().uuid(message.sender().uuid()).userIdInPlatform(message.sender().id())
                .nickname(message.sender().name()).role(CommandPermission.valueOf(message.role()))
                .groupId(room.kind().equals("GROUP") ? room.id() : null).build();
    }

    /** @return 固定的真实网页聊天，不由消息文字推断 */
    public String roomId() {
        return room;
    }

    /** @return 受理时的原成员版本 */
    public long memberRevision() {
        return revision;
    }

    /**
     * @throws SecurityException 当原会话、期限或读取专用来源不能继续执行时
     */
    @Override
    public void checkCommandSource() {
        guard.run();
        if (Instant.now().isAfter(deadline)) {
            throw new SecurityException("网页消息来源能力已超过五分钟期限。");
        }
    }
}
