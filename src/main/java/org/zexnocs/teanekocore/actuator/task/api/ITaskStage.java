package org.zexnocs.teanekocore.actuator.task.api;

import org.zexnocs.teanekocore.actuator.task.TaskStageChain;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskResult;

/**
 * 任务执行阶段。
 * 实现该接口必须要加上注解 @TaskStage 才可以被扫描到。
 * @see TaskStage
 * @author zExNocs
 * @date 2026/02/11
 */
public interface ITaskStage {
    /**
     * 处理任务。
     * 1. 前置处理
     * 2. chain.next() 调用下一个阶段
     * 3. 后置处理
     * 4. 返回 chain.next() 的 Future
     * @param chain 该任务的阶段链
     * @return 返回主运行任务的 CompletableFuture 对，分别表示 future 链的 头和尾
     */
    ITaskResult<?> process(TaskStageChain chain);
}
