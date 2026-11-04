# TeaNeko App 可扩展性、可维护性与异步执行评估

评估日期：2026/11/04。版本依据：`version.properties` 中的 `4.8.0-l`；Git 基线：`24631f9`，同时评估当前未提交源码。

本文是源码评估与修改建议，不是已实施改造记录，也不替代 [v4.8.0-l 组件装配计划](../plan/old/4.8.0-agent-composition.md)。审阅期间该计划相关源码仍在更新，下文将已实现的底座、观察到的中间态和建议目标分别说明。本次只新增本文，不修改业务代码，不执行构建、测试、真实模型请求或数据库操作。

## 一、结论

| 维度 | 当前判断 | 主要依据 |
| --- | --- | --- |
| 可扩展性 | 已有较好的声明式扩展底座，但不同模块成熟度不一致 | 工具、模型、回复协议、插件能力、管理资源可以贡献新实现；旧扫描器的冲突处理不统一，Agent 内置能力仍在从固定编排向组件装配迁移 |
| 可维护性 | 分层和业务边界较清楚，局部复杂度偏高 | Core 未检出对上层项目包的直接类型引用；已有专用存储入口和边界测试，但会话服务职责集中、部分关键代码过度压缩，构建尚未强制模块边界 |
| 异步执行 | 主链完成、取消及资源管理较完整，通用入口的容量和旧任务取消需要补齐 | `CompletionStage`、`ExecutionControl`、HTTP 订阅释放和同会话排队已接通；虚拟线程不等于容量限制，旧 `CompletableFuture.cancel(true)` 不保证停止业务 |

总体判断：不需要重建插件框架或统一改成另一套异步模型。最有价值的方向是复用已有 scanner、注册表和执行控制，补齐注册一致性、可选能力装配与通用执行边界。

优先事项：

1. 统一旧扩展目录的冲突拒绝和原子发布，避免新增类影响既有路由。
2. 修正旧任务的超时停止合同，并给高流量入口增加有界准入。
3. 在现有 4.8.0 计划内完成“组件声明 → 实际系统执行”的闭环，不止于增加目录或能力开关。
4. 随上述修改拆分确有独立职责的代码、加强边界验证，不进行全仓库机械重构。

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

检查了 Core 的扫描、注册、插件、事件、命令、任务、执行控制和组件管线；App 的客户端、内容类型及管理目录；Agent 的能力、工具、模型、回复、轮次和会话装配；OneBot 收发入口，以及前端模型模板和页面导航。

采用源码调用链、配置、现有测试源码和本地 JDK 25 源码交叉核对。下文的扩展成本是静态判断，不是假称已经完成一次新插件接入实验；容量风险也不是已测得的性能瓶颈。没有运行测试，因此“已有用例”仅表示源码中存在相应验证，不表示本次回归通过。

## 三、可扩展性评估

### 3.1 已经值得保留的机制

- [AnnotatedBeanRegistry](../../src/main/java/org/zexnocs/teanekocore/framework/extension/AnnotatedBeanRegistry.java) 复用 Spring Bean，校验注解和接口，拒绝重复键并冻结目录。它已经是新扩展点合适的公共基础，不需要再建第二套通用注册框架。
- [AgentPluginRuntime](../../src/main/java/org/zexnocs/teanekoagent/plugin/AgentPluginRuntime.java) 接收 `AgentFeature` 贡献，校验能力依赖、组件命名空间、System 写入范围和插件身份；实例选择与插件安装分开处理。
- [SystemPipeline](../../src/main/java/org/zexnocs/teanekocore/framework/composition/SystemPipeline.java) 在编译时检查重复系统、依赖环及无顺序的多个写入者，运行时检查实体版本和补丁范围。它不只是一个按顺序调用函数的列表。
- [TeaNekoAgentToolRegistry](../../src/main/java/org/zexnocs/teanekoagent/component/tool/TeaNekoAgentToolRegistry.java) 和 [TeaNekoAgentToolMethods](../../src/main/java/org/zexnocs/teanekoagent/component/tool/TeaNekoAgentToolMethods.java) 已将注解工具纳入能力选择、可信上下文、客户端过滤和真实异步返回链。
- [ReplyExtensionRegistry](../../src/main/java/org/zexnocs/teanekoagent/component/expression/ReplyExtensionRegistry.java) 按协议、版本、指令和内容类型分派；[LLMModelService](../../src/main/java/org/zexnocs/teanekoagent/component/llm/framework/model/LLMModelService.java) 按 `@LLMModel` 发现模型。这些位置不需要随着普通实现增加而追加供应商或内容类型分支。
- [ChatSettingsDirectory](../../src/main/java/org/zexnocs/teanekoapp/web/settings/ChatSettingsDirectory.java) 能自动投影配置管理器，并允许明确的专用适配器；[ManagementGateway](../../src/main/java/org/zexnocs/teanekocore/plugin/management/ManagementGateway.java) 组合管理资源，保留宿主授权和插件代次复核。

### 3.2 典型扩展场景

下表的“0”指已有扩展合同内预计不必修改原核心类，仍须新增实现、必要测试并完成配置与启用。

| 场景 | 现有接入方式 | `M_core` 判断 | 尚需注意 |
| --- | --- | --- | --- |
| 新增命令或子命令提供者 | `@Command`、`@DefaultCommand` / `@SubCommand` 与 Spring Bean | 0 | 返回真实 `CompletionStage`；重名处理尚不严格，见 E1 |
| 新增事件观察者 | `@EventListener`、`@EventHandler`、可选 `@PluginOwned` | 0 | 异步监听不是主事件完成的一部分；决策事件不能借此异步修改 |
| 新增同步任务阶段 | `ITaskStage`、`@TaskStage` | 0 | 只能依赖同步阶段合同，异步环绕使用已有 `OperationPipeline` |
| 新增 Agent 工具 | `@TeaNekoAgentToolClass`、`@TeaNekoAgentTool` | 0 | 保留能力选择、条件、client、预算和真实工具回执 |
| 新增资料来源或插件内 System | `AgentFeature` Bean 提供 prompts / systems | 0，限已有扩展位置 | 当前公开阶段为 `BEFORE_PROMPT`、`AFTER_INTERACTION`；不是任意内置步骤替换 |
| 新增模型执行适配器 | 实现 `ILLMModel` 并声明 `@LLMModel` | 0 | 模型配置、凭据、真实上下文容量仍须完整接入 |
| 为模型增加现有格式的 Web 模板 | `@LLMConfigurationAdapter` 与模板实现 | 0，普通模板 `M_ui = 0` | 模板字段取值可扩展，不代表任意新控件都已可扩展 |
| 新增回复协议、指令或内容类型 | 对应 `TeaNekoReply*` 注解和接口 | 0 | 精确版本与类型，不能绕过统一编译、发送守卫和回执 |
| 新增定时业务动作 | `AgentTaskAction` Bean | 0 | [AgentScheduleConfig](../../src/main/java/org/zexnocs/teanekoagent/component/schedule/configuration/AgentScheduleConfig.java) 已组合动作并拒绝同名，不应另建 Timer |
| 新增聊天配置区或管理资源 | 配置管理器 / 专用配置区 / `ManagementResource` | 0，通用表单通常 `M_ui = 0` | 聊天配置不恢复本地分组白名单；文件配置仍遵守自身保护规则 |
| 新增平台客户端与内容数据类型 | `@TeaNekoClient`、客户端接口、`@TeaNekoContentPart` | 基础注册为 0 | 完整平台还需自己的收发、身份、管理与表达适配；不能只算一个客户端类就宣称接入完成 |
| 新增可序列化 Agent 组件 | `@TeaNekoAgentComponent`、`IAgentComponent` | 实施中，不能整体认定为 0 | 目录与桥接已有源码，内置执行路径仍有固定装配，见 E2 |
| 新增独立顶层管理页面 | 新 Vue 页面 | 既有前端需修改 | `ConsoleNavigation.vue` 的页面集合与 `App.vue` 的渲染入口均为显式定义，见 E4 |

外部依赖模块还需要导入 Spring 配置；供类扫描使用的声明需要登记扫描根。这一边界已在 [Core 插件指南](../development/plugins/core.md) 说明。当前不是任意 JAR 热插拔、卸载或不受信代码沙箱，不能把这类能力混入本次“新增类即可扩展”的承诺。

### E1：旧目录的冲突处理和重载发布不一致——优先级 P1

源码事实：

- [TeaNekoClientScanner._scan](../../src/main/java/org/zexnocs/teanekoapp/client/TeaNekoClientScanner.java#L59) 直接 `handlerMap.put`，同 ID 后写覆盖先写。
- [ContentPartScanner._scan](../../src/main/java/org/zexnocs/teanekoapp/message/ContentPartScanner.java#L54) 先覆盖，再记录冲突警告。
- [CommandScanner._scan](../../src/main/java/org/zexnocs/teanekocore/command/CommandScanner.java#L162) 对同名记录错误后跳过后者，目录继续可用；这与“整个声明冲突明确拒绝”不同。
- [AbstractScanner.reload](../../src/main/java/org/zexnocs/teanekocore/reload/AbstractScanner.java#L35) 先清空后重扫；上述目录读取没有随整个重建过程一起串行化，可能观察到空目录或半成品，失败也不会自动保留原快照。

影响：扩展代码本身虽然只新增了类，但同名或重载失败会改变既有路由。扫描顺序成为隐含行为，降低了扩展可预测性。

建议：

1. 在临时映射中完成扫描、键和签名校验；重复声明直接拒绝，错误包含键及双方来源。
2. 完成整个目录校验后一次性替换不可变快照。重载失败保留旧目录，首次初始化失败则明确不可用。
3. Bean 扩展复用 `AnnotatedBeanRegistry` 的规则；内容 DTO 继续按类扫描，不把普通数据类强制变成 Bean。
4. 按目录逐个迁移，不直接重写所有 scanner。先检查实际部署是否已有冲突，不能用静默覆盖维持错误配置。

验收：交换扫描顺序结果不变；重名必失败；并发读取只能看见旧版或新版完整目录；重载失败不破坏已发布目录。

### E2：Agent 正在组件化，但“声明即装配”尚未覆盖内置业务——优先级 P1

源码事实：

- 审阅后段已出现 [AgentComponentFeature](../../src/main/java/org/zexnocs/teanekoagent/component/AgentComponentFeature.java) 与 `AgentComponentRegistry.contributions()`，组件正在桥接到原 `AgentFeature` 执行目录，不能再按“完全没有组件接线”评价。
- [AgentTurnPipeline.compile](../../src/main/java/org/zexnocs/teanekoagent/runtime/AgentTurnPipeline.java) 仍固定建立准入、资料、交互、完成四个宿主步骤，并在两个位置调用插件。
- 该类仍直接持有人格、记忆引用、历史、摘要等端口，处理过程中出现 `assembly.has("personality")`、`assembly.has("tool")`、`assembly.has("memory")` 等已知能力判断；[AgentExecutionConfig](../../src/main/java/org/zexnocs/teanekoagent/configuration/AgentExecutionConfig.java) 仍显式组合插件、Skill 与总来源。

判断：新增插件自有资料或阶段系统已经容易；新增一种需要参与内置生命周期的可选业务能力，仍可能需要修改中央编排。不能把“可序列化参数”和“可自由组合执行职责”视为同一项完成成果。

建议在现有 4.8.0 计划内收敛，不另建平行方案：

1. 准入、可信身份、模型预算、提交前复核和最终结果仲裁继续由宿主固定拥有，不允许普通插件替换。
2. 把可选能力的参数解释、来源贡献和业务执行交给对应组件或 Spring 管理的 System；主链消费已校验的装配结果，避免每增加一种能力再增加一组中央字符串判断。
3. 只为实际需要插入的业务阶段建立类型化端口。现有两个插件阶段足够的场景继续复用，不开放任意生命周期钩子。
4. 组件声明、参数校验、执行目录、Web 表单和 API 文档使用同一份权威元数据；兼容读取和私有凭据继续遵守现有计划边界。
5. 以一个新增可选能力做端到端验收：声明与实现放在新类中，完成配置保存、重启恢复、按需安装、执行及停用，原编排类保持不变。

普通参数分支并非问题；需要减少的是中央类对每种可选业务能力的逐一认识。本文不将并发开发中的临时接线或尚未验收功能列作已发布故障。

### E3：扫描合同及静态目录生命周期还需统一——优先级 P2

[BeanScanner](../../src/main/java/org/zexnocs/teanekocore/utils/scanner/BeanScanner.java) 使用目标类的 `getAnnotation`，而工具方法编译已经使用 Spring 的合并注解读取；[ClassScanner](../../src/main/java/org/zexnocs/teanekocore/utils/scanner/ClassScanner.java) 对“有注解但不满足接口”的类采取跳过，扫描异常还存在捕获 `Throwable` 后继续的路径。因此不能假定所有扩展点都支持相同的组合注解、错误声明诊断和失败语义。

另外，[LLMConfigurationDirectory.describe](../../src/main/java/org/zexnocs/teanekoagent/component/llm/file_config/form/LLMConfigurationDirectory.java) 和当前 `AgentComponentRegistry.entries()` 每次调用都会重新建立声明注册表；聊天配置和文件配置目录则采用首次构建后保留快照。尚无测量证明扫描是性能热点，但生命周期和错误出现时机不一致已经增加理解成本。

建议：

- 明确每个 scanner 支持直接注解还是组合注解。确有组合注解需求时统一使用已有 Spring 注解解析能力，并增加代理及组合注解用例；不能仅换一处读取方法就宣称全链支持。
- 必须识别的错误声明明确失败，不把实现遗漏表现为“没有找到扩展”。保留确有用途的普通无关类过滤，缩小异常捕获范围。
- 静态声明在合适的装配阶段校验一次并保存快照；插件是否启用、授权和代次仍在调用时检查。避免在构造期提前实例化业务 Bean 导致循环依赖，可在装配完成后的校验阶段或明确的惰性初始化边界实施。
- 不把动态权限过滤结果长期缓存为静态目录，也不为了统一外观再创建万能反射分派器。

验收：同一合同的错误声明诊断一致；代理保留；重复读取目录不重复编译静态声明；插件关闭后立即从可用结果中过滤或在执行边界拒绝。

### E4：后端元数据扩展较好，专用前端仍是编译期显式装配——优先级 P2，按需求实施

[ModelTemplateEditor.vue](../../frontend/src/ModelTemplateEditor.vue) 已通过后端 catalog 展示模型类型、默认值、型号、容量和图标，增加同类模板无需追加供应商分支。但该组件的 `form` 和提交逻辑仍明确列举现有字段，新协议若需要额外专用控件，不能仅靠模板默认值自动获得可编辑界面。

[ConsoleNavigation.vue](../../frontend/src/ConsoleNavigation.vue) 的页面类型、导航组和 [App.vue](../../frontend/src/App.vue) 的页面装配也由源码显式定义。后端贡献了管理资源，不代表已经自动生成一个完整的新业务页面。

建议优先复用已有通用字段描述和编辑器。只有确有多个独立页面扩展需求时，再增加前端构建期页面描述注册和统一导航入口，明确页面 ID、权限、标题及组件加载关系；不在后端下发可执行脚本，不引入远程任意前端插件机制。

验收目标应分开：普通配置与模板增加字段不改宿主表单；专用交互页面允许新增前端组件，但不再反复扩张中央导航和条件渲染列表。

## 四、可维护性评估

### 4.1 优点

- Core、App、Agent、客户端和业务插件有明确的包级职责。本次对 Core 搜索上层项目包的全限定名，未检出直接引用。
- Agent 业务 Store 通过专用 EasyData 入口和 DTO 访问，已有 [AgentLayerBoundaryTest](../../src/test/java/org/zexnocs/teanekoagent/runtime/AgentLayerBoundaryTest.java) 约束绕过数据库封装的导入。
- 执行控制、管线、HTTP 请求、工具方法编译和实际业务存储已分别承担不同职责，没有把所有扩展和并发问题集中到单一容器。
- 仓库已有 [插件开发入口](../development/plugins/README.md)、实例和边界测试，后续改造可以沿既有合同进行，不需要靠另建文档体系解决全部问题。

### M1：会话生命周期职责集中，修改风险大于文件长度本身——优先级 P2

[AgentConversationService](../../src/main/java/org/zexnocs/teanekoagent/runtime/conversation/AgentConversationService.java) 约 1,500 行，同时处理邮箱容量、请求去重与重放、等待计时、人格与记忆对齐、轮次启动、完成提交、退休清理和关闭。该类的风险是这些职责共享状态转换和锁，不是单纯“超过某个行数”。

建议先复用已有会话 engine、journal、state 等协作者，再根据实际变更抽取缺失职责：

- 邮箱准入和排队状态转换保持一个明确的所有者与锁域。
- 已完成请求的重放投影与复核通过窄端口交接，不暴露可变邮箱。
- 等待计时及终止注册的拥有关系可封装为小的生命周期协作者，但不新建第二个排队器。
- 不把存储服务、线程或可执行闭包搬入 ECS 组件；不以增加更多构造参数或全能上下文对象替代真正分责。

应先以同会话串行、重复请求、等待超时、取消与完成竞争、关闭和迟到回调用例保护状态机，再按一次可独立验收的职责拆分。不要在组件装配仍变动时同步大范围改写整个会话内核。

### M2：包边界主要靠约定，现有文本检查存在盲区——优先级 P2

[`settings.gradle.kts`](../../settings.gradle.kts) 当前只有根工程声明，生产代码同处一个主源码集；包名分层并不形成独立编译约束。

`AgentLayerBoundaryTest.agentDomainDoesNotImportPlatformLegacyOrAdapters()` 只检查以 `import org.zexnocs.` 开头的行；而 `AgentTurnPipeline` 中对 `AgentPluginRuntime`、`ExpressionRuntime` 的全限定类型引用不受这条规则检查。这不证明相应依赖必然错误，但证明该测试不能完整保证其描述的边界。

建议先明确哪些能力是领域端口、哪些是装配实现，再扩展现有检查覆盖全限定类型引用，必要时使用编译产物依赖检查。禁止为了让检查通过而改写引用形式，允许的协作也应显式记录。只有独立发布、依赖隔离或构建成本出现明确需求时，再评估拆成 Gradle 子模块；本次不建议立即全量拆模块。

验收：新增一条真正禁止的导入和等价全限定引用都能触发失败；允许的领域端口和启动装配不会被误报。

### M3：关键实现的压缩排版与部分说明降低审查效率——优先级 P2

`AnnotatedBeanRegistry`、`ReplyExtensionRegistry`、`StickerCollectionService` 等实际存在一行多个赋值、分支或异常处理的代码。并发和版本复核本就需要追踪执行顺序，这种写法会增加遗漏清理、错误捕获或守卫位置的概率。

建议只在实质修改的范围内展开多行结构、命名关键阶段和临时变量，按 AGENTS.md 补齐中文方法 JavaDoc、局部修改记录及准确的版本标签。异常信息和注释同步修正，尤其是线程池容量和取消语义，不能只改文字而维持错误的运行承诺。无需引入格式化框架或全仓库重排。

## 五、异步执行评估

### 5.1 已形成的执行合同

| 执行层 | 已有行为 | 评价 |
| --- | --- | --- |
| 命令 | [CommandExecutor](../../src/main/java/org/zexnocs/teanekocore/command/CommandExecutor.java) 检查返回合同，展开方法返回的 Stage，并在排队启动及结果完成时复核 | 不是调用方法后立即报告业务完成 |
| 轮次和组件管线 | 显式业务执行器，`thenCompose` 串联；实体租约、版本及补丁范围检查 | 同轮次串行可组合，不代表跨会话只能串行 |
| 执行控制 | [ExecutionControl](../../src/main/java/org/zexnocs/teanekocore/actuator/execution/ExecutionControl.java) 提供父子取消、截止时间、共享预算及可解除的终止监听 | 资源拥有者与借用者边界较清楚 |
| HTTP | [APIRequestExecution](../../src/main/java/org/zexnocs/teanekocore/api_response/APIRequestExecution.java) 同时管理完成、计时器、控制监听与实际订阅 | 不止结束外层 Future；取消会释放订阅资源 |
| 异步拦截 | [OperationPipeline](../../src/main/java/org/zexnocs/teanekocore/plugin/operation/OperationPipeline.java) 等待已启动下游，拒绝重复或迟到 next | 可保留同步任务链，按操作需要使用真正的异步环绕 |
| 会话 | 单会话运行槽、有限等待、等待超时和总会话容量 | 已存在容量边界，不应描述为所有请求无限排队 |
| 后台素材整理 | 只启动一个后台流程，虚拟业务域等待事务或模型，具有控制和作业状态 | `.join()` 在这里不是自动成立的性能缺陷 |

`SystemPipeline.execute()` 对步骤使用串行 `thenCompose`；[AgentWorkflowService](../../src/main/java/org/zexnocs/teanekoagent/runtime/workflow/AgentWorkflowService.java) 也逐个执行依赖前序结果的协作步骤。当前“异步”主要指不把远端等待压在调用线程上、可组合完成和独立执行，不是自动并行 DAG。

独立资料检索若经测量确有并行收益，可在明确的资料组合器内有限并发，并按稳定顺序合并、共享原预算和控制。不应直接把共享实体写入、同会话交付或依赖前序结果的步骤改成 `allOf`。

### A1：通用任务和平台入口缺少完整的在途容量边界——优先级 P1

源码事实：

- [ThreadPoolConfig.taskExecutor](../../src/main/java/org/zexnocs/teanekocore/_app_config/ThreadPoolConfig.java#L26) 注释声明队列大小为 CPU 核心数的 200 倍，但实际构造 `ScheduledThreadPoolExecutor(cpu)`，没有配置有界队列；`setMaximumPoolSize(cpu * 2)` 也不能把它变成该描述的线程池。
- 同类中的 `virtualExecutor` 为每个任务创建虚拟线程；[TaskService.subscribeWithFuture](../../src/main/java/org/zexnocs/teanekocore/actuator/task/TaskService.java#L86) 在登记任务前未见总在途数量限制。
- [OnebotClient](../../src/main/java/org/zexnocs/teanekoclient/onebot/core/OnebotClient.java#L108) 使用自有虚拟线程执行器分发接收和发送工作；`executeAsync` 有停机及提交失败处理，但未建立完整业务处理的入口配额。
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

[TaskExecuteService](../../src/main/java/org/zexnocs/teanekocore/actuator/task/TaskExecuteService.java#L134) 把 `CompletableFuture.runAsync` 的返回值登记为 `executingFuture`；[TaskService.TaskCache.onExpire](../../src/main/java/org/zexnocs/teanekocore/actuator/task/TaskService.java#L284) 在任务仍执行的异常分支调用 `cancel(true)`，随后结束任务回执并标记终态。

已核对本地 JDK 25 `java.base/java/util/concurrent/CompletableFuture.java` 的 `cancel`：`mayInterruptIfRunning` 不控制实际执行线程的中断。因此，已经运行且没有自己的协作取消或资源关闭机制的 Callable 仍可能继续产生副作用。这里不能继承新 HTTP 执行链已经实现的取消能力。

建议：

- 为需要停止能力的旧任务增加明确的控制接入，复用 `ExecutionControl`；在排队启动、业务阶段及副作用提交前检查，而不是只取消一个完成信号。
- 可中断或可关闭的实际资源由任务拥有者保留对应句柄并释放；共享执行器不由任务关闭。即使使用真实执行句柄，也不能把线程中断等同于远端撤回或数据库回滚。
- 分开记录“调用者已超时/取消”与“实际执行已收敛”。不可撤回的操作保留未知结果或真实完成记录，不能因此自动重试写操作。
- 接入时兼容原同步 `ITaskStage` 和外部回执模式，不要求一轮改造所有旧插件。

验收：运行中的任务超时后，协作任务不再进入后续副作用；不响应取消的底层操作仍被追踪到收敛；迟到回执不能覆盖终态或释放他人的资源；取消不会引发重复发送和自动重写。

### A3：需要明确三种不同的“完成”，不应统一改造全部事件和任务——优先级 P2

- [TaskStageChain.next](../../src/main/java/org/zexnocs/teanekocore/actuator/task/TaskStageChain.java) 是同步调用栈。同步后置逻辑不会自动等待 Callable 另外发起的异步业务。
- [EventService](../../src/main/java/org/zexnocs/teanekocore/event/EventService.java) 将 `async=true` 的监听器登记成独立任务，`pushEventWithFuture` 的主结果不等待这些监听器结束。
- `ExecutionControl.guard(stage)` 可以先结束受控等待，但只有底层适配器实际接入终止监听，才能进一步停止或释放外部资源。

这些区别在现有 [Core 插件指南](../development/plugins/core.md) 与 [执行控制说明](../../src/main/java/org/zexnocs/teanekocore/actuator/execution/README.md) 已有说明，不能把它们全部当成缺陷重写。

建议让新增接口的 JavaDoc 明确返回阶段表示“已接收”“业务完成”还是“真实交付”；需要异步前后置的操作使用现有 `OperationPipeline`，需要决定主链结果的工作通过可等待端口接入，普通旁路观察仍保留事件。只有实际业务需要时补充可等待事件能力，不改变旧监听器语义。

## 六、建议实施顺序与验收

以下是建议顺序，不是新增的进行中实施计划，不调整现有 4.8.0 计划进度。若决定实施，再将选定范围纳入 `docs/plan`。

| 顺序 | 完整交付单元 | 对应建议 | 必要验收 |
| --- | --- | --- | --- |
| 1 | 旧注册目录一致性 | E1，相关 E3 | 同名拒绝、扫描顺序无关、旧快照保留及并发读取完整性 |
| 2 | 旧任务停止与资源收敛 | A2 | 排队取消、运行超时、迟到副作用、未知结果和不重复重试 |
| 3 | 高流量入口有界准入 | A1 | 超限拒绝、跨入口共享额度、底层完成后释放、停机和排队过期 |
| 4 | Agent 声明装配闭环 | E2，复用 4.8.0 计划 | 一个新组件只新增类即可贯通声明、参数、持久化、执行与目录；旧配置兼容和权限继续生效 |
| 5 | 局部维护性收敛 | M1–M3，按需求 E4 | 保持原并发合同；架构检查识别全限定引用；通用字段不重复增加页面分支 |

第 1 项与正在进行的组件装配可分开安排，但不要同时改写同一注册或会话状态机；容量和取消应联合设计，避免“提前取消外层 Future 就释放额度”的漏洞。

### 6.1 必须保留的既有边界

- 身份与权限、客户端范围、插件归属和代次、配置版本仲裁不能变成可选扩展。
- 数据仍通过业务专用 EasyData 与 DTO；事务、行锁和版本能力不足时补基础层，不在 Store 越层读写。
- 模型输出额度由所选模型 `max-tokens` 决定；完整输出预留及显式总预算拒绝不能被新的执行配额替代。
- 密钥不进入组件公共快照、Prompt、工具目录或普通日志；文件配置与聊天配置遵守各自访问规则。
- 普通 DTO、固定状态枚举、局部参数分支和内部辅助方法无需注解化。安全提交点不能为追求 `M_core = 0` 而交给任意插件控制。

### 6.2 验证应复用的现有资产

| 已有测试源码 | 已有验证方向 | 本次建议新增的关键边界 |
| --- | --- | --- |
| [PluginRuntimeTest](../../src/test/java/org/zexnocs/teanekocore/plugin/PluginRuntimeTest.java) | 目录冲突、插件代次、异步拦截真实完成 | 将相同的确定性要求覆盖到旧客户端、内容和命令目录 |
| [AgentPluginIntegrationTest](../../src/test/java/org/zexnocs/teanekoagent/runtime/AgentPluginIntegrationTest.java) | 实际轮次安装能力、插件变更、决策与停止 | 新可序列化组件贯通配置与执行，而非只测试注册成功 |
| [SystemPipelineTest](../../src/test/java/org/zexnocs/teanekocore/framework/composition/SystemPipelineTest.java) | 写入依赖、实体租约、取消及迟到补丁 | 扩展装配后的命名空间、参数版本及受保护组件边界 |
| [APIResponseExecutionTest](../../src/test/java/org/zexnocs/teanekocore/api_response/APIResponseExecutionTest.java) | 排队取消、真实订阅释放、超时与迟到缓存写入 | 新准入额度在实际收敛时释放，不受外层提前完成干扰 |
| [CommandScannerContractTest](../../src/test/java/org/zexnocs/teanekocore/command/CommandScannerContractTest.java) | 命令异步签名和实际生产类合同 | 重名拒绝、失败重载保留目录 |
| [AgentLayerBoundaryTest](../../src/test/java/org/zexnocs/teanekoagent/runtime/AgentLayerBoundaryTest.java) | 包依赖和持久化导入边界 | 全限定类型引用不能绕过检查 |

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
