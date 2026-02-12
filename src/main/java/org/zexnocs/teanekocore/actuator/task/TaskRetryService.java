package org.zexnocs.teanekocore.actuator.task;

import lombok.NonNull;
import org.zexnocs.teanekocore.actuator.task.exception.TaskIllegalStateException;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITask;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskResult;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskRetryService;

/**
 * retry: 对一个 Task 进行重试的服务。
 * 要求 Task 处于 Executed 状态才允许重试。
 *
 * @author zExNocs
 * @date 2026/02/12
 */
public class TaskRetryService implements ITaskRetryService {
    /**
     * 尝试以一个 result 进行 retry
     *
     * @param task   任务
     * @param result 结果
     * @return true 表示已经成功将任务进行重试；false 表示重试失败，可能是 result 符合要求或者重试达到上限
     * @throws TaskIllegalStateException 如果任务不处于 Executed 状态，则抛出该异常
     */
    @Override
    public boolean retryTaskWithResult(@NonNull ITask<?> task, @NonNull ITaskResult<?> result) throws TaskIllegalStateException {
        return false;
    }

    /**
     * 尝试以一个 异常 进行 retry
     *
     * @param task      任务
     * @param exception 异常
     * @return true 表示已经成功将任务进行重试；false 表示重试失败，可能是异常符合要求或者重试达到上限
     * @throws TaskIllegalStateException 如果任务不处于 Executed 状态，则抛出该异常
     */
    @Override
    public boolean retryTaskWithException(@NonNull ITask<?> task, @NonNull Throwable exception) throws TaskIllegalStateException {
        return false;
    }
}
