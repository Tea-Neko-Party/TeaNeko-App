# 按用户 UUID 重建的证据画像

S5B2b1 提供 `IUserProfileService`，按当前 namespace、generation 和真实 TeaNeko UUID 读取已接受的事实/偏好。画像是不可变读取视图，不单独持久化一份推断结论，不创建画像表、缓存、后台学习或模型调用；每次读取都重新经过独立 Recall。

同一空间中，该主体来自不同群、客户端或操作者的证据可以共同组成画像。主体与 `MemoryFactSource.actor` 分开，不能把写入者或导入者当成画像主体。更换空间不复制旧画像；缺失空间或旧 generation 明确失败，不用其他空间、旧缓存或旧 memory/profile 行补齐空白。

## 端口与画像组成

`UserProfileRequest` 固定已授权的 `MemoryNamespaceReference`、主体 UUID、读取用途和可选 `MemoryRecallLimits`。这些数据不授予权限；可信宿主仍须检查 scope 启用、当前绑定、读开关和原业务权限。服务不从模型或平台字符串猜 UUID，也不从来源操作者推导主体。

`UserProfileService` 只依赖 `IMemoryRecallService` 和借用的业务执行器。它执行一次明确主体的查询，允许全部 FACT/PREFERENCE，不额外限制来源 scope、标签、关键词或事件日期；不分两次读取事实/偏好后拼成不一致画像。初始调用和投影校验都在 Core 业务执行域，构造无 I/O，不关闭共享执行器。

`UserProfileView` 保留：

- 固定引用、主体 UUID、有效期过滤时刻 observedAt，以及有序 evidence。
- 每条证据原 ID、revision、类型、文本、标签、置信度、重要度、来源、事件/记录/更新时间、有效期与 locked；未知事件继续标明 RECORDED_TIME_ESTIMATE。
- 检查数 scanned、当前检查范围内该主体的命中数 matched、覆盖限制和原证据数组字符数。
- projectionVersion=1，表示画像构建规则版本；证据 ID/revision 表示具体来源版本。它不是全空间的单调版本，不是缓存失效依据，也不声称两次读取之间没有新增或清空。

`facts()` 与 `preferences()` 只按原记录类型返回不可变分类列表，保留各类在原 evidence 中的相对顺序。它们不重复持久化证据，不将同标签或相互矛盾的文本覆盖为一个“最新结论”，也不从聊天、人格补正或其他主体推断性格、关系、情绪和未获准的个人事实。完整画像仅表示该读取边界已覆盖当前有效证据，不代表证据内容必然正确或不存在语义冲突。

## 完整性、预算与失败

用途只选择现有 Recall 默认值：INJECTION 默认 12 条、TOOL 默认 8 条，默认硬上限 20；字符/工作量沿用本地 Recall 策略。显式预算继续受本地硬上限约束，画像不扩大预算、重新扫描以绕过限制或丢弃原统计。

`limits()` 原样保留 SCAN、RESULTS、CHARACTERS。检查预算包含其他主体、过期和无关记录；matched 只表示已检查范围内该主体的有效证据数，不是整个空间总数。不能取默认十二条后直接声称已读取全部资料。

`isComplete()` 只在没有覆盖限制时为真；只有 `definitivelyEmpty()` 才确认本次读取边界该主体没有有效事实或偏好。`facts().isEmpty()`、`preferences().isEmpty()` 或 `evidence().isEmpty()` 都可能只是预算不足，不能作为确定“不认识这个用户”的结论。

characters 沿用 Recall 的**紧凑证据数组 JSON 的 UTF-16 字符数**，包括元数据与转义，不含画像外层字段/完整性信封，不是 token 或最终工具/Prompt 总长度。分类方法不截断事实或重新序列化改变这个计量。实际工具和 Prompt 仍须应用各自总预算。

画像对来源结果再检查完整引用、唯一主体、类型、有效期和显式预算。跨主体、过期或引用错位的数据整体失败，不通过静默过滤生成貌似完整的画像。配置/来源/调度失败保持 `MemoryRecallException` 固定分类，空间缺失和代次变化保留 `MemoryFactGenerationException`；错误不附带原始正文或异常链，不降级成正常空画像。取消观察视图不取消已经提交的只读事务。

## 可信宿主使用方式

```java
var request = new UserProfileRequest(namespaceReference, teaNekoUserId,
        MemoryRecallRequest.Use.INJECTION, null);
var profile = userProfileService.read(request);
```

namespaceReference 和 teaNekoUserId 由可信调用流程提供。读取期间的稳定快照由原 Recall 事务维护；读取完成后仍可能有新追加、到期或 clear，实际注入/工具交付前须重新复核引用与开关。异步迟到的旧视图始终携带旧引用，不更新成新 generation，也不写回新画像。

导入证据的 source 表示真实导入操作者；externalOrigin 保留旧系统/位置/ID/摘要供追溯。旧原 JSON、原来源全文和兼容调整明细仍只由独立可信管理接口读取，不通过画像泄露，也不被提升为运行指令。

本批离线验证覆盖唯一主体、来源与版本追溯、矛盾记录保留、各类部分覆盖、预算/过期/坏来源拒绝、调度拒绝、观察取消、换空间、clear、后页命中、当前证据重建，以及旧 JSON 导入后的原文隔离。实际 MySQL/JPA 的事务代理、共享锁、隔离级别与部署行为仍需实机验证。

宿主可通过 [可信记忆访问边界及模型工具](../access/README.md) 统一检查当前开关与引用，再读取本画像；该边界已提供明确新增意图与本人追加能力，memory_profile 已接入实际 Agent 工具往返。默认自动 Prompt 源已优先读取当前真实用户画像，再以剩余预算查询其他主体的字面相关事实；资料不重复写入历史，使用与重放前复核当前读开关、引用及证据到期。人格补正、压缩摘要、聊天历史及知识源保持独立，不由画像服务代替。
