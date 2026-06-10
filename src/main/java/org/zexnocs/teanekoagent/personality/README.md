# 一. Personality 模块结构介绍

`personality` 模块负责解析当前请求使用的人格状态。它从运行配置、文件基础人格、学习修正和长期记忆中得到 `ResolvedAgentPersonality`，并把模型覆盖参数转换为 `LLMModelOptions`。

| 类或接口 | 作用 |
|:---:|---|
| `AgentPersonalityResolver` | 当前人格解析入口，输出 `ResolvedAgentPersonality`。 |
| `ResolvedAgentPersonality` | 单次请求解析完成后的人格、边界、记忆和模型参数快照。 |
| `AgentRequestContext` | scopeId、agentId、userId、conversationId 四元上下文。 |
| `PersonalityBoundaryPolicy` | 描述可学习字段和不可学习字段。 |
| `PersonalitySource` | 标记人格来源。 |
| `AgentLLMModelOptionsResolver` | 将 Agent 运行配置转换为 `LLMModelOptions`。 |
| `PersonalityLearningService` | 写入通过边界检查的人格微调，记录被拒绝的冲突项。 |

# 二. 人格解析流程

```markdown
1. AgentContextService 使用当前上下文构造 AgentRequestContext。
2. AgentPersonalityResolver 通过 IAgentPersonalityConfigPort 读取运行配置。
3. 如果启用自定义人格，使用配置中的自定义人格作为 active base personality。
4. 如果未启用自定义人格，使用 file_config 中的基础人格定义。
5. 根据基础人格生成 PersonalityBoundaryPolicy。
6. 查询当前 scopeId + agentId 的 PersonalityDeltaRecord。
7. 查询当前 scopeId + agentId + userId 的长期记忆。
8. 使用 AgentLLMModelOptionsResolver 构造 LLMModelOptions。
9. 返回 ResolvedAgentPersonality 给 PromptBuilder 和 Runtime。
```

# 三. 配置来源

| 来源 | 包 | 作用 |
|---|---|---|
| 文件基础人格 | `personality.file_config` | 提供默认 agentId、身份、核心特质、说话风格和边界。 |
| 运行配置 | `personality.config` | 控制是否启用 Agent、自定义人格、记忆、学习和模型参数。 |
| 学习修正 | `memory` | 只保存通过边界检查的人格微调。 |
| 长期记忆 | `memory` | 提供用户事实、偏好、关系和摘要。 |
| LLM 参数 | `llm.framework.model` | 使用 `LLMModelOptions` 表示 provider、model、temperature、topP、maxTokens 等。 |

# 四. 核心 API

| API | 说明 |
|---|---|
| `AgentPersonalityResolver.resolve(AgentRequestContext)` | 解析当前请求的人格状态。 |
| `AgentRequestContext.of(String, String, String)` | 创建不指定 conversationId 的请求上下文。 |
| `AgentRequestContext.withAgentId(String)` | 复制上下文并替换 agentId。 |
| `ResolvedAgentPersonality.modelOptions()` | 获取当前请求模型参数覆盖。 |
| `PersonalityLearningService.recordDelta(...)` | 写入人格学习修正；冲突项写入 conflict。 |
| `AgentLLMModelOptionsResolver.resolve(...)` | 把运行配置转换为 LLM options。 |

# 五. 学习边界

| 内容 | 是否允许学习 | 说明 |
|---|---|---|
| 身份、名称、核心人格 | 否 | 属于基础人格和硬边界。 |
| 工具规则、权限规则 | 否 | 属于运行层约束。 |
| 说话细节 | 是 | 只能作为低优先级补充。 |
| 用户偏好 | 是 | 以长期记忆形式注入。 |
| 关系状态 | 是 | 应带置信度和更新时间。 |
| 单次命令 | 否 | 不应直接变成长期人格或记忆。 |

# 六. 注意事项

| 项 | 说明 |
|---|---|
| 自定义人格 | 启用自定义人格时，角色人格层以配置为准，但运行硬规则仍然有效。 |
| 文件人格 | 文件人格适合作为默认稳定基线，不应被记忆写回覆盖。 |
| 模型路由 | `modelId` 映射到 `LLMModelOptions.provider`，由 `LLMModelService` 路由。 |
| 记忆注入 | 记忆由 resolver 查询后交给 PromptBuilder 注入，不在模型调用中临时拼接字符串。 |
| 冲突记录 | 被拒绝的学习项用于审计，不进入 prompt。 |
