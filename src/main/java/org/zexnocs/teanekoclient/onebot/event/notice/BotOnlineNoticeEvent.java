package org.zexnocs.teanekoclient.onebot.event.notice;

import org.zexnocs.teanekoclient.onebot.data.receive.notice.BotOnlineNoticeData;
import org.zexnocs.teanekoclient.onebot.event.OnebotEventShareComponent;
import org.zexnocs.teanekocore.event.AbstractEvent;
import org.zexnocs.teanekocore.event.core.Event;

/**
 * Bot 上线通知事件
 *
 * @author zExNocs
 * @date 2026/03/02
 * @since 4.0.11
 */
@Event(BotOnlineNoticeEvent.KEY)
public class BotOnlineNoticeEvent extends AbstractEvent<BotOnlineNoticeData> {
    public static final String KEY = NoticeReceiveEvent.PARSE_SUFFIX_KEY + "bot_online";

    public BotOnlineNoticeEvent(String information, OnebotEventShareComponent eventShareComponent) {
        super(BotOnlineNoticeData.fromJson(information, eventShareComponent.objectMapper), BotOnlineNoticeData.class);
    }
}
