# 一. LLM File Config 结构介绍

`llm/file_config` 负责把本地文件配置转换为 LLM 框架可用的默认模型适配器和默认 options。配置文件位置为：

```text
config/llm/main-config.yml
```

如果本地文件不存在，`FileConfigService` 会优先从下面的模板复制：

```text
src/main/resources/templates/config/llm/main-config.yml
```

# 二. 配置文件结构

```yaml
default-model-id: "deepseek"

models:
  - id: "deepseek"
    model: "deepseek-v4-flash"
    api: ""
    api-key: ""
    base-url: ""
    temperature: 0.7
    max-tokens: 2048
    metadata:
      custom-provider-field: ""
```

| 字段 | 说明 |
|---|---|
| `default-model-id` | 默认模型适配器 ID，通常与 provider 相同，例如 `deepseek`。用于 `LLMModelService.call(prompt)`。 |
| `models` | 各模型适配器默认 options 列表。 |
| `models[].id` | 模型适配器注册 ID，必须与模型注册到 `LLMModelService` 的 ID 一致；不再使用 `provider/model` 格式。 |
| `models[].provider` | `id` 未设置时的兼容回退字段；推荐优先写 `id`。 |
| `models[].model` | 供应商侧具体模型名称，例如 `deepseek-v4-flash`；用于覆盖模型代码中的默认模型名，不参与路由。 |
| `models[].api` | 通用 API 配置，具体含义由供应商适配器解释。 |
| `models[].api-key` | API key。 |
| `models[].base-url` | 供应商 API base URL。 |
| `models[].metadata` | 供应商私有参数，原样进入 `LLMModelOptions.metadata`。 |

# 三. 添加新的模型配置项

在 `models` 列表中添加一项时，按下面规则填写：

```yaml
models:
  - id: "deepseek"                 # 必须等于模型适配器注册 ID，通常就是 provider。
    model: "deepseek-v4-flash"     # 供应商侧默认模型名称；未填写时使用模型代码中的默认值。
    api-key: "${DEEPSEEK_API_KEY}" # 访问凭据应来自 file config、环境变量或数据库，不要写死在代码中。
    base-url: "https://api.deepseek.com"
    api: "/chat/completions"
    temperature: 0.7               # 通用 options 字段；未填写时沿用代码默认值。
    max-tokens: 2048
    metadata:                      # 供应商私有参数，具体 key 由适配器约定。
      body.top_logprobs: 5
```

`models[].id` 如果暂时没有对应的已注册模型适配器，不会影响启动；只有实际调用该 ID 时，`LLMModelService` 才会要求对应适配器存在。

# 四. Options 合并规则

实际调用模型时，默认 options 会按顺序合并：

```text
模型代码默认 options
    -> config/llm/main-config.yml 中对应模型适配器 ID 的默认 options
    -> 本次 prompt.getOptions()
```

文件配置只覆盖显式声明的字段。未声明字段继续使用代码默认值；不属于通用 `LLMModelOptions` 的供应商私有字段应放入 `metadata`。

# 五. 模型适配器 ID 映射要求

新增模型供应商时，需要保证配置中的 `id` 与模型 Bean 注册 ID 完全一致。默认注册 ID 来自 `ILLMModel#getProvider()`：

```java
LLMModelId.of(provider)
```

例如：

| Provider | 默认 Model | 注册 ID |
|---|---|---|
| `deepseek` | `deepseek-v4-flash` | `deepseek` |
| `openai` | `gpt-4.1` | `openai` |

如果 `models` 中写了尚未注册的 ID，不会影响启动，也不会主动报错；只有实际调用该 ID 时，`LLMModelService` 才会要求对应模型存在。

# 六. 扩展供应商时的约定

扩展新供应商时应同时完成：

| 项目 | 要求 |
|---|---|
| 模型 Bean | 实现 `ILLMModel` 或继承 `AbstractLLMModel`，并声明稳定的 provider 级注册 ID 和默认模型名。 |
| README ID 映射 | 在 `llm/framework/README.md` 的“模型适配器 ID 映射”中补充 provider、默认 model、注册 ID。 |
| 文件配置模板 | 在 `config/llm/main-config.yml` 或模板中提供该模型的默认参数示例。 |
| API 配置 | API key、base URL、endpoint 等必须来自 file config 或数据库，不应写死在代码中。 |
| 私有参数 | 通用 options 无法覆盖的字段放入 `metadata`，由供应商适配器读取。 |
