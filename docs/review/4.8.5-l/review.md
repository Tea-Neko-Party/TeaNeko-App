# v4.8.5-l 复核依据与验证记录

本文保留修复前的独立审查与失败证据。2026/11/06 的后续修正已完成 V485-O01/O02，最新状态与复跑结果见[已完成项](completed.md#本次观测修正)。下文历史数字不代表修复后的结果。

审查日期：2026/11/06。版本文件为 `4.8.5-l`，HEAD 为 `d884b5e`；评价对象是当前未提交实现，重点为[目录、请求观测与存储整理](../../plan/old/4.8.5-observation-storage.md)。本轮没有修改业务代码或正式测试。

结论：本版目录与存储分流的功能方向成立，未发现新的存储迁移阻断问题；请求观测仍有 **V485-O01 凭据脱敏缺口（P1）** 和 **V485-O02 提前终态漏报（P2）**，不能整体关闭。原复现及后续修正已移至[已完成项](completed.md)，不在本文重复展开。

## 架构与实际作用

| 维度 | 本轮评价 | 修改意见或保留边界 |
| --- | --- | --- |
| 扩展性与依赖方向 | LLM、运行域分别定义观察端口，集成层统一实现；底层不反向读取上层配置。已有组件及工具扩展继续驱动前端目录，无需为每个新增工具修改卡片组件 | 保留端口和共享格式器；修正 O01/O02 不需要增加中央工具分支或第二套框架 |
| 请求观测的作用 | 汇总能够关联请求、状态、耗时、真实尝试和用量，工具续轮输入也可观察；网页阶段时间线继续存在 | 功能实际生效，但凭据保护与前置失败覆盖不足，分别由 O01/O02 处理；不将观察日志当作可靠审计或平台送达证明 |
| 异步生命周期 | 沿用 `CompletionStage`、原执行器及数据库任务；Agent 外层归属通过上下文跨线程传递；LLM 观察异常被隔离，Agent 外层也保护观察调用 | 正式取消、预算、重复启动和资源测试通过；未证明高并发吞吐。新格式化仍同步执行于完成回调，耗时需独立测量 |
| 存储职责 | `TeaNekoAgentEasyData.cleanable()` 选择同 namespace 的临时实体，Store 继续使用事务 DTO；业务事实、执行权和防重与可丢弃观察分开 | 当前各 Store 的业务迁移规则有差异，不宜仅为减少重复代码抽成无业务约束的通用搬表器 |
| 防重与恢复 | 发送摘要、任务版本、图库未清理作业继续持久化；临时表缺失不会取得新的发送权或释放未完成资源责任 | 这些持久标记发挥必要作用，不属于“看似有用的冗余”。删除其唯一证据会破坏既有合同 |
| Web 目录 | 前端从目录加载声明，搜索、筛选和详情属于通用展示；本版不新增后端授权入口，筛选不替代执行时权限复核 | 四组浏览器交互通过。专用 HTTP 自动扩展仍沿用 V484-D01 的条件设计，不扩大本次范围 |
| 可维护性与重复 | 同一格式器处理 Agent 和独立 LLM 的内容，避免两套脱敏规则；终态通知目前存在正常链与提前返回的分叉 | O02 应收束终态通知，避免逐个分支补零散日志后继续漏报或重复 |

本轮没有发现应立即删除的无用服务。更完整的组件作用、Agent 组合与 Web 架构结论继续见 [4.8.4-l 整体评估](../4.8.4-l/architecture-review.md)，不把增量复核冒充对全部历史模块的新一轮完整审计。

## 观测采集开销

**非阻断建议，尚无容量故障证据。** [LLMInteractionExecution:222](../../../src/main/java/org/zexnocs/teanekoagent/llm/framework/interaction/LLMInteractionExecution.java#L222) 在每次实际模型调用前无条件创建输入快照，[LLMRequestSnapshot:32](../../../src/main/java/org/zexnocs/teanekoagent/llm/framework/interaction/LLMRequestSnapshot.java#L32) 复制消息并保存协议声明；内容开关到终态格式化时才生效。因此关闭 INFO 或正文并不会取消这部分对象构造，多轮工具交互仍保留各轮输入容器及声明。

`max-content-characters` 限制最终日志正文长度，不是请求快照的内存上限。本文不将其量化为内存泄漏或吞吐下降，文本字符串也不等于每次均深复制。可在后续有真实长上下文、多轮工具负载时，测量观测开关前后的分配量与完成耗时；若需优化，由集成层在请求边界确定一次类型化采集策略，向下传递，关闭时跳过只为日志服务的快照。保持底层不依赖上层配置、真实模型输入不受影响，不在每个消息步骤增加配置扫描。

## 独立验证

本轮输出统一放在 `temp/verification/4.8.5-l/review-1/`，未覆盖实施者的 1025 项结果。

| 验证 | 实际输出 | 证明范围 |
| --- | --- | --- |
| 正式 Java 定向回归 | 12 类、164 项通过，失败 0、错误 0、跳过 0 | 交互执行、Agent 终态、存储迁移与相关集成流程；类清单由隔离 init 脚本固定 |
| 日志脱敏诊断 | 2 项失败，断言确认输出仍含虚构敏感标记 | O01：嵌套 JSON 密钥、内联多项 Cookie |
| 提前终态诊断 | 2 项失败，业务状态和无副作用断言通过，在 INFO 调用次数断言处失败 | O02：前置拒绝、服务已关闭 |
| 目录浏览器检查 | 四组通过，`pageerror` 为空 | 摘要对齐、完整详情与键盘、搜索与客户端组合筛选、390px／320px 页面与抽屉 |
| 文档检查 | 本轮版本文档和入口的本地链接、标题锚点与差异检查通过 | 不等同于功能、JavaDoc 或发布验收 |

Java 使用本机 JDK 25 和已有离线依赖。正式回归与诊断使用不同报告目录；诊断任务应因上述缺陷返回非零退出码，不应记录为全通过。

```powershell
.\gradlew.bat --offline --no-daemon --no-configuration-cache --console=plain `
  -I temp/verification/4.8.5-l/review-1/review.init.gradle `
  observationReviewRegression -x webBuild -x webInstall

.\gradlew.bat --offline --no-daemon --no-configuration-cache --console=plain `
  -I temp/verification/4.8.5-l/review-1/review.init.gradle `
  observationReviewDiagnostics -x webBuild -x webInstall

node temp/verification/4.8.5-l/review-1/directory-browser.mjs
```

证据入口：

- [定向任务与诊断源集](../../../temp/verification/4.8.5-l/review-1/review.init.gradle)、[首次 Java 输出](../../../temp/verification/4.8.5-l/review-1/java.log)、[最终四项诊断输出](../../../temp/verification/4.8.5-l/review-1/diagnostics.log)。
- [正式回归 HTML](../../../temp/verification/4.8.5-l/review-1/regression-report/index.html)、[诊断 HTML](../../../temp/verification/4.8.5-l/review-1/diagnostic-report/index.html) 与对应目录中的 JUnit XML。
- [目录浏览器结果](../../../temp/verification/4.8.5-l/review-1/directory-browser.json)、[本轮浏览器脚本](../../../temp/verification/4.8.5-l/review-1/directory-browser.mjs)。初次直接测量窄屏宽度的断言失败；增加渲染帧等待及实际节点几何记录后，390px／320px 的文档宽度均等于视口宽度，截图也已检查。此过程不作为已确认的产品缺陷。

以上 `temp/` 文件属于本机临时证据，可能不进入 Git；已确认的触发条件和根因保存在正式审查文档中，不依赖临时文件才可理解。

## 尚未执行的验收

未操作真实数据库、模型或平台，未启动完整应用，未做负荷测试，也未重新构建或核对本版全部发布产物。离线事务夹具验证回滚和业务语义，不能替代 MySQL 两表并发锁、实际隔离级别、索引和首次迁移性能。

真实升级继续按[本版计划的后续验收](../../plan/old/4.8.5-observation-storage.md#后续实机验收)和[部署验收清单](../../development/agent-deployment.md#实机验收清单)执行。旧进程停止、一致备份、禁止新旧混跑和回退恢复备份的约束仍有效；本轮没有理由删掉迁移或恢复保护。
