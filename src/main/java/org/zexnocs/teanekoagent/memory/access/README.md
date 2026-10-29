# 可信记忆访问边界

`MemoryAccessService` 组合已有引用、事实追加、Recall 和画像端口，供可信宿主、`AgentMemoryToolSource` 及 `AgentMemoryPromptSource` 调用。它没有数据库实现、缓存、模型调用、线程池或管理操作；Spring 构造没有 I/O。实际工具和自动 Prompt 证据注入均已接入默认 Agent 装配。

## 固定身份与明确新增

`MemoryAccessContext` 保存原请求 ID、真实 `AgentActorContext` 和完整 `MemoryNamespaceReference`。工具适配器使用 `MemoryToolContexts.from(toolContext)` 从宿主组件取得它，同时检查请求/actor/固定引用的交接一致性。缺少引用时失败，不临时解析另一个空间。模型业务参数不得包含或反序列化这些上下文、授权对象、操作者、namespace 或 operationId。

宿主从当前发起者的**单条原始消息**调用 `MemoryAddIntent.fromUserText(context, originalUserText)`，当前保守格式为 `记住：正文`、`记住:正文`、`请记住：正文` 或 `请记住:正文`，允许首尾空白。正文保留原文，长度 1–4096 个 Java 字符。普通聊天、否定句、外层引用和其他格式不会自动获得新增授权；含糊或未支持的表述由后续入口澄清，不由模型填写“已同意”布尔值。引用/转发的文字不能冒充原始消息，不能扫描历史或整个 Prompt 来寻找前缀。

意图固定完整原上下文和正文，不能在换空间、换请求、换来源后复用，模型不能把正文改写成另一条事实。平台消息的验真由可信入口承担；私有构造、类型标记和脱敏序列化用于防误传，不代替平台身份验证。当前没有通用自然语言授权分类器或自动个人事实提取。

```java
var context = MemoryToolContexts.from(toolContext);
var intent = MemoryAddIntent.fromUserText(context, originalUserText).orElseThrow();
var value = new MemoryFactValue(MemoryFactValue.Kind.PREFERENCE, intent.text(), List.of(), 1,
        MemoryFactValue.Importance.NORMAL, MemoryEventTime.unknown(), null);
var committed = memoryAccessService.append(context, originalActionId, intent, value, toolContext.control());
```

示例的 `originalUserText` 必须来自该 actor.sourceId 的当前原始用户消息；`originalActionId` 来自宿主已接受的原工具调用标识，不能是工具 JSON 中的字段。正文之外的类型、时间等仍经过现有领域校验，不从原文无条件推断精确事件时间。实际工具在模型往返前固定意图，执行时沿用原调用 ID，不能拿回填后的工具文字重新识别意图。

实际 Agent 调用通过可选 `AgentUserText` 传入独立原文凭据：

```java
var request = new AgentRunRequest(requestId, actor, AgentActivity.SERVICE, messages,
        null, null, new AgentUserText(actor.sourceId(), originalUserText));
var result = agentService.run(handle, request);
```

宿主必须只提供当前真实消息的未改写正文，不包含引用或转发原文。四/五/六参数旧构造继续可用，缺失凭据时即使 messages 中含“记住：”也不授权新增。系统任务或来源消息 ID 错位不能携带该凭据。凭据不放入 Prompt metadata、模型参数或正常会话窗口；邮箱装配和引用交接保留它，原始文本的完整指纹参与 run/retry 冲突判断。默认邮箱仍覆盖外部 namespace/会话提示，独立入口在引用解析后才向工具源交付固定引用。

普通新增始终使用真实发起者的 TeaNeko UUID 作为主体，保存原 actor/request/action 来源，没有其他主体参数。相同 requestId+actionId 派生稳定 operationId；重试保留原上下文、动作与全部内容。存储继续负责完全重复合并和参数冲突，成功仅在原事务提交后完成。`MemoryAddResult` 只返回状态和记录 ID，避免重复旧导入记录时返回其原始审计 JSON。

明确新增不授予修改、删除、锁定、导入、导出、克隆、clear 或替他人写入的权限。未来 DEBUG 管理使用独立真实权限策略和操作，不复用本入口来绕过边界。

## 独立开关与执行域

已有 `/cfg` 的 `agent` 登记增加三项独立布尔配置：

| 字段 | 已登记记录的默认值 | 当前行为 |
| --- | --- | --- |
| memoryReadEnabled | true | 控制事实查询、画像读取，以及后续共用此边界的工具/注入。 |
| memoryAddEnabled | true | 控制用户明确请求新增；关闭读取不自动关闭新增。 |
| memoryLearningEnabled | false | 独立策略数据，当前没有自动学习执行器；开启也不能绕过明确新增意图，不控制人格补正。 |

例如 `/cfg set agent memoryReadEnabled false`。原 `/cfg` 的权限链继续执行；没有增加第二份权威 namespace 配置。缺失登记或 enabled=false 时所有普通记忆访问关闭。旧 JSON 缺失新字段沿用上述默认值，实际登记仍是前提；非法模式/配置失败明确报告配置不可用，不按宽松策略继续。

每次调用先在借用业务域检查停止及当前开关，等待原引用只读验证，回到业务域再次检查后才启动业务。完成后再检查开关、引用及停止，随后交付原不可变结果；来源引用、画像主体或显式预算错位整体失败。保留 Recall/画像的 SCAN/RESULTS/CHARACTERS 和原统计，不把部分空结果当成确定不存在。共享空间查询仍可跨主体和来源，读取其他主体不授予写入其画像的权限。`verifyRead` 只复核当前读开关、原完整引用和停止，不重新检索，供资料的实际使用与授权重放调用。

数据库完成线程仅调度后续业务，不在邮箱锁或控制锁内读取配置/执行事务。服务借用阶段 `ExecutionControl` 和 Core 执行器，不关闭它们。取消返回的观察 Future 不撤销原事务；阶段控制取消、超时或关闭会阻止后继动作和迟到交付。来源及调度故障都能完成阶段，不泄漏原文、异常链或 suppressed；缺失/旧 generation、引用变化、配置错误、开关拒绝、停止和超时保留各自固定类别。

## 提交与切换的实际边界

引用校验是动作前/交付前的观察，不构成绑定、开关与事实提交之间的跨资源原子事务。检查后已经开始的追加仍可能完成原空间的写入；它不会改投新空间，clear 则由原事实事务中的 namespace 写锁和 generation 仲裁拒绝旧代次。所有未来修改/清理继续遵守同一锁协议。

取消、超时或交付前引用变化后的失败不证明写入回滚；已开始的真实副作用仍以原持久化回执为准。相同原动作重放继续经过当前开关、引用及 generation 校验，不能借历史成功恢复旧代次。交付之后发生的切换无法追溯撤回结果，未来 Prompt 使用和平台出站仍应复核其携带的原引用与会话代次。

本批离线测试覆盖身份/原文不可替换、独立开关、异步等待/调度拒绝、迟到结果、真实事实事务的提交与回滚协议、同动作冲突/重复、二十个并发重放、clear 前后竞争及空间切换。MySQL/JPA 实际事务代理、行锁/隔离级别、排序规则和部署联调仍需实机完成；人格、持久化历史、客户端补取与自动摘要保持独立。

## 已接入的模型工具

默认完整 Spring 装配通过 `MemoryToolConfig` 提供一个 `IAgentToolSource`。自定义同类策略可替换它；若需同时使用其他工具，应显式组合选定绑定，同名工具仍由原工具集拒绝，不回退全局目录。没有 TOOL 活动能力、没有已解析持久化引用或 scope 停用时不选择记忆工具。

| 工具 | 模型业务参数 | 行为 |
| --- | --- | --- |
| memory_query | 可选 subjectId、keywords、tags、kinds、start/end、includeEstimated，以及 limit/maxCharacters/maxScanned。 | 当前共享空间的确定性检索；省略主体不强制限定当前用户，时间使用带时区的 ISO-8601。 |
| memory_profile | 可选 subjectId 和三项读取预算。 | 默认当前真实发起者 UUID，也可明确查询同空间其他 UUID；系统任务必须显式提供主体。 |
| memory_add | 必填 kind，仅 FACT 或 PREFERENCE。 | 仅本轮原文凭据识别出明确新增且新增开关开启时可见；正文来自已固定意图，真实发起者是唯一主体。 |

工具 schema 禁止额外字段，不接受 actor、namespace、授权、正文、subjectId 写入目标或 actionId。`IContextualToolExecutor.call` 已提供真实 `ILLMToolCall`，新增直接使用其原 ID；不扩展 LLM 上下文、不生成随机替代 ID。超出既有 actionId 的 128 字符上限时明确 INVALID_CALL，不截断导致动作碰撞。模型无法把工具结果、旧历史或新生成的“记住”文字变成新增凭据。

新增仅保留用户明确原文，可选择事实/偏好；标签为空、重要度 NORMAL、来源置信度 1，事件时间保留 UNKNOWN，不擅自生成精确事件或关系。调用原事实事务，STORED/DUPLICATE 才返回成功业务信封，参数冲突为 OPERATION_CONFLICT；成功结果只含状态和记录 ID。取消或超时后已经开始的事务仍按上一节核对，不假装回滚。

查询默认条数沿用本地 tool-limit，默认八条，显式提高仍受 hard-limit。证据数组额外收紧到通用 8000 字符工具结果上限减去 1024 字符信封预留，即最多 6976 字符；扫描和字符也不能扩大本地策略。输出只序列化一次有序证据，保留主体、来源、事件/记录时间估算、namespace/generation、画像规则版本、检查/命中数量、覆盖限制和 definitivelyEmpty，不输出管理审计 JSON。

工具信封使用完整紧凑 JSON，`ok=true` 时提供 data，业务拒绝或不可用时为 `ok=false` 和固定 error.code。通用工具调用完成不等于记忆业务成功，应读取业务信封。实际序列化仍核对整个信封长度，坏来源超限时明确 RESULT_TOO_LARGE，不截碎 JSON 或返回假空记忆。读/写开关除了控制可见列表，也由 `MemoryAccessService` 在每次实际执行和交付前复核。

离线组合已覆盖实际 Agent 邮箱 → LLM 工具往返 → 记忆事务 → 查询/画像结果回填、原动作 ID 和来源保持、旧历史不能授权下一轮、同请求变更授权原文拒绝重放、模型调用期间关闭开关及工具提交后 clear 的失效结果。平台入口的原始消息提取、人格、持久化历史与自动摘要留在对应后续批次。

## 默认自动记忆资料

`MemoryPromptConfig` 在没有自定义 `IAgentPromptSource` 时提供实际源。默认优先本人画像，再以剩余总预算检索当前短原文字面匹配的其他主体事实；系统任务不冒充用户。所有读取使用 INJECTION、原固定空间及本边界，保留来源/主语、时间依据、证据版本与部分覆盖，旧导入审计 JSON 不进入资料。

结果作为低信任 USER 数据片段进入实际 Prompt，完整协议输入预算可整份省略查询结果，正常历史使用验证后的投影而不沉积资料。模型、工具及最终交付前调用 `verifyRead`；重放另用独立观察控制复核读开关、引用和证据到期，不重新读取或追加。查询规则、预算和重放状态见 [Agent 说明](../../agent/README.md#本轮临时-prompt-资料)。
