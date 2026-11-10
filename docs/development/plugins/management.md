# 插件管理与 Web 接口

## 已有接口

Core 提供传输无关的 IManagementResource<Q,R>、ManagementGateway、ManagementContext 和 IManagementAuthority。可信 Web 入口复用原网关，完成 token 登录后默认开放全部已注册管理资源。`local-allowlist-enabled: true` 可显式收紧为原本地白名单范围。v4.9.0-l 增加插件总管理：读取目录、预览影响、按固定操作编号启停和查询原回执；所有 HTTP 操作仍经过原授权网关。

v4.9.1-l 将插件管理置于“管理”组首位。插件卡片右上角开关表示启用意愿，实际状态另行展示；变更仍先预览依赖影响再确认。配置按钮仅在启用且存在相应 `file-config` / `chat-config` 贡献时显示，操作区底部对齐并使用当前主题色。“已安装功能”打开按贡献领域分组的弹窗，左侧导航可跳到聊天命令、后台任务等领域，停用后仍可查看安装说明。

“插件配置”在主侧栏右侧展开独立二级导航，窄屏放入导航抽屉右侧；按所属插件名称分组，页面仍由 `IPluginConsoleContribution` 和构建期 Vue 模块声明自动接入。插件名称优先用于页面标签和依赖说明，ID 保留在管理元数据、悬停说明和筛选选项中；菜单、插件管理及归属筛选均接受名称和 ID。实际停用仍撤除业务页面及入口，公开名称不改变归属校验或持久标识。

插件实现 IManagementResource 并注册 Bean，声明 descriptor()、requestType() 与 execute(context, request)。描述包含归属身份、局部 id、schemaVersion、permission、mutation 和字段说明；资源 ID 为归属ID:局部ID。业务资源归属已登记插件；系统资源使用独立 `HostCapability`，保留 `teaneko.core`、`teaneko.agent` 等稳定 ID。重复 ID 或未知归属拒绝注册，宿主身份不进入业务插件开关。

requestType 返回具体请求 DTO 的 Class，宿主按该类型解析与验证；客户端不能传任意类名、方法名、Bean 名或 SQL。execute 返回真实 CompletionStage<View>，只导出明确 DTO，不导出 Entity、整个组件对象、控制、凭据或 Throwable。字段 schema 是说明，不取代请求和业务校验；敏感字段禁止回显。

当前内置资源：

| ID | 请求及权限 | 返回 |
| --- | --- | --- |
| teaneko.core:plugins.catalog / plugins.preview / plugins.change / plugins.operation / plugins.pages | plugins.inspect 或 plugins.manage，全局授权 | 持久意愿与有效状态、依赖影响、操作回执及可用页面目录 |
| teaneko.core:plugins | PluginCatalogResource.Query；plugins.inspect，全局授权 | PluginRegistry.View 列表：声明、当前有效开关、revision |
| teaneko.agent:instances | AgentManagementResource.Query(after, limit)；agent.inspect，全局授权 | 实例 ID、定义 ID、显式声明能力、全局注册能力目录及关闭状态；limit 为 1–100，末项 ID 可作为后继游标 |
| agent.expression:stickers | StickerManagement.Query(kind,after,limit,category,state)；agent.stickers.inspect，scopeRef=global | 图库或作业有界分页、类别与容量统计；没有作者、源 URL 或原话 |
| agent.expression:stickers.preview | StickerManagement.Preview(id,expectedRevision)；agent.stickers.preview，scopeRef=global | 最多 1.5MiB 的单幅合成 PNG 内联预览 |
| agent.expression:stickers.change | StickerManagement.Change；agent.stickers.manage，scopeRef=global | 按版本固定、停用、删除、恢复、修正或重新分析的持久回执 |
| agent.expression:stickers.operation | StickerManagement.OperationQuery(operationId)；agent.stickers.inspect，scopeRef=global | 当前主体本人原操作的真实回执 |

availableFeatures 是可安装目录，不代表全部启用或当前主体获准执行；declaredFeatures 是实例显式声明，不把默认/依赖展开伪装为用户声明。全局实例视图没有 group 过滤参数，不得授予仅有某群管理权限的主体。

## 插件总管理与升级

总管理显示插件 ID、版本、分类、功能、依赖插件和客户端要求，并分别标明“启用意愿”和“当前状态”。停用或依赖阻塞不会抹去安装功能清单：组件、System、工具、能力、命令、文件/聊天配置、后台任务和独立页面均可在插件卡片展开查看，搜索也覆盖这些说明。启停先按目录 revision 预览；阻塞原因明确显示，确认影响后使用固定 operationId 提交。逻辑热启停完成后立即更新所有目录；仅实际声明需要重启的变更提示重启。超时或网络中断先查询原回执，不生成新编号重发。持久配置与历史不会因停用删除。

安装说明由 App 的 `IPluginContributionSource` SPI 汇总，各层只向下依赖中立合同 `PluginContribution(pluginId, kind, id, name, description)`。来源应从已有声明缓存读取；必要的类型/方法发现只能在贡献目录初始化或显式重扫时执行。`PluginContributionDirectory` 在全部来源成功、插件归属已注册且同插件同类别标识唯一后原子发布；失败保留旧完整快照。页面轮询只读缓存，说明不得包含配置值、凭据、业务数据或执行句柄，也不代替普通目录的实时启停、权限和白名单复核。

插件可以使用 `IPluginConsoleContribution` 增加独立配置/操作页，前后端接入见[页面扩展](../web-console-pages.md)。文件/聊天配置通过 `@PluginOwned` 声明真实归属，由宿主缓存并投影 `pluginId`；适配器与原配置类型声明冲突会报错，不能借自报字段改变所属插件。客户端管理适配器继承实际客户端插件归属，显式冲突拒绝；具体调用和秘密查看继续复核插件代次。

确需专用 HTTP 入口时，上层模块以独立 Spring Bean 实现 `IWebAdminRoutes`，返回初始化时构建、明确限定路径及方法的 `RequestMatcher`。宿主统一要求管理员身份，保留 HTTPS 来源、会话及 CSRF；未声明路径仍默认拒绝。声明不表示插件已启用，也不替代控制器中的 `WebSessionGuard`、插件代次和具体资源授权。不要将业务路径写入宿主安全清单，也不要开放整个插件路径前缀。`@WebManagedEndpoint` 只选择错误响应格式，不会自动开放路由。

`local-allowlist-enabled` 默认 `false`，旧配置缺省该字段也采用默认开放：登录后可访问已注册管理资源、文件表单和声明管理来源的指令，无需手工添加插件总管理的五项资源。设为 `true` 后才精确匹配 `management-resources`、`file-sections`、`console-prefixes`；此时显式空列表保持拒绝。升级迁移由插件的 `IManagementResourceMigration` 贡献精确旧键到新键映射，保留原列表、其他字段与秘密原树，不通过迁移扩展收紧模式下的授权范围。版本标记 `plugin-management-version: 1` 记录迁移状态，所有更新沿用原配置类型锁、原子保存和发布。

## 管理授权边界

IManagementAuthority 由宿主提供唯一明确实现，缺失时所有发现与执行都拒绝。ManagementGateway.describe(context) 按每项权限筛选可发现资源；execute(key, context, request) 在实际执行前及返回前重新授权，并复核控制和插件版本。发现失败不等于证明资源不存在，不要将不可见项用于判断其他用户数据。

ManagementContext 的 subject、scopeRef 来自已认证适配器，requestId 由宿主生成或验证，control 由管理入口拥有。它不携带客户端自报角色，也不被直接转换为原生 AgentActorContext.USER。Web 登录、群 OWNER、DEBUG 以及跨空间查看的映射必须显式实现。

4.7.0-l 新写用例在实际数据库事务或外部副作用前调用 context.checkActive()，同时复核控制与宿主捕获的实时授权。原四参数构造保留，资源权限仍由 IManagementAuthority 检查。Web 授权始终校验 token 登录、真实会话、CSRF、同一在途上下文、目标能力及插件代次；开启收紧模式后另外复核本地白名单，不按主体字符串放行。唯一 Authority 不默认为其他传输授予权限。Web 管理源为独立 MANAGEMENT，不能提交到模型活动或借用平台用户身份。

长期连接及 SSE/WebSocket 尚未提供。未来连接适配需要按主体/范围过滤、重新授权、处理撤权及断连，并释放订阅；不能把内部可修改决策事件直接推给浏览器。

## 编写管理操作

先将命令适配器中的业务编排提取为应用用例，由命令和管理资源共同调用原领域服务。只读检查提供不可变 View 和有界分页，管理写入则使用明确请求 DTO，而非通用对象字段反射。

写请求携带 operationId、预期 revision/generation 以及业务参数。领域服务负责持久幂等、原子版本仲裁、真实回执和审计；Gateway 只做路由和授权，不自动为插件补出这些保证。返回阶段失败不能证明此前没有提交，长操作应提供 operationId 查询真实终态。

Agent Store 仍经 TeaNekoAgentEasyData；普通字段复用 ConfigData 校验，namespace/人格绑定走专用用例。4.7.0-l 的 ConfigEditService 在同一原 EasyData 事务中完成版本检查、局部合并、幂等和审计，不在 Controller 先读版本再无条件覆盖。

4.7.1-l 起，已启用 `@ConfigManager` 经原 scanner 自动生成通用表单，默认属于 `general` 组。为管理器提供中文 description，为字段提供 `@Description`；4.7.4-l 起另用 `displayName` 提供简短中文名称，例如 `displayName = "入群欢迎"`，不改变原 `value`、Bean 名称或持久键。旧插件未填写时沿用说明首行。页面选中配置后直接展示完整说明，尚未登记时不显示默认字段。

元数据使用原 ObjectMapper，支持继承、泛型、别名、标量及集合/对象 JSON。通用入口通过 `ConfigEditService.changeJson` 提交完整 JSON 值，字符串必须是 JSON 字符串；旧命令与专用适配器保持原文本协议。字段校验器可覆写 `isValidJson` 处理集合等结构化值，默认适配原标量校验。敏感字段不导出，也不允许普通修改。

复杂或受限配置实现 `IChatSettingsSection`，标记 `@TeaNekoChatSettings(value = "原管理器标识", group = "agent或自定义分组")` 并注册 Spring Bean。它必须对应实际已启用管理器，并优先于该管理器的通用投影；重复键明确报错。保留命名空间等特殊绑定的专用用例，不借通用字段放开。自 4.7.3-l 起，管理员直接访问已启用且适用于当前客户端的配置，无需本地 `settings-sections` 或 `settings-groups` 放行；所属 `@PluginOwned` 插件、字段校验及事务内业务检查继续生效。

平台由 `IClientManagementAdapter` 贡献 accounts、targets、resolve、discover；resolve 校验平台/账号/目标关系，`configNamespaces(resolved)` 声明此目标适用的配置命名空间，默认空集。OneBot 声明通用及对应群聊/私聊、平台配置，网页平台只声明通用及相应群聊/私聊配置。实际事务前重新核对适用范围、授权及插件代次，不把浏览器提交的值作为适用范围。已发布 HTTP 路径见[聊天设置](../web-console.md#聊天设置)。

图库请求与视图定义在 StickerManagement，业务服务为 StickerManagementService，四项资源为自动注册的 StickerManagementResources Bean。例如先通过 stickers 查询得到 id/revision，再提交 Change(UUID.randomUUID(),id,revision,Action.PIN,null,null)。超时后保留同一 operationId 查询 stickers.operation；在保留窗口内使用完全相同请求重放亦可，不能生成新 ID 猜测上次是否提交。

图库幂等回执与目录写入在同一 DTO 根事务，保留 24 小时且最多 512 项；冲突或窗口满拒绝，授权在执行及返回时复核。DELETE 保留拒绝同图自动复活的墓碑，RESTORE 才解除；REFRESH 要求明确已登记分析区域，只借用 SYSTEM 视觉活动和原有预算，不把管理身份变成聊天 USER。各动作和预览/刷新文件占用的完整约束见[素材模块](../../../src/main/java/org/zexnocs/teanekoplugin/agent/expression/sticker/README.md#全局管理-spi)。

## 客户端指令台扩展

平台适配器可实现 `IClientManagementAdapter.consoleState/consoleLease`，通过原 Spring、注解及 scanner 注册。只有真实账号、目标与连接可确认时才返回固定 Lease；实际发送须复核原会话、插件、平台状态和该连接，不自动改投、广播或重试。默认实现明确不支持。

命令扩展沿用原命令注解和 `@Description`，另外声明 `@ManagementCommand` 与 `CommandData<ConsoleInvocation>`。权限保持原体系，平台管理权限不等于群成员身份。完整示例、HTTP 路径及回执限制见[客户端指令台](../web-console-commands.md)。

独立 `web` 平台已经通过同一管理适配器接入聊天设置和 Agent 工作区。插件可监听 `WebMessageReceiveEvent`，新增工具声明 `@TeaNekoAgentToolClass(client = "web")`；来源权限、持久回执和媒体限制见[网页聊天](../web-console-chat.md)。

## 文件配置扩展

非敏感文件默认可通过原解析器的 `IFileDocumentParser.schema` 自动投影，默认要求重启；自定义解析器未提供元数据时不会生成表单。需要敏感字段、集合增删或明确热应用契约时，Spring Bean 实现 `IFileSettingsAdapter`，标记 `@TeaNekoFileSettings("稳定配置区")`，由原 scanner 注册并覆盖同类型通用投影。声明固定类型、中文字段、秘密标记、集合上限和应用方式；重复键或同类型专用适配器冲突会拒绝装配。登录后默认允许已注册文件区，启用 `local-allowlist-enabled` 时才要求命中 `file-sections`；`@PluginOwned` 始终限制实际使用。认证根配置禁止普通注册。

`validateDocument` 检查未展开文档的结构歧义，`validate` 复用模块候选校验；两者不发布缓存。`applicationFor` 可根据固定资源变更要求重启；`verifyApplied` 通过原模块只读入口确认本次发布。钩子同步执行，不联网、不发送消息、不触发模型或迁移，不持锁等待异步结果，也不申请其他配置类型锁。不能可靠确认热应用的模块声明 RESTART。

原解析器实现 `IFileDocumentParser` 后才支持受限原树操作。插件不能从请求取得文件路径或 Java 类型，不导出候选对象、原文及隐藏字段；实际保存、版本检查、私有账本和权限复核统一由宿主执行。完整回执与文件系统边界见[文件配置说明](../web-console-files.md)。

## 值编辑器

Core 的 `@ValueEditor` 可标记配置类型、字段、访问器或 record 参数，例如：

```java
@ValueEditor("LONG_TEXT")
@Description("自定义人格说明")
private String customPersonality;
```

内置标识为 `JSON`、`LONG_TEXT`；字段声明优先于值类型声明，未声明时按原字段种类选择。元数据通过 `ConfigSchema` 投影到文件和聊天表单。注解不替代类型绑定、字段校验、敏感字段限制或保存授权。

前端共用 `ValueEditor.vue`，通过 `value-editors.ts` 的 `registerValueEditor` 注册标识、校验、格式化和按需加载扩展；重复标识明确报错。新增种类只需注册一次，并由配置注解选择，不复制弹窗。未知标识不能确认提交。组件装配字段可直接声明相同 `kind`。

## 独立 Agent 目录

实现 Spring Bean `IAgentLibrarySource`，以唯一 `id()` 提供有界的 `StoredPage<Item>`。目录只列已存空间/主体，不创建数据、不授予写权限；游标须保留代次，空页可以继续。具体操作仍声明为 `IManagementResource`，经过宿主管理策略、实时授权和原领域事务。

人格空间的基础配置从 `IPersonalityConfiguration.catalog()` 取得，不借伪聊天 cfg。首次写操作在原事务校验可信基础指纹；已提交的原样重试先核对完整回执，不受随后基础定义变化影响。

## 定时展示与分类

在任务方法或所属类型添加 `@TimerDisplay`，声明稳定 category、中文 categoryName、name、description、visible 和 controllable。方法声明优先于类型声明；同一分类键必须使用同一名称，冲突明确报错。`visible = false` 只隐藏管理观测，不取消任务；`controllable = false` 同时关闭网页动作和服务端 Core 门禁写入。

```java
/**
 * 按插件原业务入口统计日报。
 * @return 原定时任务结果
 */
@TimerDisplay(category = "plugin.business", categoryName = "业务插件", name = "日报统计",
        description = "按原规则统计日报，已开始的业务继续完成。")
public EmptyTaskResult dailySummary() {
    // 仍通过原插件业务端口完成统计。
    return EmptyTaskResult.INSTANCE;
}
```

使用原 `ITimerService.registerBy...(..., this::dailySummary, ...)` 即可解析声明。lambda 可从实现方法所属类型取得类级声明；动态包装无法确认来源时归为未分类。手动构建 `TimerTaskConfig` 可用 `.presentation(new TimerPresentation(...))` 显式提供不可变元数据，自定义 `ITimerTaskConfig` 可覆写 `getPresentation()`。展示声明在实际注册时冻结，不从浏览器改写。

Spring `@Scheduled` 继续使用原注解处理器，目录按实际注册对象关联 `ScheduledMethodRunnable` 的方法，始终只读。当前 Spring 的结果观测包装没有公开解包接口，关联适配集中在 `SpringTimerViewSource`；升级框架时保留真实注册任务测试，结构变化应明确定位，不能改用名称匹配。观测状态不冒充方法启动的异步业务终态。

需要观测其他调度器时，Spring Bean 实现 `ITimerViewSource`，经原 scanner 发现；source 标识须唯一且不能占用 core。`snapshot(limit)` 只贡献有界、动作为空的实际注册快照；自行维持同一活任务的稳定 UUID，不在查询时创建任务、触发业务或接管生命周期。分类、规则、状态及名称过滤由原目录在分页前统一执行；原全局管理权限仍生效。

## 网页房间与模型配置扩展

v4.7.5-l 的房间准备通过 `@WebRoomPreparation(id = "唯一标识")` Spring Bean 和 `IWebRoomPreparation.prepare(room, guard)` 扩展。目录复用 Core scanner，校验重复标识，按标识排序调用，异步结束后复核会话和插件代次。用于进入房间前的幂等准备，不接收浏览器自报的 scope。Agent 的 `WebPrivateAgentPreparation` 只对未登记私聊提供启用初值，使用 `ConfigEditService.initializeIfAbsent` 的原 DTO 事务，已有关闭不覆盖，群聊不自动登记。

异步消息监听器在同步通知阶段用 `event.trackProcessing(stage)` 登记实际处理链，不阻塞 Core 事件线程。网页通知结束后等待这些阶段，失败仍传播为未确认回执；不能在通知封闭后追加。仅登记监听器自己负责的业务，不把无限后台任务加入本轮。

模型配置扩展放在供应商的 LLM 目录：Spring Bean 实现 `ILLMConfigurationAdapter` 并声明 `@LLMConfigurationAdapter(id = "实际协议标识", icon = "/console/models/图标文件")`。`describe()` 返回公开模板，地址及路径复用该供应商原常量，具体模型与真实容量可编辑。图标可省略，前端使用名称缩写；本地图标放在 `frontend/public/models/` 并随程序构建。注册目录同时检查真实 `ILLMModelService` 和模板/模型的插件启用状态；重复协议标识、协议版本和模板标识不一致明确失败。模板禁止包含运行密钥或其他用户私有配置。

v4.7.7-l 将文件条目的唯一配置路由 `id` 与实际协议 `adapter` 分开，同一协议可配置多次。运行时使用所选配置路由读取参数、连接和凭据，不将其他路由的密钥借给它；旧条目未声明 `adapter` 时继续使用同名协议。新增协议实现须保留该路由绑定，可复用已有模型基类与凭据边界；完整配置及聊天切换见[模型配置](../web-console-models.md)。

凭据输入方式通过 `@LLMCredentialInput(id = "唯一标识")` 和 `ILLMCredentialInput` 贡献名称、说明、输入正则、掩码及编码前后缀；前端自动发现，手动模式原样写入，环境变量模式生成 `${变量名}`。扩展编码必须已被 Core 文件解析器或对应凭据消费端支持，页面描述不等于新增解析能力。查看公开样板仍要求原 `agent.models` 文件区授权，保存/应用沿用原文件服务、版本、回执、CSRF 和秘密保护。操作说明见[模型配置](../web-console-models.md)。

指令目录由 Core `CommandCatalog` 投影实际扫描方法、`@Description` 参数说明、`@DefaultValue`、权限、scope 及客户端声明。网页补全使用持久房间与真实成员生成的原 `CommandData`，继续调用原权限与范围服务，排除仅管理来源和不兼容原始平台类型的方法。全局说明与成员补全不执行方法、不赋予角色；实际分发仍复核所有原规则。

应用层 `ChatIdentityService` 从真实消息事件持久记录公开聊天与成员资料，业务可用 `name(client, scope)` 或 `member(client, scope, uuid)` 查询；群内名片按客户端和聊天隔离，昵称不作为身份或授权证明。目录和 Agent 工作区复用这些名称，不需要插件重复建立缓存。

## 后续业务资源与页面

4.7.0-l 已提供 Core 运行/日志与聊天持久计划管理，实际边界见[任务与日志](../web-console-runtime.md)。实例级贡献实现 IGlobalManagementResource，仍使用原网关、插件目录及明确宿主授权；不能将聊天权限当作全局权限。Web 对两类资源分别路由并执行当前本地管理策略，不为其他传输新增默认允许的 Authority。

[Web 管理设计](../../design/web-management-api.md) 保留了记忆、群配置、任务/Timer/运行实例、运行报告、日志及实时事件的资源形状。当前已经落地的是插件管理 SPI、插件与实例查询以及四项图库资源；其他业务资源需要各自完成授权映射、结构化 DTO、版本更新或持久历史后发布，不提供空路由。

Timer 定义、持久计划和单次 TaskRun 分开展示。底层命令调用任务可能先结束，外层业务 Stage 尚未结束，页面应跟踪业务 runId 的真实阶段。取消请求不等于已撤销副作用，INDETERMINATE 不显示为安全可重试。

插件表单优先使用受限 schema、表格与动作描述。4.9.0-l 的独立控制台页面通过[构建期页面声明](../web-console-pages.md)接入，导航、标题和生命周期共用一份合同；不支持加载远端插件脚本。浏览器中的记忆、日志及插件文本按非可信内容渲染，运行状态使用只读观测快照，审计使用独立可靠记录。

组件独立模型使用 `IAgentComponent.Field` 的 `MODEL` 类型。目录负责私有连接的运行脱敏、同路径密钥保留及配置版本路由，字段可提供同源 `probe` 管理端点以显示检测按钮；检测接口仍必须独立复核会话、配置、插件与真实聊天权限，字段声明不构成授权。前端共用 `ModelSelectionEditor`，无需增加宿主插件标识分支。
