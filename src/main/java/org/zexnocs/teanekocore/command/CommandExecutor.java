package org.zexnocs.teanekocore.command;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.actuator.task.TaskResult;
import org.zexnocs.teanekocore.actuator.task.TaskStages;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskService;
import org.zexnocs.teanekocore.command.interfaces.ICommandExecutor;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * 在原任务阶段中调用命令，再非阻塞地等待命令返回的 Stage。
 * <br>2026/11/02：插件命令在实际启动与异步完成时复核插件版本。
 *
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.0.0
 * @version 4.6.0-l
 */
@Service("commandExecutor")
public class CommandExecutor implements ICommandExecutor {
    public static final String DEFAULT_TASK_NAMESPACE = "command-executor-task-namespace";
    private final ITaskService taskService;
    // 2026/11/02：可选装配守卫，不改变旧直接构造方式。
    private org.zexnocs.teanekocore.plugin.PluginAccess pluginAccess;

    /**
     * 注入统一插件归属。
     * @param access 插件入口
     */
    @org.springframework.beans.factory.annotation.Autowired
    public void installPluginAccess(org.zexnocs.teanekocore.plugin.PluginAccess access) { this.pluginAccess = access; }

    /**
     * 注入既有任务服务，让反射调用沿原执行域启动并等待处理方法返回阶段。
     *
     * @param taskService 由装配方提供的 {@code ITaskService} 协作依赖
     */
    public CommandExecutor(ITaskService taskService) {
        this.taskService = taskService;
    }

    /**
     * 反射方法启动后的完成阶段封装，用于展开异步业务结果。
     *
     * @param stage 需要观察或组合的完成阶段
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
     * @version 4.5.0-l
     */
    private record Invocation(CompletionStage<?> stage) {}

    /**
     * 保留原事件、权限与任务执行域，展开反射方法返回的完成阶段，等待真实业务完成后返回结果。
     *
     * @param object 用于本次处理的 {@code object} 数据
     * @param method 已选定的目标方法
     * @param args 待绑定的命令参数
     * @param taskNamespace 用于本次处理的 {@code taskNamespace} 数据
     * @return 承载本次异步处理实际结果的可组合完成阶段
     */
    @Override
    public CompletionStage<Object> execute(Object object, Method method, Object[] args, String taskNamespace) {
        return execute(object, method, args, taskNamespace, null);
    }
    /**
     * 真正启动方法前检查借用父控制，避免事件结束后排队期间已取消的命令仍产生副作用。
     * @param object 原命令对象
     * @param method 原处理方法
     * @param args 已授权并绑定的原参数
     * @param taskNamespace 原任务执行域
     * @param parent 可选派生父控制，不由执行器关闭
     * @return 方法和真实后续业务完成阶段
     */
    @Override public CompletionStage<Object> execute(Object object, Method method, Object[] args, String taskNamespace, @Nullable ExecutionControl parent) {
        try {
            CommandMethodContract.requireAsync(method);
            var namespace = taskNamespace == null || taskNamespace.isBlank()
                    ? DEFAULT_TASK_NAMESPACE : taskNamespace;
            var task = taskService.subscribeWithFuture("指令执行", namespace, () -> {
                if (parent != null) { parent.checkActive(); }
                // 2026/11/02：捕获实际调用时的归属版本，异步结果也必须复核。
                var token = pluginAccess == null ? null : pluginAccess.capture(object, method);
                final Object returned;
                try {
                    returned = method.invoke(object, args);
                } catch (InvocationTargetException e) {
                    if (e.getCause() instanceof Exception cause) throw cause;
                    if (e.getCause() instanceof Error cause) throw cause;
                    throw e;
                }
                if (!(returned instanceof CompletionStage<?> stage)) {
                    throw new IllegalStateException("Command returned no CompletionStage: " + method);
                }
                var guarded = stage.thenApply(value -> {
                    if (parent != null) { parent.checkActive(); }
                    if (pluginAccess != null) { pluginAccess.check(token); }
                    return value;
                });
                return new TaskResult<>(true, new Invocation(guarded));
            }, Invocation.class);
            return TaskStages.successful(task, "指令调用")
                    .thenCompose(invocation -> invocation.stage().thenApply(value -> (Object) value));
        } catch (Exception e) {
            return CompletableFuture.failedStage(e);
        }
    }
}
