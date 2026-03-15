package org.zexnocs.teanekoclient.onebot.event.teaneko;

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekocore.event.AbstractEvent;

/**
 * 当连接时触发的事件。事件数据为连接的会话 ID。
 *
 * @author zExNocs
 * @date 2026/03/15
 * @since 4.3.3
 */
public class OnebotConnectEvent extends AbstractEvent<String> {

    /**
     * 事件的构造函数。
     *
     * @param data 事件数据
     */
    public OnebotConnectEvent(@Nullable String data) {
        super(data);
    }
}
