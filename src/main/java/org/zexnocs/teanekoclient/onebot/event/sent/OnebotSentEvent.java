package org.zexnocs.teanekoclient.onebot.event.sent;

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoapp.sender.SentEvent;
import org.zexnocs.teanekoclient.onebot.data.send.ISendParamsData;
import org.zexnocs.teanekoclient.onebot.data.send.OnebotSendData;

/**
 * 发送 onebot sent 事件
 *
 * @author zExNocs
 * @date 2026/03/15
 * @since 4.3.2
 */
public class OnebotSentEvent<S extends ISendParamsData<R>, R> extends SentEvent<OnebotSendData<S, R>> {
    /**
     * 事件的构造函数。
     *
     * @param data 事件数据
     */
    public OnebotSentEvent(@Nullable OnebotSendData<S, R> data) {
        super(data);
    }
}
