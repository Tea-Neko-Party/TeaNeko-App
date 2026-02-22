package org.zexnocs.teanekocore.actuator.task.api;

import org.zexnocs.teanekocore.actuator.task.TaskStageChain;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskResult;

/**
 * 任务执行阶段。
 * 实现该接口必须要加上注解 {@code @TaskStage} 才可以被扫描到。
 *
 * @see TaskStage
 * @see TaskStageChain
 * @author zExNocs
 * @date 2026/02/11
 * @since 4.0.0
 */
public interface ITaskStage {
    /**
     * 处理任务。
     * 1. 前置处理
     * 2. {@code chain.next()} 调用下一个阶段
     * 3. 后置处理
     * 4. 返回 {@code chain.next()} 的 {@code Future}
     *
     * @param chain 该任务的阶段链
     * @return 返回最终运行得到的 {@link ITaskResult}
     */
    ITaskResult<?> process(TaskStageChain chain);
}
