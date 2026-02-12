package org.zexnocs.teanekocore.actuator.task.interfaces;

import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.actuator.task.exception.TaskIllegalStateException;
import org.zexnocs.teanekocore.actuator.task.state.ITaskState;
import org.zexnocs.teanekocore.framework.state.IStateMachine;

import java.util.UUID;
import java.util.concurrent.ScheduledFuture;

/**
 * 任务接口，用于定义一个任务的基本行为。
 * 是一个简单的状态机，支持的状态包含：
 * 创建 → 提交/执行中 → 执行完毕 → 提交成功
 * 若重试会重回创建状态
 *
 * @author zExNocs
 * @date 2026/02/10
 */
public interface ITask<T> extends IStateMachine<ITaskState> {
    /**
     * 获取任务的唯一标识符
     * @return 任务的唯一标识符
     */
    UUID getKey();

    /**
     * 获取任务配置
     * @return 任务配置对象
     */
    ITaskConfig<T> getConfig();

    /**
     * 获取 result type
     */
    Class<T> getResultType();

    /**
     * 获取其被订阅的 future
     * @return 任务被订阅的 future
     */
    TaskFuture<ITaskResult<T>> getFuture();

    /**
     * 获取其线程执行的 future，用于过期取消
     * @return 任务执行的线程 future
     */
    ScheduledFuture<?> getExecutingFuture();

    /**
     * 原子性地修改成 Retry 的状态。
     * 前提是当前任务处于 Created 状态。
     * @return true 表示修改成功；false 表示重试次数达到上限
     * @throws TaskIllegalStateException 如果当前任务不处于 Created 状态
     */
    boolean switchToRetryState() throws TaskIllegalStateException;

    /**
     * 获取当前重试次数，以便于记录日志
     * @return 当前重试次数
     */
    int getCurrentRetryCount();

    /**
     * 判断是否过期，过期任务将会以异常的形式完成。
     * @param currentTimeInMillis 当前时间的毫秒数
     * @return true 表示过期；false 表示未过期
     */
    boolean isExpired(long currentTimeInMillis);

    /**
     * 设置任务执行的线程 Future，用于观察或者取消任务执行。
     * @param future 任务执行的线程 Future
     */
    void setExecutingFuture(ScheduledFuture<?> future);
}
