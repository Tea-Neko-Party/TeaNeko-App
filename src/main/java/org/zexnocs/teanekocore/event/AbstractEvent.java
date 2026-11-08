package org.zexnocs.teanekocore.event;

import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekocore.event.interfaces.IEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 用于快速创建事件的抽象类，提供了事件数据、取消状态和完整性检查等功能。
 * <br>2026/11/07：统一登记通知期间启动的异步业务，所有事件可参与插件在途排空。
 *
 * @author zExNocs
 * @date 2026/02/17
 * @version 4.9.0-l
 */
public abstract class AbstractEvent<T> implements IEvent<T> {
    // 2026/11/07：将原消息事件完成合同提升至通用事件，封闭由事件分发器统一安排。
    private final List<CompletableFuture<?>> processing = new ArrayList<>();
    private boolean processingSealed;
    /// 是否被取消
    private final AtomicBoolean isCancelled = new AtomicBoolean(false);

    /// 事件数据
    @Setter
    @Getter
    private T data;

    /**
     * 事件的构造函数。
     *
     * @param data 事件数据
     */
    public AbstractEvent(@Nullable T data) {
        this.data = data;
    }

    /**
     * 2026/11/07：在同步或异步监听的实际通知期间登记业务，不阻塞事件执行线程。
     * @param stage 已启动且代表真实业务终态的完成阶段
     * @throws IllegalStateException 当全部通知结束并已封闭登记时
     */
    public synchronized void trackProcessing(CompletionStage<?> stage) {
        if (processingSealed) {
            throw new IllegalStateException("事件通知已结束，不能追加异步处理阶段。");
        }
        processing.add(stage.toCompletableFuture());
    }

    /**
     * 2026/11/07：所有通知完成后封闭业务集合，失败继续传播，外部观察不持有根完成权限。
     * @return 已登记业务全部真实结束后的只读阶段
     */
    @Override
    public synchronized CompletionStage<Void> processingCompletion() {
        processingSealed = true;
        return CompletableFuture.allOf(processing.toArray(CompletableFuture<?>[]::new)).minimalCompletionStage();
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
     * 获取事件环境下的发送器的 token。
     *
     * @return token
     */
    public static String getTokenForSender() {
        return "event";
    }
}
