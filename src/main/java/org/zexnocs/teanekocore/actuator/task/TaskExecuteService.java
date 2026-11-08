package org.zexnocs.teanekocore.actuator.task;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.actuator.task.exception.TaskIllegalStateException;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITask;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskExecuteService;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskService;
import org.zexnocs.teanekocore.actuator.task.state.TaskCreatedState;
import org.zexnocs.teanekocore.actuator.task.state.TaskExecutedState;
import org.zexnocs.teanekocore.actuator.task.state.TaskSubmittedState;
import org.zexnocs.teanekocore.logger.ILogger;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 任务执行服务。
 * v4.0.8: 新增 AOP 处理 TaskStage namespace
 * <br>2026/11/02：同步任务阶段接入插件启停检查，原调用栈保持单线程。
 * <br>2026/11/03：观测排队、同步阶段和外部结果等待，不改变阶段推进线程。
 * <br>2026/11/07：使用可撤销的排队调度，运行尝试实际退出后才归还占用。
 * <br>2026/11/07：增加插件归属与有效状态约束。
 *
 * @author zExNocs
 * @date 2026/02/11
 * @since 4.0.0
 * @version 4.9.0-l
 */
@Service
public class TaskExecuteService implements ITaskExecuteService {
    private @org.jspecify.annotations.Nullable TaskPluginBindings pluginBindings;

    /**
     * 2026/11/07：安装注册时形成的任务归属缓存，执行路径不发现回调注解。
     * @param bindings 任务插件归属与固定代次
     */
    @Autowired
    public void installPluginBindings(TaskPluginBindings bindings) {
        this.pluginBindings = bindings;
    }
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
     * 2026/11/07：只读取初始化或配置创建时冻结的归属，不在执行路径解析方法。
     * @param stage 原同步阶段
     * @return 当前任务独立的调用适配
     */
    private org.zexnocs.teanekocore.actuator.task.api.ITaskStage guardedStage(
            org.zexnocs.teanekocore.actuator.task.api.ITaskStage stage) {
        if (pluginAccess == null) {
            return stage;
        }
        if (!(stage instanceof TaskStageBinding binding)) {
            throw new IllegalStateException("手动任务阶段必须在配置创建或显式注册时冻结归属。");
        }
        String owner = binding.pluginId();
        if (owner == null) {
            return stage;
        }
        return chain -> {
            try {
                if (!pluginAccess.enabledOwner(owner)) {
                    return chain.next();
                }
                // 2026/11/07：阶段可附着在宿主任务上，占用须覆盖该任务的实际外部工作终态。
                var call = pluginAccess.openOwner(owner);
                call.track(chain.getTask().getExecution().completion());
                var result = binding.process(chain);
                call.check();
                return result;
            } catch (RuntimeException failure) {
                throw failure;
            } catch (Exception failure) {
                throw new IllegalStateException("插件任务阶段执行失败。", failure);
            }
        };
    }
    /// 虚拟线程池
    private final ExecutorService virtualExecutor;
    private final ScheduledExecutorService scheduler;

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
     * @param scheduler 可撤销延迟任务的借用调度器
     */
    @Autowired
    public TaskExecuteService(ExecutorService virtualExecutor,
                              ILogger logger,
                              TaskStageScanner taskStageScanner,
                              ITaskService iTaskService,
                              @Qualifier("taskExecutor") ScheduledExecutorService scheduler) {
        this.virtualExecutor = virtualExecutor;
        this.scheduler = scheduler;
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
        // 2026/11/07：关闭与首次提交竞争时按取消收束，不把已撤销任务误报为重复订阅。
        task.getExecution().control().checkActive();
        logger.debug(this.getClass().getSimpleName(),
            "尝试执行任务：%s".formatted(task.getConfig().getName()));
        // 任务处于 Created 状态才允许执行
        if (!task.switchStateUnderExpected(TaskCreatedState.class, new TaskSubmittedState())) {
            logger.errorWithReport(this.getClass().getSimpleName(),
                "订阅任务：%s 在准备执行时发生异常，可能是重复提交任务".formatted(task.getConfig().getName()),
                new TaskIllegalStateException("重复提交任务",
                    task.getCurrentState().getClass(), TaskCreatedState.class));
            return;
        }
        // 2026/11/03：排队与真正开始分开观测。
        observed(task, "QUEUED", "等待首次执行");
        schedule(task, task.getConfig().getDelayDuration());
    }

    /**
     * 重试任务。由 RetryTaskService 调用。
     * 最大区别是执行的间隔是 retryInterval，而不是 delayDuration。
     *
     * @param task 任务
     */
    @Override
    public void __executeTaskRetry(ITask<?> task) {
        task.getExecution().control().checkActive();
        // 第一次重试不再 warn；超过再 warn
        if (task.getCurrentRetryCount() >= 2) {
            logger.warn(this.getClass().getSimpleName(),
                "尝试重试任务：%s，当前重试次数：%d / %d"
                    .formatted(task.getConfig().getName(),
                        task.getCurrentRetryCount(),
                        task.getConfig().getMaxRetries()));
        }
        // 任务处于 Created 状态才允许执行
        if (!task.switchStateUnderExpected(TaskCreatedState.class, new TaskSubmittedState())) {
            logger.errorWithReport(this.getClass().getSimpleName(),
                "订阅任务：%s 在准备重试执行时发生异常，可能是重复提交任务".formatted(task.getConfig().getName()),
                new TaskIllegalStateException("重复提交任务",
                    task.getCurrentState().getClass(), TaskCreatedState.class));
            return;
        }
        // 2026/11/03：重试仍属于同一个原任务回执。
        observed(task, "QUEUED", "等待重试");
        schedule(task, task.getConfig().getRetryInterval());
    }

    /**
     * 2026/11/06：调度占用从排队持续到真实线程退出；取消排队不启动原 Callable。
     * @param task 原任务
     * @param delay 延迟时长
     */
    private void schedule(ITask<?> task, Duration delay) {
        // 2026/11/07：排队之前固定代次，重新启用不能恢复旧排队任务或旧重试。
        if (pluginBindings != null) {
            pluginBindings.capture(task);
        }
        var attempt = task.getExecution().prepare();
        var execution = new CompletableFuture<Void>();
        task.setExecutingFuture(execution);
        // 2026/11/06：排队被撤销时也收束旧执行观察，但不在等待超时就伪造实际退出。
        task.getExecution().completion().whenComplete((ignored, failure) -> execution.complete(null));
        try {
            var scheduled = scheduler.schedule(() -> {
                try {
                    virtualExecutor.execute(() -> {
                        try {
                            attempt.run(() -> _executeTask(task, _getTaskStageChain(task)));
                            execution.complete(null);
                        } catch (Throwable failure) {
                            execution.completeExceptionally(failure);
                            iTaskService.forceCompleteExceptionally(task.getKey(), failure);
                            task.getExecution().settled();
                        }
                    });
                } catch (RuntimeException failure) {
                    attempt.rejected();
                    execution.completeExceptionally(failure);
                    iTaskService.forceCompleteExceptionally(task.getKey(), failure);
                    task.getExecution().settled();
                }
            }, delay.toMillis(), TimeUnit.MILLISECONDS);
            attempt.scheduled(scheduled);
        } catch (RuntimeException failure) {
            attempt.rejected();
            execution.completeExceptionally(failure);
            iTaskService.forceCompleteExceptionally(task.getKey(), failure);
            task.getExecution().settled();
        }
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
            // 2026/11/07：命名空间在配置创建时固定，此处只查询业务扫描器发布的快照。
            taskStages = taskStageScanner.getTaskStages(config.getTaskStageNamespace());
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
        observed(task, "RUNNING", "进入同步阶段链");
        try {
            task.getExecution().control().checkActive();
            // 2026/11/07：实际线程进入及同步回调完成时复核原插件代次。
            if (pluginBindings != null) {
                pluginBindings.check(task);
            }
            var result = taskStageChain.next();
            if (result == null) {
                task.getExecution().waitingExternal();
            }
            if (pluginBindings != null) {
                try {
                    pluginBindings.check(task);
                } catch (org.zexnocs.teanekocore.actuator.task.exception.TaskNoRetryRuntimeException failure) {
                    // 2026/11/07：已启动的外部工作仍占用资源，撤销结果资格不能伪造物理结束。
                    if (result == null) {
                        iTaskService.forceCompleteExceptionally(task.getKey(), failure);
                        return;
                    }
                    throw failure;
                }
            }
            if (task.getExecution().stopped()) {
                if (result != null) {
                    task.getExecution().settled();
                }
                return;
            }
            task.switchState(new TaskExecutedState());
            observed(task, "WAITING_RESULT", "等待原任务结果");
            if (result != null) {
                iTaskService.complete(task.getKey(), result);
            }
        } catch (Exception failure) {
            if (task.getExecution().stopped()) {
                task.getExecution().settled();
                return;
            }
            task.switchState(new TaskExecutedState());
            iTaskService.completeExceptionally(task.getKey(), failure);
        }
    }
}
