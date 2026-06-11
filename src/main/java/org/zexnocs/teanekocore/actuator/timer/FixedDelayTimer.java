package org.zexnocs.teanekocore.actuator.timer;

import lombok.Getter;
import org.jspecify.annotations.NonNull;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskService;
import org.zexnocs.teanekocore.actuator.timer.interfaces.ITimer;
import org.zexnocs.teanekocore.actuator.timer.interfaces.ITimerTaskConfig;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

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
    /// 任务配置
    private final ITimerTaskConfig<T> timerTaskConfig;

    /// 任务 delay
    private final Duration delay;

    /// 上次执行时间
    private final AtomicReference<Instant> lastExecutionTime;

    /// 任务是否正在执行。
    private final AtomicBoolean executing = new AtomicBoolean(false);

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
        this.lastExecutionTime = new AtomicReference<>(Instant.now());
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
     * @param currentTime 当前时间点。
     * @return 是否到了执行时间。
     */
    @Override
    public boolean isTime(Instant currentTime) {
        var lastTime = lastExecutionTime.get();
        // 如果正在执行，则不触发。
        if(executing.get()) {
            return false;
        }
        return !currentTime.isBefore(lastTime.plus(delay));
    }

    /**
     * 每次执行成功时更新定时器的状态
     *
     * @param currentTime 当前时间点。
     */
    @Override
    public void update(Instant currentTime) {
        executing.set(true);
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
                .whenComplete((v, t) -> {
                    lastExecutionTime.set(Instant.now());
                    executing.set(false);
                });
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
