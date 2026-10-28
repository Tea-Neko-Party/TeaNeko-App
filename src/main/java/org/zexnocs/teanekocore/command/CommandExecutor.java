package org.zexnocs.teanekocore.command;

import org.springframework.stereotype.Service;
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
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
@Service("commandExecutor")
public class CommandExecutor implements ICommandExecutor {
    public static final String DEFAULT_TASK_NAMESPACE = "command-executor-task-namespace";
    private final ITaskService taskService;

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
        try {
            CommandMethodContract.requireAsync(method);
            var namespace = taskNamespace == null || taskNamespace.isBlank()
                    ? DEFAULT_TASK_NAMESPACE : taskNamespace;
            var task = taskService.subscribeWithFuture("指令执行", namespace, () -> {
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
                return new TaskResult<>(true, new Invocation(stage));
            }, Invocation.class);
            return TaskStages.successful(task, "指令调用")
                    .thenCompose(invocation -> invocation.stage().thenApply(value -> (Object) value));
        } catch (Exception e) {
            return CompletableFuture.failedStage(e);
        }
    }
}
