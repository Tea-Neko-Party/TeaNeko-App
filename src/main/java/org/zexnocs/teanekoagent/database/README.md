# 一. Database 模块结构介绍

`database` 模块提供 LLM/Agent 相关数据的 EasyData 存储入口。当前主要服务于长期记忆、人格学习修正、冲突记录，后续也可承载 prompt cache、agent state 和 tool log。

| 类 | 作用 |
|:---:|---|
| `LLMRelatedEasyDataObject` | EasyData 数据对象，承载 namespace、target、o_key、o_value 和版本信息。 |
| `LLMRelatedEasyDataRepository` | LLM 相关 EasyData repository。 |
| `LLMRelatedEasyData` | 便捷访问入口，供上层按 namespace、target 和 key 读写 JSON 值。 |

# 二. EasyData 结构

| 字段 | 说明 |
|---|---|
| `namespace` | 数据域，例如 `memory`、`agent-state`、`prompt-cache`。 |
| `target` | 业务定位，例如 `scope:{scopeId}:agent:{agentId}`。 |
| `o_key` | 具体记录 key，例如 `profile:{userId}` 或 `personality.delta`。 |
| `o_value` | DTO 序列化后的 JSON。 |
| `version` | EasyData 版本字段，用于底层一致性控制。 |

# 三. 约定 key

| namespace | target | o_key | value |
|---|---|---|---|
| `memory` | `scope:{scopeId}:agent:{agentId}` | `personality.delta` | `List<PersonalityDeltaRecord>` |
| `memory` | `scope:{scopeId}:agent:{agentId}` | `personality.conflict` | `List<PersonalityConflictRecord>` |
| `memory` | `scope:{scopeId}:agent:{agentId}` | `profile:{userId}` | `List<AgentMemoryRecord>` |

# 四. 核心 API

| API | 说明 |
|---|---|
| `LLMRelatedEasyData.of(String namespace)` | 获取指定 namespace 的 EasyData 入口。 |
| `LLMRelatedEasyData.get(String target)` | 获取指定 target 的 DTO。 |
| `dto.getList(String key, Class<T>)` | 从 JSON value 读取指定类型列表。 |
| `dto.getTaskConfig(String taskName).set(...).push()` | 写入 key 并推送 EasyData 更新任务。 |

# 五. 读写建议

```markdown
1. 业务模块不要散落拼接 namespace、target 和 o_key。
2. 优先通过 AgentMemoryKeys 生成 key。
3. DTO 字段新增应保持默认值，避免旧 JSON 反序列化失败。
4. 删除一类列表型数据时，可以删除 key，也可以写入空列表。
5. 高频写入数据应考虑后续拆分 namespace，避免单个 key 过大。
```

# 六. 注意事项

| 项 | 说明 |
|---|---|
| 包边界 | database 包只提供通用存储入口，不理解人格、记忆或工具业务语义。 |
| JSON 兼容 | `o_value` 保存 DTO JSON，字段重命名会影响历史数据读取。 |
| target 稳定性 | `scopeId` 或 `agentId` 规则改变会导致旧数据无法按原 key 命中。 |
| 后续扩展 | 语义索引、prompt cache 和 agent state 应新增 namespace，不破坏现有 `memory` key。 |
