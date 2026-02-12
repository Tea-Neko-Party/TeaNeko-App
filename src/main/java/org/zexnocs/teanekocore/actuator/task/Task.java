package org.zexnocs.teanekocore.actuator.task;

import lombok.Getter;
import org.jspecify.annotations.NonNull;
import org.zexnocs.teanekocore.actuator.task.exception.TaskIllegalStateException;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITask;
import org.zexnocs.teanekocore.actuator.task.state.ITaskState;
import org.zexnocs.teanekocore.actuator.task.state.TaskCreatedState;
import org.zexnocs.teanekocore.actuator.task.state.TaskExecutedState;
import org.zexnocs.teanekocore.framework.state.LockStateMachine;

import java.util.UUID;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 由 TaskService 管理的异步 Supplier 任务，只能执行一次的任务。
 * TaskConfig 可以生成多个 Task 实例，每个实例只能执行一次。
 * 不允许外部直接创建 Task 实例，必须通过 TaskConfig 创建。
 * 具体请看 TaskConfig 文档。
 * @param <T> 任务结果的类型
 * @author zExNocs
 * @date 2026/02/10
 */
public class Task<T> extends LockStateMachine<ITaskState> implements ITask<T>, Delayed {
    /// 任务执行的 config
    @Getter
    private final TaskConfig<T> config;

    /// 任务的执行 key
    @Getter
    private final UUID key;

    /// 上次重试的时间，用于判断任务是否过期
    @Getter
    private volatile long lastRetryTime;

    /// 重新尝试次数
    private final AtomicInteger retryCount = new AtomicInteger(0);

    /// 被订阅的 future
    private final TaskFuture<T> future;

    /// 类型，用于判断 result 是否符合预期
    @Getter
    private final Class<? extends T> clazz;

    /**
     * 构造函数，外部不可见。
     * @param key 任务的唯一标识符
     * @param config 任务的配置
     */
    protected Task(UUID key,
                   TaskConfig<T> config,
                   TaskFuture<T> future,
                   Class<? extends T> clazz) {
        super(new TaskCreatedState(System.currentTimeMillis() + config.getDelayDuration().toMillis()));
        this.config = config;
        this.future = future;
        this.clazz = clazz;
        this.key = key;
    }

    // ------- ITask 接口实现 -------
    /**
     * 原子性地修改成 Retry 的状态。
     * 前提是当前任务处于 Executed 状态。
     *
     * @return true 表示修改成功；false 表示重试次数达到上限
     * @throws TaskIllegalStateException 如果当前任务不处于 Created 状态
     */
    @Override
    public boolean switchToRetryState() throws TaskIllegalStateException {
        // 首先判断是不是已经达到了最大重试次数
        if (retryCount.incrementAndGet() > config.getMaxRetries()) {
            // 达到上限，不能重试了
            return false;
        }

        // 如果当前状态不是 Created，则抛出异常
        if (!switchStateUnderExpected(TaskExecutedState.class,
                new TaskCreatedState(System.currentTimeMillis() + config.getRetryInterval().toMillis()))) {
            throw new TaskIllegalStateException("任务在非 Created 状态下尝试重试",
                    getCurrentState().getClass(), TaskExecutedState.class);
        }

        // 更新上次重试的时间
        lastRetryTime = System.currentTimeMillis();
        return true;
    }

    /**
     * 获取当前的执行时间，前提是任务处于 Created 状态。
     * @return 当前的执行时间
     */
    public long getExecuteTimeInMillis() throws TaskIllegalStateException {
        lock.lock();
        try {
            var state = getCurrentState();
            if (state instanceof TaskCreatedState createdState) {
                return createdState.getExecuteTimeInMillis();
            } else {
                throw new TaskIllegalStateException("任务在非 Created 状态下尝试获取执行时间",
                        state.getClass(), TaskCreatedState.class);
            }
        } finally {
            lock.unlock();
        }
    }

    // ------- Delayed 接口实现 -------
    /**
     * 任务的执行时间
     * @param unit 时间单位
     * @return 执行时间的毫秒数
     */
    @Override
    public long getDelay(TimeUnit unit) {
        return unit.convert(
                getExecuteTimeInMillis() - System.currentTimeMillis(),
                TimeUnit.MILLISECONDS);
    }

    /**
     * 比较任务的执行时间，越早执行的任务优先级越高。
     * @param o 另一个 Delayed 对象
     * @return 比较结果，负数表示 this 任务优先级更高，正数表示 o 任务优先级更高，0 表示相同优先级
     */
    @Override
    public int compareTo(@NonNull Delayed o) {
        if (o == this) {
            return 0;
        }
        if (o instanceof ITask<?> other) {
            return Long.compare(this.getExecuteTimeInMillis(), other.getExecuteTimeInMillis());
        }
        long diff = this.getDelay(TimeUnit.MILLISECONDS) - o.getDelay(TimeUnit.MILLISECONDS);
        return Long.compare(diff, 0);
    }
}
