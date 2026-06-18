# 一. Agent File Config 模块结构介绍

`file_config` 统一管理 Agent 自身的文件配置，包括运行时主配置、token 监控器配置和文件基础人格配置。模型调用默认参数仍由 `llm.file_config` 读取，避免在 Agent 配置层重复实现模型配置解析。

| 类或接口 | 作用 |
|:---:|---|
| `AgentMainFileConfig` | `config/agent/main-config.yml` 对应的数据类。 |
| `AgentTokenMonitorFileConfig` | `config/agent/token-monitor.yml` 对应的数据类。 |
| `AgentPersonalityFileConfig` | `config/agent/personality.yml` 对应的数据类，保存默认 agentId 和人格定义列表。 |
| `AgentPersonalityDefinition` | 单个人格的身份、核心特质、表达风格、边界和学习策略定义。 |
| `AgentPersonalityFileConfigService` | 读取文件基础人格，并在配置缺失或 ID 不存在时提供稳定的回退人格。 |
| `IAgentFileConfigService` | Agent 文件配置读取接口。 |
| `AgentFileConfigService` | Agent 文件配置读取实现，配置缺失时返回默认配置。 |

# 二. 配置文件

| 文件 | 作用 |
|---|---|
| `config/agent/main-config.yml` | Agent 运行时共享配置，目前包含默认模型适配器 ID。 |
| `config/agent/token-monitor.yml` | token 使用量记录、上下文快照、清理策略和告警上报配置。 |
| `config/agent/personality.yml` | 默认 agentId，以及每个人格的身份、特质、风格、边界和学习策略。 |
| `config/agent/model.yml` | 每个模型适配器 ID 下的默认 options，由 LLM 配置服务读取。 |

`main-config.yml` 同时包含 Agent 思考流程配置：`thinking-enabled`、`max-thinking-steps`、`max-thought-summary-length` 和 `include-thoughts-in-output`。默认最多执行 3 个模型步骤，其中最后一步用于关闭工具并收束最终答案。

# 三. 人格文件配置

`AgentPersonalityFileConfigService` 通过 `FileConfigService` 读取 `personality.yml`。未配置 `default-agent-id` 时使用 `teaneko`；找不到指定人格时，由 `AgentPersonalityDefinition.fallback(String)` 创建最小可用的人格定义。

| 配置内容 | 说明 |
|---|---|
| `default-agent-id` | Agent 未显式指定人格 ID 时使用的默认值。 |
| `characters[].id` | 人格唯一 ID，也是运行时解析和记忆定位的一部分。 |
| `characters[].identity` | 基础身份描述，不允许被学习记忆覆盖。 |
| `characters[].core-traits` | 稳定核心特质。 |
| `characters[].speaking-style` | 默认表达方式和语言风格。 |
| `characters[].boundaries` | 人格边界与禁止学习的约束。 |
| `characters[].learning-policy` | 可学习内容及其应用原则。 |

# 四. Token 监控器

`AgentTokenMonitorFileConfig` 控制以下行为：

| 配置类型 | 说明 |
|---|---|
| 记录开关 | `enabled`、`record-context`。 |
| 快照大小 | `max-context-snapshot-characters`。 |
| 保留策略 | `short-context-retention-days`、`long-context-retention-days`、`abnormal-context-retention-days`。 |
| 告警阈值 | `context-window-tokens`、`low-remaining-token-threshold`、`warning-usage-ratio`、`abnormal-usage-ratio`。 |
| debugger 报告 | `report-warning-to-debugger`、`report-abnormal-to-debugger`、`report-recipients`。 |
| 清理任务 | `cleanup-enabled`、`cleanup-cron`。 |

# 五. 阅读顺序

|顺序|导航|说明|
|---|---|---|
|$1$|[../README.md](../README.md)|了解 Agent 模块边界、运行主流程和各配置模块之间的关系。|
|$2$|[../personality/README.md](../personality/README.md)|了解文件基础人格如何与运行配置、学习修正和长期记忆合并。|
|$3$|[../agent/token/README.md](../agent/token/README.md)|了解 token 监控配置如何控制记录、清理、告警和 debugger 报告。|
|$4$|[../llm/file_config/README.md](../../teanekoagent/llm/file_config/README.md)|了解模型配置为何由 LLM 配置服务独立读取。|
