# 原生命令与 Agent 显式入口

本模块提供完整的显式文本聊天、命令工具、基本空间管理及持久化提醒。普通模型聊天需先在 model.yml 配置有效路由、模型与凭据，可使用环境占位符；识别出的当前原文任务不调用模型，创建和执行仍要求目标作用域登记并启用 Agent。密钥不经聊天指令写入。

全部 Agent 子命令已提供中文 `@Description`，沿用现有 subhelp 的参数、权限和 scope 过滤展示；使用原命令系统的 subhelp 入口查询。显式聊天与普通原生消息共用模型身份前缀，显示元数据不作为原文凭据，不在机器人回复中增加前缀。

提醒入口为 `/agent remind <ISO偏移时间> <时区> <重复秒数，单次填0> <正文>`、`/agent tasks [游标]`、`/agent task <任务UUID>` 和 `/agent canceltask <任务UUID>`；当前原生 `/agent chat 10分钟后提醒我喝水` 等有界表达也可直接创建、查询和取消。时间、当前身份、禁用后的管理权限和实际发送限制统一见 [任务说明](../../component/schedule/README.md)，本模块不重复维护任务规格。

```text
/cfg reg agent
/agent chat 你好
/agent status
/cfg set agent commandToolsEnabled true
/agent chat 请帮我执行 /签到
```

命令工具默认关闭，OWNER 可通过现有 `/cfg` 权限链启用。普通模型服务、系统任务或没有真实原生来源的宿主调用不会自动获得该工具；实例自身还须允许 TOOL 和 COMMAND 活动。所有已注册命令都可参与原匹配，无第二份命令白名单；原权限、scope、客户端、参数、启用状态和事件检查继续执行。

| 入口 | 行为与权限 |
| --- | --- |
| `/agent` | 显示当前已实现的帮助；未登记也可查看。 |
| `/agent chat <文本>` | 已启用作用域的显式文本聊天，按共享邮箱排队，使用原发送器回复。 |
| `/agent status` | 查看登记/启用、当前空间和本人在途请求 UUID，不展示他人原文；不创建默认空间或承诺模型已就绪。 |
| `/agent stop <请求UUID>` | 只请求停止本人运行/排队项；不回滚已开始业务。 |
| `/agent namespaces [偏移]` | 分页列出本人创建的空间；每页沿用本地个人配额且最多一百条。 |
| `/agent reset` | OWNER/DEBUG 创建并切换新空记忆空间，默认每人最多五个活动自建空间，可本地配置。 |
| `/agent setnamespace <已有空间>` | OWNER/DEBUG 绑定已有共享空间，非 DEBUG 也可绑定他人空间；不要求空间属于本人。 |

私聊角色沿用平台现有 OWNER 规则，群成员不能执行 reset/setnamespace。未注册或禁用时不启动对话、命令工具或空间写入；状态与帮助可以说明如何开启。reset 不物理删除旧数据，不清除人格补正；操作真实提交后读取当前权威引用，协调同 scope 的所有 Agent 实例、窗口和完成记录。同步观察中断时明确反馈“已提交、需核对”，不声称回滚。

当前 namespace 的权威值保存在原绑定服务中，`/cfg set agent memoryNamespace <已有空间>` 已与 setnamespace 共用同一边界；`/cfg agent` 读取权威值，注册/注销保留原绑定，不向 ConfigData DTO 写入副本。App 通过 IConfigCommandFields 扩展独立业务字段，拒绝重名及覆盖 DTO 字段，Core 配置服务不依赖 Agent。

DEBUG 已提供事实分页、JSON 导出、编辑/锁定、快照克隆、clear 及分批旧代次回收，参数和事务边界见 [记忆管理说明](../../component/memory/admin/README.md)。人格补正的 DEBUG key 绑定、备份/克隆、分页/导出、编辑/锁定和撤销也已接入，参数与共享会话边界见 [人格管理](../../component/personality/README.md#debug-补正管理)。自动群/私聊触发、旁观采集、模型发送计划、内部 Client/Sender 迁移、图片与主动任务的完整平台接入仍待后续阶段，本模块提供显式文本入口。

Skill 已支持本地目录、按需加载及作用域启停。`/agent skills` 查看本 scope 的显式选择，`/agent reloadskills` 仅 DEBUG 可重载本地完整目录；OWNER 使用原 `/cfg` 设置 skills 与 skillsEnabled。Skill 不额外授予工具，参数及文件格式见 [Skill 说明](../../component/skill/README.md)。

## 实际请求与权限

`AgentCommandGateway` 在首次实际聊天时创建自己的默认定义实例，不在构造时读取配置或建立模型连接。一个实例下仍按完整 client/scope 隔离会话；服务关闭只移除自己的实例。普通显式聊天正文仅去掉 `/agent chat` 语法前缀，内部空白保留为真实当前原文；从命令工具派生的子请求没有原文凭据，不借模型生成的文本授权事实新增或自动人格学习。

`NativeCommandBridge.open` 返回需要由宿主释放的来源租约。租约保持整个排队及模型/工具往返，结束后解除，最多同时保留一千个来源。原生客户端、消息及权限能力不进入 ECS、模型参数或数据库。工具选择和执行同时核对原请求、actor、引用、来源租约；异步空间/人格检查前后重新读取 commandToolsEnabled、准入及实例登记，不把旧允许视为永久授权。

命令文本通过 `TeaNekoCommandConverter.parseCommand` 复用原文本分词，`CommandData` 保留真实 rawData 类型、客户端类型、用户 UUID/平台身份和 scope。body/args 表示模型选择的命令；rawData 仍是用户实际原消息，其附件和正文不会被伪造或就地修改。直接从 rawData 正文另行解析命令的旧处理器仍看到用户原消息，必要时应按自身职责改用已解析参数或显式适配。

分发使用原 `CommandDispatcher`，不重新推送普通入站消息，不直接反射绕过权限。原命令负责自己的业务反馈；`command_dispatch` 只回传处理状态及 `feedbackOwner=NATIVE_COMMAND`，不会把任意业务返回对象或平台反馈反采进 Prompt。COMPLETED 仅表示处理方法/反馈 Stage 已结束，不能据此宣称签到等具体业务成功。当前工具需要已知的命令文本，尚不提供完整动态命令目录。

## 防重、取消与重放

`ICommandAttemptStore` 的默认适配器在 `tea_neko_agent_data` 独立 `agent.command.v1` 域保存明确 schema。只保存原身份/空间、文本摘要及终态，不保存原生命令正文、客户端或任意返回对象。唯一键和原 DatabaseTask/Version 重试仲裁执行权，只有占用真实提交后才允许副作用。

执行权按种类分别定位：CHAT_ENTRY 固定原生 client/scope/消息的稳定请求 ID，TOOL 固定原请求 ID 与原工具调用 ID；COLLECT_ENTRY 用于旁观采集，PROACTIVE_ENTRY 用于无用户权限的 SYSTEM 自主发生。原生消息在调用模型前先占用，进程重启后不会用新工具 ID 重跑同一消息；具体工具重放取得原终态，同 ID 改参数或身份返回 CONFLICT。在途记录默认 INDETERMINATE，不因超时、服务重建或源租约关闭而自动接管。前台原文和答案不通过该账本恢复，需要新操作时发送新消息。

`/agent monitor` 仅真实 DEBUG 可查看当前 scope 的脱敏运行统计，详见[运行监控](../../runtime/monitor/README.md)。

同进程缓存最近最多一千个聊天/发送阶段，相同消息复用整个完成信号，避免重复发送。缓存满时只淘汰已结束项，在途满额明确拒绝。持久化防重记录不随内存缓存、namespace reset 或 clear 自动删除；清理前必须另外确认重放和回退需求。

`CommandData.parentExecution` 是宿主借用链路，序列化时忽略，不授予权限。事件提交与真正命令任务开始前均检查父取消，实际执行器等待真实 Stage；自定义执行器应遵守同一合同。派生 Agent 请求保留父控制，由既有邮箱识别等待自身会话的 REENTRANT，不另开预算或使用 ThreadLocal。

停止只结束观察及后续步骤，已启动的命令可能继续提交或反馈；后台会尽可能将实际终态写回账本。业务结束后账本写入失败、进程中断或传输失败均不能证明没有副作用，原占用保留且不会自动重试。FAILED/COMPLETED 是处理链状态，均不表示数据库/平台操作已回滚或业务成功。

最终答案发送前复用原授权重放复核 scope、引用、人格、资料及代次，不重跑模型/工具，再通过原发送器和平台状态发送。配置、数据库、内存及外部平台不构成分布式原子事务；发送实际发生后的网络确认不确定仍需平台幂等设计。

离线验证复用事务、Core 命令和 FakeClient：新增五项关键集成流程及一项排队取消用例，覆盖完整原生命令到模型/工具/原权限/反馈、真实异步等待、原文空白、重建防重、递归、本人停止和原子空间管理。真实 MySQL 事务/唯一键重试、OneBot 发送状态及真实模型选命令/回执理解仍需实机验收。
