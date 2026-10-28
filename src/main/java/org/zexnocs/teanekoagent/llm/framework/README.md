# 一. LLM API Framework 结构介绍

`llm/framework` 是 TeaNeko Agent 的大语言模型 API 中间层。它不绑定 OpenAI、DeepSeek 或其他供应商协议，而是提供统一的 Prompt、Options、Response、Model Registry 和 Function Tool 抽象。

新增供应商时，需要实现 `ILLMModel` 并在模型类上添加 `@LLMModel(id = "...")`。只有同时满足注解和接口约束的 Bean 才会注册；如果需要复用工具调用，再实现或注册 `ILLMTool` Bean。

| 模块 | 作用 |
|:---:|---|
| [`message`](message/README.md) | 定义 system/user/assistant/tool 消息、内容片段、组成关系和 JSON 序列化规则。 |
| `model` | 定义供应商级模型适配器 ID、模型 options、模型服务和模型适配器基类。 |
| `response` | 定义统一响应结果、choice 和 usage。 |
| [`tool`](tool/README.md) | Function Tool/schema、工具注册、本轮可信绑定及异步执行。 |
| `../file_config` | 解析本地模型参数；默认路由与凭据通过独立端口注入。 |
| [`interaction`](interaction/README.md) | 有限自动往返、共享次数/token 预算、预留结算、阶段控制、不可变 transcript 和逐次 usage。 |
| `interfaces` | Prompt、Result、Choice、Usage 等公共接口。 |

# 二. 模型接入

推荐继承 `AbstractLLMModel`，使用 `@LLMModel` 声明注册 ID，并实现 `doCall(...)`：

```java
@LLMModel(id = "deepseek")
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

`LLMModelService` 只扫描带有 `@LLMModel` 且实现 `ILLMModel` 的 Bean，并仅使用注解的 `id` 作为注册 key。`getProvider()` 仍用于调用参数和供应商日志，不参与注册：

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

|术语|来源|API|说明|
|---|---|---|---|
|base options|模型代码|`ILLMModel.getBaseOptions()`、供应商 `baseOptions()`|协议和参数基础值；模型服务仍要求本地配置显式指定 model。|
|default options|`config/agent/model.yml`|`ILLMModelService.getDefaultOptions(...)`|文件配置覆盖 base options 后得到的部署默认值。|
|prompt options|单次 `ILLMPrompt`|`ILLMPrompt.getOptions()`|仅覆盖本次模型调用。|
|effective options|模型调用阶段|内部合并结果|按 base、default、prompt 的顺序合并，并强制写回路由 provider。|

| 字段 | 说明 |
|---|---|
| `provider` | 供应商级模型适配器 ID，例如 `openai`、`deepseek`。 |
| `model` | 供应商侧的具体模型名称，例如 `gpt-5.5`、`deepseek-v4-flash`。 |
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

模型代码只声明可运行的 base options；可部署环境的 default options 由 `config/agent/model.yml` 提供。框架按下面顺序合并 options：

```text
模型代码 base options
    -> config/agent/model.yml 中对应模型适配器 ID 的 default options
    -> 本次 prompt.getOptions()
```

调用方没有指定 options 时，会使用文件配置中的 default options；调用方只指定部分 options 时，未指定字段继续沿用文件 default options 或模型 base options。

`LLMModelService.call(prompt)` 会优先读取 prompt options 中的 `provider` 作为模型适配器 ID。`model` 只表示本次调用的具体模型名称，不参与适配器路由。如果 prompt 未指定 provider，则使用 `config/agent/main-config.yml` 中的 `default-model-id`：

```java
var result = llmModelService.call(new LLMPrompt(messages))
        .finish()
        .join();
```

Agent 主配置示例：

```yaml
default-model-id: "deepseek"
```

模型默认参数配置示例：

```yaml

models:
  - id: "deepseek"
    model: "deepseek-v4-flash"
    temperature: 0.7
    max-tokens: 2048
    metadata:
      deepseek.reasoningEffort: "medium"
      deepseek.topLogprobs: 5
      deepseek.streamIncludeUsage: true

  - id: "openai"
    model: "gpt-5.5"
    metadata:
      openai.reasoningEffort: "medium"
      openai.reasoningSummary: "auto"
      openai.store: false

```

`id` 必须与模型类 `@LLMModel(id = "...")` 声明的注册 ID 一致。配置中存在未注册的 ID 不会影响启动；只有实际调用该 ID 时才会用到对应配置。

`model` 是供应商侧具体模型名称，用于覆盖模型适配器代码里的默认值。例如 DeepSeek 适配器可以在代码中默认使用 `deepseek-v4-flash`，也可以在文件配置中把该默认模型名改为其他兼容模型。

密钥只由 `ICredentialResolver` 在 HTTP 请求边界从 Spring application/环境变量解析，不进入 Options、Prompt 或组件。连接覆盖也只取 Spring 配置；Prompt 中的 endpoint 不会改变实际请求地址。旧 model.yml 中的 api-key/api/base-url 必须迁移，见 [配置与迁移说明](../file_config/README.md)。

`IDefaultModelResolver` 属于 LLM 层，当前装配适配器读取 main-config.yml。文件配置不可用、模型条目缺失或模型名为空时明确失败，不静默回退。`call` 保持单次请求语义；工具返回后的再调用由上层交互服务负责。

扩展新供应商时，必须在 README 中补充该供应商的模型适配器 ID 映射，例如：

| Provider | 默认 Model | 注册 ID |
|---|---|---|
| `deepseek` | `deepseek-v4-flash` | `deepseek` |
| `openai` | `gpt-5.5` | `openai` |
| `kimi` | `kimi-k2.6` | `kimi` |

## 3.2 具体模型输入能力

`ILLMModel.getCapabilities(model)` 返回不可变的 `ModelCapabilities`。能力针对本次合并后的具体模型名，默认没有声明图像能力；新供应商可覆盖该方法，不需要导入 App 或 Agent 类型。模型服务与 AbstractLLMModel 的直接调用均在认证/HTTP 前检查内容、角色和图像细节级别。

当前已核实的支持范围见 [模型文件配置说明](../file_config/README.md#图像能力)。统一内容与构造方式见 [消息说明](message/README.md#五-content-结构)。本地配置继续选择默认模型，不因能力表而自动切换供应商或模型。

`body.*` 只能补充尚未建模的供应商字段，不能重复定义请求 DTO 的字段，也不能覆盖 model/messages/input/tools/stream、token 上限或服务端上下文引用。公共字段通过统一/供应商 Options 设置。Responses 的透明 provider state 只回放 reasoning item，不能把任意原生 message 或工具调用隐藏在该字段中。

## 3.3 单次调用的执行策略

模型服务新增 `call(modelId, prompt, execution)` 和 `call(prompt, execution)`；模型适配器新增 `call(prompt, execution)`。execution 使用 Core 的 ExecutionPolicy，通过独立参数传递：

```java
var execution = ExecutionPolicy.controlled(Duration.ofSeconds(60), control);
var response = llmModelService.call(LLMModelId.of("deepseek"), prompt, execution);
response.asStage().whenComplete((result, failure) -> { /* 消费最终结果 */ });
```

阶段时限与整轮剩余时间共同约束实际 HTTP；取消会结束返回链并释放订阅。阶段超时使用 APIRequestTimeoutException，整轮终止保留 ExecutionStoppedException 原因。调用前已经终止时，LLM 的参数准备入口同步拒绝，不解析路由/凭据或发出请求；已经启动的请求通过 TaskFuture 异常完成。

内置 DeepSeek、OpenAI Responses、Kimi 和未注册的 OpenAI Chat Completions 实现均已接通策略。自定义适配器需要实现执行策略重载，旧接口默认明确拒绝，不静默退回不受控调用。策略不放入模型 Options、Prompt 内容、工具参数或请求 JSON；单次调用仍不执行工具循环，HTTP retryCount=0，读写缓存均禁用。

原有不带策略的调用保留注解默认时限。后续交互服务/S3 再按本地配置统一传入模型阶段时限与总预算；本批没有把预算、人格或记忆循环塞进 call。

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
import org.zexnocs.teanekoagent.llm.framework.tool.parameter.LLMObjectFunctionParameter;
import org.zexnocs.teanekoagent.llm.framework.tool.parameter.LLMStringFunctionParameter;
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

新交互入口使用本轮 `LLMToolSet` 执行。先由宿主选择工具、固定绑定，再把同一个集合的 `definitions()` 放入模型 options；执行时只匹配这些绑定：

```java
var tools = new LLMToolSet(objectMapper, llmToolService.getTools("allowed-package")
        .values().stream().map(LLMToolBinding::from).toList());
// invocationContext 由宿主创建，含 requestId、不可变绑定及 ExecutionControl。
CompletionStage<ToolInvocationResult> pending = tools.execute(toolCall, invocationContext);
CompletionStage<LLMToolMessage> message = pending.thenApply(ToolInvocationResult::toMessage);
```

注解式工具支持 `CompletionStage<?>` 返回值，等待完成后才转换字符串/JSON；`CompletionStage<Void>` 完成后得到空字符串。方法可声明 `ToolInvocationContext` 参数，由宿主注入且不生成 schema。普通业务参数仍按原方法签名生成 schema。

`LLMToolService.call(...)` 保留旧同步便捷语义，不验证本轮可见范围；异步或需要可信上下文的方法会在执行前明确拒绝此入口。新交互链应使用 `LLMToolSet`，详见[工具协议与离线用例](tool/README.md)。`LLMInteractionExecution` 已在其上增加共享次数预算、末次关闭工具、顺序工具批次及独立阶段时限，使用方式见[交互阶段执行](interaction/README.md)。自动模型/工具循环与完整结果汇总留待后续编排层实现。

# 六. 供应商适配器职责

| 职责 | 说明 |
|---|---|
| 请求转换 | 把 `ILLMPrompt`、`ILLMMessage`、`LLMModelOptions` 转成供应商请求体。 |
| Tool 转换 | 把 `ILLMTool` 和 `ILLMFunctionParameter` 转成供应商要求的 function schema。 |
| 响应转换 | 把供应商返回值转成 `LLMResult`、`LLMChoice`、`LLMUsage` 和 `LLMToolCall`。 |
| 错误处理 | 将供应商错误转成清晰的异常，交由上层任务或日志系统处理。 |

# 七. 模型适配器 ID 映射

每个模型 Bean 注册到 `LLMModelService` 时，ID 仅来自 `@LLMModel.id`。具体模型名称由模型实现中的基础 `model`、`config/agent/model.yml` 的 `models[].model` 或本次 prompt options 覆盖。

新增供应商或新增模型适配器时，需要在这里维护映射，方便 `config/agent/model.yml` 正确引用。

| Provider | 默认 Model | 注册 ID | 说明 |
|---|---|---|---|
| `deepseek` | `deepseek-v4-flash` | `deepseek` | 示例映射；实际以已注册 Bean 为准。 |
| `openai` | `gpt-5.5` | `openai` | 使用 Responses API 的已注册适配器。 |
| `kimi` | `kimi-k2.6` | `kimi` | 继承 Chat Completions 通用层，只实现 Kimi 差异。 |

OpenAI Chat Completions 通用类没有 `@LLMModel`，不会注册。需要接入兼容供应商时，应继承其抽象模型，并在供应商实现类上声明独立的 `@LLMModel(id = "...")`。
