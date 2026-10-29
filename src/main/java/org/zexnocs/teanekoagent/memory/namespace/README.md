# 记忆空间注册与存储基础

本目录实现 S5A1 注册基础及 S5A2a 绑定事务：稳定业务 ID、真实创建来源、个人配额、分页列表、元数据 revision、数据 generation、scope 绑定版本与持久化操作回执。它独立于人格补正、用户画像、Prompt 和平台发送；组件只保存不可变数据，数据库和 Spring 依赖留在适配器及组合根。

`MemoryNamespaceRegistry` 和 `MemoryNamespaceBindings` 是可信宿主使用的底层服务。宿主应先完成当前 Agent 启用及操作权限检查，再调用注册或绑定服务；它们尚未接入模型工具、`/agent` 或 `/cfg`，不会在应用启动时自动创建空间。独立登记成功只表示空间元数据已提交；绑定事务成功后仍应通过会话同步入口协调当前引用，不能只根据历史绑定回执发送“已重置完成”。

## 标识与创建来源

`MemoryNamespaceId` 是业务共享空间 ID，与 EasyData 的物理 namespace 字段分开。`default` 为默认共享空间，无个人创建者且不占个人配额。用户自建空间由原始创建 requestId 定位为 `ns-<UUID>`；重试保留原 ID 和完整可信 actor，不能每次生成新 requestId。相同 ID 换成不同 UUID、client/scope 或来源消息会返回 CONFLICT，不覆盖原来源。

新业务 ID 只接受一至六十四位的小写字母、数字、点、横线和下划线，并以字母或数字开头，不自动修剪或改大小写。旧 `scope:...:agent:...` 等存储定位不直接解释成新业务 ID。创建者使用 TeaNeko UUID，平台用户 ID、来源 client/scope/消息及时间作为审计来源保留；来源不自动变成同空间长期事实的检索隔离条件。

## 配额及事务结果

```properties
tea-neko.agent.memory.max-created-namespaces=5
```

本地策略构造时不读取值，每次创建使用最新的正整数配额。非法配置只使本次创建失败，异常不回显原始值；修正后恢复。缩小配额不删除已有空间。当前注册表尚无归档/注销操作，所有自建空间都计入活动配额；默认共享空间免配额。

`create(requestId, actor)` 等待真实事务完成。`listCreated(actor, offset, limit)` 只以真实调用者 UUID 列举自建空间，页面为只读列表，大小一至一百；不接受模型提供的其他用户 UUID。系统任务不能借用用户的创建配额或列表身份。列表不会把当前绑定者误当作原创建者。

回执区分 CREATED、UNCHANGED、QUOTA_EXCEEDED、CONFLICT、NOT_FOUND 和 GENERATION_ADVANCED。基础设施失败使 Stage 异常结束，不变成“已提交成功”。相同创建请求即使在配额已满后仍可确认原记录，保留原创建时刻；元数据和创建索引在同一事务提交或回滚。取消返回的 Future 视图不取消已经交给数据库的事务。

## 适配器与存储布局

`IMemoryNamespaceStore` 是可替换的事务端口；`EasyDataMemoryNamespaceStore` 复用现有 `IDatabaseService`、`DatabaseTaskConfig.pushAsync()` 及 `GeneralEasyDataRepository`，不新增线程池、通用事务框架或数据库表。

| 表 | 物理 namespace | target | o_key | 存储内容 |
| --- | --- | --- | --- | --- |
| general_data | agent.namespace.v1 | namespace | 业务空间 ID | schemaVersion=1 的空间元数据 |
| general_data | agent.namespace.v1 | creator:&lt;TeaNeko UUID&gt; | owned | schemaVersion=1 的个人活动空间索引 |
| general_data | agent.namespace.v1 | binding:&lt;scope 摘要&gt; | current | schemaVersion=1 的权威绑定 |
| general_data | agent.namespace.v1 | binding-op:&lt;scope 摘要&gt; | 原始 requestId | schemaVersion=1 的原命令及业务回执 |

首次插入使用既有唯一键仲裁。更新使用事务读取的原始 JPA 实体及 `@Version`，通过 `saveAndFlush` 保留并发校验，禁止无版本 upsert。个人索引虽为列表，但受行版本保护，并与对应空间记录同事务提交；竞争失败由 Core 的现有数据库重试阶段重新读取，不继续使用失败事务中的旧值。只在最终提交成功后公开回执，不使用 EasyData DTO 缓存，避免提交前污染缓存或读取旧配额。

查询按空间或创建者稳定键进行，分页只读取索引指定的当前页记录，不调用全表 `findAll()`。JSON 使用显式 schema，验证记录定位与创建者归属；损坏 JSON、未来 schema 或索引异常都会失败，不能按空状态覆盖原数据。存储没有凭据、Bean、执行控制、HTTP 客户端或 Java 类名。

## 数据代次与后续边界

`revision` 表示元数据版本，`generation` 表示有效事实/画像的数据代次，两者独立。存储端口的 `advanceGeneration(id, expectedRevision, requestId)` 是后续 DEBUG 管理服务使用的 CAS 原语，当前不注册 clear 命令或工具。它先推进持久化代次，尚不物理清理事实或宣称整个逻辑清空流程完成。

重试必须保留原 expectedRevision 和 requestId。当前同一次换代可返回 UNCHANGED；已有后续变更时旧版本返回 CONFLICT，不再次推进。最近换代请求字段不是无限历史的持久化回执账本，不能把改变 expectedRevision 的再次提交当作原请求重试。版本耗尽明确失败，不允许整数溢出重置代次。

## scope 绑定事务与稳定回执

`MemoryNamespaceBindingRequest` 固定原始 requestId、真实 actor、目标、预期引用版本及 CREATE_AND_BIND / BIND_EXISTING 模式。操作目标 scope 只来自 actor，不接收第二份可替换的 scope。`MemoryNamespaceBindings.createAndBind` 将新空间、个人配额索引、绑定和回执一起提交；`bindExisting` 只绑定已登记目标，不修改创建来源、不扣创建配额，也不自行添加“只能绑定自己空间”或“必须 DEBUG”的限制。权限仍由上层按 OWNER/私聊管理边界验证。

`IMemoryNamespaceBindingStore` 由独立绑定适配器实现，与元数据适配器在同一数据库任务内协作，不调用异步 create 后再串联 ConfigData set。引用行使用原 JPA Version，首次插入由唯一键仲裁；绑定或回执写入失败会回滚此前的空间与索引写入。引用版本不匹配时先拒绝，不留下孤立候选空间。相同 scope 的并发修改必须使用原 expectedRevision，失败重试不能擅自换成新版本。

绑定以完整 client/scope 定位，不加入操作者 UUID 或 Agent 实例。物理键对两个带长度边界的字段生成 SHA-256，正文仍校验完整定位，不把摘要当授权。缺失引用返回 revision=0 的隐式 default，读取不写数据库；首次实际切换为 revision=1。同目标、正确版本的绑定返回 UNCHANGED，不更新原来源或推进会话边界；切回先前空间或 default 仍会推进引用版本，避免旧运行引用重新变得有效。

原始操作由 scope + requestId 定位。首次成功和业务拒绝都保存明确回执；相同参数重放返回历史结果，不再次创建、扣配额或切换。相同 ID 改目标、身份、方式或版本只报告 CONFLICT，保留原回执。配额提高或缺失目标后来出现后，原拒绝请求也不会变成一次新动作；新的明确操作须使用新 requestId。回执目前逐操作保留，尚未实现保留期清理；不在升级中自动删除这些防重记录。

历史 BOUND 回执不表示该绑定现在仍有效。例如请求 A 切到空间甲，请求 B 随后切到乙，再重放 A 只返回 A 当时的回执，当前引用仍为乙。上层需读取 `readReference` 并比较 namespaceId、bindingRevision 和 generation；不能根据历史回执重复通知会话失效或对外确认当前切换。

`MemoryNamespaceReference` 是同一事务读到的纯数据观察，不是永久权限或数据库锁。默认共享空间尚未登记时读取为空；显式绑定的目标缺失、schema 损坏或 scope 不一致时异常结束，不回退到共享空间。换空间推进 bindingRevision，逻辑清空推进 namespace generation，两者独立，使用该快照的动作最终仍要在对应存储提交时复核。

## 独立轮次的引用适配

`IAgentMemoryReferences` 是 Agent 所属的异步运行端口；`StoreAgentMemoryReferences` 实现当前引用解析与只读复核，复用上述事务存储。构造不触碰配置/数据库；真实 scope 注册和开关在业务执行域前后检查。显式解析仅在默认空间尚未登记时调用 `ensureShared`，完成后重新读取权威引用，读取失败不会构造虚假的 default。只读复核缺失或版本变化时拒绝，不初始化存储或替换旧引用。

九参数 `AgentService` 固定可信组件及结果来源，在模型、工具和最终交付前等待异步复核，取消与各阶段时限约束等待。默认 Spring 轮次和会话邮箱已共用此端口；邮箱在队首先解析引用再装配窗口，提交与重放前只读复核。引用变化不再公开旧答案，已观察诊断仍保留。详见 [Agent 运行说明](../../agent/README.md)。

## 后续边界

默认邮箱已提供 `synchronizeMemory(scope, parent)`，绑定提交后读取当前引用，协调同 scope 的全部实例。只有引用变化才原子换 epoch、清窗口和完成记录；旧运行槽保留到结束确认。通知失败可在下一队首惰性恢复，旧通知不能回退已观察版本，完成重放不补执行。本批没有向 ConfigData DTO 添加可直接写回的 memoryNamespace 字段；未来 `/cfg` 必须调用同一权威绑定路径，不能维护第二份独立值。

[事实/偏好存储](../fact/README.md) 已在追加事务中锁定本 namespace 元数据并校验 generation，记录、去重索引及操作回执一起提交。画像写入后续也须在提交时检查 generation，DEBUG clear 再接入分批物理清理；绑定已有空间沿用用户确认的共享规则。聊天管理授权、持久化工具/发送幂等、旧数据转换、Recall、画像、人格补正与摘要仍待相应阶段完成。

本批不读取、迁移或删除旧 `llm_related` 表及 memory/profile/personality 键；这些数据的显式兼容映射留在后续事实/人格适配，不将旧 scope/agent 定位悄悄变成新 namespace 隔离规则。

离线测试用事务替身模拟唯一键、Version、提交失败与回滚，覆盖真实适配器及并发竞争；它们不代替 MySQL/JPA、数据库排序规则与实际部署事务代理的实机验收。
