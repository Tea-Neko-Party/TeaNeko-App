package org.zexnocs.teanekocore.actuator.task.interfaces;

/**
 * 任务执行服务接口。
 *
 * @author zExNocs
 * @date 2026/02/11
 * @since 4.0.0
 */
public interface ITaskExecuteService {
    /**
     * 将任务交给线程池执行。由 {@link ITaskService} 调用。
     *
     * @param task 任务
     */
    void __executeTask(ITask<?> task);

    /**
     * 重试任务。由 {@link ITaskRetryService} 调用。
     *
     * @param task 任务
     */
    void __executeTaskRetry(ITask<?> task);
}
