package org.zexnocs.teanekocore.command.interfaces;

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import java.lang.reflect.Method;
import java.util.concurrent.CompletionStage;

/**
 * 指令执行器接口。
 * 在原任务阶段中调用处理方法，再非阻塞地等待其 CompletionStage。
 *
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.0.0
 * @version 4.5.0-l
 */
public interface ICommandExecutor {
    /**
     * 执行指令。
     *
     * @param object 指令执行对象
     * @param method 指令方法
     * @param args 指令参数
     * @param taskNamespace 指令命名空间
     * @return 承载本次异步处理实际结果的可组合完成阶段
     */
    CompletionStage<Object> execute(Object object, Method method, Object[] args, String taskNamespace);

    /**
     * 保留派生执行控制，实际异步执行器应在任务真正开始前再次检查停止。
     * @param object 命令对象
     * @param method 原处理方法
     * @param args 原已绑定参数
     * @param taskNamespace 原任务域
     * @param parent 借用父控制，普通原生命令可为空
     * @return 真实方法及后续 Stage 的完成信号
     * @throws org.zexnocs.teanekocore.actuator.execution.ExecutionStoppedException 当借用父控制已停止时
     */
    default CompletionStage<Object> execute(Object object, Method method, Object[] args, String taskNamespace, @Nullable ExecutionControl parent) {
        if (parent != null) { parent.checkActive(); }
        return execute(object, method, args, taskNamespace);
    }
}
