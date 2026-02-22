package org.zexnocs.teanekocore.actuator.task;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.actuator.task.api.SetTaskStage;
import org.zexnocs.teanekocore.actuator.task.exception.TaskIllegalStateException;
import org.zexnocs.teanekocore.actuator.task.exception.TaskNotFoundException;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITask;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskExecuteService;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskService;
import org.zexnocs.teanekocore.actuator.task.state.TaskCreatedState;
import org.zexnocs.teanekocore.actuator.task.state.TaskExecutedState;
import org.zexnocs.teanekocore.actuator.task.state.TaskSubmittedState;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekocore.utils.MethodCallableUtils;

import java.util.Optional;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 任务执行服务。
 * v4.0.8: 新增 AOP 处理 TaskStage namespace
 *
 * @author zExNocs
 * @date 2026/02/11
 * @since 4.0.0
 */
@Service
public class TaskExecuteService implements ITaskExecuteService {
    /// 线程池
    private final ScheduledThreadPoolExecutor taskExecutor;

    /// logger
    private final ILogger logger;

    /// task stage scanner
    private final TaskStageScanner taskStageScanner;
    private final ITaskService iTaskService;

    @Autowired
    public TaskExecuteService(ScheduledThreadPoolExecutor taskExecutor,
                              ILogger logger,
                              TaskStageScanner taskStageScanner,
                              ITaskService iTaskService) {
        this.taskExecutor = taskExecutor;
        this.logger = logger;
        this.taskStageScanner = taskStageScanner;
        this.iTaskService = iTaskService;
    }

    /**
     * 将任务交给线程池执行。此时 Task 的状态为 Submitted。
     *
     * @param task 任务
     * @throws TaskIllegalStateException 如果该任务已经执行过，则不允许再次执行
     */
    @Override
    public void __executeTask(ITask<?> task) {
        logger.debug(this.getClass().getSimpleName(),
                "尝试执行任务：%s".formatted(task.getConfig().getName()));
        // 任务处于 Created 状态才允许执行
        if(!task.switchStateUnderExpected(TaskCreatedState.class, new TaskSubmittedState())) {
            logger.errorWithReport(this.getClass().getSimpleName(),
                    "订阅任务：%s 在准备执行时发生异常，可能是重复提交任务".formatted(task.getConfig().getName()),
                    new TaskIllegalStateException("重复提交任务",
                            task.getCurrentState().getClass(), TaskCreatedState.class));
            return;
        }
        // 执行任务，间隔是 delayDuration
        task.setExecutingFuture(
                taskExecutor.schedule(() -> _executeTask(task, _getTaskStageChain(task)),
                        task.getConfig().getDelayDuration().toMillis(), TimeUnit.MILLISECONDS));
    }

    /**
     * 重试任务。由 RetryTaskService 调用。
     * 最大区别是执行的间隔是 retryInterval，而不是 delayDuration。
     *
     * @param task 任务
     */
    @Override
    public void __executeTaskRetry(ITask<?> task) {
        logger.warn(this.getClass().getSimpleName(),
                "尝试重试任务：%s，当前重试次数：%d / %d"
                        .formatted(task.getConfig().getName(),
                                task.getCurrentRetryCount(),
                                task.getConfig().getMaxRetries()));
        // 任务处于 Created 状态才允许执行
        if(!task.switchStateUnderExpected(TaskCreatedState.class, new TaskSubmittedState())) {
            logger.errorWithReport(this.getClass().getSimpleName(),
                    "订阅任务：%s 在准备重试执行时发生异常，可能是重复提交任务".formatted(task.getConfig().getName()),
                    new TaskIllegalStateException("重复提交任务",
                            task.getCurrentState().getClass(), TaskCreatedState.class));
            return;
        }
        // 执行任务，间隔是 retryInterval
        task.setExecutingFuture(
                taskExecutor.schedule(() -> _executeTask(task, _getTaskStageChain(task)),
                        task.getConfig().getRetryInterval().toMillis(), TimeUnit.MILLISECONDS));
    }

    /// 构造任务的 stage chain。
    private TaskStageChain _getTaskStageChain(ITask<?> task) {
        // 获取任务的阶段列表
        var config = task.getConfig();
        var taskStages = config.getTaskStages();
        // 如果列表为 null，则尝试从命名空间中获取。
        if (taskStages == null) {
            String namespace = Optional
                    .ofNullable(MethodCallableUtils.INSTANCE.getAnnotation(config.getCallable(), SetTaskStage.class))
                    .map(SetTaskStage::value)
                    .orElseGet(config::getTaskStageNamespace);
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
            if(result != null) {
                try {
                    iTaskService.complete(task.getKey(), result);
                } catch (TaskNotFoundException e) {
                    // 任务已经过期了
                    logger.errorWithReport(this.getClass().getSimpleName(),
                            "尝试完成任务时发生错误，请增加任务过期时间，未找到对应的任务："
                                    + task.getConfig().getName(), e);
                }
            }
        } catch (Exception e) {
            // 执行任务阶段链时发生异常，将 task 设置为 Executed
            task.switchState(new TaskExecutedState());
            try {
                iTaskService.completeExceptionally(task.getKey(), e);
            } catch (TaskNotFoundException ignore) {
                // 任务已经过期了
                logger.errorWithReport(this.getClass().getSimpleName(),
                        "尝试以异常方式完成任务时发生错误，请增加任务过期时间，未找到对应的任务："
                                + task.getConfig().getName(), e);
            }
        }
    }
}
