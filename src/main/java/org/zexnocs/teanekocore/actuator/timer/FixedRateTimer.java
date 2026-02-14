package org.zexnocs.teanekocore.actuator.timer;

import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.actuator.timer.interfaces.ITimer;
import org.zexnocs.teanekocore.actuator.timer.interfaces.ITimerTaskConfig;

import java.time.Duration;

/**
 * 每经过一个 rate 都会生成一个新的 task 来执行，无论上一个 task 是否完成。
 * 执行时间：rate
 * 不保证任务只有一个线程执行。
 *
 * @author zExNocs
 * @date 2026/02/14
 */
public class FixedRateTimer<T> implements ITimer<T> {
    /// 任务配置
    private final ITimerTaskConfig<T> timerTaskConfig;

    /// 任务 rate
    private final Duration rate;

    /**
     * 构造函数。
     * @param timerTaskConfig 任务配置
     * @param rate 任务 rate
     */
    public FixedRateTimer(ITimerTaskConfig<T> timerTaskConfig, Duration rate) {
        this.timerTaskConfig = timerTaskConfig;
        this.rate = rate;
    }

    /**
     * 获取 TimerTaskConfig
     *
     * @return 任务配置。
     */
    @Override
    public ITimerTaskConfig<T> getTimerTaskConfig() {
        return null;
    }

    /**
     * 每次执行成功时更新定时器的状态 或者对任务的 future 链进行配置。
     * 比如说使用 timerTaskConfig 中的 TaskFutureChain 来配置任务的 future 链。
     *
     * @param currentTime 当前时间戳（毫秒）。
     * @param taskFuture  当前执行的任务的 future 对象，可以通过它来配置任务的 future 链。
     */
    @Override
    public void update(long currentTime, TaskFuture<T> taskFuture) {

    }

    /**
     * 判断是否到了执行时间。
     *
     * @param currentTime 当前时间戳（毫秒）。
     * @return 是否到了执行时间。
     */
    @Override
    public boolean isTime(long currentTime) {
        return false;
    }
}
