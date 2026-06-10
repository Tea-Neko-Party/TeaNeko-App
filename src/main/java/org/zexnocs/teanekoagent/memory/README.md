# 一. Memory 模块结构介绍

`memory` 模块负责 Agent 长期记忆、人格学习修正和冲突记录。第一阶段使用确定性 EasyData key 保存 JSON DTO，后续可以在不改变服务接口的前提下加入语义索引或向量检索。

| 类或接口 | 作用 |
|:---:|---|
| `AgentMemoryRecord` | 用户事实、偏好、关系和摘要等长期记忆记录。 |
| `MemoryRecordType` | 记忆类型枚举。 |
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
5. 按 confidence 和 updatedAt 排序。
6. 截断到指定 limit。
7. 返回给 personality resolver，再由 prompt builder 注入当前 Prompt。
```

# 四. Tool API

| Tool | 参数 | 作用 |
|---|---|---|
| `query_memory` | `scopeId`、`agentId`、`userId`、`limit` | 查询当前上下文相关长期记忆。 |
| `write_user_fact` | `scopeId`、`agentId`、`userId`、`content`、`confidence` | 写入稳定用户事实或偏好。 |

`AgentMemoryToolProvider` 复用 LLM framework 的 `@LLMToolProvider` 和 `@LLMToolMapping`。参数 schema、反射调用和返回值序列化均由 LLM framework 处理。

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
| 锁定记录 | `locked` 用于未来管理能力，自动学习不应覆盖锁定记忆。 |
| 主人格边界 | 记忆不能改变身份、核心人格或硬边界。 |
