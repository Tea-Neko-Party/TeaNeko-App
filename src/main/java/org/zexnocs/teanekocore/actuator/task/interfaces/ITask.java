package org.zexnocs.teanekocore.actuator.task.interfaces;

/**
 * 任务接口，用于定义一个任务的基本行为。
 *
 * @author zExNocs
 * @date 2026/02/10
 */
public interface ITask<T> {
    /**
     * 获取任务配置
     * @return 任务配置对象
     */
    ITaskConfig<T> getConfig();
}
