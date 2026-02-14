package org.zexnocs.teanekocore.actuator.task;

import lombok.*;
import org.zexnocs.teanekocore.actuator.task.api.ITaskStage;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskConfig;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskResult;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 任务配置
 * 其定义：
 * 1. 创建：TaskConfig 定义并生成 Task
 * 2. 存储：TaskService 以 cache 形式进行存储
 * 3. 执行：TaskExecuteService 执行 Callable，并返回 TaskResult
 * 4. 结果：TaskResult 存储结果
 * 5. 调度：TaskService 调度整个过程
 * 结果可能由 Callable 直接返回，也可以异步获取 并在 TaskService 中执行 TaskResult。
 * 如果异步提交结果，需要自行指定 key 值以便于关联 result 和 task。
 * <p>
 * 关于 Callable 返回：
 * - 直接提交：返回非 null，会直接作为 result 提交。
 * - 间接提交：返回 null，那么 TaskService 将会等待异步主动提交结果；
 * 请不要在 Callable 里调用间接提交的方式，否则会抛出 TaskIllegalStateException。
 * 如果没有 result，请返回 new EmptyTaskResult()。
 * <p>
 * 关于提交结果 success 字段：
 * - true：表示任务成功完成，TaskService 将会将 Task 标记为完成并在 TaskStorageService 中删除；
 * - false：表示任务执行失败，TaskService 将会根据 TaskConfig 中的重试策略进行重试；
 * 如果重试次数用尽将会将 Task 标记为失败并在 TaskStorageService 中删除。
 * 策略取决于 RetryStrategy。
 * <p>
 * 注意：每一个 Task 默认等待时间为 10min (可以在 TaskConfig 中修改)，超过等待时间后将会报错并删除 Task。
 * @param <T> 任务结果的类型
 * @author zExNocs
 * @date 2026/02/10
 */
@Getter
@Builder
public class TaskConfig<T> implements ITaskConfig<T> {
    /**
     * 订阅任务的名称
     * 用于日志记录和调试
     */
    @NonNull
    private final String name;

    /**
     * 被订阅任务的 Callable
     * 执行一定是异步的
     * 要求该订阅任务 直接 / 间接 提交 result
     * 直接：Callable 直接返回 result
     * 间接：Callable 返回 null，在 TaskService 中提交 result
     * 1. 如果 "异步" 获取结果，请使用 "间接"；如果 "同步" 获取结果，请使用 "直接"。
     *    请勿在 "同步获取结果" 时使用 "间接" 方式，会在 complete 函数中 retry 失败并抛出 TaskRetryCollisionException
     * 2. 如果要求任务是可中断的，请定时使用 Thread.currentThread().isInterrupted() 检查
     *    并且在堵塞的地方 catch InterruptedException，
     */
    @NonNull
    private final Callable<ITaskResult<T>> callable;

    /**
     * 任务执行的 delay 时间
     * 只有当 delayDuration 过后，任务才会被执行
     * 重试的 interval 由 retryInterval 决定，与 delayDuration 无关
     * 默认 0，即立即执行
     */
    @Setter
    @NonNull
    @Builder.Default
    private Duration delayDuration = Duration.ZERO;

    // ----------- retry related -----------

    /**
     * 自动重试的次数
     * 默认不重试
     */
    @Builder.Default
    private final int maxRetries = 0;

    /**
     * 重试的策略
     * 什么情况才会进行重试
     * 默认 success = false 或者抛出异常时重试
     */
    @Builder.Default
    private final TaskRetryStrategy retryStrategy = TaskRetryStrategy.ALWAYS_RETRY;

    /**
     * 自动重试的间隔时间
     * 每次重试之间的间隔时间
     * 默认立刻重试，即 0
     */
    @Builder.Default
    private final Duration retryInterval = Duration.ZERO;

    // ----------- task stage related -----------

    /**
     * 任务执行阶段链的命名空间
     * 如果 taskStages 为空，则会使用命名空间自动注入
     */
    @Builder.Default
    private final String taskStageNamespace = "default";

    /**
     * 任务阶段链的缓存/手动注入。
     * 优先使用该字段，如果该字段不为 null，则会使用该字段作为任务阶段链，不再使用 taskStageNamespace 进行自动注入。
     * 如果为 null，则使用命名空间自动注入。
     */
    @Setter
    @Builder.Default
    private List<ITaskStage> taskStages = null;

    // ------------- indirect use -------------------
    /**
     * 最大保存时间
     * 超过这个延迟时间的任务将会视为异常完成，首先会尝试进行自动重试。
     * null 表示不限制延迟时间
     * 默认 10min
     */
    @Builder.Default
    private final Duration expirationDuration = Duration.ofMinutes(10);

    // ------------- internal use -------------

    /**
     * 根据该 config 创建的 Task 次数。
     * 不包括 retry 的次数。
     * 用于手动进行重试。
     */
    @Setter(AccessLevel.NONE)
    private final AtomicLong counter = new AtomicLong(0);

    /**
     * 生成了一个 task，让 counter 加 1。
     */
    @Override
    public void addCounter() {
        counter.incrementAndGet();
    }
}
