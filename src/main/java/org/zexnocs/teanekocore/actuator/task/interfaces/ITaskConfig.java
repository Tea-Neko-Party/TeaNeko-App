package org.zexnocs.teanekocore.actuator.task.interfaces;

import org.zexnocs.teanekocore.actuator.task.api.ITaskStage;

import java.util.List;

/**
 * 用于定义一个任务的基本配置，从而来组装一个 Task 实例。
 *
 * @author zExNocs
 * @date 2026/02/10
 */
public interface ITaskConfig<T> {
    /**
     * 获取任务执行阶段列表
     * @return 任务执行阶段列表，按照执行顺序排列
     */
    List<ITaskStage> getTaskStages();

    /**
     * 设置任务执行阶段列表
     * @param taskStages 任务执行阶段列表，按照执行顺序排列
     */
    void setTaskStages(List<ITaskStage> taskStages);

    /**
     * 获取任务阶段连命名空间
     * 用于注入阶段链列表
     */
    String getTaskStageNamespace();
}
