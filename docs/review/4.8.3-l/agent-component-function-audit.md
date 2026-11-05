# v4.8.3-l 组件审查历史与证据

首次补丁复评：2026/11/05；最新独立复评：2026/11/06。工作区版本：`4.8.3-l`；Git 基线：`964d205`，包含当前未提交补丁，不表示该版本已发布。

2026/11/06 文档整理：本文保留各轮审查和验证证据，不作为实现器的待办清单。实现器优先阅读[下一步实施](next-steps.md)；已确认完成的范围单独见[已完成项](completed.md)。本次迁移没有重新执行测试，也不改变原失败或通过结论。

以下第一至九节均为对应日期的审查或实施快照，其中“当前”“未修复”“待执行”“下一步”须结合当时状态阅读，不覆盖下一步文档。编号继续对应[第一补丁实施记录](../../plan/old/4.8.3-agent-component-fixes.md)，保留历史失败、修改建议和归档计划的原始验收结论。

2026/11/06 后续实施：G03 已修复，原诊断 1 项及相关回归 65 项通过，见[完成记录](completed.md#g03继承代理目录隔离)。下文第九节保留修复前结论；复用的 HTML/XML 报告目录会随重跑更新，原失败依据仍为 `review-2/run.log`。

## 一、结论

第一补丁有效，主要修正已经接入真实调用链，不是只改注释或增加未被使用的服务。

- 原 11 项中，F01–F08、F10 的原触发条件已消除，共 9 项；其中 F01 的守卫改造另引入 G01。
- F09 已解除“2048 条删除墓碑挤占缓存”的限制，但墓碑仍受整份目录的持久容量限制，属于部分修复。
- F11 已消除每轮发现，但代理缓存隔离有新回归 G02，整项尚不宜关闭。
- 新发现 G01、G02 两项 P2，均已用隔离用例复现；原 P1 的事务内守卫缺口不再保留为未修问题。
- 2026/11/05 追加 D01：根据用户提供的长期提交失败日志，核对证据校验、诊断分类和重试路径。确认当前诊断不能区分具体证据失败分支；未取得该批候选和原证据，不认定为第一补丁引入的新回归，也不计入已运行的三个隔离诊断。
- 九类组件仍有真实业务作用。“开关启用但没有输入”“声明扩展但默认装配不可用”等原问题明显减少，当前最接近后台无效工作的缺陷是 G01。

本轮没有修改业务代码或既有回归测试，只更新本报告并在 `temp/verification/4.8.3-l/review-1/` 保存诊断用例、脚本和结果。没有连接真实数据库、模型或客户端。

## 二、仍需修改的问题

P2 表示有明确触发条件的功能、扩展或可靠性缺陷；不表示所有部署都会触发，也不等于已发生线上事故。

| 编号 | 级别 | 触发条件与当前结果 | 建议 |
| --- | --- | --- | --- |
| G01（F01 后续） | P2 | 事实学习关闭、人格观察仍开启，但当前批次没有合格人格样本时，合法空筛选被判为撤权，水位不前进并进入退避；不合格前缀可阻塞后续有效样本 | 分离“没有可采用证据”和“授权失效”；允许仍获准的空批次在事务内复核后推进水位 |
| G02（F11 后续） | P2 | 两个不同工具提供者使用同一 JDK 代理类，依次调用 `prepare(bean)` 后，`select(bean,...)` 使前者读到后者的工具声明 | 将预编译规格绑定真实声明类型与 Bean 身份/编译句柄，不以代理类作为提供者唯一键 |
| F09（剩余） | P2 | 文件已回收的删除墓碑不占 2048 缓存名额，但完整条目仍写入同一目录 JSON，长期累积会触发 `8*1024*1024` 字符上限 | 将持久拒绝决定与有界素材目录物理分离或分区；保留删除语义，增加迁移及容量观察 |
| D01（日志追加诊断） | P2 | 长期候选未通过原话校验时，多种原因合并为普通异常，日志只显示 PROCESSING_FAILED；持续生成同类错误时，原前缀保留并继续退避生成。具体失败分支尚未确定 | 拆分安全原因码，生成后预校验证据关系，保留事务复核，并为持续失败提供有界恢复；不能放宽证据或伪造空成功 |

### G01：合法空观察批次被误判撤权

源码链路：

1. [StyleObservationService.java:61](../../../src/main/java/org/zexnocs/teanekoagent/component/personality/StyleObservationService.java#L61) 在没有可学习字段或合格证据时允许返回 null。[观察存储:84](../../../src/main/java/org/zexnocs/teanekoagent/component/personality/adapter/EasyDataStyleObservationStore.java#L84) 仅接受当前人格、尚未过期且原文一致的证据。因此，切换人格后积压旧人格消息、证据过期等都可能自然产生这个结果。
2. [LayeredMemoryProcessor.java:284](../../../src/main/java/org/zexnocs/teanekoagent/component/memory/layered/LayeredMemoryProcessor.java#L284) 已明确为“无观察目标且事实学习关闭”返回空 Analysis，避免无效模型调用；这不是异常输入。
3. 同文件 [306–313 行的 guard](../../../src/main/java/org/zexnocs/teanekoagent/component/memory/layered/LayeredMemoryProcessor.java#L306) 将 `!factsAllowed && !styleAllowed` 判为 ACCESS_REVOKED。此时人格观察开关仍可为 true，但 `styleAllowed` 因目标为空变为 false。
4. 异常走 `retry`。[EasyDataLongTermMemoryStore.java:262](../../../src/main/java/org/zexnocs/teanekoagent/component/memory/adapter/EasyDataLongTermMemoryStore.java#L262) 保留旧 through，仅增加退避；[claim:160](../../../src/main/java/org/zexnocs/teanekoagent/component/memory/adapter/EasyDataLongTermMemoryStore.java#L160) 下次仍从该水位领取最多四个片段。

实际复现：事实学习=false、观察 enabled=true、prepare=null，用模拟存储执行真实传入的事务 guard。预期一次空提交，实际提交计数为 0，测试失败。整个用例没有调用模型。

影响边界：这是联合整理进度停滞，不是跨 namespace 写入。若前缀持续不合格，可反复退避并挡住后续样本；资格、输入或保留期变化后可能恢复，不能描述为所有情况永久死锁。独立逐轮人格学习也不因此全部失效。

修改建议：

- 分开表示“学习域是否获准”“是否有合格目标”“是否有待写候选”，不要用目标是否为空代替授权开关。
- 空批次仍须复核任务控制、处理器生命周期、当前学习开关及原 namespace/generation/租约，再原子推进水位。
- 有事实或风格候选时分别复核所属域；有风格目标时保留当前人格、字段和版本守卫。不要为了修此回归撤销 F01 新增的事务入口及提交前守卫。

必要回归：切换人格后先有一批旧样本、随后有新样本；无可学习字段；证据到期；以及排队后真正关闭观察时仍拒绝提交。区分本轮已复现的 guard 错误与这些尚待补充的端到端用例。

### G02：代理类缓存键混淆工具提供者

[TeaNekoAgentToolMethods.java:66](../../../src/main/java/org/zexnocs/teanekoagent/component/tool/TeaNekoAgentToolMethods.java#L66) 先按真实目标类型编译规格，随后执行：

```java
METHODS.put(bean.getClass(), specs);
```

[select(Object,...):118](../../../src/main/java/org/zexnocs/teanekoagent/component/tool/TeaNekoAgentToolMethods.java#L118) 又以 `bean.getClass()` 取规格。不同目标类实现相同接口时，可以共享同一 JDK 代理类，后一次 prepare 会覆盖前一次代理键。

实际复现：使用真实 Spring ProxyFactory 创建两个共享 SharedTool 接口的代理，分别声明 `audit_alpha`、`audit_beta`，确认代理 class 相同。依次 prepare 后选择 alpha，预期 `[audit_alpha]`，实际 `[audit_beta]`。

影响不只是显示名称：目录包含 schema、client、requires 等静态合同，串用后会使暴露的声明与实际 Bean 不一致。当前诊断直接证明名称错配，未将其扩大为已完成的越权利用。

适用边界：

- [自动注册表:157](../../../src/main/java/org/zexnocs/teanekoagent/component/tool/TeaNekoAgentToolRegistry.java#L157) 使用已知 `provider.type()` 的重载，不经过这个代理类取键路径。
- 受影响的是文档支持的独立宿主及显式 Feature 的 `prepare(bean)+select(bean,...)` 路径；当前内置具体类/CGLIB 组合不能代表所有 JDK 代理扩展均安全。
- 现有 [代理测试:123](../../../src/test/java/org/zexnocs/teanekoagent/component/tool/TeaNekoAgentToolTest.java#L123) 显式使用 CGLIB，未覆盖不同目标共享 JDK 代理类的情况。

修改建议：初始化时产生绑定声明类型与实际 Bean 的不可混用句柄，或在宿主注册目录中以 Bean 名称/身份读取规格。运行期只读已编译目录，继续通过 Spring 代理调用并复核权限，不恢复消息路径的类型/注解发现。增加同接口多代理、初始化顺序互换、不同 client/requires 和实际方法调用的隔离测试。

### F09：计数分离尚未解决永久墓碑的持久容量

[StickerLibrary.java:453](../../../src/main/java/org/zexnocs/teanekoagent/component/sticker/StickerLibrary.java#L453) 已从 cacheSize 排除 `DELETED && blob == null`；管理接口也新增 cacheEntries/deletedDecisions。原 2048 条问题应确认修复，不能继续按旧描述报错。

但[文件回收:299](../../../src/main/java/org/zexnocs/teanekoagent/component/sticker/StickerLibrary.java#L299) 仅清除 blob，不清除完整 Entry 及 analysis；prune 也保留删除决定。[EasyDataStickerCatalog.java:41](../../../src/main/java/org/zexnocs/teanekoagent/component/sticker/EasyDataStickerCatalog.java#L41) 仍对同一 state 行完整解码、编码，并拒绝超过 `8*1024*1024` 的 `json.length()`。

本轮复用现有 NamespaceDatabaseFixture 和真实 EasyDataStickerCatalog，构造 12,000 个不同指纹、文件已回收且分析字段长度合法的删除条目：

| 观察项 | 实际结果 |
| --- | --- |
| cacheSize | 0 |
| JSON 字符数 | 12,216,191 |
| 目录上限 | 8,388,608 个 Java 字符，不是字节数 |
| 实际提交 | 抛出“表情包目录超过持久容量上限” |

这不是“第 12,000 条必然失败”的通用阈值，条目内容会改变大小；诊断直接构造累计状态，不是 12,000 次采集/删除的长期压测。测试通过表示确认了拒绝边界，不表示问题已修复。正常持久状态不能超过该上限，接近上限后的增长操作会失败；不据此声称所有读取都会失败。

建议将删除指纹、管理版本等必要拒绝信息独立持久化，素材目录只保留有界活动/分析状态；迁移须保持同一指纹的删除优先级和事务仲裁。不要自动清掉人工拒绝决定，不要仅调大常数或增加一个未被生产入口使用的新 Store。短期至少显示持久目录容量与接近上限状态，长期补迁移、重复采集拒绝及真实规模验收。

### D01：长期原话校验失败缺少可定位原因与明确恢复策略

2026/11/05 根据用户日志追加，供实现器修正。P2 优先级针对诊断和恢复缺口，**不是将拒绝无效证据本身判为漏洞**。本项与 G01 不同：G01 是合法空批次误判撤权；D01 是候选已经进入真实提交，并在原话校验处失败。

#### 日志依据与结论边界

用户提供的定位信息如下，仅按字段整理，没有补造模型结果或候选内容：

```text
请求：5d1e814a-7998-42f9-9c46-c49c61db8bf6
批次：256e0344-cbca-442e-bc4d-77961480c145
阶段：长期持久提交
分类：PROCESSING_FAILED；未识别异常
位置：EasyDataLongTermMemoryStore.validate:529
异常链：CompletionException → TaskNoRetryRuntimeException → IllegalArgumentException
模型状态、准入状态、HTTP 状态：未取得
处理：本批不会以失败候选推进整理进度，将按持久状态重试
```

当前[validate:517](../../../src/main/java/org/zexnocs/teanekoagent/component/memory/adapter/EasyDataLongTermMemoryStore.java#L517) 的 529 行实际抛出“长期原话不支持当前主体或来源已失效”。此前已经检查候选片段属于本批，并找到对应 evidenceId；这一行进一步拒绝原话与主体、片段或当前持久状态不匹配的候选。

| 同一异常分支中的条件 | 可触发的情况 | 建议安全原因码（尚未实现） |
| --- | --- | --- |
| 原短期消息不存在 | 领取后证据被回收或来源不再存在 | EVIDENCE_MISSING |
| 当前消息与批次快照不相等 | 消息内容、来源或其他字段发生变化 | EVIDENCE_CHANGED |
| 原消息已超过有效期 | 领取时有效，模型处理至提交前越过保留期限 | EVIDENCE_EXPIRED |
| 原消息不是 USER | 来源角色不符合真实用户证据合同 | EVIDENCE_ROLE_INVALID |
| 引用不是原消息的逐字连续片段 | 原文“我喜欢茶”，引用变成“我喜欢喝茶”；改标点、改写或拼接句子也可能失败 | QUOTE_MISMATCH |
| 非 EPISODE 候选主体不是证据作者 | 引用用户 A 的原话，却将 subjectId 指向用户 B | SUBJECT_MISMATCH |
| 证据序号不在候选选择的片段范围内 | 证据来自 S1，候选只选了同批的 S2；UUID 都真实仍然不合格 | SEGMENT_MISMATCH |

以上是源码可确认的触发集合，不是对该批数据的逐项复现。当前没有该批候选和原证据，不能断言是模型改写、主体错误还是证据过期，更不能将此日志归因为 HTTP、数据库连接或模型配置失败。运行产物若与当前源码不同，实施前先核对对应版本的行号。

#### 调用链与实际影响

1. [LLMLongTermMemoryConsolidator.decode:184](../../../src/main/java/org/zexnocs/teanekoagent/component/memory/layered/LLMLongTermMemoryConsolidator.java#L184) 要求模型 SUCCESS，再解析 JSON、字段及候选结构；但没有在此阶段核对候选与固定批次的证据关系。在默认生成路径中，走到本次 validate 说明已经通过结构解析，不表示生成内容真实可靠。
2. [commit:234](../../../src/main/java/org/zexnocs/teanekoagent/component/memory/adapter/EasyDataLongTermMemoryStore.java#L234) 验证事实候选，245 行的人格观察也复用同一个 validate。只凭“Agent 长期记忆”标签无法判断出错的是 memories 还是 styles。任一校验失败都会阻止该事务提交事实、风格、索引和整理水位，不应改为无提示的部分提交。
3. [数据库任务包装:74](../../../src/main/java/org/zexnocs/teanekocore/database/base/stage/DatabaseRetryHandlingTaskStage.java#L74) 将这类异常包装成 TaskNoRetryRuntimeException，表示数据库任务层不立即重试原事务，不等于上层停止整理。
4. [LayeredMemoryProcessor.reportFailure:387](../../../src/main/java/org/zexnocs/teanekoagent/component/memory/layered/LayeredMemoryProcessor.java#L387) 未找到专用 MemoryProcessingException 时使用 PROCESSING_FAILED；[FailureDiagnostic:70](../../../src/main/java/org/zexnocs/teanekocore/logger/FailureDiagnostic.java#L70) 只接收显式安全原因，不读取任意 Throwable 消息。普通 IllegalArgumentException 没有提供原因接口，故日志退化为“未识别异常”。模型／准入状态“未取得”是本处没有携带结果，不证明此前未调用模型或未通过准入。
5. [retry:262](../../../src/main/java/org/zexnocs/teanekoagent/component/memory/adapter/EasyDataLongTermMemoryStore.java#L262) 保留 through 并持久化退避，约 30 秒、60 秒、120 秒起递增，单次间隔最多一小时。次数字段封顶不等于存在终止重试次数；后续会重新领取输入并生成，而不是简单重复提交同一个候选对象。若同一前缀持续产生错误，会消耗模型调用并拖延后续整理；来源、资格或保留期变化可能改变结果，不能据此宣称永久死锁。

#### 建议实现范围与顺序

| 顺序／位置 | 修改要求 | 必须保留的边界 |
| --- | --- | --- |
| 1. EasyDataLongTermMemoryStore.validate/commit；MemoryProcessingException | 拆分上表条件，使用领域异常或现有安全原因合同；带出候选域 memories/styles、候选序号、请求／批次标识及必要的脱敏证据定位信息。区分前置的证据 ID 无效、片段不属于本批与本节的关联失败 | 不把所有 IllegalArgumentException 全局映射成证据错误；不记录聊天原文、候选正文、完整模型响应或任意异常消息 |
| 2. LayeredMemoryProcessor.reportFailure | 展示稳定 reasonCode 和对应固定中文说明；已有模型状态仅在实际观察并携带时报告，未知仍标未知。可通过 IFailureReason 传播，Core 不依赖 Agent 类型做分派 | 不为消除“未取得”硬填 SUCCESS；保留异常链、阶段和请求关联，不在多层重复记录相同失败 |
| 3. LLMLongTermMemoryConsolidator.analyze/decode 与领域校验入口 | 解析后按固定 batch 预校验引用、作者及片段关系，向模型明确每条 quote 必须落在所选 segments 范围内。提供清晰的证据—作者—片段映射，减少模型自行关联 UUID；纯关系校验复用一份实现 | 不盲目替换 subjectId 或改写引用来通过验证；事务内仍重新检查权威原文、有效期、generation、租约和输入指纹，不以预校验代替最终校验 |
| 4. LayeredMemoryProcessor 与持久作业状态 | 区分模型候选关联错误、来源失效、租约变化、撤权和可恢复基础设施故障。若采用模型纠错，仅在原控制、时限和剩余调用预算内做有限次数，并给出结构化错误反馈；来源失效走重新领取有效输入。为同一输入指纹持续失败保留可观察状态及受控重放入口 | 不在数据库事务内调用模型，不另开无限重试或额外预算；达到明确失败阈值后不静默跳过该批，不伪造成功水位。新增持久状态须说明旧作业兼容、解除条件及重启行为 |

最小优先补丁是前两项诊断分类和第三项证据预校验；第四项需要明确有界次数、持久状态与恢复入口后实施，不能只加一句“失败再试”。重试策略改变须同步[分层记忆合同](../../../src/main/java/org/zexnocs/teanekoagent/component/memory/layered/README.md)，不把尚未实现的恢复写成当前行为。

修复不得将错误候选删掉或替换为空列表后推进水位，也不得删除逐字引用、作者归属、有效期或片段范围校验。空候选只有在生成结果本来合法且当前仍获准时才可提交，须与 G01 的合法空批次修复保持区分。延长超时、重启或清空记忆不是证据错配的通用修复；本项不授权任何数据清理。

#### 实现验收清单（待执行）

复用 [LongTermMemoryIntegrationTest:260](../../../src/test/java/org/zexnocs/teanekoagent/component/memory/namespace/LongTermMemoryIntegrationTest.java#L260) 的真实事务夹具及[生成器测试:323](../../../src/test/java/org/zexnocs/teanekoagent/component/memory/namespace/LongTermMemoryIntegrationTest.java#L323)。既有用例已检查伪造主体和引用被拒，但不能代替下列诊断与恢复验收。

- [ ] 正确作者、逐字引用和片段范围可以提交，长期条目、索引和 through 同事务生效；覆盖事实及人格观察路径。
- [ ] 原话改写、主体错配、同批错误片段分别返回对应原因；不只断言抛出了某个异常。
- [ ] 领取后来源缺失、变化或过期能与模型关联错误区分。使用固定时钟／可控事务队列，不使用 sleep 轮询；即使预校验通过，事务内仍能拒绝迟到失效的证据。
- [ ] 同批一个合法、一个非法候选时，不提交任何事实／风格及索引，不推进水位；允许按真实结果另行保存退避或失败状态。
- [ ] 经 CompletionException、TaskNoRetryRuntimeException 包装后仍保留准确安全原因、候选域和序号；日志不包含测试中的私密原文或模型正文。
- [ ] 若实现有限纠错，错误反馈能用于下一次生成，且共享原预算和时限；真正撤权、取消或租约变化后不得继续生成或提交。
- [ ] 相同输入连续失败达到已约定阈值后不再无上限后台调用；重启保留状态，授权重放或有效输入变化可按明确合同恢复，旧租约不能提交。
- [ ] 修正后的候选能够恢复提交；G01 合法空批次仍可推进，而 D01 的失败候选不会被伪装为空成功。必要时同步作业状态迁移和文档。

追加诊断仅依据用户日志与源码，未读取该请求的真实数据库内容、未重放原模型请求、未新增或执行上述测试；不能将清单勾选为通过，也不能指定某个证据分支为这一次的已确认根因。

## 三、原 F01–F11 逐项复评

“原问题可关闭”指源码中的原触发条件已消除，并有相应补丁测试依据，不表示本轮重跑了所有原回归或覆盖全部组合。

| 编号／原问题 | 补丁与核对依据 | 复评状态 |
| --- | --- | --- |
| F01／事务排队后不执行资格守卫 | [长期存储:221](../../../src/main/java/org/zexnocs/teanekoagent/component/memory/adapter/EasyDataLongTermMemoryStore.java#L221)、252 在事务入口及返回前执行 guard，Processor 分域复核；新增可控事务队列撤权测试 | 原 P1 可关闭；另处理 G01 空批次回归 |
| F02／人格学习隐藏依赖记忆输入 | [PersonalityLearningConfig:75](../../../src/main/java/org/zexnocs/teanekoagent/component/personality/configuration/PersonalityLearningConfig.java#L75) 按 scope 的实际引用、观察、分层及历史/摘要开关选择逐轮学习，服务不再强制 memoryReference | 原问题可关闭；无记忆使用逐轮学习，不等同于独立持久观察样本通路 |
| F03／expression 独立装配缺原生表情资料 | [ExpressionFeature](../../../src/main/java/org/zexnocs/teanekoagent/component/expression/ExpressionFeature.java) 贡献独立原生目录；[NativeFaceService](../../../src/main/java/org/zexnocs/teanekoagent/component/expression/NativeFaceService.java) 处理资料、版本及发送许可，FaceReplyHandler 使用它 | 原问题可关闭；真实 Feature 选择测试覆盖 expression、加 tool、加 sticker 三种组合 |
| F04／插件任务动作被 remind 策略拒绝 | [AgentScheduleConfig:84](../../../src/main/java/org/zexnocs/teanekoagent/component/schedule/configuration/AgentScheduleConfig.java#L84) 组合公共身份检查与每动作 Policy；读取/取消不要求动作插件继续启用 | 原问题可关闭；默认工厂用例覆盖自定义动作、插件停用和重名 |
| F05／Skill 依赖复核早于最终过滤 | [AgentTurnPipeline:344](../../../src/main/java/org/zexnocs/teanekoagent/runtime/AgentTurnPipeline.java#L344) 在最终集合调用 verifySelection，CombinedTools 转交实际来源，AgentSkills 检查 loader/依赖 | 原问题可关闭；白名单删除依赖或 loader 均有拒绝用例，未自动扩权补工具 |
| F06／单字查询被当成空查询 | [长期检索:290](../../../src/main/java/org/zexnocs/teanekoagent/component/memory/adapter/EasyDataLongTermMemoryStore.java#L290)、307 对非空无索引词使用规范化字面过滤 | 原问题可关闭；仍是有界筛选，保留 limited 语义，不是全库语义检索 |
| F07／重扫暴露空或半成品目录 | [LLMModelService:373](../../../src/main/java/org/zexnocs/teanekoagent/component/llm/framework/model/LLMModelService.java#L373) 完整校验后一次发布不可变 Map，单次查询固定快照；旧 LLMToolService 同步处理 | 原问题可关闭；发现阻塞及冲突失败时保留旧目录的测试已补 |
| F08／主动候选固定偏序饥饿 | [ProactiveCoordinator:166](../../../src/main/java/org/zexnocs/teanekoagent/component/proactive/adapter/ProactiveCoordinator.java#L166) 每批轮换起点；BUSY 不消耗整轮到期机会，区别于已尝试/弃言 | 原问题可关闭；四目标双槽、受控阶段测试覆盖原条件 |
| F09／墓碑挤占缓存名额 | 新增独立 cacheSize 与管理计数，不再把已回收墓碑算入 2048 条缓存 | 部分修复；整行 JSON 持久容量仍需处理，见第二节 |
| F10／无插件目录的空事件配置失败 | [StickerEvents:83](../../../src/main/java/org/zexnocs/teanekoagent/component/sticker/StickerEvents.java#L83) 空 names 直接返回空集合 | 原问题可关闭；有总线、无目录、空名单用例已补 |
| F11／每轮发现类声明 | 类 requires、方法/参数和代理调用目录改为初始化编译，未初始化明确拒绝 | 原高频发现已修；因 G02 代理隔离回归，整项待收尾 |

关键补丁测试入口：[LongTermMemoryIntegrationTest](../../../src/test/java/org/zexnocs/teanekoagent/component/memory/namespace/LongTermMemoryIntegrationTest.java)、[PersonalityIntegrationTest](../../../src/test/java/org/zexnocs/teanekoagent/component/memory/namespace/PersonalityIntegrationTest.java)、[MediaReplyIntegrationTest](../../../src/test/java/org/zexnocs/teanekoagent/component/memory/namespace/MediaReplyIntegrationTest.java)、[AgentComponentReliabilityTest](../../../src/test/java/org/zexnocs/teanekoagent/component/memory/namespace/AgentComponentReliabilityTest.java)、[AgentPromptIntegrationTest](../../../src/test/java/org/zexnocs/teanekoagent/runtime/prompt/AgentPromptIntegrationTest.java)、[LLMModelServiceRegistrationTest](../../../src/test/java/org/zexnocs/teanekoagent/component/llm/framework/model/LLMModelServiceRegistrationTest.java)。它们属于补丁已有资产，本轮没有修改这些文件。

## 四、R01–R04 边界复评

| 编号 | 当前实现与判断 | 仍需验证或保留的限制 |
| --- | --- | --- |
| R01／主动活动与 namespace 耦合 | [ProactiveService:181](../../../src/main/java/org/zexnocs/teanekoagent/component/proactive/adapter/ProactiveService.java#L181) 仅安装 memory 时解析/复核引用；PROACTIVE_ENTRY 允许无记忆引用，仍保留持久防重身份。原主路径耦合已解除 | 新测试证明无引用的 Attempt 可跨存储重建保留身份，但没有覆盖完整无 memory 的主动生成、发送和重启流程；建议补组合验收，不列为已证实的新缺陷 |
| R02／扫描历史与串行慢动作 | [EasyDataScheduleStore:201](../../../src/main/java/org/zexnocs/teanekoagent/component/schedule/EasyDataScheduleStore.java#L201) 使用到期索引；状态同事务维护索引，启动恢复可补旧记录；[AgentScheduleService](../../../src/main/java/org/zexnocs/teanekoagent/component/schedule/AgentScheduleService.java) 分离发现与四槽执行 | 原全历史扫描和逐任务串行等待已处理；四槽仍可饱和，迟到超过 30 秒不补跑。真实数据库规模、耗时与准入容量仍需验收，不能写成不限量异步 |
| R03／原生表情依赖图库事务 | NativeFaceService 不依赖图库存储，expression 只用原生表情不再借图库 WRITE 事务 | 原生部分可关闭；sticker 的选图、冷却、预约和读占用仍锁整行目录并完整编码，吞吐未测。保留原子仲裁，按测得竞争再决定拆分 |
| R04／异常回执误记送达 | [NativeScheduleAdapter:119](../../../src/main/java/org/zexnocs/teanekoagent/component/schedule/adapter/NativeScheduleAdapter.java#L119) 使用 AgentReplySender.confirm，要求有效消息 ID | 原问题可关闭；缺消息 ID 的测试确认进入 INDETERMINATE、不自动重发；真实平台回执仍属实机验收 |

调度证据见 [AgentScheduleIntegrationTest:156](../../../src/test/java/org/zexnocs/teanekoagent/component/memory/namespace/AgentScheduleIntegrationTest.java#L156) 及发现失败恢复用例；回执证据见 [NativeScheduleIntegrationTest:176](../../../src/test/java/org/zexnocs/teanekoagent/component/memory/namespace/NativeScheduleIntegrationTest.java#L176)。实机事项继续使用[部署验收清单](../../development/agent-deployment.md#实机验收清单)，不改写历史计划的原始验收记录。

## 五、组件作用及架构评价更新

本轮沿用首次审查的九类组件与共享装配范围，重点复查补丁触及的功能闭环；没有把一次定向复评表述为全仓逐行无缺陷证明。

| 范围 | 当前作用与复评 |
| --- | --- |
| 共享装配 | 组件到 Feature/System、插件归属和执行前后守卫仍有真实接入；最终工具复核新增可替换扩展点 |
| llm | 模型目录原子发布已改善并发可靠性；协议、预算和模型/工具循环不是空服务 |
| memory | 持久化、证据、租约、明确保存/纠正/遗忘有效；G01 是后台进度缺陷，不能据此否定整个记忆层 |
| personality | 基础人格和补正有效，独立逐轮学习恢复；联合持久观察仍需处理 G01，两个通路的语义不可混写 |
| tool | 注解工具主链真实执行；预编译方向正确，G02 表明扩展身份隔离仍需修正 |
| skill | 最终依赖复核已接入，不再仅凭过滤前的目录承诺能力 |
| expression | 原生目录与图库已拆开；回复编译、顺序、真实回执和逐段交付仍有效 |
| sticker | 采集、分析、管理、发送与占用有实际作用；F09 长期存储容量和 R03 整行锁是剩余边界 |
| schedule | 插件动作策略能通过默认装配，到期索引和四槽执行发挥作用；需要真实规模容量数据 |
| proactive | 公平性及无记忆身份改造已接入；无可用文本时弃言是既定合同，不是服务假执行 |

| 评价方向 | 第一补丁后的结论 | 下一步 |
| --- | --- | --- |
| 可扩展性：尽量只新增类/注解 | 模型、注解工具、回复处理器和管理端口继续可扩展；插件任务动作现在可通过新增 Bean 与自身 Policy 接入，无需修改中央 remind 分支 | 优先修 G02，确保新增同接口代理提供者不会覆盖旧扩展；不要为了统一外观再建一套注册框架 |
| 可维护性 | 原生表情脱离图库事务，人格学习按实际输入选路，Skill 依赖检查放到最终集合，职责和合同更清晰 | 用明确状态区分空结果/撤权，用独立存储表达永久拒绝决定；补组合与边界测试而非重复机械断言 |
| 异步执行可靠性 | F01 真实事务内守卫、F07 快照、F08 公平调度及 R02 发现/执行分离都是有效改进 | 修 G01 的活性问题；真实测量调度槽位和图库行锁容量。返回 CompletionStage 不代表无限并发或业务已经送达 |

“看似有用但未提供完整能力”的项目仍应按合同区分：

- 旧 LLMToolService 已在框架文档标为兼容/独立宿主 API，不是当前 Agent 注解注册入口；不能称其替代了主工具链，也不据此擅自删除兼容入口。
- 文件、代码、知识源默认后端仍只是明确的端口合同，宿主未配置后端时不等于具备执行能力；建议管理状态继续明确显示未配置。
- 原生目录、人格独立学习、插件动作这三项的原“声明可用但链路不完整”问题已经改善，不继续沿用旧评价。
- 主动活动 token 说明、交互预算和框架自动循环说明已随补丁同步；本轮不再把旧文档偏差列为当前未修问题。

兼容要求没有因本轮审查取消：AgentTaskAction 旧三参构造仍存在，但默认拒绝创建/执行，插件须提供窄 Policy；独立宿主须在初始化时 prepare 工具，G02 应在保留这一低频编译合同的前提下修复。

## 六、验证范围与实际结果

| 验证来源 | 实际结果 | 解释 |
| --- | --- | --- |
| 补丁已有回归 XML | 101 份报告，918 项、0 失败、0 错误、1 跳过，即 917 通过 | 本轮读取并核对 `build/test-results/test`，没有重跑这 918 项；不将其当作新增边界都已覆盖的证据 |
| 本轮 G01 诊断 | 失败：预期提交计数 1，实际 0 | 用模拟存储执行真实 guard，复现合法空观察批次无法推进 |
| 本轮 G02 诊断 | 失败：预期 audit_alpha，实际 audit_beta | 用真实 Spring JDK 代理复现缓存声明串用 |
| 本轮 F09 诊断 | 通过：准确观察到容量拒绝 | 用现有事务夹具和真实 Catalog 确认剩余限制；不表示修复通过 |
| 已执行隔离诊断合计 | 3 项，2 失败、1 通过 | Gradle 任务因两个业务断言失败退出，不是依赖下载或构建环境错误 |
| D01 追加日志诊断 | 日志位置与源码校验分支对应；具体条件未确定 | 仅追加文档及源码核对，未取得原批次数据、未新增或运行测试，不计入上述三项 |
| 外部验收 | 未执行 | 未调用真实 MySQL、模型、平台，也未重新启动、打包或发布 |

诊断使用 Java 25，独立 sourceSet/test task 复用现有项目依赖和夹具，不覆盖原回归结果：

```powershell
.\gradlew.bat --offline -I temp/verification/4.8.3-l/review-1/review.init.gradle componentPatchReview --console=plain
```

源码与结果：

- [G01/G02 隔离用例](../../../temp/verification/4.8.3-l/review-1/src/org/zexnocs/teanekoagent/review/ComponentPatchReviewTest.java)。
- [F09 容量诊断](../../../temp/verification/4.8.3-l/review-1/src/org/zexnocs/teanekoagent/component/memory/namespace/TombstoneCapacityReviewTest.java)。
- [独立任务配置](../../../temp/verification/4.8.3-l/review-1/review.init.gradle)、[修复前运行日志](../../../temp/verification/4.8.3-l/review-1/run.log)。原三个诊断的结果以上表及该日志为准。
- 2026/11/06 整理说明：同一独立任务随后只重跑 G01/G02，生成目录已更新为[修复后报告](../../../temp/verification/4.8.3-l/review-1/report/index.html)及[两项结果 XML](../../../temp/verification/4.8.3-l/review-1/results/TEST-org.zexnocs.teanekoagent.review.ComponentPatchReviewTest.xml)；原 F09 XML 不再存在，不将新报告当作旧结果。F09 修复使用正式容量回归验收，见第八节。

临时诊断不属于生产源码；后续修复时应将必要回归并入原测试夹具。文档检查包括本地引用、源码行号及差异空白检查，不为本次报告再运行打包。

## 七、建议修改顺序

1. **先修 G01、G02**：恢复合法空批次的进度，隔离同接口 JDK 代理的工具声明。先使两个已失败诊断通过，再补真实队列及实际工具调用的组合验证。
2. **补 D01 证据诊断与预校验**：先区分真实失败分支，再处理有界恢复与持续失败状态；不降低证据强度，不与 G01 的合法空批次混淆。
3. **完成 F09 持久存储收尾**：独立拒绝决定的结构、迁移与管理容量必须一起接入生产路径；不自动遗忘人工删除。
4. **补 R01 组合验证和 R02/R03 实机容量验收**：无记忆主动活动全流程、慢动作与四槽饱和、图库行锁竞争及增长后的容量状态。
5. **保留已生效的修正**：事务内双守卫、动态资格检查、最终 Skill 依赖校验、目录原子发布和有效消息 ID，均不应为修新回归而撤销。

整体历史基线另见[可扩展性、可维护性与异步执行评估](../4.8.1-l/project-extensibility-maintainability-async-review.md)；本报告只更新上述组件补丁的结论，不据此关闭该文中未纳入第一补丁的其他架构事项。

## 八、2026/11/06 修复验收

本轮按 G01 → G02 → D01 → F09 顺序实施，版本仍为 `4.8.3-l`，未提交或发布。原诊断的“2 失败、1 通过”是修复前证据；本节记录修复后的实际结果。

| 项目 | 已接入的修正 | 验证入口与边界 |
| --- | --- | --- |
| G01 | 空样本与撤权分开判断；合法空批次推进，事务前后守卫保留 | StyleObservationIntegrationTest：换人格、无字段、证据到期、后续有效样本及排队后撤权 |
| G02 / F11 | 真实声明类型预编译，实际 Bean 弱身份绑定；不再以共享代理 class 区分提供者 | TeaNekoAgentToolTest：同接口 JDK 代理、互换准备顺序、schema/client/requires 和真实方法执行隔离；原两项诊断均通过 |
| D01 | 共用证据关系预检；事务回查权威来源并计入排队耗时；安全原因带候选域、序号和证据摘要 | LongTermMemoryIntegrationTest / StyleObservationIntegrationTest：准确原因、包装穿透、事实与风格混合回滚、来源变化/缺失/到期、合法提交 |
| D01 恢复 | 同输入三次无效后持久暂停，工作区显示状态并按版本恢复；旧作业缺失版本按零读取 | 重建存储仍暂停，撤权/错版本拒绝、回执幂等、输入变化恢复、旧租约拒绝及实际管理服务接线均通过；未增加模型纠错调用 |
| F09 | 回收后的删除决定按指纹分行，公开 ID 独立索引；迁移、恢复、计数和主状态同根事务 | StickerDeletionStorageTest：12,000 条合法删除项超过旧 8Mi 字符容量后仍可存储，主行小于 1024 字符；缺省字段迁移、失败回滚、稀疏及混合分页、双宿主竞争均通过 |
| F09 生产链 | 采集在视觉分析前复核独立删除决定；管理读取/恢复接入真实索引，返回字符容量及接近上限状态 | StickerCollectionIntegrationTest / StickerManagementIntegrationTest：重复图仍拒绝，版本、权限、回执和恢复语义保留 |
| R01 | 无 memory 的主动活动可按当前文本生成、取得有效平台消息 ID 并持久防重 | ProactiveWithoutMemoryTest：真实宿主、目标发送和数据库入口，模型/平台网络替身；重建不重发，空文本弃言，不访问历史或记忆引用 |
| R02 / R03 | 补充四槽饱和后继续调度、图库根锁竞争下状态与计数不丢失 | 离线原子性通过；真实 MySQL 耗时、吞吐和平台回执仍在部署验收清单，不宣称容量无限 |

### 实际验证

- 广泛离线回归：106 个测试类、979 项，978 通过、1 项真实模型测试按配置跳过，0 失败、0 错误。日志：[最终回归](../../../temp/verification/4.8.3-l/review-followup-regression-final.log)。
- 前端 `vue-tsc` 与生产构建通过；保留现有单块体积提示。`bootJar` 成功，核对 74 个修改主类型、180 个 class 与编译目录一致，版本、素材模板及 Web 入口正确，无已删除工具后处理器残留。日志：[构建](../../../temp/verification/4.8.3-l/review-followup-build.log)、[产物核对](../../../temp/verification/4.8.3-l/review-followup-artifact.log)。
- 100 个修改/新增 Java 文件的 JavaDoc 标记及引用检查通过；`git diff --check` 通过。日志：[JavaDoc](../../../temp/javadoc/4.8.3-l/review-followup-javadoc-final.log)。
- 首轮扩展分页测试耗尽离线夹具测试堆，已补齐异步失败回传和有界等待，并分开容量与分页用例；保留 12,000 条容量验证，不调大生产限制。中间失败不计为通过，最终整组回归已重新执行。

### 升级及未执行项

无须改表，但图库首次访问会自动迁移旧删除决定。先备份数据库和素材卷，停止旧实例后统一升级；旧版本不能读取新拒绝索引，不可混跑或直接降级。回退使用配套版本的数据备份，详见[图库迁移说明](../../../src/main/java/org/zexnocs/teanekoagent/component/sticker/README.md#删除记录迁移与回退)。

长期暂停的原因与恢复合同见[分层记忆说明](../../../src/main/java/org/zexnocs/teanekoagent/component/memory/layered/README.md#长期整理失败与恢复)。未连接真实数据库、模型或客户端，未启动应用；首次迁移耗时、四槽容量及图库行锁吞吐继续按[部署验收清单](../../development/agent-deployment.md#实机验收清单)执行。约定离线范围已完成，见[实施归档](../../plan/old/4.8.3-component-review-followup.md)。

## 九、2026/11/06 更新后独立复评

本轮核对 G01、G02、D01、F09 的修正及领取失败退避、R01–R03 的相关补充，沿真实入口检查源码并重跑定向回归。没有修改业务实现或既有测试；新增诊断只放在 `temp/verification/4.8.3-l/review-2/`。本节不是全仓再次逐行审计，也不替代其他进行中计划的验收。

### 9.1 当前状态与修复有效性

| 项目 | 本轮核实的实际行为 | 当前结论与剩余边界 |
| --- | --- | --- |
| G01／合法空观察批次 | `LayeredMemoryProcessor.processLong` 将观察开关与合格目标分开判断；无事实学习、无可用观察目标时不调用模型，允许仍获准的空批次提交。事务入口及提交前守卫保留 | 原问题可关闭。换人格、无字段、证据过期、后续有效样本及排队后真正撤权的回归通过 |
| G02／共享 JDK 代理类串用声明 | `METHODS` 按真实声明类编译，`PROVIDERS` 按 Bean 弱身份绑定，不以代理 class 区分提供者，也不调用代理的 equals/hashCode | 原问题可关闭。同接口多代理、准备顺序互换、schema/client/requires 与实际方法调用隔离的回归通过；工具整体仍需处理新的 G03 |
| D01／原话校验与原因定位 | `LongMemoryEvidence` 供生成端与事务共用关系校验；`MemoryEvidenceException` 提供固定原因、候选域/序号、批次及证据摘要。提交重新读取权威来源，并计入事务排队耗时，混合候选失败不部分落库 | 诊断与校验缺口可关闭。不能据此倒推旧日志那一次究竟是引用改写、主体错配还是来源失效；也不保证模型以后不会生成坏候选 |
| D01／持续失败与恢复 | 同一输入累计三次无效生成后持久暂停；来源失效、撤权、租约变化和基础设施失败不计该阈值。工作区通过真实管理服务展示状态，按版本、权限、空间代次和幂等回执恢复；旧租约不能提交 | 有界恢复已接通，重建、输入变化、旧记录缺 revision 及管理接线回归通过。没有新增模型自动纠错调用，README 已明确这一边界 |
| 领取前失败退避 | `readOrDefer` 区分读取失败与合法空结果；长期领取失败仍检查中期任务，并隔离其他分区。目录及基础设施失败也限频；`MEMORY_STORAGE_FORMAT` 不输出原 JSON，不用默认值覆盖损坏数据 | 相关 6 项扫描回归通过。该退避为进程内状态，最多跟踪 1024 个目标，30 秒起、最长一小时，重启重置；不是 D01 的持久暂停，也不是修复坏记录 |
| F09／删除墓碑持久容量 | `EasyDataStickerCatalog` 将已回收且无在途占用的删除决定与公开 ID 索引分行保存；迁移、计数、恢复仍在同一根事务。采集在视觉分析前查询拒绝决定，管理分页/详情/恢复已接入 | 原容量问题可关闭。12,000 条合法墓碑、回滚、缺省字段迁移、分页及竞争回归通过；活动目录仍受缓存和字符上限约束，不代表存储容量无限 |
| R01／无记忆主动活动 | 新组合测试经过真实宿主、目标发送及存储入口，用网络替身验证有效平台消息 ID、重建防重和空文本弃言，不读取历史或记忆引用 | 上轮指出的组合测试缺口已补，不再写成“仅证明 Attempt 可持久化” |
| R02／调度；R03／图库竞争 | 四槽饱和后继续调度及双宿主根锁下状态、计数仲裁已有离线覆盖；没有为通过测试删除锁或放宽容量 | 离线功能与原子性改善有效；真实 MySQL 吞吐、首次迁移耗时、平台回执仍待实机验收 |

主要核对入口：[长期处理器](../../../src/main/java/org/zexnocs/teanekoagent/component/memory/layered/LayeredMemoryProcessor.java)、[生成器预校验](../../../src/main/java/org/zexnocs/teanekoagent/component/memory/layered/LLMLongTermMemoryConsolidator.java)、[长期事务存储](../../../src/main/java/org/zexnocs/teanekoagent/component/memory/adapter/EasyDataLongTermMemoryStore.java)、[管理服务](../../../src/main/java/org/zexnocs/teanekoagent/integration/web/AgentLayersConsoleService.java)、[工作区界面](../../../frontend/src/AgentWorkspaceView.vue)、[图库事务](../../../src/main/java/org/zexnocs/teanekoagent/component/sticker/EasyDataStickerCatalog.java)、[采集拒绝检查](../../../src/main/java/org/zexnocs/teanekoagent/component/sticker/StickerLibrary.java)。

### 9.2 新确认 G03：共享继承方法的代理调用目录未按提供者隔离

| 编号 | 级别 | 触发与影响 | 修改建议 |
| --- | --- | --- | --- |
| G03（F11 后续） | P2 | 两个提供者继承同一条件方法，却通过不同 JDK 接口代理公开它。后初始化者的接口未进入调用目录，`select` 阶段直接抛出“工具代理尚未初始化可调用方法目录。”，可阻断本轮工具准备 | 将代理可调用目录绑定到真实提供者声明类型与 Method，或编入各自不可变规格/句柄；保留初始化编译、Bean 身份隔离及通过真实代理执行 |

确认根因：

1. [TeaNekoAgentToolMethods.java:62](../../../src/main/java/org/zexnocs/teanekoagent/component/tool/TeaNekoAgentToolMethods.java#L62) 的 `INVOCABLE` 仍只有 `Method` 这一维度。它与已修复的 `PROVIDERS` 身份映射不是同一个缓存。
2. [bind:102](../../../src/main/java/org/zexnocs/teanekoagent/component/tool/TeaNekoAgentToolMethods.java#L102) 按当前提供者取得接口，却在 106 行用 `computeIfAbsent(method, ...)` 保存结果。两个子类未覆写同一父类方法时取得同一个声明 `Method`；第二个提供者不会再加入自己的接口方法。
3. [invoke:406](../../../src/main/java/org/zexnocs/teanekoagent/component/tool/TeaNekoAgentToolMethods.java#L406) 只在缓存候选中选择声明类能够接收当前 Bean 的方法。第二个 JDK 代理既不是父类实例，也不实现第一个接口，因此 411 行抛异常。
4. [allowed:430](../../../src/main/java/org/zexnocs/teanekoagent/component/tool/TeaNekoAgentToolMethods.java#L430) 在选择与执行时复用此调用入口。即使[自动注册表:157](../../../src/main/java/org/zexnocs/teanekoagent/component/tool/TeaNekoAgentToolRegistry.java#L157) 已向 `select` 传入正确的 `provider.type()`，也不能消除这层缓存冲突。因此它不只涉及 G02 原来的独立宿主入口。

隔离复现使用两个工具类，各自具有唯一类标识和工具名 `inherited_alpha`、`inherited_beta`，分别实现 `AlphaContract`、`BetaContract`。二者共同继承父类的 `available(TeaNekoAgentToolContext)`，两个接口也各自声明这个条件方法；条件本身固定返回 true，不涉及权限撤销。真实 Spring JDK 代理依次 `prepare(alpha)`、`prepare(beta)` 后，选择 alpha 成功，选择 beta 在上述 411 行失败。故这不是工具重名、遗漏初始化或代理未公开条件方法导致。

本轮直接复现的是继承的条件方法；相同缓存也供业务方法调用使用，但尚未把继承业务方法的变体作为已执行结果。原 G02 的同接口代理声明覆盖已经修好，不应将其重新标为未修。

建议实现与验收：

- 初始化时为每个真实声明完整编译工具方法和条件方法的可调用候选，再原子发布；避免把提供者相关信息继续放入仅以共享父类 Method 为键的全局目录。
- 选择和执行只读取已编译规格，继续穿过实际 Spring 代理与动态资格检查。不要通过每轮发现接口、解包 target、改用 CGLIB 或要求业务类重复覆写公共条件来掩盖缺陷。
- 在现有 `TeaNekoAgentToolTest` 补入不同接口、共享继承条件的用例，互换准备顺序，并验证条件及业务调用经过实际代理 advice；同时覆盖共享继承业务方法的合法声明。
- 保留原 G02 的同接口多代理隔离用例，确保修复调用目录不会重新混淆 schema、client、requires 或 Bean 身份。

### 9.3 对原长期记忆日志的更新判断

原 `validate:529` 日志仍只能证明旧版本的证据校验拒绝了候选；没有当时的候选和权威证据，不能补认某一个具体分支。新增代码修复的是今后的定位与恢复能力，不是证明旧批次已经处理成功。

更新后若再遇到失败，应先按固定原因区分处理：

| 新日志或状态 | 处理方向 |
| --- | --- |
| `QUOTE_MISMATCH`、`SUBJECT_MISMATCH`、`SEGMENT_MISMATCH` 等候选关系错误 | 检查生成与证据映射合同；无效次数达到阈值后暂停。修正原因后使用有权限、带版本的恢复入口，不删掉坏候选伪造成功 |
| `EVIDENCE_MISSING`、`EVIDENCE_CHANGED`、`EVIDENCE_EXPIRED` 等来源失效 | 重新领取当前有效输入；不归咎于模型凭据或 HTTP，也不放宽逐字证据、作者归属和有效期 |
| `MEMORY_STORAGE_FORMAT` | 核对部署版本、受控记录类型和字段；仅缺 revision 的旧格式已有兼容，其他损坏保持原样。工作区“恢复整理”不是 JSON 修复器 |
| `PAUSED` | 表示后台已停止对相同输入继续无效生成，不是服务失效或整理完成。授权恢复或固定输入指纹变化可解除；只追加前缀之外的新消息未必改变当前输入 |

操作与完整原因表以[分层记忆失败恢复说明](../../../src/main/java/org/zexnocs/teanekoagent/component/memory/layered/README.md#长期整理失败与恢复)为准。未知模型/准入状态仍应标为未知；不应为消除“未取得”而硬填成功，或记录聊天原文、完整模型响应来换取诊断。

### 9.4 可扩展性、可维护性与异步能力

| 方向 | 本轮评价 | 下一步 |
| --- | --- | --- |
| 只新增类/注解即可扩展 | 真实声明预编译与 Bean 身份隔离已生效；但新增继承公共条件、使用另一接口的合法工具仍可能失败，因此 F11 的扩展可靠性不能整体关闭 | 优先修 G03 的调用目录维度；不要求每个新增业务类覆写父方法，也不引入新的中央类型分支 |
| 可维护性 | 共用证据校验、安全原因码、明确作业状态、管理恢复及删除决定独立持久化均接入生产链，不是仅增加无人调用的服务 | 将 G03 回归纳入正式测试；按模块 README 维护恢复与迁移合同，不复制多套规则 |
| 异步执行可靠性 | G01 的空批次进度、事务内守卫、排队后有效期复核、坏分区退避及调度四槽恢复均有实际效果 | 保留持久操作真正完成后才释放占用的语义；真实测量调度容量、首次迁移和图库根锁吞吐，不把 CompletionStage 等同于无限并发 |

本轮未发现这些新增服务“声明有用但生产入口完全未接入”的问题。固定输入暂停、读取退避和主动活动空文本弃言是明确的受控状态，不能按“没有继续调用模型”认定为无效服务。本次范围内已确认的待修缺陷是 G03；实机容量属于尚待验证项，不将其预先判为缺陷，也不重复列举已修复问题。

### 9.5 本轮实际验证与交付边界

| 验证来源 | 实际结果 | 说明 |
| --- | --- | --- |
| 本轮定向回归 | 11 个测试类、63 项，全部通过，0 失败、0 错误、0 跳过 | 覆盖长期证据与恢复、人格观察、注解工具、删除存储/管理/采集、无记忆主动活动、调度、扫描退避、批次时限和模型预算 |
| 本轮新增 G03 隔离诊断 | 1 项、1 失败 | 预期 beta 工具正常可选，实际抛 IllegalStateException；堆栈为 invoke:411 → allowed:430 → select:207/139 → 诊断:53 |
| 当前已有回归 XML 核对 | `build/test-results/test` 为 45 份、365 项，全通过 | 这是领取退避实施阶段留下的报告，不是本轮重跑 365 项；第八节的 979 项使用其独立历史日志，不混为同一次执行 |
| 构建任务结果 | 本轮 Gradle 以失败退出 | 编译及 63 项回归成功，失败来自 G03 诊断，不是依赖下载或编译故障；未把整体构建写成通过 |
| 文档及外部验收 | 检查本地引用、当前证据行号和差异空白；未重新打包或启动 | 未连接真实 MySQL、模型或平台；不改业务实现、既有测试或归档计划验收记录 |

使用 Java 25 与独立 sourceSet/Test 任务，结果目录不覆盖实施者原回归。实际命令：

```powershell
$env:JAVA_HOME='C:\Users\q9375\.jdks\ms-25.0.2'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat --offline -I temp/verification/4.8.3-l/review-2/review.init.gradle componentSecondReviewDiagnostic componentSecondReviewRegression --continue --console=plain
```

复现与核对入口：[独立任务配置](../../../temp/verification/4.8.3-l/review-2/review.init.gradle)、[G03 诊断源码](../../../temp/verification/4.8.3-l/review-2/src/org/zexnocs/teanekoagent/review/InheritedProxyConditionReviewTest.java)、[运行日志](../../../temp/verification/4.8.3-l/review-2/run.log)、[63 项回归报告](../../../temp/verification/4.8.3-l/review-2/regression-report/index.html)、[失败诊断报告](../../../temp/verification/4.8.3-l/review-2/diagnostic-report/index.html)。

就本次组件复评范围，建议下一批修 G03 并补入正式回归；真实容量按[部署验收清单](../../development/agent-deployment.md#实机验收清单)单独安排。F09 升级仍须备份、停旧实例后统一升级，禁止新旧存储语义混跑或直接降级，见[迁移与回退合同](../../../src/main/java/org/zexnocs/teanekoagent/component/sticker/README.md#删除记录迁移与回退)。本次审查不授权清理真实记忆、删除拒绝决定或发布升级。
