package org.zexnocs.teanekoclient.onebot.event.message;

import lombok.Getter;
import org.zexnocs.teanekoapp.message.TeaNekoMessageReceiveEvent;
import org.zexnocs.teanekoclient.onebot.data.receive.message.OnebotMessageData;
import org.zexnocs.teanekoclient.onebot.data.receive.message.OnebotRawMessageData;

/**
 * 符合 onebot 规范的消息接收事件，包含了所有 onebot 规范中定义的字段和功能。
 *
 * @author zExNocs
 * @date 2026/03/02
 * @since 4.0.11
 */
public class OnebotMessageReceiveEvent extends TeaNekoMessageReceiveEvent<OnebotMessageData> {
    /// onebot 规范中定义的原始消息数据，包含了所有 onebot 规范中定义的字段和功能
    @Getter
    private final OnebotRawMessageData onebotRawMessageData;

    /**
     * 构造函数，接收 onebotMessageData 和 teaNekoMessageData 两个参数，并将它们分别赋值给对应的字段。
     *
     * @param onebotRawMessageData onebot 规范中定义的原始消息数据，包含了所有 onebot 规范中定义的字段和功能
     * @param teaNekoMessageData 符合 TeaNeko 规范的消息数据，包含了 TeaNeko 规范中定义的字段和功能
     */
    public OnebotMessageReceiveEvent(OnebotRawMessageData onebotRawMessageData, OnebotMessageData teaNekoMessageData) {

        super(teaNekoMessageData);
        this.onebotRawMessageData = onebotRawMessageData;
    }
}
