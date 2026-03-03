package org.zexnocs.teanekoclient.onebot.event.notice;

import org.zexnocs.teanekoclient.onebot.data.receive.notice.GroupAdminNoticeData;
import org.zexnocs.teanekoclient.onebot.event.OnebotEventShareComponent;
import org.zexnocs.teanekocore.event.AbstractEvent;
import org.zexnocs.teanekocore.event.core.Event;

/**
 * 群管理员变动通知事件
 *
 * @author zExNocs
 * @date 2026/03/02
 * @since 4.0.11
 */
@Event(GroupAdminNoticeEvent.KEY)
public class GroupAdminNoticeEvent extends AbstractEvent<GroupAdminNoticeData> {
    public static final String KEY = NoticeReceiveEvent.PARSE_SUFFIX_KEY + "group_admin";

    public GroupAdminNoticeEvent(String information, OnebotEventShareComponent eventShareComponent) {
        super(GroupAdminNoticeData.fromJson(information, eventShareComponent.objectMapper));
    }
}