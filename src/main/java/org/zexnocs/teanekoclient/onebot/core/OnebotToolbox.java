package org.zexnocs.teanekoclient.onebot.core;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.zexnocs.teanekoapp.client.tools.ITeaNekoToolbox;
import org.zexnocs.teanekoclient.onebot.sender.group.GetGroupMemberInfoSender;
import org.zexnocs.teanekoclient.onebot.sender.group.GetGroupMemberListSender;
import org.zexnocs.teanekoclient.onebot.sender.group.GroupKickSender;
import org.zexnocs.teanekoclient.onebot.sender.message.OnebotGetMsgSender;
import org.zexnocs.teanekoclient.onebot.sender.message.OnebotMessageSenderTools;
import org.zexnocs.teanekoclient.onebot.sender.private_.StrangerInfoGetSender;
import org.zexnocs.teanekoclient.onebot.utils.AvatarUtils;
import org.zexnocs.teanekocore.logger.ILogger;

/**
 * onebot 发送器工具箱，提供一些基于 onebot 协议的发送器工具。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.0.11
 * @version 4.5.0-l
 */
@Component
@RequiredArgsConstructor
public class OnebotToolbox implements ITeaNekoToolbox {
    private final OnebotIdService ids;

    /**
     * 仅使用连接生命周期确认的自身 ID，连接尚未确认时不启用自动回复。
     * @return 当前机器人平台 ID 或未知
     */
    @Override public java.util.Optional<String> getSelfPlatformId() {
        long id = ids.getBotId(); return id > 0 ? java.util.Optional.of(Long.toString(id)) : java.util.Optional.empty();
    }
    @Getter
    private final OnebotGetMsgSender getMsgSender;

    @Getter
    private final OnebotMessageSenderTools messageSenderTools;

    @Getter
    private final StrangerInfoGetSender platformUserGetSender;

    @Getter
    private final ILogger logger;

    @Getter
    private final OnebotUserInfoConstructor platformUserInfoConstructorSender;

    @Getter
    private final GetGroupMemberInfoSender groupMemberInfoSender;

    @Getter
    private final GetGroupMemberListSender groupMemberListSender;

    @Getter
    private final AvatarUtils avatarGetter = AvatarUtils.Instance;

    @Getter
    private final GroupKickSender groupKickSender;
}
