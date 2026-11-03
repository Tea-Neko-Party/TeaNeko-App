package org.zexnocs.teanekocore.actuator.timer.interfaces;

import org.jspecify.annotations.NonNull;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskService;

import java.time.Instant;

/**
 * Timer 任务的配置接口，用于周期性地根据 taskConfig 来生成 task。
 * 执行顺序是 isTime → update → execute。
 * <br>2026/11/03：提供规则观测默认值，旧扩展无需伪造下一次触发时间。
 *
 * @param <T> 原任务结果类型
 * @author zExNocs
 * @date 2026/02/14
 * @since 4.0.0
 * @version 4.7.0-l
 */
public interface ITimer<T> {
    /**
     * 2026/11/03：扩展计时器可覆盖此方法贡献只读规则，不进行触发或网络操作。
     * @return 未贡献描述时保持未知时间
     */
    default org.zexnocs.teanekocore.actuator.timer.TimerObservation observation() {
        return new org.zexnocs.teanekocore.actuator.timer.TimerObservation("CUSTOM", "自定义规则，未提供预计时间", null, false, "");
    }
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
     * @param currentTime 当前时间点。
     * @return 是否到了执行时间。
     */
    boolean isTime(Instant currentTime);

    /**
     * 每次执行成功时更新定时器的状态
     * @param currentTime 当前时间点。
     */
    void update(Instant currentTime);

    /**
     * 使用 iTaskService 来执行任务。
     * 同时使用 timerTaskConfig 中的 TaskFutureChain 来配置任务的 future 链
     * @param iTaskService 任务服务。
     */
    void execute(ITaskService iTaskService);
}
