package org.zexnocs.teanekocore.command.interfaces;

import java.lang.reflect.Method;
import java.util.concurrent.CompletionStage;

/**
 * 指令执行器接口。
 * 在原任务阶段中调用处理方法，再非阻塞地等待其 CompletionStage。
 *
 * @author zExNocs
 * @date 2026/10/28
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
}
