package org.zexnocs.teanekocore.actuator.timer.interfaces;

import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskResult;
import org.zexnocs.teanekocore.framework.function.MethodCallable;

import java.time.Duration;

/**
 * 管理定时器的服务。
 *
 * @see ITimerTaskConfig
 * @author zExNocs
 * @date 2026/02/14
 */
public interface ITimerService {
    /**
     * 注册一个定时器。
     * 自行选择定时器和配置任务 config。
     * @param timer 定时器。
     */
    void register(ITimer<?> timer);

    /**
     * 使用一个 rate 和一个 callable 注册一个默认规则的定时器。
     * 每次 rate 都会生成一个 task 来执行，无论上一个任务是否完成。
     * 不保证任务只有一个线程执行。
     * 默认规则：
     * 1. 不进行重试和错误处理
     * 2. 10min 没有返回结果会过期
     *
     * @see org.zexnocs.teanekocore.actuator.timer.FixedRateTimer
     * @param taskName 任务名称
     * @param taskStage 任务阶段
     * @param callable 任务执行的 Callable
     * @param rate 定时器的周期
     * @param resultType 任务结果类型
     * @return {@link ITimerTaskConfig }<{@link T }> 定时器任务配置对象，用于设置任务的 Future 执行链与生命周期
     */
    <T> ITimerTaskConfig<T> registerByRate(String taskName,
                                           String taskStage,
                                           MethodCallable<ITaskResult<T>> callable,
                                           Duration rate,
                                           Class<T> resultType);

    /**
     * 使用一个 delay 和一个 callable 注册一个默认规则的定时器。
     * 每次 task 完成后都会经过该 delay 来生成一个新的 task 来执行。
     * 因此执行时间为 上一次 task 执行时间 + delay。
     * 保证任务永远只有一个线程执行。
     * 默认规则：
     * 1. 不进行重试和错误处理
     * 2. 10min 没有返回结果会过期
     *
     * @see org.zexnocs.teanekocore.actuator.timer.FixedDelayTimer
     * @param taskName 任务名称
     * @param taskStage 任务阶段
     * @param callable 任务执行的 Callable
     * @param delay 定时器的周期
     * @param resultType 任务结果类型
     * @return {@link ITimerTaskConfig }<{@link T }> 定时器任务配置对象，用于设置任务的 Future 执行链与生命周期
     */
    <T> ITimerTaskConfig<T> registerByDelay(String taskName,
                                            String taskStage,
                                            MethodCallable<ITaskResult<T>> callable,
                                            Duration delay,
                                            Class<T> resultType);

    /**
     * 使用一个 rate 和一个 callable 注册一个默认规则的智能定时器。
     * 每个 rate 都会尝试生成一个 task 来执行；如果上次的 task 没有完成则会一直等待上次 task 完成。
     * 执行时间为 max(rate, 上一次 task 执行时间)。
     * 保证任务永远只有一个线程执行。
     * 默认规则：
     * 1. 不进行重试和错误处理
     * 2. 10min 没有返回结果会过期
     *
     * @see org.zexnocs.teanekocore.actuator.timer.SmartRateTimer
     * @param taskName 任务名称
     * @param taskStage 任务阶段
     * @param callable 任务执行的 Callable
     * @param rate 定时器的周期
     * @param resultType 任务结果类型
     * @return {@link ITimerTaskConfig }<{@link T }> 定时器任务配置对象，用于设置任务的 Future 执行链与生命周期
     */
    <T> ITimerTaskConfig<T> registerBySmartRate(String taskName,
                                                String taskStage,
                                                MethodCallable<ITaskResult<T>> callable,
                                                Duration rate,
                                                Class<T> resultType);

    /**
     * 使用一个 cron 表达式和一个 callable 注册一个默认规则的定时器。
     *
     * @see org.zexnocs.teanekocore.actuator.timer.FixedPointTimer
     * @param taskName 任务名称
     * @param taskStage 任务阶段
     * @param callable 任务执行的 Callable
     * @param cronExpression 定时器的 cron 表达式
     * @param resultType 任务结果类型
     * @return {@link ITimerTaskConfig }<{@link T }> 定时器任务配置对象，用于设置任务的 Future 执行链与生命周期
     */
    <T> ITimerTaskConfig<T> registerByCron(String taskName,
                                           String taskStage,
                                           MethodCallable<ITaskResult<T>> callable,
                                           String cronExpression,
                                           Class<T> resultType);
}
