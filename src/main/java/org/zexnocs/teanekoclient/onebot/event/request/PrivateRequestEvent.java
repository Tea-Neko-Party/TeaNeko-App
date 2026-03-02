package org.zexnocs.teanekoclient.onebot.event.request;

import org.zexnocs.teanekoclient.onebot.data.receive.request.PrivateRequestData;
import org.zexnocs.teanekoclient.onebot.event.OnebotEventShareComponent;
import org.zexnocs.teanekocore.event.AbstractEvent;
import org.zexnocs.teanekocore.event.core.Event;

/**
 * 好友请求事件
 *
 * @author zExNocs
 * @date 2026/03/02
 * @since 4.0.11
 */
@Event(PrivateRequestEvent.KEY)
public class PrivateRequestEvent extends AbstractEvent<PrivateRequestData> {
    public static final String KEY = RequestEvent.PARSE_SUFFIX_KEY + "friend"; // 私聊请求事件

    public PrivateRequestEvent(String information, OnebotEventShareComponent eventShareComponent) {
        super(PrivateRequestData.fromJson(information, eventShareComponent.objectMapper), PrivateRequestData.class);
    }
}
