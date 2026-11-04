# Agent 插件编写

v4.8.0-l 的聊天可选能力优先使用[可装配组件](agent-components.md)，可提供独立参数并自动出现在 Web 装配页。下文的 `AgentFeature` 保留给旧实例、底层贡献及异步环绕。

## 能力包与实例选择

普通工具优先使用 [注解式 Agent 工具](agent-tools.md)，Spring Bean 加上类和方法标记即可自动注册。需要组合组件、System、资料或事件时，提供依赖 teaneko.agent 的 PluginModule，再提供 AgentFeature Bean。AgentFeature 是能力装配声明，不是运行服务定位器；只覆写本能力需要的成员。

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

内置能力包括 teaneko.agent:builtin-tools、teaneko.agent:builtin-memory、teaneko.agent:onebot 和 teaneko.agent:layered-memory。最后一项提供同 scope 持久近期与中期资料；显式选择能力集合的实例需自行加入。旧 IAgentToolSource/IAgentPromptSource Bean 代表宿主的额外总来源，不能依赖 ConditionalOnMissingBean 替换掉内置记忆。保留一个明确总来源即可，普通插件通过 AgentFeature 追加，避免多个总来源 Bean 歧义。

## 新增工具

常规工具在已有 Spring Bean 上使用 `@TeaNekoAgentToolClass`，每类只声明一个 `@TeaNekoAgentTool` 公开方法，方法和参数使用 `@Description`；宿主自动生成 schema 并注册普通能力。共享查询和授权逻辑复用服务，平台实现放在 Client 层。支持 client、DefaultValue、可信上下文、异步返回及图像；完整示例和限制见 [工具开发指南](agent-tools.md)。记忆、历史、命令、平台和资料工具已按此方式实现。

需要动态 schema 或后端策略的工具保留底层入口：使用 LLMTool.builder() 定义协议名、中文描述和参数 schema，再用 LLMToolBinding(definition, executor) 固定定义与异步执行目标。tools() 返回显式选择该绑定的 IAgentToolSource。完整可编译实现见 [ExampleClockFeature.tools](../../../src/main/java/org/zexnocs/teanekoplugin/agent_example/ExampleClockFeature.java)。

执行器参数 call 只有模型业务参数；可信 requestId、实体快照和借用控制来自 ToolInvocationContext。通过 bindings().require(组件键) 读取宿主固定组件，不从 JSON 接收操作者、scope、角色、DEBUG、数据库连接或控制。

平台专用工具使用 `new LLMToolBinding(definition, executor, Set.of("onebot"))`，或在原绑定上调用 `.forClients(Set.of("onebot"))`。不指定或使用空集合允许所有 client；已有受限绑定不能通过再次包装扩大范围。宿主在插件筛选后按请求 client 过滤模型可见工具，并在实际调用时再次核对可信 client 与请求身份。`guarded`、`guardedAsync`、`intercept` 都保留约束；独立于 Agent 使用 LLM 工具集的宿主须自行按 `supportsClient` 选择和校验。完整的平台适配见 [OneBot 工具](../../../src/main/java/org/zexnocs/teanekoclient/onebot/agent/README.md)。

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
| BEFORE_INTERACTION | 输入固定后，模型调用前 | 等待输入采集、自有准备任务 |
| AFTER_INTERACTION | 原模型/工具交互之后，历史及交付检查之前 | 业务结果校验、统计、自有结果组件 |
| BEFORE_DELIVERY | 语义历史投影后，最终交付前 | 等待历史持久化及其他已授权提交 |

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

## 表情包生命周期事件

这两项为全实例图库事件，独立于某个 AgentFeature。监听器仍使用 Core @EventListener、@EventHandler 和 @PluginOwned；安装 PluginModule 后还须在 config/agent/stickers.yml 的 event-plugins 列出插件 ID，默认无第三方参与。

| 事件 | 触发与字段 | 允许操作 |
| --- | --- | --- |
| StickerCollectingEvent | 入队前；getData() 的 proposalId 是候选去重定位，scope 为真实 client/区域，manual 是主动收藏，image 为从零起的图片序号，occurredAt 为原时间；不含图片链接、原话和作者 | public void 同步监听，suggest 最多 256 字符低信任视觉建议，setCancelled(true) 单向拒绝；不能改来源、取消后恢复或使用异步监听 |
| StickerCommittedEvent | 事务成功后；catalogId 标识实例图库，kind 为 COLLECTION_COMPLETED 或 MANAGEMENT，operationId 是原作业/管理操作 UUID，entryId 可能为空，catalogRevision 是已提交版本，at 为提交观察时间，status 为作业终态或管理动作 | 只读通知，可异步观察；不能取消或替换，不能借通知重新执行原副作用 |

```java
@EventListener
@PluginOwned("example.stickers")
public final class StickerHintListener {
    /** @param event 宿主提供的真实候选草稿 */
    @EventHandler
    public void collecting(StickerCollectingEvent event) {
        event.suggest("优先判断人物动作是否能够独立表达聊天情绪");
    }
}
```

补充提示不会改变插画排除、类型门槛、容量或当前来源权限。决策在通知结束、失败或五秒超时后冻结，迟到修改抛错；未结束的物理任务仍占有界名额。提交事件是尽力观察，可能因拥塞/失败缺失，不是可靠审计或浏览器订阅协议；消费者通过管理 SPI 查询当前状态，不能以事件回调失败推断事务回滚。文件后端、预览和刷新合同见[素材扩展文档](../../../src/main/java/org/zexnocs/teanekoagent/component/sticker/README.md#扩展开发)。

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
| 自动三层记忆 | ILayeredMemoryCompressor 与 ILongTermMemoryConsolidator 可分别替换摘要和长期选择；复用单个 LayeredMemoryProcessor。替换 ILongTermMemoryStore 仍须实现证据验证、索引/水位原子提交、维护幂等和遗忘抑制。普通资料及工具通过 teaneko.agent:layered-memory、teaneko.agent:long-memory 安装，详见[分层记忆](../../../src/main/java/org/zexnocs/teanekoagent/component/memory/layered/README.md) |
| 观察式人格 | ILongTermMemoryConsolidator.analyze 可在原模型请求同时返回 StyleObservations.Analysis；Target 由宿主固定，插件不能替换人格或自报来源。IStyleObservationStore 在原事务保存观察与采用，独立样本、原管理身份、字段锁定和已关闭学习域仍由宿主验证；旧 consolidate 函数式实现继续兼容 |
| 记忆维护 | 实现 IMemoryMaintenance 并注册 Bean，原 MemoryMaintenanceService 串行等待每个端口完成；每批必须有界，游标与回收同事务提交，不使用学习关闭作为无限保留过期数据的依据。替换 Store 时同时实现维护或明确提供独立清理后端，保留有效数据、幂等及遗忘保护 |
| 明确保存 | 默认 ConversationHistoryService 借用 ExplicitMemoryCapture 在模型前提交真实用户原文；替换 IAgentHistoryRecorder 时自行接入这一职责，不能依赖模型必定调用 memory_add。替换 ILongTermMemoryStore 时实现 appendExplicit 的幂等、完整附加信息、有效期和人工保护；未实现默认失败，不能丢弃保存请求 |
| 平台回执 | 实现 IAgentDeliveryStore 并注册 Bean，AgentDeliveryService 复用原发送链；先占用、等待真实平台消息 ID、逐段记录。已确认重放跳过传输，未知占用禁止自动补发；按原 scope/绑定/generation 读取并有界清理，不把生成或已接受当成对方已读 |
| 回复协议、指令与内容 | Spring Bean 分别声明 @TeaNekoReplyProtocol、@TeaNekoReplyDirective、@TeaNekoReplyPartHandler 并实现对应接口，按精确 ID/版本注册；复用 scanner 和原插件归属。内容 resolve 可异步，宿主统一冻结计划、控制节奏并检查真实回执，见[表达扩展](../../../src/main/java/org/zexnocs/teanekoagent/component/expression/README.md#扩展开发) |
| 表情包采集后端 | Spring Bean 实现 IStickerAnalyzer、IStickerAssetStore、IStickerAdmissionPolicy 或 IStickerCatalog，并声明对应 @TeaNekoSticker 注解；本地配置精确选择标识，复用原插件代次。目录回调保持短事务，分析和文件动作在事务外；类型、容量、来源及迟到提交仍由宿主复核，见[素材扩展](../../../src/main/java/org/zexnocs/teanekoagent/component/sticker/README.md#扩展开发) |
| 类别选图与原生表情 | teaneko.agent:media-replies 依赖 expression；sticker_list 只在实际资料保留时安装，编译只接受本轮成功完整回执。客户端 Bean 实现 IExpressionClientAdapter 并声明 @TeaNekoExpressionClient，提供明确能力、目录和独立确认；资源发送覆写 IReplyPartHandler 的 ReplySendContext 重载并在异步动作后再次守卫，详见[客户端扩展](../../../src/main/java/org/zexnocs/teanekoagent/component/sticker/README.md#扩展客户端编码) |
| Skill | 继续经现有本地目录校验/发布与 Skill 策略选择；插件功能配套的 Skill 资源按部署说明安装，不因 JAR 存在就自动发布目录或增加权限 |
| 显式工作流 | 借用已有 AgentWorkflowService 安排实例，共享原父预算；不透明地自动调用其他 Agent 不属于注册能力 |
| 管理页面 | 贡献 ManagementResource，按 [管理扩展](management.md) 提供脱敏查询或明确管理操作 |

任务动作执行前及每个副作用前等待宿主传入的 guard，使用原任务 ID 与 nextFire 做防重；返回 Outcome.COMPLETED、REJECTED 或 INDETERMINATE，不能把接受请求写成成功交付。动作 ID 格式与原持久化协议一致，最长 48 位小写字母/数字/下划线；建议带插件前缀和必要协议版本，不擅自更改旧任务含义。

本版不自动扫描所有知识后端、Skill 资源或任意候选策略。它们通过明确 Feature/既有端口组合接入，宿主仍负责实际后端、部署授权和版本选择。新增可管理 DTO 或网络适配须同时维护当前指南及相关领域文档。
