package org.zexnocs.teanekoapp.message;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekocore.event.AbstractEvent;

/**
 * 从客户端接收到 message 时触发的事件。
 * <p>客户端方面一般实现一个新的事件来继承该事件，并提供详细的泛型类型。
 * <p>监听器方面如果：
 * <p>- 处理所有 message，可以直接监听 {@code TeaNekoMessageReceiveEvent<?>}; 注意不要在 {@code ?} 处使用具体类型，因为依然会导致非该类型的事件被监听到。
 * <p>- 如果要处理特定类型的 message，请直接监听客户端中继承的事件。
 * <p>例如，客户端实现了
 * {@code AEvent extends TeaNekoMessageReceiveEvent<ASpecificMessage>}
 * 则监听器可以监听
 * {@code AEvent}
 * 来处理
 * {@code ASpecificMessage}
 * 而不能监听
 * {@code TeaNekoMessageReceiveEvent<ASpecificMessage>}
 * 来处理，因为会导致非
 * {@code ASpecificMessage}
 * 的事件也被监听到。这是因为事件的分类不是根据泛型类型的，而是根据事件类的。
 *
 * @author zExNocs
 * @date 2026/02/26
 * @since 4.0.9
 */
public class TeaNekoMessageReceiveEvent<T extends ITeaNekoMessageData> extends AbstractEvent<T> {
    /**
     * 事件的构造函数。
     *
     * @param data        事件数据
     * @param genericType 事件数据的泛型类型
     */
    public TeaNekoMessageReceiveEvent(@Nullable T data,
                                      @NonNull Class<T> genericType) {
        super(data, genericType);
    }
}
