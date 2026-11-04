package org.zexnocs.teanekocore.actuator.timer.interfaces;

import org.zexnocs.teanekocore.actuator.timer.TimerDirectory;

import java.util.List;

/**
 * 为原定时目录贡献其他调度器的只读观测，不注册或接管调度任务。
 * 实现注册为 Spring Bean，由原 scanner 发现；来源标识必须唯一。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.7.1-l
 * @version 4.7.1-l
 */
public interface ITimerViewSource {
    /** @return 稳定来源标识，不能使用宿主保留的 core */
    String source();

    /**
     * @param limit 最大观测项数，额外一项用于报告截断
     * @return 实际注册任务的只读快照，动作必须为空
     */
    List<TimerDirectory.View> snapshot(int limit);
}
