package org.zexnocs.teanekoapp.sender;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoapp.sender.api.ISendData;
import org.zexnocs.teanekocore.event.AbstractEvent;

/**
 * 用于发送消息的事件类。
 *
 * @author zExNocs
 * @date 2026/02/23
 * @since 4.0.8
 */
public class SentEvent<T extends ISendData<?>> extends AbstractEvent<T> {
    /**
     * 事件的构造函数。
     *
     * @param data        事件数据
     * @param genericType 事件数据的类型
     */
    public SentEvent(@Nullable T data, @NonNull Class<T> genericType) {
        super(data, genericType);
    }

    /**
     * 向客户端发送信息。
     */
    @Override
    public void _afterNotify() {
        var sendData = getData();
        var client = sendData.getClient();
        client.send(sendData.toSendString());
    }
}
