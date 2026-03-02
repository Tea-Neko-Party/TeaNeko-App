
package org.zexnocs.teanekoclient.onebot.event.notice;

import org.zexnocs.teanekoclient.onebot.data.receive.notice.GroupDecreaseNoticeData;
import org.zexnocs.teanekoclient.onebot.event.OnebotEventShareComponent;
import org.zexnocs.teanekocore.event.AbstractEvent;
import org.zexnocs.teanekocore.event.core.Event;

/**
 * 群成员减少通知事件
 *
 * @author zExNocs
 * @date 2026/03/02
 * @since 4.0.11
 */
@Event(GroupDecreaseNoticeEvent.KEY)
public class GroupDecreaseNoticeEvent extends AbstractEvent<GroupDecreaseNoticeData> {
    public static final String KEY = NoticeReceiveEvent.PARSE_SUFFIX_KEY + "group_decrease";

    public GroupDecreaseNoticeEvent(String information, OnebotEventShareComponent eventShareComponent) {
        super(GroupDecreaseNoticeData.fromJson(information, eventShareComponent.objectMapper), GroupDecreaseNoticeData.class);
    }
}