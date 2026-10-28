# 执行控制与阶段策略

`ExecutionControl` 保存执行 ID/父子链路、取消、截止时间和共享资源预算，不保存平台身份或业务配置。拥有者在整轮执行结束后 close，传入的调度器始终由调用方管理。

`child()` 继承父终止与预算；子执行终止不取消父执行。`whileActive(...)` 只用于轻量提交与取消的线性化，不能在其中进行 I/O、等待或运行用户回调。

`isWithin(executionId)` 查询自身或任意显式祖先 ID。子控制只复制不可变标识链，不为查询保留父控制对象；兄弟控制即使共享同一预算，也不是彼此后代。终止后仍可查询链路，结果不代表执行有效或取得业务权限。上层可据此检查显式重入，不能用预算对象相同代替调用关系。

`child(timeout, scheduler)` 在同一父时钟与预算上增加阶段截止时间，取“当前时间 + timeout”与父截止时间的较早者。阶段到期结束该子执行及其后代，父控制和其他阶段仍可继续使用。调用方拥有并关闭子控制；计时任务在生命周期锁外登记，登记失败会关闭子控制、解除父监听。该入口适用于包含准备、执行器排队及异步等待的完整阶段。

`termination()` 提供只读终止 Stage。需要解除资源监听时，使用 `onTermination(listener)` 返回的 TerminationRegistration，并在资源完成后 close。已经停止时立即通知；重入轻量提交时的通知延迟到生命周期锁释放后。监听最多执行一次，单个 RuntimeException 不阻断其他监听；close 释放回调引用，但不等待已经开始运行的回调。

`guard(stage)` 在执行终止时完成包装 Stage，适合等待结果；底层 HTTP 等外部操作仍须通过终止监听释放自身资源。guard 完成、子执行关闭时会解除对应监听。

`ExecutionPolicy` 是独立调用参数，组合可选的阶段 timeout 和借用的 ExecutionControl：

```java
var timeoutOnly = ExecutionPolicy.withTimeout(Duration.ofSeconds(15));
var controlled = ExecutionPolicy.controlled(Duration.ofSeconds(30), control);
var inherited = ExecutionPolicy.controlled(null, control);
```

时限必须为正值；显式时限覆盖调用端默认值，再由执行剩余时间收束。策略不拥有 control，也不重置预算。APIResponse 已实现策略对应的请求排队/超时/取消与资源释放；其他服务只有在实际接入时才获得这些行为，不能只接收一个策略对象就宣称已支持取消。

## 通用资源预留与结算

`ResourceBudget.consume` 保留固定消耗语义。`reserve(key, units)` 原子占用已声明的额度，返回一次性 Reservation；在实际消耗确定后调用 `settle(charged)`，用非负实际量替换本句柄预留，未用部分退还，更多消耗如实补记。`used` 和快照包含已结算消耗与其他在途预留，结算后读数可以降低。

尚未发起业务时可以 close 预留，幂等退还全部额度。业务已经发起而消耗未知时应保守结算，不能用 close 假装未发生。一个句柄只能 settle 一次，settle 后 close 不重复退还；这是一项资源记账操作，不负责取消外部任务或回滚副作用。

若实际量使当前占用超过上限，保留超额读数并封锁该维度；其他在途预留后续退款不能解除封锁。remaining 为零，新 consume/reserve 均被拒绝。累计长整数溢出时读数固定饱和到 Long.MAX_VALUE，Usage 的 exceeded/overflow 明确标识无法继续准入或精确表示的情况。未声明维度仍是错误，只有 isDeclared 可用于查询可选能力是否存在。
