package org.zexnocs.teanekoclient.onebot.event.notice;

import org.zexnocs.teanekoclient.onebot.data.receive.notice.OfflineFileNoticeData;
import org.zexnocs.teanekoclient.onebot.event.OnebotEventShareComponent;
import org.zexnocs.teanekocore.event.AbstractEvent;
import org.zexnocs.teanekocore.event.core.Event;

/**
 * 离线文件通知事件
 *
 * @author zExNocs
 * @date 2026/03/02
 * @since 4.0.11
 */
@Event(OfflineFileNoticeEvent.KEY)
public class OfflineFileNoticeEvent extends AbstractEvent<OfflineFileNoticeData> {
    public static final String KEY = NoticeReceiveEvent.PARSE_SUFFIX_KEY + "offline_file";

    public OfflineFileNoticeEvent(String information, OnebotEventShareComponent eventShareComponent) {
        super(OfflineFileNoticeData.fromJson(information, eventShareComponent.objectMapper), OfflineFileNoticeData.class);
    }
}