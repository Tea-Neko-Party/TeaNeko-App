package org.zexnocs.teanekocore.actuator.timer.interfaces;

import org.jspecify.annotations.NonNull;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskService;

/**
 * Timer 任务的配置接口，用于周期性地根据 taskConfig 来生成 task。
 * 执行顺序是 isTime → update → execute。
 *
 * @author zExNocs
 * @date 2026/02/14
 */
public interface ITimer<T> {
    /**
     * 获取 TimerTaskConfig
     *
     * @return 任务配置。
     */
    @NonNull
    ITimerTaskConfig<T> getTimerTaskConfig();

    /**
     * 判断是否到了执行时间。
     *
     * @param currentTime 当前时间戳（毫秒）。
     * @return 是否到了执行时间。
     */
    boolean isTime(long currentTime);

    /**
     * 每次执行成功时更新定时器的状态
     * @param currentTime 当前时间戳（毫秒）。
     */
    void update(long currentTime);

    /**
     * 使用 iTaskService 来执行任务。
     * 同时使用 timerTaskConfig 中的 TaskFutureChain 来配置任务的 future 链
     * @param iTaskService 任务服务。
     */
    void execute(ITaskService iTaskService);
}
