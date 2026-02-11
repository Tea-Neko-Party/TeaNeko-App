package org.zexnocs.teanekocore.actuator.task;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.actuator.task.exception.TaskRepeatedExecutionException;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITask;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskExecuteService;
import org.zexnocs.teanekocore.logger.ILogger;

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

    /// logger
    private final ILogger logger;
    private final TaskStageScanner taskStageScanner;

    @Autowired
    public TaskExecuteService(ThreadPoolTaskExecutor threadPoolTaskExecutor, ILogger logger, TaskStageScanner taskStageScanner) {
        this.threadPoolTaskExecutor = threadPoolTaskExecutor;
        this.logger = logger;
        this.taskStageScanner = taskStageScanner;
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

    /**
     * 构造任务的 stage chain。
     */
    private TaskStageChain _getTaskStageChain(ITask<?> task) {
        // 获取任务的阶段列表
        var config = task.getConfig();
        var taskStages = config.getTaskStages();
        // 如果列表为 null，则尝试从命名空间中获取。
        if (taskStages == null) {
            var namespace = config.getTaskStageNamespace();
            taskStages = taskStageScanner.getTaskStages(namespace);
            config.setTaskStages(taskStages);
        }
        return new TaskStageChain(task, taskStages);
    }

    /// 执行一个任务阶段链，并报错告未处理的异常。
    private void _executeTask(TaskStageChain taskStageChain) {
        try {
            // 执行任务。
            taskStageChain.next();
        } catch (Exception e) {
            logger.errorWithReport(
                    TaskExecuteService.class.getSimpleName(),
                    """
                    任务 {%s} 执行出现异常，并且没有阶段处理该异常。"""
                            .formatted(taskStageChain.getTaskName()),
                    e);
        }
    }
}
