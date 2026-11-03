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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 任务执行服务。
 * v4.0.8: 新增 AOP 处理 TaskStage namespace
 * <br>2026/11/02：同步任务阶段接入插件启停检查，原调用栈保持单线程。
 * <br>2026/11/03：观测排队、同步阶段和外部结果等待，不改变阶段推进线程。
 *
 * @author zExNocs
 * @date 2026/02/11
 * @since 4.0.0
 * @version 4.7.0-l
 */
@Service
public class TaskExecuteService implements ITaskExecuteService {
    private org.zexnocs.teanekocore.actuator.runtime.@org.jspecify.annotations.Nullable RuntimeMonitor monitor;

    /**
     * 2026/11/03：安装只读任务阶段观测。
     * @param monitor 有界进程窗口
     */
    @Autowired
    public void installMonitor(org.zexnocs.teanekocore.actuator.runtime.RuntimeMonitor monitor) {
        this.monitor = monitor;
    }

    /**
     * @param task 原任务
     * @param state 原执行状态
     * @param stage 当前阶段
     */
    private void observed(ITask<?> task, String state, String stage) {
        if (monitor != null) {
            monitor.phase(task.getKey(), state, stage);
        }
    }
    // 2026/11/02：插件只包装阶段调用，不在异步回调中推进同步游标。
    private org.zexnocs.teanekocore.plugin.PluginAccess pluginAccess;

    /**
     * 安装任务阶段归属守卫。
     * @param access 插件入口
     */
    @Autowired
    public void installPluginAccess(org.zexnocs.teanekocore.plugin.PluginAccess access) { this.pluginAccess = access; }

    /**
     * 包装插件阶段，禁用时继续下一阶段，宿主阶段沿用原对象。
     * @param stage 原同步阶段
     * @return 当前任务独立的调用适配
     */
    private org.zexnocs.teanekocore.actuator.task.api.ITaskStage guardedStage(org.zexnocs.teanekocore.actuator.task.api.ITaskStage stage) {
        if (pluginAccess == null) { return stage; }
        return chain -> {
            try {
                var method = stage.getClass().getMethod("process", TaskStageChain.class);
                if (!pluginAccess.enabled(stage, method)) { return chain.next(); }
                var token = pluginAccess.capture(stage, method);
                var result = stage.process(chain); pluginAccess.check(token); return result;
            } catch (RuntimeException failure) { throw failure; }
            catch (Exception failure) { throw new IllegalStateException("插件任务阶段执行失败。", failure); }
        };
    }
    /// 虚拟线程池
    private final ExecutorService virtualExecutor;

    /// logger
    private final ILogger logger;

    /// task stage scanner
    private final TaskStageScanner taskStageScanner;

    /// task service
    private final ITaskService iTaskService;

    /**
     * 2026/11/02：说明原任务依赖，不创建第二套线程或阶段目录。
     * @param virtualExecutor 原执行器
     * @param logger 项目日志
     * @param taskStageScanner 阶段目录
     * @param iTaskService 原完成服务
     */
    @Autowired
    public TaskExecuteService(ExecutorService virtualExecutor,
                              ILogger logger,
                              TaskStageScanner taskStageScanner,
                              ITaskService iTaskService) {
        this.virtualExecutor = virtualExecutor;
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
        // 2026/11/03：排队与真正开始分开观测。
        observed(task, "QUEUED", "等待首次执行");
        // 执行任务，间隔是 delayDuration
        var future = CompletableFuture.runAsync(
                () -> _executeTask(task, _getTaskStageChain(task)),
                CompletableFuture.delayedExecutor(
                        task.getConfig().getDelayDuration().toMillis(),
                        TimeUnit.MILLISECONDS,
                        virtualExecutor
                )
        );
        task.setExecutingFuture(future);
    }

    /**
     * 重试任务。由 RetryTaskService 调用。
     * 最大区别是执行的间隔是 retryInterval，而不是 delayDuration。
     *
     * @param task 任务
     */
    @Override
    public void __executeTaskRetry(ITask<?> task) {
        // 第一次重试不再 warn；超过再 warn
        if(task.getCurrentRetryCount() >= 2) {
            logger.warn(this.getClass().getSimpleName(),
                    "尝试重试任务：%s，当前重试次数：%d / %d"
                            .formatted(task.getConfig().getName(),
                                    task.getCurrentRetryCount(),
                                    task.getConfig().getMaxRetries()));
        }
        // 任务处于 Created 状态才允许执行
        if(!task.switchStateUnderExpected(TaskCreatedState.class, new TaskSubmittedState())) {
            logger.errorWithReport(this.getClass().getSimpleName(),
                    "订阅任务：%s 在准备重试执行时发生异常，可能是重复提交任务".formatted(task.getConfig().getName()),
                    new TaskIllegalStateException("重复提交任务",
                            task.getCurrentState().getClass(), TaskCreatedState.class));
            return;
        }
        // 2026/11/03：重试仍属于同一个原任务回执。
        observed(task, "QUEUED", "等待重试");
        // 执行任务，间隔是 retryInterval
        var future = CompletableFuture.runAsync(
                () -> _executeTask(task, _getTaskStageChain(task)),
                CompletableFuture.delayedExecutor(
                        task.getConfig().getRetryInterval().toMillis(),
                        TimeUnit.MILLISECONDS,
                        virtualExecutor
                )
        );
        task.setExecutingFuture(future);
    }

    /**
     * 2026/11/02：构造本次任务的单线程阶段链并安装插件守卫。
     * @param task 当前任务
     * @return 本次独立阶段链
     */
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
        // 2026/11/02：每次任务使用独立包装，不把启停快照写回共享配置。
        // 2026/11/03：观测仅同步通知，不改变阶段链单线程游标。
        return new TaskStageChain(task, taskStages.stream().map(this::guardedStage).toList(),
                stage -> observed(task, "RUNNING", stage));
    }

    /**
     * 在当前调用栈执行任务并提交同步结果，空结果保留外部异步完成方式。
     * @param task 当前任务
     * @param taskStageChain 本次独立链
     */
    private void _executeTask(ITask<?> task, TaskStageChain taskStageChain) {
        // 2026/11/03：Callable 返回空值时仍等待外部实际结果，不能展示为完成。
        observed(task, "RUNNING", "进入同步阶段链");
        try {
            // 执行任务。
            var result = taskStageChain.next();
            // 成功获取到 result，将 task 设置为 Executed
            task.switchState(new TaskExecutedState());
            observed(task, "WAITING_RESULT", "等待原任务结果");
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
