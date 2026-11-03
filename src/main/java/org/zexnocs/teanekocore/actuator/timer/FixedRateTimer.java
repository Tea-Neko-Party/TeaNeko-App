package org.zexnocs.teanekocore.actuator.timer;

import lombok.Getter;
import org.jspecify.annotations.NonNull;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskService;
import org.zexnocs.teanekocore.actuator.timer.interfaces.ITimer;
import org.zexnocs.teanekocore.actuator.timer.interfaces.ITimerTaskConfig;

import java.time.Duration;
import java.time.Instant;

/**
 * 每经过一个 rate 都会生成一个新的 task 来执行，无论上一个 task 是否完成。
 * 执行时间：rate
 * 不保证任务只有一个线程执行。
 *
 * 2026/11/03：提供原规则的只读预计触发与等待状态。
 *
 * @param <T> 任务结果类型
 * @author zExNocs
 * @date 2026/02/14
 * @since 4.0.0
 * @version 4.7.0-l
 */
public class FixedRateTimer<T> implements ITimer<T> {
    /**
     * 2026/11/03：返回实际规则，等待上次完成时不编造预计时间。
     * @return 本计时器的近时观测
     */
    @Override
    public TimerObservation observation() {
        return new TimerObservation("FIXED_RATE", "固定触发间隔：" + rate, lastExecutionTime.plus(rate), false, "UTC");
    }

    /// 任务配置
    private final ITimerTaskConfig<T> timerTaskConfig;

    /// 任务 rate
    private final Duration rate;

    /// 上次执行的时间
    // 2026/11/03：调度线程发布的新时刻供只读观测读取。
    private volatile Instant lastExecutionTime;

    /// result type
    @Getter
    private final Class<T> resultType;

    /**
     * 构造函数。
     * @param timerTaskConfig 任务配置
     * @param rate 任务 rate
     * @param resultType 任务结果类型
     */
    public FixedRateTimer(@NonNull ITimerTaskConfig<T> timerTaskConfig, @NonNull Duration rate, @NonNull Class<T> resultType) {
        this.timerTaskConfig = timerTaskConfig;
        this.rate = rate;
        this.lastExecutionTime = Instant.now();
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
        return !currentTime.isBefore(lastExecutionTime.plus(rate));
    }

    /**
     * 每次执行成功时更新定时器的状态
     *
     * @param currentTime 当前时间点。
     */
    @Override
    public void update(Instant currentTime) {
        this.lastExecutionTime = currentTime;
    }

    /**
     * 使用 iTaskService 来执行任务。
     * 同时使用 timerTaskConfig 中的 TaskFutureChain 来配置任务的 future 链
     *
     * @param iTaskService 任务服务。
     */
    @Override
    public void execute(ITaskService iTaskService) {
        var taskFuture = iTaskService.subscribeWithFuture(timerTaskConfig.getTaskConfig(), resultType);
        // 配置任务的 future 链。
        var chain = timerTaskConfig.getTaskFutureChain();
        if(chain != null) {
            var finalFuture = chain.apply(taskFuture);
            finalFuture.finish();
        } else {
            taskFuture.finish();
        }
    }
}
