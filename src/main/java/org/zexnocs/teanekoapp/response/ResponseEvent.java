package org.zexnocs.teanekoapp.response;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekocore.event.AbstractEvent;

/**
 * 用于接收从 client 的响应信息。
 *
 * @author zExNocs
 * @date 2026/02/23
 */
public class ResponseEvent extends AbstractEvent<ResponseData> {
    /**
     * 事件的构造函数。
     *
     * @param data        事件数据
     * @param genericType 事件数据的泛型类型
     */
    public ResponseEvent(@Nullable ResponseData data,
                         @NonNull Class<ResponseData> genericType) {
        super(data, genericType);
    }
}
