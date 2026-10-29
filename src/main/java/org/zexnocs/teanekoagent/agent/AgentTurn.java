package org.zexnocs.teanekoagent.agent;

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.agent.access.AgentAccessDecision;
import org.zexnocs.teanekoagent.agent.access.AgentAccessGate;
import org.zexnocs.teanekoagent.agent.memory.AgentMemoryReferenceException;
import org.zexnocs.teanekoagent.agent.memory.IAgentMemoryReferences;
import org.zexnocs.teanekoagent.agent.prompt.AgentPromptException;
import org.zexnocs.teanekoagent.agent.prompt.AgentPromptTranscript;
import org.zexnocs.teanekoagent.llm.framework.LLMOperationRejectedException;
import org.zexnocs.teanekoagent.llm.framework.interaction.LLMInteractionResult;
import org.zexnocs.teanekoagent.llm.framework.model.LLMConfigurationException;
import org.zexnocs.teanekoagent.memory.namespace.MemoryNamespaceReference;
import org.zexnocs.teanekocore.actuator.execution.BudgetExceededException;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.actuator.execution.ExecutionStoppedException;
import org.zexnocs.teanekocore.framework.composition.ComponentPatch;
import org.zexnocs.teanekocore.framework.composition.Entity;
import org.zexnocs.teanekocore.framework.composition.EntityId;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 单轮的资源拥有者和诊断观察点，服务、控制和监听均不进入组件。
 * 组件写入由准备阶段或有序管线完成；取消后的诊断只观察，不提交迟到数据。
 *
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
final class AgentTurn implements AutoCloseable {
    final AgentHandle handle;
    final AgentRunRequest request;
    final Entity entity;
    final ExecutionControl lifetime;
    private final boolean independent;
    private final Instant acceptedAt;
    private final ConcurrentLinkedQueue<ExecutionControl.TerminationRegistration> links = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicReference<AgentAccessDecision> rejected = new AtomicReference<>();
    private final AtomicBoolean interactionStarted = new AtomicBoolean();
    private final CompletableFuture<Void> interactionAttached = new CompletableFuture<>();
    private volatile @Nullable ExecutionControl execution;
    private volatile @Nullable AgentAccessDecision access;
    private volatile @Nullable LLMInteractionResult observed;
    private volatile @Nullable MemoryNamespaceReference memoryReference;
    private final AtomicReference<AgentRunResult.Status> contextRejected = new AtomicReference<>();
    private volatile @Nullable AgentPromptTranscript promptTranscript;

    /**
     * 接管当前轮的轻量生命周期，不读取配置或执行工具。
     * @param handle 实例拥有者
     * @param request 已冻结请求
     * @param lifetime 本轮拥有的生命周期控制
     * @param independent 是否需要根据就绪设置新建父预算
     * @param acceptedAt 独立请求被接收的时刻，准备排队也计入实际总时限
     */
    AgentTurn(AgentHandle handle, AgentRunRequest request, ExecutionControl lifetime, boolean independent, Instant acceptedAt) {
        this.handle = handle;
        this.request = request;
        this.lifetime = lifetime;
        this.independent = independent;
        this.acceptedAt = acceptedAt;
        var initial = ComponentPatch.builder()
                .put(AgentComponents.INSTANCE, handle.id()).put(AgentComponents.DEFINITION, handle.definition())
                .put(AgentComponents.REQUEST, request).put(AgentComponents.ACTOR, request.actor())
                .put(AgentComponents.ACTIVITY, request.activity());
        if (request.conversation() != null) {
            initial.put(AgentComponents.CONVERSATION, request.conversation());
        }
        entity = new Entity(EntityId.random(), initial.build());
    }

    /**
     * 登记自己拥有的监听；完成与登记竞争时仍释放迟到的登记。
     * @param link 可解除的资源监听
     */
    void own(ExecutionControl.TerminationRegistration link) {
        links.add(link);
        if (closed.get() && links.remove(link)) { link.close(); }
    }

    /**
     * 固定准入数据并建立真实总预算及截止时间，派生轮次继续共享生命周期父预算。
     * @param decision 已完成的准入检查
     * @param scheduler 借用计时器
     * @param clock 独立请求的可信时钟
     * @return 是否成功准备并允许进入管线
     * @throws ExecutionStoppedException 当轮次已经取消或总截止时间耗尽时
     * @throws RuntimeException 当计时器无法建立实际控制时
     */
    boolean prepare(AgentAccessDecision decision, ScheduledExecutorService scheduler, Clock clock) {
        lifetime.checkActive();
        access = decision;
        if (!decision.allowed()) { rejected.compareAndSet(null, decision); return false; }
        var runtime = decision.runtime().orElseThrow();
        final ExecutionControl current;
        if (independent) {
            Instant deadline;
            try { deadline = acceptedAt.plus(runtime.interaction().turnTimeout()); }
            catch (java.time.DateTimeException | ArithmeticException overflow) { deadline = Instant.MAX; }
            current = ExecutionControl.withDeadline(runtime.interaction().limits().newBudget(), deadline, clock, scheduler);
            execution = current;
            own(lifetime.onTermination(ignored -> current.cancel()));
        } else {
            current = lifetime.child(runtime.interaction().turnTimeout(), scheduler);
            execution = current;
        }
        if (closed.get()) { current.close(); }
        current.checkActive();
        lifetime.whileActive(() -> entity.commit(entity.snapshot().revision(), ComponentPatch.builder()
                .put(AgentComponents.RUNTIME, runtime)
                .put(AgentComponents.SCOPE_POLICY, decision.scope().orElseThrow()).build()));
        return true;
    }

    /**
     * 在实际控制建立后固定空间引用；只借用本轮控制，异步完成和关闭仍由外层拥有者负责。
     * @param references 空间端口；显式无持久化记忆的轮次为空
     * @return 引用已固定或原始终止原因；失效不刷新成另一个空间
     */
    @SuppressWarnings("resource")
    CompletionStage<Void> resolveMemory(@Nullable IAgentMemoryReferences references) {
        if (references == null) { return CompletableFuture.completedStage(null); }
        try {
            var expected = request.memoryReference();
            CompletionStage<MemoryNamespaceReference> source = expected == null ? references.resolve(request.actor().scope())
                    : references.verify(expected).thenApply(ignored -> expected);
            return control().guard(Objects.requireNonNull(source, "空间引用解析阶段不能为空。"))
                    .thenAccept(value -> {
                        if (value == null || !value.scope().equals(request.actor().scope())) {
                            throw new AgentMemoryReferenceException(AgentMemoryReferenceException.Reason.UNAVAILABLE);
                        }
                        lifetime.whileActive(() -> {
                            entity.commit(entity.snapshot().revision(), ComponentPatch.builder().put(AgentComponents.MEMORY_REFERENCE, value).build());
                            memoryReference = value;
                            return null;
                        });
                    }).exceptionallyCompose(this::rejectContext);
        } catch (RuntimeException failure) { return rejectContext(failure); }
    }

    /**
     * 异步复核已固定引用，不在生命周期锁内访问存储，不关闭借用的本轮控制。
     * @param references 空间端口，显式无持久化记忆的轮次为空
     * @return 一致时完成，失效通过交互专用拒绝终止后继动作
     */
    @SuppressWarnings("resource")
    CompletionStage<Void> verifyMemory(@Nullable IAgentMemoryReferences references) {
        if (references == null) { return CompletableFuture.completedStage(null); }
        var fixed = memoryReference;
        if (fixed == null) { return rejectContext(new AgentMemoryReferenceException(AgentMemoryReferenceException.Reason.UNAVAILABLE)); }
        try {
            return control().guard(Objects.requireNonNull(references.verify(fixed), "空间引用复核阶段不能为空。"))
                    .exceptionallyCompose(this::rejectContext);
        } catch (RuntimeException failure) { return rejectContext(failure); }
    }

    /**
     * 保存首次引用或资料拒绝，保留取消/超时类别，不把来源故障当成可恢复的普通工具文本。
     * @param failure 原始阶段失败，仅用于类型分类
     * @param <T> 被拒绝阶段的结果类型
     * @return 原始停止或交互专用拒绝的异常阶段
     */
    <T> CompletionStage<T> rejectContext(Throwable failure) {
        for (int depth = 0; depth < 32 && (failure instanceof CompletionException || failure instanceof ExecutionException); depth++) {
            var cause = failure.getCause(); if (cause == null || cause == failure) { break; } failure = cause;
        }
        if (failure instanceof ExecutionStoppedException) { return CompletableFuture.failedStage(failure); }
        var status = failure instanceof AgentMemoryReferenceException memory ? switch (memory.reason()) {
            case CHANGED -> AgentRunResult.Status.STALE_CONVERSATION;
            case DISABLED -> AgentRunResult.Status.SKIPPED;
            case UNAVAILABLE -> AgentRunResult.Status.CONFIGURATION_ERROR;
        } : failure instanceof AgentPromptException prompt ? switch (prompt.reason()) {
            case DISABLED -> AgentRunResult.Status.SKIPPED;
            case CHANGED -> AgentRunResult.Status.STALE_CONVERSATION;
            case UNAVAILABLE -> AgentRunResult.Status.CONFIGURATION_ERROR;
            case INPUT_TOO_LARGE -> AgentRunResult.Status.INPUT_TOO_LARGE;
        } : AgentRunResult.Status.CONFIGURATION_ERROR;
        contextRejected.compareAndSet(null, status);
        return CompletableFuture.failedStage(new LLMOperationRejectedException());
    }

    /**
     * 获取已建立的实际执行控制，不允许在准备前执行模型或工具。
     * @return 当前轮拥有的实际控制
     * @throws IllegalStateException 当尚未完成运行准备时
     */
    ExecutionControl control() {
        var current = execution;
        if (current == null) { throw new IllegalStateException("Agent 轮次尚未建立执行控制。"); }
        return current;
    }

    /**
     * 检查显式控制是否源自本轮，独立预算根和生命周期链均保持可识别。
     * @param candidate 待检查的派生控制
     * @return 当前轮次拥有该控制链时为 true
     */
    boolean contains(ExecutionControl candidate) {
        var current = execution;
        return candidate.isWithin(lifetime.id()) || current != null && candidate.isWithin(current.id());
    }

    /**
     * 复核固定轮次的当前门控；策略变化不刷新本轮绑定，而是拒绝继续。
     * 这里只检查本轮拥有的控制，关闭由整轮完成链统一执行。
     * @param gate 统一准入入口
     * @param activity 当前检查的活动
     * @return 准入仍成立时为 true
     * @throws ExecutionStoppedException 当轮次已经停止时
     */
    @SuppressWarnings("resource")
    boolean check(AgentAccessGate gate, AgentActivity activity) {
        lifetime.checkActive();
        control().checkActive();
        if (rejected.get() != null) { return false; }
        var decision = gate.evaluate(handle, request.actor(), activity);
        if (decision.allowed() && !decision.scope().orElseThrow().equals(entity.snapshot().require(AgentComponents.SCOPE_POLICY))) {
            decision = new AgentAccessDecision(AgentAccessDecision.Reason.SCOPE_POLICY_CHANGED, decision.scope(), decision.readiness());
        }
        access = decision;
        if (!decision.allowed()) { rejected.compareAndSet(null, decision); return false; }
        return true;
    }

    /**
     * 在模型或工具实际入口执行检查，拒绝通过专用异常停止交互而不冒充普通工具失败。
     * @param gate 统一门控
     * @param tool 是否还需检查定义的工具能力
     * @throws LLMOperationRejectedException 当当前门控已失效时
     * @throws ExecutionStoppedException 当轮次已经停止时
     */
    void guard(AgentAccessGate gate, boolean tool) {
        if (!check(gate, request.activity()) || tool && !check(gate, AgentActivity.TOOL)) {
            throw new LLMOperationRejectedException();
        }
    }

    /** 标记正在调用交互端口，供同步取消时等待诊断观察器完成登记。 */
    void enteringInteraction() { interactionStarted.set(true); }

    /** 完成交互观察器登记；不等待远端响应或工具业务。 */
    void attachedInteraction() { interactionAttached.complete(null); }

    /**
     * 保留已观察完成的只读交互，不允许迟到结果在交付后修改本轮记录。
     * @param result 完整交互诊断
     * @param projection 可选已验证历史投影，必须绑定原交互
     */
    void observe(LLMInteractionResult result, @Nullable AgentPromptTranscript projection) {
        if (!closed.get()) { observed = Objects.requireNonNull(result, "交互结果不能为空。"); promptTranscript = projection; }
    }

    /**
     * 等待终止通知释放资源并完成同步诊断，再形成结果；不等待不响应取消的远端 Stage。
     * @return 终止回调和观察器登记完成的阶段
     */
    CompletionStage<Void> settled() {
        CompletionStage<Void> result = stopped(lifetime) == null ? CompletableFuture.completedStage(null)
                : lifetime.termination().thenApply(ignored -> null);
        return result.thenCompose(ignored -> {
            var current = execution;
            return current == null || stopped(current) == null ? CompletableFuture.completedStage(null)
                    : current.termination().thenApply(reason -> null);
        }).thenCompose(ignored -> interactionStarted.get() ? interactionAttached.minimalCompletionStage()
                : CompletableFuture.completedStage(null));
    }

    /**
     * 形成最终只读结果；拒绝优先于模型候选，取消/超时优先于成功。
     * 固定可空字段的本地引用；交互快照在终止检查后读取，保留同步终止通知中观察到的诊断。
     * @param failure 管线失败；正常结束时为空
     * @return 本轮结果及交付时共享预算读数
     */
    AgentRunResult result(@Nullable Throwable failure) {
        var denied = rejected.get();
        var current = execution;
        var reason = denied == null ? stopped(lifetime) : null;
        if (denied == null && reason == null && current != null) { reason = stopped(current); }
        var interaction = observed;
        AgentRunResult.Status status;
        if (denied != null) {
            status = switch (denied.reason()) {
                case INSTANCE_CLOSED -> AgentRunResult.Status.CANCELLED;
                case READINESS_UNAVAILABLE, SCOPE_CONFIG_UNAVAILABLE -> AgentRunResult.Status.CONFIGURATION_ERROR;
                default -> AgentRunResult.Status.SKIPPED;
            };
        } else {
            var outcome = entity.snapshot().find(AgentComponents.OUTCOME).orElse(null);
            var memoryFailure = contextRejected.get();
            status = reason != null ? stopStatus(reason) : memoryFailure != null ? memoryFailure : failure != null ? failureStatus(failure)
                    : outcome != null ? outcome.status() : interaction == null ? AgentRunResult.Status.FAILED
                    : AgentRunResult.Status.valueOf(interaction.status().name());
        }
        var budget = current == null ? lifetime.budget() : current.budget();
        var projection = promptTranscript;
        return new AgentRunResult(handle.id(), request.requestId(), status, Optional.ofNullable(denied == null ? access : denied),
                Optional.ofNullable(interaction), budget.snapshot(), request.conversation(), memoryReference,
                projection != null && projection.interaction() == interaction ? projection : null);
    }

    /**
     * 检查首次停止原因，同时触发已经到期的截止时间。
     * @param control 待检查控制
     * @return 停止原因；仍有效时为空
     */
    private static ExecutionControl.@Nullable StopReason stopped(ExecutionControl control) {
        try { control.checkActive(); return null; }
        catch (ExecutionStoppedException stopped) { return stopped.reason(); }
    }

    /**
     * 将控制终止映射为业务状态。
     * @param reason 已生效的停止原因
     * @return 超时或取消状态
     */
    static AgentRunResult.Status stopStatus(ExecutionControl.StopReason reason) {
        return reason == ExecutionControl.StopReason.DEADLINE_EXCEEDED
                ? AgentRunResult.Status.TIMED_OUT : AgentRunResult.Status.CANCELLED;
    }

    /**
     * 分类异常，不将异常消息、Cause 或配置值写入结果。
     * @param failure 观察到的完成失败
     * @return 稳定业务状态
     */
    static AgentRunResult.Status failureStatus(Throwable failure) {
        while ((failure instanceof CompletionException || failure instanceof ExecutionException) && failure.getCause() != null) {
            failure = failure.getCause();
        }
        if (failure instanceof ExecutionStoppedException stopped) { return stopStatus(stopped.reason()); }
        if (failure instanceof BudgetExceededException) { return AgentRunResult.Status.BUDGET_EXCEEDED; }
        if (failure instanceof LLMConfigurationException || failure instanceof UnsupportedOperationException) {
            return AgentRunResult.Status.CONFIGURATION_ERROR;
        }
        return AgentRunResult.Status.FAILED;
    }

    /** 解除监听并关闭自己拥有的控制，既不关闭宿主控制也不关闭共享执行器。 */
    @Override public void close() {
        if (closed.compareAndSet(false, true)) {
            ExecutionControl.TerminationRegistration link;
            while ((link = links.poll()) != null) { link.close(); }
            var current = execution;
            if (current != null) { current.close(); }
            lifetime.close();
        }
    }
}
