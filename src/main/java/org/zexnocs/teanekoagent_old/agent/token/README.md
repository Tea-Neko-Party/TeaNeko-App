# 一. Agent Token 模块结构介绍

`agent.token` 负责 Agent 运行时的 token 使用量记录、上下文快照、清理策略和告警事件。它不解析供应商原始响应，而是复用 LLM framework 已经提供的 `ILLMUsage`。

| 类 | 作用 |
|:---:|---|
| `AgentTokenMonitorService` | 记录模型调用 usage、保存上下文快照、注册清理任务、推送告警事件并写 warn。 |
| `AgentTokenUsageRecord` | token 使用摘要，包含 api、模型、prompt/completion/total token、上下文长度和快照引用。 |
| `AgentTokenContextSnapshot` | 可清理上下文快照，只保存渲染后的 role/name/text，不保存原始 message 实现对象。 |
| `AgentTokenWarningEvent` | token 告警事件，允许监听器在 warn 日志前处理告警。 |
| `AgentTokenWarningData` | token 告警聚合数据，包含本轮多次模型调用的 token 汇总。 |
| `AgentTokenUsageLevel` | token 使用级别：`NORMAL`、`WARNING`、`ABNORMAL`。 |

# 二. 配置

配置文件位置：

```text
config/agent/token-monitor.yml
```

主要配置项：

| 字段 | 说明 |
|---|---|
| `enabled` | 是否启用 token 监控器。 |
| `record-context` | 是否保存上下文快照。 |
| `short-usage-token-threshold` | 小型 token 使用阈值，小于该值使用短保留期。 |
| `abnormal-usage-token-threshold` | 异常 token 使用阈值。 |
| `short-context-retention-days` | 小型上下文快照保留天数，默认 7 天。 |
| `long-context-retention-days` | 普通或较长上下文快照保留天数，默认 30 天。 |
| `abnormal-context-retention-days` | 异常上下文快照保留天数；负数表示不自动清理，0 表示不保存。 |
| `context-window-tokens` | 模型上下文窗口 token 上限；大于 0 时用于上下文剩余量告警。 |
| `low-remaining-token-threshold` | 剩余 token 告警阈值。 |
| `warning-usage-ratio` | warning 使用比例阈值。 |
| `abnormal-usage-ratio` | abnormal 使用比例阈值。 |
| `report-warning-to-debugger` | warning 是否调用 `ILogger#errorWithReport`。 |
| `report-abnormal-to-debugger` | abnormal 或模型异常是否调用 `ILogger#errorWithReport`。 |
| `cleanup-cron` | 上下文快照清理任务 cron。 |

# 三. 数据落点

| 数据 | EasyData | namespace | target |
|---|---|---|---|
| token 使用摘要 | `DebugEasyData` | `agent-token-usage` | `scopeId/agentId/date` |
| 上下文快照 | `CleanableEasyData` | `agent-token-context` | `usageId` |

上下文快照的 key 固定为 `context`。摘要记录只保存短字段和快照引用，完整上下文只在 cleanable 表中短期保存。

# 四. 告警流程

```markdown
1. AgentRuntimeService 每次模型调用完成后调用 AgentTokenMonitorService.recordModelCall(...)。
2. 监控器从 ILLMResult.getUsage() 读取 token 使用量。
3. 监控器写入 token 使用摘要，并按配置写入上下文快照。
4. 单轮对话结束后，AgentRuntimeService 调用 warnIfNecessary(...)。
5. 如果本轮任一记录达到 warning 或 abnormal，监控器推送 AgentTokenWarningEvent。
6. AgentTokenWarningEvent 处理完成后，监控器写 warn 日志。
7. 如果配置允许，监控器同时调用 ILogger#errorWithReport 报告给 debugger。
```

# 五. 判断规则

| 条件 | 级别 |
|---|---|
| `totalTokens >= abnormal-usage-token-threshold` | `ABNORMAL` |
| 上下文或 completion 使用比例达到 `abnormal-usage-ratio` | `ABNORMAL` |
| 上下文或 completion 使用比例达到 `warning-usage-ratio` | `WARNING` |
| 上下文或 completion 估算剩余 token 小于等于 `low-remaining-token-threshold` | `WARNING` |
| 未达到以上条件 | `NORMAL` |

上下文窗口来自 `context-window-tokens`；completion 上限来自本次调用的 `LLMModelOptions.maxTokens`。
