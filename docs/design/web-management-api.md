# Web 管理接口预留设计

设计日期：2026/11/02。依据用户提出的全项目 Web 可视化需求制定接口合同。本文及 [插件扩展设计](agent-plugin-extensibility.md) 属于待实施设计；接口名称、DTO 和 HTTP 路径均为建议，当前没有对应完整实现，不计入既有 S0–S9 完成比例。

## 目标与分层

实施更新：v4.6.0-l 已提供通用管理 SPI、默认拒绝授权路由和插件/实例两个只读资源，实际合同见 [管理扩展指南](../development/plugins/management.md)。本文其余业务端口与 HTTP 路径仍为后续设计。

覆盖 Agent 记忆检查、群组及其他 scope 配置、任务/定时器待执行与执行中状态、运行报告、日志，以及未来插件管理页面。接口应服务整个 TeaNeko App，Agent 是其中一个业务模块。

依赖方向：Web/聊天命令适配器 → 应用查询与操作端口 → 原领域服务 → Core/EasyData。Core 提供通用查询分页、操作标识、任务观测和通知合同，不依赖 HTTP、Agent 或前端框架。Web 负责认证接入、协议映射及连接管理；业务层不依赖 Controller、Servlet、WebSocket 会话或浏览器状态。

管理端口与插件执行 SPI 分开：执行 SPI 服务 Agent 运行；管理端口服务操作者检查及修改。两者共用权威业务服务与授权，不另建可绕过版本校验的管理 Store。聊天命令也逐步调用同一应用端口，返回中文展示与 JSON 视图各由适配层处理。

## 当前基础与待补项

| 当前基础 | 可复用内容 | 需要补充 |
| --- | --- | --- |
| 记忆管理命令、管理存储端口及 namespace/事实模型 | DEBUG、generation/revision、分页、编辑/锁定/克隆/清空及幂等回执 | 将命令适配中的编排下沉为可复用管理用例；增加结构化视图及可信 Web 身份适配 |
| ConfigData、字段检查器及 Agent scope 绑定端口 | 异步持久化、字段约束和权威 namespace 绑定 | 字段描述/可见性、结构化查询；并发更新所需的原子预期版本校验，不能只在 Controller 先读后写 |
| AgentScheduleService | create/detail/list/cancel，以及持久化状态、revision、nextFire | 执行实例明细、当前阶段、运行取消状态和受限全局查询；暂停/恢复尚不能根据状态枚举推定为已有操作 |
| Core ITimerService、任务状态与 TaskStageChain | 原调度器、执行域及任务生命周期 | 通用定时器目录、可查询运行快照和阶段观测，不能让 Web 反射遍历内部线程或 Future |
| AgentRuntimeMonitor 与报告事件 | 有界近期报告、用量及交付语义 | 可查询的结构化快照；现有 describe 返回中文字符串，不能让前端解析字符串来取指标 |
| Core 事件及应用日志 | 内部事件分发、原日志来源 | 对外事件映射、订阅授权、有界缓冲；历史日志查询及保留策略需单独实现，内部总线不等于历史数据库 |

源码依据：[记忆管理](../../src/main/java/org/zexnocs/teanekoagent/memory/admin/README.md)、[配置写入端口](../../src/main/java/org/zexnocs/teanekocore/database/configdata/interfaces/IConfigDataSetService.java)、[任务服务](../../src/main/java/org/zexnocs/teanekoagent/schedule/AgentScheduleService.java)、[任务状态](../../src/main/java/org/zexnocs/teanekoagent/schedule/AgentScheduledTask.java)、[定时器端口](../../src/main/java/org/zexnocs/teanekocore/actuator/timer/interfaces/ITimerService.java)、[运行监控](../../src/main/java/org/zexnocs/teanekoagent/agent/monitor/AgentRuntimeMonitor.java)。

## 统一应用合同

以下按职责拆成小接口，不建立囊括整个项目的巨型服务。异步读写以 CompletionStage<T> 表达真实结果，新增命令入口继续返回 CompletionStage<?>。查询返回不可变 View DTO，不直接序列化 Entity、数据库对象、ExecutionControl、Throwable 或 Spring Bean。

| 建议端口 | 建议操作 | 主要合同 |
| --- | --- | --- |
| ManagementAuthorityPort | resolve、authorize | 从认证适配器取得可信主体，计算目标 scope 的查看/操作资格；客户端不能提交角色或 DEBUG |
| ScopeManagementQuery / ScopeManagementCommands | list、detail、describeFields、updateFields | 查询配置和字段约束；写入携带预期版本，特殊绑定走专用操作 |
| AgentManagementQuery | list、detail、listFeatures、describePipeline | 查看实例、安装能力、有效状态及脱敏组件视图；不暴露任意组件对象或执行句柄 |
| AgentMemoryQuery / AgentMemoryCommands | listNamespaces、listFacts、detailFact；edit、lock、clone、clear | 固定 namespace/generation，遵循既有权限及版本；画像、人格作为独立子资源扩展 |
| ScheduleManagementQuery / ScheduleManagementCommands | listSchedules、detail、listRuns；create、cancel | 区分计划与执行实例，复用原防重及实际回执；仅声明真实支持的动作 |
| TaskRuntimeQuery / TaskRuntimeCommands | listTimers、listQueuedRuns、listRunningRuns、detailRun；requestCancel | Core 通用观测；取消只请求协作终止，不承诺副作用撤销 |
| RuntimeReportQuery / LogQuery | queryReports、queryLogs | 时间、等级、scope、插件及关联 ID 筛选；返回结构化、脱敏、有界分页结果 |
| ManagementEventSource | subscribe、resume | 面向外部的只读状态事件，绑定主体、范围、过滤和订阅寿命；提供断流与重新同步语义 |
| ManagementContributionRegistry | describeResources、describeActions、describeViews | 插件贡献可管理资源、配置 schema 和可展示能力，宿主验证归属/版本并统一授权 |

查询上下文至少包含可信管理主体、授权目标、requestId 和取消/超时信息。scope 使用包含 client、scope 类型和标识的结构化键或宿主生成的不透明引用，不能只用群号导致跨客户端冲突。Web 主体经显式授权映射到既有权限；当前 AgentActorContext 的 USER 原生来源约束不允许靠伪造聊天消息绕过。需要增加可信管理来源时同步扩展授权合同并验证原边界。

列表采用有上限的游标分页并规定排序。View 带必要的 revision、generation、updatedAt、observedAt 和 supportedActions；具体版本字段按资源定义，不要求所有 DTO 都有同一套字段。supportedActions 仅用于显示，执行时重新授权。分页中途换代明确冲突或要求重新查询，不混合不同记忆代次。

## 管理写入与长操作

- 写入带 operationId 及预期 revision/generation，服务端将参数与幂等回执绑定；相同操作重放不重复执行，改变参数返回冲突。已提交回执不等于当前资源仍处于该版本。
- 预期版本检查与写入必须在同一业务提交边界仲裁；缺少能力时先补 EasyData/领域服务，不在 Web 内缓存或直接操作 Repository。
- 保留现有 OWNER/DEBUG 边界，Web 登录本身不授予记忆查看/导出或维护资格。跨群、跨空间全局查询单独授权，不把页面可见性当作授权。
- 群组配置字段提供类型、说明、默认值、约束、是否敏感、能否编辑及重启要求。namespace、人格 key 等特殊字段仍走明确管理用例，不通过通用反射 PATCH 绕过规则。
- 普通视图不返回密钥；前端需要显示凭据状态时只返回“已配置”等描述。模型仍以 model.yml 为唯一配置来源；将来开放文件编辑需另外定义模板保留与原子加载，不能把环境变量展开值自动写回。
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
| GET /api/v1/capabilities | 当前版本、模块、可管理资源、字段 schema 和实际支持动作 |
| GET /api/v1/scopes；GET/PATCH /api/v1/scopes/{scopeRef}/config | 已授权 scope 目录、配置读取与普通字段版本更新 |
| GET /api/v1/agents；GET /api/v1/agents/{agentId}/features | 实例及其安装能力、阶段描述与允许展示的组件信息 |
| GET /api/v1/agent/namespaces/{id}/facts | 固定 generation 的记忆分页检查 |
| POST /api/v1/agent/namespaces/{id}/actions/{action} | 明确注册的编辑、锁定、克隆、清空等管理操作，使用独立类型化请求 |
| GET/POST /api/v1/agent/schedules；GET /api/v1/agent/schedules/{id} | 业务计划查询及创建 |
| POST /api/v1/agent/schedules/{id}/actions/cancel | 取消业务计划 |
| GET /api/v1/runtime/timers；GET /api/v1/runtime/runs | 通用定时器目录和带状态筛选的运行查询 |
| GET /api/v1/runtime/runs/{id}；POST /api/v1/runtime/runs/{id}/actions/cancel | 运行详情和取消请求 |
| GET /api/v1/runtime/reports；GET /api/v1/logs | 运行报告与日志筛选 |
| GET /api/v1/operations/{id} | 管理操作真实终态及安全结果 |
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

1. 插件底座及应用管理边界一并设计，先落实可信管理身份、类型化 DTO 和能力描述；选择群组配置完成查询、校验、版本更新、审计的端到端用例，聊天命令复用服务。
2. 开放记忆只读检查与已有 DEBUG 管理操作，保持代次、共享空间影响和幂等；后续加入人格/画像视图。
3. 为 Core Timer/Task 增加结构化观测，接入 Agent 持久计划与真实异步运行链，交付待执行/排队/运行中/终态展示及明确取消合同。
4. 接入有界实时通知、结构化运行报告和日志查询；注明内存窗口、历史存储及断流限制。
5. 发布 Web 协议与基础界面，验证浏览器认证和真实部署；插件通过管理贡献协议增加自己的页面能力。

测试仅覆盖重要风险：scope/DEBUG 授权、版本冲突与重复操作、换代分页、真实异步终态与取消、订阅撤权/断流及敏感数据脱敏。复用现有业务夹具，不为每个 DTO、机械映射或表格字段建立重复测试。本次仅预留设计合同，尚未实现应用端口、HTTP 路由、前端或实机验收。
