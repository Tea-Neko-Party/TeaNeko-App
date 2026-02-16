package org.zexnocs.teanekocore.event.interfaces;

import jakarta.annotation.Nullable;

/**
 * 事件类的接口。
 *
 * @author zExNocs
 * @date 2026/02/17
 */
public interface IEvent<T> {
    /**
     * 获取事件数据。
     * @return 事件数据
     */
    @Nullable
    T getData();

    /**
     * 获取事件数据的类型。
     * @return 事件数据的类型
     */
    Class<T> getGenericType();

    /**
     * 事件是否被取消。
     * @return 是否被取消
     */
    boolean isCancelled();

    /**
     * 设置事件是否被取消
     * 关于多线程的取消注意事项：
     * 1. 异步线程取消事件对主线程无效。
     * 2. 异步线程取消事件可能对其他异步线程有效。(多线程的不确定性)
     * 3. 主线程取消事件对异步线程有效。(无论优先级)
     * 4. 主线程取消事件对主线程有效，取决于优先级。
     * @param cancelled 是否被取消
     */
    void setCancelled(boolean cancelled);

    /**
     * 获取并设置 cancelled 的值。
     * 用于原子操作。
     * @param cancelled 是否被取消
     */
    boolean getAndSetCancelled(boolean cancelled);

    /**
     * 检测事件的完整性。
     * 如果完整则返回 true，否则返回 false。
     * @return 是否完整。
     */
    boolean isComplete();

    /**
     * 在通知处理器之前调用的方法。
     */
    default void _beforeNotify() {}

    /**
     * 在通知处理器之后调用的方法。
     */
    default void _afterNotify() {}

    /**
     * 获取要在同一线程中推动的下一个事件。
     * 如果没有下一个事件则返回 null。
     * @return 下一个事件。
     */
    @Nullable
    default IEvent<?> _getNextEvent() {
        return null;
    }
}
