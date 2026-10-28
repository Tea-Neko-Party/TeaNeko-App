# LLM 本地配置与凭据边界

`LLMFileConfigService` 将 `config/agent/model.yml` 转为模型默认 Options。默认路由通过 `IDefaultModelResolver` 注入；当前装配适配器读取 `config/agent/main-config.yml`。LLM framework 不导入 Agent 配置或旧包类型。

自动往返的次数和时限由 LLM 自己的 `tea-neko.llm.interaction` Spring 配置提供，字段、默认值和显式覆盖方式见 [交互说明](../framework/interaction/README.md)。这些限制不写入模型 metadata，也不依赖 Agent 文件配置；错误配置在调用时返回明确状态。

## 部署配置

先在 `config/agent/main-config.yml` 选择一个已注册的适配器 ID：

```yaml
default-model-id: "deepseek"
```

在 `config/agent/model.yml` 为该 ID 明确填写实际部署的模型名。这里的模型名只是占位符，不是推荐值：

```yaml
models:
  - id: "deepseek"
    model: "YOUR_DEPLOYED_MODEL"
    max-tokens: 2048
    metadata:
      deepseek.reasoningEffort: "medium"
```

密钥及可选连接覆盖放在 **Spring Boot application 配置**中。例如 `application.yml`：

```yaml
tea-neko:
  llm:
    providers:
      deepseek:
        api-key: "${DEEPSEEK_API_KEY:}"
        # base-url: "https://your-trusted-gateway.example/v1"
        # api-path: "/chat/completions"
      openai:
        api-key: "${OPENAI_API_KEY:}"
      kimi:
        api-key: "${KIMI_API_KEY:}"
```

这些占位符由 Spring 解析，`config/agent/model.yml` 不承担密钥占位符解析。供应商 ID 使用小写字母、数字及连字符。`base-url` / `api-path` 未配置时使用适配器内建地址和协议路径；Prompt 中同名连接字段不会改变实际调用地址。base URL 只允许 HTTP(S)，不允许用户信息、查询和片段；API path 必须是以 `/` 开头的路径，不允许外部 URL、查询、片段或目录跳转。

`openai` 使用 Responses；Chat Completions 通用类没有 `@LLMModel`，不能作为独立路由配置。手动构造未注册的 `OpenAIChatCompletionsModel` 时，凭据端口使用它自己的 `openai-completions` ID。兼容供应商应继承通用层并声明自己的注册 ID、默认地址和凭据配置。

## 参数合并与失败语义

| 字段 | 说明 |
| --- | --- |
| `models[].id` | 与 `@LLMModel.id` 对应；必需且不可重复。 |
| `models[].provider` | 未填写 id 时的兼容路由字段。 |
| `models[].model` | 模型服务要求显式填写的供应商模型名称。 |
| 通用参数 | thinking、max-tokens、temperature 等只覆盖显式声明的值。 |
| `metadata` | 非敏感供应商参数；认证字段会被拒绝，包含嵌套和 header/body 形式。 |

调用时依次合并：代码 base options → 本地模型条目 → 单次 Prompt options，最终 provider 固定为实际注册路由 ID。

配置文件不可用、选定条目不存在或 model 为空时，调用抛出 `LLMConfigurationException`，不会静默使用代码中的模型名。结构中的空条目、空路由或重复路由同样报错。验证发生在读取/调用阶段，不在 Bean 构造或模型注册时加载必需密钥，因此缺少某个供应商配置不阻止整个应用启动。Agent 的统一 readiness/禁用门控在后续 Agent 层接入。

纯 LLM 使用方可注入自己的 `IDefaultModelResolver` 和 `ILLMFileConfigService`，也可直接构造模型适配器并注入 `ICredentialResolver`；直接调用不要求 Agent 文件存在。`ILLMModelService.call` 和适配器 `call` 均只执行一次请求，不执行工具循环，不自动重试。

## 图像能力

图像能力由具体适配器按精确模型名声明，不用于设置默认路由，也不从任意 metadata 接受“支持视觉”的开关。未知模型仍可做文本调用，但携带图片会明确拒绝；如需新模型或自定义网关模型名，应在对应适配器覆盖 getCapabilities 并补契约用例。

| 适配器 | 已声明的图像模型 | 图像 detail |
| --- | --- | --- |
| DeepSeek | deepseek-flash、deepseek-v4-flash、deepseek-v4-flash-vision-exp | auto/low/high/original |
| OpenAI Responses / Chat Completions | gpt-5.5、gpt-5.5-2026-04-23、gpt-5.4、gpt-5.4-mini、gpt-5.4-nano | auto/low/high/original |
| OpenAI Responses / Chat Completions | gpt-5.2、gpt-5.1、gpt-4.1、gpt-4.1-mini、gpt-4o、gpt-4o-mini | auto/low/high |
| Kimi | kimi-k2.6、kimi-k2.7-code、kimi-k2.7-code-highspeed | auto（省略协议 detail 字段） |

表格是本适配器已实现且验证请求结构的范围，不是供应商全部模型列表。Kimi 官方图像示例未规定 detail 级别，本实现仅传默认形式。当前图像只进入 user 消息；不支持的模型、角色或 detail 在发出 HTTP 前拒绝。body 扩展不得更换已验证的模型或内容。

协议依据（2026-10-28 核对）：[DeepSeek Vision](https://api-docs.deepseek.com/guides/vision/)、[DeepSeek 模型与旧名称说明](https://api-docs.deepseek.com/quick_start/pricing/)、[OpenAI Images and vision](https://developers.openai.com/api/docs/guides/images-vision)、[GPT-5.5 模型与快照](https://developers.openai.com/api/docs/models/gpt-5.5)、[Kimi 视觉输入](https://platform.kimi.com/docs/guide/use-kimi-vision-model)。本轮只验证离线协议，没有真实模型识图效果或服务端限额验收。

## 旧配置迁移

1. 保留 `config/agent` 的文件布局、路由 ID、模型名和非敏感模型参数。
2. 把旧 `model.yml` 中 `api-key` 移到 `tea-neko.llm.providers.<id>.api-key`，建议使用环境变量占位符。
3. 如需自定义连接，把旧 `base-url` 和 `api` 分别改为 Spring 中的 `base-url` 和 `api-path`；随后从 model.yml 删除旧字段及对应连接 metadata。
4. 删除 Options metadata 中的认证字段。旧字段仍可反序列化以提供明确的迁移错误，但不能再转换为调用选项；不会自动把密钥复制到新配置或数据库。

供应商模型 DTO 及纯 mapper 保留调用签名，新增有序图文映射；生产模型在 mapper 返回后，用可信配置重新设置连接地址。新供应商同样应在请求边界解析凭据，不能从 Options、消息、组件或工具参数取得密钥。内置请求 DTO 的认证字段不进入 JSON；`LLMProviderAccess` 的 JSON 与 toString 只返回脱敏标记。

OpenAI 认证按 [官方 Authentication 说明](https://developers.openai.com/api/reference/overview/)使用服务端凭据与 Bearer 请求头；图像能力及消息协议依据见上方图像能力小节。

## 验证

默认使用无 Spring 上下文的配置/模型替身测试。真实 DeepSeek 测试仅在显式设置 `TEANEKO_LIVE_LLM_TESTS=true` 时运行；它会启动整个 Spring 应用，应另外准备应用依赖与上述配置。
