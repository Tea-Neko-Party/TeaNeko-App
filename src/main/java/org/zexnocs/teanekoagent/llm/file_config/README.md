# LLM 本地配置与凭据边界

4.7.0-l 的网页配置管理支持原模板局部保存和独立应用，见[文件配置说明](../../../../../../../../docs/development/web-console-files.md)。Spring 宿主通过 `LLMFileConfigurationEpoch` 在同步参数及请求构造期间固定文件发布代次；旧已解析参数在换代后拒绝使用新凭据。内部 metadata 标记只含非秘密代次，不是供应商请求体字段。已构造请求继续使用原凭据，不持锁等待网络；应用不发起远端密钥有效性检查。

`config/agent/model.yml` 统一保存默认路由、模型参数、api-key、base-url、api-path 和 metadata。Core YAML 解析器统一展开环境变量；LLMFileConfigService 只把非敏感字段转为 Options，FileLLMCredentialResolver 只在真实请求边界取得凭据与连接。LLM 不再为默认路由依赖 Agent 主配置，也不再读取 application 的第二份供应商配置。

自动往返的次数和时限由 LLM 自己的 `tea-neko.llm.interaction` Spring 配置提供，字段、默认值和显式覆盖方式见 [交互说明](../framework/interaction/README.md)。这些限制不写入模型 metadata，也不依赖 Agent 文件配置；错误配置在调用时返回明确状态。

## 部署配置

在同一份 `config/agent/model.yml` 中选择默认路由并配置模型。模型名是占位符，不是推荐值：

```yaml
default-model-id: "${LLM_DEFAULT_MODEL_ID:deepseek}"
models:
  - id: "deepseek"
    model: "${DEEPSEEK_MODEL:YOUR_DEPLOYED_MODEL}"
    api-key: "${DEEPSEEK_API_KEY:}"
    base-url: "${DEEPSEEK_BASE_URL:}"
    api-path: "${DEEPSEEK_API_PATH:}"
    max-tokens: "${DEEPSEEK_MAX_TOKENS:2048}"
    metadata:
      deepseek.reasoningEffort: "${DEEPSEEK_REASONING_EFFORT:medium}"
```

OpenAI、Kimi 或自定义适配器使用同样的条目结构。default-model-id 为空时独立宿主须在 Options 指定供应商。main-config.yml 只保留 Agent 本地总开关。供应商 ID 使用小写字母、数字及连字符；api 为旧 YAML 的 api-path 别名。

所有 YAML FileConfig 的字符串值、嵌套对象/列表和 metadata 都支持 `${变量名}` 与 `${变量名:默认值}`，优先级由 Spring Environment 决定；无 Spring 直接实例化解析器时使用标准系统属性及环境变量。先解析 YAML 再替换字符串节点，因此环境值中的换行、冒号等不会注入新的 YAML 字段。字段名不展开；数字和布尔字段由目标类型转换，metadata 中无明确目标类型的值仍为字符串，不猜测数字或结构。

缺失必需变量或循环引用使整份文件本次加载失败；需要先留空的供应商使用 `${KEY:}`，真正调用该路由时再检查密钥。加载不自动修改文件。显式 FileConfigService.write 保存当前对象值，包括已展开的环境值；需要保留原始占位符时直接编辑 YAML。敏感配置的普通序列化不导出密钥及连接字段，显式本地 YAML 写回会保留这些字段；加载错误不携带原始解析异常或凭据。

`base-url` / `api-path` 留空使用适配器内建地址和协议路径；Prompt 中同名连接字段不会改变实际调用地址。base URL 只允许 HTTP(S)，不允许用户信息、查询和片段；API path 必须是以 `/` 开头的路径，不允许外部 URL、查询、片段或目录跳转。

`openai` 使用 Responses；Chat Completions 通用类没有 `@LLMModel`，不能作为独立路由配置。手动构造未注册的 `OpenAIChatCompletionsModel` 时，凭据端口使用它自己的 `openai-completions` ID。兼容供应商应继承通用层并声明自己的注册 ID、默认地址和凭据配置。

## 参数合并与失败语义

| 字段 | 说明 |
| --- | --- |
| `models[].id` | 与 `@LLMModel.id` 对应；必需且不可重复。 |
| `models[].provider` | 未填写 id 时的兼容路由字段。 |
| `models[].model` | 模型服务要求显式填写的供应商模型名称。 |
| `default-model-id` | 同文件的默认路由，不再从 main-config.yml 读取。 |
| `models[].api-key/base-url/api-path` | 只供凭据端口读取，不合入普通 Options、Prompt 或业务组件。 |
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

1. 保留模型 ID、具体模型和非敏感调用参数，将 main-config.yml 的 default-model-id 移到 model.yml 顶层。
2. 将 `tea-neko.llm.providers.<id>` 的 api-key、base-url、api-path 放入对应 models 条目，推荐使用环境变量引用；删除旧 application 项，不维护两份生效来源。
3. 已经位于 model.yml 的顶层条目密钥/连接字段现在可直接使用；api 作为 api-path 的兼容别名，后续编辑建议统一为 api-path。
4. metadata 保留在同一条目，可以嵌套并使用环境占位符，但认证和连接字段应移到专用字段，不能借 metadata、Prompt 或工具内容替换认证边界。
5. 修改后使用现有文件重载流程；若变更进程环境变量，需按运行环境重新启动进程。缺失或最近加载失败的文件不会以旧凭据继续请求。

供应商模型 DTO 及纯 mapper 保留调用签名，新增有序图文映射；生产模型在 mapper 返回后，用可信配置重新设置连接地址。新供应商同样应在请求边界解析凭据，不能从 Options、消息、组件或工具参数取得密钥。内置请求 DTO 的认证字段不进入 JSON；`LLMProviderAccess` 的 JSON 与 toString 只返回脱敏标记。

OpenAI 认证按 [官方 Authentication 说明](https://developers.openai.com/api/reference/overview/)使用服务端凭据与 Bearer 请求头；图像能力及消息协议依据见上方图像能力小节。

## 验证

默认使用无 Spring 上下文的配置/模型替身测试。真实 DeepSeek 测试仅在显式设置 `TEANEKO_LIVE_LLM_TESTS=true` 时运行；它会启动整个 Spring 应用，应另外准备应用依赖与上述配置。
