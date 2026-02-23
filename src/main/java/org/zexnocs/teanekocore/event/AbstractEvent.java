package org.zexnocs.teanekocore.event;

import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekocore.event.interfaces.IEvent;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 用于快速创建事件的抽象类，提供了事件数据、取消状态和完整性检查等功能。
 *
 * @author zExNocs
 * @date 2026/02/17
 */
public abstract class AbstractEvent<T> implements IEvent<T> {
    /// 是否被取消
    private final AtomicBoolean isCancelled = new AtomicBoolean(false);

    /// 事件数据
    @Setter
    @Getter
    private T data;

    /// 事件数据的类型
    @Getter
    private final Class<T> genericType;

    /**
     * 事件的构造函数。
     *
     * @param data        事件数据
     * @param genericType 事件数据的泛型类型
     */
    public AbstractEvent(@Nullable T data, @NonNull Class<T> genericType) {
        this.data = data;
        this.genericType = genericType;
    }

    /**
     * 在通知处理器之前调用的方法。
     * 默认没有任何操作。
     */
    @Override
    public void _beforeNotify() {}

    /**
     * 在通知处理器之后调用的方法。
     * 默认没有任何操作。
     */
    @Override
    public void _afterNotify() {}

    /**
     * 默认没有下一个事件。
     * @return 下一个事件。
     */
    @Override
    public IEvent<?> _getNextEvent() {
        return null;
    }

    /**
     * 事件是否被取消。
     * @return 是否被取消
     */
    @Override
    public boolean isCancelled() {
        return isCancelled.get();
    }

    /**
     * 设置事件是否被取消
     * 关于多线程的取消注意事项：
     * 1. 异步线程取消事件对主线程无效。
     * 2. 异步线程取消事件可能对其他异步线程有效。(多线程的不确定性)
     * 3. 主线程取消事件对异步线程有效。(无论优先级)
     * 4. 主线程取消事件对主线程有效，取决于优先级。
     * @param cancelled 是否被取消
     */
    @Override
    public void setCancelled(boolean cancelled) {
        isCancelled.set(cancelled);
    }

    /**
     * 获取并设置 cancelled 的值。
     * 用于原子操作。
     * @param cancelled 是否被取消
     */
    @Override
    public boolean getAndSetCancelled(boolean cancelled) {
        return isCancelled.getAndSet(cancelled);
    }

    /**
     * 检测事件的完整性。
     * 如果完整则返回 true，否则返回 false。
     * @return 是否完整。
     */
    @Override
    public boolean isComplete() {
        // 如果 T 是 Void，则永远返回 true
        if (data == null && Void.class.equals(genericType)) {
            return true;
        }
        return data != null;
    }
}
