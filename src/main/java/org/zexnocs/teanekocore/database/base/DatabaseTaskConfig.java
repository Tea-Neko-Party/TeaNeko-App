package org.zexnocs.teanekocore.database.base;

import lombok.Getter;
import lombok.NonNull;
import org.zexnocs.teanekocore.actuator.task.EmptyTaskResult;
import org.zexnocs.teanekocore.actuator.task.TaskConfig;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.actuator.task.TaskRetryStrategy;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskConfig;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskResult;
import org.zexnocs.teanekocore.database.base.exception.DatabaseTaskRepeatedSubmissionException;
import org.zexnocs.teanekocore.database.base.interfaces.IDatabaseService;
import org.zexnocs.teanekocore.database.base.interfaces.IDatabaseTaskConfig;

import java.time.Duration;
import java.util.Collection;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 标准的数据库任务配置。
 *
 * @author zExNocs
 * @date 2026/02/15
 */
public class DatabaseTaskConfig implements IDatabaseTaskConfig {
    /**
     * 任务名称
     */
    @Getter
    private final String name;

    /**
     * 数据库服务。
     */
    private final IDatabaseService databaseService;

    /**
     * 是否已经提交任务。
     * 以防止任务提交给服务后再次更改该任务的状态。
     */
    private final AtomicBoolean isPushed = new AtomicBoolean(false);

    /**
     * 事务任务队列。
     */
    private final Queue<Runnable> transactionQueue = new ConcurrentLinkedQueue<>();

    /**
     * 缓存任务队列。
     */
    private final Queue<Runnable> cacheQueue = new ConcurrentLinkedQueue<>();

    /**
     * 使用默认的任务阶段链创建数据库任务配置。
     * @param databaseService 数据库服务
     * @param taskName 任务名称
     */
    public DatabaseTaskConfig(IDatabaseService databaseService,
                              String taskName) {
        this.databaseService = databaseService;
        this.name = taskName;
    }

    /**
     * 将任务推送给任务服务队列。
     * 该方法会原子性地设置任务的 push 状态，以防止任务提交给服务后再次更改该任务的状态。
     *
     * @return 数据库任务的 Future 对象，用于获取任务执行的异常；获取到 future 后应当使用 .finish() 方法来报告异常。
     * @throws DatabaseTaskRepeatedSubmissionException 数据库任务重复提交异常。
     */
    @Override
    public TaskFuture<ITaskResult<Void>> push() throws DatabaseTaskRepeatedSubmissionException {
        // 如果已经提交则抛出冲突提交异常
        if (isPushed.getAndSet(true)) {
            throw new DatabaseTaskRepeatedSubmissionException("""
                    数据库任务 {%s} 已经提交，但是尝试再次提交。""".formatted(name));
        }
        return databaseService.__pushTask(this);
    }

    /**
     * 添加数据库事务任务。
     *
     * @param task 任务
     * @throws DatabaseTaskRepeatedSubmissionException 数据库任务重复提交异常。
     */
    @Override
    public void addTransactionTask(@NonNull Runnable task) throws DatabaseTaskRepeatedSubmissionException {
        if (isPushed.get()) {
            // 任务已经提交，不应该再添加任务。
            throw new DatabaseTaskRepeatedSubmissionException("""
                    数据库任务 {%s} 已经提交，但是尝试再次添加任务。""".formatted(name));
        }

        // 添加任务到任务队列。
        transactionQueue.add(task);
    }

    /**
     * 添加缓存任务。
     *
     * @param task 缓存任务
     * @throws DatabaseTaskRepeatedSubmissionException 数据库任务重复提交异常。
     */
    @Override
    public void addCacheTask(@NonNull Runnable task) throws DatabaseTaskRepeatedSubmissionException {
        if (isPushed.get()) {
            // 任务已经提交，不应该再添加任务。
            throw new DatabaseTaskRepeatedSubmissionException("""
                    数据库任务 {%s} 已经提交，但是尝试再次添加任务。""".formatted(name));
        }
        // 添加任务到任务队列。
        cacheQueue.add(task);
    }

    /**
     * 合并任务。
     * 前提两个任务都没有提交过。
     * 参数的 task 会被视为已经 push 过。
     *
     * @param config 任务
     * @throws DatabaseTaskRepeatedSubmissionException 数据库任务重复提交异常。
     */
    @Override
    public void merge(@NonNull IDatabaseTaskConfig config) throws DatabaseTaskRepeatedSubmissionException {
        // 判断当前任务是否已经提交。
        if (isPushed.get()) {
            // 当前任务已经提交，不应该再合并任务。
            throw new DatabaseTaskRepeatedSubmissionException("""
                    数据库合并主任务 {%s} 已经提交，但是尝试再次合并任务。""".formatted(name));
        }
        // 判断参数任务是否已经提交，并且将参数任务的状态设置为已提交。
        if (config.__getAndSetPushed(true)) {
            // 合并的任务已经提交，不应该再合并任务。
            throw new DatabaseTaskRepeatedSubmissionException("""
                    数据库合并副任务 {%s} 已经提交，但是尝试再次合并任务。""".formatted(config.getName()));
        }
        // 合并任务。
        this.transactionQueue.addAll(config.__getTasksWithTransaction());
        this.cacheQueue.addAll(config.__getTasksWithCache());
    }

    /**
     * 原子性修改任务的push状态。
     * 用于在 merge 时将参数的 task 视为已经 push 过。
     *
     * @param value 新的 push 状态
     * @return 修改前的状态
     */
    @Override
    public boolean __getAndSetPushed(boolean value) {
        return isPushed.getAndSet(value);
    }

    /**
     * 获取事务任务集合。
     *
     * @return 事务任务集合
     */
    @Override
    public Collection<Runnable> __getTasksWithTransaction() {
        return transactionQueue;
    }

    /**
     * 获取缓存任务集合。
     *
     * @return 缓存任务集合
     */
    @Override
    public Collection<Runnable> __getTasksWithCache() {
        return cacheQueue;
    }

    /**
     * 获取 TaskConfig。
     *
     * @return TaskConfig
     */
    @Override
    public ITaskConfig<Void> __getTaskConfig() {
        return TaskConfig.<Void>builder()
                .name(name)
                .taskStageNamespace(DatabaseService.TASK_STAGE_NAMESPACE)
                .maxRetries(databaseService.__getMaxRetryCount())
                .retryInterval(Duration.ofMillis(200))
                .retryStrategy(TaskRetryStrategy.NO_RETRY)      // 只有抛出 retry exception 时才会重试，其他异常不重试。
                .callable(() -> {
                    // 执行 Transaction 任务。
                    databaseService.__executeTaskWithTransaction(transactionQueue);
                    // 如果事务没有抛出异常，则执行 Cache 任务。
                    databaseService.__executeTaskWithCache(cacheQueue);
                    return new EmptyTaskResult();
                })
                .build();
    }
}
