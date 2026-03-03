package org.zexnocs.teanekoclient.onebot.event.meta;

import org.zexnocs.teanekoclient.onebot.data.receive.meta.HeartbeatMetaEventData;
import org.zexnocs.teanekoclient.onebot.event.OnebotEventShareComponent;
import org.zexnocs.teanekocore.event.AbstractEvent;
import org.zexnocs.teanekocore.event.core.Event;

/**
 * 心跳 Meta 事件
 *
 * @author zExNocs
 * @date 2026/03/01
 * @since 4.0.11
 */
@Event(HeartbeatMetaEvent.KEY)
public class HeartbeatMetaEvent extends AbstractEvent<HeartbeatMetaEventData> {
    public static final String KEY = MetaEvent.PARSE_SUFFIX_KEY + "heartbeat";

    public HeartbeatMetaEvent(String information, OnebotEventShareComponent eventShareComponent) {
        super(HeartbeatMetaEventData.fromJson(information, eventShareComponent.objectMapper));
    }
}
