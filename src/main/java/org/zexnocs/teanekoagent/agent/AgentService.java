package org.zexnocs.teanekoagent.agent;

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.agent.access.AgentAccessDecision;
import org.zexnocs.teanekoagent.agent.access.AgentAccessGate;
import org.zexnocs.teanekoagent.agent.history.IAgentHistoryRecorder;
import org.zexnocs.teanekoagent.agent.memory.IAgentMemoryReferences;
import org.zexnocs.teanekoagent.agent.monitor.AgentTurnReport;
import org.zexnocs.teanekoagent.agent.personality.IAgentPersonalitySource;
import org.zexnocs.teanekoagent.agent.prompt.IAgentPromptSource;
import org.zexnocs.teanekoagent.agent.summary.IAgentConversationSummaries;
import org.zexnocs.teanekoagent.llm.framework.interaction.ILLMInteractionService;
import org.zexnocs.teanekoagent.llm.framework.interaction.LLMInteractionResult;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.actuator.execution.ExecutionStoppedException;
import org.zexnocs.teanekocore.framework.composition.EntityId;
import org.zexnocs.teanekocore.framework.composition.SystemPipeline;
import org.zexnocs.teanekocore.logger.ILogger;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 独立轮次的异步入口及资源拥有者，复用预编译管线与既有 LLM 交互服务。
 * 同实例可执行不同轮次；默认会话服务在本端口外组合队列与窗口，本类只拥有独立轮次。
 * <br>2026/11/02：支持启动前安装插件管线，运行轮次仍复用原拥有和关闭路径。
 * <br>2026/11/02：为未知失败增加请求关联、模型尝试次数及脱敏异常结构。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.0.0
 * @version 4.6.0-l
 */
public final class AgentService implements IAgentTurnService, AutoCloseable {
    private volatile Consumer<AgentTurnReport> observer = report -> { };

    /**
     * 安装只消费脱敏报告的宿主观察器，观察故障不改变业务结果。
     * @param observer 报告消费者，不接收运行句柄或原文
     * @return 当前服务，供装配组合
     */
    public AgentService observe(Consumer<AgentTurnReport> observer) { this.observer = Objects.requireNonNull(observer); return this; }
    private final AgentAccessGate gate;
    private final Executor executor;
    private final ScheduledExecutorService scheduler;
    private final Clock clock;
    private final ILogger logger;
    private final @Nullable IAgentMemoryReferences references;
    private final IAgentPromptSource prompts;
    private final @Nullable IAgentPersonalitySource personalities;
    private final @Nullable IAgentConversationSummaries summaries;
    private final ConcurrentMap<EntityId, AgentTurn> turns = new ConcurrentHashMap<>();
    private final Object lifecycleLock = new Object();
    // 2026/11/02：仅在首次请求之前允许宿主完成插件装配。
    private SystemPipeline pipeline;
    private final AgentTurnPipeline executionPipeline;
    private boolean started;

    /**
     * 在服务接受请求前安装插件，运行期间禁止替换步骤。
     * @param plugins 能力组合器
     * @throws IllegalStateException 当服务已经开始接收请求或关闭时
     */
    public void installPlugins(org.zexnocs.teanekoagent.plugin.AgentPluginRuntime plugins) {
        synchronized (lifecycleLock) {
            if (started || closed) { throw new IllegalStateException("Agent 服务运行后不可替换插件管线。"); }
            executionPipeline.installPlugins(plugins); pipeline = executionPipeline.pipeline;
        }
    }
    private boolean closed;

    /**
     * 装配显式无持久化记忆的独立轮次，不读取配置、创建实例或发起业务。
     * @param gate 全活动准入门控
     * @param interactions 完整 LLM 交互端口
     * @param tools 显式本轮工具选择端口
     * @param mapper JSON 校验器
     * @param executor 借用业务执行器
     * @param scheduler 借用计时器
     * @param clock 独立请求时钟
     * @param logger 项目诊断日志
     * @throws NullPointerException 当必需依赖为空时
     */
    public AgentService(AgentAccessGate gate, ILLMInteractionService interactions, IAgentToolSource tools,
            ObjectMapper mapper, Executor executor, ScheduledExecutorService scheduler, Clock clock, ILogger logger) {
        this(gate, interactions, tools, mapper, executor, scheduler, clock, logger, null, true, IAgentPromptSource.empty(), null, null, null);
    }

    /**
     * 装配固定持久化空间引用的独立轮次，构造期间不解析空间或写入数据库。
     * 会话请求必须同时提供邮箱固定的空间引用，原引用仍由实际运行守卫复核。
     * @param gate 统一门控
     * @param interactions 完整 LLM 交互端口
     * @param tools 显式工具选择端口
     * @param mapper 本地 JSON 转换器
     * @param executor 借用业务执行器
     * @param scheduler 借用计时器
     * @param clock 轮次时钟
     * @param logger 固定诊断日志
     * @param references 必需的当前空间解析和复核端口
     * @throws NullPointerException 当必需依赖为空时
     */
    public AgentService(AgentAccessGate gate, ILLMInteractionService interactions, IAgentToolSource tools,
            ObjectMapper mapper, Executor executor, ScheduledExecutorService scheduler, Clock clock, ILogger logger,
            IAgentMemoryReferences references) {
        this(gate, interactions, tools, mapper, executor, scheduler, clock, logger,
                Objects.requireNonNull(references, "持久化空间引用端口不能为空。"), false, IAgentPromptSource.empty(), null, null, null);
    }

    /**
     * 显式组合本轮临时资料端口；空引用表示独立无持久化模式，不授予资料来源任何业务权限。
     * @param gate 统一准入门控
     * @param interactions 完整交互端口
     * @param tools 本轮工具策略
     * @param mapper 协议转换器
     * @param executor 借用业务执行器
     * @param scheduler 借用计时器
     * @param clock 宿主时钟
     * @param logger 项目日志
     * @param references 可选持久化引用端口
     * @param prompts 可等待资料及有效性检查端口
     * @throws NullPointerException 当必需依赖为空时
     */
    public AgentService(AgentAccessGate gate, ILLMInteractionService interactions, IAgentToolSource tools,
            ObjectMapper mapper, Executor executor, ScheduledExecutorService scheduler, Clock clock, ILogger logger,
            @Nullable IAgentMemoryReferences references, IAgentPromptSource prompts) {
        this(gate, interactions, tools, mapper, executor, scheduler, clock, logger, references, references == null,
                Objects.requireNonNull(prompts, "Prompt 资料端口不能为空。"), null, null, null);
    }

    /**
     * 显式组合实际人格与记忆资料，原构造保留无人格源的独立使用方式。
     * @param gate 准入门控
     * @param interactions LLM 交互
     * @param tools 显式工具
     * @param mapper 协议转换器
     * @param executor 借用执行器
     * @param scheduler 借用计时器
     * @param clock 时钟
     * @param logger 日志
     * @param references 可选记忆引用
     * @param prompts 资料源
     * @param personalities 可选实际人格源
     */
    public AgentService(AgentAccessGate gate, ILLMInteractionService interactions, IAgentToolSource tools, ObjectMapper mapper, Executor executor,
            ScheduledExecutorService scheduler, Clock clock, ILogger logger, @Nullable IAgentMemoryReferences references,
            IAgentPromptSource prompts, @Nullable IAgentPersonalitySource personalities) {
        this(gate, interactions, tools, mapper, executor, scheduler, clock, logger, references, references == null, prompts, personalities, null, null);
    }

    /**
     * 组合独立历史记录能力；旧构造方式不自动获得历史持久化。
     * @param gate 准入门控
     * @param interactions 交互服务
     * @param tools 工具选择
     * @param mapper 协议转换器
     * @param executor 借用业务执行器
     * @param scheduler 借用计时器
     * @param clock 时钟
     * @param logger 日志
     * @param references 固定空间端口
     * @param prompts 资料端口
     * @param personalities 实际人格端口
     * @param history 可选历史记录端口
     */
    public AgentService(AgentAccessGate gate, ILLMInteractionService interactions, IAgentToolSource tools, ObjectMapper mapper, Executor executor,
            ScheduledExecutorService scheduler, Clock clock, ILogger logger, @Nullable IAgentMemoryReferences references,
            IAgentPromptSource prompts, @Nullable IAgentPersonalitySource personalities, @Nullable IAgentHistoryRecorder history) {
        this(gate, interactions, tools, mapper, executor, scheduler, clock, logger, references, references == null, prompts, personalities, history, null);
    }

    /**
     * 为默认组合提供摘要使用及重放校验，后台生命周期仍由独立摘要服务拥有。
     * @param gate 统一门控
     * @param interactions 原交互
     * @param tools 工具选择
     * @param mapper 协议转换器
     * @param executor 借用业务执行器
     * @param scheduler 借用调度器
     * @param clock 时钟
     * @param logger 日志
     * @param references 原空间端口
     * @param prompts 原资料端口
     * @param personalities 原人格端口
     * @param history 可选历史记录
     * @param summaries 可选摘要端口
     */
    public AgentService(AgentAccessGate gate, ILLMInteractionService interactions, IAgentToolSource tools, ObjectMapper mapper, Executor executor,
            ScheduledExecutorService scheduler, Clock clock, ILogger logger, @Nullable IAgentMemoryReferences references,
            IAgentPromptSource prompts, @Nullable IAgentPersonalitySource personalities, @Nullable IAgentHistoryRecorder history,
            @Nullable IAgentConversationSummaries summaries) {
        this(gate, interactions, tools, mapper, executor, scheduler, clock, logger, references, references == null, prompts, personalities, history, summaries);
    }

    /**
     * 统一组装资源和预编译管线，不把显式无记忆模式当作已获得持久化空间授权。
     * @param gate 统一门控
     * @param interactions 交互端口
     * @param tools 工具选择
     * @param mapper JSON 转换器
     * @param executor 借用执行器
     * @param scheduler 借用计时器
     * @param clock 时钟
     * @param logger 日志
     * @param references 可选空间端口
     * @param detachedMemory 是否使用既有显式无持久化记忆入口
     * @param prompts 本轮临时资料端口
     * @param personalities 可选实际人格源
     * @param history 可选历史记录端口
     * @param summaries 可选摘要复核端口
     * @throws NullPointerException 当必需依赖为空时
     */
    private AgentService(AgentAccessGate gate, ILLMInteractionService interactions, IAgentToolSource tools,
            ObjectMapper mapper, Executor executor, ScheduledExecutorService scheduler, Clock clock, ILogger logger,
            @Nullable IAgentMemoryReferences references, boolean detachedMemory, IAgentPromptSource prompts, @Nullable IAgentPersonalitySource personalities,
            @Nullable IAgentHistoryRecorder history, @Nullable IAgentConversationSummaries summaries) {
        this.gate = Objects.requireNonNull(gate, "Agent 门控不能为空。");
        this.executor = Objects.requireNonNull(executor, "业务执行器不能为空。");
        this.scheduler = Objects.requireNonNull(scheduler, "计时器不能为空。");
        this.clock = Objects.requireNonNull(clock, "轮次时钟不能为空。");
        this.logger = Objects.requireNonNull(logger, "日志端口不能为空。");
        this.references = detachedMemory ? null : Objects.requireNonNull(references, "空间引用端口不能为空。");
        this.prompts = Objects.requireNonNull(prompts, "Prompt 资料端口不能为空。");
        this.personalities = personalities;
        this.summaries = summaries;
        // 2026/11/02：保留步骤组合器，宿主只能在首次运行前安装插件并重新编译。
        executionPipeline = new AgentTurnPipeline(this::requireTurn, gate,
                Objects.requireNonNull(interactions, "交互端口不能为空。"), Objects.requireNonNull(tools, "工具源不能为空。"),
                Objects.requireNonNull(mapper, "JSON 校验器不能为空。"), executor, this.references, prompts, personalities, history, summaries);
        pipeline = executionPipeline.pipeline;
    }

    /**
     * 在业务执行器复核缓存结果的当前准入，不创建轮次、执行工具或预占预算。
     * @param handle 目标实例
     * @param request 原始请求
     * @return 当前准入或服务已停止的异常阶段
     * @throws NullPointerException 当实例或请求为空时
     */
    @Override public CompletionStage<AgentAccessDecision> checkAccess(AgentHandle handle, AgentRunRequest request) {
        Objects.requireNonNull(handle, "Agent 实例不能为空。");
        Objects.requireNonNull(request, "Agent 请求不能为空。");
        try {
            return CompletableFuture.supplyAsync(() -> {
                synchronized (lifecycleLock) {
                    if (closed) { throw new ExecutionStoppedException(ExecutionControl.StopReason.CLOSED); }
                }
                return gate.evaluate(handle, request.actor(), request.activity());
            }, executor);
        } catch (RuntimeException failure) { return CompletableFuture.failedStage(failure); }
    }

    /**
     * 使用本服务固定资料源复核原布局，外部引用提示不能替换原交互的实际引用。
     * 当前准入、epoch 和观察期限由邮箱前后复核；本方法不读取新资料或建立模型预算。
     * @param handle 原实例
     * @param request 原可信请求及原始文本凭据
     * @param result 原结果
     * @param control 本次观察拥有者借出的控制
     * @return 资料源明确支持且当前有效时为 true
     * @throws NullPointerException 当必需输入为空时
     */
    @Override public CompletionStage<Boolean> verifyPromptReplay(AgentHandle handle, AgentRunRequest request, AgentRunResult result, ExecutionControl control) {
        Objects.requireNonNull(control, "重放观察控制不能为空。");
        try {
            return CompletableFuture.supplyAsync(() -> {
                control.checkActive();
                synchronized (lifecycleLock) { if (closed) { throw new ExecutionStoppedException(ExecutionControl.StopReason.CLOSED); } }
                if (!handle.id().equals(result.agentId()) || !request.requestId().equals(result.requestId())) {
                    return CompletableFuture.completedStage(false);
                }
                var projection = result.promptTranscript();
                if (projection == null) { return CompletableFuture.completedStage(true); }
                var fixed = new AgentRunRequest(request.requestId(), request.actor(), request.activity(), projection.input().original(),
                        result.conversation(), result.memoryReference(), request.userText(), Long.MAX_VALUE,
                        projection.input().personality() == null ? null : projection.input().personality().identity(), projection.input().summary());
                var personality = projection.input().personality();
                if (personality != null && personalities == null) { return CompletableFuture.completedStage(false); }
                CompletionStage<Void> checked = personality == null ? CompletableFuture.completedStage(null)
                        : Objects.requireNonNull(personalities).verify(request.actor().scope(), personality, control);
                var summary = projection.input().summary();
                if (summary != null) {
                    if (summaries == null) { return CompletableFuture.completedStage(false); }
                    checked = checked.thenComposeAsync(ignored -> summaries.verify(fixed, summary, control), executor);
                }
                return control.guard(checked).thenComposeAsync(ignored -> projection.input().fragments().isEmpty() ? CompletableFuture.completedStage(true)
                        : control.guard(Objects.requireNonNull(prompts.verifyReplay(fixed, projection.input(), control), "资料重放复核阶段不能为空。")), executor);
            }, executor).thenCompose(Function.identity());
        } catch (RuntimeException failure) { return CompletableFuture.failedStage(failure); }
    }

    /**
     * 建立具有独立总预算的轮次，所有配置检查切入业务执行域。
     * @param handle 目标实例
     * @param request 固定宿主请求
     * @return 真正完成的只读业务阶段
     * @throws NullPointerException 当实例或请求为空时
     */
    public CompletionStage<AgentRunResult> run(AgentHandle handle, AgentRunRequest request) {
        return prepare(handle, request).start();
    }

    /**
     * 为宿主请求建立共享预算的子轮次，关闭不反向影响父执行。
     * @param handle 目标实例
     * @param request 固定宿主请求
     * @param parent 借用的总控制，必须已声明 LLM 所需维度
     * @return 真正完成的只读业务阶段
     * @throws NullPointerException 当实例、请求或父控制为空时
     */
    public CompletionStage<AgentRunResult> run(AgentHandle handle, AgentRunRequest request, ExecutionControl parent) {
        return prepare(handle, request, parent).start();
    }

    /**
     * 登记可取消的独立轮次，显式启动前不读取配置或执行模型。
     * @param handle 目标实例
     * @param request 固定宿主请求
     * @return 尚未执行业务的句柄
     * @throws NullPointerException 当实例或请求为空时
     */
    @Override public AgentRunExecution prepare(AgentHandle handle, AgentRunRequest request) {
        return prepareInternal(handle, request, null);
    }

    /**
     * 登记共享宿主预算的冷轮次，不新建父总额度。
     * @param handle 目标实例
     * @param request 固定宿主请求
     * @param parent 借用的父执行控制
     * @return 可在启动前取消的句柄
     * @throws NullPointerException 当实例、请求或父控制为空时
     */
    @Override public AgentRunExecution prepare(AgentHandle handle, AgentRunRequest request, ExecutionControl parent) {
        return prepareInternal(handle, request, Objects.requireNonNull(parent, "派生轮次的父控制不能为空。"));
    }

    /**
     * 接收邮箱固定的同一引用，显式无记忆服务拒绝此能力。
     * @param handle 目标实例
     * @param request 含会话和空间的请求
     * @param parent 可选借用父控制
     * @return 尚未执行业务的句柄
     * @throws UnsupportedOperationException 当服务没有空间端口时
     * @throws NullPointerException 当实例或请求为空时
     */
    @Override public AgentRunExecution prepareReferenced(AgentHandle handle, AgentRunRequest request, @Nullable ExecutionControl parent) {
        if (references == null) { throw new UnsupportedOperationException("当前独立轮次未装配持久化记忆引用保护。"); }
        Objects.requireNonNull(handle, "Agent 实例不能为空。"); Objects.requireNonNull(request, "固定引用请求不能为空。");
        if (request.conversation() == null || request.memoryReference() == null) {
            return completed(new AgentRunResult(handle.id(), request.requestId(), AgentRunResult.Status.CONFIGURATION_ERROR,
                    null, null, Map.of()));
        }
        return prepareInternal(handle, request, parent);
    }

    /**
     * 登记当前拥有关系和完成观察器，业务准备由返回句柄显式启动。
     * @param handle 目标实例
     * @param request 已固定请求
     * @param parent 可选借用父控制
     * @return 已登记取消和完成观察的冷执行句柄；跨实例绑定和当前不支持的持久化会话组合在分配控制前拒绝
     * @throws NullPointerException 当实例或请求为空时
     */
    private AgentRunExecution prepareInternal(AgentHandle handle, AgentRunRequest request, @Nullable ExecutionControl parent) {
        Objects.requireNonNull(handle, "Agent 实例不能为空。");
        Objects.requireNonNull(request, "Agent 请求不能为空。");
        if (request.activity() == AgentActivity.COLLECT || request.activity() == AgentActivity.TOOL
                || request.conversation() != null && !request.conversation().key().agentId().equals(handle.id())) {
            return completed(new AgentRunResult(handle.id(), request.requestId(),
                    AgentRunResult.Status.INVALID_INPUT, null, null, Map.of()));
        }
        if (references != null && request.conversation() != null && request.memoryReference() == null
                || references == null && request.memoryReference() != null) {
            return completed(new AgentRunResult(handle.id(), request.requestId(),
                    AgentRunResult.Status.CONFIGURATION_ERROR, null, null, Map.of()));
        }
        final AgentTurn turn; final java.time.Instant acceptedAt;
        try {
            acceptedAt = clock.instant();
            turn = new AgentTurn(handle, request, parent == null ? ExecutionControl.create() : parent.child(), parent == null, acceptedAt);
        } catch (RuntimeException failure) {
            return completed(new AgentRunResult(handle.id(), request.requestId(),
                    AgentTurn.failureStatus(failure), null, null,
                    parent == null ? Map.of() : parent.budget().snapshot()));
        }
        final boolean accepted;
        synchronized (lifecycleLock) {
            // 2026/11/02：即使首个轮次立即结束，也不能在之后更换运行管线。
            started = true;
            accepted = !closed;
            if (accepted) { turns.put(turn.entity.snapshot().id(), turn); }
        }
        if (!accepted) {
            turn.lifetime.cancel();
            var result = turn.result(null);
            turn.close();
            return completed(result);
        }
        turn.own(handle.onClose(turn.lifetime::cancel));
        var work = new CompletableFuture<>();
        var completion = turn.lifetime.guard(work).handle((ignored, failure) -> turn.settled().thenApply(done -> {
            var result = turn.result(failure);
            // 2026/11/02：与 LLM 日志共享请求标识，管线错误也保留安全的原因链。
            if (result.status() == AgentRunResult.Status.FAILED) { reportFailure(turn, result, failure); }
            try { observer.accept(AgentTurnReport.from(request, result, java.time.Duration.between(acceptedAt, clock.instant()).toMillis())); }
            catch (RuntimeException ignoredReportFailure) { /* 监控不能改写业务结果。 */ }
            return result;
        })).thenCompose(Function.identity()).whenComplete((ignored, failure) -> {
            turns.remove(turn.entity.snapshot().id(), turn);
            turn.close();
        }).toCompletableFuture().minimalCompletionStage();
        return new AgentRunExecution(completion, () -> launch(turn, work), turn.lifetime::cancel, turn::contains);
    }

    /**
     * 在观察器登记后异步准备和执行，未启动取消不会触发任何业务端口。
     * @param turn 当前资源拥有者
     * @param work 用于驱动统一完成路径的内部信号
     */
    private void launch(AgentTurn turn, CompletableFuture<Object> work) {
        try {
            turn.lifetime.checkActive();
            CompletableFuture.supplyAsync(() -> {
                turn.lifetime.checkActive();
                return turn.prepare(gate.evaluate(turn.handle, turn.request.actor(), turn.request.activity()), scheduler, clock);
            }, executor).thenComposeAsync(prepared -> {
                turn.lifetime.checkActive();
                if (!prepared) { return CompletableFuture.completedStage(null); }
                if (references == null) { return turn.control().guard(pipeline.execute(turn.entity, turn.control())); }
                return turn.resolveMemory(references).thenComposeAsync(ignored -> {
                    turn.lifetime.checkActive(); return turn.control().guard(pipeline.execute(turn.entity, turn.control()));
                }, executor);
            }, executor).whenComplete((value, failure) -> {
                if (failure == null) { work.complete(value); } else { work.completeExceptionally(failure); }
            });
        } catch (RuntimeException failure) { work.completeExceptionally(failure); }
    }

    /**
     * 为准备前拒绝提供已结束句柄，不创建运行资源。
     * @param result 明确终止结果
     * @return 启动和取消均无业务动作的句柄
     */
    private static AgentRunExecution completed(AgentRunResult result) {
        return new AgentRunExecution(CompletableFuture.completedStage(result), () -> {}, () -> false, ignored -> false);
    }

    /**
     * 仅查找本服务登记的资源拥有者，不提供任意服务定位。
     * @param id 管线实体标识
     * @return 当前拥有者
     * @throws IllegalStateException 当轮次已经回收或不属于本服务时
     */
    private AgentTurn requireTurn(EntityId id) {
        var turn = turns.get(id);
        if (turn == null) { throw new IllegalStateException("Agent 轮次已回收或未由当前服务登记。"); }
        return turn;
    }

    /**
     * 2026/11/02：只记录可信关联和脱敏结构，LLM 已收束的失败通过相同请求查其阶段日志。
     * @param turn 当前轮次拥有者
     * @param result 已确定的业务终态
     * @param failure 可选的管线异常，不导出原异常文本
     */
    private void reportFailure(AgentTurn turn, AgentRunResult result, @Nullable Throwable failure) {
        try {
            var attempts = result.interaction().map(LLMInteractionResult::modelAttempts).orElse(0L);
            var detail = failure == null ? "请查看相同请求的 LLM 交互失败日志。" : org.zexnocs.teanekocore.logger.FailureDiagnostic.from(failure).toString();
            logger.error(AgentService.class.getName(), "Agent 轮次执行失败。请求=" + turn.request.requestId() + "，活动=" + turn.request.activity()
                    + "，模型尝试=" + attempts + "，" + detail);
        }
        catch (RuntimeException ignored) { /* 不能因诊断失败而丢失轮次结果。 */ }
    }

    /** 关闭本服务在途轮次，回调在拥有关系锁外执行，不关闭实例、父请求或借用线程池。 */
    @Override public void close() {
        final List<AgentTurn> active;
        synchronized (lifecycleLock) {
            if (closed) { return; }
            closed = true;
            active = List.copyOf(turns.values());
        }
        active.forEach(turn -> turn.lifetime.cancel());
    }
}
