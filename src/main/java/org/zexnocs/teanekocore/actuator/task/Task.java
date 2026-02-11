package org.zexnocs.teanekocore.actuator.task;

import lombok.Getter;
import org.jspecify.annotations.NonNull;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITask;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 由 TaskService 管理的异步 Supplier 任务，只能执行一次的任务。
 * TaskConfig 可以生成多个 Task 实例，每个实例只能执行一次。
 * 不允许外部直接创建 Task 实例，必须通过 TaskConfig 创建。
 * 具体请看 TaskConfig 文档。
 * @param <T> 任务结果的类型
 * @author zExNocs
 * @date 2026/02/10
 */
public class Task<T> implements ITask<T>, Delayed {
    /// 任务执行的 config
    @Getter
    private final TaskConfig<T> config;

    /// 执行的时间，单位为毫秒
    private final long executeTimeInMillis;

    /// 上次重试的时间，用于判断任务是否过期
    @Getter
    private long lastRetryTime;

    /// 重新尝试次数
    private int retryCount;

    /// 被订阅的 future
    private final TaskFuture<T> future;

    /// 是否被提交到 TaskService 中进行排队执行
    private final AtomicBoolean isSubmitted = new AtomicBoolean(false);

    /// 是否已经到达过时间来执行过一次 Supplier
    private final AtomicBoolean isExecuted = new AtomicBoolean(false);

    /// 类型
    @Getter
    private final Class<? extends T> clazz;

    /**
     * 构造函数，外部不可见。
     * @param executeTimeInMillis 任务的执行时间，单位为毫秒
     * @param config 任务的配置
     */
    protected Task(long executeTimeInMillis,
                   TaskConfig<T> config,
                   TaskFuture<T> future,
                   Class<? extends T> clazz) {
        this.config = config;
        this.executeTimeInMillis = executeTimeInMillis;
        this.future = future;
        this.clazz = clazz;
    }

    // ------- ITask 接口实现 -------

    /**
     * 是否已经超过了最大重试次数
     * @return 是否超过了最大重试次数
     */
    @Override
    public boolean isMaxRetryCountExceeded() {
        return retryCount >= config.getMaxRetries();
    }

    /**
     * 线程安全的更新重试状态。
     * 如果没有执行过，则不允许重试
     * @return 是否允许重试。true表示允许重试，false 表示不允许重试
     */
    @Override
    public boolean safeUpdateRetry() {
        // 如果上一次的没有执行完毕，则不允许重试
        // 如果 executed 为 true，则 submitted 一定为 true
        // 因此不用判断 submit 是否为 true
        if(!isExecuted.getAndSet(false)) {
            return false;
        }
        isSubmitted.set(false);
        retryCount++;
        lastRetryTime = System.currentTimeMillis();
        return true;
    }

    /**
     * 原子性地设置并标记为已提交执行
     * @return 在这次提交执行之前是否已经提交过
     */
    @Override
    public boolean getAndSetSubmitted() {
        return isSubmitted.getAndSet(true);
    }

    /**
     * 是否彻底完成任务
     * @return 是否彻底完成任务
     */
    @Override
    public boolean isDone() {
        return future.isDone();
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
                executeTimeInMillis - System.currentTimeMillis(),
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
        if (o instanceof Task<?> other) {
            return Long.compare(this.executeTimeInMillis, other.executeTimeInMillis);
        }
        long diff = this.getDelay(TimeUnit.MILLISECONDS) - o.getDelay(TimeUnit.MILLISECONDS);
        return Long.compare(diff, 0);
    }
}
