package org.zexnocs.teanekocore.actuator.timer;

import lombok.Getter;
import org.jspecify.annotations.NonNull;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskService;
import org.zexnocs.teanekocore.actuator.timer.interfaces.ITimer;
import org.zexnocs.teanekocore.actuator.timer.interfaces.ITimerTaskConfig;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 每个 rate 都会尝试生成一个 task 来执行；如果上次的 task 没有完成则会一直等待上次 task 完成。
 * 执行时间: max(rate, 上一次 task 执行时间)。
 * 完成不包括 taskChain 执行的时间
 * 保证任务永远只有一个线程执行。
 * 注意：如果任务没有提交会导致定时器永久无法执行。
 *
 * @author zExNocs
 * @date 2026/02/15
 */
public class SmartRateTimer<T> implements ITimer<T> {
    /// 任务配置
    private final ITimerTaskConfig<T> timerTaskConfig;

    /// 任务 delay
    private final Duration delay;

    /// 上次执行时间
    private long lastExecutionTime;

    /// 上次执行任务是否已经完成。true 表示上次执行的任务已经完成，false 表示上次执行的任务还在执行中。
    private final AtomicBoolean lastTaskCompleted;

    /// result type
    @Getter
    private final Class<T> resultType;

    /**
     * 构造函数。
     *
     * @param timerTaskConfig 任务配置
     * @param delay 任务 delay
     */
    public SmartRateTimer(@NonNull ITimerTaskConfig<T> timerTaskConfig,
                          @NonNull Duration delay,
                          @NonNull Class<T> resultType) {
        this.timerTaskConfig = timerTaskConfig;
        this.delay = delay;
        this.lastExecutionTime = System.currentTimeMillis();
        this.lastTaskCompleted = new AtomicBoolean(true);
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
        // 已经完成了并且当前时间已经超过了上次执行时间 + delay
        return lastTaskCompleted.get() && currentTime - lastExecutionTime >= delay.toMillis();
    }

    /**
     * 每次执行成功时更新定时器的状态
     *
     * @param currentTime 当前时间戳（毫秒）。
     */
    @Override
    public void update(long currentTime) {
        // 更新上次执行时间为当前时间
        lastExecutionTime = currentTime;

        // 任务开始执行时，设置 lastTaskCompleted 为 false
        lastTaskCompleted.set(false);
    }

    /**
     * 使用 iTaskService 来执行任务。
     * 同时使用 timerTaskConfig 中的 TaskFutureChain 来配置任务的 future 链
     *
     * @param iTaskService 任务服务。
     */
    @Override
    public void execute(ITaskService iTaskService) {
        // 当任务完成时，设置 lastTaskCompleted 为 true
        var future = iTaskService.subscribeWithFuture(timerTaskConfig.getTaskConfig(), resultType)
                .whenComplete((v, t) -> lastTaskCompleted.set(true));

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
