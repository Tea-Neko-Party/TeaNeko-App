package org.zexnocs.teanekocore.actuator.task.interfaces;

import org.zexnocs.teanekocore.actuator.task.exception.TaskRepeatedExecutionException;

/**
 * 任务执行服务接口。
 *
 * @author zExNocs
 * @date 2026/02/11
 */
public interface ITaskExecuteService {
    /**
     * 将任务交给线程池执行。
     * @param task 任务
     * @throws TaskRepeatedExecutionException 如果该任务已经执行过，则不允许再次执行
     */
    void executeSubscriptionTask(ITask<?> task) throws TaskRepeatedExecutionException;
}
