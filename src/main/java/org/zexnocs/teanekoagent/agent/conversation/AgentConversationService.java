package org.zexnocs.teanekoagent.agent.conversation;

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.agent.*;
import org.zexnocs.teanekoagent.agent.access.AgentAccessDecision;
import org.zexnocs.teanekoagent.agent.memory.AgentMemoryReferenceException;
import org.zexnocs.teanekoagent.agent.memory.IAgentMemoryReferences;
import org.zexnocs.teanekoagent.agent.personality.IAgentPersonalitySource;
import org.zexnocs.teanekoagent.agent.prompt.AgentPromptException;
import org.zexnocs.teanekoagent.agent.summary.AgentSummarySnapshot;
import org.zexnocs.teanekoagent.agent.summary.IAgentConversationSummaries;
import org.zexnocs.teanekoagent.memory.namespace.MemoryNamespaceReference;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.actuator.execution.ExecutionStoppedException;
import org.zexnocs.teanekocore.framework.composition.ComponentPatch;
import org.zexnocs.teanekocore.framework.composition.Entity;
import org.zexnocs.teanekocore.framework.composition.EntityId;
import org.zexnocs.teanekocore.framework.composition.EntitySnapshot;
import org.zexnocs.teanekocore.logger.ILogger;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 按 client/scope/实例串行执行的邮箱，入队顺序以锁内登记为准，跨邮箱独立调度。
 * 锁只保护拥有关系和状态，不执行配置、启动、取消、资源关闭或用户 Future 回调。
 * 空闲会话按 TTL 和容量惰性淘汰；切换代次不移除仍占运行槽的旧邮箱。
 *
 * @author zExNocs
 * @date 2026/10/30
 * @since 4.0.0
 * @version 4.5.0-l
 */
public final class AgentConversationService implements IAgentService, IAgentConversationControl, AutoCloseable {
    private final IAgentTurnService turns;
    private final IAgentQueueDefaults defaults;
    private final IAgentConversationDefaults conversations;
    private final IAgentConversationPolicy policy;
    private final AgentConversationEngine engine;
    private final Executor executor;
    private final ScheduledExecutorService scheduler;
    private final Clock clock;
    private final ILogger logger;
    private final @Nullable AgentMemoryBoundary memory;
    private final AgentMemoryBoundary observations;
    private final @Nullable IAgentPersonalitySource personalities;
    private final @Nullable IAgentConversationSummaries summaries;
    private final Object lock = new Object();
    private final Map<AgentConversationKey, Mailbox> mailboxes = new LinkedHashMap<>();
    private final ConcurrentLinkedQueue<Mailbox> retired = new ConcurrentLinkedQueue<>();
    private boolean closed;

    /**
     * 保存借用资源，不创建实例、读取策略或启动计时任务。
     * @param turns 可显式启动和取消的独立轮次端口
     * @param defaults 快速本地排队策略
     * @param executor 借用业务执行器
     * @param scheduler 借用排队计时器
     * @param clock 等待时限来源
     * @param logger 固定诊断端口
     * @throws NullPointerException 当运行依赖为空时
     */
    public AgentConversationService(IAgentTurnService turns, IAgentQueueDefaults defaults, Executor executor,
            ScheduledExecutorService scheduler, Clock clock, ILogger logger) {
        this(turns, defaults, executor, scheduler, clock, logger, AgentConversationSettings::defaults);
    }

    /**
     * 保存独立的会话保留策略，构造时不解析配置或启动清理任务。
     * @param turns 可显式启动的独立轮次端口
     * @param defaults 快速本地排队策略
     * @param executor 借用业务执行器
     * @param scheduler 借用排队计时器
     * @param clock 等待及空闲时限来源
     * @param logger 固定诊断端口
     * @param conversations 快速本地会话保留策略
     * @throws NullPointerException 当运行依赖为空时
     */
    public AgentConversationService(IAgentTurnService turns, IAgentQueueDefaults defaults, Executor executor,
            ScheduledExecutorService scheduler, Clock clock, ILogger logger, IAgentConversationDefaults conversations) {
        this(turns, defaults, executor, scheduler, clock, logger, conversations,
                (handle, request) -> AgentConversationOptions.defaults(), new ObjectMapper());
    }

    /**
     * 装配可替换的窗口策略和协议处理，不在构造时访问配置或业务资源。
     * @param turns 独立轮次端口
     * @param defaults 快速排队策略
     * @param executor 借用业务执行器
     * @param scheduler 借用计时器
     * @param clock 可信时钟
     * @param logger 固定诊断端口
     * @param conversations 空闲保留策略
     * @param policy 出队时读取的窗口策略
     * @param mapper 本地协议 JSON 转换器
     * @throws NullPointerException 当必需依赖为空时
     */
    public AgentConversationService(IAgentTurnService turns, IAgentQueueDefaults defaults, Executor executor,
            ScheduledExecutorService scheduler, Clock clock, ILogger logger, IAgentConversationDefaults conversations,
            IAgentConversationPolicy policy, ObjectMapper mapper) {
        this(turns, defaults, executor, scheduler, clock, logger, conversations, policy, mapper, null);
    }

    /**
     * 装配持久化引用保护，旧构造显式保留无持久化记忆模式，构造不执行任何 I/O。
     * @param turns 支持固定引用的独立轮次端口
     * @param defaults 排队策略
     * @param executor 借用业务执行器
     * @param scheduler 借用计时器
     * @param clock 可信时钟
     * @param logger 固定诊断
     * @param conversations 保留策略
     * @param policy 窗口策略
     * @param mapper 协议转换器
     * @param references 空间端口；为空时显式不启用持久化记忆
     * @throws NullPointerException 当必需依赖为空时
     */
    public AgentConversationService(IAgentTurnService turns, IAgentQueueDefaults defaults, Executor executor,
            ScheduledExecutorService scheduler, Clock clock, ILogger logger, IAgentConversationDefaults conversations,
            IAgentConversationPolicy policy, ObjectMapper mapper, @Nullable IAgentMemoryReferences references) {
        this(turns, defaults, executor, scheduler, clock, logger, conversations, policy, mapper, references, null);
    }

    /**
     * 组合人格生命周期，在历史装配前协调人格身份；旧构造保留显式无人格模式。
     * @param turns 独立轮次端口
     * @param defaults 排队策略
     * @param executor 借用业务执行器
     * @param scheduler 借用计时器
     * @param clock 可信时钟
     * @param logger 日志
     * @param conversations 会话保留策略
     * @param policy 窗口策略
     * @param mapper 协议转换器
     * @param references 可选记忆引用
     * @param personalities 可选实际人格源，完整装配与独立轮次使用同一源
     */
    public AgentConversationService(IAgentTurnService turns, IAgentQueueDefaults defaults, Executor executor,
            ScheduledExecutorService scheduler, Clock clock, ILogger logger, IAgentConversationDefaults conversations,
            IAgentConversationPolicy policy, ObjectMapper mapper, @Nullable IAgentMemoryReferences references,
            @Nullable IAgentPersonalitySource personalities) {
        this(turns, defaults, executor, scheduler, clock, logger, conversations, policy, mapper, references, personalities, null);
    }
    /**
     * 显式组合后台摘要，邮箱只管理纯数据采用及成功轮次交接，不拥有摘要控制。
     * @param turns 独立轮次端口
     * @param defaults 排队策略
     * @param executor 借用业务执行器
     * @param scheduler 借用调度器
     * @param clock 时间来源
     * @param logger 固定日志
     * @param conversations 会话保留策略
     * @param policy 窗口策略
     * @param mapper 协议转换器
     * @param references 可选空间端口
     * @param personalities 可选人格端口
     * @param summaries 可选会话压缩端口
     */
    public AgentConversationService(IAgentTurnService turns, IAgentQueueDefaults defaults, Executor executor,
            ScheduledExecutorService scheduler, Clock clock, ILogger logger, IAgentConversationDefaults conversations,
            IAgentConversationPolicy policy, ObjectMapper mapper, @Nullable IAgentMemoryReferences references,
            @Nullable IAgentPersonalitySource personalities, @Nullable IAgentConversationSummaries summaries) {
        this.turns = Objects.requireNonNull(turns, "独立轮次端口不能为空。");
        this.defaults = Objects.requireNonNull(defaults, "排队策略端口不能为空。");
        this.conversations = Objects.requireNonNull(conversations, "会话保留策略端口不能为空。");
        this.policy = Objects.requireNonNull(policy, "会话窗口策略不能为空。");
        this.engine = new AgentConversationEngine(mapper);
        this.executor = Objects.requireNonNull(executor, "邮箱执行器不能为空。");
        this.scheduler = Objects.requireNonNull(scheduler, "排队计时器不能为空。");
        this.clock = Objects.requireNonNull(clock, "排队时钟不能为空。");
        this.logger = Objects.requireNonNull(logger, "邮箱日志端口不能为空。");
        observations = new AgentMemoryBoundary(references, turns, executor, scheduler, clock);
        this.memory = references == null ? null : observations;
        this.personalities = personalities;
        this.summaries = summaries;
    }

    /**
     * 按共享会话排队，出队后才建立独立模型预算。
     * @param handle 目标实例
     * @param request 固定请求
     * @return 可等待的只读会话结果
     * @throws NullPointerException 当实例或请求为空时
     */
    @Override public CompletionStage<AgentRunResult> run(AgentHandle handle, AgentRunRequest request) {
        return submit(handle, request, null);
    }

    /**
     * 排队执行共享预算的派生请求，排队中同样继承宿主终止。
     * @param handle 目标实例
     * @param request 固定请求
     * @param parent 借用父控制
     * @return 排队和实际执行的完整结束信号
     * @throws NullPointerException 当实例、请求或父控制为空时
     */
    @Override public CompletionStage<AgentRunResult> run(AgentHandle handle, AgentRunRequest request, ExecutionControl parent) {
        return submit(handle, request, Objects.requireNonNull(parent, "派生请求父控制不能为空。"));
    }

    /**
     * 只观察原请求或重放有界缓存，不对缺失记录重新入队。
     * @param handle 原目标实例
     * @param request 原始可信请求
     * @return 只读观察结果
     * @throws NullPointerException 当实例或请求为空时
     */
    @Override public CompletionStage<AgentRunResult> retry(AgentHandle handle, AgentRunRequest request) {
        return observeKnown(handle, request, null);
    }

    /**
     * 为重试观察借用父控制，停止观察不会停止原请求。
     * @param handle 原目标实例
     * @param request 原始请求
     * @param parent 本次观察的父控制
     * @return 受父终止约束的只读结果
     * @throws NullPointerException 当必需参数为空时
     */
    @Override public CompletionStage<AgentRunResult> retry(AgentHandle handle, AgentRunRequest request, ExecutionControl parent) {
        return observeKnown(handle, request, Objects.requireNonNull(parent, "重试观察父控制不能为空。"));
    }

    /**
     * 锁外比较消息指纹和执行链，锁内只查找已有拥有关系；查询与完成竞争时重试查找。
     * @param handle 目标实例
     * @param request 原始请求
     * @param parent 可选观察父控制
     * @return 原执行观察或明确拒绝，不创建轮次和排队计时
     * @throws NullPointerException 当实例或请求为空时
     */
    private CompletionStage<AgentRunResult> observeKnown(AgentHandle handle, AgentRunRequest request, @Nullable ExecutionControl parent) {
        Objects.requireNonNull(handle, "Agent 实例不能为空。"); Objects.requireNonNull(request, "Agent 请求不能为空。");
        boolean unavailable;
        synchronized (lock) { unavailable = closed; }
        if (unavailable || handle.isClosed()) { return immediate(handle, request, AgentRunResult.Status.CANCELLED, parent); }
        final AgentRequestJournal.Identity identity;
        final Instant now;
        try { identity = engine.identity(request); now = clock.instant(); }
        catch (RuntimeException failure) { return immediate(handle, request, AgentRunResult.Status.INVALID_INPUT, parent); }
        var key = new AgentConversationKey(handle.id(), request.actor().scope());
        CompletionStage<AgentRunResult> source = null;
        AgentConversationStamp stamp = null;
        AgentRunResult.Status rejection = null;
        try {
            for (;;) {
                final Mailbox observed;
                final AgentRunExecution owner;
                synchronized (lock) {
                    observed = mailboxes.get(key);
                    owner = observed == null || observed.active == null ? null : observed.active.execution;
                }
                boolean recursive = parent != null && owner != null && owner.contains(parent);
                synchronized (lock) {
                    var current = mailboxes.get(key);
                    var currentOwner = current == null || current.active == null ? null : current.active.execution;
                    if (observed != current || owner != currentOwner) { continue; }
                    if (current != null && expired(current, current.retention, now)) { retire(current); current = null; }
                    if (closed || handle.isClosed()) { rejection = AgentRunResult.Status.CANCELLED; }
                    else if (current == null || current.retired) { rejection = AgentRunResult.Status.REPLAY_UNAVAILABLE; }
                    else {
                        stamp = state(current).stamp();
                        var pending = current.requests.get(request.requestId());
                        var completed = journal(current).find(request.requestId()).orElse(null);
                        var originalIdentity = pending != null ? pending.identity : completed == null ? null : completed.identity();
                        if (originalIdentity == null) { rejection = AgentRunResult.Status.REPLAY_UNAVAILABLE; }
                        else if (!identity.equals(originalIdentity)) { rejection = AgentRunResult.Status.REQUEST_CONFLICT; }
                        else if (pending != null) {
                            if (!Objects.equals(stamp, pending.stamp)) { rejection = AgentRunResult.Status.STALE_CONVERSATION; }
                            else if (recursive) { rejection = AgentRunResult.Status.REENTRANT; }
                            else { source = pending.result.minimalCompletionStage(); }
                        } else {
                            var saved = completed.result();
                            if (saved == null) { rejection = AgentRunResult.Status.REPLAY_UNAVAILABLE; }
                            else { source = CompletableFuture.completedStage(saved); }
                        }
                    }
                    break;
                }
            }
        } catch (RuntimeException failure) { report(); rejection = AgentRunResult.Status.FAILED; }
        drainRetired();
        return rejection != null ? immediate(handle, request, rejection, parent)
                : replay(handle, request, Objects.requireNonNull(source), Objects.requireNonNull(stamp), parent);
    }

    /**
     * 以独立有界观察复核实际准入、代次和资料来源，未实现资料重放协议时明确不可重放。
     * 派生观察不更改原结果、控制或预算，也不以不可重放为由补执行模型或工具。
     * @param handle 原目标实例
     * @param request 原始请求
     * @param source 原完成信号
     * @param stamp 查找时的会话代次
     * @param parent 可选观察父控制
     * @return 可等待的只读重放结果
     */
    private CompletionStage<AgentRunResult> replay(AgentHandle handle, AgentRunRequest request, CompletionStage<AgentRunResult> source,
            AgentConversationStamp stamp, @Nullable ExecutionControl parent) {
        try {
            var deadline = clock.instant().plus(defaults.resolve().waitTimeout());
            return observations.awaitControlled(parent, handle, deadline, control -> control.guard(source).thenComposeAsync(raw -> {
                control.checkActive();
                var before = replayStatus(handle, stamp);
                if (before != null) { return CompletableFuture.completedStage(restate(raw, before)); }
                return replayAccess(handle, request, raw).thenComposeAsync(checked -> {
                    control.checkActive();
                    if (checked.status() != AgentRunResult.Status.SUCCESS || checked.promptTranscript() == null
                            || checked.promptTranscript().input().fragments().isEmpty() && checked.promptTranscript().input().personality() == null
                                    && checked.promptTranscript().input().summary() == null) {
                        return CompletableFuture.completedStage(checked);
                    }
                    return control.guard(Objects.requireNonNull(turns.verifyPromptReplay(handle, request, checked, control), "资料重放阶段不能为空。"))
                            .thenComposeAsync(allowed -> {
                                control.checkActive();
                                if (!Boolean.TRUE.equals(allowed)) { return CompletableFuture.completedStage(restate(checked, AgentRunResult.Status.REPLAY_UNAVAILABLE)); }
                                return replayAccess(handle, request, checked);
                            }, executor);
                }, executor).thenApplyAsync(checked -> {
                    control.checkActive(); var after = replayStatus(handle, stamp); return after == null ? checked : restate(checked, after);
                }, executor).exceptionally(failure -> restate(raw, AgentMemoryBoundary.status(failure)));
            }, executor)).handle((result, failure) -> failure == null ? result
                    : rejectedResult(handle, request, AgentMemoryBoundary.status(failure), parent));
        } catch (RuntimeException failure) { return immediate(handle, request, AgentMemoryBoundary.status(failure), parent); }
    }

    /**
     * 在资料观察前后复核当前准入及实际引用，不把已结束的原运行控制作为当前授权。
     * @param handle 原实例
     * @param request 原可信请求
     * @param raw 保留诊断的原结果
     * @return 当前仍允许时保留原对象，否则隐藏回答
     */
    private CompletionStage<AgentRunResult> replayAccess(AgentHandle handle, AgentRunRequest request, AgentRunResult raw) {
        var boundary = memory;
        if (boundary != null) {
            var reference = raw.memoryReference();
            if (reference == null) { return CompletableFuture.completedStage(restate(raw, AgentRunResult.Status.CONFIGURATION_ERROR)); }
            return boundary.verify(handle, request, reference).thenApply(ignored -> raw);
        }
        return Objects.requireNonNull(turns.checkAccess(handle, request), "重放准入阶段不能为空。").thenApply(decision -> {
            Objects.requireNonNull(decision, "重放准入结果不能为空。");
            return decision.allowed() ? raw : new AgentRunResult(raw.agentId(), raw.requestId(), AgentMemoryBoundary.accessStatus(decision),
                    Optional.of(decision), raw.interaction(), raw.budgetUsage(), raw.conversation(), raw.memoryReference(), raw.promptTranscript());
        });
    }

    /**
     * 显式读取当前权威引用，再协调同一 scope 的全部实例；调用方负责管理权限和先前持久化提交。
     * @param scope 已授权作用域
     * @param parent 借用的管理控制
     * @return 实际更换 epoch 的邮箱数，同引用和不存在均为零
     * @throws NullPointerException 当必需参数为空时
     * @throws UnsupportedOperationException 当没有装配引用保护时
     */
    @Override public CompletionStage<Integer> synchronizeMemory(AgentScope scope, ExecutionControl parent) {
        Objects.requireNonNull(scope, "同步作用域不能为空。"); Objects.requireNonNull(parent, "同步父控制不能为空。");
        var boundary = memory;
        if (boundary == null) { throw new UnsupportedOperationException("当前邮箱未装配持久化记忆引用。"); }
        try {
            var deadline = clock.instant().plus(defaults.resolve().waitTimeout());
            return boundary.awaitControlled(parent, null, deadline, control -> boundary.references().resolve(scope)
                    .thenApplyAsync(reference -> {
                        control.checkActive();
                        parent.checkActive();
                        if (!reference.scope().equals(scope)) { throw new AgentMemoryReferenceException(AgentMemoryReferenceException.Reason.UNAVAILABLE); }
                        return alignMemory(reference, null);
                    }, executor));
        } catch (RuntimeException failure) { return CompletableFuture.failedStage(failure); }
    }

    /**
     * 只在锁内提交不可变引用、代次和空窗口，锁外取消旧请求；不接受低版本迟到观察。
     * @param reference 当前权威观察
     * @param head 可保留并转入新代次的当前队首，同步通知时为空
     * @return 实际推进代次的邮箱数
     * @throws AgentMemoryReferenceException 当观察落后或同版本定位冲突时
     * @throws ExecutionStoppedException 当服务已关闭时
     */
    private int alignMemory(MemoryNamespaceReference reference, @Nullable Entry head) {
        var cancelled = new ArrayList<Entry>();
        var running = new ArrayList<Entry>();
        var now = clock.instant();
        int changed = 0;
        synchronized (lock) {
            if (closed) { throw new ExecutionStoppedException(ExecutionControl.StopReason.CLOSED); }
            if (head != null && (head.phase != Phase.PREPARING || head.stopped != null)) { return 0; }
            var related = mailboxes.values().stream().filter(box -> !box.retired && box.key.scope().equals(reference.scope())).toList();
            for (var box : related) {
                box.entity.snapshot().find(AgentComponents.MEMORY_REFERENCE).ifPresent(previous -> validateMemoryAdvance(reference, previous));
            }
            for (var box : related) {
                var previous = box.entity.snapshot().find(AgentComponents.MEMORY_REFERENCE).orElse(null);
                if (reference.equals(previous)) { continue; }
                var patch = ComponentPatch.builder().put(AgentComponents.MEMORY_REFERENCE, reference);
                if (previous != null) {
                    changed++;
                    var stamp = new AgentConversationStamp(box.key, UUID.randomUUID());
                    patch.put(AgentConversationState.COMPONENT, new AgentConversationState(stamp, later(now, state(box).lastActivity())))
                            .put(AgentConversationWindow.COMPONENT, AgentConversationWindow.empty()).remove(AgentSummarySnapshot.COMPONENT)
                            .put(AgentRequestJournal.COMPONENT, AgentRequestJournal.empty());
                    for (var entry : List.copyOf(box.requests.values())) {
                        if (entry == head) { entry.stamp = stamp; continue; }
                        if (entry.phase == Phase.FINISHED) { continue; }
                        entry.stopped = AgentRunResult.Status.STALE_CONVERSATION;
                        if (entry.phase == Phase.RUNNING) { running.add(entry); }
                        else { detach(entry, now); cancelled.add(entry); }
                    }
                }
                box.entity.commit(box.entity.snapshot().revision(), patch.build());
            }
        }
        running.forEach(entry -> stop(entry, AgentRunResult.Status.STALE_CONVERSATION, false));
        cancelled.forEach(entry -> deliver(entry, result(entry, null, null), null));
        return changed;
    }

    /**
     * 区分迟到观察与同版本定位损坏，不允许回退绑定版本或同一空间的数据代次。
     * @param reference 新观察
     * @param previous 已提交观察
     * @throws AgentMemoryReferenceException 当版本回退或同版本目标不一致时
     */
    private static void validateMemoryAdvance(MemoryNamespaceReference reference, MemoryNamespaceReference previous) {
        if (reference.bindingRevision() < previous.bindingRevision()
                || reference.namespaceId().equals(previous.namespaceId()) && reference.generation() < previous.generation()) {
            throw new AgentMemoryReferenceException(AgentMemoryReferenceException.Reason.CHANGED);
        }
        if (reference.bindingRevision() == previous.bindingRevision() && !reference.namespaceId().equals(previous.namespaceId())) {
            throw new AgentMemoryReferenceException(AgentMemoryReferenceException.Reason.UNAVAILABLE);
        }
    }

    /**
     * 锁外读取时钟，再校验当前缓存拥有关系，不让已失效会话重新公开旧答案。
     * @param handle 目标实例
     * @param stamp 查找时的代次
     * @return 仍有效时为空，否则为取消或代次失效
     */
    private AgentRunResult.@Nullable Status replayStatus(AgentHandle handle, AgentConversationStamp stamp) {
        var now = clock.instant();
        synchronized (lock) {
            if (closed || handle.isClosed()) { return AgentRunResult.Status.CANCELLED; }
            var mailbox = mailboxes.get(stamp.key());
            return mailbox == null || mailbox.retired || !state(mailbox).stamp().equals(stamp) || expired(mailbox, mailbox.retention, now)
                    ? AgentRunResult.Status.STALE_CONVERSATION : null;
        }
    }

    /**
     * 保留已观察诊断，只替换当前交付状态。
     * @param result 原结果
     * @param status 当前明确状态
     * @return 不会把拒绝或失效诊断作为回答交付的结果
     */
    private static AgentRunResult restate(AgentRunResult result, AgentRunResult.Status status) {
        return new AgentRunResult(result.agentId(), result.requestId(), status, result.access(), result.interaction(), result.budgetUsage(), result.conversation(), result.memoryReference(), result.promptTranscript());
    }

    /**
     * 在锁外固定策略和监听，再登记请求；执行链查询也在锁外，状态改变时重试登记。
     * @param handle 实例拥有者
     * @param request 可信请求
     * @param parent 可选借用父控制
     * @return 只读结果，不对同 ID 的不同请求隐式合并
     * @throws NullPointerException 当实例或请求为空时
     */
    private CompletionStage<AgentRunResult> submit(AgentHandle handle, AgentRunRequest request, @Nullable ExecutionControl parent) {
        Objects.requireNonNull(handle, "Agent 实例不能为空。");
        Objects.requireNonNull(request, "Agent 请求不能为空。");
        if (request.activity() == AgentActivity.COLLECT || request.activity() == AgentActivity.TOOL) {
            return immediate(handle, request, AgentRunResult.Status.INVALID_INPUT, parent);
        }
        boolean unavailable;
        synchronized (lock) { unavailable = closed; }
        if (unavailable || handle.isClosed()) { return immediate(handle, request, AgentRunResult.Status.CANCELLED, parent); }
        final AgentQueueSettings settings;
        final AgentConversationSettings retention;
        final AgentRequestJournal.Identity identity;
        final Instant acceptedAt;
        try {
            acceptedAt = clock.instant();
            settings = Objects.requireNonNull(defaults.resolve(), "排队策略不能为空。");
            retention = Objects.requireNonNull(conversations.resolve(), "会话保留策略不能为空。");
        } catch (RuntimeException failure) {
            report(); return immediate(handle, request, AgentRunResult.Status.CONFIGURATION_ERROR, parent);
        }
        try { identity = engine.identity(request); }
        catch (RuntimeException failure) { return immediate(handle, request, AgentRunResult.Status.INVALID_INPUT, parent); }
        final Entry entry;
        try {
            entry = new Entry(handle, request, settings, acceptedAt,
                    parent == null ? ExecutionControl.create() : parent.child(), parent == null, identity);
        } catch (RuntimeException failure) { return immediate(handle, request, failureStatus(failure), parent); }
        entry.own(entry.lifetime.onTermination(reason -> stop(entry, stopStatus(reason), false)));
        entry.own(handle.onClose(entry.lifetime::cancel));
        Entry selected = null;
        Mailbox created = null;
        AgentRunResult replayed = null;
        AgentConversationStamp replayStamp = null;
        AgentRunResult.Status rejection = null;
        try {
            var candidate = new Mailbox(entry.key, retention, acceptedAt);
            for (;;) {
                final Mailbox observed;
                final AgentRunExecution owner;
                synchronized (lock) {
                    observed = mailboxes.get(entry.key);
                    owner = observed == null || observed.active == null ? null : observed.active.execution;
                }
                boolean recursive = parent != null && owner != null && owner.contains(parent);
                synchronized (lock) {
                    if (entry.phase == Phase.FINISHED) { return entry.result.minimalCompletionStage(); }
                    var current = mailboxes.get(entry.key);
                    var currentOwner = current == null || current.active == null ? null : current.active.execution;
                    if (current != observed || currentOwner != owner) { continue; }
                    purgeExpired(retention, acceptedAt);
                    current = mailboxes.get(entry.key);
                    if (closed) { rejection = AgentRunResult.Status.CANCELLED; }
                    else if (recursive) { rejection = AgentRunResult.Status.REENTRANT; }
                    else if (current != null && current.retired) { rejection = AgentRunResult.Status.BUSY; }
                    else if (current != null && current.requests.containsKey(request.requestId())) {
                        rejection = current.requests.get(request.requestId()).identity.equals(identity)
                                ? AgentRunResult.Status.DUPLICATE_REQUEST : AgentRunResult.Status.REQUEST_CONFLICT;
                    } else if (current != null && journal(current).find(request.requestId()).isPresent()) {
                        var completed = journal(current).find(request.requestId()).orElseThrow();
                        if (!completed.identity().equals(identity)) { rejection = AgentRunResult.Status.REQUEST_CONFLICT; }
                        else if (completed.result() == null) { rejection = AgentRunResult.Status.REPLAY_UNAVAILABLE; }
                        else { replayed = completed.result(); replayStamp = state(current).stamp(); }
                    } else if (current != null && current.active != null && current.waiting.size() >= settings.maxWaiting()) {
                        rejection = AgentRunResult.Status.BUSY;
                    } else if (current == null && !makeRoom(retention.maxConversations() - 1)) {
                        rejection = AgentRunResult.Status.BUSY;
                    } else {
                        if (current == null) { current = candidate; created = current; mailboxes.put(entry.key, current); }
                        current.retention = retention;
                        touch(current, acceptedAt);
                        entry.mailbox = current;
                        entry.stamp = state(current).stamp();
                        current.requests.put(request.requestId(), entry);
                        if (current.active == null) {
                            current.active = entry; entry.phase = Phase.SELECTED; selected = entry;
                        } else { current.waiting.addLast(entry); entry.phase = Phase.WAITING; }
                    }
                    break;
                }
            }
        } catch (RuntimeException failure) { rejection = AgentRunResult.Status.FAILED; report(); }
        drainRetired();
        if (created != null) {
            var owner = created;
            owner.own(handle.onClose(() -> retireClosed(owner)));
        }
        if (replayed != null) {
            stop(entry, AgentRunResult.Status.CANCELLED, false);
            return replay(handle, request, CompletableFuture.completedStage(replayed), Objects.requireNonNull(replayStamp), parent);
        }
        if (rejection != null) { stop(entry, rejection, false); }
        else {
            armWaiting(entry);
            if (selected != null && entry.phase != Phase.FINISHED) { schedule(selected); }
        }
        return entry.result.minimalCompletionStage();
    }

    /**
     * 读取仍有效的 Conversation 实体；观察不会延长 TTL，也不暴露运行控制。
     * @param handle 目标实例
     * @param scope 客户端及作用域
     * @return 不可变数据快照，不存在或过期时为空
     * @throws NullPointerException 当实例或作用域为空时
     */
    @Override public Optional<EntitySnapshot> snapshot(AgentHandle handle, AgentScope scope) {
        Objects.requireNonNull(handle, "Agent 实例不能为空。");
        var key = new AgentConversationKey(handle.id(), scope);
        var now = clock.instant();
        final EntitySnapshot snapshot;
        synchronized (lock) {
            var mailbox = mailboxes.get(key);
            if (mailbox != null && expired(mailbox, mailbox.retention, now)) { retire(mailbox); mailbox = null; }
            snapshot = closed || mailbox == null || mailbox.retired ? null : mailbox.entity.snapshot();
        }
        drainRetired();
        return Optional.ofNullable(snapshot);
    }

    /**
     * 锁内推进代次并移除未启动旧项，锁外交付和取消；运行槽保留到实际结束回执。
     * @param handle 目标实例
     * @param scope 待失效作用域
     * @return 新代次，不存在或过期时为空
     * @throws NullPointerException 当实例或作用域为空时
     */
    @Override public Optional<AgentConversationStamp> invalidate(AgentHandle handle, AgentScope scope) {
        Objects.requireNonNull(handle, "Agent 实例不能为空。");
        var key = new AgentConversationKey(handle.id(), scope);
        var stamp = new AgentConversationStamp(key, UUID.randomUUID());
        var now = clock.instant();
        var cancelled = new ArrayList<Entry>();
        Entry running = null;
        boolean changed = false;
        synchronized (lock) {
            var mailbox = mailboxes.get(key);
            if (mailbox != null && expired(mailbox, mailbox.retention, now)) { retire(mailbox); mailbox = null; }
            if (!closed && mailbox != null && !mailbox.retired) {
                changed = true;
                mailbox.entity.commit(mailbox.entity.snapshot().revision(), ComponentPatch.builder()
                        .put(AgentConversationState.COMPONENT, new AgentConversationState(stamp, later(now, state(mailbox).lastActivity())))
                        .put(AgentConversationWindow.COMPONENT, AgentConversationWindow.empty()).remove(AgentSummarySnapshot.COMPONENT)
                        .put(AgentRequestJournal.COMPONENT, AgentRequestJournal.empty()).build());
                mailbox.waiting.clear();
                for (var entry : List.copyOf(mailbox.requests.values())) {
                    if (entry.phase == Phase.FINISHED) { continue; }
                    if (entry.stopped == null) { entry.stopped = AgentRunResult.Status.STALE_CONVERSATION; }
                    if (entry.phase == Phase.RUNNING) { running = entry; }
                    else { detach(entry, now); cancelled.add(entry); }
                }
            }
        }
        drainRetired();
        if (running != null) { stop(running, AgentRunResult.Status.STALE_CONVERSATION, false); }
        cancelled.forEach(entry -> deliver(entry, result(entry, null, null), null));
        return changed ? Optional.of(stamp) : Optional.empty();
    }

    /**
     * 使用最新策略显式清理，不安装周期任务，不淘汰在途会话。
     * @return 本次实际移除数量
     * @throws IllegalStateException 当保留策略不可用时
     */
    @Override public int purgeIdle() {
        synchronized (lock) { if (closed) { return 0; } }
        final AgentConversationSettings settings;
        final Instant now;
        try { settings = Objects.requireNonNull(conversations.resolve()); now = clock.instant(); }
        catch (RuntimeException ignored) { throw new IllegalStateException("Agent 会话清理配置不可用。"); }
        final int removed;
        synchronized (lock) {
            int before = mailboxes.size();
            if (!closed) { purgeExpired(settings, now); makeRoom(settings.maxConversations()); }
            removed = before - mailboxes.size();
        }
        drainRetired();
        return removed;
    }

    /**
     * 锁内移除所有过期空闲项，运行、排队或准备中的邮箱始终保留。
     * @param settings 本次策略
     * @param now 锁外取得的当前时间
     */
    private void purgeExpired(AgentConversationSettings settings, Instant now) {
        for (var mailbox : List.copyOf(mailboxes.values())) {
            if (expired(mailbox, settings, now)) { retire(mailbox); }
        }
    }

    /**
     * 判断空闲有效期，时钟回拨不产生负时长淘汰。
     * @param mailbox 当前邮箱
     * @param settings 本次保留策略
     * @param now 当前时间
     * @return 没有在途请求且达到有效期时为 true
     */
    private static boolean expired(Mailbox mailbox, AgentConversationSettings settings, Instant now) {
        return mailbox.requests.isEmpty() && Duration.between(state(mailbox).lastActivity(), now).compareTo(settings.idleTtl()) >= 0;
    }

    /**
     * 锁内按最近活动时间淘汰空闲邮箱，不能移除活跃项来绕过单会话顺序。
     * @param targetSize 目标保留数量
     * @return 已收缩至目标数量时为 true，全部剩余项活跃时为 false
     */
    private boolean makeRoom(int targetSize) {
        while (mailboxes.size() > targetSize) {
            var oldest = mailboxes.values().stream().filter(mailbox -> mailbox.requests.isEmpty())
                    .min(Comparator.comparing(mailbox -> state(mailbox).lastActivity())).orElse(null);
            if (oldest == null) { return false; }
            retire(oldest);
        }
        return true;
    }

    /**
     * 锁内撤销空闲邮箱拥有关系，监听关闭延后至锁外执行。
     * @param mailbox 无在途请求的邮箱
     */
    private void retire(Mailbox mailbox) {
        if (mailboxes.remove(mailbox.key, mailbox)) { mailbox.retired = true; retired.add(mailbox); }
    }

    /**
     * 实例关闭时回收空闲状态；活跃邮箱等待各请求的关闭回执再移除。
     * @param mailbox 实例拥有的邮箱
     */
    private void retireClosed(Mailbox mailbox) {
        synchronized (lock) {
            mailbox.retired = true;
            if (mailbox.requests.isEmpty()) { retire(mailbox); }
        }
        drainRetired();
    }

    /** 只在邮箱锁外解除已淘汰邮箱的实例监听，迟到登记由邮箱自身回收。 */
    private void drainRetired() {
        Mailbox mailbox;
        while ((mailbox = retired.poll()) != null) { mailbox.dispose(); }
    }

    /**
     * 读取实体中唯一的不可变生命周期组件。
     * @param mailbox 数据拥有者
     * @return 当前会话状态
     */
    private static AgentConversationState state(Mailbox mailbox) {
        return mailbox.entity.snapshot().require(AgentConversationState.COMPONENT);
    }

    /**
     * 读取当前代次的不可变完成记录。
     * @param mailbox 会话拥有者
     * @return 当前完成记录
     */
    private static AgentRequestJournal journal(Mailbox mailbox) { return mailbox.entity.snapshot().require(AgentRequestJournal.COMPONENT); }

    /**
     * 在邮箱锁内按当前 revision 提交数据，不携带服务、控制或回调。
     * @param mailbox 实体拥有者
     * @param replacement 新状态
     */
    private static void replaceState(Mailbox mailbox, AgentConversationState replacement) {
        mailbox.entity.commit(mailbox.entity.snapshot().revision(), ComponentPatch.builder()
                .put(AgentConversationState.COMPONENT, replacement).build());
    }

    /**
     * 更新活动时刻，回拨时仍保留较晚读数。
     * @param mailbox 当前邮箱
     * @param now 锁外取得的活动时间
     */
    private static void touch(Mailbox mailbox, Instant now) {
        var current = state(mailbox);
        var updated = later(now, current.lastActivity());
        if (!updated.equals(current.lastActivity())) { replaceState(mailbox, new AgentConversationState(current.stamp(), updated)); }
    }

    /**
     * 比较可信时刻，避免回拨缩短已记录的活动时间。
     * @param first 当前观察时刻
     * @param second 已记录时刻
     * @return 两者中较晚的时刻
     */
    private static Instant later(Instant first, Instant second) { return first.isAfter(second) ? first : second; }

    /**
     * 只接受同 TeaNeko UUID 的请求级取消，不将共享 scope 解释为共享取消权限。
     * @param handle 目标实例
     * @param scope 来源客户端及作用域
     * @param requestId 原始请求标识
     * @param requester 宿主验证的用户 UUID
     * @return 取消请求接收结果
     * @throws NullPointerException 当实例、作用域、请求标识或发起者为空时
     */
    @Override public CancelResult cancel(AgentHandle handle, AgentScope scope, UUID requestId, UUID requester) {
        Objects.requireNonNull(handle, "Agent 实例不能为空。"); Objects.requireNonNull(requester, "取消发起者不能为空。");
        Objects.requireNonNull(requestId, "请求标识不能为空。");
        final Entry entry;
        synchronized (lock) {
            var mailbox = mailboxes.get(new AgentConversationKey(handle.id(), scope));
            entry = mailbox == null ? null : mailbox.requests.get(requestId);
            if (entry == null || entry.phase == Phase.FINISHED) { return CancelResult.NOT_FOUND; }
            if (entry.request.actor().userId().filter(requester::equals).isEmpty()) { return CancelResult.NOT_OWNER; }
        }
        return entry.lifetime.cancel() ? CancelResult.REQUESTED : CancelResult.NOT_FOUND;
    }

    /**
     * 为尚未启动业务的请求建立到期任务，选中后的执行器排队也计入等待时间。
     * @param entry 当前未启动请求
     */
    private void armWaiting(Entry entry) {
        if (!beforeStart(entry.phase)) { return; }
        try {
            var remaining = Duration.between(clock.instant(), entry.waitDeadline);
            long delay;
            try { delay = remaining.isNegative() ? 0 : remaining.toNanos(); }
            catch (ArithmeticException overflow) { delay = Long.MAX_VALUE; }
            var timer = Objects.requireNonNull(scheduler.schedule((Callable<Void>) () -> {
                stop(entry, AgentRunResult.Status.QUEUE_TIMED_OUT, true); return null;
            }, delay, TimeUnit.NANOSECONDS), "排队计时器未返回任务句柄。");
            entry.timer.set(timer);
            if (!beforeStart(entry.phase)) { cancelTimer(entry); }
        } catch (RuntimeException failure) { report(); stop(entry, AgentRunResult.Status.FAILED, true); }
    }

    /**
     * 每个邮箱使用工作计数驱动，直接执行器下连续完成大量请求也不递归增长调用栈。
     * @param entry 已经占有运行槽的请求
     */
    private void schedule(Entry entry) {
        var mailbox = Objects.requireNonNull(entry.mailbox);
        mailbox.ready.add(entry);
        if (entry.phase == Phase.FINISHED) { mailbox.ready.remove(entry); return; }
        if (mailbox.work.getAndIncrement() == 0) {
            try { executor.execute(() -> drain(mailbox)); }
            catch (RuntimeException failure) { report(); failDispatch(mailbox); }
        }
    }

    /**
     * 消费当前邮箱的启动信号，不等待任何模型或工具 Stage。
     * @param mailbox 当前邮箱拥有者
     */
    private void drain(Mailbox mailbox) {
        int missed = 1;
        for (;;) {
            Entry entry;
            while ((entry = mailbox.ready.poll()) != null) { dispatch(entry); }
            missed = mailbox.work.addAndGet(-missed);
            if (missed == 0) { return; }
        }
    }

    /**
     * 锁外准备冷句柄，先公布拥有关系和完成观察，再启动真实业务。
     * @param entry 已选中的请求
     */
    private void dispatch(Entry entry) {
        try {
            if (!clock.instant().isBefore(entry.waitDeadline)) {
                stop(entry, AgentRunResult.Status.QUEUE_TIMED_OUT, true); return;
            }
            synchronized (lock) {
                if (entry.phase != Phase.SELECTED) { return; }
                entry.phase = Phase.PREPARING;
            }
            var boundary = memory;
            if (boundary == null) { preparePersonality(entry, null); return; }
            boundary.await(entry.lifetime, entry.handle, entry.waitDeadline, () -> boundary.resolve(entry.handle, entry.request))
                    .thenAcceptAsync(reference -> {
                        entry.lifetime.checkActive();
                        if (!clock.instant().isBefore(entry.waitDeadline)) { stop(entry, AgentRunResult.Status.QUEUE_TIMED_OUT, true); return; }
                        if (!reference.scope().equals(entry.key.scope())) { throw new AgentMemoryReferenceException(AgentMemoryReferenceException.Reason.UNAVAILABLE); }
                        alignMemory(reference, entry);
                        preparePersonality(entry, reference);
                    }, executor).exceptionally(failure -> { stop(entry, AgentMemoryBoundary.status(failure), false); return null; });
        } catch (ExecutionStoppedException stopped) {
            stop(entry, stopStatus(stopped.reason()), false);
        } catch (RuntimeException failure) {
            report();
            if (entry.phase == Phase.RUNNING) { stop(entry, AgentRunResult.Status.FAILED, false); }
            else { finished(entry, null, failure); }
        }
    }

    /**
     * 在固定记忆引用之后、读取历史之前解析人格，未准入的实例不触发人格初始化。
     * @param entry 当前队首
     * @param reference 可选固定记忆引用
     */
    private void preparePersonality(Entry entry, @Nullable MemoryNamespaceReference reference) {
        var source = personalities;
        if (source == null) { startPrepared(entry, reference); return; }
        observations.awaitControlled(entry.lifetime, entry.handle, entry.waitDeadline, control -> turns.checkAccess(entry.handle, entry.request)
                .thenComposeAsync(decision -> {
                    control.checkActive();
                    if (!decision.allowed()) { throw new AgentPromptException(decision.reason() == AgentAccessDecision.Reason.READINESS_UNAVAILABLE
                            || decision.reason() == AgentAccessDecision.Reason.SCOPE_CONFIG_UNAVAILABLE ? AgentPromptException.Reason.UNAVAILABLE : AgentPromptException.Reason.DISABLED); }
                    return source.resolve(entry.key.scope(), control);
                }, executor)).thenAcceptAsync(snapshot -> {
                    entry.lifetime.checkActive(); entry.reservedPersonalityBytes = engine.personalityBytes(snapshot);
                    alignPersonality(entry, snapshot.identity()); startPrepared(entry, reference);
                }, executor).exceptionally(failure -> { stop(entry, AgentMemoryBoundary.status(failure), false); return null; });
    }

    /**
     * 仅在邮箱锁内协调纯数据，当前队首保留，旧等待项和窗口/缓存一起失效。
     * @param head 已固定新人格的队首
     * @param identity 实际人格身份
     */
    private void alignPersonality(Entry head, String identity) {
        var cancelled = new ArrayList<Entry>(); var now = clock.instant();
        synchronized (lock) {
            if (head.phase != Phase.PREPARING || head.stopped != null) { throw new AgentPromptException(AgentPromptException.Reason.CHANGED); }
            var box = Objects.requireNonNull(head.mailbox); var current = box.entity.snapshot();
            var previous = current.find(AgentComponents.PERSONALITY_IDENTITY).orElse(null);
            if (!Objects.equals(head.stamp, state(box).stamp())) { throw new AgentPromptException(AgentPromptException.Reason.CHANGED); }
            var patch = ComponentPatch.builder().put(AgentComponents.PERSONALITY_IDENTITY, identity);
            if (previous != null && !previous.equals(identity)) {
                var stamp = new AgentConversationStamp(box.key, UUID.randomUUID()); head.stamp = stamp;
                patch.put(AgentConversationState.COMPONENT, new AgentConversationState(stamp, later(now, state(box).lastActivity())))
                        .put(AgentConversationWindow.COMPONENT, AgentConversationWindow.empty()).remove(AgentSummarySnapshot.COMPONENT).put(AgentRequestJournal.COMPONENT, AgentRequestJournal.empty());
                for (var entry : List.copyOf(box.requests.values())) {
                    if (entry == head || entry.phase == Phase.FINISHED) { continue; }
                    entry.stopped = AgentRunResult.Status.STALE_CONVERSATION; detach(entry, now); cancelled.add(entry);
                }
            }
            box.entity.commit(current.revision(), patch.build()); head.personalityIdentity = identity;
        }
        cancelled.forEach(entry -> deliver(entry, result(entry, null, null), null));
    }

    /**
     * 引用协调完成后才取得当前窗口，继续保留原文凭据；冷句柄公布与实际启动保留原拥有关系。
     * @param entry 当前队首
     * @param reference 本轮固定引用，无记忆模式为空
     */
    private void startPrepared(Entry entry, @Nullable MemoryNamespaceReference reference) {
        try {
            AgentConversationWindow window;
            final AgentSummarySnapshot previousSummary;
            synchronized (lock) {
                if (entry.phase != Phase.PREPARING) { return; }
                var box = Objects.requireNonNull(entry.mailbox);
                if (!Objects.equals(entry.stamp, state(box).stamp())
                        || reference != null && box.entity.snapshot().find(AgentComponents.MEMORY_REFERENCE).filter(reference::equals).isEmpty()) {
                    throw new AgentMemoryReferenceException(AgentMemoryReferenceException.Reason.CHANGED);
                }
                entry.memoryReference = reference;
                window = box.entity.snapshot().require(AgentConversationWindow.COMPONENT);
                previousSummary = box.entity.snapshot().find(AgentSummarySnapshot.COMPONENT).orElse(null);
            }
            if (summaries != null && reference != null && entry.personalityIdentity != null) {
                try {
                    var selected = summaries.select(Objects.requireNonNull(entry.stamp), reference, entry.personalityIdentity, window, previousSummary);
                    synchronized (lock) {
                        var box = Objects.requireNonNull(entry.mailbox);
                        if (entry.phase != Phase.PREPARING || !Objects.equals(entry.stamp, state(box).stamp())
                                || box.entity.snapshot().require(AgentConversationWindow.COMPONENT) != window) { throw new AgentPromptException(AgentPromptException.Reason.CHANGED); }
                        var patch = ComponentPatch.builder().put(AgentConversationWindow.COMPONENT, selected.window());
                        if (selected.summary() == null) { patch.remove(AgentSummarySnapshot.COMPONENT); }
                        else { patch.put(AgentSummarySnapshot.COMPONENT, selected.summary()); }
                        box.entity.commit(box.entity.snapshot().revision(), patch.build());
                    }
                    window = selected.window(); entry.summary = selected.summary();
                } catch (RuntimeException ignored) { report(); entry.summary = null; }
            }
            try { entry.options = Objects.requireNonNull(policy.resolve(entry.handle, entry.request), "窗口策略不能为空。"); }
            catch (RuntimeException failure) { report(); stop(entry, AgentRunResult.Status.CONFIGURATION_ERROR, false); return; }
            entry.lifetime.checkActive();
            final AgentConversationEngine.Prepared prepared;
            try {
                var original = entry.request;
                var fixed = new AgentRunRequest(original.requestId(), original.actor(), original.activity(), original.messages(), original.conversation(),
                        original.memoryReference(), original.userText(), original.inputMaxBytes(), entry.personalityIdentity, entry.summary);
                prepared = engine.prepare(fixed, Objects.requireNonNull(entry.stamp), window, entry.options,
                        Math.addExact(entry.reservedPersonalityBytes, entry.summary == null ? 0 : engine.summaryBytes(entry.summary))); entry.prepared = prepared;
            }
            catch (AgentConversationEngine.InputTooLargeException failure) { stop(entry, AgentRunResult.Status.INPUT_TOO_LARGE, false); return; }
            var base = prepared.request();
            var request = reference == null ? base : new AgentRunRequest(base.requestId(), base.actor(), base.activity(), base.messages(), base.conversation(), reference, base.userText(), base.inputMaxBytes(), base.personalityIdentity(), base.summary());
            entry.startedAt = clock.instant();
            var execution = Objects.requireNonNull(reference != null ? turns.prepareReferenced(entry.handle, request, entry.independent ? null : entry.lifetime)
                    : entry.independent ? turns.prepare(entry.handle, request) : turns.prepare(entry.handle, request, entry.lifetime), "独立轮次端口返回的句柄不能为空。");
            if (!clock.instant().isBefore(entry.waitDeadline)) { stop(entry, AgentRunResult.Status.QUEUE_TIMED_OUT, true); }
            final boolean accepted;
            synchronized (lock) {
                accepted = entry.phase == Phase.PREPARING;
                if (accepted) { entry.execution = execution; entry.phase = Phase.RUNNING; }
            }
            if (!accepted) { execution.cancel(); return; }
            cancelTimer(entry);
            execution.result().whenComplete((result, failure) -> finished(entry, result, failure));
            execution.start();
        } catch (ExecutionStoppedException stopped) {
            stop(entry, stopStatus(stopped.reason()), false);
        } catch (RuntimeException failure) {
            report();
            if (memory != null) { stop(entry, AgentMemoryBoundary.status(failure), false); }
            else if (entry.phase == Phase.RUNNING) { stop(entry, AgentRunResult.Status.FAILED, false); }
            else { finished(entry, null, failure); }
        }
    }

    /**
     * 记录首个终止原因；运行中的请求等待底层结束回执后才释放运行槽，取消抛错不能替代结束确认。
     * @param entry 待终止请求
     * @param status 明确原因
     * @param waitingOnly 是否只允许结束尚未启动业务的请求
     */
    private void stop(Entry entry, AgentRunResult.Status status, boolean waitingOnly) {
        var now = activityTime(entry);
        final AgentRunExecution running;
        final Entry next;
        synchronized (lock) {
            if (entry.phase == Phase.FINISHED || waitingOnly && !beforeStart(entry.phase)) { return; }
            if (entry.stopped == null) { entry.stopped = status; }
            running = entry.phase == Phase.RUNNING ? entry.execution : null;
            next = running == null ? detach(entry, now) : null;
        }
        drainRetired();
        if (running != null) {
            if (entry.cancellationRequested.compareAndSet(false, true)) {
                if (memory != null) { entry.lifetime.cancel(); }
                try { running.cancel(); } catch (RuntimeException failure) { report(); }
            }
        } else { deliver(entry, result(entry, null, null), next); }
    }

    /**
     * 接收实际轮次结束信号，保留诊断及用量，终止后的成功不成为可交付回答。
     * @param entry 当前运行请求
     * @param raw 可选实际结果
     * @param failure 可选端口失败
     */
    private void finished(Entry entry, @Nullable AgentRunResult raw, @Nullable Throwable failure) {
        var boundary = memory;
        var expected = entry.memoryReference;
        if (boundary == null || raw == null || failure != null || raw.status() != AgentRunResult.Status.SUCCESS || entry.stopped != null) {
            commitFinished(entry, raw, failure); return;
        }
        if (expected == null || !expected.equals(raw.memoryReference()) || !Objects.equals(entry.stamp, raw.conversation())) {
            commitFinished(entry, restate(raw, AgentRunResult.Status.CONFIGURATION_ERROR), null); return;
        }
        try {
            var timeout = raw.access().flatMap(AgentAccessDecision::runtime).orElseThrow().interaction().turnTimeout();
            var deadline = Objects.requireNonNull(entry.startedAt).plus(timeout);
            boundary.await(entry.lifetime, entry.handle, deadline, () -> boundary.verify(entry.handle, entry.request, expected))
                    .whenComplete((ignored, rejected) -> commitFinished(entry,
                            rejected == null ? raw : restate(raw, AgentMemoryBoundary.status(rejected)), null));
        } catch (RuntimeException rejected) { commitFinished(entry, restate(raw, AgentMemoryBoundary.status(rejected)), null); }
    }

    /**
     * 在引用复核完成后仲裁 epoch 与窗口提交，迟到的旧结果仍保留原诊断。
     * @param entry 请求拥有者
     * @param raw 实际结果或安全改写的失败结果
     * @param failure 可选端口失败
     */
    private void commitFinished(Entry entry, @Nullable AgentRunResult raw, @Nullable Throwable failure) {
        var outcome = result(entry, raw, failure);
        AgentConversationWindow updated = null;
        AgentConversationWindow summaryCandidate = null;
        var prepared = entry.prepared;
        if (prepared != null) {
            try {
                var turn = engine.completedTurn(prepared, outcome);
                if (turn != null) {
                    updated = prepared.window().append(turn, prepared.options());
                    if (summaries != null) {
                        var all = new ArrayList<>(prepared.window().turns()); all.add(turn);
                        summaryCandidate = new AgentConversationWindow(all, prepared.window().discardedTurns(), false);
                    }
                }
            }
            catch (RuntimeException invalid) { outcome = restate(outcome, AgentRunResult.Status.INVALID_RESPONSE); }
        }
        var now = activityTime(entry);
        final Entry next;
        final AgentRunResult accepted;
        synchronized (lock) {
            if (entry.phase == Phase.FINISHED) { return; }
            if (entry.mailbox != null && !Objects.equals(entry.stamp, state(entry.mailbox).stamp()) && entry.stopped == null) {
                entry.stopped = AgentRunResult.Status.STALE_CONVERSATION;
            }
            if (entry.stopped == null && (closed || entry.handle.isClosed() || entry.mailbox != null && entry.mailbox.retired)) {
                entry.stopped = AgentRunResult.Status.CANCELLED;
            }
            accepted = result(entry, outcome, null);
            if (accepted.status() == AgentRunResult.Status.SUCCESS && updated != null && entry.mailbox != null) {
                var mailbox = entry.mailbox;
                mailbox.entity.commit(mailbox.entity.snapshot().revision(), ComponentPatch.builder()
                        .put(AgentConversationWindow.COMPONENT, updated).build());
            }
            next = detach(entry, now);
        }
        drainRetired();
        if (summaries != null && accepted.status() == AgentRunResult.Status.SUCCESS && summaryCandidate != null) {
            try { summaries.offer(entry.handle, accepted, summaryCandidate, entry.summary); } catch (RuntimeException ignored) { report(); }
        }
        deliver(entry, accepted, next);
    }

    /**
     * 在锁外取得完成时刻；时钟故障不能阻止请求结束或后继推进。
     * @param entry 当前请求
     * @return 当前时刻，读取失败时使用接收时刻
     */
    private Instant activityTime(Entry entry) {
        try { return clock.instant(); }
        catch (RuntimeException ignored) { report(); return entry.acceptedAt; }
    }

    /**
     * 仅在邮箱锁内移除拥有关系并选下一项，不调用任何端口或完成 Future。
     * @param entry 已结束或尚未开始的请求
     * @param now 锁外取得的完成时刻
     * @return 下一项，空表示暂不启动其他请求
     */
    private @Nullable Entry detach(Entry entry, Instant now) {
        entry.phase = Phase.FINISHED;
        var mailbox = entry.mailbox;
        if (mailbox == null) { return null; }
        mailbox.ready.remove(entry);
        mailbox.waiting.remove(entry);
        touch(mailbox, now);
        Entry next = null;
        if (mailbox.active == entry) {
            mailbox.active = null;
            if (!closed) {
                next = mailbox.waiting.pollFirst();
                if (next != null) { next.phase = Phase.SELECTED; mailbox.active = next; }
            }
        }
        return next;
    }

    /**
     * 在锁外回收当前请求、交付结果，再推进已经预留运行槽的后继。
     * @param entry 已结束请求
     * @param result 稳定结果
     * @param next 可选后继
     */
    private void deliver(Entry entry, AgentRunResult result, @Nullable Entry next) {
        var now = activityTime(entry);
        var bytes = engine.resultBytes(result);
        synchronized (lock) {
            var mailbox = entry.mailbox;
            if (mailbox != null && mailbox.requests.get(entry.request.requestId()) == entry) {
                if (!closed && !mailbox.retired && Objects.equals(entry.stamp, state(mailbox).stamp())) {
                    mailbox.entity.commit(mailbox.entity.snapshot().revision(), ComponentPatch.builder()
                            .put(AgentRequestJournal.COMPONENT, journal(mailbox).append(entry.request.requestId(), entry.identity,
                                    result, bytes, now, entry.options)).build());
                }
                mailbox.requests.remove(entry.request.requestId(), entry);
                if (mailbox.requests.isEmpty() && (closed || mailbox.retired)) { retire(mailbox); }
            }
        }
        drainRetired();
        dispose(entry);
        entry.result.complete(result);
        if (next != null) { schedule(next); }
    }

    /**
     * 调度器拒绝启动时批量结束尚未启动的请求，避免直接执行器下递归失败。
     * @param mailbox 被拒绝启动的邮箱
     */
    private void failDispatch(Mailbox mailbox) {
        final List<Entry> rejected;
        synchronized (lock) {
            mailbox.retired = true;
            rejected = mailbox.requests.values().stream().filter(entry -> entry.phase != Phase.RUNNING && entry.phase != Phase.FINISHED).toList();
            for (var entry : rejected) {
                entry.stopped = AgentRunResult.Status.FAILED; entry.phase = Phase.FINISHED;
                mailbox.waiting.remove(entry);
                if (mailbox.active == entry) { mailbox.active = null; }
            }
            if (mailbox.requests.isEmpty()) { retire(mailbox); }
        }
        drainRetired();
        mailbox.ready.clear(); mailbox.work.set(0);
        rejected.forEach(entry -> deliver(entry, result(entry, null, null), null));
    }

    /**
     * 形成当前请求结果，不接受其他请求或实例的结果，也不复制原始异常文本。
     * @param entry 原始请求拥有者
     * @param raw 实际轮次结果
     * @param failure 可选失败
     * @return 只属于当前请求的稳定结果
     */
    private AgentRunResult result(Entry entry, @Nullable AgentRunResult raw, @Nullable Throwable failure) {
        var memoryReference = raw == null ? null : raw.memoryReference();
        if (raw != null && (!raw.agentId().equals(entry.handle.id()) || !raw.requestId().equals(entry.request.requestId())
                || raw.conversation() != null && !Objects.equals(raw.conversation(), entry.stamp)
                || memoryReference != null && !memoryReference.scope().equals(entry.key.scope())
                || memory != null && raw.status() == AgentRunResult.Status.SUCCESS && !Objects.equals(memoryReference, entry.memoryReference))) {
            raw = null; failure = new IllegalStateException("轮次结果身份与当前请求不一致。");
        }
        var status = entry.stopped != null ? entry.stopped : failure != null ? failureStatus(failure)
                : raw == null ? AgentRunResult.Status.FAILED : raw.status();
        return new AgentRunResult(entry.handle.id(), entry.request.requestId(), status,
                raw == null ? Optional.empty() : raw.access(), raw == null ? Optional.empty() : raw.interaction(),
                raw == null ? entry.lifetime.budget().snapshot() : raw.budgetUsage(), entry.stamp,
                raw == null ? entry.memoryReference : raw.memoryReference(), raw == null ? null : raw.promptTranscript());
    }

    /**
     * 回收本请求监听和排队计时，不关闭父请求、实际端口或共享执行器。
     * @param entry 已结束请求
     */
    private void dispose(Entry entry) {
        if (entry.disposed.compareAndSet(false, true)) {
            cancelTimer(entry);
            ExecutionControl.TerminationRegistration link;
            while ((link = entry.links.poll()) != null) { link.close(); }
            entry.lifetime.close();
        }
    }

    /**
     * 隔离计时句柄关闭故障，不能因此遗漏结果完成或后继推进。
     * @param entry 计时资源拥有者
     */
    private void cancelTimer(Entry entry) {
        var timer = entry.timer.getAndSet(null);
        if (timer != null) { try { timer.cancel(false); } catch (RuntimeException ignored) { report(); } }
    }

    /**
     * 创建未入队的明确拒绝结果，不创建任何轮次或排队资源。
     * @param handle 目标实例
     * @param request 固定请求
     * @param status 终止状态
     * @param parent 可选父预算
     * @return 只读完成结果
     */
    private static CompletionStage<AgentRunResult> immediate(AgentHandle handle, AgentRunRequest request,
            AgentRunResult.Status status, @Nullable ExecutionControl parent) {
        return CompletableFuture.completedStage(rejectedResult(handle, request, status, parent));
    }

    /**
     * 形成没有执行动作的拒绝结果，不将其他请求的预算计入本次观察。
     * @param handle 目标实例
     * @param request 原始请求
     * @param status 拒绝原因
     * @param parent 可选借用预算
     * @return 当前请求的无动作结果
     */
    private static AgentRunResult rejectedResult(AgentHandle handle, AgentRunRequest request, AgentRunResult.Status status,
            @Nullable ExecutionControl parent) {
        return new AgentRunResult(handle.id(), request.requestId(), status, null, null,
                parent == null ? Map.of() : parent.budget().snapshot());
    }

    /**
     * 区分父截止时间与普通取消。
     * @param reason 首次停止原因
     * @return 超时或取消状态
     */
    private static AgentRunResult.Status stopStatus(ExecutionControl.StopReason reason) {
        return reason == ExecutionControl.StopReason.DEADLINE_EXCEEDED ? AgentRunResult.Status.TIMED_OUT : AgentRunResult.Status.CANCELLED;
    }

    /**
     * 区分尚未启动业务的阶段，排队超时不能作用于已经运行的轮次。
     * @param phase 当前状态
     * @return 仍可因排队时限结束时为 true
     */
    private static boolean beforeStart(Phase phase) {
        return phase != Phase.RUNNING && phase != Phase.FINISHED;
    }

    /**
     * 分类端口失败，不根据异常文本猜测业务状态。
     * @param failure 完成异常
     * @return 明确终止状态
     */
    private static AgentRunResult.Status failureStatus(Throwable failure) {
        while ((failure instanceof CompletionException || failure instanceof ExecutionException) && failure.getCause() != null) {
            failure = failure.getCause();
        }
        return failure instanceof ExecutionStoppedException stopped ? stopStatus(stopped.reason()) : AgentRunResult.Status.FAILED;
    }

    /** 固定诊断不携带请求或异常值，日志故障也不能阻止结果完成。 */
    private void report() {
        try { logger.error(AgentConversationService.class.getName(), "Agent 邮箱配置或调度失败，请检查本地策略和执行资源。"); }
        catch (RuntimeException ignored) { /* 诊断不参与执行准入。 */ }
    }

    /** 关闭本服务拥有的运行与等待项，锁外取消，不关闭实例、独立轮次端口或借用执行器。 */
    @Override public void close() {
        final List<Entry> active;
        final List<Mailbox> owned;
        synchronized (lock) {
            if (closed) { return; }
            closed = true;
            active = mailboxes.values().stream().flatMap(mailbox -> mailbox.requests.values().stream()).toList();
            owned = List.copyOf(mailboxes.values());
            for (var mailbox : owned) {
                mailbox.retired = true;
                if (mailbox.requests.isEmpty()) { retire(mailbox); }
            }
        }
        drainRetired();
        observations.close();
        owned.forEach(Mailbox::dispose);
        active.forEach(entry -> entry.lifetime.cancel());
    }

    /**
     * 单请求状态；FINISHED 已完成业务仲裁，交付结束前保留请求索引以阻止重复执行。
     * @author zExNocs
     * @date 2026/10/29
     * @since 4.0.0
     * @version 4.5.0-l
     */
    private enum Phase { NEW, WAITING, SELECTED, PREPARING, RUNNING, FINISHED }

    /**
     * 邮箱运行拥有者；实体只保留不可变数据，控制和监听不进入组件。
     * @author zExNocs
     * @date 2026/10/29
     * @since 4.0.0
     * @version 4.5.0-l
     */
    private static final class Mailbox {
        final AgentConversationKey key;
        final Entity entity;
        AgentConversationSettings retention;
        boolean retired;
        final AtomicReference<ExecutionControl.TerminationRegistration> closeLink = new AtomicReference<>();
        final AtomicBoolean disposed = new AtomicBoolean();
        final Map<UUID, Entry> requests = new HashMap<>();
        final Deque<Entry> waiting = new ArrayDeque<>();
        final ConcurrentLinkedQueue<Entry> ready = new ConcurrentLinkedQueue<>();
        final AtomicInteger work = new AtomicInteger();
        @Nullable Entry active;

        /**
         * 固定邮箱身份，业务执行仍由外部端口承担。
         * @param key 共享会话键
         * @param retention 本次保留策略
         * @param now 创建时刻
         */
        Mailbox(AgentConversationKey key, AgentConversationSettings retention, Instant now) {
            this.key = key; this.retention = retention;
            entity = new Entity(EntityId.random(), ComponentPatch.builder().put(AgentConversationState.COMPONENT,
                    new AgentConversationState(new AgentConversationStamp(key, UUID.randomUUID()), now))
                    .put(AgentConversationWindow.COMPONENT, AgentConversationWindow.empty()).remove(AgentSummarySnapshot.COMPONENT)
                    .put(AgentRequestJournal.COMPONENT, AgentRequestJournal.empty()).build());
        }

        /**
         * 锁外接管实例关闭监听，淘汰先发生时立即解除迟到登记。
         * @param link 当前邮箱唯一的实例监听
         */
        void own(ExecutionControl.TerminationRegistration link) {
            closeLink.set(link);
            if (disposed.get()) {
                var late = closeLink.getAndSet(null);
                if (late != null) { late.close(); }
            }
        }

        /** 锁外解除自己拥有的实例监听，不关闭实例或借用执行器。 */
        void dispose() {
            disposed.set(true);
            var link = closeLink.getAndSet(null);
            if (link != null) { link.close(); }
        }
    }

    /**
     * 单个请求的受控拥有者，不存入实体组件；状态变更由邮箱锁仲裁。
     * @author zExNocs
     * @date 2026/10/29
     * @since 4.0.0
     * @version 4.5.0-l
     */
    private static final class Entry {
        final AgentHandle handle;
        final AgentRunRequest request;
        final AgentRequestJournal.Identity identity;
        final AgentConversationKey key;
        final ExecutionControl lifetime;
        final boolean independent;
        final Instant acceptedAt;
        final Instant waitDeadline;
        final CompletableFuture<AgentRunResult> result = new CompletableFuture<>();
        final AtomicReference<ScheduledFuture<?>> timer = new AtomicReference<>();
        final ConcurrentLinkedQueue<ExecutionControl.TerminationRegistration> links = new ConcurrentLinkedQueue<>();
        final AtomicBoolean disposed = new AtomicBoolean();
        final AtomicBoolean cancellationRequested = new AtomicBoolean();
        volatile Phase phase = Phase.NEW;
        volatile AgentRunResult.@Nullable Status stopped;
        @Nullable Mailbox mailbox;
        @Nullable AgentRunExecution execution;
        @Nullable AgentConversationStamp stamp;
        volatile AgentConversationOptions options = AgentConversationOptions.defaults();
        volatile AgentConversationEngine.@Nullable Prepared prepared;
        volatile @Nullable String personalityIdentity;
        volatile @Nullable AgentSummarySnapshot summary;
        volatile long reservedPersonalityBytes;
        volatile @Nullable MemoryNamespaceReference memoryReference;
        volatile @Nullable Instant startedAt;

        /**
         * 固定请求和等待截止时间，不预占模型或工具额度。
         * @param handle 实例拥有者
         * @param request 可信请求
         * @param settings 本次固定策略
         * @param acceptedAt 接收时刻
         * @param lifetime 本请求拥有的控制
         * @param independent 是否建立独立模型预算
         * @param identity 已在锁外计算的原始请求身份
         */
        Entry(AgentHandle handle, AgentRunRequest request, AgentQueueSettings settings, Instant acceptedAt,
                ExecutionControl lifetime, boolean independent, AgentRequestJournal.Identity identity) {
            this.handle = handle; this.request = request; this.lifetime = lifetime; this.independent = independent;
            this.acceptedAt = acceptedAt;
            this.identity = identity;
            key = new AgentConversationKey(handle.id(), request.actor().scope());
            Instant deadline;
            try { deadline = acceptedAt.plus(settings.waitTimeout()); }
            catch (DateTimeException | ArithmeticException overflow) { deadline = Instant.MAX; }
            waitDeadline = deadline;
        }

        /**
         * 接管监听，资源回收与迟到登记竞争时仍关闭该登记。
         * @param link 可解除监听
         */
        void own(ExecutionControl.TerminationRegistration link) {
            links.add(link);
            if (disposed.get() && links.remove(link)) { link.close(); }
        }
    }
}
