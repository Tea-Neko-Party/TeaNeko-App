# v4.8.6-l 本轮验证记录

日期：2026/11/06。评估工作区版本 `4.8.6-l`：起始 HEAD `5050d8b`，期间并行工作提交为 `bc35a78`，收尾仍有未提交的社交装配修正。首发范围为单实例、小规模受控用户。本审查只写文档导航和 temp；本审查没有执行提交、推送、部署或修改业务实现，期间的产品改动及提交来自并行实施。

## 本轮实际执行

| 检查 | 实际结果 | 证据及范围 |
| --- | --- | --- |
| 正式 Java 定向回归 | **9 类 159 项通过，0 失败/错误/跳过** | JDK 25，正式 test 源集，无源码排除；测试类见下表 |
| O01/O02 原审查诊断 | **4 项通过** | 2 项脱敏、2 项提前终态；隔离副本只适配明确的最新观测开关 |
| 收尾 Spring 装配检查 | **SocialComponentWiringTest 9 项通过** | 仅针对最后并行修改的组件服务依赖和资料归属；与前述 159 项分开记录，不代表完整实机启动 |
| 图库事务重试诊断 | **1 项失败，复现 J01** | 一次提交冲突后误报 INPUT_BUDGET；0 执行错误；不是回归通过 |
| 前端回执诊断 | **4 个无效回执场景失败，2 个有效对照通过** | 执行真实 api.ts 和两页真实函数，替换网络/响应式容器/刷新；复现 F01 |
| 真实 Vue 浏览器诊断 | 确认 F02 无名控件、F03 tabs 缺口；选定日志页 390/320px 无页面横向溢出，最终 pageerror 为空 | 无界面 Chrome、真实组件库、替身 API；不是完整端到端或读屏验收 |
| Java 结构扫描 | 生产 1312、Agent 605、图库 42、测试 200 文件的快照；有人工方法清单 | 词法候选只用于排序，不是缺陷计数；见 Java 专题 |
| 文档 | 9 份报告及 3 处导航的本地引用、行号/锚点检查通过；`git diff --check` 通过，新文档无空白错误 | 引用数量及详细结果见临时 `document-check.json`；不因文档修改重新打包 |

| 正式测试类 | 项数 |
| --- | ---: |
| LLMInteractionServiceTest | 50 |
| LLMLayerBoundaryTest | 1 |
| InteractionLogFormatterTest | 14 |
| AgentLayerBoundaryTest | 2 |
| AgentObservationTest | 4 |
| AgentServiceTest | 72 |
| WebConsoleSecurityTest | 13 |
| WebManagementCallsTest | 1 |
| ProjectLayerBoundaryTest | 2 |

正式回归最终命令（已执行）：

```powershell
$env:JAVA_HOME='C:\Users\q9375\.jdks\ms-25.0.2'
.\gradlew.bat --offline --no-daemon --no-configuration-cache --console=plain `
  -I temp/verification/4.8.6-l/mvp-review/review.init.gradle `
  mvpPriorRegression mvpPriorDiagnostics -x webBuild -x webInstall
```

第一次正式源集收尾日志为 `final-java.log`。其后发现并行提交及源码变化，重新运行同一范围，最新日志为 `final-current-java.log`，`BUILD SUCCESSFUL`，结果仍为 159 项正式回归和 4 项原诊断通过，重复运行不累加数量。没有联网下载或真实模型调用。中途因并行测试源码编译错误使用过隔离源集，最终已切回正式源集。保留首轮与隔离日志用于追溯，不用它们代替最终结果。

22:32 的上述回归之后，组件资料归属与组合入口继续修改。本轮重读后仅补跑同一 init 脚本下的 `mvpLateWiringReview`：9 项通过，日志 `late-wiring.log`。它使用真实 Spring 装配与替身业务依赖，检验组件发现、轮次服务工厂、运行资料与撤销边界；没有把旧 159 项称为覆盖这次新增装配修改。最终源码快照将这两组时点分别记录。

## 失败诊断怎样重现

J01 的隔离用例复用正式 `MediaReplyIntegrationTest.Harness`，无冲突对照先成功，再用现有 `beforeCommit` 钩子注入一次乐观锁提交冲突。输入预算 7302 字节、单页估算 862 字节；一次重试次数断言通过，完整页断言失败，实际为 `complete=false / INPUT_BUDGET`。有界等待 5 秒、类级期限 20 秒，没有数据库网络访问。

只运行 `mvpStickerRetryDiagnostic` 指定的方法 `StickerQueryRetryReviewTest.queryRetryMustNotChargeInputBudgetTwice`。生成文件复用了其他夹具/测试内容，不应把整个复制类加入正式测试，修复时只迁入有价值的诊断要求。

F01 的每页两个失败输入是 HTTP200 `{}` 与别的 operationId 回执；两页都清 pending，聊天正文/媒体同时清空。有效对照确认测试没有把一切成功都判失败。该证据证明客户端确认合同失效，没有证明真实部署已经重复发送。

## 临时证据入口

全部位于 `temp/verification/4.8.6-l/mvp-review/`，可能不进入 Git，因此问题的触发和根因也保存在正式专题中。

- [最终日志](../../../temp/verification/4.8.6-l/mvp-review/final-current-java.log)、[正式测试 XML](../../../temp/verification/4.8.6-l/mvp-review/regression-results/)、[旧诊断 XML](../../../temp/verification/4.8.6-l/mvp-review/prior-diagnostic-results/)。
- [收尾装配日志](../../../temp/verification/4.8.6-l/mvp-review/late-wiring.log)、[装配检查 XML](../../../temp/verification/4.8.6-l/mvp-review/late-wiring-results/TEST-org.zexnocs.teanekoagent.component.SocialComponentWiringTest.xml)。
- [重试诊断源码](../../../temp/verification/4.8.6-l/mvp-review/java-style/src/org/zexnocs/teanekoagent/component/memory/namespace/StickerQueryRetryReviewTest.java)、[失败 XML](../../../temp/verification/4.8.6-l/mvp-review/java-style/diagnostic-results/TEST-org.zexnocs.teanekoagent.component.memory.namespace.StickerQueryRetryReviewTest.xml)。
- [前端函数诊断](../../../temp/verification/4.8.6-l/mvp-review/frontend/receipt-diagnostics.json)、[浏览器结果](../../../temp/verification/4.8.6-l/mvp-review/frontend/browser-diagnostics.json)、[前端快照](../../../temp/verification/4.8.6-l/mvp-review/frontend/source-snapshot.json)。
- [初始生产源码快照](../../../temp/verification/4.8.6-l/mvp-review/source-before.json)、[最终差异与汇总](../../../temp/verification/4.8.6-l/mvp-review/final-evidence.json)、[Java 候选摘要](../../../temp/verification/4.8.6-l/mvp-review/java-style/style-summary.json)。

代理对 J01 直接相关实现及前端五个诊断文件做过 hash 复核，未变化；AgentWorkspace 只有导入排版变化，F03 片段重读及定位已更新。J04 的协议快照文件有变更，unchecked 及乱码示例重新确认仍在；统计明确属于 21:00 的扫描快照。并行共享索引补建已重读并撤销旧候选，社交装配修正没有消除重复观察事务。159+4 的运行前保存 `source-test-before.json`，装配检查前保存 `source-wiring-before.json`；各自与收尾快照的比较及新增文件写入 `final-evidence.json`。这些快照只包含生产 Java/前端，不声称覆盖全部配置、测试与二进制。历史归档计划里的 349、287、135 等用例数量不累计为本次运行数。

## 未执行的范围

未连接真实 MySQL、外部模型或平台，没有真实发消息、恢复生产备份或进行负荷测试。没有完成全站无障碍、读屏、所有主题对比度或完整实际 JAR/静态资源部署核验。A01 挂起、D01 吞吐和旧 A2 副作用目前是源码/合同分析，不能写为本轮已运行故障实验。

上线验收见 [评估标准](assessment-standard.md)和既有 [部署清单](../../development/agent-deployment.md#实机验收清单)。本轮诊断为核实问题而执行；最终文档校验不扩大为无关测试或重新打包。
