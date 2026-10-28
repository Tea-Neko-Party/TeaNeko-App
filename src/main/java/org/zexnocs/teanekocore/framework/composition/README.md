# 组件组合与异步 System 管线

本包是平台无关的 Core 组合能力，不依赖 Spring、数据库、LLM 或 Agent 业务。

## 类型与状态

- `ComponentKey<T>` 用稳定 id 和运行时类型标识组件；通过 `find/require` 读取，不能用同 id 的其他类型读取或覆盖。
- `ComponentPatch` 只提供类型化 put/remove，build 后不受 builder 后续操作影响。
- `EntitySnapshot` 是带 revision 的只读快照。容器不深拷贝业务对象，组件应使用不可变 record 和防御性集合。
- `Entity.commit(expectedRevision, patch)` 原子验证版本并提交整个 patch。失败不会部分更新；旧快照保持不变。删除后保留键的类型绑定。

## 异步执行

实现 `IEntitySystem`，声明稳定 ID、必需输入、允许写入和 `after` 依赖；返回 `CompletionStage<SystemResult>`。

`SystemPipeline.compile(...)` 在装配时检查重复 ID、缺失依赖、依赖环和类型冲突。同一组件的多个写入步骤必须声明先后依赖。无依赖步骤按 ID 稳定排序，不默认并行。

`execute(entity, control)` 按顺序等待每个 Stage。System 只能取得当前快照，返回 patch 后由管线校验写入声明、执行状态和 revision 再提交；显式 optional 的步骤可以因缺组件跳过，必需输入缺失则失败。

返回 STOP 的步骤提交自己的 patch 后停止后续步骤；同步异常和异步失败均向调用者传播。已经提交的前序步骤不会回滚，外部副作用也不能依赖此管线回滚。

同一实体同一时间只允许一条管线执行；第二次运行快速失败。需要排队时由上层会话队列承担，不在此处阻塞等待自己。不同实体可使用同一个已编译管线，其状态相互独立。

执行租约属于 `Entity` 实例，不是全局按 ID 加锁。上层注册表应保证一个业务实体 ID 对应当前唯一实例；本包不会用静态全局 World 自动合并相同 ID 的不同对象。

## 取消与资源

`ExecutionControl` 位于 `teanekocore/actuator/execution`，负责取消、截止时间、运行链路和显式资源预算。

- 有截止时间时借用调用方提供的 `ScheduledExecutorService`，不创建或关闭线程池。
- 即使 System 返回的 Stage 永不完成，取消/截止时间也能结束等待并释放实体执行租约。迟到结果不会再提交。
- `whileActive` 用于将轻量组件提交与取消线性化；其回调内不得进行 I/O 或等待其他任务。
- 资源预算逐项原子扣减，未配置维度直接报错，失败预占不扣额度；child 共享额度并继承父终止，子取消不影响父执行。
- 拥有者必须在运行结束后 close 控制对象。取消包装 Stage 不代表已中断 HTTP 或撤销副作用；外部适配器应订阅终止信号释放自己的资源。

管线没有 finish/join 或私有线程池。需要接入现有 Task 时使用 `TaskFuture.asStage()` 和 `TaskFuture.fromStage(...)`，在实际异步链尾处理错误。

## 扩展边界

Core 的预算只认识通用 `BudgetKey`，组件只保存数据。人物、用户、memoryNamespace、模型选择等语义由上层定义；Repository、Bean、密钥和平台客户端不应放进组件容器。System 的服务依赖通过构造器注入。

纯 JUnit 契约测试位于 `src/test/java/org/zexnocs/teanekocore/framework/composition` 与 `actuator/execution`，无需 Spring 上下文或数据库。
