package org.zexnocs.teanekocore.command;

import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.actuator.task.EmptyTaskResult;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskService;
import org.zexnocs.teanekocore.command.interfaces.ICommandExecutor;

import java.lang.reflect.Method;

/**
 * 指令执行器实现类，使用任务服务异步执行指令方法，确保指令执行不会阻塞主线程。
 *
 * @author zExNocs
 * @date 2026/02/18
 * @since 4.0.0
 */
@Service("commandExecutor")
public class CommandExecutor implements ICommandExecutor {
    public final static String DEFAULT_TASK_NAMESPACE = "command-executor-task-namespace";
    private final ITaskService iTaskService;

    public CommandExecutor(ITaskService iTaskService) {
        this.iTaskService = iTaskService;
    }

    /**
     * 执行指令。
     * @param object 指令执行对象
     * @param method 指令方法
     * @param args 指令参数
     * @param taskNamespace 指令命名空间
     */
    @Override
    public void execute(Object object, Method method, Object[] args, String taskNamespace) {
        if(taskNamespace == null || taskNamespace.isBlank()) {
            taskNamespace = DEFAULT_TASK_NAMESPACE;
        }
        iTaskService.subscribe(
                "指令执行",
                taskNamespace,
                () -> {
                    method.invoke(object, args);
                    return EmptyTaskResult.INSTANCE;
                },
                EmptyTaskResult.getResultType());
    }
}
