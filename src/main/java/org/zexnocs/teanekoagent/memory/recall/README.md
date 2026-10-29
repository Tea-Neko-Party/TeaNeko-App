# 确定性事实 Recall

S5B2a 提供当前 namespace/generation 内的有界候选读取、确定性筛选排序和证据预算。`IMemoryRecallService` 供可信宿主显式调用；构造不读取配置或数据库，不自动注册工具、注入 Prompt、生成画像或查询聊天客户端。读开关、scope 启用、当前绑定及业务权限由调用和实际使用证据的边界校验，不能从请求 DTO 推导授权。

## 从存储到业务的依赖

`IMemoryRecallStore` 只返回稳定候选前缀与是否读取到末尾，不执行人格或聊天策略。`EasyDataMemoryRecallStore` 复用 namespace 的 DatabaseTask、事实 schema 和分页，不建立新表、缓存或线程池。`MemoryRecallService` 依赖该端口、本地默认策略、Clock、借用的业务执行器及可信 JSON 数组度量函数；候选完成后切回业务域进行过滤、排序和度量，不在数据库完成线程执行这些工作。

读取先对目标 namespace 元数据取得 PESSIMISTIC_READ 锁，再验证 generation，锁持有至整个多页任务提交。普通事实追加、兼容导入和 clear 使用同一元数据的写锁/更新边界，因此不能在读取期间插入较小 ID、使游标漏读却仍宣称完整。所有未来修改/清理写入同样必须遵守这个锁协议；不能绕过它直接更新事实行。

每页最多解析 100 条原记录，可额外读取一行判断后续存在；解析检查数最多为本次 maxScanned。整个有界读取在同一个事务完成，候选只保留检索证据投影，不累计旧审计 JSON。单页原记录扫描 `IMemoryFactStore.list` 仍保留原合同，多个独立 list 调用不构成这个快照协议。

## 查询和时间

`MemoryRecallQuery` 支持显式主体 UUID、来源 client/scope、类型、标签、关键词和事件时间范围。默认不限主语/来源，不按当前发起者、群或人格重新隔离共享 namespace；提供筛选条件不会改变实际来源或归属。

- 类型为空表示不限；最多 16 个标签，按原标签精确匹配且必须全部存在。
- 最多 8 个关键词，每项最多 128 个 Java 字符，去首尾空白并按 Locale.ROOT 小写去重。每个词必须命中正文或某个标签的字面子串；不解释正则、SQL 或查询语言，不检索操作者身份、工具文本或旧审计原文。
- 所有候选先按本次固定 Clock 时刻排除到期记录，expiresAt 等于该时刻即过期。单次只取一次时钟，来源等待后不改用另一个有效期参照。
- 时间范围使用 Instant 闭区间交集，单侧事件端点表示时间点；未知事件仅在 includeEstimated=true 时按 createdAt 回退，证据标记 RECORDED_TIME_ESTIMATE。false 时排除未知事件，不把记录时刻冒充事情发生时刻；来源提供的事件时间仍保留原精度/表达。

排序依次为重要度降序、置信度降序、更新时间降序、稳定 UUID 字符串升序。没有语义向量分数，不把重要度排序宣称为语义理解。筛选发生在有界候选扫描后，条数上限不用于提前终止仍有预算的扫描。

## 预算与完整性

```properties
tea-neko.agent.memory.recall.injection-limit=12
tea-neko.agent.memory.recall.tool-limit=8
tea-neko.agent.memory.recall.hard-limit=20
tea-neko.agent.memory.recall.max-characters=16000
tea-neko.agent.memory.recall.max-scanned=1000
```

Spring 适配器在每次业务执行中懒读一份合法策略；在途查询继续用原策略，下一次调用才使用更新。非法配置明确失败，修复后可恢复，不偷偷回退到宽松值。用途 INJECTION/TOOL 只选择默认条数，不执行对应动作。

显式 `MemoryRecallLimits` 可把条数提高到本地 hard-limit，字符和检查数只能收紧本地上限。执行绝对界限为结果 1–100、检查数 1–10000、字符 2–1000000，防止误配置成无界工作量；默认硬上限仍为 20。原候选检查预算包含过期和未命中的记录。

字符按**紧凑证据数组 JSON 的 Java UTF-16 字符数**计量，包括全部证据元数据、JSON 转义、数组括号和逗号；不含结果外层引用/统计信封，不是 token 数、UTF-8 字节或最终工具/Prompt 总长度。装配使用明确的紧凑数组 writer，独立于输入 Mapper 的美化开关。工具总长度与轮次输入预算仍在上层执行。

每条证据完整装入预算，过长时跳过并继续寻找能容纳的后继条目，不截断事实正文、来源或时间。这会产生带 CHARACTERS 限制的结果，不能把跳过的事实当成不存在。

`MemoryRecallResult` 保留固定引用、过滤时刻、检查数、已检查范围内的命中数、实际负载字符数及限制集合：

| 限制 | 含义 |
| --- | --- |
| SCAN | 尚有原记录未检查，当前排序仅在检查前缀内成立。 |
| RESULTS | 有符合条件的记录因条数预算未返回。 |
| CHARACTERS | 有符合条件的完整证据无法装入字符预算。 |

只有 `definitivelyEmpty()` 为真，才能确认**这个读取边界和查询条件**下没有结果；`evidence().isEmpty()` 可能只是预算不足。命中数不包含未检查的记录，不是全空间总数。当前后端按 UUID 前缀有界扫描，没有可跨调用复用的快照游标；扩大搜索需调整本地工作预算，未来可替换为有索引的候选后端。不能反复读取同一前缀后声称已经搜索全部。

## 证据、失败和使用方式

`MemoryRecallEvidence` 保留记录/空间/代次/版本、主体、事实内容、可信来源、记录/更新时刻、locked 和事件时间依据。导入记录的 source 仍表示真实导入操作者；`externalOrigin` 只保留原系统/物理位置/旧 ID/摘要供追溯，原始 JSON、原来源全文和调整明细通过独立可信管理读取，不直接作为检索数据输出。

```java
var query = new MemoryRecallQuery(null, null, Set.of(MemoryFactValue.Kind.PREFERENCE),
        Set.of("饮品"), List.of("茶"), null, null, true);
var request = new MemoryRecallRequest(namespaceReference, query, MemoryRecallRequest.Use.TOOL, null);
var result = recallService.recall(request);
```

示例中的 namespaceReference 是宿主已授权且固定的引用，不能由模型更改。结果不是高优先级指令，也不是当前绑定仍有效的凭据；实际注入或工具交付前仍要复核引用、启用状态和读开关。完整快照不保证随后没有新写入或 clear。

配置错误为 `MemoryRecallException(CONFIGURATION_ERROR)`，来源/度量/调度失败为 `MemoryRecallException(UNAVAILABLE)`，均只公开固定中文信息且没有原异常链。空间缺失或代次变化保留 `MemoryFactGenerationException.missingNamespace` 分类，不伪装正常空结果。来源合同损坏、跨空间、超量或半途解析失败也异常结束，不返回前面已收集的部分成功。取消观察视图不取消已经提交的只读事务，执行器由 Core 拥有。

本批离线验证涵盖筛选、稳定排序、时间/过期、共享与隔离、各类预算和元数据计量、配置更新、线程切换、调度拒绝、提交完成及多页读取与追加/clear 的竞争，并解析真实 Hibernate 共享锁查询。事务替身使用更严格的独占锁模拟读写互斥；MySQL 的实际事务代理、共享锁、隔离级别、排序规则与读并发性能仍需实机验收。

按 namespace+UUID 的画像、明确新增意图及读写工具、Prompt 接入、持久化聊天历史/客户端补取和人格仍留在后续批次，不由这个 Recall 服务自动完成。
