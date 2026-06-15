# 一. LLM File Config 结构介绍

`llm/file_config` 负责把本地模型配置转换为 LLM framework 可用的默认 `LLMModelOptions`。模型配置文件位置为：

```text
config/agent/model.yml
```

# 二. 配置文件结构

```yaml
models:
  - id: "deepseek"
    model: "deepseek-v4-flash"
    api: "/chat/completions"
    api-key: "${DEEPSEEK_API_KEY}"
    temperature: 0.7
    max-tokens: 2048
    metadata:
      deepseek.reasoningEffort: "medium"
      deepseek.streamIncludeUsage: true
```

| 字段 | 说明 |
|---|---|
| `models` | 各模型适配器默认 options 列表。 |
| `models[].id` | 模型适配器注册 ID，必须与 `LLMModelService` 中注册的 ID 一致。 |
| `models[].provider` | `id` 未设置时的兼容字段，推荐优先写 `id`。 |
| `models[].model` | 供应商侧具体模型名称，只覆盖调用参数，不参与路由。 |
| `models[].api` | 通用 API 配置，具体含义由供应商适配器解释。 |
| `models[].api-key` | API key。 |
| `models[].base-url` | 供应商 API base URL。 |
| `models[].metadata` | 供应商私有参数，原样进入 `LLMModelOptions.metadata`。 |

# 三. 读取顺序

`base options` 专指模型代码自带参数，`default options` 专指文件配置覆盖 base options 后的默认参数。文件配置服务的方法名保持为 `getDefaultOptions(...)`，其第二个参数统一命名为 `baseOptions`。

`LLMFileConfigService` 的读取顺序：

```text
config/agent/model.yml
    -> 默认空配置
```

实际调用模型时，options 合并顺序为：

```text
模型代码 base options
    -> config/agent/model.yml 中对应模型适配器 ID 的 default options
    -> 本次 prompt.getOptions()
```

文件配置只覆盖显式声明的字段。未声明字段继续使用模型 base options；不属于通用 `LLMModelOptions` 的供应商私有字段应放入 `metadata`。

OpenAI Responses API 配置示例：

```yaml
models:
  - id: "openai"
    model: "gpt-5.5"
    api-key: "${OPENAI_API_KEY}"
    base-url: "https://api.openai.com/v1"
    api: "/responses"
    max-tokens: 2048
    metadata:
      openai.reasoningEffort: "medium"
      openai.reasoningSummary: "auto"
      openai.verbosity: "medium"
      openai.store: false
      openai.parallelToolCalls: true
```

OpenAI Chat Completions 配置示例：

```yaml
models:
  - id: "openai-completions"
    model: "gpt-5.5"
    api-key: "${OPENAI_API_KEY}"
    base-url: "https://api.openai.com/v1"
    api: "/chat/completions"
    max-tokens: 2048
    metadata:
      openaiChat.organization: ""
      openaiChat.project: ""
      openaiChat.parallelToolCalls: true
      openaiChat.topLogprobs: 3
```

`openai` 使用 Responses API，`openai-completions` 使用 Chat Completions API。第三方兼容服务应优先继承 Chat Completions 通用层，并使用独立的注册 ID，避免与 OpenAI 原生适配器混淆。

Kimi OpenAI 兼容 Chat Completions 配置示例：

```yaml
models:
  - id: "kimi"
    model: "kimi-k2.6"
    api-key: "${KIMI_API_KEY}"
    api: "/chat/completions"
    max-tokens: 4096
    thinking: true
    metadata:
      kimi.thinkingKeep: true
      kimi.promptCacheKey: ""
      kimi.safetyIdentifier: ""
```

Kimi base URL 固定为官方地址 `https://api.moonshot.cn/v1`。`kimi-k2.7-code` 可以通过 `model` 字段选择，但该模型固定开启 thinking，不应配置 `thinking: false`。

默认模型适配器 ID 属于 Agent 主配置，存放在 `config/agent/main-config.yml` 的 `default-model-id` 字段中。

# 四. 扩展供应商约定

| 项目 | 要求 |
|---|---|
| 模型 Bean | 原生协议实现 `ILLMModel` 或继承 `AbstractLLMModel`；OpenAI Chat Completions 兼容服务优先继承 `AbstractOpenAIChatCompletionModel`。 |
| 配置 ID | `models[].id` 必须与模型 Bean 注册 ID 一致。 |
| API 配置 | API key、endpoint 等必须来自 file config 或数据库，不应写死在代码中。 |
| 私有参数 | 通用 options 无法覆盖的字段放入 `metadata`，由供应商适配器读取。 |
| 快速透传 | Chat Completions 兼容服务可使用 `body.*` 添加请求字段，使用 `header.*` 添加请求头。 |
