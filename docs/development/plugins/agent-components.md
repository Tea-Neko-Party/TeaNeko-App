# 可装配的 Agent 组件

v4.8.0-l 起，新增面向聊天装配的能力优先使用 `IAgentComponent`。原 `AgentFeature` 仍用于旧实例、底层能力和异步环绕，不作为组件参数的持久化格式。

## 职责

- Entity 保存本轮身份、装配摘要、组件参数和运行结果，不保存服务、执行器或连接。
- Component 声明默认参数、字段、校验、独占数据键及系统。原文序列化到 cfg，公共实体仅保存不可变、脱敏参数。
- System 由 Spring 管理，实现 `IEntitySystem`，声明读写键及顺序，返回真实工作的 `CompletionStage<SystemResult>`。存储和协议继续使用业务端口。

内置能力位于 `teanekoagent.component`，按 `llm/memory/personality/tool/skill/expression/sticker/schedule/proactive` 分类。每个能力的参数、系统、领域服务、存储适配和专属配置一并放入对应目录，不只放 Component/System 类；插件可使用自己的 `component` 包。共享生命周期位于 `runtime`，具体客户端适配位于 `teanekoclient/<客户端>/agent`，完整边界见[项目分层](../project-layers.md)。

| 阶段 | 用途 |
| --- | --- |
| `BEFORE_PROMPT` | 准入后初始化、解析人格与准备资料 |
| `BEFORE_INTERACTION` | 输入固定后，模型调用前等待历史采集 |
| `AFTER_INTERACTION` | 模型/工具返回后校验或编译表达计划 |
| `BEFORE_DELIVERY` | 语义投影完成后、正式交付前提交历史 |

宿主保留准入、模型循环、结果提交及资源关闭。System 不修改宿主授权或交互结果，不另建预算或重试框架。异步工作必须由返回阶段表示真实完成，宿主等待后提交补丁。

## 最小声明

先注册自己的 `PluginModule`，再提供组件及 `@PluginOwned`。组件注解本身是 Spring `@Component`，`id` 不覆盖 Bean 名。

```java
/**
 * 为轮次保存独立的显示语言参数。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.8.0-l
 * @version 4.8.0-l
 */
@TeaNekoAgentComponent(id = "locale", name = "显示语言", description = "提供当前 Agent 的语言参数。")
@PluginOwned("example.locale")
public final class LocaleComponent extends DeclaredAgentComponent {
    /** 创建默认语言及表单描述，不读取当前聊天。 */
    public LocaleComponent() {
        super("locale", Map.of("language", "中文"), List.of(
                new Field("language", "语言", "系统输入的语言名称。", "TEXT")));
    }
}
```

导入 `org.zexnocs.teanekoagent.component.*`、`PluginOwned` 及 Java 集合类型。装配加入 `"locale":{"language":"中文"}` 后，Web 自动提供参数表单，无需中央分支或前端清单。示例只提供参数；回复行为须由贡献的系统或资料实现。

## 参数、系统与来源

`DeclaredAgentComponent` 复用类型校验、默认值合并和 `key()`。系统通过 `snapshot.require(key()).read()` 取得本轮参数。业务校验覆写 `validate` 并先调用父方法；字段支持 `BOOLEAN/NUMBER/TEXT/LONG_TEXT/JSON/SECRET`。秘密不能提供真实默认值，不暴露为系统参数，需由受保护业务端口处理。

构造注入 System Bean，在 `systems()` 返回阶段配对。`keys()` 声明全部自有键，初始化及系统写入均不得越界。键与 System ID 使用 `agent.component.<组件id>.` 前缀；`after` 只引用同阶段系统。注解 `requires` 声明必需组件，配置须显式安装，运行时不偷偷补齐。

组件目录会在初始化时创建组件并读取声明。组件与 System 的构造及声明方法只提供元数据，不访问运行服务；有作用域策略或组件目录依赖的业务服务通过 `ObjectProvider` 注入，在 `execute` 或资料构建、复核阶段取得。声明阶段调用 `getObject()` 仍会产生依赖环。运行时按类型取得已管理的单例，不重新扫描，也不缓存权限判断。

内置可编译参考：

- [人格组件](../../../src/main/java/org/zexnocs/teanekoagent/component/personality/PersonalityComponent.java)与[解析系统](../../../src/main/java/org/zexnocs/teanekoagent/component/personality/PersonalityResolveSystem.java)：独立参数、结果键。
- [记忆组件](../../../src/main/java/org/zexnocs/teanekoagent/component/memory/MemoryComponent.java)：两个明确阶段使用原存储端口。
- [表达系统](../../../src/main/java/org/zexnocs/teanekoagent/component/expression/ExpressionCompileSystem.java)：异步编译后提交自有计划，发送仍由宿主控制。

组件可覆写 `tools()`、`prompts()`、`promptNames()`；工具还需要 `tool`，资料名须唯一。复用旧 `AgentFeature` 用 `features()` 声明依赖，不重复实例化服务。插件参数从组件读取，不向 `AgentScopeConfigData` 加平铺字段。

组件拥有的资料服务若作为独立 Spring Bean，须实现 `IAgentComponentPromptSource`，并由组件的 `prompts()` 返回。它只通过组件选择和插件守卫执行，不参与宿主全局来源选择。独立宿主仍可提供一个普通 `IAgentPromptSource`；多个宿主来源必须显式组合，启动冲突会列出来源类型。验证装配时应包含 `AgentExecutionConfig` 创建的实际 `AgentService`，仅创建目录不能验证完整服务组合。

重复标识、未知依赖、依赖环及越权写入均被拒绝。插件禁用、参数变化使旧选择失效；注册不等于启用，参数不授予跨聊天权限。单例组件和系统不保存用户可变状态。

## 序列化与迁移

只持久化 `version/components`，系统由声明确定，不反序列化类名、服务或代码。注解声明参数协议版本，当前支持 1。管理员格式与用法见[装配说明](../agent-composition.md)。

本版将记忆、人格、LLM、历史、摘要、技能、工具、表达、表情及调度的完整实现移到对应 `component` 子包；`AgentComponents/AgentPluginSelection` 及插件版本守卫系统也归入 `component`。实例、轮次、邮箱和通用工作流位于 `runtime`；原生消息、命令、配置与 Web 通用接入位于 `integration`。引用者须更新 import 并重新编译，组件键及持久标识不变；内部 Client/Sender/Response 迁移见[项目分层](../project-layers.md#内部客户端为什么保留)。

旧 `AgentDefinition` 构造保留旧能力语义。聊天装配固定到轮次定义，不修改共享 `AgentHandle`。事件与异步环绕继续使用[Agent 插件契约](agent.md)。
