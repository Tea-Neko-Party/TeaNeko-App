package org.zexnocs.teanekoclient.onebot.core;

import lombok.Getter;
import org.springframework.stereotype.Component;
import org.zexnocs.teanekoapp.sender.api.ITeaNekoToolbox;
import org.zexnocs.teanekoapp.sender.api.sender_box.IGetGroupMemberInfoSender;
import org.zexnocs.teanekoclient.onebot.sender.group.GetGroupMemberInfoSender;
import org.zexnocs.teanekoclient.onebot.sender.message.OnebotGetMsgSender;
import org.zexnocs.teanekoclient.onebot.sender.message.OnebotMessageSenderTools;
import org.zexnocs.teanekoclient.onebot.sender.private_.StrangerInfoGetSender;
import org.zexnocs.teanekocore.logger.ILogger;

/**
 * onebot 发送器工具箱，提供一些基于 onebot 协议的发送器工具。
 *
 * @author zExNocs
 * @date 2026/03/02
 * @since 4.0.11
 */
@Component
public class OnebotToolbox implements ITeaNekoToolbox {
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
    private final IGetGroupMemberInfoSender groupInfoGetSender;

    public OnebotToolbox(OnebotGetMsgSender getMsgSender,
                         OnebotMessageSenderTools messageSenderTools,
                         StrangerInfoGetSender platformUserGetSender,
                         ILogger logger,
                         OnebotUserInfoConstructor platformUserInfoConstructorSender,
                         GetGroupMemberInfoSender groupInfoGetSender) {
        this.getMsgSender = getMsgSender;
        this.messageSenderTools = messageSenderTools;
        this.platformUserGetSender = platformUserGetSender;
        this.logger = logger;
        this.platformUserInfoConstructorSender = platformUserInfoConstructorSender;
        this.groupInfoGetSender = groupInfoGetSender;
    }
}
