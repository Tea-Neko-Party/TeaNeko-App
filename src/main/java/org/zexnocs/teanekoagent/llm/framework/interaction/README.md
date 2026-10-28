# 有限模型与工具交互

`LLMInteractionExecution` 统一单个交互中的模型请求、顺序工具批次、局部与宿主共享预算及阶段时限。它可脱离 Spring、Agent、数据库和平台使用，不读取人格、记忆或凭据，也不往业务历史中提交消息。

`ILLMInteractionService.interact` 在阶段执行器之上完成自动往返：冻结初始输入，验证完整工具历史，执行模型、顺序工具及下一模型，返回 `LLMInteractionResult`。显式阶段 API 仍可供其他编排复用，自动服务负责模型响应与工具 ID 的关联，不复制预算或工具注册表。

## 限额与配置

`LLMCallLimits` 默认是三次模型请求、两批工具、六次工具执行。模型次数必须为正，工具限额允许零以禁用工具；工具批次数会收束到 `maxModelCalls - 1`。`LLMInteractionSettings` 默认模型阶段六十秒、单工具十五秒、整轮一百二十秒，均可由调用方显式替换；没有隐含重试。

```java
var settings = LLMInteractionSettings.defaults();
// 独立请求才创建总预算；同请求的前置步骤或协作应继续复用同一父控制。
var clock = Clock.systemUTC();
var parent = ExecutionControl.withDeadline(settings.limits().newBudget(),
        clock.instant().plus(settings.turnTimeout()), clock, taskExecutor);
var trusted = new ToolInvocationContext(requestId, trustedBindings, parent);
CompletionStage<LLMInteractionResult> completed = interactionService
        .interact(prompt, selectedTools, trusted, settings)
        .whenComplete((result, failure) -> parent.close());
```

Spring 装配复用 Core 的 `virtualExecutor` 与 `taskExecutor`。不传显式 settings 时，`SpringLLMInteractionDefaults` 在调用时读取下列本地配置；构造 Bean 不解析字段。格式错误或非正时限返回 `CONFIGURATION_ERROR`，不回显原始配置值，也不阻断 App 启动。修复配置后下一次调用重新读取；显式 settings 完全绕过默认值读取。

```yaml
tea-neko:
  llm:
    interaction:
      max-model-calls: 3
      max-tool-rounds: 2
      max-tool-calls: 6
      # 可选；不填不设局部 token 上限，0 表示不允许模型请求。
      # max-total-tokens: 12000
      model-timeout-seconds: 60
      tool-timeout-seconds: 15
      turn-timeout-seconds: 120
```

次数和时限不进入 Prompt metadata。每轮交互设置在开始时固定，不随热更新改变。输出 token 上限仍取模型 options，并在可选总预算不足时进一步收紧。

生产装配应借用项目已有的异步业务执行器和 ScheduledExecutorService，本模块不创建线程池。业务执行器不得在提交线程中内联运行可能阻塞的操作。父预算必须预先声明 `MODEL_REQUESTS`、`TOOL_ROUNDS`、`TOOL_EXECUTIONS`；缺失维度明确失败。独立请求通过 `limits.newBudget()` 声明总量，同请求的新交互只能继承父预算。每个交互另外维护局部限额，不能凭更高的局部配置扩张宿主额度。

## 可选总 token 预算

`new LLMCallLimits(3, 2, 6, 12000L)` 增加本轮 token 上限，三参数构造及默认设置保持未配置。`maxTotalTokens=null` 表示不设局部上限；如果宿主已经声明 `TOTAL_TOKENS`，本轮仍继承父限制。零是零额度，负数非法。局部开启预算要求宿主也显式声明该维度，缺失时返回 `INVALID_INPUT`，不会自动创建新的父总额。

独立请求应使用实际选定 settings 的 `limits.newBudget()` 建立宿主预算；从本地默认设置启动的宿主先调用 `ILLMInteractionDefaults.resolve()`，再将同一 settings 显式传给交互服务。同一请求的协作或派生步骤必须复用该父控制，不能各自调用 newBudget。次数与 token 限额可以分别收紧，整次请求的截止时间仍由宿主建立。

启用后，在模型阶段的异步工作中调用 `ILLMModelService.resolveOptions` 取得真实合并参数；此预检不发起模型请求。必须有正数 maxTokens，缺失时返回 `CONFIGURATION_ERROR`。`ILLMTokenEstimator` 在执行控制临界区外估算固定消息、工具定义和透明状态；默认 `JsonLLMTokenEstimator` 按 UTF-8 JSON 长度加固定开销计数，流式统计长度，不保存额外正文副本或执行工具。可替换为具体模型的本地 tokenizer，不得偷偷发起额外供应商计数请求。

预留总量为输入估算加输出上限。输出上限取模型配置与两级剩余额度减输入估算的较小值，至少留一个输出 token；不够时在调用模型端口前拒绝，不扣模型次数。Core 原子预留同时约束并行交互的在途请求；模型次数最终未取得时退还尚未使用的预留。

成功响应有合法 usage 时按 `max(totalTokens, promptTokens + completionTokens)` 结算，退还未用预留或补记超出估算的量，不重复加算 reasoningTokens。原始字段仍保留在响应。已发起的失败、取消、超时或缺失/非法 usage 保守保留本次全部预留，并在 `ModelAttempt.tokenUsage` 中标为 `ESTIMATED`；算法标识和预留量可观察。未启用预算且缺失报告时 tokenUsage 为空，表示未知，不表示零消耗。迟到响应不能再次结算或退还已经交付的估算。

实际结算若使已结算消耗加其他在途预留超过总额，保留超额事实并封锁该预算维度；其他请求后续退款也不会解除本次父预算的封锁。结果为 `BUDGET_EXCEEDED`，保留该次模型响应与 usage，停止新请求及工具执行。`rejectedBudget` 明确指出被拒维度；每个工具副作用开始前也复查 token 额度。已有副作用不回滚，在途请求也可能已有远端消耗。

此硬限制约束的是本地准入和保守记账。默认估算器不是供应商 tokenizer，图片计费及隐藏开销无法由 JSON 长度精确保证；供应商报告、未知 usage、已发生的远端请求不能伪装成精确费用硬上限。估算偏大可能提前拒绝，估算偏小时只能在收到报告后记入超额并阻止后续动作。

## 模型与工具的完成契约

`callModel(prompt)` 在阶段准备工作实际开始后、调用模型服务前预占一次请求。取消或超时的排队工作不会发出请求、不会扣模型额度；已经进入模型端口的失败尝试不退还额度，后续显式尝试继续计数。计数代表服务尝试，不声称本地配置失败已经产生远端 HTTP 或计费；远端 usage 仍由模型结果提供。

本次 Prompt 的工具定义始终被选定工具集替换。局部或共享模型额度只剩末次时，强制 `tools=[]`、`toolChoice=none`；工具批次或执行额度耗尽时同样关闭工具。`maxModelCalls=1` 从第一次就无工具，不会偷偷追加最终请求。

模型阶段结束且开放了工具后，`executeTools(calls)` 接受至多一个批次。先校验整批格式、ID 去重和已知额度，再按原顺序逐个执行、等待真实业务阶段。显然超过额度或含重复 ID 的批次不会先执行前几项副作用。其他交互并发消耗父额度时，仍可能在本批中途额度不足，此时异常结束并保留真实已执行动作，不能声称回滚。

工具仍经过真实 `LLMToolSet`：未选工具不执行，参数按固定 schema 检查，可信绑定不从模型参数取得。单个工具阶段超时可回填固定 `TIMED_OUT` 错误后继续下一项；整轮取消、关闭、到期或共享预算耗尽立即停止余项。结果保留调用 ID 与截断标志，列表不可变。

执行器验证工具批次及调用时序，自动服务进一步验证本次模型响应、历史回填的 ID/名称/顺序和全链重复 ID。当前只接受一个 index=0 候选，避免从多个候选隐式选择一条产生副作用；无候选是空输出，缺失消息或非法结束原因是非法响应。截断、拒绝或失败响应携带的工具不会执行。末次仍请求工具时不增加模型尝试，不执行额外工具。

工具调用优先于同一条消息中的提前答案，带工具的消息不会作为最终答案候选。下一轮保留 assistant 正文、工具列表与透明 provider metadata，工具结果按原顺序回填。跨请求副作用去重仍需后续业务记录服务，不能用内存集合代替持久化幂等。

## 完整结果与历史提交

`LLMInteractionResult` 包含明确 `status`、诊断 `transcript`、可选最终候选、逐次模型 `attempts`、已观察到完成的 `tools`、局部 `budgetUsage` 及可选的 `rejectedBudget`。`modelAttempts()` 读取实际预占次数；已进入模型端口的失败也有一条无响应尝试记录，token 超额时仍保留已收到的响应。供应商未提供 usage 时保持缺失，不能将其统计为零 token；估算另列于 ModelAttempt.tokenUsage，字段精度仍取决于供应商。

状态区分成功、空输出、截断、拒绝、预算不足、取消、超时、非法输入、非法响应、配置错误和执行失败。单工具错误或阶段超时可以回填并继续取得最终答案，工具的真实状态和截断标志始终保留；整轮取消、到期或预算不足停止后续步骤，不自动重试。

失败 transcript 可能包含尚未回填完整的工具请求，仅供诊断。只有 `committableTranscript()` 非空时才是可提交的正常历史；服务不自动写业务历史。批次中途失败仍保留此前已观察完成的工具记录；未返回结果的工具可能已产生外部副作用，不能据此推断未执行或已回滚。

## 生命周期与扩展边界

整轮与每个阶段都使用独立子控制，继承父时钟和预算且不晚于父截止时间。阶段时限覆盖准备、排队、等待实际完成；模型通过 S2C `ExecutionPolicy` 传到真实 HTTP，工具通过 `context.withControl(stage)` 响应终止。阶段结果到达调用者前关闭自己的计时与父监听。

宿主若在同一请求中组合多个交互，还须为父控制设置该请求的总截止时间；每个交互的局部一百二十秒不会替代跨交互的父截止时间。前述独立单交互示例由本实例的整轮计时提供上界，后续 Agent 管线负责建立请求总截止时间。

同一交互只允许一个在途模型或工具操作，重叠调用明确失败。`cancel()` 取消当前交互及后代，`close()` 幂等关闭拥有的控制；均不取消父请求、不关闭借用执行器。导出的阶段为只读视图，取消视图不会取消根执行，主动停止须调用控制入口。

同步兼容工具如果忽略终止并阻塞线程，本层只能结束等待和阻止后续动作，不能强制回滚或安全杀死任意同步代码。资源工具应使用异步端口并在终止时释放订阅/连接。

配置、工具定义和执行器引用在交互内固定。消息、文本、工具调用、选项 metadata、供应商状态、logprobs 与返回内容均作必要深层快照；已不可变的图像可复用。嵌套状态只接受最多 64 层的 JSON 值，保留 null 和列表顺序，数组转不可变列表，拒绝循环、非有限数及任意运行对象。调用方须避免复制期间并发修改原数据，复制完成后修改原 DTO 不影响本轮。

自动服务返回前关闭本轮拥有的阶段和整轮控制，取消由传入宿主控制传播；不关闭父控制或借用线程池。显式阶段 API 的使用方仍须自行关闭 `LLMInteractionExecution`。

离线测试包括 `LLMInteractionExecutionTest`、`LLMInteractionServiceTest`、`LLMInteractionDefaultsTest`、`LLMInteractionMapperTest`、`LLMTokenBudgetTest` 和 `LLMTokenEstimatorTest`，使用真实 Core 子控制、真实工具绑定、手动时钟与受控模型，覆盖循环、次数/token 准入、结算、透明状态回放、快照、终止及资源归属；没有真实模型、平台或应用启动依赖。
