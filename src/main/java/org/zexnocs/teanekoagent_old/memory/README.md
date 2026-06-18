# 一. Memory 模块结构介绍

`memory` 模块负责 Agent 长期记忆、人格学习修正和冲突记录。第一阶段使用确定性 EasyData key 保存 JSON DTO，后续可以在不改变服务接口的前提下加入语义索引或向量检索。

| 类或接口 | 作用 |
|:---:|---|
| `AgentMemoryRecord` | 用户事实、偏好、关系和摘要等长期记忆记录。 |
| `MemoryRecordType` | 记忆类型枚举。 |
| `MemoryImportance` | 记忆重要度；高重要度记忆应尽量保留更精确的时间。 |
| `MemoryTimeRange` | 事件发生时间点或范围，与记录创建时间分离。 |
| `MemoryTimePrecision` | 时间精度，例如精确时间、日期、周、月份或大致范围。 |
| `PersonalityDeltaRecord` | 通过边界检查后允许注入 Prompt 的人格微调。 |
| `PersonalityConflictRecord` | 被拒绝的人格修改候选，只用于审计。 |
| `ConversationSummaryRecord` | 对话摘要记录，供上下文压缩或长期回顾使用。 |
| `RelationshipMemoryRecord` | 关系类记忆记录。 |
| `AgentMemoryKeys` | 统一生成 namespace、target 和 o_key。 |
| `AgentMemoryQueryService` | 查询和写入长期记忆、人格修正和冲突记录。 |
| `AgentMemoryToolProvider` | 通过 LLM Function Tool 暴露 `query_memory` 和 `write_user_fact`。 |

# 二. 存储键规则

| 字段 | 规则 | 说明 |
|---|---|---|
| namespace | `memory` | 第一阶段所有 Agent 记忆数据共用该命名空间。 |
| target | `scope:{scopeId}:agent:{agentId}` | 同一作用域和同一 agent 的记忆写在同一个 target 下。 |
| personality delta key | `personality.delta` | 保存 `List<PersonalityDeltaRecord>`。 |
| personality conflict key | `personality.conflict` | 保存 `List<PersonalityConflictRecord>`。 |
| user profile key | `profile:{userId}` | 保存 `List<AgentMemoryRecord>`。 |

# 三. 记忆查询流程

```markdown
1. AgentPersonalityResolver 根据当前 AgentRequestContext 调用 AgentMemoryQueryService。
2. 服务使用 scopeId + agentId 构造 EasyData target。
3. 查询 personality.delta，过滤已过期记录。
4. 查询 profile:userId，过滤已过期记录。
5. 可按事件时间点或范围过滤，旧记录没有事件时间时回退到 createdAt。
6. 按 importance、confidence 和 updatedAt 排序。
7. 截断到指定 limit。
8. 返回给 personality resolver，再由 prompt builder 注入当前 Prompt。
```

# 四. Tool API

| Tool | 参数 | 作用 |
|---|---|---|
| `query_memory` | `scopeId`、`agentId`、`userId`、`limit` | 查询当前上下文相关长期记忆。 |
| `query_memory_by_time` | `scopeId`、`agentId`、`userId`、`timePoint`、`rangeStart`、`rangeEnd`、`limit` | 按 ISO-8601 时间点或时间范围查询事件记忆。 |
| `write_user_fact` | 基础参数以及 importance、事件时间点/范围、时间精度和原始时间表达 | 写入带事件时间的稳定用户事实或偏好。 |

`AgentMemoryToolProvider` 复用 LLM framework 的 `@LLMToolProvider` 和 `@LLMToolMapping`。参数 schema、反射调用和返回值序列化均由 LLM framework 处理。

时间表达由 Agent 自主解析，不提供独立自然语言时间解析器。Agent 根据 `temporal-context` 中的当前时间进行换算，再调用 Tool。例如当前本地时间为 `2026-06-11T20:22:00+08:00`：

| 用户表达 | Tool 参数示例 |
|---|---|
| 一小时前 | `timePoint: 2026-06-11T19:22:00+08:00` |
| 一周前 | `timePoint: 2026-06-04` |
| 一周前左右 | `rangeStart: 2026-06-02T00:00:00+08:00`、`rangeEnd: 2026-06-06T23:59:59+08:00` |

日期形式的 `timePoint` 会匹配该本地日期的完整一天。模糊时间应使用范围参数，不应把“一周前左右”等自然语言直接传给 Tool。

# 五. 记录类型

| 类型 | 用途 |
|---|---|
| `USER_FACT` | 稳定用户事实。 |
| `PREFERENCE` | 用户偏好。 |
| `RELATIONSHIP` | 关系状态和互动印象。 |
| `PERSONALITY_DELTA` | 人格学习修正。 |
| `EPISODE_SUMMARY` | 对话片段或摘要。 |

# 六. 注意事项

| 项 | 说明 |
|---|---|
| 记忆清洗 | `content` 应保存短、明确、可注入 Prompt 的文本。 |
| 过期过滤 | `expiresAt` 不为空且早于当前时间时不会进入查询结果。 |
| 置信度 | `confidence` 范围为 0 到 1，工具写入时会自动夹取到合法范围。 |
| 事件时间 | `eventTime` 表示事情发生时间，`createdAt/updatedAt` 表示记录生命周期，二者不能混用。 |
| 时间精度 | 高重要度记忆优先使用精确时间点或窄范围；低重要度记忆允许使用周、月或大致范围。 |
| 锁定记录 | `locked` 用于未来管理能力，自动学习不应覆盖锁定记忆。 |
| 主人格边界 | 记忆不能改变身份、核心人格或硬边界。 |
