package org.zexnocs.teanekoclient.onebot.event.notice;

import org.zexnocs.teanekoclient.onebot.data.receive.notice.GroupCardNoticeData;
import org.zexnocs.teanekoclient.onebot.event.OnebotEventShareComponent;
import org.zexnocs.teanekocore.event.AbstractEvent;
import org.zexnocs.teanekocore.event.core.Event;

/**
 * 群名片变更通知事件
 *
 * @author zExNocs
 * @date 2026/03/02
 * @since 4.0.11
 */
@Event(GroupCardNoticeEvent.KEY)
public class GroupCardNoticeEvent extends AbstractEvent<GroupCardNoticeData> {
    public static final String KEY = NoticeReceiveEvent.PARSE_SUFFIX_KEY + "group_card";

    public GroupCardNoticeEvent(String information, OnebotEventShareComponent eventShareComponent) {
        super(GroupCardNoticeData.fromJson(information, eventShareComponent.objectMapper));
    }
}