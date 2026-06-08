# 一. LLM API Framework 结构介绍

`llm/framework` 是 TeaNeko Agent 的大语言模型 API 中间层。它不绑定 OpenAI、DeepSeek 或其他供应商协议，而是提供统一的 Prompt、Options、Response、Model Registry 和 Function Tool 抽象。

新增供应商时，通常只需要实现一个或多个 `ILLMModel` Spring Bean；如果需要复用工具调用，再实现或注册 `ILLMTool` Bean。

| 模块 | 作用 |
|:---:|---|
| `message` | 定义 system/user/assistant/tool 消息和内容片段。 |
| `model` | 定义供应商级模型适配器 ID、模型 options、模型服务和模型适配器基类。 |
| `response` | 定义统一响应结果、choice 和 usage。 |
| `tool` | 定义 Function Tool、参数 schema、tool call 和工具注册服务。 |
| `../file_config` | 定义 LLM 文件配置，用于保存默认模型适配器和各供应商默认 options。 |
| `interfaces` | Prompt、Result、Choice、Usage 等公共接口。 |

# 二. 模型接入

推荐继承 `AbstractLLMModel`，只实现 `doCall(...)`：

```java
@Service
public class DeepSeekChatModel extends AbstractLLMModel {
    public DeepSeekChatModel() {
        super("deepseek", "deepseek-v4-flash",
                LLMModelOptions.builder()
                        .provider("deepseek")
                        .model("deepseek-v4-flash")
                        .temperature(0.7)
                        .maxTokens(2048)
                        .build());
    }

    @Override
    protected TaskFuture<ILLMResult> doCall(ILLMPrompt prompt, LLMModelOptions options) {
        // 1. 将 prompt.getMessages() 和 options 转成供应商 HTTP request。
        // 2. 调用供应商 API。
        // 3. 将供应商 response 转成 TaskFuture<ILLMResult>。
        throw new UnsupportedOperationException();
    }
}
```

`LLMModelService` 会扫描所有 `ILLMModel` Bean，并使用供应商级 ID 作为唯一 key。默认情况下该 ID 等于 `getProvider()`，例如 `deepseek`：

```java
TaskFuture<ILLMResult> resultFuture = llmModelService.call(
        LLMModelId.of("deepseek"),
        prompt
);

ILLMResult result = resultFuture.finish().join();
```

如果只想切换同一供应商下的具体模型名称，不需要注册新的 ID，而是覆盖本次调用的 `model` option：

```java
TaskFuture<ILLMResult> resultFuture = llmModelService.call(
        "deepseek",
        "deepseek-v4-flash",
        prompt
);

ILLMResult result = resultFuture.finish().join();
```

# 三. Options

`LLMModelOptions` 是统一配置 DTO，字段包括：

| 字段 | 说明 |
|---|---|
| `provider` | 供应商级模型适配器 ID，例如 `openai`、`deepseek`。 |
| `model` | 供应商侧的具体模型名称，例如 `gpt-4.1`、`deepseek-v4-flash`。 |
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

## 3.1 文件配置默认 Options

LLM 默认模型和默认 options 不应写死在代码中。框架会读取 `config/llm/main-config.yml`，并按下面顺序合并 options：

```text
模型代码默认 options
    -> config/llm/main-config.yml 中对应模型适配器 ID 的默认 options
    -> 本次 prompt.getOptions()
```

调用方没有指定 options 时，会使用文件配置中的默认 options；调用方只指定部分 options 时，未指定字段继续沿用文件配置或代码默认值。

`LLMModelService.call(prompt)` 会优先读取 prompt options 中的 `provider` 作为模型适配器 ID。`model` 只表示本次调用的具体模型名称，不参与适配器路由。如果 prompt 未指定 provider，则使用 `default-model-id`：

```java
var result = llmModelService.call(new LLMPrompt(messages))
        .finish()
        .join();
```

文件配置示例：

```yaml
default-model-id: "deepseek"

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

  - id: "openai"
    model: "gpt-4.1"
    api-key: "${OPENAI_API_KEY}"
    base-url: "https://api.openai.com/v1"
    metadata:
      organization: ""
```

`id` 必须与模型适配器注册到 `LLMModelService` 的 ID 一致，通常就是 provider，例如 `deepseek` 或 `openai`。配置中存在未注册的 ID 不会影响启动；只有实际调用该 ID 时才会用到对应配置。

`model` 是供应商侧具体模型名称，用于覆盖模型适配器代码里的默认值。例如 DeepSeek 适配器可以在代码中默认使用 `deepseek-v4-flash`，也可以在文件配置中把该默认模型名改为其他兼容模型。

`api-key`、`base-url`、`api` 和其他供应商私有字段会进入 `LLMModelOptions.metadata`，由具体模型适配器读取。任何 API key、base URL 或供应商访问参数都应来自 file config 或数据库，不应写在模型代码中。

扩展新供应商时，必须在 README 中补充该供应商的模型适配器 ID 映射，例如：

| Provider | 默认 Model | 注册 ID |
|---|---|---|
| `deepseek` | `deepseek-v4-flash` | `deepseek` |
| `openai` | `gpt-4.1` | `openai` |

# 四. Response

统一响应由三个类组成：

| 类 | 作用 |
|:---:|---|
| `LLMResult` | 一次模型调用的完整响应，包含 id、choices、object、created、model 和 usage。 |
| `LLMChoice` | 单个候选结果，包含 index、finishReason、assistant message 和 logprobs。 |
| `LLMUsage` | token 用量信息，兼容 prompt、completion、cache hit/miss 和 reasoning tokens。 |

assistant 消息支持 `tool_calls`，tool 响应用 `LLMToolMessage` 携带 `tool_call_id` 和 tool name。

# 五. Function Tool

`LLMTool` 是当前主要工具类型。它描述“暴露给模型的函数定义”和“本地如何执行这个函数”。

| 字段 | 说明 |
|---|---|
| `type` | 工具类型，当前默认 `function`。 |
| `name` | 工具名，必须全局唯一，模型返回 tool call 时会用它定位工具。 |
| `description` | 工具说明，会暴露给模型，用于帮助模型判断何时调用。 |
| `parameters` | JSON-schema-like 参数定义。 |
| `strict` | 是否要求模型严格遵循 schema。 |
| `toolPackage` | 内部过滤字段，用于按包选择暴露给模型的工具，不参与 JSON 序列化。 |
| `arguments` | 模型生成的工具参数，通常为 JSON 字符串。 |
| `executor` | 本地执行逻辑，不参与 JSON 序列化。 |

`LLMToolService` 会注册工具、按名称执行工具，并支持按 `toolPackage` 筛选要暴露给模型的工具。当前支持两种注册方式：

| 注册方式 | 适用场景 |
|---|---|
| 手写 `ILLMTool` Bean | 需要完全控制 schema、执行器、严格模式等细节。 |
| `@LLMToolProvider` / `ILLMToolProvider` + `@LLMToolMapping` | 已经有 Spring Bean 方法，希望通过注解快速注册工具。 |

## 5.1 手写 LLMTool Bean

手写注册时，需要创建一个 Spring Bean，返回 `ILLMTool` 或 `LLMTool`。这种方式要求显式声明参数 schema 和执行器。

```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.zexnocs.teanekoagent.llm.framework.tool.LLMObjectFunctionParameter;
import org.zexnocs.teanekoagent.llm.framework.tool.LLMStringFunctionParameter;
import org.zexnocs.teanekoagent.llm.framework.tool.LLMTool;
import org.zexnocs.teanekoagent.llm.framework.tool.interfaces.ILLMTool;

import java.util.List;
import java.util.Map;

@Configuration
public class LLMToolConfig {
    @Bean
    public ILLMTool currentTimeTool() {
        return LLMTool.function(
                "get_current_time",
                "获取当前系统时间。",
                new LLMObjectFunctionParameter(
                        "获取系统时间的参数。",
                        Map.of("zoneId", new LLMStringFunctionParameter("时区 ID，例如 Asia/Shanghai。")),
                        List.of("zoneId")
                ),
                "system",
                arguments -> java.time.ZonedDateTime.now().toString()
        );
    }
}
```

如果不需要按包筛选，可以使用不带 `toolPackage` 的重载：

```java
LLMTool.function(name, description, parameters, executor);
```

执行器入参是模型返回的 JSON 字符串。手写工具需要自己解析 `arguments`，或者在执行器里调用项目已有的 `ObjectMapper`。

## 5.2 使用 LLMToolProvider 注解注册

如果工具逻辑已经存在于某个 Spring Bean 中，可以使用 `@LLMToolProvider` 标注类，再使用 `@LLMToolMapping` 标注需要注册的方法。

```java
import org.zexnocs.teanekoagent.llm.framework.tool.api.LLMToolMapping;
import org.zexnocs.teanekoagent.llm.framework.tool.api.LLMToolProvider;
import org.zexnocs.teanekocore.framework.description.Description;

import java.time.ZoneId;
import java.time.ZonedDateTime;

@LLMToolProvider(toolPackage = "system")
public class SystemLLMTools {
    @LLMToolMapping("get_current_time")
    @Description("获取当前系统时间。")
    public String getCurrentTime(
            @Description("时区 ID，例如 Asia/Shanghai。") String zoneId) {
        return ZonedDateTime.now(ZoneId.of(zoneId)).toString();
    }
}
```

注解式注册的规则：

| 注解/字段 | 作用 |
|---|---|
| `@LLMToolProvider(toolPackage = "...")` | 声明该 Spring Bean 会提供 LLM Tool，并设置该类下工具的默认包。 |
| `@LLMToolMapping("tool_name")` | 把方法注册为工具；未写名称时默认使用方法名。 |
| `@LLMToolMapping(description = "...")` | 设置工具描述；未写时读取方法上的 `@Description`。 |
| `@LLMToolMapping(toolPackage = "...")` | 覆盖类级默认包。 |
| `@LLMToolMapping(strict = true)` | 开启严格 schema 模式。 |
| `@Description` 标注方法 | 作为工具描述。 |
| `@Description` 标注方法参数 | 作为参数描述。 |

工具参数会根据方法签名自动生成：

| Java 类型 | 生成的 Function Parameter |
|---|---|
| `String` / `char` / 时间类型 / `UUID` / `URI` | `string` |
| `boolean` / `Boolean` | `boolean` |
| 整数类型 | `integer` |
| 浮点数 / `BigDecimal` | `number` |
| `enum` | 带 `enum` 值的 `string` |
| 数组 / `Collection<T>` | `array` |
| `Map` / `Object` | 允许额外属性的 `object` |
| 普通 POJO | 根据字段递归生成 `object` |
| `Optional<T>` / `@Nullable` 参数 | 不加入 required 列表 |

参数名来自 Java 反射的 `Parameter#getName()`，因此构建脚本需要保留方法参数名；当前项目已在 `build.gradle.kts` 中为 `JavaCompile` 配置 `-parameters`。

## 5.3 使用 ILLMToolProvider 接口注册

如果不想使用 `@LLMToolProvider`，也可以让 Spring Bean 实现 `ILLMToolProvider`，并在工具方法上使用 `@LLMToolMapping`。

```java
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.llm.framework.tool.api.LLMToolMapping;
import org.zexnocs.teanekoagent.llm.framework.tool.interfaces.ILLMToolProvider;
import org.zexnocs.teanekocore.framework.description.Description;

@Service
public class SystemLLMTools implements ILLMToolProvider {
    @Override
    public String getLLMToolPackage() {
        return "system";
    }

    @LLMToolMapping("get_current_time")
    @Description("获取当前系统时间。")
    public String getCurrentTime() {
        return java.time.ZonedDateTime.now().toString();
    }
}
```

这种方式适合类已经有其他 Spring 注解，或者希望通过接口统一约束工具提供者的场景。

## 5.4 按包暴露工具

工具注册后不代表一定暴露给模型。调用模型前，可以通过 `LLMToolService` 按包选择工具：

```java
// 只暴露 system 包里的工具
var systemTools = llmToolService.getToolList("system");

// 暴露所有已注册工具
var allTools = llmToolService.getToolList("all");

// null 或空字符串表示不暴露任何工具
var noTools = llmToolService.getToolList("");
```

`toolPackage` 的约定：

| 取值 | 含义 |
|---|---|
| `"all"` | 选择所有已注册工具。 |
| `null` / `""` | 不暴露任何工具。 |
| 其他字符串 | 只暴露 `toolPackage` 完全匹配的工具。 |

将筛选出的工具放入模型调用选项：

```java
var prompt = new LLMPrompt(
        messages,
        LLMModelOptions.builder()
                .tools(llmToolService.getToolList("system"))
                .toolChoice("auto")
                .build()
);
```

`toolPackage` 是框架内部过滤字段，不会序列化进供应商的 Function Tool Schema。

## 5.5 执行模型返回的 Tool Call

当模型响应中包含 `tool_calls` 时，可以把每个 `ILLMToolCall` 交给 `LLMToolService` 执行：

```java
for (var toolCall : assistantMessage.getToolCalls()) {
    String result = llmToolService.call(toolCall);
    // 将 result 包装成 LLMToolMessage，再继续发给模型。
}
```

注解式工具方法返回 `String` 时会直接作为工具结果；返回其他对象时，框架会使用 `ObjectMapper` 序列化为 JSON 字符串。

# 六. 供应商适配器职责

| 职责 | 说明 |
|---|---|
| 请求转换 | 把 `ILLMPrompt`、`ILLMMessage`、`LLMModelOptions` 转成供应商请求体。 |
| Tool 转换 | 把 `ILLMTool` 和 `ILLMFunctionParameter` 转成供应商要求的 function schema。 |
| 响应转换 | 把供应商返回值转成 `LLMResult`、`LLMChoice`、`LLMUsage` 和 `LLMToolCall`。 |
| 错误处理 | 将供应商错误转成清晰的异常，交由上层任务或日志系统处理。 |

# 七. 模型适配器 ID 映射

每个模型 Bean 注册到 `LLMModelService` 时，ID 都是供应商级 ID，通常与 provider 相同。具体模型名称由模型实现中的默认 `model`、`config/llm/main-config.yml` 的 `models[].model` 或本次 prompt options 覆盖。

新增供应商或新增模型适配器时，需要在这里维护映射，方便 `config/llm/main-config.yml` 正确引用。

| Provider | 默认 Model | 注册 ID | 说明 |
|---|---|---|---|
| `deepseek` | `deepseek-v4-flash` | `deepseek` | 示例映射；实际以已注册 Bean 为准。 |
| `openai` | `gpt-4.1` | `openai` | 示例映射；实际以已注册 Bean 为准。 |
