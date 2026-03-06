package org.zexnocs.teanekoclient.onebot.core;

import lombok.Getter;
import org.springframework.stereotype.Component;
import org.zexnocs.teanekoapp.sender.api.ITeaNekoToolbox;
import org.zexnocs.teanekoclient.onebot.sender.message.OnebotGetMsgSender;
import org.zexnocs.teanekoclient.onebot.sender.message.OnebotMessageSender;
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
    private final OnebotMessageSender messageSender;

    @Getter
    private final StrangerInfoGetSender platformUserGetSender;

    @Getter
    private final ILogger logger;

    public OnebotToolbox(OnebotGetMsgSender getMsgSender,
                         OnebotMessageSender messageSender,
                         StrangerInfoGetSender platformUserGetSender, ILogger logger) {
        this.getMsgSender = getMsgSender;
        this.messageSender = messageSender;
        this.platformUserGetSender = platformUserGetSender;
        this.logger = logger;
    }
}
