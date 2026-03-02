package org.zexnocs.teanekoclient.onebot.event.notice;

import org.zexnocs.teanekoclient.onebot.data.receive.notice.GroupUploadNoticeData;
import org.zexnocs.teanekoclient.onebot.event.OnebotEventShareComponent;
import org.zexnocs.teanekocore.event.AbstractEvent;
import org.zexnocs.teanekocore.event.core.Event;

/**
 * 群文件上传通知事件
 *
 * @author zExNocs
 * @date 2026/03/02
 * @since 4.0.11
 */
@Event(GroupUploadNoticeEvent.KEY)
public class GroupUploadNoticeEvent extends AbstractEvent<GroupUploadNoticeData> {
    public static final String KEY = NoticeReceiveEvent.PARSE_SUFFIX_KEY + "group_upload";

    public GroupUploadNoticeEvent(String information, OnebotEventShareComponent eventShareComponent) {
        super(GroupUploadNoticeData.fromJson(information, eventShareComponent.objectMapper), GroupUploadNoticeData.class);
    }
}