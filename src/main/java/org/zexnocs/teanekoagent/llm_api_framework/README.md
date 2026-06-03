# 一. LLM API Framework 结构介绍

`llm_api_framework` 是 TeaNeko Agent 的大语言模型 API 中间层。它不绑定 OpenAI、DeepSeek 或其他供应商协议，而是提供统一的 Prompt、Options、Response、Model Registry 和 Function Tool 抽象。

新增供应商时，通常只需要实现一个或多个 `ILLMModel` Spring Bean；如果需要复用工具调用，再实现或注册 `ILLMTool` Bean。

| 模块 | 作用 |
|:---:|---|
| `message` | 定义 system/user/assistant/tool 消息和内容片段。 |
| `model` | 定义模型 ID、模型 options、模型服务和模型适配器基类。 |
| `response` | 定义统一响应结果、choice 和 usage。 |
| `tool` | 定义 Function Tool、参数 schema、tool call 和工具注册服务。 |
| `interfaces` | Prompt、Result、Choice、Usage 等公共接口。 |

# 二. 模型接入

推荐继承 `AbstractLLMModel`，只实现 `doCall(...)`：

```java
@Service
public class DeepSeekChatModel extends AbstractLLMModel {
    public DeepSeekChatModel() {
        super("deepseek", "deepseek-chat",
                LLMModelOptions.builder()
                        .provider("deepseek")
                        .model("deepseek-chat")
                        .temperature(0.7)
                        .maxTokens(2048)
                        .build());
    }

    @Override
    protected ILLMResult doCall(ILLMPrompt prompt, LLMModelOptions options) {
        // 1. 将 prompt.getMessages() 和 options 转成供应商 HTTP request。
        // 2. 调用供应商 API。
        // 3. 将供应商 response 转成 LLMResult。
        throw new UnsupportedOperationException();
    }
}
```

`LLMModelService` 会扫描所有 `ILLMModel` Bean，并使用 `provider/model` 作为唯一 key：

```java
ILLMResult result = llmModelService.call(
        LLMModelId.of("deepseek", "deepseek-chat"),
        prompt
);
```

# 三. Options

`LLMModelOptions` 是统一配置 DTO，字段包括：

| 字段 | 说明 |
|---|---|
| `provider` | 供应商 ID，例如 `openai`、`deepseek`。 |
| `model` | 模型 ID。 |
| `thinking` | 是否启用思考/推理模式。 |
| `maxTokens` | 最大输出 token。 |
| `temperature` / `topP` | 采样控制。 |
| `frequencyPenalty` / `presencePenalty` | 重复与主题惩罚。 |
| `responseFormat` | `TEXT` 或 `JSON`。 |
| `stopWords` | 停止词。 |
| `stream` | 是否流式响应。 |
| `tools` | 可供模型调用的工具列表。 |
| `toolChoice` | `none`、`auto`、`required` 或供应商自定义值。 |
| `logprobs` | 是否请求 token 概率信息。 |
| `metadata` | 供应商扩展字段。 |

接口层保留 `getXxx()` 抛 `UnsupportedOperationException` 的旧约定，同时提供 `findXxx()` Optional 风格方法，便于供应商适配器只读取调用方真正提供的字段。

# 四. Response

统一响应由三个类组成：

| 类 | 作用 |
|:---:|---|
| `LLMResult` | 一次模型调用的完整响应，包含 id、choices、object、created、model 和 usage。 |
| `LLMChoice` | 单个候选结果，包含 index、finishReason、assistant message 和 logprobs。 |
| `LLMUsage` | token 用量信息，兼容 prompt、completion、cache hit/miss 和 reasoning tokens。 |

assistant 消息支持 `tool_calls`，tool 响应用 `LLMToolMessage` 携带 `tool_call_id` 和 tool name。

# 五. Function Tool

`LLMTool` 是当前主要工具类型。它包含：

| 字段 | 说明 |
|---|---|
| `type` | 默认 `function`。 |
| `name` | 工具名，必须全局唯一。 |
| `description` | 工具说明。 |
| `parameters` | JSON-schema-like 参数定义。 |
| `strict` | 是否要求模型严格遵循 schema。 |
| `executor` | 本地执行逻辑，不参与 JSON 序列化。 |

示例：

```java
@Bean
public ILLMTool weatherTool() {
    return LLMTool.function(
            "get_weather",
            "Get current weather by city name.",
            new LLMObjectFunctionParameter(
                    "Weather query parameters.",
                    Map.of("city", new LLMStringFunctionParameter("City name.")),
                    List.of("city")
            ),
            arguments -> "sunny"
    );
}
```

`LLMToolService` 会扫描所有 `ILLMTool` Bean，并按 tool name 注册。模型返回 `ILLMToolCall` 后，可通过 `LLMToolService.call(toolCall)` 执行。

# 六. 供应商适配器职责

| 职责 | 说明 |
|---|---|
| 请求转换 | 把 `ILLMPrompt`、`ILLMMessage`、`LLMModelOptions` 转成供应商请求体。 |
| Tool 转换 | 把 `ILLMTool` 和 `ILLMFunctionParameter` 转成供应商要求的 function schema。 |
| 响应转换 | 把供应商返回值转成 `LLMResult`、`LLMChoice`、`LLMUsage` 和 `LLMToolCall`。 |
| 错误处理 | 将供应商错误转成清晰的异常，交由上层任务或日志系统处理。 |
