# v4.8.4-l 已完成与验证证据

日期：2026/11/06。本文分别保留审查时观察、随后实施验证和最新独立复核。V484-W01/A01/D02 已完成并复核通过，当前结论见下文；条件设计见 [next-steps.md](next-steps.md)。

## 修正独立复核

2026/11/06 重新核对真实回执处理、组件／客户端声明缓存和页面生命周期，V484-W01、V484-A01、V484-D02 可以保持关闭，未在本轮范围确认新的待修业务缺陷。实际执行 9 类 Java 回归共 39 项、20 个回执隔离场景，以及源码／生产分包各一轮相同的 11 组浏览器交互，全部通过。报告独立写入 `temp/verification/4.8.4-l/review-2/`，未覆盖原失败记录或实施者结果。

完整源码依据、命令、初次隔离脚本环境问题与修正、实际报告及范围见[修正独立复核](reassessment.md)。工作区已进入 4.8.5-l，本次不表示该版本新功能已全部验收；未进行负荷测试或真实后端联调，也未重打 bootJar。

## 页面扩展收尾

- V484-D02：原先导航类型、分组、标题、打开状态与模板分支分别维护在中央组件。现由 [page-registry.ts](../../../frontend/src/pages/page-registry.ts) 统一声明，宿主按需加载；新增页面不改外壳分支。目标选择留在所属页面，未引入远端脚本、Router 或第二套状态库。
- [页面宿主](../../../frontend/src/pages/ConsolePageHost.vue) 保留编辑实例，隐藏时停止轮询，退出前检查所有页面及嵌套草稿。坏回执仍保留原编号；注销失败不释放状态，认证失效与迟到加载不会重建旧会话。
- V484-D01：普通资源继续通过原类型化网关、白名单及精确安全路由；当前没有需要新 HTTP 协议的功能，不增加假设性授权框架。按用户追加要求，核对页面所需全部 13 项管理资源和 9 项文件配置，并为本地配置补齐缺失的 5 项空间资源及 `agent.observability` 文件入口；默认模板已经完整，不自动改写其他部署的显式白名单。
- 产物检查发现 `processResources` 的复制留下旧哈希分包，已改为对生成控制台目录执行有边界的同步。清理只涉及构建输出，可重新构建，不删除源码、配置或数据。
- 验证：前端类型检查和生产构建通过；既有 7 组、追加 11 组真实浏览器交互通过，追加组同时核查源码与生产分包。3 个既有安全／空间测试类共 17 项通过，无失败或错误；不重算上一批 1038 项结果。
- 产物：44 个前端资源在生成目录、启动资源目录及 JAR 中逐项一致，无旧分包；88 个主源码类型对应 172 个 class 与编译目录一致。构建无新增废弃 API 警告，`gradle.properties` 未改动。
- 证据：`temp/verification/4.8.4-l/pages-browser-result.json`、`pages-bundle-result.json`、`pages-security-results/`、`pages-package.log`、`verify-page-assets.ps1`；完整范围见[归档计划](../../plan/old/4.8.4-console-pages.md)，扩展方式见[页面指南](../../development/web-console-pages.md)。未进行真实后端、数据库或平台联调。

## 实施收尾

- V484-W01：请求层分开 JSON/空响应；回执验证对象、字段、允许状态、原操作和目标。坏响应保留原编号，已确认后刷新失败只提示刷新。独立浏览器已验证坏回执恢复入口，源码函数隔离复核也通过。
- V484-A01：组件/客户端静态声明只在初始化与显式重扫发现，完整快照原子发布；查询不重新发现，插件启停及代次实时复核。失败重扫、撤权/重启代次、真实 Spring 装配已有正式回归。
- 最终相关回归：122 类、1038 项，零失败/错误/跳过；七组真实浏览器交互通过（仅 HTTP 接口替身，不访问真实服务）。
- 验证日志与报告：`temp/verification/4.8.4-l/final-build.log`、`final-results/`、`browser-result.json`；完整范围与产物检查见[实施归档](../../plan/old/4.8.4-console-editors-workspace.md)。

以下保留修复前审查证据，不将当时失败改写为通过；也不把后来验证扩大为真实模型或线上验收。

## 已确认的范围

| 范围 | 结论 | 证据／边界 |
| --- | --- | --- |
| G03 继承代理目录隔离 | 独立复评通过，可以关闭；原诊断 1 项和正式回归 65 项通过 | [4.8.3-l 专项复评](../4.8.3-l/g03-reassessment.md)，不重复开修复任务 |
| Agent 组件组合 | 组件通过原 Feature／System／工具／资料链接入；不是独立空壳目录 | [整体评估](architecture-review.md)，本轮按消费链核对，不宣称所有方法已穷尽测试 |
| 工作流、Skill、宿主扩展工具、类型化输出 | 对应离线调用链验证通过；宿主接入条件仍有效 | 下表 121 项回归；不等于默认应用已拥有文件后端、沙箱或自主多 Agent |
| App/Web 既有授权与配置入口 | 指定管理、安全、文件／聊天配置回归通过 | 同一批回归，不扩大为全部接口无缺陷 |
| V484-W01 问题诊断 | 审查时复现，随后已修复 | 当前证据见“实施收尾” |
| V484-A01 源码核对 | 审查时确认，随后已修复 | 静态描述已随目录发布，运行资格继续实时复核 |

## 整体架构定向回归

复用 15 个正式测试类，在单独报告目录执行，未修改正式测试或覆盖 G03 结果：

| 测试类 | 项数 | 结果 |
| --- | --- | --- |
| ProjectLayerBoundaryTest | 2 | 全通过 |
| AgentLayerBoundaryTest | 2 | 全通过 |
| AgentAssemblyTest | 4 | 全通过 |
| AgentPluginIntegrationTest | 5 | 全通过 |
| AgentServiceTest | 58 | 全通过 |
| AgentWorkflowIntegrationTest | 3 | 全通过 |
| LocalSkillCatalogTest | 1 | 全通过 |
| AgentExtensionToolsTest | 2 | 全通过 |
| LLMJsonProtocolTest | 2 | 全通过 |
| LLMJsonServiceTest | 17 | 全通过 |
| AgentWebFailureMapperTest | 1 | 全通过 |
| WebManagementCallsTest | 1 | 全通过 |
| WebConsoleSecurityTest | 13 | 全通过 |
| ChatSettingsServiceTest | 1 | 全通过 |
| FileSettingsIntegrationTest | 9 | 全通过 |
| 合计 | 121 | 0 失败、0 错误、0 跳过 |

JDK 25、项目根目录、离线执行：

```powershell
.\gradlew.bat --offline -I temp/verification/4.8.4-l/review-1/review.init.gradle architectureReviewRegression -x webInstall --console=plain
```

证据：[独立任务配置](../../../temp/verification/4.8.4-l/review-1/review.init.gradle)、[完整日志](../../../temp/verification/4.8.4-l/review-1/regression.log)、[HTML 报告](../../../temp/verification/4.8.4-l/review-1/regression-report/index.html)、[XML 目录](../../../temp/verification/4.8.4-l/review-1/regression-results)。实际 `BUILD SUCCESSFUL`，测试任务执行；其依赖链同时执行了 `compileJava`、`compileTestJava` 和 `webBuild`（vue-tsc、Vite），不是只读取旧报告。Vite 的包体提示不是测试失败。

报告在本轮运行结束时生成，随后工作区继续有 4.8.4-l 并行修改；不能用这 121 项替代那些后续修改的验收。G03 的 66 项属于单独批次，既有组件修复的历史通过数也不并入本表。

## Web 回执隔离诊断

```powershell
node temp/verification/4.8.4-l/review-1/web-receipt-contract.mjs
```

诊断脚本从真实源码编译请求封装，并提取实际 Vue 回执处理函数；替身只提供 fetch、ref 和读取面板，不访问网络、数据库或浏览器。一个脚本完成三组输入观察：有效回执正常清理，非 JSON 和空对象回执都异常清理待确认状态。最终保护性断言失败，退出码 1。

证据：[脚本](../../../temp/verification/4.8.4-l/review-1/web-receipt-contract.mjs)、[日志](../../../temp/verification/4.8.4-l/review-1/web-receipt-contract.log)。它说明诊断时 V484-W01 存在，**不能将这次诊断失败改写为全部验证通过**；也不影响此前 G03 的独立通过结论。

## 交付与未执行范围

- 本轮只修改审查文档和必要导航，在 `temp/verification` 保留隔离验证配置、脚本及结果；未修业务实现或正式测试，未修改版本、提交或发布。
- 没有启动真实应用、访问真实模型／数据库／平台、执行真实管理写入；没有进行性能、召回质量、生成质量或端到端浏览器验收。
- 本轮整体评估没有重新打 bootJar 或验证部署目录旧 class；不能借用实施阶段的产物检查宣称本轮重复通过。
- 实机容量、首次迁移与真实交付等继续查阅[部署验收清单](../../development/agent-deployment.md#实机验收清单)。
- 已检查本轮审查及导航涉及的 12 篇文档，本地链接、标题锚点与源码行号目标检查通过；差异检查未发现空白错误。新文件另外执行无索引差异检查，不为文档整理再次重复测试或打包。

## 历史问题与修改边界（已关闭）

以下为修复前观察和验收要求，保留追溯；当前状态以上方实施收尾为准。

### V484-W01：无效回执提前清除待确认操作

#### 触发、证据与影响

管理操作已持有 `pending`，服务端或中间层返回 HTTP 200，但正文不是 JSON、为 `null`，或缺少必要回执字段：

1. [api.ts:45](../../../frontend/src/api.ts#L45) 将 JSON 解析失败转成 `null`，HTTP 成功后直接 `as T` 返回。泛型断言不做运行时验证。
2. [AgentWorkspaceView.finish:178](../../../frontend/src/AgentWorkspaceView.vue#L178) 在读取 `result.status` 前就执行 `pending.value = undefined`，也未关联原操作编号。
3. 非 JSON 输入随后发生 `TypeError`，空对象则显示“本次没有提交新修改：undefined。”；两种情况下原待确认状态已经丢失。

受影响的是调用该 `finish` 的记忆、分层记忆、人格和计划管理回执。结果未知时，界面失去原编号查询／同编号重试入口；若用户因此创建新操作，可能失去原幂等恢复路径。**本轮没有证明服务端发生重复提交或越权，也没有证明线上已经出现坏响应。**

隔离诊断读取真实 `api.ts`，并用 TypeScript AST 提取真实 Vue `finish` 执行，只替换网络与视图状态，不重写被测逻辑。三个输入的观察如下：

| 输入 | 请求封装是否接受 | 待确认操作是否保留 | 实际结果 |
| --- | --- | --- | --- |
| 完整且匹配的 APPLIED 回执 | 是 | 否 | 正常控制，符合预期 |
| HTTP 200，非 JSON 正文 | 是 | 否 | TypeError，恢复状态丢失 |
| HTTP 200，正文为 `{}` | 是 | 否 | 显示 undefined 状态，恢复状态丢失 |

证据：[诊断脚本](../../../temp/verification/4.8.4-l/review-1/web-receipt-contract.mjs)、[实际输出](../../../temp/verification/4.8.4-l/review-1/web-receipt-contract.log)。这是一个脚本的三个输入场景；脚本完成观察后在首个保护性断言失败退出，不是两个正式测试失败。

#### 修改边界

- 请求层明确区分“必须返回 JSON”和“允许无正文”两种合同。JSON 解析失败抛出协议错误，不以 `null as T` 伪装成功；登录、登出等合法空响应不能因此被统一误判。
- 变更回执增加小型运行时校验，覆盖对象形状、必要字段类型、当前资源允许的状态及原 `operationId`。按各动作合同关联目标；克隆会产生新目标，不能盲目要求回执 `target` 一律等于提交目标。
- 取得有效且匹配的确定回执后，才保存已确认结果、清除 `pending`。无效、未知或不匹配回执保留原资源、参数与编号，并明确显示“结果未确认”，不能显示“没有提交”。
- 保留现有查询与同编号恢复路径，不自动新建编号重试、不自动重复执行恢复／清空／克隆。有效的 CONFLICT、NOT_FOUND 等业务终态按各接口合同处理，不与协议错误混为一类。
- 不改变后端事务、防重、版本与授权语义；不为了修补此问题引入一套新管理状态框架。

#### 必要验收

1. 完整匹配的成功／冲突等有效终态按合同收敛；回执已确认后，刷新视图失败不能被误认为原变更失败并重新提交。
2. 非 JSON、`null`、缺字段、字段类型错误、未知状态及编号不匹配均保留原待确认数据；错误信息不回显原始敏感正文。
3. 未确认状态可查询原回执；恢复／重试使用完全相同的编号与参数，不产生新操作。
4. 合法空响应接口、CSRF 轮换、会话过期及切页面确认仍正常。复用现有测试设施或小型纯函数测试，不需要搭建完整浏览器测试平台才能修复。

### V484-A01：缓存声明仍未覆盖高频静态元数据发现

#### 审查时事实

| 路径 | 高频行为 | 已有保障与剩余缺口 |
| --- | --- | --- |
| [AgentAccessGate:98、124](../../../src/main/java/org/zexnocs/teanekoagent/runtime/access/AgentAccessGate.java#L98) → [ConfigDataAgentScopePolicy:64](../../../src/main/java/org/zexnocs/teanekoagent/integration/config/ConfigDataAgentScopePolicy.java#L64) → 组件校验 | 显式组件配置在准入及复核时进入 `validate`；[enabled:128](../../../src/main/java/org/zexnocs/teanekoagent/component/AgentComponentRegistry.java#L128) 每次 `getMethod`；[checkDependencies:148](../../../src/main/java/org/zexnocs/teanekoagent/component/AgentComponentRegistry.java#L148) 每次取组件注解依赖 | 收尾时组件目录已经实现 `IScanner`、显式初始化及完整映射发布；不能再报“未接入扫描器”。剩余是方法和注解等静态声明未随目录缓存 |
| [AgentComponentRegistry.describe:158](../../../src/main/java/org/zexnocs/teanekoagent/component/AgentComponentRegistry.java#L158) | 排序与展示重新读取注解，重组字段／系统描述 | 页面目录不应重新发现声明；运行参数和资格则应实时读取 |
| [ClientManagementDirectory.list:50](../../../src/main/java/org/zexnocs/teanekoapp/web/client/ClientManagementDirectory.java#L50) 与 [guard:94](../../../src/main/java/org/zexnocs/teanekoapp/web/client/ClientManagementDirectory.java#L94) | 每次请求对已知类型调用 `findMergedAnnotation(...PluginOwned)` | Bean 注册表已经在构造时建立；插件 owner 仍重复发现，目录也未实现业务 `IScanner` 生命周期 |

这是项目 AGENTS.md 的明确扫描合同缺口，不是本轮测出的吞吐瓶颈，也**不是每次请求都做全量 Bean 扫描**；更不是 G03 代理方法缓存回归。与旧 E3 同属声明生命周期问题，本轮只将上述具体调用链列为可实施范围。

#### 修改边界

- 在现有业务目录内编译不可变条目，保存真实类型、Bean、插件归属、依赖、所需 Method 以及约定为静态的公开描述。沿用真实 Bean，不绕过代理，不创建第二套注册中心。
- 组件目录保留刚接入的 `IScanner`；客户端管理目录按同一初始化／显式重扫合同收敛。全部校验成功后原子发布，失败不暴露半成品；首次不可用时明确拒绝，不在查询中补扫描。
- 缓存的是声明，不是资格：插件启停及代次、当前 session、连接状态、配置 revision、参数校验和授权守卫仍在实际操作边界执行。
- 不把整个 `validate` 简单缓存为“已允许”，也不把可变 DTO/配置节点作为共享不可变快照。若某描述确需动态值，显式分开静态描述与运行状态，不能借动态状态继续发现注解。

#### 必要验收

初始化后反复准入、组件目录展示和客户端查询不再触发发现；新增声明只在初始化／显式重扫进入目录。覆盖失败重扫仍可读取完整旧目录、重名拒绝、插件停用立即拒绝、重启代次不复用旧授权及新配置参数生效。测试观察发现入口与业务结果，不以微基准代替合同验证。
