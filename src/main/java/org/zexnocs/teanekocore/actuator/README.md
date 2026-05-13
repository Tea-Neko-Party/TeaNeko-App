# 一. Actuator 结构介绍

`actuator` 是 TeaNeko Core 的异步执行与定时调度模块，主要负责把业务逻辑包装成可追踪、可重试、可串联的任务。

| 模块 | 作用 |
|:---:|---|
| `task` | 异步任务模型。提供 `TaskConfig`、`TaskService`、`TaskFuture`、任务阶段链和重试策略。 |
| `timer` | 定时器模型。按固定频率、固定延迟、智能频率或 Cron 表达式周期性提交 task。 |

# 二. Task 模块

## 1. 核心模型

| 类或接口 | 作用 |
|:---:|---|
| `TaskConfig<T>` / `ITaskConfig<T>` | 描述一次任务的执行配置，包括名称、`MethodCallable<ITaskResult<T>>`、延迟、重试、阶段链和过期时间。 |
| `Task<T>` / `ITask<T>` | 单次任务实例，持有唯一 `UUID key`、执行状态、重试次数、执行线程 future 和用户可观察的 `TaskFuture`。 |
| `TaskResult<T>` / `ITaskResult<T>` | 任务结果。`isSuccess()` 决定是否成功，`getResult()` 返回业务结果。 |
| `EmptyTaskResult` | 无返回值任务的成功结果，结果类型是 `Void.class`。 |
| `TaskFuture<T>` | 对 `CompletableFuture<T>` 的轻量包装，提供链式 `thenApply`、`thenComposeTask`、`exceptionally`、`handle`、`whenComplete` 和 `finish()`。 |
| `TaskService` / `ITaskService` | 注册任务、提交异步结果、异常完成任务、管理任务过期。 |
| `TaskExecuteService` | 在线程池中执行任务阶段链和 callable。 |
| `TaskRetryService` | 根据 `TaskRetryStrategy` 判断结果或异常是否需要重试。 |
| `TaskStageChain` | 以 AOP 风格串联多个 `ITaskStage`，执行顺序为高优先级阶段包裹低优先级阶段和最终 callable。 |
| `TaskStageScanner` | 扫描 `@TaskStage` Bean，并按 namespace 和 priority 生成阶段列表。 |

任务的实际状态类位于 `task/state`：

| 状态 | 含义 |
|:---:|---|
| `TaskCreatedState` | 任务已创建，等待延迟结束或等待提交执行。 |
| `TaskSubmittedState` | 任务已经提交到执行线程。 |
| `TaskExecutedState` | callable 已执行完，等待直接完成、间接完成或重试判定。 |
| `TaskFinishedState` | 任务已从 `TaskService` 缓存中移除并完成。 |

## 2. 任务执行流程

```markdown
1. 构造 TaskConfig，声明 callable、任务名、阶段 namespace、重试和过期参数。
2. 调用 TaskService.subscribeWithFuture(...) 或 subscribe(...) 注册任务。
3. TaskService 创建 Task，并交给 TaskExecuteService。
4. TaskExecuteService 根据 TaskConfig 创建 TaskStageChain。
5. 阶段链按 priority 从高到低进入，在最终 callable 执行后反向返回。
6. callable 返回非 null 的 ITaskResult<T> 时，TaskService 会直接完成任务。
7. callable 返回 null 时，任务进入间接完成模式，调用方需要保存 key，并在之后调用 complete(...) 或 completeExceptionally(...)。
8. 当结果失败或抛出异常时，TaskRetryService 根据 TaskRetryStrategy、maxRetries、retryInterval 判定是否重试。
9. 如果任务超过 expirationDuration，TaskService 会尝试按过期异常重试；无法重试时以 TaskExpirationException 完成。
10. 调用方应在 TaskFuture 链尾调用 finish()，否则未处理异常不会被统一日志记录。
```

## 3. TaskConfig 主要参数

| 参数 | 默认值 | 说明 |
|:---:|:---:|---|
| `name` | 必填 | 任务名称，用于日志和排查。 |
| `callable` | 必填 | 实际执行逻辑，返回 `ITaskResult<T>`；返回 `null` 表示稍后间接提交结果。 |
| `delayDuration` | `Duration.ZERO` | 初次执行前延迟。 |
| `maxRetries` | `0` | 自动重试次数。 |
| `retryStrategy` | `ALWAYS_RETRY` | 重试触发策略。 |
| `retryInterval` | `Duration.ZERO` | 重试之间的等待时间。 |
| `taskStageNamespace` | `"default"` | 自动注入阶段链的 namespace。 |
| `taskStages` | `null` | 手动指定阶段链。非 `null` 时优先使用该列表。 |
| `expirationDuration` | `10` 分钟 | 任务最大存活时间，超时后会异常完成或重试。 |

## 4. Task API

| API | 说明 |
|---|---|
| `ITaskService.subscribeWithFuture(UUID, ITaskConfig<T>, Class<T>)` | 指定 key 注册任务并返回 `TaskFuture<ITaskResult<T>>`。 |
| `ITaskService.subscribeWithFuture(ITaskConfig<T>, Class<T>)` | 使用随机 key 注册任务。 |
| `ITaskService.subscribe(...)` | 注册任务并自动在 returned future 上调用 `finish()`，适合不关心结果的任务。 |
| `ITaskService.complete(UUID, ITaskResult<?>)` | 间接提交成功或失败结果；返回 `false` 表示已进入重试。 |
| `ITaskService.completeExceptionally(UUID, Throwable)` | 间接提交异常；可能触发重试。 |
| `ITaskService.forceCompleteExceptionally(UUID, Throwable)` | 强制异常完成，不再重试。 |
| `TaskFuture.finish()` | 链尾异常处理入口，默认写入 `ILogger#errorWithReport`。 |
| `TaskFuture.finish(Consumer<Throwable>)` | 自定义异常处理。 |
| `TaskFuture.unwrapException(Throwable)` | 解包 `CompletionException`、`ExecutionException`、`TaskRetryRuntimeException`、`TaskNoRetryRuntimeException`。 |

## 5. TaskStage API

| API | 说明 |
|---|---|
| `@TaskStage(value = "...", priority = n)` | 将类注册为任务阶段。`value` 是 namespace 列表，`priority` 越大越先进入。 |
| `ITaskStage.process(TaskStageChain chain)` | 阶段处理方法。通常先做前置逻辑，再调用 `chain.next()`，最后做后置逻辑。 |
| `@SetTaskStage("namespace")` | 标注在 `MethodCallable` 对应方法上，强制该任务使用指定阶段 namespace。 |

```java
@TaskStage(value = "audit", priority = 100)
public class AuditStage implements ITaskStage {
    @Override
    public ITaskResult<?> process(TaskStageChain chain) {
        // before
        ITaskResult<?> result = chain.next();
        // after
        return result;
    }
}
```

# 三. Timer 模块

## 1. 定时器类型

| 类 | 触发规则 |
|:---:|---|
| `FixedRateTimer` | 每隔固定 `rate` 触发一次，不等待上一轮任务完成。可能并发执行。 |
| `FixedDelayTimer` | 上一轮任务完成后再等待固定 `delay`。保证同一个定时器不会并发执行。 |
| `SmartRateTimer` | 每隔固定 `rate` 尝试触发；若上一轮未完成则等待。保证单线程语义。 |
| `CronTimer` | 使用 Spring `CronExpression` 计算下一次触发时间，时间工具统一使用中国时区。 |

## 2. Timer API

| API | 说明 |
|---|---|
| `ITimerService.register(ITimer<?>)` | 注册自定义定时器。 |
| `registerByRate(...)` | 快速注册 fixed-rate 定时任务。 |
| `registerByDelay(...)` | 快速注册 fixed-delay 定时任务。 |
| `registerBySmartRate(...)` | 快速注册 smart-rate 定时任务。 |
| `registerByCron(...)` | 快速注册 Cron 定时任务。 |
| `ITimerTaskConfig.setTaskFutureChain(...)` | 配置每次任务返回后执行的 future 链。链尾由 timer 自动调用 `finish()`。 |
| `ITimerTaskConfig.setLivable(...)` | 设置生命周期；`isAlive()` 为 `false` 后定时器会被移除。 |
| `ITimerTaskConfig.setPausable(...)` | 设置暂停逻辑；`isPaused()` 为 `true` 时跳过本轮触发。 |

`TimerService.timerLoop()` 由 `@Scheduled(fixedDelayString = "${tea-neko.timer.update-delay-ms:10}")` 驱动，默认每 10ms 检查一次所有定时器。

## 3. 使用示例

```java
timerService.registerBySmartRate(
        "refresh-cache",
        "default",
        () -> EmptyTaskResult.INSTANCE,
        Duration.ofSeconds(30),
        EmptyTaskResult.getResultType()
).setTaskFutureChain(future ->
        future.whenComplete((result, error) -> {
            // 每次定时任务完成后的处理
        })
);
```

# 四. 注意事项

| 场景 | 建议 |
|---|---|
| 不需要返回值 | 返回 `EmptyTaskResult.INSTANCE`，不要返回 `null`。 |
| 需要异步等待外部回调 | 使用指定 key 注册任务，callable 返回 `null`，外部回调中调用 `complete` 或 `completeExceptionally`。 |
| 需要统一错误日志 | 所有 `TaskFuture` 链尾调用 `finish()`。 |
| 任务可中断 | callable 内部主动检查 `Thread.currentThread().isInterrupted()`，阻塞点要正确处理 `InterruptedException`。 |
| 数据库任务 | 优先使用 `database` 模块的 `DatabaseTaskConfig`，它已经接入事务、缓存任务和重试阶段。 |
