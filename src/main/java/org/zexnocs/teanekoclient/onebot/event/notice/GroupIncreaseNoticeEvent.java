package org.zexnocs.teanekoclient.onebot.event.notice;

import org.zexnocs.teanekoclient.onebot.data.receive.notice.GroupIncreaseNoticeData;
import org.zexnocs.teanekoclient.onebot.event.OnebotEventShareComponent;
import org.zexnocs.teanekocore.event.AbstractEvent;
import org.zexnocs.teanekocore.event.core.Event;

/**
 * 群成员增加通知事件
 *
 * @author zExNocs
 * @date 2026/03/02
 * @since 4.0.11
 */
@Event(GroupIncreaseNoticeEvent.KEY)
public class GroupIncreaseNoticeEvent extends AbstractEvent<GroupIncreaseNoticeData> {
    public static final String KEY = NoticeReceiveEvent.PARSE_SUFFIX_KEY + "group_increase";

    public GroupIncreaseNoticeEvent(String information, OnebotEventShareComponent eventShareComponent) {
        super(GroupIncreaseNoticeData.fromJson(information, eventShareComponent.objectMapper));
    }
}