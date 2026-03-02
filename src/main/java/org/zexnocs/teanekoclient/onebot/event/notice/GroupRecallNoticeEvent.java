package org.zexnocs.teanekoclient.onebot.event.notice;

import org.zexnocs.teanekoclient.onebot.data.receive.notice.GroupRecallNoticeData;
import org.zexnocs.teanekoclient.onebot.event.OnebotEventShareComponent;
import org.zexnocs.teanekocore.event.AbstractEvent;
import org.zexnocs.teanekocore.event.core.Event;

/**
 * 群消息撤回通知事件
 *
 * @author zExNocs
 * @date 2026/03/02
 * @since 4.0.11
 */
@Event(GroupRecallNoticeEvent.KEY)
public class GroupRecallNoticeEvent extends AbstractEvent<GroupRecallNoticeData> {
    public static final String KEY = NoticeReceiveEvent.PARSE_SUFFIX_KEY + "group_recall";

    public GroupRecallNoticeEvent(String information, OnebotEventShareComponent eventShareComponent) {
        super(GroupRecallNoticeData.fromJson(information, eventShareComponent.objectMapper), GroupRecallNoticeData.class);
    }
}