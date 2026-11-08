package org.zexnocs.teanekocore.actuator.task.interfaces;

// 2026/11/07：接口统一使用 I 前缀，调用合同与持久数据保持不变。

import lombok.NonNull;
import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekocore.actuator.task.TaskRetryStrategy;
import org.zexnocs.teanekocore.actuator.task.api.ITaskStage;
import org.zexnocs.teanekocore.framework.function.IMethodCallable;

import java.time.Duration;
import java.util.List;

/**
 * 用于定义一个任务的基本配置，从而来组装一个 Task 实例。
 *
 * <p>关于 Callable 返回：
 * - 直接提交：返回非 null，会直接作为 result 提交。
 * - 间接提交：返回 null，那么 {@link ITaskService} 将会等待异步主动提交结果；
 * 请不要在 Callable 里调用间接提交的方式，否则会抛出 {@link org.zexnocs.teanekocore.actuator.task.exception.TaskIllegalStateException}。
 * 如果没有 result，可以返回 {@link org.zexnocs.teanekocore.actuator.task.EmptyTaskResult}。
 * <br>2026/11/07：明确手动阶段的可空与创建时冻结契约。
 *
 * @param <T> 任务结果类型
 * @author zExNocs
 * @date 2026/02/10
 * @since 4.0.0
 * @version 4.9.0-l
 * @see ITaskResult
 */
public interface ITaskConfig<T> {
    /**
     * 获取任务名称
     *
     * @return 任务名称
     */
    String getName();

    /**
     * 获取任务 callable
     *
     * @return 任务 callable，用于生成任务实例
     */
    IMethodCallable<ITaskResult<T>> getCallable();

    /**
     * 2026/11/07：获取显式配置时冻结的任务阶段列表，查询时不得发现归属。
     *
     * @return 按执行顺序排列的阶段快照；为空时读取命名空间目录
     */
    @Nullable List<ITaskStage> getTaskStages();

    /**
     * 获取任务阶段连命名空间
     * 用于注入阶段链列表
     *
     * @return 任务阶段连命名空间
     */
    String getTaskStageNamespace();

    /**
     * 获取执行延迟
     *
     * @return 执行延迟 duration
     */
    @NonNull
    Duration getDelayDuration();

    /**
     * 获取最大重试次数
     *
     * @return 最大重试次数
     */
    int getMaxRetries();

    /**
     * 获取重试策略
     *
     * @return {@link TaskRetryStrategy} 重试策略
     */
    TaskRetryStrategy getRetryStrategy();

    /**
     * 获取重试间隔
     *
     * @return 重试间隔 duration
     */
    Duration getRetryInterval();

    /**
     * 获取最大保存时间
     *
     * @return 最大保存时间 duration，超过这个时间的任务将被丢弃
     */
    Duration getExpirationDuration();

    /**
     * 2026/11/07：在显式配置时冻结任务阶段归属。
     *
     * @param taskStages 按执行顺序排列的阶段；为空时恢复读取命名空间目录
     */
    void setTaskStages(@Nullable List<ITaskStage> taskStages);

    /**
     * 生成了一个 task，让 counter 加 1。
     */
    void addCounter();
}
