package org.zexnocs.teanekocore.actuator.task;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.zexnocs.teanekocore.actuator.task.api.ITaskStage;

import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

/**
 * 任务配置
 * 其定义：
 * 1. 创建：TaskConfig 定义并生成 Task
 * 2. 存储：TaskStorageService 存储
 * 3. 执行：TaskExecuteService 执行 Supplier，并返回 TaskResult
 * 4. 结果：TaskResult 存储结果
 * 5. 调度：TaskService 调度整个过程
 * 结果可能由 Supplier 直接返回，也可以异步获取 并在 TaskService 中执行 TaskResult。
 * 如果异步提交结果，需要自行指定 key 值以便于关联 result 和 task。
 * <p>
 * 关于 Supplier 返回：
 * - 直接提交：返回非 null，会直接作为 result 提交。
 * - 间接提交：返回 null，那么 TaskService 将会等待异步主动提交结果；
 * <p>
 * 关于提交结果 success 字段：
 * - true：表示任务成功完成，TaskService 将会将 Task 标记为完成并在 TaskStorageService 中删除；
 * - false：表示任务执行失败，TaskService 将会根据 TaskConfig 中的重试策略进行重试；
 * 如果重试次数用尽将会将 Task 标记为失败并在 TaskStorageService 中删除。
 * <p>
 * 关于重复执行：如果允许重复执行，那么只有当上一个 Task 已经完成（无论成功还是失败）后，才会执行下一个 Task；
 * <p>
 * 注意：每一个 Task 默认等待时间为 10min (可以在 TaskConfig 中修改)，超过等待时间后将会报错并删除 Task。
 * @param <T> 任务结果的类型
 * @author zExNocs
 * @date 2026/02/10
 */
@Getter
@Builder
public class TaskConfig<T> {
    /**
     * 订阅任务的名称
     * 用于日志记录和调试
     */
    private final String name;

    /**
     * 被订阅任务的 supplier
     * 执行一定是异步的
     * 要求该订阅任务 直接 / 间接 提交 result
     * 直接：supplier 直接返回 result
     * 间接：supplier 返回 null，在 TaskService 中提交 result
     * 1. 如果 "异步" 获取结果，请使用 "间接"；如果 "同步" 获取结果，请使用 "直接"。
     *    请勿在 "同步获取结果" 时使用 "间接" 方式，会在 complete 函数中 retry 失败并抛出 TaskRetryCollisionException
     * 2. 如果要求任务是可中断的，请定时使用 Thread.currentThread().isInterrupted() 检查
     *    并且在堵塞的地方 catch InterruptedException，
     */
    private final Supplier<TaskResult<T>> supplier;

    /**
     * 最大重试的次数
     * 默认不重试
     */
    @Builder.Default
    private final int maxRetries = 0;

    /**
     * 最大保存时间
     * 超过这个延迟时间的任务将被丢弃并且以异常方式完成
     * null 表示不限制延迟时间
     * 默认 10min
     */
    @Builder.Default
    private final Duration expireDuration = Duration.ofMinutes(10);

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

    /**
     * 任务执行的 delay 时间
     * 只有当 delayDuration 过后，任务才会被执行
     * 默认 0，即立即执行
     */
    @Builder.Default
    private final Duration delayDuration = Duration.ZERO;

    /**
     * 订阅的 key
     * 由 TaskService 生成并设置，用于关联 result 和 task
     * builder 设置无效，会被无视
     */
    @Setter
    private String key;
}
