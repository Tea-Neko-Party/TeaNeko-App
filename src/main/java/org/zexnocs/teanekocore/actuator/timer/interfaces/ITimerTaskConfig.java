package org.zexnocs.teanekocore.actuator.timer.interfaces;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskConfig;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskResult;
import org.zexnocs.teanekocore.framework.lifescycle.interfaces.ILivable;
import org.zexnocs.teanekocore.framework.lifescycle.interfaces.IPausable;

import java.util.function.Function;

/**
 * 用于包装 ITaskConfig 来给 ITimer 周期性地生成 ITask。
 * 是面向用户的接口，提供给用户配置定时任务的参数和 Future 执行链。
 * 此外，还提供了生命周期和暂停周期的配置接口。
 * 注意：因为 ITimer 用来封装统一接口的可重复延迟任务，所以大部分情况下请将 TaskConfig 中的 DelayDuration 设置为 Duration.ZERO，
 *      转而在各自的 ITimer 中设置时间间隔；否则任务可能在 Timer 中到达特定时间后再次被延迟。除非你清楚地知道自己在做什么。
 * @see ITaskConfig
 * @see IPausable
 * @see ILivable
 * @author zExNocs
 * @date 2026/02/14
 */
public interface ITimerTaskConfig<T> {
    /**
     * 获取任务配置。
     * @return 任务配置。
     */
    @NonNull
    ITaskConfig<T> getTaskConfig();

    /**
     * 用于配置 Future 执行链的函数。
     * 内部可自行设置异常处理器。
     * 该 Future 会自动在后面使用 finish() 函数，无需在该函数中调用。
     * @return Future 配置函数。
     */
    Function<TaskFuture<ITaskResult<T>>, TaskFuture<?>> getTaskFutureChain();

    /**
     * 用于设置 Future 执行链的函数。
     * 内部可自行设置异常处理器。
     * 该 Future 会自动在后面使用 finish() 函数，无需在该函数中调用。
     * @param taskFutureChain Future 配置函数。
     */
    void setTaskFutureChain(Function<TaskFuture<ITaskResult<T>>, TaskFuture<?>> taskFutureChain);

    /**
     * 获取计时器的生命周期。
     * 如果为 null 则表示没有生命周期限制，也就是不会被删除。
     * @return 生命周期。
     */
    @Nullable
    ILivable getLivable();

    /**
     * 获取暂停周期。
     * 如果为 null 则表示没有暂停周期限制，也就是不会被暂停。
     * @return 暂停周期。
     */
    @Nullable
    IPausable getPausable();

    /**
     * 设置生命周期。
     * @param livable 生命周期。
     */
    void setLivable(@Nullable ILivable livable);

    /**
     * 设置暂停周期。
     * @param pausable 暂停周期。
     */
    void setPausable(@Nullable IPausable pausable);
}
