## 一. Actuator 结构介绍
Actuator 是负责进行异步任务执行的模块，主要包含以下几大类：

|   包   | 介绍                                                                                  |
|:-----:|-------------------------------------------------------------------------------------|
| task  | 用于将一个异步 Supplier 包装成 Future 的模块<br>主要用于根据 Supplier 获得 Future 的 result，支持 Future 执行链 |
| timer | 用于定时、重复地将 Supplier 提交给 task 模块执行                                                    |

## 二. Task 模块
Task 模块的核心类是：

|       类        | 介绍                                                                                               |
|:--------------:|--------------------------------------------------------------------------------------------------|
|    Task<T>     | 异步执行 Supplier 的包装器<br>目标是执行 Supplier 直接或者间接获取 result 来提交给 future，从而执行 future 的执行链 <br>是一次性的      |
| TaskConfig<T>  | 用于定义一个 Task 执行的形式，快速创建一个 Task <br> 是可以重复使用的                                                      |
| TaskResult<T>  | 由 Task 执行后可以直接或者间接获取的 result <br> 可以表示执行失败或者成功                                                   |
| TaskFuture<T>  | 包装一个 CompletableFuture<TaskResult<T>><br>用于管理获取 result 后执行的 future 链<br> 提供快速记录异常日志的方法           |
| TaskStageChain | 执行主函数 Supplier 的执行链，在提交 task 前可以通过 AOP 形式修改 supplier 执行前、执行后结果的执行链<br>可以使用 namespace 自动注入也可以手动注入 |

其核心功能包含：

|    功能     | 说明                                                                                 |
|:---------:|------------------------------------------------------------------------------------|
|   Retry   | 当一个 Task 的 Supplier 执行失败时，进行重新执行的操作<br>当 retry 失败时才会提交失败 result 或者以异常形式结束一个 future |
| TaskStage | 以 AOP 形式在 Task 执行前、执行后结果的执行链中插入一个 Stage<br>可以通过 namespace 自动注入也可以手动注入              |

一个 Task 可能存在的状态为：
|   状态   | 说明 |
|:-------:|-----|
| Created | 已经由 TaskConfig 创建并注册到 TaskService 中，但没有到达执行时间 (取决于 `delayDuration` 字段) |
| Submitted | 已经提交给 TaskExecuteService 执行，但还没有执行完成 |
| Executed | 已经执行完成，正在等待是否提交给 future 链还是等待重新执行 |
| Retrying | 达到重试决策条件，正在等待重新执行<br>状态同 Created，还没达到执行时间 (取决于 `retryInterval` 字段)<br>其接下来的状态为 Submitted 流程 |
| Waiting | supplier 执行完成，但是是通过间接提交的方式来提交 result 给 future 链<br>正在等待 result 被提交给 future 链来执行 future 链 |
| Done | 执行完成，成功将结果提交给 future 链或者以异常形式结束 future 链 |

其流程如下：
```markdown
1. 创建：创建 TaskConfig 并生成 Task
2. 注册：在 TaskService 中注册该 Task （同时为 TaskConfig 注入 TaskStage 列表）
3. 初次执行：当到达该 Task 执行时间时 (由 `delayDuration` 字段决定)，TaskService 提交该 Task 到 TaskExecuteService 执行
4. 创建链：TaskExecuteService 根据 Task 创建 TaskStageChain，开启其他线程来执行该 TaskStageChain
5. 链执行：执行顺序为 阶段A → 阶段B → ... → 阶段N → 任务 → 阶段N → ... → 阶段B → 阶段A → 提交任务
6. 重试：如果达到重试决策条件，则会尝试重试该 task；如果没达到重试次数，则再次注册该 task 到 TaskService 中
7. 重试执行：如果 Task 达到了重试的执行时间 (由 `retryInterval` 字段决定)，则返回步骤 4 的流程
8. 结果：如果没有达到重试决策条件，或者重试次数达到上限，则会最终提交 TaskResult 给该 Task
9. 直接提交：如果该 Supplier 返回非 null 值，则将该 Result 提交给 Future 来执行 future 链
10. 间接提交：如果该 Supplier 返回 null 值，则需要异步将 Result 通过 TaskService 提交给 Future 来执行 future 链
```
