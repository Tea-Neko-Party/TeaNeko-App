# Web 管理接口与控制台设计入口

目标版本：v4.7.0-l；本期功能与约定离线验收已完成，见[归档记录](../plan/old/4.7.0-web-console.md)。下方仍包含后续资源的设计建议，实际已接通路径以对应操作说明为准，不代表已经正式发布或通过实机验收。

设计日期：2026/11/02；2026/11/03 补充客户端导航、文件热更新、指令台、Web client 及公网令牌登录。本文及 [插件扩展设计](agent-plugin-extensibility.md) 保存总体合同；除下方已交付部分外，接口名称、DTO 和 HTTP 路径仍为建议，不能据此推断完整网页已实现。

本页维护后端分层、资源和真实终态合同；[控制台体验与客户端设计](web-console-experience.md)维护用户需求、可理解的界面映射、前端候选及 Web client；[登录与公网安全设计](web-console-security.md)维护令牌生成、会话、入口鉴权与安全验收。只读本次涉及的权威章节，不把这些建议接口视为已有运行能力。

## 目标与分层

实施更新：已有通用管理 SPI、插件/实例查询及四项图库资源，见[管理扩展指南](../development/plugins/management.md)。Web 已接通基础控制台、聊天配置、六项聊天 Agent 资源、两项实例级运行/日志资源、[客户端指令台](../development/web-console-commands.md)和[独立网页聊天](../development/web-console-chat.md)；实际路径与限制见各操作说明。插件/实例/图库资源尚未直接发布网页路由。

覆盖 Agent 记忆检查、群组及其他 scope 配置、任务/定时器待执行与执行中状态、运行报告、日志，以及未来插件管理页面。接口应服务整个 TeaNeko App，Agent 是其中一个业务模块。

依赖方向：Web/聊天命令适配器 → 应用查询与操作端口 → 原领域服务 → Core/EasyData。Core 提供通用查询分页、操作标识、任务观测和通知合同，不依赖 HTTP、Agent 或前端框架。Web 负责认证接入、协议映射及连接管理；业务层不依赖 Controller、Servlet、WebSocket 会话或浏览器状态。

管理端口与插件执行 SPI 分开：执行 SPI 服务 Agent 运行；管理端口服务操作者检查及修改。两者共用权威业务服务与授权，不另建可绕过版本校验的管理 Store。聊天命令也逐步调用同一应用端口，返回中文展示与 JSON 视图各由适配层处理。

## 当前基础与待补项

| 当前基础 | 可复用内容 | 需要补充 |
| --- | --- | --- |
| 记忆管理命令、管理存储端口及 namespace/事实模型 | 原代次/版本、分页、管理动作与幂等回执 | 已接入三层查询、独立 Web 管理、证据画像和人格补正；当前事实克隆不等于完整三层备份 |
| ConfigData、字段检查器及 Agent scope 绑定端口 | 异步持久化、字段约束和权威 namespace 绑定 | 已接入中文字段、事务内版本比较与审计；特殊绑定继续走专用管理 |
| AgentScheduleService | create/detail/list/cancel，以及持久化状态、revision、nextFire | 执行实例明细、当前阶段、运行取消状态和受限全局查询；暂停/恢复尚不能根据状态枚举推定为已有操作 |
| Core ITimerService、任务状态与 TaskStageChain | 原调度器、执行域及任务生命周期 | 通用定时器目录、可查询运行快照和阶段观测，不能让 Web 反射遍历内部线程或 Future |
| AgentRuntimeMonitor、报告与 AgentActivityTimeline | 原轮次用量、生命周期和真实逐段回执 | 已有有界结构化时间线；逐工具细节、全部后台节点和持久活动账本尚未提供 |
| Core 事件及应用日志 | 内部事件分发、原日志来源 | 对外事件映射、订阅授权、有界缓冲；历史日志查询及保留策略需单独实现，内部总线不等于历史数据库 |
| TeaNekoClient scanner、平台适配与 OneBot 连接 | client 注册、真实消息入口、群/私聊定位及原命令 Stage | 指令台已接通；OneBot 要求唯一已确认账号并锁定原连接，多账号定向仍待扩展 |
| FileConfig 与 Core YAML 环境解析 | 已注册配置、字段解析、缓存和重载 | 原文档版本/局部编辑、原子替换、应用方确认、脱敏与占位符保留；现有 write/reload 不等于跨模块热更事务 |
| 通用消息接口与独立 WebTeaNekoClient | 原消息、命令、工具箱、Agent 和逐段回执 | 已接通持久私聊/模拟群、成员权限、认证图片与游标补读，不依赖测试类或复用 OneBot 身份 |

源码依据：[记忆管理](../../src/main/java/org/zexnocs/teanekoplugin/agent/cognition/memory/admin/README.md)、[配置写入端口](../../src/main/java/org/zexnocs/teanekocore/database/configdata/interfaces/IConfigDataSetService.java)、[任务服务](../../src/main/java/org/zexnocs/teanekoplugin/agent/automation/schedule/AgentScheduleService.java)、[任务状态](../../src/main/java/org/zexnocs/teanekoplugin/agent/automation/schedule/AgentScheduledTask.java)、[定时器端口](../../src/main/java/org/zexnocs/teanekocore/actuator/timer/interfaces/ITimerService.java)、[运行监控](../../src/main/java/org/zexnocs/teanekoagent/runtime/monitor/AgentRuntimeMonitor.java)。

## 统一应用合同

以下按职责拆成小接口，不建立囊括整个项目的巨型服务。异步读写以 CompletionStage<T> 表达真实结果，新增命令入口继续返回 CompletionStage<?>。查询返回不可变 View DTO，不直接序列化 Entity、数据库对象、ExecutionControl、Throwable 或 Spring Bean。

| 建议端口 | 建议操作 | 主要合同 |
| --- | --- | --- |
| 可信 Web 会话入口 + 现有 ManagementAuthority | 登录、注销、会话查询；authorize | 本地令牌换取可撤销会话，显式映射服务器角色和资源权限；浏览器不能提交角色或 DEBUG，不另造平行管理网关 |
| ClientManagementAdapter + 注册注解/目录 | describe、listAccounts、listTargets、resolveTarget、readiness | scanner 发现明确支持可视化的 client；中文平台/账号/群聊/私聊映射实际配置，客户端不拼接 scope |
| ScopeManagementQuery / ScopeManagementCommands | list、detail、describeFields、updateFields | 查询配置和字段约束；写入携带预期版本，特殊绑定走专用操作 |
| FileConfigManagementQuery / Commands | describe、readEditable、validate、save、apply、operation | 受控配置标识、原始文档版本、局部修改与秘密保留；磁盘保存和运行生效分别确认 |
| ClientConsoleCommands | describeSupported、submit、operation | 选真实已就绪目标，经显式管理来源和命令权限映射执行，不伪造原平台成员或开放任意 API |
| WebChatCommands / Query | createRoom、listMembers、submitMessage、listMessages、receive | 生产级独立 client，群/私聊、服务端身份、持久消息和重连；不另建模型循环 |
| AgentManagementQuery | list、detail、listFeatures、describePipeline | 查看实例、安装能力、有效状态及脱敏组件视图；不暴露任意组件对象或执行句柄 |
| AgentMemoryQuery / AgentMemoryCommands | listNamespaces、listFacts、detailFact；edit、lock、clone、clear | 固定 namespace/generation，遵循既有权限及版本；画像、人格作为独立子资源扩展 |
| ScheduleManagementQuery / ScheduleManagementCommands | listSchedules、detail、listRuns；create、cancel | 区分计划与执行实例，复用原防重及实际回执；仅声明真实支持的动作 |
| TaskRuntimeQuery / TaskRuntimeCommands | listTimers、listQueuedRuns、listRunningRuns、detailRun；requestCancel | Core 通用观测；取消只请求协作终止，不承诺副作用撤销 |
| RuntimeReportQuery / LogQuery | queryReports、queryLogs | 时间、等级、scope、插件及关联 ID 筛选；返回结构化、脱敏、有界分页结果 |
| ManagementEventSource | subscribe、resume | 面向外部的只读状态事件，绑定主体、范围、过滤和订阅寿命；提供断流与重新同步语义 |
| ManagementContributionRegistry | describeResources、describeActions、describeViews | 插件贡献可管理资源、配置 schema 和可展示能力，宿主验证归属/版本并统一授权 |

查询上下文至少包含可信管理主体、授权目标、requestId 和取消/超时信息。界面和外部请求按平台、账号、群聊/私聊目标组织，使用宿主生成的 targetRef；scope 为内部实际映射，可在高级详情定位，不能让浏览器拼接群号/UUID 来决定权限。现有私聊配置可能由相同用户 UUID 共享，界面明确显示影响，不擅自更改存储语义。Web 主体经显式授权映射到既有权限；当前 AgentActorContext 的 USER 原生来源约束不允许靠伪造聊天消息绕过。需要增加可信管理来源时同步扩展授权合同并验证原边界。

列表采用有上限的游标分页并规定排序。View 带必要的 revision、generation、updatedAt、observedAt 和 supportedActions；具体版本字段按资源定义，不要求所有 DTO 都有同一套字段。supportedActions 仅用于显示，执行时重新授权。分页中途换代明确冲突或要求重新查询，不混合不同记忆代次。

## 管理写入与长操作

- 写入带 operationId 及预期 revision/generation，服务端将参数与幂等回执绑定；相同操作重放不重复执行，改变参数返回冲突。已提交回执不等于当前资源仍处于该版本。
- 预期版本检查与写入必须在同一业务提交边界仲裁；缺少能力时先补 EasyData/领域服务，不在 Web 内缓存或直接操作 Repository。
- 保留现有 OWNER/DEBUG 边界，Web 登录本身不授予记忆查看/导出或维护资格。跨群、跨空间全局查询单独授权，不把页面可见性当作授权。
- 群组配置字段提供类型、说明、默认值、约束、是否敏感、能否编辑及重启要求。namespace、人格 key 等特殊字段仍走明确管理用例，不通过通用反射 PATCH 绕过规则。
- 普通视图不返回密钥；前端只显示是否声明，不证明远端凭据有效。模型仍以 model.yml 为唯一配置来源；[文件编辑](../development/web-console-files.md)保留未修改的原模板，磁盘保存与运行应用分别确认，不把环境变量展开值自动写回。
- 长操作返回可查询的 operationId，分别记录已受理、执行中、成功、失败、结果不明及请求取消。HTTP 已受理不是成功，连接断开不是回滚；用操作查询确认真实结果。
- 管理审计记录操作者、目标、动作、预期版本、结果及关联 ID，不记录凭据和完整敏感正文。需要可靠追溯的审计使用持久化记录，不依赖可能丢弃的监控通知。

## 任务与计时器展示语义

分开描述三类资源：TimerDefinition 表示调度规则及下一次预计触发；ScheduleView 表示持久化业务计划；TaskRunView 表示某次实际运行。它们通过 timerId、scheduleId、runId、parentRunId 和 requestId 关联，不能用一个 status 字段混淆。

TaskRunView 预留创建/入队/开始/结束时间、当前阶段及阶段开始时间、可选进度、预算摘要、取消请求和安全结果码。排队表示实际已进入执行队列，未来计划只表示尚待触发；RUNNING 表示业务异步链仍未结束，不能在提交 Callable 或启动 IO 时提前完成。没有可测总量时进度为未知，不编造百分比。

当前 Agent 计划状态为 ACTIVE、RUNNING、COMPLETED、CANCELLED、EXPIRED、PAUSED、INDETERMINATE，保留领域语义。新增展示状态不得修改旧状态含义，也不能据 PAUSED 枚举就对外承诺已有暂停/恢复操作。取消运行与取消未来计划分别建模；不明副作用保持不明，不在页面提供无条件重试。

Core 为现有 Timer/Task 增加只读观测端口，使用明确生命周期节点更新快照；Agent 扩展自己的业务字段。观测失败不能阻塞执行。运行视图可为有界内存窗口，重启后明确标记窗口已重置；历史查询只有在相应持久化实现启用时承诺可用，不能将内存状态宣传为持久任务账本。

## HTTP 与实时通知映射

建议使用 /api/v1，应用端口保持与传输无关。下列是预留资源形状，只有对应应用用例完成后才发布路由，不提供空 Controller 或恒定成功响应。

| 路由形状 | 用途 |
| --- | --- |
| POST /api/v1/auth/login；POST /api/v1/auth/logout；GET /api/v1/auth/session | 受限令牌登录、服务端注销及当前认证会话；只有登录入口用于认证前请求，不返回根令牌 |
| GET /api/v1/capabilities | 当前版本、模块、可管理资源、字段 schema 和实际支持动作 |
| GET /api/v1/clients；GET /api/v1/clients/{clientRef}/accounts | 已实现管理适配的平台、真实账号/连接和能力 |
| GET /api/v1/clients/{clientRef}/accounts/{accountRef}/targets | 中文群聊/私聊目录，带不透明 targetRef、共享标记和发现来源 |
| GET/PATCH /api/v1/targets/{targetRef}/config | 经适配器映射与授权的聊天配置和普通字段版本更新，不接受原始 scope 拼接 |
| GET /api/v1/file-configs；GET /api/v1/file-configs/{configRef} | 已注册文件的受控可编辑视图、字段与生效能力，敏感文件不提供原文下载 |
| POST /api/v1/file-configs/{configRef}/actions/validate；POST /api/v1/file-configs/{configRef}/actions/save | 局部配置预检、版本保存及应用回执，不接受任意路径 |
| POST /api/v1/file-configs/{configRef}/actions/apply；GET /api/v1/file-configs/{configRef}/operations/{operationId} | 独立应用固定磁盘版本，查询私有持久回执；保存不自动发布缓存 |
| GET /api/v1/agents；GET /api/v1/agents/{agentId}/features | 实例及其安装能力、阶段描述与允许展示的组件信息 |
| GET /api/v1/agent/namespaces/{id}/facts | 固定 generation 的记忆分页检查 |
| POST /api/v1/agent/namespaces/{id}/actions/{action} | 明确注册的编辑、锁定、克隆、清空等管理操作，使用独立类型化请求 |
| GET/POST /api/v1/agent/schedules；GET /api/v1/agent/schedules/{id} | 业务计划查询及创建 |
| POST /api/v1/agent/schedules/{id}/actions/cancel | 取消业务计划 |
| GET /api/v1/runtime/timers；GET /api/v1/runtime/runs | 通用定时器目录和带状态筛选的运行查询 |
| GET /api/v1/runtime/runs/{id}；POST /api/v1/runtime/runs/{id}/actions/cancel | 运行详情和取消请求 |
| GET /api/v1/runtime/reports；GET /api/v1/logs | 运行报告与日志筛选 |
| GET /api/v1/operations/{id} | 管理操作真实终态及安全结果 |
| GET/POST /api/v1/clients/{client}/accounts/{account}/targets/{target}/console；GET .../console/operations/{operationId} | 已交付：明确管理来源、固定账号/聊天及连接，读取目录、提交原命令并查询持久事实 |
| GET/POST /api/v1/chat/rooms；GET/POST /api/v1/chat/rooms/{roomRef}/messages | 已交付：网页私聊/模拟群与持久消息，服务端检查档案、成员与原操作编号 |
| POST /api/v1/chat/profiles；POST /api/v1/chat/rooms/{roomRef}/members | 已交付：原 TeaUser 映射与专用版本化成员管理，消息不能自报 UUID 或角色 |
| GET /api/v1/chat/rooms/{roomRef}/operations/{operationId}；GET/POST .../media；GET/DELETE .../media/{id} | 已交付：入站事实、认证媒体和未引用图片清理；写入/删除要求原 CSRF |
| GET /api/v1/events | 已授权增量通知；首选 SSE，未来需要双向交互再增加 WebSocket 适配 |
| /api/v1/plugins/{pluginId}/resources/... | 经宿主显式注册的插件管理资源，不接受任意类名或方法名调用 |

错误映射区分未认证、禁止、资源不可见/不存在、版本冲突、校验失败、限流与依赖不可用，包含稳定 code、中文 message、requestId 和必要冲突信息。是否隐藏资源存在性由授权策略统一决定。HTTP 受理长操作时再返回 operationId；短操作可直接返回真实提交结果。

实时事件建议包含 eventId、type、schemaVersion、occurredAt、resourceRef、resourceRevision、关联 ID 与脱敏 payload。对外事件从内部观测事件显式映射，不把可修改的决策事件或任意 Java 对象推送给浏览器。状态变更只有提交确认后才发布；普通事件投递与事务并非原子，需要可靠保证的特定场景再引入事务发件箱等机制。

实时流用于更新提示，查询服务提供权威当前状态。提供有界重放窗口及续传游标；过期、重启、拥塞丢弃时明确通知重新同步。必须处理首次快照与订阅之间的间隙：在实现的窗口内记录游标并重放，检测缺口就重新拉取，不承诺无限历史或精确一次投递。前端按资源版本去重，禁止旧通知覆盖新状态。

慢客户端使用有界队列、批量/合并及断流策略，不阻塞 Agent 主链。连接按主体及范围过滤，权限撤销或会话失效时终止或重新授权；断开连接释放订阅。日志正文、插件文本及记忆按非可信文本渲染，服务端脱敏并限制体积。浏览器认证、跨域、会话及请求伪造防护由 Web 适配层统一实现后再开放网络访问。

## 插件管理扩展

插件管理贡献声明 resourceType、schemaVersion、配置字段、查询能力、可执行动作和通知类型；每项绑定插件 ID、revision 与权限能力。宿主挂载资源、合并能力目录并处理冲突。没有配置或管理动作的插件可以不提供管理贡献。

基础前端可根据受限 schema 渲染表单、表格与详情，复杂插件未来再提供明确版本的页面扩展协议。首阶段只描述数据与动作，不要求运行插件自带前端脚本。前端未知资源类型可显示通用摘要，未知字段按版本合同兼容。

组件注册时另提供可选 ComponentView 描述器，声明允许展示的只读字段及脱敏规则；不能通过反射直接输出整个组件。事件贡献区分内部决策与公开观测，Stage 拦截器提供节点描述和耗时信息，不暴露修改运行链的任意管理接口。插件启停仍走原调用租约及资源回收合同。

## 实施顺序与验收

1. 令牌/会话、全入口安全、基础前端与适配器目录；以 OneBot 群/私聊的可理解配置查询、校验、版本更新及审计交付第一个可用闭环。所有公网入口先有明确认证策略。
2. Agent 当前活动、工具及交付时间线、三层记忆/画像/人格检查和原管理动作；提供有界实时通知及真实进度，不输出隐藏推理或伪造完成。
3. 本地 FileConfig 编辑、占位符与秘密保留、版本保存及按模块热应用；明确保存、生效和重启三个边界。
4. Core Timer/Task 的结构化观测、Agent 持久计划与真实异步终态，加上可筛选日志及其有界窗口/历史策略。
5. 客户端连接/身份快照及就绪守卫、可信管理来源、明确支持的指令台；OneBot 唯一账号可操作后才开放，真正多账号先补定向路由。
6. 独立 Web client 的私聊/群聊、受控模拟身份、媒体、分段回执和重连；与现有 Agent、记忆、权限和 client 工具范围接通。插件继续通过明确管理贡献扩展，不先执行任意前端脚本。

测试覆盖重要风险：认证及默认拒绝、目标/命令授权、版本冲突与重复操作、换代分页、真实异步终态与取消、订阅撤权/断流、配置应用与占位符保留、客户端断线/换号、模拟身份及秘密脱敏。复用现有业务夹具，不为每个 DTO、机械映射或表格字段建立重复测试。本期离线验收结果以归档记录为准，真实数据库、平台、模型及公网仍按部署清单验证，不以设计篇幅计完成度。
