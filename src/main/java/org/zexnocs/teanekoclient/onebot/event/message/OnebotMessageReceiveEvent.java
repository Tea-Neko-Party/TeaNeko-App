package org.zexnocs.teanekoclient.onebot.event.message;

import lombok.Getter;
import org.zexnocs.teanekoapp.message.TeaNekoMessageData;
import org.zexnocs.teanekoapp.message.TeaNekoMessageReceiveEvent;
import org.zexnocs.teanekoclient.onebot.data.receive.message.OnebotMessageData;

/**
 * 符合 onebot 规范的消息接收事件，包含了所有 onebot 规范中定义的字段和功能。
 *
 * @author zExNocs
 * @date 2026/03/02
 * @since 4.0.11
 */
public class OnebotMessageReceiveEvent extends TeaNekoMessageReceiveEvent<TeaNekoMessageData> {
    /// onebot 规范中定义的原始消息数据，包含了所有 onebot 规范中定义的字段和功能
    @Getter
    private final OnebotMessageData onebotMessageData;

    /**
     * 构造函数，接收 onebotMessageData 和 teaNekoMessageData 两个参数，并将它们分别赋值给对应的字段。
     *
     * @param onebotMessageData onebot 规范中定义的原始消息数据，包含了所有 onebot 规范中定义的字段和功能
     * @param teaNekoMessageData 符合 TeaNeko 规范的消息数据，包含了 TeaNeko 规范中定义的字段和功能
     */
    public OnebotMessageReceiveEvent(OnebotMessageData onebotMessageData, TeaNekoMessageData teaNekoMessageData) {
        super(teaNekoMessageData);
        this.onebotMessageData = onebotMessageData;
    }
}
