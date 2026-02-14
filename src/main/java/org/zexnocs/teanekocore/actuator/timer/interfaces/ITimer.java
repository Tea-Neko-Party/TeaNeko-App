package org.zexnocs.teanekocore.actuator.timer.interfaces;

import org.zexnocs.teanekocore.actuator.task.TaskFuture;

/**
 * Timer 任务的配置接口，用于周期性地根据 taskConfig 来生成 task。
 *
 * @author zExNocs
 * @date 2026/02/14
 */
public interface ITimer<T> {
    /**
     * 获取 TimerTaskConfig
     * @return 任务配置。
     */
    ITimerTaskConfig<T> getTimerTaskConfig();

    /**
     * 每次执行成功时更新定时器的状态 或者对任务的 future 链进行配置。
     * 比如说使用 timerTaskConfig 中的 TaskFutureChain 来配置任务的 future 链。
     * @param currentTime 当前时间戳（毫秒）。
     * @param taskFuture 当前执行的任务的 future 对象，可以通过它来配置任务的 future 链。
     */
    void update(long currentTime, TaskFuture<T> taskFuture);

    /**
     * 判断是否到了执行时间。
     *
     * @param currentTime 当前时间戳（毫秒）。
     * @return 是否到了执行时间。
     */
    boolean isTime(long currentTime);
}
