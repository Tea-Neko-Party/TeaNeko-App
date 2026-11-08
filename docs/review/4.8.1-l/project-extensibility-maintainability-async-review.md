# v4.8.1-l 可扩展性、可维护性与异步执行评估

2026/11/06 按原评估版本迁入审查目录。本文是 4.8.1-l 的历史架构基线，正文日期、版本和验证结论保留，不作为后续版本的直接实施清单。当前实现器入口见 [4.8.4-l 下一步实施](../4.8.4-l/next-steps.md)，组件修正及 G03 已完成范围保留于 [4.8.3-l](../4.8.3-l/completed.md)；未纳入复评的历史架构事项不能据此标为已完成。

复评日期：2026/11/05。版本依据：`version.properties` 中的 `4.8.1-l`；Git 基线：`36e0188`，同时评估当前未提交源码。首次评估为 2026/11/04、基线 `24631f9`，本文更新为当前结论，不再沿用重构中间态的判断。

本文是源码评估与修改建议，不是实施记录。[v4.8.0-l 组件装配](../../plan/old/4.8.0-agent-composition.md)与[分层重构](../../plan/old/4.8.0-layering.md)均已归档完成，[计划索引](../../plan/README.md)当前无进行中的实施计划。本次只更新本文，不修改业务代码，不执行构建、测试、真实模型请求或数据库操作。

## 一、结论

| 维度 | 当前判断 | 主要依据 |
| --- | --- | --- |
| 可扩展性 | 明显改善；已有合同内的常规扩展基本满足“只新增实现与声明” | 可序列化组件已贯通校验、字段目录与四阶段执行，工具、模型、回复和管理资源继续复用既有扩展点；旧目录冲突及宿主内建语义耦合仍有边界 |
| 可维护性 | 明显改善；分层约束和能力归类已落地，局部生命周期复杂度仍高 | 五层源码扫描未检出反向类型引用，完整能力归入组件目录、平台主动适配、工具独立成类；会话服务仍集中管理较多状态，内部边界检查尚不完整 |
| 异步执行 | 主链底座较完整；关键可靠性短板较上次变化不大 | 组件真实完成阶段已被等待；通用入口容量、旧任务取消语义仍需补齐，系统返回 Stage 与执行域切换也应明确区分 |

总体判断：4.8.0 的改善不只是移动目录，已经降低新增组件、工具和客户端适配对原类的修改需求。下一阶段应优先补齐注册一致性与异步可靠性，不需要重建插件框架、继续大规模目录重构或更换异步模型。

优先事项：

1. 统一旧扩展目录的冲突拒绝和原子发布，避免新增类影响既有路由。
2. 修正旧任务的超时停止合同，并给高流量入口增加有界准入。
3. 明确新增 System 的执行域合同，避免扩展在 I/O 完成回调线程执行阻塞工作。
4. 按实际替换需求收敛剩余宿主耦合；渐进处理会话职责与 Agent 内部分层检查，不进行全仓库机械重构。

### 1.1 与上次评价的变化

| 上次关注点 | 本次复核 | 处理结论 |
| --- | --- | --- |
| E2：组件声明与实际执行仍在迁移 | 已通过组件贡献接入四阶段，内置人格、记忆和表达有实际 System | 撤回“装配实施中”的判断；剩余内建语义耦合降为 P2 |
| M2：分层主要靠约定 | 已有全项目依赖检查，客户端主动适配；本轮扫描未检出跨层反向类型引用 | 主要问题已改善；只保留 Agent 内部检查细化建议 |
| 工具与能力职责聚合 | 可选能力按完整职责归类，19 个注解工具分布于独立文件 | 肯定已实现的维护性改善，不再要求重复拆分 |
| E3 / E4：目录反复编译、前端扩展受限 | 组件目录已缓存，普通组件字段已自动生成表单 | 部分改善；LLM 配置目录、专用控件和顶层页面仍有局部限制 |
| E1 / A1 / A2：旧目录、入口容量、旧任务取消 | 相关关键实现与上次基线未变 | 保留三项 P1，作为下一阶段重点 |
| A4：新增系统的执行域 | 管线串行组合 Stage，但不为每个 System 自动切换执行器 | 新增 P2 合同风险；不是已发生阻塞故障的结论 |

## 二、评价口径与范围

### 2.1 “尽可能只新增类”的具体含义

以一个实际扩展场景为单位，区分以下成本：

| 指标 | 计算口径 | 建议目标 |
| --- | --- | --- |
| `M_core` | 为增加一种既有扩展点的实现，需要修改的既有核心、调度、路由和业务编排类数量 | 常规扩展为 0 |
| `M_bootstrap` | 既有启动配置、扫描根或依赖声明的修改量 | 单独记录；外部模块首次导入可以有明确装配成本 |
| `M_ui` | 元数据能够表达的配置扩展，需要修改的既有前端组件数量 | 普通配置、模型模板和目录项为 0 |
| 注册确定性 | 同名、错误接口、非法声明、依赖环是否被明确拒绝 | 发布目录前失败，保留可定位的来源信息 |
| 运行完整性 | 新实现能否沿用权限、插件代次、客户端限制、预算、取消和资源释放 | 不能由扩展自行绕过 |

“新增实现并使用现有注解”和“创建全新的扩展类别”不是一回事。前者应尽量做到 `M_core = 0`；后者通常需要一次性建立接口、注解和宿主调用位置。一个从未被宿主识别的注解本身不会产生执行语义。

增加配置、显式选择能力、安装依赖和首次导入外部 Spring 配置，不计作核心类修改，但不能把这些步骤隐去。插件发现也不等于自动启用，更不等于获得业务权限。

### 2.2 本次检查范围

检查了 Core 的扫描、注册、插件、事件、命令、任务、执行控制和组件管线；App 的客户端、内容类型及管理目录；Agent 的组件、工具、模型、回复、轮次和会话装配；客户端适配、OneBot 收发入口，以及前端组件表单、模型模板和页面导航。

本轮对 1,231 个生产 Java 文件按五层方向做了文本扫描，去除块注释和独占行注释，保留完整全限定类名及字符串，未检出跨层反向类型引用；检出 19 个 `@TeaNekoAgentTool` 方法，没有单文件聚合多个工具的情况。测试源码共 174 个 Java 文件，定点阅读与本次结论相关的用例。该扫描不能证明动态拼接反射或所有固定能力标识的语义依赖均不存在。

采用源码调用链、配置和现有测试源码交叉核对，并复用首次评估已核实的本地 JDK 25 执行器及取消语义。下文的扩展成本是静态判断，不是一次新插件接入实验；容量风险也不是已测得的性能瓶颈。未运行测试，既有用例和归档验收均不计作本次回归通过。

## 三、可扩展性评估

### 3.1 已经值得保留的机制

- [AnnotatedBeanRegistry](../../../src/main/java/org/zexnocs/teanekocore/framework/extension/AnnotatedBeanRegistry.java) 复用 Spring Bean，校验注解和接口，拒绝重复键并冻结目录。它已经是新扩展点合适的公共基础，不需要再建第二套通用注册框架。
- [AgentPluginRuntime](../../../src/main/java/org/zexnocs/teanekoagent/plugin/AgentPluginRuntime.java) 接收 `AgentFeature` 贡献，校验能力依赖、组件命名空间、System 写入范围和插件身份；实例选择与插件安装分开处理。
- [AgentComponentRegistry](../../../src/main/java/org/zexnocs/teanekoagent/component/AgentComponentRegistry.java) 缓存已校验的静态声明，向参数校验、运行装配和 Web 目录提供共同元数据；[AgentComponentFeature](../../../src/main/java/org/zexnocs/teanekoagent/component/AgentComponentFeature.java) 将原 Spring 组件桥接到既有能力执行链，不重新实例化业务服务。
- [SystemPipeline](../../../src/main/java/org/zexnocs/teanekocore/framework/composition/SystemPipeline.java) 在编译时检查重复系统、依赖环及无顺序的多个写入者，运行时检查实体版本和补丁范围。它不只是一个按顺序调用函数的列表。
- [TeaNekoAgentToolRegistry](../../../src/main/java/org/zexnocs/teanekoagent/component/tool/TeaNekoAgentToolRegistry.java) 和 [TeaNekoAgentToolMethods](../../../src/main/java/org/zexnocs/teanekoagent/component/tool/TeaNekoAgentToolMethods.java) 已将注解工具纳入能力选择、可信上下文、客户端过滤和真实异步返回链。
- [ReplyExtensionRegistry](../../../src/main/java/org/zexnocs/teanekoplugin/agent/expression/expression/ReplyExtensionRegistry.java) 按协议、版本、指令和内容类型分派；[LLMModelService](../../../src/main/java/org/zexnocs/teanekoagent/llm/framework/model/LLMModelService.java) 按 `@LLMModel` 发现模型。这些位置不需要随着普通实现增加而追加供应商或内容类型分支。
- [ChatSettingsDirectory](../../../src/main/java/org/zexnocs/teanekoapp/web/settings/ChatSettingsDirectory.java) 能自动投影配置管理器，并允许明确的专用适配器；[ManagementGateway](../../../src/main/java/org/zexnocs/teanekocore/plugin/management/ManagementGateway.java) 组合管理资源，保留宿主授权和插件代次复核。

### 3.2 典型扩展场景

下表的“0”指已有扩展合同内预计不必修改原核心类，仍须新增实现、必要测试并完成配置与启用。

| 场景 | 现有接入方式 | `M_core` 判断 | 尚需注意 |
| --- | --- | --- | --- |
| 新增命令或子命令提供者 | `@Command`、`@DefaultCommand` / `@SubCommand` 与 Spring Bean | 0 | 返回真实 `CompletionStage`；重名处理尚不严格，见 E1 |
| 新增事件观察者 | `@EventListener`、`@EventHandler`、可选 `@PluginOwned` | 0 | 异步监听不是主事件完成的一部分；决策事件不能借此异步修改 |
| 新增同步任务阶段 | `ITaskStage`、`@TaskStage` | 0 | 只能依赖同步阶段合同，异步环绕使用已有 `OperationPipeline` |
| 新增 Agent 工具 | `@TeaNekoAgentToolClass`、`@TeaNekoAgentTool`，独立工具类 | 0 | 保留能力选择、条件、client、预算和真实工具回执；共享业务通过服务复用 |
| 新增资料来源或插件内 System | `AgentFeature` Bean 提供 prompts / systems | 0，限已有扩展位置 | 已有 `BEFORE_PROMPT`、`BEFORE_INTERACTION`、`AFTER_INTERACTION`、`BEFORE_DELIVERY` 四阶段；不是任意内置步骤替换 |
| 新增模型执行适配器 | 实现 `ILLMModel` 并声明 `@LLMModel` | 0 | 模型配置、凭据、真实上下文容量仍须完整接入 |
| 为模型增加现有格式的 Web 模板 | `@LLMConfigurationAdapter` 与模板实现 | 0，普通模板 `M_ui = 0` | 模板字段取值可扩展，不代表任意新控件都已可扩展 |
| 新增回复协议、指令或内容类型 | 对应 `TeaNekoReply*` 注解和接口 | 0 | 精确版本与类型，不能绕过统一编译、发送守卫和回执 |
| 新增定时业务动作 | `AgentTaskAction` Bean | 0 | [AgentScheduleConfig](../../../src/main/java/org/zexnocs/teanekoplugin/agent/automation/schedule/configuration/AgentScheduleConfig.java) 已组合动作并拒绝同名，不应另建 Timer |
| 新增聊天配置区或管理资源 | 配置管理器 / 专用配置区 / `ManagementResource` | 0，通用表单通常 `M_ui = 0` | 聊天配置不恢复本地分组白名单；文件配置仍遵守自身保护规则 |
| 新增平台客户端与内容数据类型 | `@TeaNekoClient`、客户端接口、`@TeaNekoContentPart` | 基础注册为 0 | 完整平台还需自己的收发、身份、管理与表达适配；不能只算一个客户端类就宣称接入完成 |
| 新增可序列化 Agent 组件 | `@TeaNekoAgentComponent`、`IAgentComponent`，贡献参数、系统、工具或资料 | 已有合同内为 0，普通字段 `M_ui = 0` | 复用四阶段和现有字段类型；替换内建人格或表达语义槽位不属于同等成本，见 E2 |
| 新增独立顶层管理页面 | 新 Vue 页面 | 既有前端需修改 | `ConsoleNavigation.vue` 的页面集合与 `App.vue` 的渲染入口均为显式定义，见 E4 |

外部依赖模块还需要导入 Spring 配置；供类扫描使用的声明需要登记扫描根。这一边界已在 [Core 插件指南](../../development/plugins/core.md) 说明。当前不是任意 JAR 热插拔、卸载或不受信代码沙箱，不能把这类能力混入本次“新增类即可扩展”的承诺。

### E1：旧目录的冲突处理和重载发布不一致——优先级 P1

源码事实：

- [TeaNekoClientScanner._scan](../../../src/main/java/org/zexnocs/teanekoapp/client/TeaNekoClientScanner.java#L59) 直接 `handlerMap.put`，同 ID 后写覆盖先写。
- [ContentPartScanner._scan](../../../src/main/java/org/zexnocs/teanekoapp/message/ContentPartScanner.java#L54) 先覆盖，再记录冲突警告。
- [CommandScanner._scan](../../../src/main/java/org/zexnocs/teanekocore/command/CommandScanner.java#L162) 对同名记录错误后跳过后者，目录继续可用；这与“整个声明冲突明确拒绝”不同。
- [AbstractScanner.reload](../../../src/main/java/org/zexnocs/teanekocore/reload/AbstractScanner.java#L35) 先清空后重扫；上述目录读取没有随整个重建过程一起串行化，可能观察到空目录或半成品，失败也不会自动保留原快照。

影响：扩展代码本身虽然只新增了类，但同名或重载失败会改变既有路由。扫描顺序成为隐含行为，降低了扩展可预测性。

建议：

1. 在临时映射中完成扫描、键和签名校验；重复声明直接拒绝，错误包含键及双方来源。
2. 完成整个目录校验后一次性替换不可变快照。重载失败保留旧目录，首次初始化失败则明确不可用。
3. Bean 扩展复用 `AnnotatedBeanRegistry` 的规则；内容 DTO 继续按类扫描，不把普通数据类强制变成 Bean。
4. 按目录逐个迁移，不直接重写所有 scanner。先检查实际部署是否已有冲突，不能用静默覆盖维持错误配置。

验收：交换扫描顺序结果不变；重名必失败；并发读取只能看见旧版或新版完整目录；重载失败不破坏已发布目录。

### E2：组件装配已形成闭环，宿主仍认识少量内建语义——剩余优先级 P2

源码事实：

- [IAgentComponent](../../../src/main/java/org/zexnocs/teanekoagent/component/IAgentComponent.java) 已声明参数、字段、数据键、初始化、System、工具及资料来源；[AgentTurnPipeline.compile](../../../src/main/java/org/zexnocs/teanekoagent/runtime/AgentTurnPipeline.java#L125) 实际接入四个组件执行阶段。
- [PersonalityComponent](../../../src/main/java/org/zexnocs/teanekoplugin/agent/cognition/personality/PersonalityComponent.java) 注入人格解析系统；[MemoryComponent](../../../src/main/java/org/zexnocs/teanekoplugin/agent/cognition/memory/MemoryComponent.java) 注入输入和输出系统，真实等待历史保存；[ExpressionCompileSystem](../../../src/main/java/org/zexnocs/teanekoplugin/agent/expression/expression/ExpressionCompileSystem.java) 返回实际表达编译阶段。不是只有组件外壳或开关。
- [AgentScopeFieldChecker](../../../src/main/java/org/zexnocs/teanekoagent/integration/config/AgentScopeFieldChecker.java#L75) 保存时校验装配，[ConfigDataAgentScopePolicy](../../../src/main/java/org/zexnocs/teanekoagent/integration/config/ConfigDataAgentScopePolicy.java#L64) 运行读取时复核；[AgentComponentController](../../../src/main/java/org/zexnocs/teanekoagent/integration/web/AgentComponentController.java#L80) 提供统一字段目录，普通表单据此生成。
- 客户端能力通过 `assemblyRequirements()` 主动声明所需组件；[ToolComponent](../../../src/main/java/org/zexnocs/teanekoagent/component/tool/ToolComponent.java) 只依赖本层工具能力，[OnebotAgentFeature](../../../src/main/java/org/zexnocs/teanekoplugin/onebot/common/agent/OnebotAgentFeature.java#L114) 负责平台适配，不再由 Agent 固定绑定平台能力标识。
- 剩余耦合是 `AgentTurnPipeline.preparePrompt` 直接消费 [PersonalityResolveSystem.SNAPSHOT](../../../src/main/java/org/zexnocs/teanekoagent/runtime/AgentTurnPipeline.java#L222)，`projectComponents` 直接消费 [ExpressionCompileSystem.PLAN](../../../src/main/java/org/zexnocs/teanekoagent/runtime/AgentTurnPipeline.java#L391)；旧实例兼容路径仍调用人格、历史和表达端口，[AgentExecutionConfig](../../../src/main/java/org/zexnocs/teanekoagent/configuration/AgentExecutionConfig.java) 仍显式组合部分来源。

判断：在已有四阶段追加能力、使用普通字段并沿用默认宿主语义，可以静态判定 `M_core = 0`、`M_ui = 0`。替换人格快照来源、表达计划投影等内建语义则仍可能修改宿主。这是局部可替换性限制，不再是“组件没有执行闭环”。

建议：

1. 准入、可信身份、模型预算、提交前复核和最终结果仲裁继续由宿主固定拥有，不允许普通插件替换。
2. 继续由组件拥有完整业务职责，不再为新增能力向中央类追加字符串分支；沿用已建立的字段目录、依赖校验与四阶段执行。
3. 只有出现实际替换需求时，再将人格结果、表达计划等交接定义为窄的宿主端口或类型化协议，不为追求绝对零分支建立万能处理器。
4. 旧实例兼容路径先确认迁移、部署和回退需求，再决定是否收敛；不能为了缩短主链直接删除。
5. 后续新增真实组件时，用新类贯通参数保存、恢复、选择、执行及停用，并确认原编排类不变。复用已有装配和轮次用例，不把单系统测试当作全链验收。

普通参数分支和固定安全提交点不是扩展缺陷。无需重开已完成的 4.8.0 计划来重复实现这些能力。

### E3：扫描合同及静态目录生命周期还需统一——优先级 P2

[BeanScanner](../../../src/main/java/org/zexnocs/teanekocore/utils/scanner/BeanScanner.java) 使用目标类的 `getAnnotation`，而工具方法编译已经使用 Spring 的合并注解读取；[ClassScanner](../../../src/main/java/org/zexnocs/teanekocore/utils/scanner/ClassScanner.java) 对“有注解但不满足接口”的类采取跳过，扫描异常还存在捕获 `Throwable` 后继续的路径。因此不能假定所有扩展点都支持相同的组合注解、错误声明诊断和失败语义。

目录生命周期已有部分改善：[AgentComponentRegistry.entries](../../../src/main/java/org/zexnocs/teanekoagent/component/AgentComponentRegistry.java#L42) 通过同步方法和 `registered` 缓存静态目录，上次“每次重建”的判断已不适用。[LLMConfigurationDirectory.describe](../../../src/main/java/org/zexnocs/teanekoagent/llm/file_config/form/LLMConfigurationDirectory.java#L61) 仍每次建立模型模板及凭据输入注册表；聊天配置和文件配置目录则保留首次构建的快照。尚无测量证明这是性能热点，建议针对剩余生命周期和错误出现时机的不一致处理。

建议：

- 明确每个 scanner 支持直接注解还是组合注解。确有组合注解需求时统一使用已有 Spring 注解解析能力，并增加代理及组合注解用例；不能仅换一处读取方法就宣称全链支持。
- 必须识别的错误声明明确失败，不把实现遗漏表现为“没有找到扩展”。保留确有用途的普通无关类过滤，缩小异常捕获范围。
- 静态声明在合适的装配阶段校验一次并保存快照；插件是否启用、授权和代次仍在调用时检查。避免在构造期提前实例化业务 Bean 导致循环依赖，可在装配完成后的校验阶段或明确的惰性初始化边界实施。
- 不把动态权限过滤结果长期缓存为静态目录，也不为了统一外观再创建万能反射分派器。

验收：同一合同的错误声明诊断一致；代理保留；重复读取目录不重复编译静态声明；插件关闭后立即从可用结果中过滤或在执行边界拒绝。

### E4：后端元数据扩展较好，专用前端仍是编译期显式装配——优先级 P2，按需求实施

[AgentAssemblyEditor.vue](../../../frontend/src/features/agent/AgentAssemblyEditor.vue#L165) 已按组件目录的 `fields`、`defaults` 和 `requires` 渲染与选择，支持 BOOLEAN、NUMBER、JSON、SECRET、LONG_TEXT、TEXT。使用这些字段类型的普通新组件无需修改宿主表单，不能继续笼统评价为“组件前端没有通用扩展”。

[ModelTemplateEditor.vue](../../../frontend/src/features/models/ModelTemplateEditor.vue) 已通过后端 catalog 展示模型类型、默认值、型号、容量和图标，增加同类模板无需追加供应商分支。但该组件的 `form` 和提交逻辑仍明确列举现有字段，新协议若需要额外专用控件，不能仅靠模板默认值自动获得可编辑界面。

[ConsoleNavigation.vue](../../../frontend/src/app/ConsoleNavigation.vue) 的页面类型、导航组和 [App.vue](../../../frontend/src/app/App.vue) 的页面装配也由源码显式定义。后端贡献了管理资源，不代表已经自动生成一个完整的新业务页面。

建议优先复用已有通用字段描述和编辑器。只有确有多个独立页面扩展需求时，再增加前端构建期页面描述注册和统一导航入口，明确页面 ID、权限、标题及组件加载关系；不在后端下发可执行脚本，不引入远程任意前端插件机制。

验收目标应分开：已有字段合同内的普通配置和组件不改宿主表单；同结构模型模板不加供应商分支。只有决定扩展专用页面注册时，才要求新增前端组件不再反复扩张中央导航和条件渲染列表。

## 四、可维护性评估

### 4.1 优点

- Core、App、Agent、Client、Plugin 的单向依赖已有 [ProjectLayerBoundaryTest](../../../src/test/java/org/zexnocs/teanekocore/framework/ProjectLayerBoundaryTest.java) 约束，覆盖导入、内联全限定名和完整反射类名字符串；本轮按相同方向扫描五层源码，未检出跨层反向类型引用。
- 可选能力的参数、系统、服务、适配及配置已按完整职责归入 `component/<能力>`；共享轮次与会话归入 `runtime`，通用 App 接入归入 `integration`，具体平台适配由客户端持有。该结构比只拆组件壳类更便于独立理解与修改。
- 注解工具已拆为独立提供者，19 个工具未发现单文件多方法聚合；共享授权、查询与转换通过服务复用，不再要求每新增工具修改聚合类。
- App 的 Web 错误处理已通过 [IWebFailureMapper](../../../src/main/java/org/zexnocs/teanekoapp/web/error/IWebFailureMapper.java)、声明注解与注册机制接入，下层不再直接认识 Agent 或客户端异常实现。
- Agent 业务 Store 通过专用 EasyData 入口和 DTO 访问，已有 [AgentLayerBoundaryTest](../../../src/test/java/org/zexnocs/teanekoagent/runtime/AgentLayerBoundaryTest.java) 约束绕过数据库封装的导入。
- 执行控制、管线、HTTP 请求、工具方法编译和实际业务存储已分别承担不同职责，没有把所有扩展和并发问题集中到单一容器。
- 仓库已有 [插件开发入口](../../development/plugins/README.md)、实例和边界测试，后续改造可以沿既有合同进行，不需要靠另建文档体系解决全部问题。

### M1：会话生命周期职责集中，修改风险大于文件长度本身——优先级 P2

[AgentConversationService](../../../src/main/java/org/zexnocs/teanekoagent/runtime/conversation/AgentConversationService.java) 约 1,600 行（含注释和空行），同时处理邮箱容量、请求去重与重放、等待计时、人格与记忆对齐、轮次启动、完成提交、退休清理和关闭。该类的风险是这些职责共享状态转换和锁，不是单纯“超过某个行数”。分层迁移没有自动消除这部分维护成本。

建议先复用已有会话 engine、journal、state 等协作者，再根据实际变更抽取缺失职责：

- 邮箱准入和排队状态转换保持一个明确的所有者与锁域。
- 已完成请求的重放投影与复核通过窄端口交接，不暴露可变邮箱。
- 等待计时及终止注册的拥有关系可封装为小的生命周期协作者，但不新建第二个排队器。
- 不把存储服务、线程或可执行闭包搬入 ECS 组件；不以增加更多构造参数或全能上下文对象替代真正分责。

应先以同会话串行、重复请求、等待超时、取消与完成竞争、关闭和迟到回调用例保护状态机，再按一次可独立验收的职责拆分。避免与准入、取消改造同时大范围重写同一个会话状态机。

### M2：项目层边界已受检，Agent 内部边界仍有检查盲区——剩余优先级 P2

`ProjectLayerBoundaryTest.productionDependenciesPointOnlyDownward()` 已对完整生产源码检查五层依赖方向，不再只检查 import；同一测试类也约束单工具文件。上次“项目分层主要靠约定”的评价应修正为“已有自动化源码约束，但未形成独立编译模块”。[`settings.gradle.kts`](../../../settings.gradle.kts) 仍只有根工程声明。

剩余盲区在更细的 Agent 内部规则：`AgentLayerBoundaryTest` 对运行域引用具体 `component.*.adapter`、配置及 EasyData 实现，以及 Store 绕过持久化封装的检查仍以 import 行为依据。全限定引用可以绕过这类检查，而全项目测试只限制跨层方向，不区分同属 Agent 层的端口和具体适配器。这是检查覆盖不足，不表示已确认现有源码发生这些越界调用。

建议复用全项目检查的处理方式补齐内部禁止引用；必要时再使用编译产物依赖检查。继续明确组件端口与实现的区别，不把整个组件目录都当成纯数据或禁止运行域使用它。禁止为了通过检查而改写引用形式。只有独立发布、依赖隔离或构建成本出现明确需求时，再评估 Gradle 子模块；本次不建议立即拆模块。

验收：禁止的跨层引用和 Agent 内部实现引用，无论使用导入还是全限定名都能触发失败；允许的组件端口和启动装配不会被误报。固定能力 ID 的反向绑定仍需按真实装配关系审查，不能仅凭类型扫描通过作保证。

### M3：关键实现的压缩排版与部分说明降低审查效率——优先级 P2

本次新增或迁移的部分实现已经更易阅读，但 `AnnotatedBeanRegistry`、`ReplyExtensionRegistry`、`StickerCollectionService` 等仍局部存在一行多个赋值、分支或异常处理。并发和版本复核需要追踪执行顺序，压缩排版会增加遗漏清理、错误捕获或守卫位置的概率。[AgentServiceTest 的局部说明](../../../src/test/java/org/zexnocs/teanekoagent/runtime/AgentServiceTest.java#L66) 还含实际替换字符乱码，应随相关维护修正，不作为主要架构问题。

建议只在实质修改的范围内展开多行结构、命名关键阶段和临时变量，按 AGENTS.md 补齐中文方法 JavaDoc、局部修改记录及准确的版本标签。异常信息和注释同步修正，尤其是线程池容量和取消语义，不能只改文字而维持错误的运行承诺。无需引入格式化框架或全仓库重排。

## 五、异步执行评估

### 5.1 已形成的执行合同

| 执行层 | 已有行为 | 评价 |
| --- | --- | --- |
| 命令 | [CommandExecutor](../../../src/main/java/org/zexnocs/teanekocore/command/CommandExecutor.java) 检查返回合同，展开方法返回的 Stage，并在排队启动及结果完成时复核 | 不是调用方法后立即报告业务完成 |
| 轮次和组件管线 | 宿主阶段显式使用业务执行器，系统内部通过 `thenCompose` 串联；实体租约、版本及补丁范围检查 | 四个扩展阶段等待真实完成；同阶段后续系统不保证自动切换回业务域，见 A4 |
| 执行控制 | [ExecutionControl](../../../src/main/java/org/zexnocs/teanekocore/actuator/execution/ExecutionControl.java) 提供父子取消、截止时间、共享预算及可解除的终止监听 | 资源拥有者与借用者边界较清楚 |
| HTTP | [APIRequestExecution](../../../src/main/java/org/zexnocs/teanekocore/api_response/APIRequestExecution.java) 同时管理完成、计时器、控制监听与实际订阅 | 不止结束外层 Future；取消会释放订阅资源 |
| 异步拦截 | [OperationPipeline](../../../src/main/java/org/zexnocs/teanekocore/plugin/operation/OperationPipeline.java) 等待已启动下游，拒绝重复或迟到 next | 可保留同步任务链，按操作需要使用真正的异步环绕 |
| 会话 | 单会话运行槽、有限等待、等待超时和总会话容量 | 已存在容量边界，不应描述为所有请求无限排队 |
| 后台素材整理 | 只启动一个后台流程，虚拟业务域等待事务或模型，具有控制和作业状态 | `.join()` 在这里不是自动成立的性能缺陷 |

`SystemPipeline.execute()` 对步骤使用串行 `thenCompose`；[AgentWorkflowService](../../../src/main/java/org/zexnocs/teanekoagent/runtime/workflow/AgentWorkflowService.java) 也逐个执行依赖前序结果的协作步骤。当前“异步”主要指不把远端等待压在调用线程上、可组合完成和独立执行，不是自动并行 DAG。

独立资料检索若经测量确有并行收益，可在明确的资料组合器内有限并发，并按稳定顺序合并、共享原预算和控制。不应直接把共享实体写入、同会话交付或依赖前序结果的步骤改成 `allOf`。

### A1：通用任务和平台入口缺少完整的在途容量边界——优先级 P1

源码事实：

- [ThreadPoolConfig.taskExecutor](../../../src/main/java/org/zexnocs/teanekocore/_app_config/ThreadPoolConfig.java#L26) 注释声明队列大小为 CPU 核心数的 200 倍，但实际构造 `ScheduledThreadPoolExecutor(cpu)`，没有配置有界队列；`setMaximumPoolSize(cpu * 2)` 也不能把它变成该描述的线程池。
- 同类中的 `virtualExecutor` 为每个任务创建虚拟线程；[TaskService.subscribeWithFuture](../../../src/main/java/org/zexnocs/teanekocore/actuator/task/TaskService.java#L86) 在登记任务前未见总在途数量限制。
- [OnebotClient](../../../src/main/java/org/zexnocs/teanekoclient/onebot/core/OnebotClient.java#L108) 使用自有虚拟线程执行器分发接收和发送工作；`executeAsync` 有停机及提交失败处理，但未建立完整业务处理的入口配额。
- `apiScheduler` 确实使用有界弹性调度器；会话也限制 `maxWaiting` 和 `maxConversations`。这些局部约束不能代替所有任务、所有平台或某个远端服务的共享并发额度，调度队列有界也不等于异步 HTTP 在途请求数有界。

JDK 行为已用本地 JDK 25 `java.base/java/util/concurrent/ScheduledThreadPoolExecutor.java` 核对：该执行器使用无界延迟队列，调整 `maximumPoolSize` 对此调度模型没有预期的扩容作用。

风险判断：高峰输入、慢数据库或慢远端服务可能积累在途任务、回执和相关对象。目前未做压测，不能据此宣称已经发生内存耗尽或给出吞吐上限。

建议：

1. 先在任务接收、平台入站及实际外部调用的所属边界建立可观测的准入额度，分别约束排队和实际运行；已有会话额度继续复用。
2. 通过非阻塞准入或有界等待返回明确繁忙结果，不把 WebSocket 接收线程阻塞在等待许可上。
3. 配额覆盖其声明的真实工作阶段。入站转换结束但派生业务仍在运行时，不能提前释放“完整处理”额度；外层取消但底层操作未结束时，也不能假装资源已经释放。
4. 清楚区分计时器与业务执行器：计时回调只做轻量通知。不要机械地把所有虚拟线程替换为固定线程池，也不要通过扩大线程数处理外部容量不足。
5. 额度大小依据连接池、供应商限制及测量决定；每轮 token 和调用次数预算继续独立管理，不自动调低模型输出上限。

验收：超过限制可预测拒绝；取消等待项释放排队位；底层迟到完成不重复释放；共享远端资源不会被另一入口绕过；停机期间新提交明确结束，不留下永久等待的回执。

### A2：旧 Task 的 Future 取消不等于业务停止——优先级 P1

[TaskExecuteService](../../../src/main/java/org/zexnocs/teanekocore/actuator/task/TaskExecuteService.java#L134) 把 `CompletableFuture.runAsync` 的返回值登记为 `executingFuture`；[TaskService.TaskCache.onExpire](../../../src/main/java/org/zexnocs/teanekocore/actuator/task/TaskService.java#L284) 在任务仍执行的异常分支调用 `cancel(true)`，随后结束任务回执并标记终态。

已核对本地 JDK 25 `java.base/java/util/concurrent/CompletableFuture.java` 的 `cancel`：`mayInterruptIfRunning` 不控制实际执行线程的中断。因此，已经运行且没有自己的协作取消或资源关闭机制的 Callable 仍可能继续产生副作用。这里不能继承新 HTTP 执行链已经实现的取消能力。

建议：

- 为需要停止能力的旧任务增加明确的控制接入，复用 `ExecutionControl`；在排队启动、业务阶段及副作用提交前检查，而不是只取消一个完成信号。
- 可中断或可关闭的实际资源由任务拥有者保留对应句柄并释放；共享执行器不由任务关闭。即使使用真实执行句柄，也不能把线程中断等同于远端撤回或数据库回滚。
- 分开记录“调用者已超时/取消”与“实际执行已收敛”。不可撤回的操作保留未知结果或真实完成记录，不能因此自动重试写操作。
- 接入时兼容原同步 `ITaskStage` 和外部回执模式，不要求一轮改造所有旧插件。

验收：运行中的任务超时后，协作任务不再进入后续副作用；不响应取消的底层操作仍被追踪到收敛；迟到回执不能覆盖终态或释放他人的资源；取消不会引发重复发送和自动重写。

### A3：明确三种不同的“完成”——合同说明，不单列为缺陷

- [TaskStageChain.next](../../../src/main/java/org/zexnocs/teanekocore/actuator/task/TaskStageChain.java) 是同步调用栈。同步后置逻辑不会自动等待 Callable 另外发起的异步业务。
- [EventService](../../../src/main/java/org/zexnocs/teanekocore/event/EventService.java) 将 `async=true` 的监听器登记成独立任务，`pushEventWithFuture` 的主结果不等待这些监听器结束。
- `ExecutionControl.guard(stage)` 可以先结束受控等待，但只有底层适配器实际接入终止监听，才能进一步停止或释放外部资源。

这些区别在现有 [Core 插件指南](../../development/plugins/core.md) 与 [执行控制说明](../../../src/main/java/org/zexnocs/teanekocore/actuator/execution/README.md) 已有说明，不能把它们全部当成缺陷重写。

建议让新增接口的 JavaDoc 明确返回阶段表示“已接收”“业务完成”还是“真实交付”；需要异步前后置的操作使用现有 `OperationPipeline`，需要决定主链结果的工作通过可等待端口接入，普通旁路观察仍保留事件。只有实际业务需要时补充可等待事件能力，不改变旧监听器语义。

### A4：System 返回异步阶段，不等于其同步前段已切换执行域——优先级 P2

源码事实：

- [SystemPipeline.execute](../../../src/main/java/org/zexnocs/teanekocore/framework/composition/SystemPipeline.java#L123) 用非 Async 的 `thenCompose` 推进步骤；`runStep` 等待受控阶段并提交结果后继续后续步骤。
- [AgentGuardedSystem.execute](../../../src/main/java/org/zexnocs/teanekoagent/component/AgentGuardedSystem.java#L48) 直接调用原 System，再通过 `thenApply` 进行版本复核，不负责切换执行器。
- [AgentTurnPipeline.pluginStep](../../../src/main/java/org/zexnocs/teanekoagent/runtime/AgentTurnPipeline.java#L184) 在进入扩展阶段时使用业务执行器，但不能保证同阶段后续系统都在该执行器启动；它们可能沿前一 Stage 的完成线程继续执行。
- [IEntitySystem](../../../src/main/java/org/zexnocs/teanekocore/framework/composition/IEntitySystem.java) 和[组件开发指南](../../development/plugins/agent-components.md) 要求返回真实 Stage，尚未明确同步前段的阻塞限制和执行域责任。

风险：新扩展如果在返回 Stage 前同步访问 JDBC、文件或执行较长计算，可能占用前一步的 I/O 完成回调线程。这里确认的是合同缺口和可发生的调用路径，不是已经测得内置系统发生线程阻塞事故。

建议先约定 `execute` 的同步前段快速返回；阻塞工作交给借用的业务执行器，返回包含真实工作的 Stage，并保留原取消与版本检查。若宿主必须保证每个扩展系统的入口线程，则在 Agent 包装层明确调度边界和执行器所有权。不要让 Core 管线自行创建线程池，也不要把所有组合操作机械替换成 Async。

验收：让前一系统从可控的模拟 I/O 执行器完成，验证后一系统的实际执行线程及完成时点；取消期间不能继续提交补丁。使用 Future 或有界等待，不通过 `Thread.sleep` 轮询证明线程行为。

## 六、建议实施顺序与验收

以下是建议顺序，不是新增的进行中实施计划，也不重开已完成的 4.8.0 计划。若决定实施，再将选定范围纳入 `docs/plan`。

| 顺序 | 完整交付单元 | 对应建议 | 必要验收 |
| --- | --- | --- | --- |
| 1 | 旧注册目录一致性 | E1，相关 E3 | 同名拒绝、扫描顺序无关、旧快照保留及并发读取完整性 |
| 2 | 旧任务停止与资源收敛 | A2 | 排队取消、运行超时、迟到副作用、未知结果和不重复重试 |
| 3 | 高流量入口有界准入 | A1 | 超限拒绝、跨入口共享额度、底层完成后释放、停机和排队过期 |
| 4 | 组件执行域与目录合同 | A4，剩余 E3 | 同阶段续体线程可验证；阻塞工作不占 I/O 回调域；静态目录不重复编译，动态授权不被缓存 |
| 5 | 局部装配和维护性收敛 | 按实际需求 E2、M1–M3、E4 | 新组件不改宿主；会话状态所有权不分裂；内部边界检查覆盖全限定引用；普通字段继续自动展示 |

前三项解决当前未改变的 P1 风险；后两项按真实扩展和维护需求推进。容量和取消应联合设计，避免“提前取消外层 Future 就释放额度”的漏洞；不要同时大范围改写同一个注册或会话状态机。

### 6.1 必须保留的既有边界

- 身份与权限、客户端范围、插件归属和代次、配置版本仲裁不能变成可选扩展。
- 数据仍通过业务专用 EasyData 与 DTO；事务、行锁和版本能力不足时补基础层，不在 Store 越层读写。
- 模型输出额度由所选模型 `max-tokens` 决定；完整输出预留及显式总预算拒绝不能被新的执行配额替代。
- 密钥不进入组件公共快照、Prompt、工具目录或普通日志；文件配置与聊天配置遵守各自访问规则。
- 普通 DTO、固定状态枚举、局部参数分支和内部辅助方法无需注解化。安全提交点不能为追求 `M_core = 0` 而交给任意插件控制。

### 6.2 验证应复用的现有资产

| 已有测试源码 | 已有验证方向 | 本次建议新增的关键边界 |
| --- | --- | --- |
| [PluginRuntimeTest](../../../src/test/java/org/zexnocs/teanekocore/plugin/PluginRuntimeTest.java) | 目录冲突、插件代次、异步拦截真实完成 | 将相同的确定性要求覆盖到旧客户端、内容和命令目录 |
| [AgentAssemblyTest](../../../src/test/java/org/zexnocs/teanekoagent/component/AgentAssemblyTest.java) | 新记录纯 LLM、旧记录兼容、参数与依赖、秘密保留、客户端贡献选择 | 新组件从配置保存到恢复执行的完整闭环；其中单系统参数测试不等于完整宿主轮次 |
| [AgentServiceTest](../../../src/test/java/org/zexnocs/teanekoagent/runtime/AgentServiceTest.java) | 实际轮次中，未装人格时记忆与表达仍执行；最小装配不调用记忆、人格和工具 | 新扩展只新增实现仍能参加主链，停用、取消及版本变化继续生效 |
| [AgentPluginIntegrationTest](../../../src/test/java/org/zexnocs/teanekoagent/runtime/AgentPluginIntegrationTest.java) | 实际轮次安装能力、插件变更、决策与停止 | 与组件装配用例复用守卫场景，不重复只测注册成功 |
| [SystemPipelineTest](../../../src/test/java/org/zexnocs/teanekocore/framework/composition/SystemPipelineTest.java) | 写入依赖、实体租约、取消及迟到补丁 | 同阶段续体的执行域，装配后的参数版本及受保护组件边界 |
| [APIResponseExecutionTest](../../../src/test/java/org/zexnocs/teanekocore/api_response/APIResponseExecutionTest.java) | 排队取消、真实订阅释放、超时与迟到缓存写入 | 新准入额度在实际收敛时释放，不受外层提前完成干扰 |
| [CommandScannerContractTest](../../../src/test/java/org/zexnocs/teanekocore/command/CommandScannerContractTest.java) | 命令异步签名和实际生产类合同 | 重名拒绝、失败重载保留目录 |
| [ProjectLayerBoundaryTest](../../../src/test/java/org/zexnocs/teanekocore/framework/ProjectLayerBoundaryTest.java) | 五层单向依赖、全限定引用与完整反射类名、单工具文件 | 持续防止客户端回流和工具重新聚合；不据此宣称可检测所有语义依赖 |
| [AgentLayerBoundaryTest](../../../src/test/java/org/zexnocs/teanekoagent/runtime/AgentLayerBoundaryTest.java) | Agent 内部运行域和持久化导入边界 | 同层具体适配器的全限定引用也不能绕过检查 |

上述项目是后续实施验收要求，不是本次测试执行报告。实际改代码后再按风险运行定向用例、集中回归、强制编译与产物检查，性能额度另用可重复的负载测量确定。

## 七、推荐的扩展验收标准

后续每新增一种常规实现，可以用以下闭环判断扩展性是否真正改善：

```text
新增实现与声明
  → Spring / scanner 发现
  → 静态合同、冲突与依赖校验
  → 发布不可变目录及公共元数据
  → 显式选择 + 当前权限 / 插件代次复核
  → 原执行控制内完成业务与资源收敛
```

一次实际扩展验收应同时满足：

1. 原核心分派和业务编排类没有因该实现新增分支，即 `M_core = 0`；首次导入配置成本另列。
2. 元数据足以表达的表单、目录和说明自动出现，普通扩展 `M_ui = 0`。
3. 同名、错误接口、非法依赖或版本不兼容在目录发布前明确失败；不依赖扫描顺序。
4. 未选择、未授权、客户端不匹配或插件停用时不能执行；关闭再启用也不能复活旧调用。
5. 返回阶段反映声明的真实完成语义；超时、取消、排队和资源关闭有可验证的结果。

达到这组标准，比增加注解数量、接口数量或把所有逻辑拆成小类，更能说明 TeaNeko App 已具备低侵入、可维护的扩展能力。
