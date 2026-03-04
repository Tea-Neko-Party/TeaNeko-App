package org.zexnocs.teanekoclient.onebot.event.message;

import org.zexnocs.teanekoclient.onebot.data.receive.message.OnebotMessageData;
import org.zexnocs.teanekoclient.onebot.data.receive.message.OnebotRawMessageData;
import org.zexnocs.teanekoclient.onebot.event.OnebotEventShareComponent;
import org.zexnocs.teanekoclient.onebot.event.PostReceiveEvent;
import org.zexnocs.teanekoclient.onebot.utils.OnebotMessageDataConvertUtils;
import org.zexnocs.teanekocore.event.AbstractEvent;
import org.zexnocs.teanekocore.event.core.Event;

/**
 * 在发送 {@link OnebotMessageReceiveEvent} 之前触发的事件，主要用于构造 {@link OnebotMessageData}。
 *
 * @author zExNocs
 * @date 2026/03/02
 * @since 4.0.11
 */
@Event(OnebotPreMessageReceiveEvent.KEY)
public class OnebotPreMessageReceiveEvent extends AbstractEvent<OnebotRawMessageData> {
    public final static String KEY = PostReceiveEvent.SUFFIX_KEY + "message";

    /**
     * 共享组件，包含了事件处理过程中需要用到的各种组件和工具类，例如 JSON 解析器、日志记录器等。
     */
    private final OnebotEventShareComponent eventShareComponent;

    /**
     * 接收原始信息字符串和共享组件。
     * @param information 原始信息字符串
     * @param eventShareComponent 共享组件
     */
    public OnebotPreMessageReceiveEvent(String information, OnebotEventShareComponent eventShareComponent) {
        super(OnebotRawMessageData.fromJson(information, eventShareComponent.objectMapper));
        this.eventShareComponent = eventShareComponent;
    }

    /**
     * 在通知处理器之后调用的方法。
     * 尝试解析原始信息字符串，得到 onebotMessageData 和 teaNekoMessageData 的解析结果，
     * 并构造一个新的 OnebotMessageReceiveEvent 事件，并将其推送。
     */
    @Override
    public void _afterNotify() {
        var data = getData();
        this.eventShareComponent.iTeaUserService
                .getOrCreate(this.eventShareComponent.onebotTeaNekoClient, String.valueOf(data.getUserId()))
                .thenComposeTask(uuid ->
                        this.eventShareComponent.iEventService
                                .pushEventWithFuture(new OnebotMessageReceiveEvent(data,
                                        OnebotMessageDataConvertUtils.Instance.parse(data, eventShareComponent, uuid))))
                .finish();
    }
}
