package org.zexnocs.teanekoclient.onebot.event.meta;

import org.zexnocs.teanekoclient.onebot.data.receive.meta.LifecycleMetaEventData;
import org.zexnocs.teanekoclient.onebot.event.OnebotEventShareComponent;
import org.zexnocs.teanekocore.event.AbstractEvent;
import org.zexnocs.teanekocore.event.core.Event;

/**
 * 生命周期元事件
 *
 * @author zExNocs
 * @date 2026/03/01
 * @since 4.0.11
 */
@Event(LifecycleMetaEvent.KEY)
public class LifecycleMetaEvent extends AbstractEvent<LifecycleMetaEventData> {
    public static final String KEY = MetaEvent.PARSE_SUFFIX_KEY + "lifecycle";

    public LifecycleMetaEvent(String information, OnebotEventShareComponent eventShareComponent) {
        super(LifecycleMetaEventData.fromJson(information, eventShareComponent.objectMapper), LifecycleMetaEventData.class);
    }
}
