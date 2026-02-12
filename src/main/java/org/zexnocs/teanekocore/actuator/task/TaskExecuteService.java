package org.zexnocs.teanekocore.actuator.task;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.actuator.task.exception.TaskIllegalStateException;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITask;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskExecuteService;
import org.zexnocs.teanekocore.actuator.task.state.TaskCreatedState;
import org.zexnocs.teanekocore.actuator.task.state.TaskExecutedState;
import org.zexnocs.teanekocore.actuator.task.state.TaskSubmittedState;
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

    /// task stage scanner
    private final TaskStageScanner taskStageScanner;

    @Autowired
    public TaskExecuteService(ThreadPoolTaskExecutor threadPoolTaskExecutor,
                              ILogger logger,
                              TaskStageScanner taskStageScanner) {
        this.threadPoolTaskExecutor = threadPoolTaskExecutor;
        this.logger = logger;
        this.taskStageScanner = taskStageScanner;
    }

    /**
     * 将任务交给线程池执行。此时 Task 的状态为 Submitted。
     *
     * @param task 任务
     * @throws TaskIllegalStateException 如果该任务已经执行过，则不允许再次执行
     */
    @Override
    public void executeSubscriptionTask(ITask<?> task) {
        // 任务处于 Created 状态才允许执行
        if(!task.switchStateUnderExpected(TaskCreatedState.class, new TaskSubmittedState())) {
            logger.errorWithReport(this.getClass().getSimpleName(), "订阅任务：%s 在准备执行时发生异常，可能是重复提交任务",
                    new TaskIllegalStateException("重复提交任务",
                            task.getCurrentState().getClass(), TaskCreatedState.class));
            return;
        }
        // todo
    }

    /// 构造任务的 stage chain。
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
    private void _executeTask(ITask<?> task, TaskStageChain taskStageChain) {
        try {
            // 执行任务。
            var result = taskStageChain.next();
            // 成功获取到 result，将 task 设置为 Executed
            task.switchState(new TaskExecutedState());
            // todo: 提交给 service
        } catch (Exception e) {
            // 执行任务阶段链时发生异常，将 task 设置为 Executed
            task.switchState(new TaskExecutedState());
            // todo: 提交给 service
        }
    }
}
