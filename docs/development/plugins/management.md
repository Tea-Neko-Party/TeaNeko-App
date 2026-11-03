# 插件管理与未来 Web 接口

## 已有接口

Core 提供传输无关的 ManagementResource<Q,R>、ManagementGateway、ManagementContext 和 ManagementAuthority。4.7.0-l 的可信 Web 入口复用原网关，按本地 management-resources 白名单开放 [Agent 工作区](../web-console-agent.md)的五项资源；插件/实例/图库资源尚未直接发布网页路由，不能据此推断它们已经对外开放。

插件实现 ManagementResource 并注册 Bean，声明 descriptor()、requestType() 与 execute(context, request)。描述包含 pluginId、局部 id、schemaVersion、permission、mutation 和字段说明；资源 ID 为插件ID:局部ID。重复 ID 或未知所属插件拒绝注册。

requestType 返回具体请求 DTO 的 Class，宿主按该类型解析与验证；客户端不能传任意类名、方法名、Bean 名或 SQL。execute 返回真实 CompletionStage<View>，只导出明确 DTO，不导出 Entity、整个组件对象、控制、凭据或 Throwable。字段 schema 是说明，不取代请求和业务校验；敏感字段禁止回显。

当前内置资源：

| ID | 请求及权限 | 返回 |
| --- | --- | --- |
| teaneko.core:plugins | PluginCatalogResource.Query；plugins.inspect，全局授权 | PluginRegistry.View 列表：声明、当前有效开关、revision |
| teaneko.agent:instances | AgentManagementResource.Query(after, limit)；agent.inspect，全局授权 | 实例 ID、定义 ID、显式声明能力、全局注册能力目录及关闭状态；limit 为 1–100，末项 ID 可作为后继游标 |
| teaneko.agent:stickers | StickerManagement.Query(kind,after,limit,category,state)；agent.stickers.inspect，scopeRef=global | 图库或作业有界分页、类别与容量统计；没有作者、源 URL 或原话 |
| teaneko.agent:stickers.preview | StickerManagement.Preview(id,expectedRevision)；agent.stickers.preview，scopeRef=global | 最多 1.5MiB 的单幅合成 PNG 内联预览 |
| teaneko.agent:stickers.change | StickerManagement.Change；agent.stickers.manage，scopeRef=global | 按版本固定、停用、删除、恢复、修正或重新分析的持久回执 |
| teaneko.agent:stickers.operation | StickerManagement.OperationQuery(operationId)；agent.stickers.inspect，scopeRef=global | 当前主体本人原操作的真实回执 |

availableFeatures 是可安装目录，不代表全部启用或当前主体获准执行；declaredFeatures 是实例显式声明，不把默认/依赖展开伪装为用户声明。全局实例视图没有 group 过滤参数，不得授予仅有某群管理权限的主体。

## 默认拒绝的授权边界

ManagementAuthority 由宿主提供唯一明确实现，缺失时所有发现与执行都拒绝。ManagementGateway.describe(context) 按每项权限筛选可发现资源；execute(key, context, request) 在实际执行前及返回前重新授权，并复核控制和插件版本。发现失败不等于证明资源不存在，不要将不可见项用于判断其他用户数据。

ManagementContext 的 subject、scopeRef 来自已认证适配器，requestId 由宿主生成或验证，control 由管理入口拥有。它不携带客户端自报角色，也不被直接转换为原生 AgentActorContext.USER。Web 登录、群 OWNER、DEBUG 以及跨空间查看的映射必须显式实现。

4.7.0-l 新写用例在实际数据库事务或外部副作用前调用 context.checkActive()，同时复核控制与宿主捕获的实时授权。原四参数构造保留，资源权限仍由 ManagementAuthority 检查。Web 授权要求同一在途上下文对象、真实会话、白名单与插件代次，不按主体字符串放行；唯一 Authority 不默认为其他传输授予权限。Web 管理源为独立 MANAGEMENT，不能提交到模型活动或借用平台用户身份。

长期连接及 SSE/WebSocket 尚未提供。未来连接适配需要按主体/范围过滤、重新授权、处理撤权及断连，并释放订阅；不能把内部可修改决策事件直接推给浏览器。

## 编写管理操作

先将命令适配器中的业务编排提取为应用用例，由命令和管理资源共同调用原领域服务。只读检查提供不可变 View 和有界分页，管理写入则使用明确请求 DTO，而非通用对象字段反射。

写请求携带 operationId、预期 revision/generation 以及业务参数。领域服务负责持久幂等、原子版本仲裁、真实回执和审计；Gateway 只做路由和授权，不自动为插件补出这些保证。返回阶段失败不能证明此前没有提交，长操作应提供 operationId 查询真实终态。

Agent Store 仍经 TeaNekoAgentEasyData；普通字段复用 ConfigData 校验，namespace/人格绑定走专用用例。4.7.0-l 的 ConfigEditService 在同一原 EasyData 事务中完成版本检查、局部合并、幂等和审计，不在 Controller 先读版本再无条件覆盖。

聊天设置扩展实现 IChatSettingsSection，标记 @TeaNekoChatSettings("稳定区标识") 并注册 Spring Bean，复用 scanner。声明中文字段视图、固定 ConfigManager 和字段白名单，配置区须列入本地 settings-sections 且所属 @PluginOwned 插件启用。不得通过普通字段开放凭据或特殊绑定。平台由 IClientManagementAdapter 贡献 accounts、targets、resolve、discover；resolve 校验平台/账号/目标关系，实际提交用 ClientManagementDirectory.guard 复核插件代次。已发布 HTTP 路径和边界见[聊天设置](../web-console.md#聊天设置)。

图库请求与视图定义在 StickerManagement，业务服务为 StickerManagementService，四项资源为自动注册的 StickerManagementResources Bean。例如先通过 stickers 查询得到 id/revision，再提交 Change(UUID.randomUUID(),id,revision,Action.PIN,null,null)。超时后保留同一 operationId 查询 stickers.operation；在保留窗口内使用完全相同请求重放亦可，不能生成新 ID 猜测上次是否提交。

图库幂等回执与目录写入在同一 DTO 根事务，保留 24 小时且最多 512 项；冲突或窗口满拒绝，授权在执行及返回时复核。DELETE 保留拒绝同图自动复活的墓碑，RESTORE 才解除；REFRESH 要求明确已登记分析区域，只借用 SYSTEM 视觉活动和原有预算，不把管理身份变成聊天 USER。各动作和预览/刷新文件占用的完整约束见[素材模块](../../../src/main/java/org/zexnocs/teanekoagent/sticker/README.md#全局管理-spi)。

## 客户端指令台扩展

平台适配器可实现 `IClientManagementAdapter.consoleState/consoleLease`，通过原 Spring、注解及 scanner 注册。只有真实账号、目标与连接可确认时才返回固定 Lease；实际发送须复核原会话、插件、平台状态和该连接，不自动改投、广播或重试。默认实现明确不支持。

命令扩展沿用原命令注解和 `@Description`，另外声明 `@ManagementCommand` 与 `CommandData<ConsoleInvocation>`。权限保持原体系，平台管理权限不等于群成员身份。完整示例、HTTP 路径及回执限制见[客户端指令台](../web-console-commands.md)。

独立 `web` 平台已经通过同一管理适配器接入聊天设置和 Agent 工作区。插件可监听 `WebMessageReceiveEvent`，新增工具声明 `@TeaNekoAgentToolClass(client = "web")`；来源权限、持久回执和媒体限制见[网页聊天](../web-console-chat.md)。

## 文件配置扩展

Spring Bean 实现 `IFileSettingsAdapter`，标记 `@TeaNekoFileSettings("稳定配置区")`，由原 scanner 注册。声明固定配置类型、中文字段、秘密标记、集合上限和应用方式；同一区键或文件类型重复会拒绝装配。该区还须加入本机 `file-sections` 白名单，`@PluginOwned` 继续限制实际使用。认证根配置禁止注册。

`validateDocument` 检查未展开文档的结构歧义，`validate` 复用模块候选校验；两者不发布缓存。`applicationFor` 可根据固定资源变更要求重启；`verifyApplied` 通过原模块只读入口确认本次发布。钩子同步执行，不联网、不发送消息、不触发模型或迁移，不持锁等待异步结果，也不申请其他配置类型锁。不能可靠确认热应用的模块声明 RESTART。

原解析器实现 `IFileDocumentParser` 后才支持受限原树操作。插件不能从请求取得文件路径或 Java 类型，不导出候选对象、原文及隐藏字段；实际保存、版本检查、私有账本和权限复核统一由宿主执行。完整回执与文件系统边界见[文件配置说明](../web-console-files.md)。

## 后续业务资源与页面

4.7.0-l 已提供 Core 运行/日志与聊天持久计划管理，实际边界见[任务与日志](../web-console-runtime.md)。实例级贡献实现 GlobalManagementResource，仍使用原网关、插件目录及明确宿主授权；不能将聊天权限当作全局权限。Web 对两类资源分别路由并检查本机白名单，不新增默认允许的 Authority。

[Web 管理设计](../../design/web-management-api.md) 保留了记忆、群配置、任务/Timer/运行实例、运行报告、日志及实时事件的资源形状。当前已经落地的是插件管理 SPI、插件与实例查询以及四项图库资源；其他业务资源需要各自完成授权映射、结构化 DTO、版本更新或持久历史后发布，不提供空路由。

Timer 定义、持久计划和单次 TaskRun 分开展示。底层命令调用任务可能先结束，外层业务 Stage 尚未结束，页面应跟踪业务 runId 的真实阶段。取消请求不等于已撤销副作用，INDETERMINATE 不显示为安全可重试。

插件表单和页面优先使用受限 schema、表格与动作描述；复杂界面将来再制定页面扩展协议。浏览器中的记忆、日志及插件文本按非可信内容渲染，运行状态使用只读观测快照，审计使用独立可靠记录。
