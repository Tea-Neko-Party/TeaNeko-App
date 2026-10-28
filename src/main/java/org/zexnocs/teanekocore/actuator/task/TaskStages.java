package org.zexnocs.teanekocore.actuator.task;

import org.zexnocs.teanekocore.actuator.task.exception.TaskUnsuccessfulException;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskResult;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

/**
 * 将任务的执行状态展开为可组合的完成阶段，不安装终端日志，也不启动额外重试。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
public final class TaskStages {
    /**
     * 禁止直接实例化此辅助类型，统一复用静态入口。
     */
    private TaskStages() {}

    /**
     * 等待任务结束并检查最终状态；成功的 Void/null 业务值是合法结果。
     *
     * @param task 用于本次处理的 {@code task} 数据
     * @param operation 用于定位失败任务的操作名称
     * @param <T> 本方法约定的泛型数据类型
     * @return 承载本次异步处理实际结果的可组合完成阶段
     * @throws NullPointerException 当必需的输入或依赖为空时
     */
    public static <T> CompletionStage<T> successful(
            TaskFuture<? extends ITaskResult<T>> task, String operation) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(operation, "operation");
        return task.asStage().thenApply(result -> requireSuccess(result, operation));
    }

    /**
     * 用于已有 TaskFuture 的 thenApply 链；失败结果不能当成业务成功。
     *
     * @param result 已经形成的处理结果
     * @param operation 用于定位失败任务的操作名称
     * @param <T> 本方法约定的泛型数据类型
     * @return 满足本方法约束的 {@code T} 结果
     * @throws TaskUnsuccessfulException 当该操作的校验或下游调用失败时
     * @throws NullPointerException 当必需的输入或依赖为空时
     */
    public static <T> T requireSuccess(ITaskResult<T> result, String operation) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(result, "Task returned no result: " + operation);
        if (!result.isSuccess()) {
            throw new TaskUnsuccessfulException(operation);
        }
        return result.getResult();
    }
}
