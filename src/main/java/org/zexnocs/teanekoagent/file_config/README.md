# 一. Agent File Config 模块结构介绍

`file_config` 负责读取 Agent 运行时主配置。模型调用默认参数放在 `config/agent/model.yml`；Agent 主配置放在 `config/agent/main-config.yml`，token 监控器配置放在 `config/agent/token-monitor.yml`。

| 类或接口 | 作用 |
|:---:|---|
| `AgentMainFileConfig` | `config/agent/main-config.yml` 对应的数据类。 |
| `AgentTokenMonitorFileConfig` | `config/agent/token-monitor.yml` 对应的数据类。 |
| `IAgentFileConfigService` | Agent 文件配置读取接口。 |
| `AgentFileConfigService` | Agent 文件配置读取实现，配置缺失时返回默认配置。 |

# 二. 配置文件

| 文件 | 作用 |
|---|---|
| `config/agent/main-config.yml` | Agent 运行时共享配置，目前包含默认模型适配器 ID。 |
| `config/agent/token-monitor.yml` | token 使用量记录、上下文快照、清理策略和告警上报配置。 |
| `config/agent/model.yml` | 每个模型适配器 ID 下的默认 options，由 LLM 配置服务读取。 |

`main-config.yml` 同时包含 Agent 思考流程配置：`thinking-enabled`、`max-thinking-steps`、`max-thought-summary-length` 和 `include-thoughts-in-output`。默认最多执行 3 个模型步骤，其中最后一步用于关闭工具并收束最终答案。

# 三. Token 监控器

`AgentTokenMonitorFileConfig` 控制以下行为：

| 配置类型 | 说明 |
|---|---|
| 记录开关 | `enabled`、`record-context`。 |
| 快照大小 | `max-context-snapshot-characters`。 |
| 保留策略 | `short-context-retention-days`、`long-context-retention-days`、`abnormal-context-retention-days`。 |
| 告警阈值 | `context-window-tokens`、`low-remaining-token-threshold`、`warning-usage-ratio`、`abnormal-usage-ratio`。 |
| debugger 报告 | `report-warning-to-debugger`、`report-abnormal-to-debugger`、`report-recipients`。 |
| 清理任务 | `cleanup-enabled`、`cleanup-cron`。 |
