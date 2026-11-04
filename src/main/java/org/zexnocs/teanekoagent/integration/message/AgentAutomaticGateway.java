package org.zexnocs.teanekoagent.integration.message;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import jakarta.annotation.PreDestroy;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.component.expression.AgentReplyPlan;
import org.zexnocs.teanekoagent.component.expression.ExpressionDelivery;
import org.zexnocs.teanekoagent.component.expression.ReplyCoordinator;
import org.zexnocs.teanekoagent.component.expression.adapter.AgentReplySender;
import org.zexnocs.teanekoagent.component.llm.framework.message.LLMUserMessage;
import org.zexnocs.teanekoagent.component.llm.framework.message.content.LLMContentListBuilder;
import org.zexnocs.teanekoagent.component.llm.framework.model.LLMModelOptionsSnapshot;
import org.zexnocs.teanekoagent.component.memory.history.AgentDeliveryService;
import org.zexnocs.teanekoagent.component.memory.history.HistoryContext;
import org.zexnocs.teanekoagent.component.memory.runtime.IAgentMemoryReferences;
import org.zexnocs.teanekoagent.component.tool.command.CommandInvocationReceipt;
import org.zexnocs.teanekoagent.component.tool.command.ICommandAttemptStore;
import org.zexnocs.teanekoagent.integration.command.NativeCommandBridge;
import org.zexnocs.teanekoagent.runtime.*;
import org.zexnocs.teanekoagent.runtime.trigger.AgentTriggerGate;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.utils.StableDigest;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

/**
 * 自动消息的模型、持久化防重与顺序发送闭环，不借用命令 DEBUG 或内部客户端。
 * <br>2026/11/03：逐段保存实际平台回执，失败不自动补发。
 * <br>2026/11/03：自动回复接入独立等待及精确类型发送，关闭停止未发送尾部。
 * <br>2026/11/03：来源租约改用确定赋值，明确同步失败与异步交付的释放边界并整理排版。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/05：持久化准入前后复核协调截止时间，避免已到期的自动回复继续启动模型。
 *
 * <br>2026/11/05：自动回复失败可提示具体原因，取消和撤权不补发。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.5.0-l
 * @version 4.8.2-l
 */
@Service
public final class AgentAutomaticGateway implements AutoCloseable {
    private java.util.function.Predicate<AgentScope> failureReplies = scope -> true;

    /**
     * 2026/11/05：安装实时聊天回执策略，独立宿主保持默认开启。
     * @param policy 当前配置读取入口
     */
    @org.springframework.beans.factory.annotation.Autowired
    public void installFailureReplies(org.zexnocs.teanekoagent.integration.config.AgentFailureReplyPolicy policy) {
        failureReplies = policy::enabled;
    }
    private final IAgentFactory factory;
    private final IAgentRegistry registry;
    private final IAgentService agents;
    private final IAgentMemoryReferences references;
    private final ICommandAttemptStore attempts;
    private final AgentAutomaticAccess access;
    private final AgentTriggerGate triggers;
    private final NativeCommandBridge nativeCommands;
    private @Nullable AgentHandle handle;
    private boolean closed;
    private @Nullable AgentDeliveryService delivery;
    // 2026/11/03：拥有者只标记本入口生命周期，不作为持久身份或目标边界。
    private final String replyOwner = "automatic:" + UUID.randomUUID();
    private @Nullable ReplyCoordinator coordinator;
    private @Nullable ExpressionDelivery expressionDelivery;
    /**
     * 2026/11/03：完整默认装配共享真实目标队列，旧独立宿主保持原行为。
     * @param delivery 表达交付边界
     * @param coordinator 目标顺序与取消
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setExpressions(ExpressionDelivery delivery, ReplyCoordinator coordinator) {
        // 2026/11/03：可选装配的非空依赖分别赋值，保留未装配时的可空字段状态。
        this.expressionDelivery = delivery;
        this.coordinator = coordinator;
    }

    /**
     * 2026/11/03：默认宿主可选接入账本，保留原独立构造方式。
     * @param delivery 持久回执边界
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setDelivery(@Nullable AgentDeliveryService delivery) {
        // 2026/11/03：参数与可清除的可空字段契约一致。
        this.delivery = delivery;
    }

    /**
     * 组合原会话、引用和防重服务，构造不创建实例或调用模型。
     * @param factory 原实例工厂
     * @param registry 实例拥有者
     * @param agents 默认顺序邮箱
     * @param references 原空间边界
     * @param attempts 持久化操作防重
     * @param access 原管线的票据适配
     * @param triggers 当前触发门控
     * @param nativeCommands 真实命令来源租约，仍遵守原工具开关和权限
     */
    public AgentAutomaticGateway(IAgentFactory factory, IAgentRegistry registry, IAgentService agents,
            IAgentMemoryReferences references, ICommandAttemptStore attempts, AgentAutomaticAccess access, AgentTriggerGate triggers,
            NativeCommandBridge nativeCommands) {
        this.factory = factory; this.registry = registry; this.agents = agents; this.references = references;
        this.attempts = attempts; this.access = access; this.triggers = triggers;
        this.nativeCommands = nativeCommands;
    }

    /**
     * 取得持久化唯一准入后生成，失败按当前配置发送安全原因；票据释放由调用方负责。
     * 原生命令租约由异步工作链释放，覆盖模型、工具和实际发送，不能在同步作用域提前关闭。
     * @param data 实际原生消息
     * @param text 当前原文中的文本部分，不包含引用正文
     * @param ticket 已取得的原生触发票据
     * @return 实际发送全部成功时为真，无发言或重复消息为假
     */
    public CompletionStage<Boolean> respond(ITeaNekoMessageData data, String text, AgentTriggerGate.Ticket ticket) {
        var input = new NativeMessageInput(text, LLMUserMessage.builder().contents(new LLMContentListBuilder().addText(text).build()).build(),
                data.getTime(), StableDigest.sha256("text", text, data.getTime().toString()));
        return respond(data, input, ticket, null);
    }

    /**
     * 图文保持原顺序，已经采集的原消息继续使用同一空间及人格，排队后变化则拒绝。
     * 2026/11/03：先取得真实发送目标的顺序位置，再运行原生成和交付。
     * @param data 原生来源
     * @param input 原生冻结图文
     * @param ticket 原触发票据
     * @param captured 已完成采集的固定边界，关闭采集时为空
     * @return 全部实际交付成功时为真
     */
    public CompletionStage<Boolean> respond(ITeaNekoMessageData data, NativeMessageInput input, AgentTriggerGate.Ticket ticket,
            @Nullable HistoryContext captured) {
        if (coordinator != null) {
            var actor = ticket.signal().actor();
            var id = requestId(actor);
            return coordinator.execute(replyOwner, actor, id, data.getClient().getTeaNekoToolbox().getSelfPlatformId().orElse(""),
                    control -> respondPrepared(data, input, ticket, captured, control))
                    .whenComplete((sent, failure) -> {
                        if (failure != null) { access.release(ticket); triggers.finish(ticket, false); }
                    });
        }
        return respondPrepared(data, input, ticket, captured, null);
    }

    /**
     * 2026/11/03：保留原持久化准入，在真实顺序位置执行生成与发送。
     * 2026/11/03：租约只赋值一次，完整异步交付和同步装配失败分别释放，不提前关闭。
     * @param data 原来源
     * @param input 原图文
     * @param ticket 原票据
     * @param captured 固定采集边界
     * @param control 可选协调控制
     * @return 实际交付状态
     */
    private CompletionStage<Boolean> respondPrepared(ITeaNekoMessageData data, NativeMessageInput input, AgentTriggerGate.Ticket ticket,
            @Nullable HistoryContext captured, @Nullable ExecutionControl control) {
        try {
            triggers.verify(ticket);
            var actor = ticket.signal().actor();
            if (!actor.equals(NativeCommandBridge.actor(data)) || !input.hasContent() || input.text().length() > 65536) {
                throw new IllegalArgumentException("自动消息来源或文本无效。");
            }
            var id = requestId(actor);
            var fixed = captured == null
                    ? references.resolve(actor.scope())
                    : references.verify(captured.reference()).thenApply(ignored -> captured.reference());
            return fixed.thenCompose(reference -> {
                // 2026/11/05：异步资料查询结束后不得继续提交已经停止的请求。
                if (control != null) {
                    control.checkActive();
                }
                triggers.verify(ticket);
                var attempt = new ICommandAttemptStore.Attempt(id, "automatic-entry", actor, reference, input.digest(),
                        ICommandAttemptStore.Kind.CHAT_ENTRY);
                return attempts.claim(attempt).thenCompose(claim -> {
                    if (!claim.acquired()) {
                        return CompletableFuture.completedFuture(false);
                    }

                    CompletionStage<Boolean> work;
                    try {
                        // 2026/11/05：准入写入期间到期时仍收尾账本，但不再发起模型执行。
                        if (control != null) {
                            control.checkActive();
                        }
                        triggers.verify(ticket);
                        access.bind(ticket);
                        var target = instance();
                        var request = new AgentRunRequest(id, actor, AgentActivity.AUTOMATIC_REPLY,
                                List.of(AgentReplyPlan.instructions(), input.message()), null, reference,
                                new AgentUserText(actor.sourceId(), input.text(), input.occurredAt()),
                                Long.MAX_VALUE, captured == null ? null : captured.personality().identity());

                        var source = nativeCommands.open(request, data);
                        try {
                            var generated = agents.run(target, request);
                            if (control != null) {
                                java.util.Objects.requireNonNull(coordinator).observe(control, generated,
                                        () -> agents.cancel(target, actor.scope(), id, actor.userId().orElseThrow()));
                            }
                            work = generated.thenCompose(result -> send(data, target, request, result, ticket, control))
                                    .whenComplete((sent, failure) -> source.close());
                        } catch (RuntimeException failure) {
                            source.close();
                            throw failure;
                        }
                    } catch (RuntimeException failure) {
                        work = CompletableFuture.failedFuture(failure);
                    }

                    return work.whenComplete((sent, failure) -> access.release(ticket))
                            .handle((sent, failure) -> {
                                var status = failure == null
                                        ? CommandInvocationReceipt.Status.COMPLETED : CommandInvocationReceipt.Status.FAILED;
                                return attempts.finish(attempt, new CommandInvocationReceipt(status)).thenApply(receipt -> {
                                    if (failure != null) {
                                        throw new CompletionException(failure);
                                    }
                                    return sent;
                                });
                            }).thenCompose(stage -> stage);
                });
            });
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    /**
     * 每段发送前重放原授权检查，禁止因旧成功结果绕过当前引用、资料、人格和触发约束。
     * @param data 原发送目标
     * @param target 原实例
     * @param request 原固定请求
     * @param result 原模型结果
     * @param ticket 原触发票据
     * @param control 2026/11/03：可选独立交付控制
     * @return 全部平台回执成功时为真，空答案不发送
     */
    private CompletionStage<Boolean> send(ITeaNekoMessageData data, AgentHandle target, AgentRunRequest request,
            AgentRunResult result, AgentTriggerGate.Ticket ticket, @Nullable ExecutionControl control) {
        // 2026/11/05：失败也经原身份、票据、启用和停止检查；提示不写入模型对话历史。
        if (result.status() != AgentRunResult.Status.SUCCESS) {
            // 2026/11/05：跳过、取消和过期不是模型故障，不向已经失效的聊天补发通知。
            if (result.status() == AgentRunResult.Status.SKIPPED
                    || result.status() == AgentRunResult.Status.CANCELLED
                    || result.status() == AgentRunResult.Status.STALE_CONVERSATION) {
                return CompletableFuture.completedStage(false);
            }
            synchronized (this) {
                if (closed) {
                    return CompletableFuture.completedStage(false);
                }
            }
            if (control != null) {
                control.checkActive();
            }
            triggers.verify(ticket);
            if (!failureReplies.test(request.actor().scope())
                    || !request.actor().equals(NativeCommandBridge.actor(data))) {
                return CompletableFuture.completedStage(false);
            }
            return data.getMessageSender().sendReplyMessageAsync("Agent 未能回复：" + result.failureDescription())
                    .thenApply(ignored -> false);
        }
        // 2026/11/03：表达计划复用原票据和目标守卫，完成段数不通过重解析推断。
        if (result.replyPlan() != null) {
            if (result.replyPlan().steps().isEmpty()) { return CompletableFuture.completedStage(false); }
            if (expressionDelivery == null || control == null) { return CompletableFuture.failedStage(new IllegalStateException("表达计划缺少实际交付装配。")); }
            return expressionDelivery.send(data, request, result, control, () -> agents.retry(target, request).thenAccept(checked -> {
                synchronized (this) { if (closed) { throw new IllegalStateException("自动回复入口已关闭。"); } }
                triggers.verify(ticket);
                if (checked.status() != AgentRunResult.Status.SUCCESS || !java.util.Objects.equals(checked.replyPlan(), result.replyPlan()) || !NativeCommandBridge.actor(data).equals(request.actor())) {
                    throw new IllegalStateException("自动表达回复的原结果或目标已失效。");
                }
            })).thenApply(ignored -> true);
        }
        var plan = AgentReplyPlan.from(result);
        if (plan.isEmpty()) { return CompletableFuture.completedFuture(false); }
        CompletionStage<Void> chain = CompletableFuture.completedFuture(null);
        int next = 0;
        for (var part : plan.get().parts()) {
            int index = next++;
            // 2026/11/03：授权复核保留在实际发送回调内，不能被数据库等待隔开。
            java.util.function.Supplier<CompletionStage<Void>> sending = () -> agents.retry(target, request).thenCompose(checked -> {
                // 2026/11/03：旧协议同样响应已经生成后的发送取消。
                if (control != null) { control.checkActive(); }
                if (checked.status() != AgentRunResult.Status.SUCCESS || !AgentReplyPlan.from(checked).equals(plan)) {
                    return CompletableFuture.failedFuture(new IllegalStateException("自动回复的原结果已失效。"));
                }
                synchronized (this) { if (closed) { throw new IllegalStateException("自动回复入口已关闭。"); } }
                triggers.verify(ticket);
                if (!request.actor().equals(org.zexnocs.teanekoagent.integration.command.NativeCommandBridge.actor(data))) {
                    throw new IllegalStateException("自动回复的原生目标已变化。");
                }
                return AgentReplySender.send(data, part);
            });
            chain = chain.thenCompose(ignored -> delivery == null ? sending.get() : delivery.send(result.memoryReference(), request.actor(), request.requestId(),
                    index, plan.get().parts().size(), part, sending));
        }
        return chain.thenApply(ignored -> true);
    }

    /**
     * 每个宿主只拥有一个自动回复实例，scope 会话仍由原邮箱隔离。
     * @return 当前实例
     * @throws IllegalStateException 当服务已经关闭时
     */
    private synchronized AgentHandle instance() {
        if (closed) { throw new IllegalStateException("自动回复入口已关闭。"); }
        if (handle == null || handle.isClosed()) { handle = factory.create(new AgentDefinition("agent-automatic", new LLMModelOptionsSnapshot(null), Set.of(AgentActivity.values()))); }
        return handle;
    }

    /** 关闭本入口拥有的实例，不关闭借用邮箱、执行器或其他宿主实例。 */
    @Override @PreDestroy public void close() {
        // 2026/11/03：先标记关闭，再在锁外停止等待与原实例。
        AgentHandle current; synchronized (this) { closed = true; current = handle; handle = null; }
        if (coordinator != null) { coordinator.cancelOwner(replyOwner); }
        if (current != null) { registry.remove(current.id()); }
    }
    /**
     * 2026/11/03：排队与原账本使用相同稳定请求标识。
     * @param actor 原身份
     * @return 稳定请求 ID
     */
    private static UUID requestId(AgentActorContext actor) {
        return UUID.nameUUIDFromBytes(StableDigest.sha256("automatic-reply-v1", actor.scope().clientId(), actor.scope().scopeId(), actor.sourceId()).getBytes(StandardCharsets.UTF_8));
    }
}
