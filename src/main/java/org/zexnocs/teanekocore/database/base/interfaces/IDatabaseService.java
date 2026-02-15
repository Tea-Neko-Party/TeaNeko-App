package org.zexnocs.teanekocore.database.base.interfaces;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskResult;

import java.util.Collection;

/**
 * 数据库服务，用于管理 Database Task 的执行。
 * 包括处理事务和缓存的任务。
 * 先执行事务任务，事务成功后再执行缓存任务；如果事务失败，则不执行缓存任务，事务进行回滚。
 * 流程是：
 * 执行事务任务 → 执行缓存任务 → 执行 future 链
 * future 链如果出现任何异常都不会影响事务和缓存的执行结果。
 * 此外，future 结尾应当使用 .finish() 方法；否则出现异常不会被日志记录。
 *
 * @author zExNocs
 * @date 2026/02/15
 */
public interface IDatabaseService {
    /**
     * 快速添加一个简单的带事务的数据库任务。
     *
     * @param taskName            任务名称。
     * @param transactionCallback 事务回调，必须提供。
     * @param cacheCallback       缓存回调，可选提供。
     * @return 数据库任务的 Future 对象，可用于获取任务执行的异常；获取到 future 后应当使用 .finish() 方法来报告异常。
     */
    TaskFuture<ITaskResult<Void>> pushQuickTask(String taskName,
                                                @NonNull Runnable transactionCallback,
                                                @Nullable Runnable cacheCallback);

    /**
     * 直接执行数据库任务。
     * 该方法由 Database task config 的 push() 方法调用。
     * 该方法不会设置 Database task config 的 push 状态；
     * push 状态应该由 Database task config 自行原子性地设置。
     *
     * @param task 任务。
     * @return 数据库任务的 Future 对象，用于给 config 的 push() 方法返回 future。
     */
    TaskFuture<ITaskResult<Void>> __pushTask(IDatabaseTaskConfig task);

    /**
     * 给 Database Config 执行的事务处理，需要启动事务
     *
     * @param tasks 事务任务
     */
    void __executeTaskWithTransaction(Collection<Runnable> tasks);

    /**
     * 给 Database Config 执行的缓存处理
     *
     * @param tasks 缓存任务
     */
    void __executeTaskWithCache(Collection<Runnable> tasks);

    /**
     * 获取最大重试的尝试次数。
     * 由 spring boot config 配置项提供。
     *
     * @return 最大重试的尝试次数
     */
    int __getMaxRetryCount();
}
