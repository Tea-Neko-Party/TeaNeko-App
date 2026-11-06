# v4.8.4-l 修正独立复核

日期：2026/11/06。本轮复核上一轮 V484-W01、V484-A01 以及随后完成的 V484-D02，不修改业务源码或正式测试。Git 基线为 `d884b5e`，包含未提交修正；工作区版本已为 `4.8.5-l`，因此结论只覆盖本文列出的修正，不代表正在实施的 4.8.5-l 观测／存储工作已验收。

## 一、结论

**W01、A01、D02 独立复核通过，可以保持关闭。** 本轮没有在这些修正范围内确认新的待修业务缺陷。D01 仍是有实际专用 HTTP 协议需求才启动的设计项，不需要为完成审查额外创建框架。G03 相关实现与测试没有新的未提交差异，本轮未重复执行其 66 项历史复验，不重复计算为当前通过数。

| 编号 | 复核结果 | 源码及实际行为 | 关闭边界 |
| --- | --- | --- | --- |
| V484-W01 | 通过 | JSON／空响应分离；真实回执校验在清除 pending 之前执行。坏正文、缺字段、未知状态、编号和目标不符均保留原请求；有效回执后的刷新失败不恢复提交状态 | 关闭原已复现的回执丢失问题，不扩大为所有前端响应都已做完整领域校验 |
| V484-A01 | 通过 | 组件声明、字段、依赖、系统描述和插件归属在初始化／显式重扫编译；客户端目录缓存 owner，查询和守卫读取已发布条目，动态启停及代次继续复核 | 关闭原指定的组件／客户端目录调用链，不宣称整个仓库所有扫描器都已统一 |
| V484-D02 | 通过 | 页面、标题、导航分组、可见条件和加载入口共用声明表；真实浏览器验证草稿、未决操作、轮询及退出生命周期 | 新增页面仍需增加一条构建声明，但不再修改 App／导航的业务分支；不是任意远端页面插件系统 |
| V484-D01 | 保持条件设计项 | 本次功能仍使用原管理资源与服务端精确授权，没有要求新的 HTTP 协议 | 不因“扩展方便”开放任意路径或删除 denyAll、CSRF、session／插件检查 |

## 二、关键源码复核

### W01：协议确认先于状态收敛

- [api.ts](../../../frontend/src/api.ts) 的 `request` 拒绝成功状态下的无效 JSON／null；`requestEmpty` 为合法空响应保留独立入口，登录、登出使用该入口，CSRF 返回值另行验证。
- [management-receipts.ts:16](../../../frontend/src/management-receipts.ts#L16) 接收 `unknown`，校验必要字段、资源允许的状态和原 `operationId`；成功目标按动作区分，克隆／备份允许返回新目标。
- [AgentWorkspaceView.finish:209](../../../frontend/src/AgentWorkspaceView.vue#L209) 先取得 `confirmedReceipt` 再清除 `pending`；刷新失败明确提示“修改已确认”，不让用户误将已提交操作重新执行。
- `resend` 保留原请求参数；整理恢复的查询使用 `REPLAY_OPERATION`，不把查询变成再次恢复。坏回执经过真实 `run` 错误处理后仍保留恢复入口。

本轮隔离执行的 20 个场景覆盖上述原故障和修复容易误伤的终态／目标差异，未仅以 TypeScript 编译通过代替业务验证。

### A01：静态声明和动态资格分离

- [AgentComponentRegistry.reload:56](../../../src/main/java/org/zexnocs/teanekoagent/component/AgentComponentRegistry.java#L56) 完整构造 entries、metadata 和 Bean 身份对应的 owner 后一次发布 `Snapshot`；参数校验、依赖检查、脱敏和目录描述使用同一调用捕获的快照。模板独立解码，调用者不能污染后续公开默认值。
- `getAnnotation`、`findMergedAnnotation`、初始化入口的 Method 和 `PluginAccess.owner` 解析保留在重扫链，而非准入／页面读取链。运行阶段调用 [enabledOwner](../../../src/main/java/org/zexnocs/teanekocore/plugin/PluginAccess.java#L59) 检查实际插件状态，没有把“曾经允许”缓存为永久资格。
- [ClientManagementDirectory](../../../src/main/java/org/zexnocs/teanekoapp/web/client/ClientManagementDirectory.java) 实现 `IScanner`，完整构造后发布。`invoke` 捕获真实条目，守卫同时检查插件代次和目录条目身份；失败重扫不撤销旧完整目录，成功换代不允许旧调用继续沿用旧目录资格。
- 正式回归实际验证查询不调用扫描器、组件描述不反复调用静态描述方法、失败重扫保留旧数据、默认模板不被污染、真实 Spring 装配，以及客户端停用／重启后旧令牌仍失效。成功目录换代的守卫位置另外通过源码核对，不将未单独重现的并发交错宣称为已穷尽验证。

### D02：页面注册与生命周期共同收敛

[page-registry.ts](../../../frontend/src/pages/page-registry.ts) 的声明派生页面类型和导航，重复 key 明确失败；[ConsolePageHost](../../../frontend/src/pages/ConsolePageHost.vue) 负责按需加载和会话内保留，[ConsolePageOutlet](../../../frontend/src/pages/ConsolePageOutlet.vue) 按页面 key 隔离实例。[page-guards](../../../frontend/src/pages/page-guards.ts) 汇总嵌套草稿，确认退出只批准离开，不提前删除数据；[active-polling](../../../frontend/src/pages/active-polling.ts) 在页面未激活或浏览器不可见时停止新轮询，不取消已提交写入。

同一组 11 个真实浏览器场景分别在源码服务和本轮生成的生产分包运行：首次仅挂载概览；文件／模型实例独立；坏回执跨导航保留；明文令牌离页收起；指令轮询暂停／恢复且不重发；独立空间选择保留；网页聊天输入及标题保留；动态加载失败保留原页；注销失败不清状态、成功才销毁；桌面／移动可见条件一致；认证失效后的迟到模块不重建旧会话。两轮均无页面异常或未声明 API 调用。

## 三、本轮真实验证

| 验证 | 实际结果 | 证据 |
| --- | --- | --- |
| Java 定向回归 | 9 类、39 项通过，0 失败／错误／跳过 | [完整日志](../../../temp/verification/4.8.4-l/review-2/regression.log)、[HTML](../../../temp/verification/4.8.4-l/review-2/regression-report/index.html)、[XML](../../../temp/verification/4.8.4-l/review-2/regression-results) |
| 回执协议与工作区函数 | 20 个隔离场景全部通过 | [脚本](../../../temp/verification/4.8.4-l/review-2/receipts-reassessment.mjs)、[最终结果](../../../temp/verification/4.8.4-l/review-2/receipts-final.log) |
| 源码页面交互 | 11 组全部通过，errors／unknown 均为空 | [源码结果](../../../temp/verification/4.8.4-l/review-2/pages-browser-result.json) |
| 生产分包页面交互 | 同一组 11 个场景全部通过，不算新增 11 种场景 | [分包结果](../../../temp/verification/4.8.4-l/review-2/pages-bundle-result.json) |
| 构建链 | Gradle 成功，定向 Test 任务实际执行；其依赖链执行 vue-tsc 和 Vite 生产构建 | 同上 Gradle 日志；Java 编译任务为 up-to-date，不写成重新编译 |

Java 回归明细：AgentAssemblyTest 6、ClientManagementDirectoryTest 1、WebManagementCallsTest 1、WebConsoleSecurityTest 13、AgentLibraryIntegrationTest 3、FileSettingsIntegrationTest 10、ChatSettingsServiceTest 1、ProjectLayerBoundaryTest 2、AgentLayerBoundaryTest 2，共 39 项。没有重跑实施阶段的 1038 项或首次评估的 121 项，不将历史数量累计为本轮数量。

JDK 25 下从项目根目录执行，独立报告写入 `review-2`：

```powershell
.\gradlew.bat --offline -I temp/verification/4.8.4-l/review-2/review.init.gradle revisionReviewRegression -x webInstall --console=plain
node temp/verification/4.8.4-l/review-2/receipts-reassessment.mjs
node temp/verification/4.8.4-l/review-2/pages-reassessment.mjs
node temp/verification/4.8.4-l/review-2/pages-reassessment.mjs --bundle
```

[Gradle 隔离配置](../../../temp/verification/4.8.4-l/review-2/review.init.gradle)只选择现有正式用例；[浏览器运行包装](../../../temp/verification/4.8.4-l/review-2/pages-reassessment.mjs)复用实施阶段原脚本，仅隔离输出位置，不修改预期或 API 替身。关键源码指纹见[本轮快照](../../../temp/verification/4.8.4-l/review-2/source-snapshot.json)。

首次回执隔离脚本将模块放入不同 VM，却未共享浏览器同一 realm 中的 `Error` 构造器，造成错误消息断言误判；原待确认状态已被正确保留。修正测试环境的构造器一致性后，保持生产源码和断言不变重跑，20 项全通过。[初次脚本输出](../../../temp/verification/4.8.4-l/review-2/receipts.log)保留，不记为生产缺陷，也不隐藏为一次通过。

## 四、交付边界

- 这是功能与实现复核，**不是负荷测试**。浏览器只连接本机回环测试服务，全部 API 使用替身；没有真实后端、数据库、模型或平台调用。
- 未重打 bootJar，未重做部署目录／JAR 的 class 与分包一致性检查；实施阶段的 172 个 class、44 个资源核对仍是历史证据，不冒充本轮复验。
- 不修改业务实现、正式测试、版本文件或当前 4.8.5-l 实施计划；仅新增隔离验证资料和复核文档，同步当前审查状态。
- 真实吞吐、并发容量、数据库锁竞争、模型质量与平台交付仍需按[部署验收清单](../../development/agent-deployment.md#实机验收清单)另行安排。39 项功能回归不提供容量或性能结论。
- 本轮范围内没有新增直接代码修复项；下一步继续见 [next-steps.md](next-steps.md)。
