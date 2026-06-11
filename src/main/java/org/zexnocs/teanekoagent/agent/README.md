# 一. Agent 模块结构介绍

`agent` 模块负责一次对话的运行时编排。它保存会话消息历史，调用人格和记忆模块构建 Prompt，并使用 LLM framework 完成模型调用、工具回填和出站消息生成。

| 类或接口 | 作用 |
|:---:|---|
| `AgentRuntimeService` | 处理一次 `AgentInboundMessage`，执行模型调用和 tool call loop，返回 `AgentOutboundMessage`。 |
| `AgentContextService` | 创建上下文、追加消息、解析人格、构建 Prompt、压缩历史和写入暂存记忆。 |
| `AgentConversationContext` | 保存 conversationId、scopeId、agentId、userId、LLM 消息历史、已解析人格和暂存记忆。 |
| `TeaNekoAgent` | 单个会话的轻量门面，便于上层按对象方式追加消息、压缩、构建 Prompt 和写入记忆。 |
| `IAgentContextCompressionStrategy` | 上下文压缩策略接口。 |
| `TailKeepingAgentContextCompressionStrategy` | 默认压缩策略，保留首个 system message 和最近消息窗口。 |
| `AgentInboundMessage` | 平台无关的入站消息 DTO。 |
| `AgentOutboundMessage` | 平台无关的出站消息 DTO。 |
| `IAgentHostPort` | 宿主应用能力 port，定义 scope 解析、发送和用户资料读取。 |
| `agent.event` | 运行时事件包，提供整轮对话、模型调用、工具调用和出站消息的事件数据与事件类。 |
| `file_config` | Agent 主配置与 token 监控器配置。 |
| `agent.token` | token 使用量日志、上下文快照、清理任务和告警事件。 |

# 二. 对话处理流程

```markdown
1. 宿主应用把平台消息转换为 AgentInboundMessage。
2. AgentRuntimeService.handle(...) 构造 AgentTurnData 并推送 AgentTurnEvent。
3. AgentTurnEvent 未取消时，默认调用 AgentRuntimeService.__handleForEvent(...)。
4. 当前人格未解析时，调用 AgentContextService.resolvePersonality(...)。
5. 如果当前配置未启用 Agent，直接结束事件默认流程。
6. 将用户文本通过 LLMMessageListBuilder 写入 user message。
7. 使用上下文压缩策略控制消息窗口长度。
8. AgentContextService.buildPrompt(...) 构造 LLMPrompt。
9. AgentRuntimeService 为 Prompt 注入当前可见 ILLMTool 列表。
10. 推送 AgentModelCallEvent，默认调用 LLMModelService.call(...)。
11. assistant message 写回上下文。
12. 如果 assistant 带有 tool_calls，逐个推送 AgentToolCallEvent。
13. AgentToolCallEvent 默认执行工具并写入 tool message 后继续下一轮。
14. 如果没有 tool_calls，构造 AgentOutboundMessageData 并推送 AgentOutboundMessageEvent。
15. token 监控器聚合本轮模型调用记录，必要时推送 AgentTokenWarningEvent，并在事件结束后写 warn。
16. 事件未取消时，handle(...) 返回最终 AgentOutboundMessage。
```

# 三. 核心 API

| API | 说明 |
|---|---|
| `AgentRuntimeService.handle(AgentConversationContext, AgentInboundMessage)` | 使用默认最大工具轮数处理一次入站消息。 |
| `AgentRuntimeService.handle(..., int maxToolRounds)` | 指定最大工具轮数，避免工具循环无限进行。 |
| `AgentRuntimeService.__handleForEvent(AgentTurnData)` | `AgentTurnEvent` 的默认运行时处理入口，通常不由业务代码直接调用。 |
| `AgentContextService.createContext(String, String, String, String)` | 创建并立即解析人格的会话上下文。 |
| `AgentContextService.appendUser(...)` | 追加 user message。 |
| `AgentContextService.appendTool(...)` | 追加 tool result message。 |
| `AgentContextService.buildPrompt(...)` | 根据上下文、人格和额外组件构建 `LLMPrompt`。 |
| `AgentContextService.recordStagedMemories(...)` | 将上下文暂存记忆写入长期记忆服务。 |
| `AgentConversationContext.snapshotMessages()` | 返回当前 LLM 消息历史快照。 |
| `TeaNekoAgent.buildPrompt(String)` | 通过会话门面构建 Prompt。 |

# 四. Tool Call Loop

| 阶段 | 行为 |
|---|---|
| 暴露工具 | `AgentToolRegistryService.getToolList(...)` 返回 `ILLMTool` 列表并写入 `LLMModelOptions.tools`。 |
| 模型请求 | 模型在 assistant message 中返回 `ILLMToolCall` 列表。 |
| 执行工具 | `AgentToolCallEvent` 默认调用 `AgentToolRegistryService.call(ILLMToolCall)`。 |
| 结果回填 | 运行时使用 `LLMMessageListBuilder.addTool(toolCallId, result)` 写入 tool message。 |
| 继续推理 | 下一轮重新构建 Prompt，让模型读取 tool result 后生成回复或继续调用工具。 |

# 五. 事件扩展点

| 事件 | 默认行为 | 可扩展点 |
|---|---|---|
| `AgentTurnEvent` | 调用 `AgentRuntimeService.__handleForEvent(...)`。 | 取消整轮对话、替换出站结果、追加运行时状态。 |
| `AgentModelCallEvent` | 调用 `LLMModelService.call(prompt)`。 | 修改 Prompt、替换模型结果、取消模型调用。 |
| `AgentToolCallEvent` | 执行 `AgentToolRegistryService.call(toolCall)`。 | 改写工具调用、替换工具结果、记录工具审计。 |
| `AgentOutboundMessageEvent` | 无额外默认动作。 | 修改出站文本、取消本次回复、附加发送前审计。 |
| `AgentTokenWarningEvent` | 事件完成后由 token 监控器写入 warn，并可按配置报告 debugger。 | 监听 token 异常、上下文接近耗尽或模型异常。 |

# 六. LLM 复用点

| LLM 类型 | Agent 使用方式 |
|---|---|
| `ILLMMessage` | 会话上下文直接保存该接口列表。 |
| `LLMMessageListBuilder` | 构造 system/user/assistant/tool 消息。 |
| `LLMPrompt` | PromptBuilder 的唯一输出类型。 |
| `LLMModelService` | 统一模型调用入口。 |
| `LLMModelOptions` | 承载模型参数和工具列表。 |
| `ILLMToolCall` | 表示模型返回的工具调用请求。 |

# 七. Token 监控器

| 项 | 说明 |
|---|---|
| 使用量来源 | 直接读取 `ILLMResult.getUsage()`，不重复实现模型 usage 解析。 |
| 日志摘要 | 写入 `DebugEasyData` 的 `agent-token-usage` namespace，包含 api、模型、token 明细和上下文长度。 |
| 上下文快照 | 写入 `CleanableEasyData` 的 `agent-token-context` namespace，按配置保留 7 天、30 天、异常不保存或不自动清理。 |
| 清理任务 | `ApplicationReadyEvent` 后按 `agent/token-monitor.yml` 中的 cron 注册定时清理任务。 |
| 告警事件 | 单轮对话完成后，如果达到 warning/abnormal 阈值，会先推送 `AgentTokenWarningEvent`，再写 warn。 |
| debugger 报告 | `report-warning-to-debugger` 和 `report-abnormal-to-debugger` 控制是否调用 `ILogger#errorWithReport`。 |

# 八. 注意事项

| 项 | 说明 |
|---|---|
| 用户消息去重 | 如果用户消息已经写入上下文，构建 Prompt 时应传入空字符串，避免重复追加。 |
| 工具轮数 | 默认最多 3 轮工具调用。需要长链工具时应显式配置上限。 |
| 空回复 | 入站文本为空、Agent 未启用或模型未生成可见文本时返回 `Optional.empty()`。 |
| 事件取消 | 取消 `AgentTurnEvent` 会跳过默认运行流程；取消 `AgentModelCallEvent` 或 `AgentToolCallEvent` 时应在 data 中写入替代结果。 |
| 上下文压缩 | 默认策略只保留首个 system 和最近消息，未来可替换为摘要压缩策略。 |
| 平台发送 | Runtime 只生成 `AgentOutboundMessage`，实际发送由宿主 adapter 负责。 |
