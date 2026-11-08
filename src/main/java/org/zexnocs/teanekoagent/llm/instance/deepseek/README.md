# 一. DeepSeek Instance 结构介绍

`llm/instance/deepseek` 是 DeepSeek Chat Completions API 的模型适配器实现，注册 ID 为 `deepseek`。

| 文件 | 作用 |
|---|---|
| `DeepSeekChatModel` | Spring Bean 模型适配器，继承 `AbstractLLMModel` 并通过 `IAPIResponseService` 发起请求。 |
| `DeepSeekChatCompletionRequestData` | DeepSeek 请求 DTO，实现 `IAPIRequestData`。 |
| `DeepSeekChatCompletionResponseData` | DeepSeek 响应 DTO，实现 `IAPIResponseData`。 |
| `DeepSeekChatCompletionMapper` | 在 DeepSeek API DTO 和 LLM framework DTO 之间转换。 |

# 二. 配置

`DeepSeekChatModel` 默认：

| 项目 | 值 |
|---|---|
| 注册 ID | `deepseek` |
| 默认模型 | `deepseek-v4-flash` |
| 内建 base URL | `https://api.deepseek.com` |
| 默认 API path | `/chat/completions` |

模型名称及非敏感参数来自 model.yml：

```yaml
models:
  - id: "deepseek"
    model: "deepseek-v4-flash"
    temperature: 0.7
    max-tokens: 2048
    context-window-tokens: "${DEEPSEEK_CONTEXT_WINDOW_TOKENS}"
    context-input-ratio: 0.8
    metadata:
      deepseek.reasoningEffort: "medium"
      deepseek.topLogprobs: 5
      deepseek.streamIncludeUsage: true
```

密钥与连接从统一 model.yml 的可信凭据端口取得，Core YAML 支持环境占位符。详见[配置说明](../../file_config/README.md)。`body.*` 只用于非敏感请求字段，认证字段会被拒绝。

# 三. 参数映射

| LLMModelOptions | DeepSeek 请求字段 |
|---|---|
| `model` | `model` |
| `thinking` | `thinking.type = enabled/disabled` |
| `DeepSeekModelOptions.reasoningEffort` | 顶层 `reasoning_effort` |
| `maxTokens` | `max_tokens` |
| `frequencyPenalty` | `frequency_penalty` |
| `temperature` | `temperature` |
| `topP` | `top_p` |
| `presencePenalty` | `presence_penalty` |
| `responseFormat = JSON` | `response_format.type = json_object` |
| `stopWords` | `stop` |
| `stream` | `stream`，当前适配器只支持 `false` 或未设置 |
| `tools` | `tools[].function` |
| `toolChoice` | `tool_choice` |
| `logprobs` | `logprobs` |
| `DeepSeekModelOptions.topLogprobs` | `top_logprobs` |
| `DeepSeekModelOptions.streamIncludeUsage` | `stream_options.include_usage` |
| `DeepSeekModelOptions.userId` | `user_id` |

`deepseek.reasoningEffort` 保留现有配置名，映射到请求顶层；`thinking` 仅在显式指定开关时生成，并包含 `type`。只配置思考强度不会生成缺少 `type` 的对象。参数位置以 [DeepSeek 官方协议](https://api-docs.deepseek.com/api/create-chat-completion/)为准，供应商兼容值 `medium` 无需为本次修复改名。

# 四. 响应映射

DeepSeek 返回的 `choices` 会转换为 `LLMChoice`，`message.tool_calls` 会转换为 `LLMToolCall`，`usage` 会转换为 `LLMUsage`，包括 cache hit/miss token 和 reasoning token。

`reasoning_content` 作为供应商元数据保存，工具续轮时回传，不作为聊天正文。工具预算耗尽后，已有工具历史的请求仍保留本轮获准的工具定义，同时设置 `tool_choice=none`；执行预算不会因此恢复。正文泄漏 DSML 时，由[有限交互](../../framework/interaction/README.md#内部工具标记泄漏)在原预算内至多纠正一次；不把标记当成工具执行，也不直接发送给用户。

当前适配器使用 `org.zexnocs.teanekocore.api_response` 统一发起 API 请求，不直接创建 `WebClient`。


图像输入使用统一 `ImageLLMContentPart`，按顺序映射为真实的协议内容块，纯文本保持既有格式。所选模型、角色及 detail 必须符合 [已声明的图像能力](../../file_config/README.md#图像能力)，未核实的模型不会自动继承同供应商其他模型的能力。body 扩展不能覆盖 DTO 已建模字段及模型、输入、工具或上下文控制字段。
DeepSeek Chat Completions 的图像块使用 `image_url: {url, detail?}`；Pro 的图片输入在本地拒绝，不执行自动模型切换。
