package org.zexnocs.teanekoclient.onebot.event.notice;

import org.zexnocs.teanekoclient.onebot.data.receive.notice.ReactionNoticeData;
import org.zexnocs.teanekoclient.onebot.event.OnebotEventShareComponent;
import org.zexnocs.teanekocore.event.AbstractEvent;
import org.zexnocs.teanekocore.event.core.Event;

/**
 * 反应事件通知
 *
 * @author zExNocs
 * @date 2026/03/02
 * @since 4.0.11
 */
@Event(ReactionNoticeEvent.KEY)
public class ReactionNoticeEvent extends AbstractEvent<ReactionNoticeData> {
    public static final String KEY = NoticeReceiveEvent.PARSE_SUFFIX_KEY + "reaction";

    public ReactionNoticeEvent(String information, OnebotEventShareComponent eventShareComponent) {
        super(ReactionNoticeData.fromJson(information, eventShareComponent.objectMapper), ReactionNoticeData.class);
    }
}