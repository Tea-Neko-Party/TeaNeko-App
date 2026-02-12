package org.zexnocs.teanekocore.actuator.task;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.zexnocs.teanekocore.actuator.task.exception.TaskIllegalStateException;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITask;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskConfig;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskResult;
import org.zexnocs.teanekocore.actuator.task.state.ITaskState;
import org.zexnocs.teanekocore.actuator.task.state.TaskCreatedState;
import org.zexnocs.teanekocore.actuator.task.state.TaskExecutedState;
import org.zexnocs.teanekocore.framework.state.LockStateMachine;

import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 由 TaskService 管理的异步 Callable 任务，只能执行一次的任务。
 * TaskConfig 可以生成多个 Task 实例，每个实例只能执行一次。
 * 不允许外部直接创建 Task 实例，必须通过 TaskConfig 创建。
 * 具体请看 TaskConfig 文档。
 * @param <T> 任务结果的类型
 * @author zExNocs
 * @date 2026/02/10
 */
public class Task<T> extends LockStateMachine<ITaskState> implements ITask<T> {
    /// 任务执行的 config
    @Getter
    private final ITaskConfig<T> config;

    /// 任务的执行 key
    @Getter
    private final UUID key;

    /// 上次重试的时间，用于判断任务是否过期
    @Getter(AccessLevel.PROTECTED)
    private volatile long lastRetryTime;

    /// 重新尝试次数
    private final AtomicInteger retryCount = new AtomicInteger(0);

    /// 被订阅的 future
    @Getter
    private final TaskFuture<ITaskResult<T>> future;

    /// 类型，用于判断 result 是否符合预期
    @Getter
    private final Class<T> resultType;

    /// 任务执行的线程 Future，用于观察或者取消任务执行
    @Setter
    @Getter
    private volatile ScheduledFuture<?> executingFuture = null;

    /**
     * 构造函数，外部不可见。
     * @param key 任务的唯一标识符
     * @param config 任务的配置
     */
    protected Task(UUID key,
                   ITaskConfig<T> config,
                   TaskFuture<ITaskResult<T>> future,
                   Class<T> resultType) {
        super(new TaskCreatedState());
        this.config = config;
        this.future = future;
        this.resultType = resultType;
        this.key = key;

        // config 创建 task 增加其创建计数器
        this.config.addCounter();
    }

    // ------- ITask 接口实现 -------
    /**
     * 原子性地修改成 Retry 的状态。
     * 前提是当前任务处于 Executed 状态。
     *
     * @return true 表示修改成功；false 表示重试次数达到上限
     * @throws TaskIllegalStateException 如果当前任务不处于 Executed 状态
     */
    @Override
    public boolean switchToRetryState() throws TaskIllegalStateException {
        // 如果当前状态不是 Created，则抛出异常
        if (!switchStateUnderExpected(TaskExecutedState.class, new TaskCreatedState())) {
            throw new TaskIllegalStateException("任务在非 Executed 状态下尝试重试",
                    getCurrentState().getClass(), TaskExecutedState.class);
        }

        // 判断是不是已经达到了最大重试次数
        if (retryCount.incrementAndGet() > config.getMaxRetries()) {
            // 达到上限，不能重试了
            return false;
        }

        // 更新上次重试的时间
        lastRetryTime = System.currentTimeMillis();
        return true;
    }

    /**
     * 判断是否过期。过期的定义是：当前时间 - 上次重试时间 > 过期时间。
     * @param currentTimeInMillis 当前时间，单位毫秒
     * @return 是否过期
     */
    @Override
    public boolean isExpired(long currentTimeInMillis) {
        return currentTimeInMillis - lastRetryTime > config.getExpirationDuration().toMillis();
    }

    /**
     * 获取当前重试次数，以便于记录日志
     *
     * @return 当前重试次数
     */
    @Override
    public int getCurrentRetryCount() {
        return retryCount.get();
    }
}
