package org.zexnocs.teanekoclient.onebot.event.notice;

import org.zexnocs.teanekoclient.onebot.data.receive.notice.GroupBanNoticeData;
import org.zexnocs.teanekoclient.onebot.event.OnebotEventShareComponent;
import org.zexnocs.teanekocore.event.AbstractEvent;
import org.zexnocs.teanekocore.event.core.Event;

/**
 * 群禁言通知事件
 *
 * @author zExNocs
 * @date 2026/03/02
 * @since 4.0.11
 */
@Event(GroupBanNoticeEvent.KEY)
public class GroupBanNoticeEvent extends AbstractEvent<GroupBanNoticeData> {
    public static final String KEY = NoticeReceiveEvent.PARSE_SUFFIX_KEY + "group_ban";

    public GroupBanNoticeEvent(String information, OnebotEventShareComponent eventShareComponent) {
        super(GroupBanNoticeData.fromJson(information, eventShareComponent.objectMapper));
    }
}