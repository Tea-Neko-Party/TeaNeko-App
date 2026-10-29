# Agent 实例、会话邮箱与轮次执行

本目录是 Agent 的平台无关业务底座，依赖本层、LLM、Core 及记忆模块的不可变引用数据。App、数据库和配置系统的连接放在 `adapter` 或 `_app_config`，不在组件中保存 Spring Bean、凭据、Repository 或平台客户端。

当前完成 S4 的实例、独立轮次和默认会话底座：`IAgentService.run` 按同会话顺序调用真实 LLM 管线，保留可信工具上下文及动态门控。邮箱具备 TTL、容量、epoch、完整成功轮次窗口和有界结果重放。本模块尚不自动接收平台消息、持久化历史或发送回复。旧 Agent 的平台入口仍待后续迁移，不能认为已由新门控统一接管。

S5A2b2 已将持久化记忆引用接入默认邮箱及 Spring 装配：队首固定引用后读取窗口，最终提交和缓存重放再次只读复核；显式同步入口协调同一 client/scope 下的全部 Agent 实例。

## 定义与实例拥有关系

`AgentDefinition` 保存稳定定义 ID、`LLMModelOptionsSnapshot` 和允许的 `AgentActivity` 集合。参数在构造时冻结，`toOptions()` 每次返回独立参数容器，嵌套数据继续只读；工具执行器不随模型选项保存。

```java
var definition = new AgentDefinition("assistant",
        new LLMModelOptionsSnapshot(null), Set.of(AgentActivity.values()));
var first = agentFactory.create(definition);
var second = agentFactory.create(definition);
```

同一份定义创建两个实例时，各自使用新 Core Entity 和生命周期。工厂只在 `IAgentRegistry` 登记实例拥有关系，不注册 scope、不绑定记忆空间、不读取模型或凭据，也不启动后台任务。

`AgentHandle.snapshot()` 只读，当前使用类型化 `AgentComponents` 保存定义与关闭状态；关闭控制保留在拥有者内部。`onClose` 返回可解除的监听，后续轮次可用它取消各自的执行控制。`close()` 幂等且只影响当前实例。`registry.remove(id)` 先移除拥有关系，再在注册表锁外关闭，回调可安全重入；关闭注册表会关闭其拥有的所有实例。直接关闭句柄不自动删除管理记录，完成生命周期后应由拥有者 remove。

## 身份与统一准入

`AgentScope` 保留 clientId 与已有 scopeId，不自行解析平台编号。`AgentActorContext` 固定 TeaNeko UUID、平台来源、消息/任务来源和目标。系统任务的 userId 必须为空，平台用户来源也为空，不能继承刚才聊天者的权限。数据由宿主适配器构造，不从模型参数读取。

`AgentAccessGate.evaluate` 对 SERVICE、COMMAND、AUTOMATIC_REPLY、COLLECT、SUMMARY、PROACTIVE、SCHEDULED、TOOL 使用同一开关边界：

1. 实例未关闭且仍由当前注册表拥有，定义允许该活动。
2. 交互命令来自用户；自主和定时活动来自系统任务。
3. scope 有真实 `agent` 配置登记且 enabled 为 true。
4. PROACTIVE 需要 ACTIVE 模式，普通 SCHEDULED 任务不等同主动聊天。
5. 本地总开关、运行设置、具体模型和凭据检查均通过。
6. 检查后复核实例及 scope；准备期间观察到关闭、注销或策略变化时拒绝旧结果。

未注册或禁用时不会读取模型、凭据或运行设置。所有结果含固定中文原因，允许时才有运行快照；结果只是当前检查，不是永久授权。后续入口须在开始、工具副作用、提交和出站前复核，且继续调用原命令/业务权限链。该门控不实现 @/引用/续聊触发判定、不提供分布式原子授权，也未替代 S4B/S5 的会话代次和引用 revision。

## `/cfg` 与本地设置

新 `AgentScopeConfigManager` 的名称为 `agent`，沿用原 `/cfg` 权限、注册和异步完成链。示例：

```text
/cfg reg agent
/cfg set agent enabled false
/cfg set agent enabled true
/cfg set agent mode PASSIVE_BASIC
/cfg set agent mode PASSIVE_CONTEXTUAL
/cfg set agent mode ACTIVE
/cfg unreg agent
```

登记默认启用且使用最低被动模式。缺失记录始终返回 unregistered，读取适配器不自动注册，也不回退到旧 `agent-personality` 默认值。允许字段为 enabled、mode、historyMaxMessages 和 inputMaxBytes；认证/连接字段、correctionKey 均不能从这里写入。模式只是门控数据，自动触发和主动任务尚未实现。

主文件 `config/agent/main-config.yml` 新增 `enabled: true` 作为新 Agent 总开关；交互次数、时限和可选 token 预算继续由 `tea-neko.llm.interaction` 提供。全局关闭后不继续读取模型或凭据。注册、注销和诊断管理入口不经过 Agent 执行门控，避免未开启时无法开通。

`FileAgentRuntimeConfig` 对主文件和模型文件使用 Core `requireCurrent`，最近一次已完成加载失败时不会沿用旧缓存作为有效配置。既有 `get` 保持最后有效值语义。修复后通过项目现有文件重载流程加载，再在下一次检查取得新快照。

## 就绪状态与后续接入

`IAgentReadiness.inspect` 依次检查本地开关/交互设置、模型路由/参数/能力和 Spring 凭据。只使用模型有效参数预检，不发起 HTTP；认证结果不会放进快照。状态区分 READY、LOCAL_DISABLED、LOCAL_CONFIG_UNAVAILABLE、MODEL_UNAVAILABLE 和 CREDENTIALS_UNAVAILABLE。日志仅记录固定中文阶段原因，不传递原始配置值和异常链。失败不缓存成永久全局关闭，配置源显式重载修正后可以重新检查；一个定义失败不影响其他有效定义。

Spring 组合根仅装配空注册表、工厂和检查端口，构造期间不访问配置/数据库，也不创建默认 Agent。配置和 scope 读取可能接触缓存或文件服务，调用方应在异步业务执行器中检查，不放在 Core `whileActive` 临界区。

## 默认会话入口与独立轮次

宿主将本次新增输入规范化为统一消息，并构造 `AgentRunRequest(requestId, actor, activity, messages)`，不重复传入默认邮箱已经持有的旧历史。构造时冻结消息、文本及透明状态；UUID、client/scope、来源和目标由宿主固定，模型参数不参与身份绑定。显式独立轮次没有邮箱，调用方自行提供完整输入。

```java
var request = new AgentRunRequest(UUID.randomUUID(), actor, AgentActivity.SERVICE, messages);
CompletionStage<AgentRunResult> completed = agentService.run(handle, request);
```

`run` 返回只读 Stage，包含会话排队、模型/工具实际执行和交付检查。取消返回视图不取消根执行。默认 Bean `agentService` 是 `AgentConversationService`；`agentTurnService` 只实现 `IAgentTurnService`，不会同时成为默认 Agent 服务候选。

排队期间不创建独立轮次，不预占模型或工具额度。独立请求出队后按实际 readiness 设置建立总预算，执行期限从冷轮次准备时刻计算，并扣除其内部准备耗时。派生步骤使用 `run(handle, request, parent)`，排队和执行全程继承父终止、预算及截止时间；宿主须提前声明 LLM 所需维度并保持父控制存活，等待所有派生步骤结束后再关闭。服务不为缺少维度的父控制补出新额度。

需要显式独立执行的服务组合使用 `IAgentTurnService.prepare(...)`。返回的 `AgentRunExecution` 已登记取消和结果观察，调用 `start()` 才读取运行配置并开始实际业务，重复 start 不重复调用。启动前取消或拥有者关闭同样完成 result，不发起模型/工具。其 result 只读，cancel 只影响本轮，运行回调不进入组件或 JSON。具体 `AgentService.run` 便捷方法仍是显式独立执行，不用于默认会话注入。

可执行的模型轮次活动为 SERVICE、COMMAND、AUTOMATIC_REPLY、SUMMARY、PROACTIVE、SCHEDULED。COLLECT 和 TOOL 是独立能力类别，用它们调用此入口返回 INVALID_INPUT，避免被动采集意外调用模型。真实入站采集、触发判定和任务服务后续通过各自端口接入。

服务只编译一次 `agent.admit → agent.interact → agent.complete` 管线。请求实体保存定义、实例 ID、可信身份、活动、请求、scope/runtime 快照、交互诊断和最终结果；执行控制、工具绑定、监听及服务依赖保留在轮次拥有者。三个步骤声明各自数据依赖与写入范围，组件不保存可执行容器。

准备、门控、工具选择和最终复核均在借用的业务执行器运行。模型完成线程只推进结果链，最终 scope/readiness 读取会切回业务执行域。端口方法须及时返回完成阶段，不在同步方法内等待同一交互或完整远端业务。默认装配借用 `virtualExecutor` / `taskExecutor`，不新建或关闭共享线程池，不在构造时读取配置或发起执行。

每轮把 readiness 的模型选项快照转为 `LLMOptionsPolicy.RESOLVED` Prompt；模型、参数、metadata、能力数据和交互设置固定在本轮，新轮次才使用新配置。动态检查可观察总开关/配置失效并阻止新动作，但不会用新 readiness 替换本轮参数。scope 配置读取值本身不提供 revision 或分布式原子授权；会话 epoch 由下述邮箱生命周期独立维护。

## 独立轮次的持久化记忆引用

显式创建 `AgentService` 时，九参数构造方法最后传入 `IAgentMemoryReferences`，启用持久化空间保护；原八参数构造方法保留显式无持久化记忆模式。两种构造都不读取数据库、登记空间或执行模型。带 conversation stamp 的请求必须同时提供邮箱固定的 memoryReference，否则在分配控制前返回 CONFIGURATION_ERROR。独立入口可以自行解析当前引用；显式提供的引用仅被验证，不会刷新为新空间。

实际启动并通过 Agent 门控、建立总预算与时限后，服务等待 `resolve(scope)`，在 `AgentComponents.MEMORY_REFERENCE` 和 `AgentRunResult.memoryReference()` 中固定 namespaceId、bindingRevision 与 generation。引用不写入 Prompt metadata，模型无法用参数覆盖它。每次模型准入、实际工具执行及最终交付前等待 `verify(expected)`，随后切回业务执行域再次检查动态门控。失效只停止本轮，不用新引用刷新旧轮次。

引用或 generation 变化返回 STALE_CONVERSATION，scope 停用返回 SKIPPED，存储/配置不可用返回 CONFIGURATION_ERROR；取消和超时保持对应状态。失败结果隐藏 answer 和可提交 transcript，保留已观察的模型、工具和 usage 诊断。模型校验等待计入模型阶段时限且不提前扣模型请求预算；工具校验等待计入工具时限，最终校验受整轮截止时间约束。迟到允许不会启动已停止的模型或工具，服务只关闭自身控制，不关闭借用的父控制及执行器。

默认适配器 `StoreAgentMemoryReferences` 在借用业务域复核真实 scope 开关，经绑定事务端口读取当前引用；仅在已启用 scope 显式解析且默认空间未登记时按需初始化，`verify` 始终只读。数据库失败仅公开固定中文原因，不返回原配置、正文或 Cause。自定义交互端口必须等待 `ILLMInteractionGuard.checkAsync`；只支持同步检查的端口面对异步守卫会明确失败。

未来事实/画像写入仍须在自己的数据库事务中验证 generation；动作前检查不能代替提交时仲裁，已经执行的外部副作用也不能回滚。

## 默认会话的引用协调

Spring 默认邮箱与独立轮次共用必需的 `IAgentMemoryReferences`。队首先异步检查实际 Agent 准入并解析引用，再在邮箱锁内协调引用和代次，之后才读取窗口并构造实际输入。调用者传入的 stamp 和 memoryReference 均被默认邮箱覆盖。冷轮次通过 `IAgentTurnService.prepareReferenced` 显式交接同一引用；不支持该能力的自定义端口明确拒绝，不能回退普通 prepare 绕过保护。

引用变化时，在同一次实体提交中固定新引用、推进 epoch、清空窗口及完成记录。旧等待/准备项失效，旧运行请求取消但保留运行槽至真实结束回执；触发惰性恢复的当前队首进入新代次。相同引用不清上下文；bindingRevision 回退或同空间 generation 回退被拒绝，同绑定版本却换目标属于不可用配置。切回旧 namespace 仍按新绑定版本换代，迟到旧通知不能恢复旧绑定。

可信管理服务在绑定事务成功后可调用 `IAgentConversationControl.synchronizeMemory(scope, parent)`，它读取当前权威引用并协调同一 scope 的所有实例，返回实际换代的邮箱数；不创建空邮箱。管理权限由调用方验证，返回值不表示旧外部副作用已经回滚。通知失败保留旧内存状态，下一队首仍会重新解析；它不会根据历史 BOUND 回执执行同步。没有登记聊天管理命令，也没有向 ConfigData 添加第二份权威 memoryNamespace 字段。

真实轮次结束后，邮箱在原轮次的剩余执行时限内复核原引用和当前准入，再仲裁 epoch 与窗口提交。只对引用和 stamp 一致的完整 SUCCESS 追加历史；拒绝仍保留交互和 usage。缓存 retry 或同 ID run 在交付前复核当前引用、准入及 epoch，不发起新模型或工具。重放观察使用当前排队等待上限，父取消和观察超时不停止原执行。队首解析计入排队时限，服务关闭会结束自己拥有的引用观察，不关闭借用父控制、执行器或存储。

旧构造方法仍允许可信宿主显式创建无持久化记忆邮箱，结果不具备此保护。当前仅提供 namespace 注册/绑定和引用协调，事实/画像读写、人格、持久化历史及自动摘要按后续阶段接入。最终已完成仲裁的结果不追溯撤回；未来平台出站仍必须复核结果携带的引用和代次。

## 会话顺序、等待与请求级取消

默认键为 Agent 实例 ID + 原始 client/scope，不含用户 UUID；同群不同用户进入一个邮箱，不同客户端、scope 或实例可以并行。入队顺序在轻量锁中确定，后续按该顺序获得运行槽，不合并不同消息。锁内只维护拥有关系和状态，不读取配置、不调用业务、不取消资源或完成用户 Future。

默认最多等待 5 条，不包含当前已占运行槽的请求；零表示忙碌时不排队。等待上限默认 30 秒，也覆盖已选中但仍在执行器等待启动的阶段。实际开始后关闭排队计时，改由轮次时限管理；出队会重新检查截止时间，迟到排队计时回调不能结束已运行的轮次。

```properties
tea-neko.agent.queue.max-waiting-requests=5
tea-neko.agent.queue.timeout-seconds=30
```

`SpringAgentQueueDefaults` 构造时不解析值，入队时在邮箱锁外读取快速内存配置。非法设置只返回本次 CONFIGURATION_ERROR，不影响应用启动，修复后下一次可恢复。新入队使用新容量/时限，已入队请求保留原等待截止时间。自定义策略端口不能阻塞 I/O。

队列满返回 BUSY，等待超时返回 QUEUE_TIMED_OUT；父执行到期继续返回 TIMED_OUT。在途相同 requestId、身份、活动和输入返回 DUPLICATE_REQUEST；同 ID 内容不同返回 REQUEST_CONFLICT。完成记录仍存在时，匹配的 run 复用已缓存结果并重新检查门控，不再次执行。需要观察原执行或安全重试时使用下述 retry；缓存之外的普通 run 仍是新请求提交。

宿主通过 `cancel(handle, scope, requestId, requesterUUID)` 请求取消本人发起的运行或排队项。UUID 来自真实宿主身份；系统任务没有可继承的用户 UUID，普通用户不能借共享群邮箱取消它。REQUESTED 仅表示已请求取消；运行项等待底层结束回执后释放运行槽，保留实际 usage/动作。scope 管理者批量停止的独立授权入口尚待后续命令层接入。

工具和同问题派生步骤必须显式传入原 control。框架用不可变执行链检查当前轮次后代，同会话递归调用返回 REENTRANT，避免工具等待自己占有的邮箱；共享预算的兄弟请求仍可排队，其他 scope/实例可独立执行。不使用 ThreadLocal，也不从模型参数推断链路或权限。

每个邮箱用工作计数推进后继，不阻塞等待阶段，也不因直接执行器下大量同步完成而递归增加调用栈。已经开始的外部动作仍须遵守控制协议；邮箱串行管理受控轮次，不强制中断不响应取消的同步业务。

## 会话保留与代次失效

默认最多保留 1000 个会话，包含执行、准备和等待中的邮箱。只有没有任何在途请求的会话可以过期或被容量淘汰；空闲 TTL 从最后一次完成或接收请求的较晚时刻计算，默认 30 分钟。容量不足时按最近最少活动顺序淘汰空闲项；全部活跃时，新会话返回 BUSY，已有会话仍可按自己的排队策略接收请求。缩小配置不强制淘汰活跃项。

```properties
tea-neko.agent.conversation.max-conversations=1000
tea-neko.agent.conversation.idle-ttl-seconds=1800
```

`SpringAgentConversationDefaults` 与排队策略分开，入队时读取并校验。非法配置只使本次入队 CONFIGURATION_ERROR；修复后恢复。新入队及 `purgeIdle()` 使用最新设置，`snapshot()` / `invalidate()` 使用该邮箱最近入队时的保留策略，不为检查额外读取配置。时钟回拨不会使活动时刻倒退。

TTL 是惰性有效期：入队扫描过期空闲项，读取某个快照或失效该会话时检查其过期时间，也可由宿主显式调用 `IAgentConversationControl.purgeIdle()` 清理并收缩容量。没有后台定时清理任务；无人访问时，已逻辑过期的数据可以暂留到下次操作，内存项仍受容量限制。观察快照不续期。实例关闭立即回收自己的空闲会话，服务关闭解除缓存监听；两者均不关闭共享执行器。

Conversation 使用 Core `Entity`，由 `AgentConversationState`、`AgentConversationWindow` 和 `AgentRequestJournal` 三个组件分别保存生命周期、完整成功轮次和独立完成记录。`snapshot(handle, scope)` 返回带 revision 的只读实体快照，执行句柄、排队容器、控制和监听始终留在邮箱拥有者内部。

受信任的宿主可用 `invalidate(handle, scope)` 推进目标会话 epoch。不存在、已关闭或过期时返回空，不创建邮箱。有效会话在同一次实体提交中换代、清空窗口与完成记录，并拒绝旧等待项，锁外请求取消旧运行；旧运行槽保留到结束回执，避免新旧轮次同时占槽。新请求使用新代次排队。旧轮的迟到成功返回 STALE_CONVERSATION，只保留交互诊断和 usage，`answer()` / `committableTranscript()` 为空。已在失效前完成仲裁的结果保持原结果，不能追溯撤回；结果的 conversation 保留原 stamp，出站方仍须与当前代次比较。

自定义轮次端口在取消或启动抛错后仍须完成 `result()`；仅抛出异常不会释放已经发布的运行槽。真实轮次端口通过执行控制完成取消与时限结果，邮箱等待该结束信号后再推进后继。

默认邮箱覆盖输入中的代次提示，在冷轮次请求与 `AgentComponents.CONVERSATION` 中固定真实绑定。该数据通过可信工具上下文读取，不放入 Prompt metadata。管理端口不校验聊天角色，也不会创建 namespace 或切换人格；后续管理服务须先完成原有权限和持久化引用事务，再调用失效入口。显式独立轮次绕过邮箱生命周期，不能凭一个 stamp 获得当前会话授权。取消不能回滚已经开始的外部动作；后续持久化写入和平台出站仍须校验对应 generation/epoch。

## 完整轮次窗口与输入限制

队首开始准备时读取最新窗口和 `IAgentConversationPolicy` 快照；后继请求能看到前一成功轮次。输入中的 user 名称绑定为可信 UUID 派生的稳定标识，窗口另外保留真实 actor 和 requestId，模型不能借名称改变操作者。消息角色、图片引用和 providerMetadata 使用统一协议冻结与回放。

```properties
tea-neko.agent.conversation.max-history-messages=40
tea-neko.agent.conversation.max-input-bytes=262144
tea-neko.agent.conversation.max-completed-requests=32
tea-neko.agent.conversation.max-replay-bytes=1048576
```

scope 的 historyMaxMessages / inputMaxBytes 默认 -1，继承本地设置；前者允许 0 关闭窗口，后者必须为正数。示例为 `/cfg set agent historyMaxMessages 40`、`/cfg set agent inputMaxBytes 262144`。本地值先校验，非法配置仅返回本次 CONFIGURATION_ERROR，修正后可恢复；配置在下一队首生效，同轮保持快照。

输入限制按规范化协议 JSON 的 UTF-8 字节计量，包含图片引用、工具参数和透明状态；不是 token 数或供应商最终 HTTP 大小。按完整旧 turn 淘汰以同时满足消息数和输入预算，新输入自身仍超限则返回 INPUT_TOO_LARGE，不调用模型。只有最终 SUCCESS、原输入前缀不变、工具协议闭合且最终候选匹配的结果可提交；只保存本轮增量，拒绝新增的伪造 system/user 响应。失败、取消和未闭合工具链只进入独立诊断记录。

单个完整成功轮次超过窗口限制时仍交付成功，但清空整个近期窗口并设置 lastTurnDiscarded、累计 discardedTurns，避免略过中间轮次后沿用旧上下文。不会拆开工具往返，也不会把这种丢弃当作自动摘要；摘要属于 S5。

## 请求观察与结果重放

`agentService.retry(handle, originalRequest)` 只观察原在途请求或复用完成结果，不创建轮次、占用队列或重复模型/工具调用。requestId、可信 actor、活动和原始输入指纹必须一致；不一致返回 REQUEST_CONFLICT。结果保留时返回原对象，重新检查当前 scope/readiness 及检查前后的 epoch；拒绝时保留诊断但隐藏 answer。

未知 ID、TTL 到期、换代、记录被淘汰或正文预算不足返回 REPLAY_UNAVAILABLE，不回退成执行新请求。重试观察期间发生换代返回 STALE_CONVERSATION。带 parent 的重试只继承观察期限和取消，不扣模型额度、不关闭父控制，也不停止原请求；原请求后代观察自己会返回 REENTRANT。

每会话默认保留 32 条完成状态，优先保留最近结果正文，约 1 MiB 的保守序列化预算包含协议透明状态；预算为 0 时仅保留完成状态及身份。该数值不等于 JVM 堆占用，记录数与会话容量也共同约束缓存。正文和状态都被淘汰后，retry 仍拒绝执行；普通 run 需要宿主区分重新提问与重试。进程重启不恢复这些记录，持久化工具及平台发送幂等在 S5/S6/S9 分别落实。

## 工具与动态门控

`IAgentToolSource` 为本轮显式选择 `LLMToolBinding`，默认装配返回空列表，禁止回退到全局全部工具。没有 TOOL 活动能力的定义连工具源都不会读取。业务能力装配可以提供自己的工具源 Bean；记忆、命令和任务工具将在对应阶段接入。

选入绑定通过 `guarded` 保留冻结定义和原执行器，在 schema 校验后、实际执行器之前复核源活动及 TOOL 门控。上下文的 requestId 和组件来自宿主轮次；工具只能从可信绑定取得发起者与固定目标。该检查不授予命令或数据库权限，具体执行器仍须走原业务权限链。

每次模型准入前也有显式 `ILLMInteractionGuard`，因此工具等待期间注销后，下一次模型请求同样不能发出。守卫在生命周期锁外执行且包含在阶段时限内。宿主拒绝使用 `LLMOperationRejectedException` 停止余下交互，不当作普通工具错误恢复；同步、异步及完成包装中的拒绝语义一致。普通工具业务失败继续保留真实 FAILED 状态，并可回填后取得最终模型结果。

## 结果、取消与后续边界

实例关闭或注册表 remove 会取消该实例运行和排队项；邮箱服务关闭取消自己拥有的请求并拒绝新请求，不关闭实例、独立轮次服务、宿主控制或共享执行器。Spring 单独管理独立轮次服务生命周期。请求完成解除监听和计时，关闭一个实例不影响其他实例。

`AgentRunResult` 区分成功、跳过、配置不可用、取消、超时、预算不足、空/截断/拒绝/非法响应及失败。拒绝时仍可保留 `interaction` 诊断和已观察用量/动作；不推断尚未完成动作是否已产生外部副作用，也不声称回滚。总 `budgetUsage` 是交付时的宿主共享读数，具体轮次的模型/工具记录使用 interaction 的局部数据，不能跨轮重复汇总父总额。

只有最终 SUCCESS 提供 `answer()` 和 `committableTranscript()`。scope 在交付前失效时，即使已有成功模型候选，也只保留在诊断中。取消会等待控制终止通知及诊断观察器登记，不等待不响应取消的远端异步结果；迟到结果不能提交组件或改写已交付结果。

`answer()` 当前仍是统一模型候选；受控决策、结构化发送计划及实际平台出站尚未接入，不能将内部结构化 JSON 直接作为平台回复。S4 已提供完整窗口、输入限制与内存请求重放；S5 已实现 [namespace 注册与绑定事务基础](../memory/namespace/README.md)、独立轮次及默认会话的引用保护、[事实/偏好事务存储和显式旧资料导入](../memory/fact/README.md)，以及 [独立 Recall 的筛选和证据预算](../memory/recall/README.md)。事实尚未接入模型工具或 Prompt；画像、持久化历史、自动摘要与人格仍待后续实现。内存 epoch 不代表分布式原子授权，也不代表持久化副作用幂等。
