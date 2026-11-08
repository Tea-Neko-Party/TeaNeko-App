package org.zexnocs.teanekoplugin.support;

// 2026/11/07：接口统一使用 I 前缀，调用合同与持久数据保持不变。

import org.zexnocs.teanekocore.actuator.timer.interfaces.ITimerTaskConfig;
import org.zexnocs.teanekocore.framework.function.IMethodCallable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/**
 * 拥有一次插件启用期间的定时器与在途工作，关闭后旧回调永远不可重新进入。
 * <br>2026/11/07：停用先关闭入口，再等待本代工作真实结束，不关闭宿主共享执行器。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
public final class PluginTaskScope {
    private final List<ITimerTaskConfig<?>> timers = new ArrayList<>();
    private final Set<CompletableFuture<Void>> running = new HashSet<>();
    private boolean active = true;

    /** 创建独立启用代次；重启插件须创建新的作用域。 */
    public PluginTaskScope() {
    }

    /**
     * 接管本代定时器；停止后的迟到注册立即失效。
     * @param timer 宿主创建且由本业务拥有生命周期的定时器
     */
    public synchronized void own(ITimerTaskConfig<?> timer) {
        if (!active) {
            timer.setLivable(() -> false);
            return;
        }
        timers.add(timer);
        timer.setLivable(this::isActive);
    }

    /**
     * 释放被规则替换的旧定时器，避免长时间启用时积累失效句柄。
     * @param timer 本作用域已经登记的定时器
     */
    public synchronized void release(ITimerTaskConfig<?> timer) {
        timer.setLivable(() -> false);
        timers.remove(timer);
    }

    /**
     * 查询本代是否仍接受新工作。
     * @return 本代仍启用时为真
     */
    public synchronized boolean isActive() {
        return active;
    }

    /**
     * 在新的异步副作用开始前复核本代仍启用。
     * @throws CancellationException 当插件已经停止时
     */
    public synchronized void checkActive() {
        if (!active) {
            throw new CancellationException("所属插件已停止，本代工作不能继续提交新的操作。");
        }
    }

    /**
     * 登记工作后在锁外执行；异步业务应在回调返回前等待其真实完成。
     * @param action 本次同步业务或等待完整异步链的业务
     * @param <T> 结果类型
     * @return 原业务结果
     * @throws Exception 当业务失败时原样传播
     * @throws CancellationException 当本代已经停止时拒绝迟到工作
     */
    public <T> T execute(IMethodCallable<T> action) throws Exception {
        var completion = new CompletableFuture<Void>();
        synchronized (this) {
            if (!active) {
                throw new CancellationException("所属插件已停止，本代定时工作不可继续启动。");
            }
            running.add(completion);
        }
        try {
            return action.call();
        } finally {
            synchronized (this) {
                running.remove(completion);
            }
            completion.complete(null);
        }
    }

    /**
     * 2026/11/07：在提交异步链前登记占用，停止等待原链真实完成而不提前取消回执。
     * 调用方只能取消自己的等待副本，不能撤销内部真实完成观察。
     * @param action 返回实际业务完成阶段的启动函数，不得返回仅表示取消等待的包装阶段
     * @param <T> 结果类型
     * @return 原业务结果；本代已停止时返回失败阶段
     */
    public <T> CompletionStage<T> executeAsync(Supplier<CompletionStage<T>> action) {
        var completion = new CompletableFuture<Void>();
        synchronized (this) {
            if (!active) {
                return CompletableFuture.failedStage(new CancellationException("所属插件已停止，拒绝新的异步工作。"));
            }
            running.add(completion);
        }
        CompletionStage<T> work;
        try {
            work = Objects.requireNonNull(action.get(), "插件业务必须返回真实完成阶段。");
        } catch (Throwable failure) {
            work = CompletableFuture.failedStage(failure);
        }
        var result = new CompletableFuture<T>();
        // 2026/11/07：内部观察不对外暴露，取消返回值不会跳过本代占用释放。
        work.whenComplete((value, failure) -> {
            synchronized (this) {
                running.remove(completion);
            }
            completion.complete(null);
            if (failure == null) {
                result.complete(value);
            } else {
                result.completeExceptionally(failure);
            }
        });
        return result.minimalCompletionStage();
    }

    /**
     * 停止新工作并等待已经登记的工作结束，可重复调用。
     * @return 在途工作真实结束的只读阶段
     */
    public synchronized CompletionStage<Void> stop() {
        active = false;
        timers.forEach(timer -> timer.setLivable(() -> false));
        timers.clear();
        return CompletableFuture.allOf(running.toArray(CompletableFuture<?>[]::new)).minimalCompletionStage();
    }
}
