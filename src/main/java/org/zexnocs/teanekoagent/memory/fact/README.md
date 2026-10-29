# 事实与偏好的事务存储

S5B1 提供独立事实记录、可信来源、稳定追加动作、完全重复合并、原操作回执、当前 generation 内的原证据读取，以及显式旧 JSON 转换与幂等导入。`MemoryFacts` 是可信宿主显式使用的追加服务，`ILegacyMemoryStore` 是受控兼容端口；构造不访问数据库，成功只在事务提交后返回。S5B2a 的 [独立 Recall](../recall/README.md) 在该存储之上完成过滤、排序及证据预算；当前没有接入模型记忆工具、画像生成或聊天管理命令。

## 业务边界

`MemoryFactAppend` 固定 operationId、MemoryNamespaceReference、主体 TeaNeko UUID、内容及来源。`MemoryFactSource` 保留原请求 ID、同请求内的稳定 actionId 和完整 AgentActorContext。操作 ID 应由宿主为每个事实动作创建并在重试时保留；多个工具调用不能共用一个操作 ID。数据对象不提供权限，调用方仍须校验当前 scope 启用、当前绑定、明确新增意图和原业务权限。

主体与发起者分别保存，同一 namespace 可检索不同用户、scope 或 Agent 的记录。普通用户请求新增自己的事实与 DEBUG 维护其他主体是不同的授权边界；底层存储不从文本推断授权，也不会把来源身份悄悄变成检索隔离条件。事实/偏好、用户画像和人格补正是独立能力，画像后续从接受的证据构建。

`MemoryFactValue` 只允许 FACT / PREFERENCE，正文非空且最多 4096 个 Java 字符；标签最多 16 项，每项非空且最多 64 个字符，按稳定顺序去重冻结。置信度必须为 0–1 的有限值，浮点正负零归一；重要度独立保存。标签不提升权限，正文和来源不进入异常文本。

## 时间与有效性

`MemoryEventTime` 保存事件起止、精度和原始表达；单侧端点表示时间点，倒置范围拒绝。事件时间与 createdAt/updatedAt 分开；记录时刻由可信 Clock 固定，事务重试不会重新取时。内部保存 Instant，时区展示及自然语言解析留在上层。

未知事件时间只有在 includeEstimated 为 true 时回退记录时刻；严格事件查询排除这类估算。有效期到达 expiresAt 时即过期。`list` 是包含过期证据的有界原记录扫描，按 UUID 字符串升序分页；后续 Recall 应过滤有效期、时间、类型和相关性，不能把扫描页中没有当作确定不存在。

## 存储与原子提交

`MemoryFactDataObject` 沿用既有 `llm_related` 表，继承 ID/Version；独立新物理域不会覆盖旧 memory/profile 列表。namespace 注册元数据仍在 general_data。没有新增业务表、通用事务框架或线程池。

| 表 | namespace | target | o_key | 正文 |
| --- | --- | --- | --- | --- |
| llm_related | agent.fact.v1 | record:&lt;业务空间&gt;:g:&lt;generation&gt; | 首次接受的 operationId | schemaVersion=1 的不可变事实 |
| llm_related | agent.fact.v1 | duplicate:&lt;业务空间&gt;:g:&lt;generation&gt; | 主体及全部内容的 SHA-256 | 首次事实 ID |
| llm_related | agent.fact.v1 | operation:&lt;业务空间&gt;:g:&lt;generation&gt; | 原 operationId | 完整原命令及原回执 |

`EasyDataMemoryFactStore` 复用现有 DatabaseTask。追加首先经 `MemoryNamespaceWriteRepository` 对对应 general_data namespace 元数据执行 PESSIMISTIC_WRITE 锁定查询，校验最新 generation，锁持有至同一事务结束。clear 的原元数据更新会与该行锁互斥：追加先提交则随后 clear 隐藏旧代次；clear 先提交则旧追加被拒绝。不能只在事务外读取 generation，也不能依靠只读实体的 Version 假装提交时有仲裁。

事实、内容索引和操作回执一起提交或回滚；不会无版本覆盖整列表。取消返回的 Future 视图不撤销已经提交给数据库的工作。读取和扫描在事务内检查当前 generation，空间缺失或旧代次通过 MemoryFactGenerationException 区分，不回退旧数据或伪装成空页面。缺失 namespace 不隐式登记默认空间。

同 operationId、相同完整命令返回原回执及原记录时刻；更换主体、来源、内容或固定引用只返回 CONFLICT，不覆盖原回执。不同操作的同主体、全部内容相等才合并为 DUPLICATE：保留首次事实 ID、来源和时刻，新增来源保存在自己的操作回执中。不同主体、类型、时间、有效期、标签、置信度或重要度不会仅因正文相同被合并。重复不修改 locked 记录，当前没有覆盖/解锁接口。

回执只表示原操作的提交结果，不证明当前引用、内容版本或权限仍有效。generation 校验先于旧成功回执读取，因此 clear 后旧任务不能借成功重放写回旧事实。记录 ID 和操作回执在空间及 generation 内定位；回执目前持续保留，保留期和 DEBUG 修改/清理后续单独实现。

## 显式旧资料预览与导入

旧 `llm_related` 的 namespace=memory、target=scope:&lt;scope&gt;:agent:&lt;agent&gt;、profile:&lt;旧用户&gt; 原样保留。`LegacyMemoryLocation` 要求实际旧键，不修剪或猜测 UUID；`read(location, fallbackRecordedAt)` 只读预览，缺失行返回 Optional.empty，空数组返回有效空快照。普通事实读取和启动装配均不自动导入旧资料。新旧 JPA 实体共用原表，新生产代码不导入旧 Java 类型。

预览接受实际旧 Bean 的 JSON 数组，最多 1 MiB UTF-8、1000 项；拒绝重复键、尾随数据、未知字段/类型、重复旧 ID 及超出新内容限额的资料，不静默截断。USER_FACT/PREFERENCE 分别转换为 FACT/PREFERENCE，RELATIONSHIP/PERSONALITY_DELTA/EPISODE_SUMMARY 作为 skipped 明确列出，仍由旧行保留。未来格式和损坏资料明确失败，不作为空列表覆盖。

预览与导入必须使用同一固定 fallbackRecordedAt。缺失 ID 按原行位置派生 row-index:&lt;index&gt;；缺失主体使用明确的旧 profile 键，再由调用方提供真实 UUID 映射。缺失创建时间使用固定估算，缺失更新时间沿用创建时间，调整均可审计；不调用旧 Bean 的随机 ID/当前时间默认构造。无 ID 列表被重排后可能发生映射冲突，应人工核对，不能视作全新记录重导。记录时间倒置明确拒绝。

事件时间保留原表达及全部旧精度，包括 HOUR/YEAR。旧版仅有 end 时按旧查询规则补入记录时间形成区间，倒置事件端点交换；有端点却 UNKNOWN 时标记 APPROXIMATE，无端点却声称已知精度时改为 UNKNOWN。每项保留原 JSON 和调整说明；旧 Bean 额外序列化的 eventTime.known 仅校验原端点一致性，不作为时间事实。原创建/更新/有效期、重要度、置信度和 locked 状态均保存，过期证据不在导入时丢弃。

可信宿主先检查启用、当前绑定及 DEBUG 管理权限，审核预览后，明确传入目标引用、旧主体到 TeaNeko UUID 的映射、原行 Version/摘要和导入操作者：

```java
var location = new LegacyMemoryLocation("旧 scope", "旧 Agent", "旧平台用户");
var fallback = Instant.now(); // 固定一次；确认、重试和导入均复用该值。
var preview = migration.read(location, fallback);
var imported = preview.thenCompose(optional -> {
    var snapshot = optional.orElseThrow(() -> new IllegalStateException("旧资料不存在。"));
    return migration.importSnapshot(new LegacyMemoryImport(operationId, namespaceReference, location,
            snapshot.version(), snapshot.digest(), Map.of("旧平台用户", teaNekoUserId), importSource, fallback));
});
```

示例中的 migration 为 ILegacyMemoryStore，operationId、namespaceReference、teaNekoUserId、importSource 来自可信管理流程，不由模型填写。应映射快照中全部实际 subjectKey；记录内另有主体时不能只提供画像键。importSource 保存真实导入操作者，原来源及主体保留在 MemoryFactProvenance.originalJson，不能把旧字符串伪装为可信 actor。原文仅供管理审计，不直接注入 Prompt。

导入按 namespace → 源行顺序获取 PESSIMISTIC_WRITE 锁并持有至提交。先检查目标 generation，再检查已提交请求；首次动作还须校验锁定源行的最新 Version、精确 UTF-8 摘要和实际物理定位。来源缺失为 SOURCE_MISSING，预览过期为 SOURCE_CHANGED，旧记录或请求重定向为 CONFLICT，均无部分导入。只有 IMPORTED 回执持久化；同完整命令重放返回原结果，历史成功不代表源行或当前权限仍未变化。

| 表 | namespace | target | o_key | 正文 |
| --- | --- | --- | --- | --- |
| llm_related | agent.fact.legacy.v1 | mapping | 原 memory/target/key/ID 的 SHA-256 | schemaVersion=1 的原证据与首次导入记录 |
| llm_related | agent.fact.legacy.v1 | request | 原 operationId | schemaVersion=1 的完整原命令和成功回执 |

事实、内容索引、全局旧记录映射和请求回执在同一 DatabaseTask 事务提交/回滚。相同旧 ID 只对应首次目标及主体；新请求不能将它静默转投其他空间，也不能在 clear 后重新灌入新 generation。已映射证据改变、丢失或被修改时明确拒绝，不自动覆盖、恢复或解锁。后续 DEBUG 克隆/修复需要独立管理策略，不借导入接口完成。

不同旧 ID 即使内容相同也保留独立记录，以保留不同时间、锁定和原来源；内容索引仍指向首条接受的事实，普通 append 继续完全重复合并。旧端仅追加新条目时，可重新预览后用新请求增量导入，既有映射不重复生成事实；缺失时间的旧条目仍须复用首次估算时刻，改变估算或既有原证据会整批冲突。导入不删除或改写源行，也不意味着可以停止旧部署或清理回退资料。

## 当前验收边界

离线风险测试覆盖真实旧 Bean、完整默认字段、来源/主体显式映射、时间/locked 保留、幂等账本、回滚、并发导入、源行更新和 clear 的提交竞争；用当前 Hibernate 在禁用 JDBC 元数据及 DDL 的条件下验证新旧实体映射、实际锁定 JPQL 及分页属性解析。它们不代替 MySQL/JPA 实际事务代理、隔离级别、行锁等待、数据库排序规则和部署联调。

独立 Recall 已提供查询过滤、稳定读取和证据预算；按 UUID 画像和受控工具留在下一批。当前兼容入口不开放聊天权限，不等于已完成用户可用的长期记忆或整个 S5；人格、历史持久化、自动摘要及平台接入仍按主计划继续。
