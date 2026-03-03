package org.zexnocs.teanekoclient.onebot.event.notice;

import org.zexnocs.teanekoclient.onebot.data.receive.notice.NotifyNoticeData;
import org.zexnocs.teanekoclient.onebot.event.OnebotEventShareComponent;
import org.zexnocs.teanekocore.event.AbstractEvent;
import org.zexnocs.teanekocore.event.core.Event;

/**
 * 通知事件（如戳一戳等）
 *
 * @author zExNocs
 * @date 2026/03/02
 * @since 4.0.11
 */
@Event(NotifyNoticeReceiveEvent.KEY)
public class NotifyNoticeReceiveEvent extends AbstractEvent<NotifyNoticeData> {
    public static final String KEY = NoticeReceiveEvent.PARSE_SUFFIX_KEY + "notify";

    public NotifyNoticeReceiveEvent(String information, OnebotEventShareComponent eventShareComponent) {
        super(NotifyNoticeData.fromJson(information, eventShareComponent.objectMapper));
    }
}