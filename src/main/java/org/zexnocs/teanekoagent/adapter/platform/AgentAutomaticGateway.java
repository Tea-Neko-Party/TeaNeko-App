package org.zexnocs.teanekoagent.adapter.platform;

import jakarta.annotation.PreDestroy;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.adapter.command.NativeCommandBridge;
import org.zexnocs.teanekoagent.agent.*;
import org.zexnocs.teanekoagent.agent.command.CommandInvocationReceipt;
import org.zexnocs.teanekoagent.agent.command.ICommandAttemptStore;
import org.zexnocs.teanekoagent.agent.memory.IAgentMemoryReferences;
import org.zexnocs.teanekoagent.agent.trigger.AgentTriggerGate;
import org.zexnocs.teanekoagent.history.HistoryContext;
import org.zexnocs.teanekoagent.llm.framework.message.LLMUserMessage;
import org.zexnocs.teanekoagent.llm.framework.message.content.LLMContentListBuilder;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptionsSnapshot;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
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
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.0.0
 * @version 4.5.0-l
 */
@Service
public final class AgentAutomaticGateway implements AutoCloseable {
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
     * 取得持久化唯一准入后生成，失败不对聊天发送错误噪声；票据释放由调用方负责。
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
     * @param data 原生来源
     * @param input 原生冻结图文
     * @param ticket 原触发票据
     * @param captured 已完成采集的固定边界，关闭采集时为空
     * @return 全部实际交付成功时为真
     */
    public CompletionStage<Boolean> respond(ITeaNekoMessageData data, NativeMessageInput input, AgentTriggerGate.Ticket ticket,
            @Nullable HistoryContext captured) {
        try {
            triggers.verify(ticket); var actor = ticket.signal().actor();
            if (!actor.equals(org.zexnocs.teanekoagent.adapter.command.NativeCommandBridge.actor(data)) || !input.hasContent() || input.text().length() > 65536) {
                throw new IllegalArgumentException("自动消息来源或文本无效。");
            }
            var id = UUID.nameUUIDFromBytes(StableDigest.sha256("automatic-reply-v1", actor.scope().clientId(), actor.scope().scopeId(),
                    actor.sourceId()).getBytes(StandardCharsets.UTF_8));
            var fixed = captured == null ? references.resolve(actor.scope()) : references.verify(captured.reference()).thenApply(ignored -> captured.reference());
            return fixed.thenCompose(reference -> {
                triggers.verify(ticket);
                var attempt = new ICommandAttemptStore.Attempt(id, "automatic-entry", actor, reference, input.digest(), ICommandAttemptStore.Kind.CHAT_ENTRY);
                return attempts.claim(attempt).thenCompose(claim -> {
                    if (!claim.acquired()) { return CompletableFuture.completedFuture(false); }
                    CompletionStage<Boolean> work;
                    NativeCommandBridge.Lease source = null;
                    try {
                        triggers.verify(ticket); access.bind(ticket); var target = instance();
                        var request = new AgentRunRequest(id, actor, AgentActivity.AUTOMATIC_REPLY,
                                List.of(AgentReplyPlan.instructions(), input.message()), null, reference, new AgentUserText(actor.sourceId(), input.text(), input.occurredAt()),
                                Long.MAX_VALUE, captured == null ? null : captured.personality().identity());
                        source = nativeCommands.open(request, data); var owner = source;
                        work = agents.run(target, request).thenCompose(result -> send(data, target, request, result, ticket))
                                .whenComplete((sent, failure) -> owner.close());
                    } catch (RuntimeException failure) {
                        if (source != null) { source.close(); } work = CompletableFuture.failedFuture(failure);
                    }
                    return work.whenComplete((sent, failure) -> access.release(ticket))
                            .handle((sent, failure) -> attempts.finish(attempt, new CommandInvocationReceipt(failure == null
                            ? CommandInvocationReceipt.Status.COMPLETED : CommandInvocationReceipt.Status.FAILED)).thenApply(receipt -> {
                                if (failure != null) { throw new CompletionException(failure); } return sent;
                            })).thenCompose(stage -> stage);
                });
            });
        } catch (RuntimeException failure) { return CompletableFuture.failedFuture(failure); }
    }

    /**
     * 每段发送前重放原授权检查，禁止因旧成功结果绕过当前引用、资料、人格和触发约束。
     * @param data 原发送目标
     * @param target 原实例
     * @param request 原固定请求
     * @param result 原模型结果
     * @param ticket 原触发票据
     * @return 全部平台回执成功时为真，空答案不发送
     */
    private CompletionStage<Boolean> send(ITeaNekoMessageData data, AgentHandle target, AgentRunRequest request,
            AgentRunResult result, AgentTriggerGate.Ticket ticket) {
        var plan = AgentReplyPlan.from(result);
        if (plan.isEmpty()) { return CompletableFuture.completedFuture(false); }
        CompletionStage<Void> chain = CompletableFuture.completedFuture(null);
        for (var part : plan.get().parts()) {
            chain = chain.thenCompose(ignored -> agents.retry(target, request)).thenCompose(checked -> {
                if (checked.status() != AgentRunResult.Status.SUCCESS || !AgentReplyPlan.from(checked).equals(plan)) {
                    return CompletableFuture.failedFuture(new IllegalStateException("自动回复的原结果已失效。"));
                }
                synchronized (this) { if (closed) { throw new IllegalStateException("自动回复入口已关闭。"); } }
                triggers.verify(ticket);
                if (!request.actor().equals(org.zexnocs.teanekoagent.adapter.command.NativeCommandBridge.actor(data))) {
                    throw new IllegalStateException("自动回复的原生目标已变化。");
                }
                return AgentReplySender.send(data, part);
            });
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
    @Override @PreDestroy public synchronized void close() { closed = true; if (handle != null) { registry.remove(handle.id()); handle = null; } }
}
