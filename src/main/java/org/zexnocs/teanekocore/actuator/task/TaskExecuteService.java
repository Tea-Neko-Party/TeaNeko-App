package org.zexnocs.teanekocore.actuator.task;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.actuator.task.exception.TaskRepeatedExecutionException;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITask;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskExecuteService;

/**
 * 任务执行服务。
 *
 * @author zExNocs
 * @date 2026/02/11
 */
@Service
public class TaskExecuteService implements ITaskExecuteService {
    /// 线程池
    private final ThreadPoolTaskExecutor threadPoolTaskExecutor;

    @Autowired
    public TaskExecuteService(ThreadPoolTaskExecutor threadPoolTaskExecutor) {
        this.threadPoolTaskExecutor = threadPoolTaskExecutor;
    }


    /**
     * 将任务交给线程池执行。
     *
     * @param task 任务
     * @throws TaskRepeatedExecutionException 如果该任务已经执行过，则不允许再次执行
     */
    @Override
    public void executeSubscriptionTask(ITask<?> task) throws TaskRepeatedExecutionException {
        // todo
    }
}
