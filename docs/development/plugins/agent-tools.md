# 注解式 Agent 工具

v4.8.0-l 的显式装配须安装 `tool` 才会选入工具。`@TeaNekoAgentToolClass(requires = {"memory"})` 可进一步要求业务组件；未安装时不解析或执行这些工具。客户端、条件和权限约束继续保留。“API 文档 → 工具目录”展示注解参数，不代表当前聊天已获授权。

适用版本：v4.8.0-l。普通工具使用两个注解即可注册，一个工具类只声明一个工具方法，不必实现工具接口或手写 JSON schema。业务类必须已是 Spring Bean，例如使用 `@Service`、`@Component` 或返回具体类型的 `@Bean`。

## 最小示例

```java
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.component.tool.api.TeaNekoAgentTool;
import org.zexnocs.teanekoagent.component.tool.api.TeaNekoAgentToolClass;
import org.zexnocs.teanekocore.command.api.DefaultValue;
import org.zexnocs.teanekocore.framework.description.Description;

/**
 * 提供文本重复工具。
 * @author zExNocs
 * @date 2026/11/05
 * @since 4.8.0-l
 * @version 4.8.0-l
 */
@Service
@TeaNekoAgentToolClass("text-utils")
public class TextRepeatTool {
    /**
     * 返回指定次数的文字，实际业务限制仍由方法校验。
     * @param text 需要重复的文字
     * @param count 重复次数
     * @return 重复结果
     * @throws IllegalArgumentException 当次数或文字长度超限时
     */
    @TeaNekoAgentTool("text_repeat")
    @Description("按指定次数重复文字。")
    public String repeat(@Description("需要重复的文字，最多一百字。") String text,
                         @DefaultValue("2") @Description("重复次数，范围一到五。") int count) {
        if (count < 1 || count > 5 || text.length() > 100) {
            throw new IllegalArgumentException("文字或重复次数超出范围。");
        }
        return text.repeat(count);
    }
}
```

该工具归属自动能力 `teaneko.agent:text-utils`。默认安装到沿用宿主默认能力的实例；显式选择 features 的实例须包含这个 key。实例仍须允许 `AgentActivity.TOOL`，并通过原 scope 开关、准入及预算检查。

生产注册表在初始化或显式重扫时编译类注解、方法规格、参数绑定规则和代理调用目录；每轮只读缓存，权限、组件装配和客户端状态仍实时复核。参数改名、默认值及 DTO 展开也不在执行期重新读取注解。独立宿主直接使用 `TeaNekoAgentToolMethods.select` 前，须在初始化时对实际 Bean 调用 `TeaNekoAgentToolMethods.prepare(bean)`；不能在消息路径临时扫描。未准备的类型明确报错，保留 Spring 代理调用，不绕过拦截器。

独立宿主按实际 Bean 身份绑定预编译声明，不按代理 class 区分提供者；同一接口的多个 JDK 代理可有不同工具名、参数、客户端和组件要求，不会互相覆盖。替换 Bean 后须在低频装配阶段准备新实例，不能借旧代理的绑定运行。

业务和条件方法可以从父类继承。v4.8.3-l 的调用候选随每个提供者规格独立编译，共用父类方法不会串用不同代理接口；JDK 代理仍须公开相应方法。同一注册目录内的工具名继续要求唯一。

类上的 value 可省略，宿主按真实类型和 Bean 名生成稳定 ID；需要从实例配置引用时建议明确命名。方法上的 value 可省略，默认使用 Java 方法名。工具名称必须全局唯一，为 1–64 位字母、数字、下划线或短横线；能力 ID 则使用小写字母开头、最长 32 位的字母、数字、短横线。

## 注解与参数

| 注解 | 用法 |
| --- | --- |
| `@TeaNekoAgentToolClass` | 标记已有 Spring Bean；设置 value、pluginId、toolPackage、client 和 defaultEnabled |
| `@TeaNekoAgentTool` | 导出 public 实例方法；设置工具名、client、condition 或 strict |
| `@Description` | 方法上的文字成为工具说明；参数及 DTO 字段上的文字成为字段说明 |
| `@DefaultValue("…")` | 参数可省略，实际调用时按声明类型转换默认字符串，适用于文字、数字、布尔、枚举等标量 |
| `@Nullable` | 引用参数可省略，实际传入 null；可选数字应使用包装类型 |
| `@TeaNekoAgentToolParam("参数名")` | 为模型字段改名；未指定时使用编译保留的 Java 参数名 |
| `@TeaNekoAgentToolParam(required = false)` | 省略时传 null，基本类型必须另有 DefaultValue |
| `@TeaNekoAgentToolParam(allowedValues = {"sender", "replied"})` | 限制字符串枚举；已有 Java 枚举直接使用枚举类型即可 |
| `@TeaNekoAgentToolParam(flatten = true)` | 将唯一业务 DTO 的字段展开为根参数；可同时注入宿主上下文 |

参数解析复用既有 LLM schema 工厂，支持 UUID、枚举、数组、集合和 DTO。DTO 字段按 Java 名称生成 schema，避免用只改变序列化名称的注解使 schema 与数据不一致。忽略字段可用 Core 的 Ignore。展开 DTO 的实际示例见 [AgentMemoryToolSource](../../../src/main/java/org/zexnocs/teanekoagent/component/memory/adapter/AgentMemoryToolSource.java)。

可选表示字段可省略；当前 schema 不允许显式 JSON null。默认值必须可转换且满足业务约束。schema 校验类型、必填、枚举及未知字段；范围、授权、预算和幂等仍由业务方法及原领域服务校验。

## 按 client 限制

方法使用 `@TeaNekoAgentTool(client = "onebot")`，或类使用 `@TeaNekoAgentToolClass(client = {"onebot", "其他客户端"})`。方法未指定则沿用类范围；两者均未指定时所有 client 可用。方法可以缩小类的范围，不能扩大；空白标识或非法范围在注册时拒绝。

client 是实际请求中的客户端标识，不能由模型参数指定。客户端可用不代表来源、用户、scope 或业务权限已经通过；平台工具继续检查真实来源租约。

## 可信上下文与动态准入

方法可直接声明以下参数，它们由宿主注入，不进入模型 schema：

| 类型 | 内容与用途 |
| --- | --- |
| `TeaNekoAgentToolContext` | 固定 definition、request、可选 runtime 和实际 input；通过 requireInvocation() 借用执行控制，通过 requireCall() 取得原调用 ID |
| `ToolInvocationContext` | 当前 requestId、只读组件快照和借用控制 |
| `ILLMToolCall` | 原工具调用，副作用幂等应保留它的 ID |

完整包名分别为 `org.zexnocs.teanekoagent.component.tool`、`org.zexnocs.teanekoagent.component.llm.framework.tool` 和后者的 `interfaces` 子包。控制由宿主管理，工具不得关闭它或将上下文存入单例字段。

动态工具设置 `condition = "canRead"`，类中提供 `public boolean canRead(TeaNekoAgentToolContext context)`。选择阶段及实际业务调用前后均检查该条件；执行条件撤销会停止整轮交互，已取到的内容不会交付模型。条件必须是无副作用的快速准入读取；选择阶段没有 call/invocation，不能在其中调用 requireInvocation()。依赖资料的工具根据 context.input() 中实际保留的片段决定可见性。

业务仍须调用原授权服务；参考 [InfoUserMessagesTool](../../../src/main/java/org/zexnocs/teanekoplugin/general/info/InfoUserMessagesTool.java) 的独立留言工具及其复用的 DEBUG 检查，以及 [AgentCommandToolSource](../../../src/main/java/org/zexnocs/teanekoagent/integration/command/AgentCommandToolSource.java) 的原命令链。不从业务 JSON 接收 actor、角色或可信 scope。

## 返回值与异步

- String 原样作为结果；普通 DTO 转 JSON；void/null 业务值转为空字符串。
- CompletionStage 或 CompletableFuture 等待真实完成再处理结果；声明阶段却返回 null 会失败。返回完整组合阶段，不启动后台工作后提前报告成功。
- 图像工具明确返回 `LLMToolOutput` 或 `CompletionStage<LLMToolOutput>`；使用 `new LLMToolOutput(文字, List<ImageLLMContentPart>)`。普通 JSON 里的 URL 不会自动成为视觉输入。
- 图像结果沿用原数量、容量、视觉模型能力及历史投影检查；参考 [OnebotMessageTool.getMessage](../../../src/main/java/org/zexnocs/teanekoclient/onebot/agent/OnebotMessageTool.java)，来源校验和图像投影集中在 [OnebotAgentSupport](../../../src/main/java/org/zexnocs/teanekoclient/onebot/agent/OnebotAgentSupport.java)。

业务方法、完成检查和序列化使用宿主借用的业务执行器，不创建第二套线程池。取消/超时结束等待并拒绝迟到结果，不承诺强制中断任意同步业务或回滚外部副作用。业务适配器继续按原控制释放实际 IO 资源。

执行通过 Spring 代理，保留事务及环绕；使用 JDK 接口代理时，工具及条件方法必须能从代理接口调用。不能为了暴露方法而解包代理目标。

## 插件归属与高级入口

普通应用工具默认归属 `teaneko.agent`。独立插件提供 PluginModule，并在类上用 `pluginId` 或 Core `@PluginOwned` 声明所属插件；宿主核对插件注册、依赖、逻辑开关和版本。仅希望显式安装时设置 `defaultEnabled = false`，然后在 AgentDefinition.features 选择自动能力。

注册时读取 Bean 类型元数据，实际选择才取得业务 Bean。工厂方法应返回可识别的具体提供者类型；无法从接口返回类型预知的懒 FactoryBean 产品不属于此自动发现入口。注册后动态添加新 Bean 不会重建既有能力目录。

内置记忆、历史、命令、平台和资料工具使用独立的注解提供者；既有组合能力使用 `managedByFeature = true`，由原 Feature 显式调用同一 TeaNekoAgentToolMethods 解析器，保持能力 ID 和 Prompt 归属。普通新工具保持默认 false；自动能力与既有能力重名会在注册阶段报错。

一个工具类只声明一个 `@TeaNekoAgentTool` 方法；查询、授权和转换共用领域服务。同一能力下的多个工具类可使用相同的类级 `value`，但工具名必须不同。平台实现放在 `teanekoclient/<客户端>/agent`，由客户端主动适配 Agent，不要求底层组件写死平台能力标识。

需要同时贡献平台参考资料时，由 `AgentFeature` 持有工具和 Prompt 端口。其 `assemblyRequirements()` 可返回 `Set.of("tool")`，仅当这些组件全部装配、`defaultEnabled()` 为真且所属插件启用时自动接入；空集合不触发此规则。旧实例和显式能力选择保留原行为。共享支持 Bean 不应另外实现宿主附加来源接口，避免同一资料重复装配。

依赖动态后端或真实 Skill 目录的工具继续使用底层 LLMToolBinding。组件、System、资料与事件的组合方式见 [Agent 插件指南](agent.md)；只新增普通工具时无需实现这些接口。
