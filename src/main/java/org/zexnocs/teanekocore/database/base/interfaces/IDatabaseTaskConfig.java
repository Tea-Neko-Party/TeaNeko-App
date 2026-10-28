package org.zexnocs.teanekocore.database.base.interfaces;

import lombok.NonNull;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.actuator.task.TaskStages;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskConfig;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskResult;
import org.zexnocs.teanekocore.database.base.exception.DatabaseTaskRepeatedSubmissionException;
import org.zexnocs.teanekocore.framework.function.VoidCallable;

import java.util.Collection;
import java.util.concurrent.CompletionStage;

/**
 * 数据库任务配置接口。
 * 主要用于获取 TimerTaskConfig 和将任务 push 到数据库服务中。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
public interface IDatabaseTaskConfig {
    /**
     * 获取任务名称。
     *
     * @return 任务名称
     */
    String getName();

    /**
     * 将任务推送给任务服务队列。
     * 该方法会原子性地设置任务的 push 状态，以防止任务提交给服务后再次更改该任务的状态。
     *
     * @throws DatabaseTaskRepeatedSubmissionException 数据库任务重复提交异常。
     */
    void push() throws DatabaseTaskRepeatedSubmissionException;

    /**
     * 将任务推送给任务服务队列。
     * 该方法会原子性地设置任务的 push 状态，以防止任务提交给服务后再次更改该任务的状态。
     * 此外，会获取 TaskFuture 对象来获取执行的异常；获取到 future 后应当使用 .finish() 方法来报告异常。
     *
     * @return 承载数据库任务最终结果的 {@code TaskFuture<ITaskResult<Void>>}
     * @throws DatabaseTaskRepeatedSubmissionException 数据库任务重复提交异常。
     */
    TaskFuture<ITaskResult<Void>> pushWithFuture() throws DatabaseTaskRepeatedSubmissionException;

    /**
     * 提交并等待事务和缓存处理结束，失败状态或异常均传给调用方。
     * 不额外提交任务或安装终端日志；调用方负责在业务链末端处理异常。
     * 重复提交仍同步抛出 DatabaseTaskRepeatedSubmissionException。
     *
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     * @throws DatabaseTaskRepeatedSubmissionException 当该操作的校验或下游调用失败时
     */
    default CompletionStage<Void> pushAsync() throws DatabaseTaskRepeatedSubmissionException {
        return TaskStages.successful(pushWithFuture(), getName());
    }

    /**
     * 添加数据库事务任务。
     *
     * @param task 任务
     * @return 满足本方法约束的 {@code IDatabaseTaskConfig} 结果
     * @throws DatabaseTaskRepeatedSubmissionException 数据库任务重复提交异常。
     */
    IDatabaseTaskConfig addTransactionTask(@NonNull VoidCallable task) throws DatabaseTaskRepeatedSubmissionException;

    /**
     * 添加缓存任务。
     *
     * @param task 缓存任务
     * @return 满足本方法约束的 {@code IDatabaseTaskConfig} 结果
     * @throws DatabaseTaskRepeatedSubmissionException 数据库任务重复提交异常。
     */
    IDatabaseTaskConfig addCacheTask(@NonNull VoidCallable task) throws DatabaseTaskRepeatedSubmissionException;

    /**
     * 合并任务。
     * 前提两个任务都没有提交过。
     * 参数的 task 会被视为已经 push 过。
     *
     * @param config 任务
     * @return 满足本方法约束的 {@code IDatabaseTaskConfig} 结果
     * @throws DatabaseTaskRepeatedSubmissionException 数据库任务重复提交异常。
     */
    IDatabaseTaskConfig merge(@NonNull IDatabaseTaskConfig config) throws DatabaseTaskRepeatedSubmissionException;

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
    Collection<VoidCallable> __getTasksWithTransaction();

    /**
     * 获取缓存任务集合。
     *
     * @return 缓存任务集合
     */
    Collection<VoidCallable> __getTasksWithCache();

    /**
     * 获取 TaskConfig。
     *
     * @return 实际承载当前数据库写入配置的任务对象
     */
    ITaskConfig<Void> __getTaskConfig();
}
