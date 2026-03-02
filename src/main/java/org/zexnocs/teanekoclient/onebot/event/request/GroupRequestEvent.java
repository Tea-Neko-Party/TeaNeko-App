package org.zexnocs.teanekoclient.onebot.event.request;

import org.zexnocs.teanekoclient.onebot.data.receive.request.GroupRequestData;
import org.zexnocs.teanekoclient.onebot.event.OnebotEventShareComponent;
import org.zexnocs.teanekocore.event.AbstractEvent;
import org.zexnocs.teanekocore.event.core.Event;

/**
 * 群组请求事件
 *
 * @author zExNocs
 * @date 2026/03/02
 * @since 4.0.11
 */
@Event(GroupRequestEvent.KEY)
public class GroupRequestEvent extends AbstractEvent<GroupRequestData> {
    public static final String KEY = RequestEvent.PARSE_SUFFIX_KEY + "group";

    public GroupRequestEvent(String information, OnebotEventShareComponent eventShareComponent) {
        super(GroupRequestData.fromJson(information, eventShareComponent.objectMapper), GroupRequestData.class);
    }
}
