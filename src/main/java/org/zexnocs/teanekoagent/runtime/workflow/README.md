# 显式多 Agent 协作

`AgentWorkflowService` 提供宿主明确安排的顺序协作，不向主聊天或模型自动注册分派工具。先用现有 AgentFactory 创建不同实例，再将这些实例作为借用步骤交给服务；实例、注册表及父控制仍由调用方拥有。

```java
var steps = List.of(
        new AgentWorkflowStep("draft", writer, "基于用户已给条件起草建议"),
        new AgentWorkflowStep("review", reviewer, "复核先前建议的证据与遗漏，不虚构执行结果"));
return workflows.run(workflowId, origin, steps, parentControl, currentSourceCheck);
```

`writer`、`reviewer` 是不同的 AgentHandle，`origin` 是真实宿主请求。`currentSourceCheck` 是宿主当前来源、权限及会话代次的只读检查，不能来自模型声明。计划最多八步，名称和实例不重复。创建/登记不会启动模型，只有明确调用 run 才开始；调用方完成自有实例后使用注册表 remove 关闭并移除，协作服务不会关闭借用实例。

## 身份、状态与结果

所有子步骤保留根请求的 actor、client/scope 和 activity，派生请求没有原文授权凭据、原会话 stamp 或其他实例的供应商透明状态。步骤目标及先前答案只作为 USER 参考数据交接，不提升成运行规则或新的明确写入意图。实例不会共享可变 Conversation，也不把一个实例的完整工具诊断或透明 metadata 当作另一个实例的回答。

第一个实际结果确定的记忆引用和人格身份被后继步骤固定；如果根请求已经固定引用，则继续使用它。每次交接和最终交付前复核先前步骤的准入、记忆引用及实际资料，来源撤销、scope 变化或旧资料不能继续公开时停止后续步骤。自定义资料源需支持只读 verifyReplay，未支持则明确拒绝交接，不重新检索或偷偷重跑模型。

结果包含整体状态、已观察步骤诊断及父预算观察。只有整体 SUCCESS 的 answer 可用于最终交付；步骤诊断即使保留了旧答案，也不能当作当前授权。服务不发送平台消息，不自动重试或提供持久化工作流恢复；同一业务重放必须由宿主先完成防重仲裁。

## 预算和取消

调用方提供已有 ExecutionControl，至少声明 MODEL_REQUESTS、TOOL_ROUNDS 和 TOOL_EXECUTIONS；可以同时声明 TOTAL_TOKENS。服务仅创建共享父预算的子控制，每个实例继续沿用它，第二个实例不能重置次数或 token。父上限和每个实例的本地上限同时成立。

父控制应由宿主设置整体截止时间，各子轮还受到其本地时限约束。需要单独取消该协作时，由调用方先创建一个父控制的分支，再借给协作服务；取消该分支不会反向取消根控制、其他分支或关闭实例。结果阶段的观察取消不等于取消实际执行。预算读数是共享父预算的累计观察，可能包含其他共享子任务和未结算预留，不能累加各步骤读数。

可运行离线示例复用真实 Factory、Agent、LLM 交互及手动执行夹具：

```powershell
.\gradlew.bat test --tests "org.zexnocs.teanekoagent.runtime.workflow.AgentWorkflowIntegrationTest"
```

示例验证两个实例顺序交接、统一身份、供应商状态隔离、总预算、来源撤销和取消，不访问真实模型。实际模型的协作质量和端到端时延仍需实机验收。
