package org.zexnocs.teanekocore.event.interfaces;

import jakarta.annotation.Nullable;

/**
 * 事件类的接口。
 *
 * <br>2026/11/07：明确事件登记业务的实际完成阶段。
 *
 * @author zExNocs
 * @date 2026/02/17
 */
public interface IEvent<T> {
    /**
     * 2026/11/06：通知完成后封闭已登记的异步业务，普通事件没有附加工作。
     * @return 已启动业务实际完成的阶段，不以通知结束代替实际处理完成
     */
    default java.util.concurrent.CompletionStage<Void> processingCompletion() {
        return java.util.concurrent.CompletableFuture.completedStage(null);
    }
    /**
     * 获取事件数据。
     * @return 事件数据
     */
    T getData();

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
     * 在通知处理器之前调用的方法。
     */
    void _beforeNotify();

    /**
     * 在通知处理器之后调用的方法。
     */
    void _afterNotify();

    /**
     * 获取要在同一线程中推动的下一个事件。
     * 如果没有下一个事件则返回 null。
     * @return 下一个事件。
     */
    @Nullable
    IEvent<?> _getNextEvent();
}
