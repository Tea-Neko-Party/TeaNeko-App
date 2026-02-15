package org.zexnocs.teanekocore.database.base.interfaces;

import lombok.NonNull;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskConfig;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskResult;
import org.zexnocs.teanekocore.database.base.exception.DatabaseTaskRepeatedSubmissionException;

import java.util.Collection;

/**
 * 数据库任务配置接口。
 * 主要用于获取 TimerTaskConfig 和将任务 push 到数据库服务中。
 *
 * @author zExNocs
 * @date 2026/02/15
 */
public interface IDatabaseTaskConfig {
    /**
     * 获取任务名称。
     * @return 任务名称
     */
    String getName();

    /**
     * 将任务推送给任务服务队列。
     * 该方法会原子性地设置任务的 push 状态，以防止任务提交给服务后再次更改该任务的状态。
     *
     * @return 数据库任务的 Future 对象，用于获取任务执行的异常；获取到 future 后应当使用 .finish() 方法来报告异常。
     * @throws DatabaseTaskRepeatedSubmissionException 数据库任务重复提交异常。
     */
    TaskFuture<ITaskResult<Void>> push() throws DatabaseTaskRepeatedSubmissionException;

    /**
     * 添加数据库事务任务。
     *
     * @param task 任务
     * @throws DatabaseTaskRepeatedSubmissionException 数据库任务重复提交异常。
     */
    void addTransactionTask(@NonNull Runnable task) throws DatabaseTaskRepeatedSubmissionException;

    /**
     * 添加缓存任务。
     *
     * @param task 缓存任务
     * @throws DatabaseTaskRepeatedSubmissionException 数据库任务重复提交异常。
     */
    void addCacheTask(@NonNull Runnable task) throws DatabaseTaskRepeatedSubmissionException;

    /**
     * 合并任务。
     * 前提两个任务都没有提交过。
     * 参数的 task 会被视为已经 push 过。
     *
     * @param config 任务
     * @throws DatabaseTaskRepeatedSubmissionException 数据库任务重复提交异常。
     */
    void merge(@NonNull IDatabaseTaskConfig config) throws DatabaseTaskRepeatedSubmissionException;

    /**
     * 原子性修改任务的push状态。
     * 用于在 merge 时将参数的 task 视为已经 push 过。
     *
     * @param value 新的 push 状态
     * @return 修改前的状态
     */
    boolean __getAndSetPushed(boolean value);

    /**
     * 获取事务任务集合。
     *
     * @return 事务任务集合
     */
    Collection<Runnable> __getTasksWithTransaction();

    /**
     * 获取缓存任务集合。
     *
     * @return 缓存任务集合
     */
    Collection<Runnable> __getTasksWithCache();

    /**
     * 获取 TaskConfig。
     * @return TaskConfig
     */
    ITaskConfig<Void> __getTaskConfig();
}
