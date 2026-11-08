package org.zexnocs.teanekoclient.agent.service;

import jakarta.annotation.PreDestroy;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.integration.message.NativeMessageInput;
import org.zexnocs.teanekoagent.integration.message.NativeMessageSources;
import org.zexnocs.teanekoagent.runtime.AgentActorContext;
import org.zexnocs.teanekoagent.runtime.access.IAgentScopePolicyPort;
import org.zexnocs.teanekoagent.runtime.context.IAgentContextReference;
import org.zexnocs.teanekoagent.runtime.context.IAgentContextReferences;
import org.zexnocs.teanekoagent.runtime.operation.AgentOperationReceipt;
import org.zexnocs.teanekoagent.runtime.operation.IAgentOperationStore;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekoclient.agent.response.AgentConversationResponse;
import org.zexnocs.teanekoclient.agent.response.AgentMemoryWriteResponse;
import org.zexnocs.teanekoclient.agent.response.AgentPersonalityCorrectionResponse;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.actuator.execution.ResourceBudget;
import org.zexnocs.teanekocore.utils.StableDigest;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;

/**
 * 内部客户端的通用操作执行边界，统一固定真实来源、持久执行权和资源所有权。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/07：接入插件归属与独立贡献，保留原权限及执行契约。
 * <br>2026/11/07：业务通过类型化目录提供，客户端不解释记忆或人格数据。
 * <br>2026/11/08：业务授权在认领前复核，认领后的失败仍完成真实回执；普通聊天可独立装配。
 * <br>2026/11/08：先发布实际业务结果再关闭自有控制，取消观察仍等待真实资源释放。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.5.0-l
 * @version 4.9.0-l
 */
@Service
public final class AgentOperations implements AutoCloseable {
    private final IAgentScopePolicyPort scopes;
    private final @Nullable IAgentContextReferences references;
    private final IAgentOperationStore attempts;
    private final AgentClientOperationRegistry operations;
    private final Executor executor;
    private final ScheduledExecutorService scheduler;
    private final Semaphore capacity = new Semaphore(64);
    private final Map<UUID, ExecutionControl> active = new HashMap<>();
    private volatile boolean closed;

    /**
     * 借用通用生命周期及操作目录，构造不读取存储或执行业务。
     * @param scopes 当前聊天准入
     * @param references 可选上下文引用生命周期
     * @param attempts 原持久化防重
     * @param operations 已发布的操作目录
     * @param executor 共享业务执行域
     * @param scheduler 共享截止时间设施
     */
    public AgentOperations(IAgentScopePolicyPort scopes, Optional<IAgentContextReferences> references,
            IAgentOperationStore attempts, AgentClientOperationRegistry operations,
            @Qualifier("virtualExecutor") Executor executor,
            @Qualifier("taskExecutor") ScheduledExecutorService scheduler) {
        this.scopes = scopes;
        this.references = references.orElse(null);
        this.attempts = attempts;
        this.operations = operations;
        this.executor = executor;
        this.scheduler = scheduler;
    }

    /**
     * 保留普通聊天的类型化直接调用入口。
     * @param source 真实原生消息
     * @param parent 可选借用父控制
     * @return 正式响应，不执行外部平台发送
     */
    public CompletionStage<AgentConversationResponse> conversation(ITeaNekoMessageData source,
            @Nullable ExecutionControl parent) {
        return execute(AgentConversationResponse.PROTOCOL, source, "", parent);
    }

    /**
     * 保留既有客户端协议便捷入口，写入规则由注册的业务处理器拥有。
     * @param source 真实原生消息
     * @param parent 可选借用父控制
     * @return 原记忆写入响应协议
     */
    public CompletionStage<AgentMemoryWriteResponse> memory(ITeaNekoMessageData source,
            @Nullable ExecutionControl parent) {
        return execute(AgentMemoryWriteResponse.PROTOCOL, source, "", parent);
    }

    /**
     * 保留既有客户端协议便捷入口，字段和权限由注册的业务处理器校验。
     * @param source 真实原生消息
     * @param field 操作字段
     * @param parent 可选借用父控制
     * @return 原候选提交响应协议
     */
    public CompletionStage<AgentPersonalityCorrectionResponse> correction(ITeaNekoMessageData source, String field,
            @Nullable ExecutionControl parent) {
        return execute(AgentPersonalityCorrectionResponse.PROTOCOL, source, field, parent);
    }

    /**
     * 按稳定协议取得真实执行权；未安装或停用的处理器不能启动业务。
     * @param protocol 类型化传输协议
     * @param source 原生来源
     * @param field 操作字段
     * @param parent 可选借用父控制
     * @param <R> 响应类型
     * @return 观察取消受控制约束，资源仍等待实际业务终态释放
     */
    public <R> CompletionStage<R> execute(AgentOperationProtocol<R> protocol, ITeaNekoMessageData source,
            String field, @Nullable ExecutionControl parent) {
        if (closed || !capacity.tryAcquire()) {
            return CompletableFuture.failedStage(new IllegalStateException("内部 Agent 入口已关闭或在途容量已满。"));
        }
        final ExecutionControl control;
        try {
            control = parent == null ? ExecutionControl.withDeadline(new ResourceBudget(Map.of()),
                    Instant.now().plusSeconds(180), Clock.systemUTC(), scheduler) : parent.child();
        } catch (RuntimeException failure) {
            capacity.release();
            return CompletableFuture.failedStage(failure);
        }
        synchronized (active) {
            active.put(control.id(), control);
            if (closed) {
                control.cancel();
            }
        }
        final AgentClientOperationRegistry.Invocation<R> invocation;
        try {
            invocation = operations.open(protocol);
        } catch (RuntimeException failure) {
            release(control);
            return CompletableFuture.failedStage(failure);
        }
        CompletionStage<R> work;
        try {
            var actor = NativeMessageSources.actor(source);
            var input = NativeMessageInput.from(source);
            var id = operation(actor, protocol.id());
            var digest = invocation.digest(input, field);
            Runnable guard = () -> {
                guard(source, actor, control);
                invocation.check(source);
            };
            work = CompletableFuture.supplyAsync(() -> {
                guard.run();
                return references != null && references.available(actor.scope()) ? references.resolve(actor.scope())
                        : CompletableFuture.<IAgentContextReference>completedStage(null);
            }, executor).thenCompose(stage -> stage).thenComposeAsync(reference -> {
                guard.run();
                var attempt = new IAgentOperationStore.Attempt(id, protocol.id(), actor, reference, digest,
                        IAgentOperationStore.Kind.CLIENT_ENTRY);
                return attempts.claim(attempt).thenCompose(claim -> {
                    if (!claim.acquired()) {
                        guard.run();
                        return CompletableFuture.completedStage(protocol.statusResponse().apply(id,
                                "REPLAY_" + claim.receipt().status()));
                    }
                    CompletionStage<R> operation;
                    try {
                        var context = new AgentClientOperationContext(id, source, actor, input, field, reference,
                                control, parent != null, guard);
                        operation = verify(reference).thenCompose(ignored -> {
                            guard.run();
                            return invocation.execute(context);
                        }).thenCompose(result -> verify(reference).thenApply(ignored -> {
                            guard.run();
                            return result;
                        }));
                    } catch (RuntimeException failure) {
                        operation = CompletableFuture.failedStage(failure);
                    }
                    return operation.handle((result, failure) -> attempts.finish(attempt, new AgentOperationReceipt(
                            failure == null ? AgentOperationReceipt.Status.COMPLETED : AgentOperationReceipt.Status.FAILED))
                            .thenApply(receipt -> {
                                if (failure != null) {
                                    throw new CompletionException(failure);
                                }
                                guard.run();
                                return result;
                            })).thenCompose(stage -> stage);
                });
            }, executor);
        } catch (RuntimeException failure) {
            work = CompletableFuture.failedStage(failure);
        }
        // 2026/11/08：先发布真实结果再关闭自有控制，避免成功完成被 CLOSED 终止覆盖。
        var completed = new CompletableFuture<R>();
        var observed = control.guard(completed);
        invocation.track(work).whenComplete((result, failure) -> {
            try {
                if (failure == null) {
                    completed.complete(result);
                } else {
                    completed.completeExceptionally(failure);
                }
            } finally {
                release(control);
            }
        });
        return observed;
    }

    /**
     * 仅对本次实际安装的上下文引用进行复核，纯聊天不创建业务引用。
     * @param reference 本次固定引用
     * @return 当前引用仍有效时完成
     */
    private CompletionStage<Void> verify(@Nullable IAgentContextReference reference) {
        if (reference == null) {
            return CompletableFuture.completedStage(null);
        }
        if (references == null) {
            return CompletableFuture.failedStage(new IllegalStateException("内部操作的上下文提供者已不可用。"));
        }
        return references.verify(reference);
    }

    /**
     * 每个检查点读取真实注册与原生身份，不在控制锁内访问存储。
     * @param source 原消息
     * @param actor 固定身份
     * @param control 当前控制
     * @throws IllegalStateException 入口、作用域或原生身份失效时
     */
    private void guard(ITeaNekoMessageData source, AgentActorContext actor, ExecutionControl control) {
        control.checkActive();
        var policy = scopes.read(actor.scope());
        if (closed || !policy.registered() || !policy.enabled() || !actor.equals(NativeMessageSources.actor(source))) {
            throw new IllegalStateException("内部 Agent 当前来源或作用域不可执行。");
        }
    }

    /**
     * 按原生发生和操作定位稳定身份，echo 只用于传输关联。
     * @param actor 固定来源
     * @param action 稳定操作类别
     * @return 与既有版本一致的业务身份
     */
    private static UUID operation(AgentActorContext actor, String action) {
        return UUID.nameUUIDFromBytes(StableDigest.sha256("agent-internal-v1", actor.scope().clientId(),
                actor.scope().scopeId(), actor.sourceId(), action).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 真实业务完成后释放本入口拥有的控制和容量。
     * @param control 自有独立或派生控制
     */
    private void release(ExecutionControl control) {
        synchronized (active) {
            active.remove(control.id());
        }
        control.close();
        capacity.release();
    }

    /** 取消自有控制，处理器实例、共享邮箱和借用执行器由各自所有者释放。 */
    @Override
    @PreDestroy
    public void close() {
        closed = true;
        List<ExecutionControl> controls;
        synchronized (active) {
            controls = List.copyOf(active.values());
        }
        controls.forEach(ExecutionControl::cancel);
    }
}
