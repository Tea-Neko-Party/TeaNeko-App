# 插件管理与未来 Web 接口

## 已有接口

Core 提供传输无关的 ManagementResource<Q,R>、ManagementGateway、ManagementContext 和 ManagementAuthority。它们服务全项目，Agent 是其中一个资源提供方；当前没有开放 HTTP、登录接口或前端页面。

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

长期连接及 SSE/WebSocket 尚未提供。未来连接适配需要按主体/范围过滤、重新授权、处理撤权及断连，并释放订阅；不能把内部可修改决策事件直接推给浏览器。

## 编写管理操作

先将命令适配器中的业务编排提取为应用用例，由命令和管理资源共同调用原领域服务。只读检查提供不可变 View 和有界分页，管理写入则使用明确请求 DTO，而非通用对象字段反射。

写请求携带 operationId、预期 revision/generation 以及业务参数。领域服务负责持久幂等、原子版本仲裁、真实回执和审计；Gateway 只做路由和授权，不自动为插件补出这些保证。返回阶段失败不能证明此前没有提交，长操作应提供 operationId 查询真实终态。

Agent Store 仍经 TeaNekoAgentEasyData；群配置普通字段复用 ConfigData 校验，namespace/人格绑定等特殊管理走专用用例。当前 ConfigData 通用字段接口尚无完整的 Web 原子版本更新合同，不能先在 Controller 读一次版本就声称具备 CAS。

图库请求与视图定义在 StickerManagement，业务服务为 StickerManagementService，四项资源为自动注册的 StickerManagementResources Bean。例如先通过 stickers 查询得到 id/revision，再提交 Change(UUID.randomUUID(),id,revision,Action.PIN,null,null)。超时后保留同一 operationId 查询 stickers.operation；在保留窗口内使用完全相同请求重放亦可，不能生成新 ID 猜测上次是否提交。

图库幂等回执与目录写入在同一 DTO 根事务，保留 24 小时且最多 512 项；冲突或窗口满拒绝，授权在执行及返回时复核。DELETE 保留拒绝同图自动复活的墓碑，RESTORE 才解除；REFRESH 要求明确已登记分析区域，只借用 SYSTEM 视觉活动和原有预算，不把管理身份变成聊天 USER。各动作和预览/刷新文件占用的完整约束见[素材模块](../../../src/main/java/org/zexnocs/teanekoagent/sticker/README.md#全局管理-spi)。

## 后续业务资源

[Web 管理设计](../../design/web-management-api.md) 保留了记忆、群配置、任务/Timer/运行实例、运行报告、日志及实时事件的资源形状。当前已经落地的是插件管理 SPI、插件与实例查询以及四项图库资源；其他业务资源需要各自完成授权映射、结构化 DTO、版本更新或持久历史后发布，不提供空路由。

Timer 定义、持久计划和单次 TaskRun 分开展示。底层命令调用任务可能先结束，外层业务 Stage 尚未结束，页面应跟踪业务 runId 的真实阶段。取消请求不等于已撤销副作用，INDETERMINATE 不显示为安全可重试。

插件表单和页面优先使用受限 schema、表格与动作描述；复杂界面将来再制定页面扩展协议。浏览器中的记忆、日志及插件文本按非可信内容渲染，运行状态使用只读观测快照，审计使用独立可靠记录。
