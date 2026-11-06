# v4.8.5-l 已核实范围

更新日期：2026/11/06。V485-O01/O02 已完成修正；本页分别保留修复验证和此前独立审查证据。剩余实机边界见[下一步实施](next-steps.md)。

## 本次观测修正

### V485-O01

原正则未展开字符串中的 JSON，且授权头匹配依赖行首，导致下面两条原始诊断漏出虚构凭据：

```json
{"body":"{\"api_key\":\"review-hidden-nested\"}"}
```

```json
{"header":"Cookie: first=review-hidden-first; session=review-hidden-session"}
```

已在[共享脱敏器](../../../src/main/java/org/zexnocs/teanekoagent/integration/monitor/ObservationContentRedactor.java)按结构隐藏敏感键的完整值，并检查有限层次的编码 JSON。Cookie、Set-Cookie 和 Authorization 隐藏完整值及缩进续行；畸形敏感片段、残留编码和超限输入明确隐藏，不回退原文。所有处理先于显示截断。

输入、工具参数、工具回填和供应商推理共用该入口。推理分段处理，避免拼接破坏有效 JSON；安全兄弟字段、普通正文、用量和原内容开关继续有效。[正式测试](../../../src/test/java/org/zexnocs/teanekoagent/integration/monitor/InteractionLogFormatterTest.java)覆盖原始载荷、四条入口、多层编码、Unicode 键、对象／数组密钥、畸形跨行内容、完整授权头、处理预算及长转义截断。

### V485-O02

原终态观察只挂在已建立轮次的完成链；活动拒绝、配置拒绝、父控制创建失败和服务关闭后的 `completed(result)` 绕过了观察。

[AgentService](../../../src/main/java/org/zexnocs/teanekoagent/runtime/AgentService.java)现在让提前返回与正常结果共用一个完成通知阶段，每个执行句柄只通知一次。使用原请求编号、实际状态和单调耗时；未调用模型时次数为零。没有增加准入查询、工具调用或全局编号去重，原取消与资源关闭责任保留。

[正式测试](../../../src/test/java/org/zexnocs/teanekoagent/runtime/AgentServiceTest.java)覆盖活动拒绝、关闭后的请求、缺少记忆端口／会话／引用、已取消父控制、冷轮次及运行取消、迟到完成、重复启动和模型失败。关闭 INFO 仍不输出；观察器及日志抛错不改变业务结果或资源回收；同一请求编号的两个独立执行各自通知。

### 修复验证

| 检查 | 实际结果 |
| --- | --- |
| 正式定向回归 | 4 类、135 项通过，失败／错误／跳过均为 0；覆盖格式器、Agent 观察、轮次及 LLM 交互 |
| 原独立诊断 | 原源码未修改，4 项全部通过；新报告单独保存，保留修复前的失败结果 |
| JavaDoc | 本次 3 个主类型和 2 个测试类型通过 |
| 构建及产物 | `bootJar` 通过；212 个相关 class、43 个页面资源与实际启动目录及 JAR 一致；版本仍为 4.8.5-l |
| 文档及工作区 | 本次文档引用和 `git diff --check` 通过；`gradle.properties` 的原 SHA256 保持不变 |

本次日志及 XML 位于 `temp/verification/4.8.5-l/observation-fixes/`，JavaDoc 位于 `temp/javadoc/4.8.5-l/observation-fixes/`。没有运行真实数据库、模型、客户端或负荷验收，没有提交、打标签或发布。

## 修复前独立审查已通过的范围

| 范围 | 已确认行为 | 证据与边界 |
| --- | --- | --- |
| 组件／工具目录 | 读取实际目录，摘要等高且底部对齐；详情保留完整长说明；客户端筛选包含通用工具，可叠加文字搜索 | 四组真实 Vue／Chrome 交互通过，涵盖键盘及 390px／320px；API 使用替身 |
| 正常 Agent／LLM 汇总归属 | 正常 Agent 轮次只汇总一次，其内部 LLM 不重复；独立 LLM 多轮工具交互只通知一次；实际输入快照保留每轮工具定义及回填 | `AgentServiceTest`、`LLMInteractionServiceTest` 通过；不包含 O02 的提前返回 |
| 观测隔离与开关 | 已应用配置控制 INFO、上下文和推理；配置或日志端口异常不改变业务结果；时间线仅保存有界元数据 | `AgentObservationTest` 通过；一般格式与截断通过不代表 O01 已关闭 |
| 发送回执分流 | 正文移入临时表；持久摘要和状态仍阻止 ACK 重发、UNKNOWN 补发；正文丢失后已开始发送仍可确认 | 跨表迁移失败回滚及清理后行为通过；并非允许删除持久 `claims` |
| 计划管理回执 | 短期操作观察迁入临时表，任务规格、状态和版本继续持久保存；清理后旧版本取消被拒绝，任务不复活 | `AgentTransientStorageTest` 与计划集成测试通过 |
| 图库终态作业 | 无恢复责任的终态及短期回执进入临时表；待执行、未释放容量／刷新占用的作业仍在持久目录 | 迁移、采集、管理、删除集成测试通过；物理文件及数据库竞争仍需实机验收 |
| 维护游标 | 游标按实体分流；迁移与维护动作同事务，回滚不发布候选游标；删除临时游标可从首页重建 | `AgentTransientStorageTest` 通过；业务整理水位保持持久化 |

修复前独立执行 12 类、164 项正式定向回归，全部通过，无跳过；当时另有 4 项临时诊断失败，确认 O01/O02 的缺口。完整路径、执行命令及原报告索引见 [review.md](review.md#独立验证)。这些历史数量不与本次验证重复累计。

实施者此前记录的 1025 项离线回归、31 类 JavaDoc、211 个 class 和 43 个前端资源核对，保留在[归档实施计划](../../plan/old/4.8.5-observation-storage.md)。这些是实施阶段证据，本轮没有重新执行全量回归、JavaDoc、打包及全部产物核对。

[4.8.4-l W01/A01/D02](../4.8.4-l/completed.md)与 [G03](../4.8.3-l/g03-reassessment.md)沿用既有关闭结论；本轮不重复计算其验证数量。
