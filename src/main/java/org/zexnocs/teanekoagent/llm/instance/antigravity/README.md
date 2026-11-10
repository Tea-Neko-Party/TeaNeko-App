# Gemini 原生代理协议

`antigravity` 是系统内置的 LLM 协议适配器，通过既有 `@LLMModel` 和 `@LLMConfigurationAdapter` 注册，不依赖 Agent 或业务插件。模型配置页、聊天私有模型和表情包独立视觉模型自动使用同一目录。

## 连接配置

```yaml
models:
  - id: "gemini"
    adapter: "antigravity"
    model: "YOUR_DEPLOYED_MODEL"
    api-key: "${GEMINI_PROXY_API_KEY}"
    base-url: "http://127.0.0.1:8045"
    api-path: "/v1beta/models"
    max-tokens: 2048
    context-window-tokens: "${GEMINI_CONTEXT_WINDOW_TOKENS}"
    context-input-ratio: 0.8
    image-input: true
```

基础地址填写代理根地址及必要的部署前缀，不附带 `/v1`。`api-path` 填写模型集合路径，不填写模型名或动作；默认追加 `/{model}:streamGenerateContent?alt=sse`，显式 `stream: false` 时追加 `/{model}:generateContent`。普通入口是 `/v1beta/models`，分组网关可能要求 `/antigravity/v1beta/models`。出现 `API key group platform is not gemini` 时先核对分组专用路径，不据此把 Antigravity 分组改成 Anthropic 协议。

凭据仅通过 `x-goog-api-key` 请求头发送，沿用原文件/私有模型凭据解析和代次校验，不放入 URL、请求正文或公开目录。代理账号登录与分组管理由部署服务负责。

`id` 是应用内稳定路由，`adapter` 才决定传输协议。同一安装可以同时保留 `openai`（Responses）、`openai-completions` 和 `antigravity`；需要两套连接时分别使用唯一的路由 ID。既有路由切换协议不改变聊天对该路由的引用；不会自动探测或在请求失败后换协议。

`max-tokens` 原值映射为 `generationConfig.maxOutputTokens`，上下文容量继续用于本地准入，不自动压低输出上限。样板额度是可编辑的起点，代理别名及真实模型容量需按实际部署填写。未知型号默认只接受文本；视觉别名须显式填写 `image-input: true`，并通过已有识图检测核实。

## 协议行为

- 系统消息进入 `systemInstruction`；普通消息进入 `contents`。图片分别映射为 `inlineData` 或 `fileData`，远端引用由代理处理，本适配器不下载图片。
- 工具声明使用 `functionDeclarations[].parametersJsonSchema`；工具调用和结果使用 `functionCall` / `functionResponse`。响应的原始部件及 `thoughtSignature` 在既有透明状态中冻结，续轮先核对正文、工具名、调用 ID 和参数，再按原顺序回传。缺少上游 ID 时只生成本地配对 ID，不冒充上游 ID。
- JSON 对象模式使用 `generationConfig.responseMimeType=application/json`；Schema 模式额外设置完整 `generationConfig.responseJsonSchema`。原生协议没有 Responses 的 `text.format.strict` 字段。两者均经过既有本地严格校验；显式启用的有限纠错、总时限及预算保持生效。
- `STOP`、截断、安全拒答及未知终态分别映射为成功、长度限制、内容拒绝及错误。非成功终态的部分工具输出不会执行。缺少关键用量字段保留“未知”，不伪造零用量；思考 token 计入输出并保留独立计数。
- 支持文本、用户图片、普通/并行工具及结构输出。`stream` 省略或为 `true` 时使用 SSE，`false` 保留原非流式传输；两种方式都交付完整结果。`logprobs`、超过五个停止词和无法映射的输出部件明确拒绝。没有独立网络客户端或绕过执行控制的后台任务。

## 流式接收与传输中断

SSE 在网络层持续接收，应用在正常 EOF 且每个候选已获得 `finishReason` 后一次性交付，不逐片执行工具或发送消息。可见文本直接拼接，不把网络分片变成多条回复；原生部件和思考签名按原序保留。用量取最后报告的累计快照，不把各片相加。

整个接收阶段仍受原执行总时限约束，收到新分片不会无限延长截止时间。取消或连接重置会释放订阅并丢弃部分结果；即使已经收到终态标记，后续传输错误也不能冒充完整成功。空流、缺失终态、身份变化、终态后新增内容均明确失败。聚合上限为累计 8 Mi 字符、8192 个事件、16384 个部件和 8 个候选，超过时明确报告容量；单事件继续受 HTTP 解码器容量限制。

HTTP 客户端关闭 TCP 中断时的隐式重发，原生请求声明也为零重试。失败不自动切回非流式或重发，因为上游可能已经执行和计费；结构化输出纠错继续只处理已成功接收但格式错误的完整响应。代理不支持 SSE 时显式设置 `stream: false`，不通过失败后试发另一请求猜协议。

流式可以缩短支持 SSE 的代理在生成期间没有数据返回的间隔，但不能消除网关硬超时、网络重置或上游迟迟不发首片的问题。遇到 `Connection reset` 仍需按请求及发生时间检查上游日志；不得把相邻成功请求视为该失败已解决的证据。

可选思考参数：`thinking: true/false` 映射为 `thinkingBudget=-1/0`；也可只设置 `metadata.antigravity.thinkingLevel`（`minimal/low/medium/high`）或 `metadata.antigravity.thinkingBudget`（不小于 -1 的整数）。三者互斥；模型实际是否支持仍由远端校验。代理型号别名如含思考等级，不等于本地自动修改 metadata。`body.*` 不能覆盖消息、Schema、工具或输出额度。

配置与检测见[模型配置](../../../../../../../../../docs/development/web-console-models.md)，结构化输出及一次纠错见[JSON 服务](../../framework/structured/README.md)。
