package org.zexnocs.teanekocore.actuator.timer;

import lombok.Getter;
import org.jspecify.annotations.NonNull;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskService;
import org.zexnocs.teanekocore.actuator.timer.interfaces.ITimer;
import org.zexnocs.teanekocore.actuator.timer.interfaces.ITimerTaskConfig;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 固定 delay 的定时器。
 * 每次 task 完成后都会经过该 delay 来生成一个新的 task 来执行。
 * 完成不包括 taskChain 执行的时间。
 * 执行时间： 上一次 task 执行时间 + delay。
 * 保证任务永远只有一个线程执行。
 * 注意：如果任务没有提交会导致定时器永久无法执行。
 *
 * @param <T> 任务返回值类型
 * @author zExNocs
 * @date 2026/02/14
 */
public class FixedDelayTimer<T> implements ITimer<T> {
    /// 任务正在执行的标志，表示 lastExecutionTime 的值无效。
    private static final long EXECUTING = -1L;

    /// 任务配置
    private final ITimerTaskConfig<T> timerTaskConfig;

    /// 任务 delay
    private final Duration delay;

    /// 上次执行时间
    private final AtomicLong lastExecutionTime;

    /// result type
    @Getter
    private final Class<T> resultType;

    /**
     * 构造函数。
     *
     * @param config 任务配置
     * @param delay 任务 delay
     */
    public FixedDelayTimer(@NonNull ITimerTaskConfig<T> config,
                           @NonNull Duration delay,
                           @NonNull Class<T> resultType) {
        this.delay = delay;
        this.timerTaskConfig = config;
        this.lastExecutionTime = new AtomicLong(System.currentTimeMillis());
        this.resultType = resultType;
    }

    /**
     * 获取 TimerTaskConfig
     *
     * @return 任务配置。
     */
    @Override
    public @NonNull ITimerTaskConfig<T> getTimerTaskConfig() {
        return timerTaskConfig;
    }

    /**
     * 判断是否到了执行时间。
     *
     * @param currentTime 当前时间戳（毫秒）。
     * @return 是否到了执行时间。
     */
    @Override
    public boolean isTime(long currentTime) {
        long lastTime = lastExecutionTime.get();
        // 如果正在执行，则不触发。
        if(lastTime == EXECUTING) {
            return false;
        }
        return currentTime - lastTime >= delay.toMillis();
    }

    /**
     * 每次执行成功时更新定时器的状态
     *
     * @param currentTime 当前时间戳（毫秒）。
     */
    @Override
    public void update(long currentTime) {
        // 临时将 lastExecutionTime 设置为无穷大，防止在任务执行过程中定时器被重复触发。
        lastExecutionTime.set(EXECUTING);
    }

    /**
     * 使用 iTaskService 来执行任务。
     * 同时使用 timerTaskConfig 中的 TaskFutureChain 来配置任务的 future 链
     *
     * @param iTaskService 任务服务。
     */
    @Override
    public void execute(ITaskService iTaskService) {
        // 让任务更新定时器状态。
        var future = iTaskService.subscribeWithFuture(timerTaskConfig.getTaskConfig(), resultType)
                .whenComplete((v, t) -> lastExecutionTime.set(System.currentTimeMillis()));
        // 配置任务的 future 链。
        var chain = timerTaskConfig.getTaskFutureChain();
        if(chain != null) {
            var finalFuture = chain.apply(future);
            finalFuture.finish();
        } else {
            future.finish();
        }
    }
}
