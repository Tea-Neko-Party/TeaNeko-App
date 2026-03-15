package org.zexnocs.teanekoapp.sender;

import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoapp.sender.api.ISendData;
import org.zexnocs.teanekocore.event.AbstractEvent;

import java.util.UUID;

/**
 * 用于发送消息的事件类。
 *
 * @author zExNocs
 * @date 2026/02/23
 * @since 4.0.8
 */
public class SentEvent<T extends ISendData<?>> extends AbstractEvent<T> {

    /// 注册到 task 的 key
    @Setter
    @Getter
    private UUID taskKey;

    /**
     * 事件的构造函数。
     *
     * @param data        事件数据
     */
    public SentEvent(@Nullable T data) {
        super(data);
    }

    /**
     * 向客户端发送信息。
     */
    @Override
    public void _afterNotify() {
        var sendData = getData();
        var client = sendData.getClient();
        client.send(sendData);
    }
}
