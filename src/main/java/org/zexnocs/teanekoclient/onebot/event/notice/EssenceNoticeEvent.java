package org.zexnocs.teanekoclient.onebot.event.notice;

import org.zexnocs.teanekoclient.onebot.data.receive.notice.EssenceNoticeData;
import org.zexnocs.teanekoclient.onebot.event.OnebotEventShareComponent;
import org.zexnocs.teanekocore.event.AbstractEvent;
import org.zexnocs.teanekocore.event.core.Event;

/**
 * 群加精通知事件
 *
 * @author zExNocs
 * @date 2026/03/02
 * @since 4.0.11
 */
@Event(EssenceNoticeEvent.KEY)
public class EssenceNoticeEvent extends AbstractEvent<EssenceNoticeData> {
    public static final String KEY = NoticeReceiveEvent.PARSE_SUFFIX_KEY + "essence";

    public EssenceNoticeEvent(String information, OnebotEventShareComponent eventShareComponent) {
        super(EssenceNoticeData.fromJson(information, eventShareComponent.objectMapper), EssenceNoticeData.class);
    }
}