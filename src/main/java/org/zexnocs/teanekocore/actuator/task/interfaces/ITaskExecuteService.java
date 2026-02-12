package org.zexnocs.teanekocore.actuator.task.interfaces;

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
     */
    void executeSubscriptionTask(ITask<?> task);
}
