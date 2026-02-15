package org.zexnocs.teanekocore.actuator.task;

import lombok.NonNull;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.actuator.task.exception.TaskIllegalStateException;
import org.zexnocs.teanekocore.actuator.task.exception.TaskNoRetryRuntimeException;
import org.zexnocs.teanekocore.actuator.task.exception.TaskRetryRuntimeException;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITask;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskExecuteService;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskResult;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskRetryService;

/**
 * retry: 对一个 Task 进行重试的服务。
 * 要求 Task 处于 Executed 状态才允许重试。
 *
 * @author zExNocs
 * @date 2026/02/12
 */
@Service
public class TaskRetryService implements ITaskRetryService {
    private final ITaskExecuteService iTaskExecuteService;

    public TaskRetryService(ITaskExecuteService iTaskExecuteService) {
        this.iTaskExecuteService = iTaskExecuteService;
    }

    /**
     * 尝试以一个 result 进行 retry
     * 当符合 result 为 false 且重试策略支持 false 时，才允许重试
     * @param task   任务
     * @param result 结果
     * @return true 表示已经成功将任务进行重试；false 表示重试失败，可能是 result 符合要求或者重试达到上限
     * @throws TaskIllegalStateException 如果任务不处于 Executed 状态，则抛出该异常
     */
    @Override
    public boolean __retryTaskWithResult(@NonNull ITask<?> task,
                                         @NonNull ITaskResult<?> result) throws TaskIllegalStateException {
        // 条件判断，如果 success 则不需要重试
        if(result.isSuccess()) {
            return false;
        }

        // 判断是否支持 false 时重试
        if(!task.getConfig().getRetryStrategy().isRetryOnFalse()) {
            return false;
        }

        // 条件达成，尝试重试
        if(task.switchToRetryState()) {
            iTaskExecuteService.__executeTaskRetry(task);
            return true;
        }

        // 达到重试上限 无法重试
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
    public boolean __retryTaskWithException(@NonNull ITask<?> task,
                                            @NonNull Throwable exception) throws TaskIllegalStateException {
        // 如果是 NoRetry 异常，则不允许重试
        if(exception instanceof TaskNoRetryRuntimeException) {
            return false;
        }
        // 如果不是 Retry 异常，则判断是否支持其他异常时重试
        if(!(exception instanceof TaskRetryRuntimeException)) {
            if(!task.getConfig().getRetryStrategy().isRetryOnException()) {
                return false;
            }
        }
        // 条件达成，尝试重试
        if(task.switchToRetryState()) {
            iTaskExecuteService.__executeTaskRetry(task);
            return true;
        }
        // 达到重试上限 无法重试
        return false;
    }
}
