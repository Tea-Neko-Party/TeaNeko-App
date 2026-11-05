# 类型化 JSON 输出

`LLMJsonService` 按 Java 类型生成结构说明、开启 JSON 选项，并异步返回该类型。它复用原模型路由、预算、动态准入和取消控制，不自动重试，不提交业务数据。

## 使用

根类型加 `@LLMJsonType`，类与字段用 Core 的 `@Description` 说明含义及约束。推荐使用记录类型，构造方法校验长度、范围及字段关系。

```java
@LLMJsonType(maxCharacters = 8000)
public record Summary(
        @Description("非空简短摘要，最多600字。") String brief,
        @Description("非空详细摘要，最多4000字。") String details) { }

CompletionStage<Summary> result = json.generate(
        Summary.class, prompt, context, settings, guard);
```

`json` 由 Spring 注入；`context`、`settings` 和 `guard` 沿用宿主已有的身份、执行控制及准入检查。示例省略了记录构造中的业务校验。

若调用前还有本地输入预算检查，先 `prepare(Summary.class, prompt)`，对返回的 `request.prompt()` 估算，再调用 `generate(request, context, settings, guard, observer)`。观察者在解码前取得真实模型状态及用量；失败不丢失已消耗额度。

`LLMJsonSchemas` 在初始化或显式重扫时发现类型并原子发布合同。请求只查缓存，不懒扫描。支持具名对象、具体字段、嵌套对象、数组/有元素类型的集合、枚举、UUID，以及 `@Nullable`；支持字段上的 `@JsonProperty`、`@JsonIgnore`。拒绝无界 Map、循环引用和未绑定泛型，不把它们伪装成空对象。自定义序列化方式应与字段声明一致。

所有字段都须出现；可空字段写 `null`，不能省略。Schema 不替代构造约束、证据核验、权限和事务检查。

## 模型适配

通用选项为 `LLMResponseFormat.JSON` 与不可变 `responseSchema`。适配器通过 `ILLMModel.getJsonOutputSupport(model)` 声明能力，未知实现默认 `NONE`。

| 能力 | 实际请求 | 本地处理 |
| --- | --- | --- |
| `SCHEMA` | Chat Completions 的 `response_format.json_schema`，或 Responses 的 `text.format`，包含 `strict=true` | 仍严格解码并校验领域类型 |
| `OBJECT` | `response_format={"type":"json_object"}` | 完整 Schema 同时放入提示；本地检查字段和类型 |
| `NONE` | 不发送原生 JSON 约束 | 实际调用时警告，保留结构提示及严格解码 |

默认 DeepSeek、Kimi 适配对象模式；OpenAI Responses 适配 Schema；Chat Completions 按已知模型能力选择。自定义服务须按其实际支持声明，远端拒绝继续报告真实 HTTP 状态，不暗中换模型或重试。

JSON 对象模式只约束 JSON 格式，不保证符合字段结构。Schema 模式也可能遇到拒答或截断，必须检查模型终态。[OpenAI 官方说明](https://developers.openai.com/api/docs/guides/structured-outputs)、[DeepSeek 官方说明](https://api-docs.deepseek.com/guides/json_mode/)。

## 已迁移功能

| 功能 | 输出类型 | 业务边界 |
| --- | --- | --- |
| 表情包视觉分类 | `Stickers.Analysis` | 类型、质量及类别仍由采集策略复核 |
| 中期摘要 | `MemoryLayers.Digest` | 不推进失败批次的整理水位 |
| 长期事实及风格观察 | `StyleObservations.Analysis` | 统一 `memories/styles` 对象；无人格目标时 `styles=[]` |
| 独立人格学习 | `PersonalityLearningOutput` | `adjustment=null` 表示不学习；字段白名单与提交代次不变 |
| 主动发言 | `ProactiveOutput` | 仍经原 Agent 管道，使用同一结构提示、JSON 选项及解码 |
| 可选旧回复信封 | `LegacyReplyOutput` | 静态初始化合同并复用 `LLMJsonCodec`；不强制普通聊天输出 JSON |

纯文本会话摘要不需要 JSON；工具参数、宿主工具结果、配置与数据库 JSON 属于其他协议，不改为模型生成入口。

## 诊断

`LLMJsonException` 区分模型失败、缺少正文、输出超限、JSON 语法、字段结构与领域绑定错误。日志包含真实模型状态、请求号、已知字段路径或行列、字符数、摘要及隐藏字段值后的原输出结构。不补括号、不剥围栏、不忽略额外字段，也不把无效输出当空候选。

需要核对原文时，可在**本地启动配置**中临时开启：

```properties
tea-neko.llm.json-diagnostics.enabled=true
# 可选；默认相对后端工作目录，不应放到静态资源目录
tea-neko.llm.json-diagnostics.directory=data/diagnostics/llm-json
```

默认关闭。仅统一生成服务的解码失败会归档，日志给出对应文件名；不会保存请求、密钥、推理过程或供应商错误正文。文件只允许宿主所有者访问，每份最多 262144 字符、最多 20 份；后续写入时清理超过 24 小时的本类文件。超过容量会明确标注截断。目录不对 Web 提供下载；无法设置安全权限时不写原文。

原文仍可能包含聊天隐私，排查后关闭配置并按本机数据保留要求清理。此开关不适用于普通聊天的静态旧信封解析；该路径只提供脱敏结构诊断。历史日志未记录的原文无法恢复。
