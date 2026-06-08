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
| 默认 API path | `/chat/completions` |

访问参数必须来自 file config 或数据库，不能写死在代码中：

```yaml
models:
  - id: "deepseek"
    model: "deepseek-v4-flash"
    api-key: "${DEEPSEEK_API_KEY}"
    base-url: "https://api.deepseek.com"
    api: "/chat/completions"
    temperature: 0.7
    max-tokens: 2048
    metadata:
      body.top_logprobs: 5
```

`api-key`、`base-url`、`api` 会进入 `LLMModelOptions.metadata`，由 `DeepSeekChatModel` 读取。`metadata` 中以 `body.` 开头的字段会去掉前缀后写入 DeepSeek 请求体。

# 三. 参数映射

| LLMModelOptions | DeepSeek 请求字段 |
|---|---|
| `model` | `model` |
| `thinking` | `thinking.type = enabled/disabled` |
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

# 四. 响应映射

DeepSeek 返回的 `choices` 会转换为 `LLMChoice`，`message.tool_calls` 会转换为 `LLMToolCall`，`usage` 会转换为 `LLMUsage`，包括 cache hit/miss token 和 reasoning token。

当前适配器使用 `org.zexnocs.teanekocore.api_response` 统一发起 API 请求，不直接创建 `WebClient`。
