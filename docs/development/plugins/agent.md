# Agent 插件编写

## 能力包与实例选择

先提供依赖 teaneko.agent 的 PluginModule，再提供一个或多个 AgentFeature Bean。AgentFeature 是能力装配声明，不是运行服务定位器；只覆写本能力需要的成员。

| 成员 | 含义 |
| --- | --- |
| pluginId / id | 所属插件和局部能力 ID；id 为小写字母开头、最长 32 位的字母/数字/短横线 |
| key() | 插件ID:局部ID，供实例声明 |
| defaultEnabled() | 是否默认安装；第三方默认 false，安装插件不等于自动给全部实例加能力 |
| dependencies() | 依赖的能力 key，选择时自动展开；缺失或环拒绝 |
| components() | 自有组件键全集，初始化和 System 写入都不能越界 |
| instanceComponents(definition) | 工厂创建实例时的不可变配置，不访问数据库或执行副作用 |
| turnComponents(context) | 准入后的轮次独立初始数据 |
| systems() | 安排在指定阶段的普通 Core System |
| tools() | 返回 IAgentToolSource，贡献原 LLMToolBinding |
| prompts() / promptNames() | 返回 IAgentPromptSource，并声明自有资料名称 |
| promptInterceptors() | 为完整资料解析贡献类型化异步环绕 |

实例通过四参数 AgentDefinition 显式选择，例如：

```java
new AgentDefinition("业务助手", modelOptions,
        Set.of(AgentActivity.SERVICE, AgentActivity.TOOL),
        Set.of("example.clock:clock"));
```

使用宿主注入的 IAgentFactory 创建实例。默认工厂会在登记前检查能力、初始化组件并编译两个阶段的 System 依赖；失败不登记半初始化实例。原三参数定义仍安装宿主默认能力；原独立构造的无插件工厂保留，不支持静默忽略显式 features。

内置能力为 teaneko.agent:builtin-tools 和 teaneko.agent:builtin-memory。旧 IAgentToolSource/IAgentPromptSource Bean 现在代表宿主的额外总来源，不能再依赖 ConditionalOnMissingBean 替换掉内置记忆。保留一个明确总来源即可，普通插件通过 AgentFeature 追加，避免多个总来源 Bean 歧义。

## 新增工具

使用 LLMTool.builder() 定义协议名、中文描述和参数 schema，再用 LLMToolBinding(definition, executor) 固定定义与异步执行目标。tools() 返回显式选择该绑定的 IAgentToolSource。完整可编译实现见 [ExampleClockFeature.tools](../../../src/main/java/org/zexnocs/teanekoplugin/agent_example/ExampleClockFeature.java)。

执行器参数 call 只有模型业务参数；可信 requestId、实体快照和借用控制来自 ToolInvocationContext。通过 bindings().require(组件键) 读取宿主固定组件，不从 JSON 接收操作者、scope、角色、DEBUG、数据库连接或控制。

协议工具名使用供应商允许的字母/数字/下划线/短横线，建议采用插件前缀，例如 example_clock_now，不直接把带冒号的能力 key 当工具名。绑定继续由 LLMToolSet 冻结 schema、校验 JSON、参数和返回大小；本轮重名明确失败，不悄悄覆盖其他工具。

工具业务结束才完成返回 Stage。宿主继续执行每次工具的准入、总预算、超时及资料引用校验；插件不另建模型循环、根预算或重试框架。写工具按业务幂等键等待真实回执，结果不明明确报告，不因一次异常就重做副作用。

依赖某资料声明的工具使用四参数 select(definition, request, runtime, input)，检查 input.fragments 中实际保留的自有声明；被预算省略时不能暗中注册工具。示例时钟工具依赖可信组件，没有“省略资料就禁用”的业务约束。

## 新增组件及 System

组件键必须使用 `plugin.<插件ID>.<局部能力ID>.` 前缀，且在 components() 声明。例如示例的 plugin.example.clock.clock.zone 和 started-at。值必须不可变，不保存 Bean、Repository、执行器或 ExecutionControl。

instanceComponents 只创建实例配置。轮次会复制实例的不可变插件组件，在 turnComponents 或 System 中写入本轮新值，之后不回写实例；需要跨轮次状态时通过明确、按 scope/用户隔离的业务服务管理。单例 Feature/System 不保存用户可变会话字段。

System 实现 IEntitySystem，descriptor() 声明稳定 ID、required、writes、after 与 optional。ID 同样使用该能力的组件前缀，writes 必须是该能力声明的键；读取 Core 的可信组件可以，写入 ACTOR、REQUEST、预算、授权、交互或 OUTCOME 不允许。

| Phase | 实际位置 | 适合用途 |
| --- | --- | --- |
| BEFORE_PROMPT | agent.admit 之后，资料解析之前 | 创建检索上下文、业务配置投影、轮次数据 |
| AFTER_INTERACTION | 原模型/工具交互之后，历史及交付检查之前 | 业务结果校验、统计、自有结果组件 |

插件在隔离实体副本执行，由 Core 检查依赖、写入声明及版本，宿主只提交允许的插件组件补丁。after 引用同阶段的其他 System ID；跨能力协作同时声明能力依赖。required 只判断输入存在，不决定自动启用，实例选择仍是安装依据。

执行返回 SystemResult，宿主在插件阶段前后重新复核准入；STOP 按 SKIPPED 拒绝本轮交付，不表示已经回滚副作用，也不复用先前观察到的模型成功。正常回答仍由原交互和完成流程产生。System 中的数据访问使用原受控业务端口，不能自行绕过授权执行副作用。插件管线最多缓存 256 个阶段组合，超出时按需编译，不随任意实例组合无限增加缓存。

## 可监听的 Agent 事件

监听类使用 Core @EventListener 和 @PluginOwned，并按 [Core 指南](core.md) 正确装配。监听方法 public void；决策事件必须同步，远程异步处理使用下一节的异步操作接口。

| 事件 | 触发时机与用途 | 可修改内容 |
| --- | --- | --- |
| AgentPromptPreparingEvent | 所有资料及异步环绕结束后，输入预算裁剪前；适合筛选、重排或补充已声明资料 | values() 为资料快照，replace(list) 替换草稿 |
| AgentToolsSelectingEvent | 根据实际输入选择工具后，冻结工具集和开始交互前 | 仅从原候选移除或重排；新增工具使用 Feature.tools，不能注入无归属执行器 |
| AgentTurnReportEvent | 整轮终态形成后的异步监控，适合统计与观测 | payload 不可变；取消/替换事件不改变原业务结果 |
| AgentTokenAlertEvent | 原本地阈值和冷却逻辑触发告警时 | 只观察；不自动向聊天发送通知，不授予执行权限 |

前两个事件继承 AgentDecisionEvent。getData() 返回固定 AgentEventContext：

| 字段 | 数据含义 |
| --- | --- |
| requestId | 宿主本轮请求 ID，不是模型工具 callId |
| agentId | 实际运行实例的 EntityId |
| definitionId | 可共享的定义标识，不代表某个用户 |
| scope | clientId 与 scopeId 的完整可信定位，不能只按群号做跨客户端授权 |
| features | 本轮实际安装能力 key，包含默认与依赖展开结果 |

PluginOwned 监听器仅在本轮安装其所属插件的能力时参与；同插件内多个能力仍由监听器检查 features 细分。全局未声明归属的宿主监听器保留旧行为，第三方必须声明归属。

AgentPromptFragment 包含 name（自有来源名）、text（完整低信任正文）、expiresAt（可选到期）、transientToolResults（历史投影需替换正文的工具名集合）。来源名须由该能力 promptNames() 声明，全目录重名拒绝。解析器、拦截器和事件新增的资料都遵守声明；默认最多 32 个片段、总正文及最终协议字节还有原预算限制。业务名不用于取得权限。

AgentToolsSelectingEvent 的 values 为已经冻结定义的 LLMToolBinding，definition() 是模型可见 schema，实际执行器不普通导出。监听器不能改 schema 或用重新创建的“同名绑定”替换原候选。

决策拒绝使用 setCancelled(true)，后续监听器不能恢复为 false。通知无论成功失败都冻结草稿，冻结后 replace 抛错；异步监听决策事件被总线拒绝。事件本身成功完成仍须读取取消状态。宿主之后继续检查输入预算、资料来源、插件版本、当前权限和引用。

AgentTurnReport 字段：requestId、agentId、scope、activity、status 定位原轮；elapsedMillis 是完整轮次耗时；modelCalls、toolCalls 为本轮次数；reportedTokens 为报告用量、estimatedTokens 为保守估算、unknownUsageAttempts 为无可用用量次数。status 是模型轮次终态，不表示平台已经发出消息，用量不是精确费用。报告不含原文、答案、参数、异常链或密钥。AgentTokenAlertEvent.Alert 的 report 为该报告，threshold 为本地告警阈值，suppressed 为上个冷却期合并次数。

报告依赖原 AgentRuntimeMonitor 的有界异步发布，拥塞或监听失败允许丢弃，不能把它作为持久审计或完整历史数据库。异步监听只消费快照，不延长原轮次成功或失败。

## 修改异步 Stage 链

当前明确开放的是资料解析操作：promptInterceptors() 返回 `OperationPipeline.Binding<AgentPluginContext, List<AgentPromptFragment>>`。Binding 的 pluginId 必须与能力所属一致；order 越大越先进入，相同顺序由稳定标识决定。

AgentPluginContext 提供 definition、request、runtime、snapshot、control。它是调用上下文，不是组件，不保存或关闭它。next.execute 必须交回原上下文对象；不能用另一个 actor、scope 或控制替换。返回资料列表可以有序过滤和转换，但来源名仍须声明，并在事件之后接受原完整预算和来源校验。

异步前置使用 thenCompose 后再 next.execute(context)，异步后置返回 next.execute(context).thenCompose(...)，同步结果变换使用 thenApply。必须返回组合后的 Stage。资料操作不允许不调用 next 的短路；原来源不能因插件缓存而绕过复核。可编译示例是 ExampleClockFeature.promptInterceptors()。

工具环绕使用 binding.intercept(ToolInterceptor)，可以为实际调用计时或转换结果；next.call 必须传入原 call 与 trusted 对象，最多一次，不允许跳过。宿主等待实际工具完成，原实际失败不能被伪装为成功；参数修改应在显式业务适配中重新验证，而不是绕过 LLMToolSet 校验。拦截器不能关闭借用控制。

需要为新领域操作开放环绕时，在该操作端口显式组合 Core OperationPipeline，并提供类型明确的贡献接口。现有 TaskStageChain 继续只包围同步调用，不对任意私有方法进行反射 AOP。

## 资料校验与完成重放

IAgentPromptSource.resolve 返回完整资料；verify 在原模型、工具及交付检查点复核当前版本、到期及授权；verifyReplay 只读检查旧布局，动态来源默认拒绝。纯 IAgentPromptSource.empty() 明确不产生外部资料，可以通过自身的重放检查。

聚合器只把各能力 promptNames() 拥有的片段交给该能力的 verify/verifyReplay；宿主旧总来源只收到非插件片段。它们仍共同服从原全局预算。实际布局的 plugins 字段保存能力及插件依赖版本，不进入模型消息；包装或过滤 AgentPromptInput 时必须保留该字段。

插件禁用或依赖换代后，旧资料、工具执行及旧完成重放不能继续通过版本检查。组件/工具插件即使没有 Prompt 正文也被记录；因此不应把“资料为空”当作无须检查插件的依据。

## 其他扩展点

| 需要扩展 | 接入方式及边界 |
| --- | --- |
| 持久任务动作 | 提供 AgentTaskAction Bean；声明 pluginId、兼容的动作 id 和 AgentScheduleService.Action。与内置 remind 重名拒绝；复用原 Timer、存储、权限、防重及真实终态 |
| 知识检索 | 实现 IKnowledgeSource，复用 AgentExtensionTools.knowledgeSearch 及其资料/策略适配，通过 Feature 注册；继续保留来源、revision、完整性和重放检查 |
| 文件/代码后端 | 实现 IAgentFileAccess 或 IAgentCodeExecutor，经 AgentExtensionTools 生成工具；必须显式提供 IAgentExtensionPolicy，不能因注册 Bean 就开放宿主文件或 shell |
| 模型供应商 | 实现 ILLMModel，标记 @LLMModel 与 @PluginOwned；继续使用统一 model.yml、原 ExecutionPolicy 和实际 IO 取消；模型服务在完成时复核插件版本 |
| 记忆、画像、人格业务 | 复用已有服务及专用 EasyData，可贡献候选、工具和资料；最终写入仍受 namespace/generation、主体、来源、锁定和 DEBUG 管理约束 |
| Skill | 继续经现有本地目录校验/发布与 Skill 策略选择；插件功能配套的 Skill 资源按部署说明安装，不因 JAR 存在就自动发布目录或增加权限 |
| 显式工作流 | 借用已有 AgentWorkflowService 安排实例，共享原父预算；不透明地自动调用其他 Agent 不属于注册能力 |
| 管理页面 | 贡献 ManagementResource，按 [管理扩展](management.md) 提供脱敏查询或明确管理操作 |

任务动作执行前及每个副作用前等待宿主传入的 guard，使用原任务 ID 与 nextFire 做防重；返回 Outcome.COMPLETED、REJECTED 或 INDETERMINATE，不能把接受请求写成成功交付。动作 ID 格式与原持久化协议一致，最长 48 位小写字母/数字/下划线；建议带插件前缀和必要协议版本，不擅自更改旧任务含义。

本版不自动扫描所有知识后端、Skill 资源或任意候选策略。它们通过明确 Feature/既有端口组合接入，宿主仍负责实际后端、部署授权和版本选择。新增可管理 DTO 或网络适配须同时维护当前指南及相关领域文档。
