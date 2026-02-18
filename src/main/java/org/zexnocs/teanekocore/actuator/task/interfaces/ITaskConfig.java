package org.zexnocs.teanekocore.actuator.task.interfaces;

import lombok.NonNull;
import org.zexnocs.teanekocore.actuator.task.TaskRetryStrategy;
import org.zexnocs.teanekocore.actuator.task.api.ITaskStage;
import org.zexnocs.teanekocore.framework.function.MethodCallable;

import java.time.Duration;
import java.util.List;

/**
 * 用于定义一个任务的基本配置，从而来组装一个 Task 实例。
 * <p>
 * 关于 Callable 返回：
 * - 直接提交：返回非 null，会直接作为 result 提交。
 * - 间接提交：返回 null，那么 TaskService 将会等待异步主动提交结果；
 * 请不要在 Callable 里调用间接提交的方式，否则会抛出 TaskIllegalStateException。
 * 如果没有 result，请返回 new EmptyTaskResult()。
 * <p>
 * @author zExNocs
 * @date 2026/02/10
 */
public interface ITaskConfig<T> {
    /**
     * 获取任务名称
     * @return 任务名称
     */
    String getName();

    /**
     * 获取任务 callable
     * @return 任务 callable，用于生成任务实例
     */
    MethodCallable<ITaskResult<T>> getCallable();

    /**
     * 获取任务执行阶段列表
     * @return 任务执行阶段列表，按照执行顺序排列
     */

    List<ITaskStage> getTaskStages();

    /**
     * 获取任务阶段连命名空间
     * 用于注入阶段链列表
     */
    String getTaskStageNamespace();

    /**
     * 获取执行延迟
     * @return 执行延迟 duration
     */
    @NonNull
    Duration getDelayDuration();

    /**
     * 获取最大重试次数
     * @return 最大重试次数
     */
    int getMaxRetries();

    /**
     * 获取重试策略
     * @return 重试策略
     */
    TaskRetryStrategy getRetryStrategy();

    /**
     * 获取重试间隔
     * @return 重试间隔 duration
     */
    Duration getRetryInterval();

    /**
     * 获取最大保存时间
     * @return 最大保存时间 duration，超过这个时间的任务将被丢弃
     */
    Duration getExpirationDuration();

    /**
     * 设置任务执行阶段列表
     * @param taskStages 任务执行阶段列表，按照执行顺序排列
     */
    void setTaskStages(List<ITaskStage> taskStages);

    /**
     * 生成了一个 task，让 counter 加 1。
     */
    void addCounter();
}
