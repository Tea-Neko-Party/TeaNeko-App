package org.zexnocs.teanekoagent.client;

import jakarta.annotation.PreDestroy;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.adapter.command.NativeCommandBridge;
import org.zexnocs.teanekoagent.adapter.platform.AgentReplyPlan;
import org.zexnocs.teanekoagent.adapter.platform.NativeMessageInput;
import org.zexnocs.teanekoagent.agent.*;
import org.zexnocs.teanekoagent.agent.access.IAgentScopePolicyPort;
import org.zexnocs.teanekoagent.agent.command.CommandInvocationReceipt;
import org.zexnocs.teanekoagent.agent.command.ICommandAttemptStore;
import org.zexnocs.teanekoagent.agent.memory.IAgentMemoryReferences;
import org.zexnocs.teanekoagent.agent.personality.IAgentPersonalitySource;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptionsSnapshot;
import org.zexnocs.teanekoagent.memory.access.MemoryAccessContext;
import org.zexnocs.teanekoagent.memory.access.MemoryAccessService;
import org.zexnocs.teanekoagent.memory.access.MemoryAddIntent;
import org.zexnocs.teanekoagent.memory.fact.MemoryEventTime;
import org.zexnocs.teanekoagent.memory.fact.MemoryFactValue;
import org.zexnocs.teanekoagent.memory.namespace.MemoryNamespaceReference;
import org.zexnocs.teanekoagent.personality.IPersonalityLearningTarget;
import org.zexnocs.teanekoagent.personality.PersonalityCorrection;
import org.zexnocs.teanekoagent.response.AgentConversationResponse;
import org.zexnocs.teanekoagent.response.AgentMemoryWriteResponse;
import org.zexnocs.teanekoagent.response.AgentPersonalityCorrectionResponse;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.actuator.execution.ResourceBudget;
import org.zexnocs.teanekocore.command.api.CommandPermission;
import org.zexnocs.teanekocore.utils.StableDigest;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * 三类显式服务入口；只借用真实原生身份，不发送平台回复或回灌消息事件。
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.0.0
 * @version 4.5.0-l
 */
@Service
public final class AgentOperations implements AutoCloseable {
    private final IAgentService agents;
    private final IAgentFactory factory;
    private final IAgentRegistry registry;
    private final IAgentScopePolicyPort scopes;
    private final IAgentMemoryReferences references;
    private final NativeCommandBridge nativeCommands;
    private final ICommandAttemptStore attempts;
    private final MemoryAccessService memories;
    private final IAgentPersonalitySource personalities;
    private final @Nullable IPersonalityLearningTarget learning;
    private final Executor executor;
    private final ScheduledExecutorService scheduler;
    private final Semaphore capacity = new Semaphore(64);
    private final Map<UUID, ExecutionControl> active = new HashMap<>();
    private @Nullable AgentHandle handle;
    private volatile boolean closed;

    /**
     * 借用现有业务端口，构造不读取数据库或调用模型。
     * @param agents 会话邮箱
     * @param factory 实例工厂
     * @param registry 实例拥有者
     * @param scopes 当前开关
     * @param references 空间边界
     * @param nativeCommands 原生命令来源
     * @param attempts 持久化防重
     * @param memories 明确记忆新增
     * @param personalities 当前人格
     * @param learning 表达候选端口
     * @param executor 共享业务执行域
     * @param scheduler 共享截止时间设施
     */
    public AgentOperations(IAgentService agents, IAgentFactory factory, IAgentRegistry registry, IAgentScopePolicyPort scopes,
            IAgentMemoryReferences references, NativeCommandBridge nativeCommands, ICommandAttemptStore attempts, MemoryAccessService memories,
            IAgentPersonalitySource personalities, @Nullable IPersonalityLearningTarget learning, @Qualifier("virtualExecutor") Executor executor,
            @Qualifier("taskExecutor") ScheduledExecutorService scheduler) {
        this.agents = agents; this.factory = factory; this.registry = registry; this.scopes = scopes; this.references = references;
        this.nativeCommands = nativeCommands; this.attempts = attempts; this.memories = memories; this.personalities = personalities; this.learning = learning;
        this.executor = executor; this.scheduler = scheduler;
    }

    /**
     * 运行当前原生图文，复用共享会话；无父控制时由原邮箱建立本地配置预算。
     * @param source 真实原生消息
     * @param parent 可选借用父控制
     * @return 正式响应，不执行外部平台发送
     */
    public CompletionStage<AgentConversationResponse> conversation(ITeaNekoMessageData source, @Nullable ExecutionControl parent) {
        var input = NativeMessageInput.from(source); var actor = NativeCommandBridge.actor(source); var id = operation(actor, "conversation");
        return execute(source, id, "conversation", input.digest(), parent, (reference, control) -> {
            var target = instance();
            var request = new AgentRunRequest(id, actor, AgentActivity.COMMAND, List.of(AgentReplyPlan.instructions(), input.message()),
                    null, reference, parent == null ? new AgentUserText(actor.sourceId(), input.text(), input.occurredAt()) : null);
            var lease = nativeCommands.open(request, source);
            try {
                control.checkActive();
                var running = parent == null ? agents.run(target, request) : agents.run(target, request, control);
                var cancellation = control.onTermination(ignored -> agents.cancel(target, actor.scope(), id, actor.userId().orElseThrow()));
                return running.thenCompose(result -> result.status() == AgentRunResult.Status.SUCCESS ? agents.retry(target, request, control)
                        : CompletableFuture.completedStage(result)).thenApply(AgentConversationResponse::from)
                        .whenComplete((result, failure) -> { cancellation.close(); lease.close(); });
            } catch (RuntimeException failure) { lease.close(); throw failure; }
        }, status -> new AgentConversationResponse(id, status, List.of()));
    }

    /**
     * 只接受当前原生文本的明确记住格式，主语固定为实际发起者。
     * @param source 真实原文来源
     * @param parent 可选借用父控制
     * @return 实际事实新增回执
     */
    public CompletionStage<AgentMemoryWriteResponse> memory(ITeaNekoMessageData source, @Nullable ExecutionControl parent) {
        var input = NativeMessageInput.from(source); var actor = NativeCommandBridge.actor(source); var id = operation(actor, "memory");
        return execute(source, id, "memory", input.digest(), parent, (reference, control) -> {
            var context = new MemoryAccessContext(id, actor, reference);
            var intent = MemoryAddIntent.fromUserText(context, input.text()).orElseThrow(() -> new IllegalArgumentException("手动记忆须由当前用户明确提供记住原文。"));
            var value = new MemoryFactValue(MemoryFactValue.Kind.FACT, intent.text(), List.of(), 1,
                    MemoryFactValue.Importance.NORMAL, MemoryEventTime.unknown(), null);
            return memories.append(context, "internal-memory", intent, value, control)
                    .thenApply(result -> new AgentMemoryWriteResponse(result.status().name(), result.recordId()));
        }, status -> new AgentMemoryWriteResponse(status, null));
    }

    /**
     * 当前 OWNER/DEBUG 可提交表达候选，不修改画像、人格身份、字段白名单或管理锁。
     * @param source 完整补正原文的真实来源
     * @param field 当前基础人格允许的表达字段
     * @param parent 可选借用父控制
     * @return 真实候选提交结果
     * @throws IllegalStateException 当原生角色不是 OWNER 或 DEBUG 时
     */
    public CompletionStage<AgentPersonalityCorrectionResponse> correction(ITeaNekoMessageData source, String field, @Nullable ExecutionControl parent) {
        requireOwner(source);
        var learner = learning;
        if (learner == null) { return CompletableFuture.failedStage(new UnsupportedOperationException("当前人格适配器不支持表达候选。")); }
        var input = NativeMessageInput.from(source); var actor = NativeCommandBridge.actor(source); var id = operation(actor, "correction");
        return execute(source, id, "correction", StableDigest.sha256(input.digest(), field), parent, (reference, control) -> {
            requireOwner(source);
            return personalities.resolve(actor.scope(), control).thenCompose(basis -> {
                requireOwner(source); guard(source, actor, control);
                var candidate = new PersonalityCorrection(id, basis.baseId(), basis.baseVersion(), field, input.text(), 1, actor, input.occurredAt(), null, false);
                return learner.propose(actor.scope(), basis, candidate, control).thenCompose(added ->
                        personalities.verify(actor.scope(), basis, control).thenApply(ignored -> {
                            requireOwner(source); return new AgentPersonalityCorrectionResponse(added ? "STORED" : "DUPLICATE", added);
                        }));
            });
        }, status -> new AgentPersonalityCorrectionResponse(status, false));
    }

    /**
     * 在有界业务域固定引用、认领发生、等待真实终态并释放自有控制；不接管旧在途记录。
     * @param source 原生来源
     * @param id 稳定操作
     * @param action 固定操作种类
     * @param digest 原始负载摘要
     * @param parent 可选借用父控制
     * @param actionBody 明确业务动作
     * @param replay 已有回执的安全响应投影
     * @param <R> 响应 DTO
     * @return 受取消和截止时间保护的实际结果
     */
    private <R> CompletionStage<R> execute(ITeaNekoMessageData source, UUID id, String action, String digest, @Nullable ExecutionControl parent,
            BiFunction<MemoryNamespaceReference, ExecutionControl, CompletionStage<R>> actionBody, Function<String, R> replay) {
        if (closed || !capacity.tryAcquire()) { return CompletableFuture.failedStage(new IllegalStateException("内部 Agent 入口已关闭或在途容量已满。")); }
        final ExecutionControl control;
        try { control = parent == null ? ExecutionControl.withDeadline(new ResourceBudget(Map.of()), Instant.now().plusSeconds(180), Clock.systemUTC(), scheduler) : parent.child(); }
        catch (RuntimeException failure) { capacity.release(); return CompletableFuture.failedStage(failure); }
        var actor = NativeCommandBridge.actor(source);
        synchronized (active) { active.put(control.id(), control); if (closed) { control.cancel(); } }
        try {
            var work = CompletableFuture.supplyAsync(() -> { guard(source, actor, control); return references.resolve(actor.scope()); }, executor)
                    .thenCompose(stage -> stage).thenComposeAsync(reference -> {
                        guard(source, actor, control);
                        var attempt = new ICommandAttemptStore.Attempt(id, action, actor, reference, digest, ICommandAttemptStore.Kind.CLIENT_ENTRY);
                        return attempts.claim(attempt).thenCompose(claim -> {
                            if (!claim.acquired()) { return CompletableFuture.completedStage(replay.apply("REPLAY_" + claim.receipt().status())); }
                            CompletionStage<R> operation;
                            try {
                                operation = references.verify(reference).thenCompose(ignored -> {
                                    guard(source, actor, control); return actionBody.apply(reference, control);
                                }).thenCompose(result -> references.verify(reference).thenApply(ignored -> { guard(source, actor, control); return result; }));
                            } catch (RuntimeException failure) { operation = CompletableFuture.failedStage(failure); }
                            return operation.handle((result, failure) -> attempts.finish(attempt, new CommandInvocationReceipt(failure == null
                                    ? CommandInvocationReceipt.Status.COMPLETED : CommandInvocationReceipt.Status.FAILED)).thenApply(receipt -> {
                                if (failure != null) { throw new CompletionException(failure); } return result;
                            })).thenCompose(stage -> stage);
                        });
                    }, executor);
            return control.guard(work).whenComplete((result, failure) -> release(control));
        } catch (RuntimeException failure) { release(control); return CompletableFuture.failedStage(failure); }
    }

    /**
     * 每个业务检查点读取真实注册与原生身份，不在控制锁内访问配置或存储。
     * @param source 原消息
     * @param actor 固定身份
     * @param control 当前控制
     * @throws IllegalStateException 当入口、作用域或原生身份失效时
     */
    private void guard(ITeaNekoMessageData source, AgentActorContext actor, ExecutionControl control) {
        control.checkActive(); var policy = scopes.read(actor.scope());
        if (closed || !policy.registered() || !policy.enabled() || !actor.equals(NativeCommandBridge.actor(source))) {
            throw new IllegalStateException("内部 Agent 当前来源或作用域不可执行。");
        }
    }

    /**
     * 使用实际平台角色，不接受数据中的权限声明。
     * @param source 原生来源
     * @throws IllegalStateException 当原生角色不是 OWNER 或 DEBUG 时
     */
    private static void requireOwner(ITeaNekoMessageData source) {
        var role = source.getUserData().getRole();
        if (role != CommandPermission.OWNER && role != CommandPermission.DEBUG) { throw new IllegalStateException("手动公共表达补正需要真实 OWNER 或 DEBUG 权限。"); }
    }

    /**
     * 按原生发生和操作定位稳定 ID，echo 不是业务幂等键。
     * @param actor 固定来源
     * @param action 操作类别
     * @return 稳定业务 ID
     */
    private static UUID operation(AgentActorContext actor, String action) {
        return UUID.nameUUIDFromBytes(StableDigest.sha256("agent-internal-v1", actor.scope().clientId(), actor.scope().scopeId(), actor.sourceId(), action).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 延迟创建内部会话实例，保持 scope 邮箱和原工具门控。
     * @return 本入口拥有的实例
     */
    private synchronized AgentHandle instance() {
        if (closed) { throw new IllegalStateException("内部 Agent 入口已关闭。"); }
        if (handle == null || handle.isClosed()) { handle = factory.create(new AgentDefinition("agent-internal", new LLMModelOptionsSnapshot(null), Set.of(AgentActivity.COMMAND, AgentActivity.TOOL))); }
        return handle;
    }

    /**
     * 完成或停止后统一释放本操作的拥有资源。
     * @param control 自有独立或派生控制
     */
    private void release(ExecutionControl control) { synchronized (active) { active.remove(control.id()); } control.close(); capacity.release(); }

    /** 取消在途业务并释放自有实例，借用父控制、邮箱和执行器均由原宿主拥有。 */
    @Override @PreDestroy public void close() {
        closed = true; List<ExecutionControl> controls; synchronized (active) { controls = List.copyOf(active.values()); }
        controls.forEach(ExecutionControl::cancel); synchronized (this) { if (handle != null) { registry.remove(handle.id()); handle = null; } }
    }
}
