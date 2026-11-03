package org.zexnocs.teanekoagent.adapter.command;

import jakarta.annotation.PreDestroy;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.adapter.platform.AgentReplyPlan;
import org.zexnocs.teanekoagent.adapter.platform.AgentReplySender;
import org.zexnocs.teanekoagent.agent.*;
import org.zexnocs.teanekoagent.agent.access.IAgentScopePolicyPort;
import org.zexnocs.teanekoagent.agent.command.CommandInvocationReceipt;
import org.zexnocs.teanekoagent.agent.command.ICommandAttemptStore;
import org.zexnocs.teanekoagent.agent.conversation.IAgentConversationControl;
import org.zexnocs.teanekoagent.agent.memory.IAgentMemoryReferences;
import org.zexnocs.teanekoagent.expression.ExpressionDelivery;
import org.zexnocs.teanekoagent.expression.ReplyCoordinator;
import org.zexnocs.teanekoagent.history.AgentDeliveryService;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptionsSnapshot;
import org.zexnocs.teanekoagent.memory.namespace.IMemoryNamespaceDefaults;
import org.zexnocs.teanekoagent.memory.namespace.MemoryNamespaceBindings;
import org.zexnocs.teanekoagent.memory.namespace.MemoryNamespaceRegistry;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.utils.StableDigest;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;

/**
 * 原生命令的应用边界，按需拥有一个共享定义实例，原生来源仅在请求生命周期内绑定。
 * 不自动监听平台消息，不接管旧 Client，不恢复未完成执行或任意跨 scope 原文。
 * <br>2026/11/02：显式聊天加入发送者与 scope 上下文，原文凭据不包含显示元数据。
 * <br>2026/11/03：显式聊天逐段记录平台回执，生成成功不再作为送达依据。
 * <br>2026/11/03：顺序协调生成与发送，停止本人等待，沿用原交付复核及账本。
 * <br>2026/11/03：来源租约改用确定赋值，明确同步失败与异步完成的释放边界并整理排版。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.5.0-l
 * @version 4.6.2-l
 */
@Service
public final class AgentCommandGateway implements AutoCloseable {
    private final IAgentFactory factory;
    private final IAgentRegistry registry;
    private final IAgentService agents;
    private final IAgentScopePolicyPort scopes;
    private final NativeCommandBridge nativeCommands;
    private final IAgentMemoryReferences references;
    private final MemoryNamespaceBindings bindings;
    private final MemoryNamespaceRegistry namespaces;
    private final IMemoryNamespaceDefaults defaults;
    private final ICommandAttemptStore attempts;
    private final AgentNamespaceCommands namespaceCommands;
    private final Map<UUID, Chat> chats = new LinkedHashMap<>();
    private @Nullable AgentHandle handle;
    private boolean closed;
    private @Nullable AgentDeliveryService delivery;
    // 2026/11/03：拥有者只标记本入口生命周期，不作为持久身份或目标边界。
    private final String replyOwner = "command:" + UUID.randomUUID();
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
     * 2026/11/03：可选装配原账本，独立旧宿主不改变构造契约。
     * @param delivery 持久发送边界
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setDelivery(@Nullable AgentDeliveryService delivery) {
        // 2026/11/03：参数与可清除的可空字段契约一致。
        this.delivery = delivery;
    }
    /**
     * 组合现有业务端口，构造不创建实例、不读配置或请求模型。
     * @param factory 实例工厂
     * @param registry 实例拥有关系
     * @param agents 默认邮箱服务
     * @param conversations 会话生命周期
     * @param scopes 当前启用记录
     * @param nativeCommands 原生来源租约及桥接
     * @param references 当前持久化引用
     * @param bindings 原子绑定服务
     * @param namespaces 空间注册/分页服务
     * @param defaults 本地个人配额
     * @param scheduler 借用管理时限调度器
     * @param attempts 原生请求及工具共享的持久化防重端口
     */
    public AgentCommandGateway(IAgentFactory factory, IAgentRegistry registry, IAgentService agents, IAgentConversationControl conversations,
            IAgentScopePolicyPort scopes, NativeCommandBridge nativeCommands, IAgentMemoryReferences references, MemoryNamespaceBindings bindings,
            MemoryNamespaceRegistry namespaces, IMemoryNamespaceDefaults defaults, @Qualifier("taskExecutor") ScheduledExecutorService scheduler, ICommandAttemptStore attempts) {
        this.factory = factory; this.registry = registry; this.agents = agents; this.scopes = scopes;
        this.nativeCommands = nativeCommands; this.references = references; this.bindings = bindings; this.namespaces = namespaces;
        this.defaults = defaults; this.attempts = attempts;
        this.namespaceCommands = new AgentNamespaceCommands(scopes, references, bindings, conversations, scheduler);
    }
    /**
     * 显式文本聊天，等待真实回答及一次平台发送；相同消息的并发提交复用完整阶段。
     * 派生命令保留父控制，模型产生的子请求不伪装为原始用户文本凭据。
     * @param data 真实原生消息
     * @param text 当前命令提供的文本
     * @param parent 可选原工具阶段父控制
     * @return 含真实平台发送完成的阶段
     */
    public CompletionStage<Void> chat(ITeaNekoMessageData data, String text, @Nullable ExecutionControl parent) {
        // 2026/11/03：普通原生轮次从生成前取得目标顺序，借用父控制的内部调用保持原重入约束。
        try {
            var actor = NativeCommandBridge.actor(data); requireEnabled(actor.scope());
            if (text.isBlank() || text.length() > 65536) { return reply(data, "请输入一至65536字符的对话文本。"); }
            var id = operation(data, parent == null ? "chat" : "chat-child:" + parent.id()); var target = instance();
            // 2026/11/02：命令聊天与普通原生图文共用模型上下文，原文凭据不包含元数据。
            var request = new AgentRunRequest(id, actor, AgentActivity.COMMAND, List.of(AgentReplyPlan.instructions(), org.zexnocs.teanekoagent.adapter.platform.NativeMessageInput.textMessage(data, text)),
                    null, null, parent == null ? new AgentUserText(actor.sourceId(), text, data.getTime()) : null);
            if (parent != null) { return agents.run(target, request, parent).thenCompose(result -> deliver(data, target, request, result, parent)); }
            final Chat chat;
            synchronized (chats) {
                var original = chats.get(id);
                if (original != null) { return original.actor().equals(actor) && original.textDigest().equals(StableDigest.sha256(text))
                        ? original.completion().minimalCompletionStage() : CompletableFuture.failedStage(new IllegalArgumentException("相同消息对应不同聊天内容。")); }
                if (chats.size() >= 1000) {
                    var iterator = chats.entrySet().iterator();
                    while (iterator.hasNext() && chats.size() >= 1000) { if (iterator.next().getValue().completion().isDone()) { iterator.remove(); } }
                }
                if (closed || chats.size() >= 1000) { return CompletableFuture.failedStage(new IllegalStateException("Agent 入口已关闭或容量已满。")); }
                chat = new Chat(actor, StableDigest.sha256(text), new CompletableFuture<>()); chats.put(id, chat);
            }
            CompletionStage<Void> started;
            try { started = coordinator == null ? startChat(data, request, target, null)
                    : coordinator.execute(replyOwner, actor, id, data.getClient().getTeaNekoToolbox().getSelfPlatformId().orElse(""),
                            control -> startChat(data, request, target, control)); } catch (RuntimeException failure) { started = CompletableFuture.failedStage(failure); }
            started.whenComplete((ignored, failure) -> {
                if (failure == null) { chat.completion().complete(null); } else { chat.completion().completeExceptionally(failure); }
            });
            return chat.completion().minimalCompletionStage();
        } catch (RuntimeException invalid) { return reply(data, "Agent 当前不可执行，请检查注册、配置和运行状态。"); }
    }
    /**
     * 先提交原生消息的唯一准入，重启后不恢复旧模型循环或以新工具 ID 重跑同一消息。
     * 2026/11/03：租约只赋值一次，异步模型结束和同步装配失败分别释放，不提前关闭。
     * @param data 原发送来源
     * @param request 宿主固定请求
     * @param target 当前实例
     * @param control 2026/11/03：可选独立排队与交付控制
     * @return 包含生成、来源释放、交付复核和发送的完整阶段
     */
    private CompletionStage<Void> startChat(ITeaNekoMessageData data, AgentRunRequest request, AgentHandle target,
            @Nullable ExecutionControl control) {
        return references.resolve(request.actor().scope()).thenCompose(reference -> {
            requireEnabled(request.actor().scope());
            var attempt = new ICommandAttemptStore.Attempt(request.requestId(), "entry", request.actor(), reference,
                    StableDigest.sha256(Objects.requireNonNull(request.userText()).text()), ICommandAttemptStore.Kind.CHAT_ENTRY);
            return attempts.claim(attempt).thenCompose(claim -> {
                if (!claim.acquired()) {
                    return reply(data, "该消息已有处理记录（" + claim.receipt().status()
                            + "），不会重新执行模型或命令；如需新操作请发送新消息。");
                }

                CompletionStage<Void> execution;
                try {
                    requireEnabled(request.actor().scope());
                    synchronized (chats) {
                        if (closed) {
                            throw new IllegalStateException("Agent 命令入口已关闭。");
                        }
                    }

                    var lease = nativeCommands.open(request, data);
                    try {
                        var generated = agents.run(target, request);
                        if (control != null) {
                            Objects.requireNonNull(coordinator).observe(control, generated,
                                    () -> agents.cancel(target, request.actor().scope(), request.requestId(),
                                            request.actor().userId().orElseThrow()));
                        }
                        execution = generated.whenComplete((result, failure) -> lease.close())
                                .thenCompose(result -> result.status() == AgentRunResult.Status.SUCCESS
                                        ? agents.retry(target, request) : CompletableFuture.completedStage(result))
                                .thenCompose(result -> deliver(data, target, request, result, control));
                    } catch (RuntimeException failure) {
                        lease.close();
                        throw failure;
                    }
                } catch (RuntimeException failure) {
                    execution = CompletableFuture.failedStage(failure);
                }

                return execution.handle((ignored, failure) -> {
                    var status = failure == null
                            ? CommandInvocationReceipt.Status.COMPLETED : CommandInvocationReceipt.Status.FAILED;
                    return attempts.finish(attempt, new CommandInvocationReceipt(status))
                            .handle((receipt, writeFailure) -> {
                                if (failure != null) {
                                    throw new CompletionException(failure);
                                }
                                if (writeFailure != null) {
                                    throw new CompletionException(writeFailure);
                                }
                                return (Void) null;
                            });
                }).thenCompose(stage -> stage);
            });
        });
    }
    /**
     * 只展示开关、当前空间和本人在途请求标识，不泄露其他用户的内容。
     * @param data 真实查询来源
     * @return 可直接发送的状态说明
     */
    public CompletionStage<String> status(ITeaNekoMessageData data) {
        try {
            var actor = NativeCommandBridge.actor(data); var policy = scopes.read(actor.scope());
            if (!policy.registered() || !policy.enabled()) { return CompletableFuture.completedStage("Agent 未登记或未启用；请由有权限者使用 /cfg reg agent 或 /cfg set agent enabled true。"); }
            final List<String> pending;
            synchronized (chats) { pending = chats.entrySet().stream().filter(entry -> !entry.getValue().completion().isDone()
                    && entry.getValue().actor().scope().equals(actor.scope()) && entry.getValue().actor().userId().equals(actor.userId())).map(entry -> entry.getKey().toString()).toList(); }
            return bindings.readReference(actor.scope()).thenApply(value -> "Agent 已启用；当前空间：" + value.map(ref -> ref.namespaceId().value()).orElse("尚未初始化")
                    + "；本人在途请求：" + (pending.isEmpty() ? "无" : String.join("、", pending)));
        } catch (RuntimeException failure) { return CompletableFuture.completedStage("Agent 状态暂不可用。"); }
    }
    /**
     * 只停止真实发起者自己的请求，不使用共享群上下文的其他身份。
     * @param data 真实请求来源
     * @param requestId 待停止标识
     * @return 明确取消接收状态
     * @throws IllegalStateException 当作用域未启用时
     * @throws IllegalArgumentException 当请求标识不是有效 UUID 时
     */
    public String stop(ITeaNekoMessageData data, String requestId) {
        var actor = NativeCommandBridge.actor(data); requireEnabled(actor.scope());
        final AgentHandle current; synchronized (chats) { current = handle; }
        // 2026/11/03：已生成但仍等待发送的请求也属于本人在途工作。
        var id = UUID.fromString(requestId);
        if (coordinator != null && coordinator.cancel(actor, id)) { return "停止请求：" + IAgentService.CancelResult.REQUESTED; }
        if (current == null) { return "没有在途请求。"; }
        return "停止请求：" + agents.cancel(current, actor.scope(), id, actor.userId().orElseThrow());
    }
    /**
     * 列举本人创建的空间，默认分页大小沿用个人配额，不能指定其他 UUID。
     * @param data 原生来源
     * @param offset 分页偏移
     * @return 有界空间列表
     * @throws IllegalStateException 当作用域未启用时
     */
    public CompletionStage<String> namespaces(ITeaNekoMessageData data, int offset) {
        var actor = NativeCommandBridge.actor(data); requireEnabled(actor.scope());
        return namespaces.listCreated(actor, offset, Math.min(100, defaults.maxCreated())).thenApply(values -> values.isEmpty() ? "本页没有自建空间。"
                : values.stream().map(value -> value.id().value() + "，创建于 " + value.createdAt()).collect(Collectors.joining("\n")) + "\n下一页偏移：" + (offset + values.size()));
    }
    /**
     * 通过原事务创建空空间或绑定已有空间，提交后读取当前权威引用并协调所有会话。
     * @param data 已经原命令链授权的真实来源
     * @param target 已有空间，null 表示创建新的空空间
     * @param parent 可选工具父控制，保持借用
     * @return 真实提交、拒绝及同步观察结果，不声称删除旧数据
     * 未启用或权限不足时返回明确拒绝，不初始化空间或修改绑定。
     */
    public CompletionStage<String> changeNamespace(ITeaNekoMessageData data, @Nullable String target, @Nullable ExecutionControl parent) {
        return namespaceCommands.change(data, target, parent);
    }
    /**
     * 以完整客户端、scope、消息和操作定位派生稳定请求 ID，不从正文读取身份。
     * @param data 实际原生消息
     * @param action 固定操作
     * @return 稳定请求标识
     */
    private UUID operation(ITeaNekoMessageData data, String action) {
        return UUID.nameUUIDFromBytes(StableDigest.sha256(data.getClient().getClientId(), data.getScopeId(), data.getMessageId(), action).getBytes(StandardCharsets.UTF_8));
    }
    /**
     * 在实例拥有关系中按需创建一个默认定义，不因创建自动启用 scope。
     * @return 当前未关闭实例
     * @throws IllegalStateException 当命令入口已关闭时
     */
    private AgentHandle instance() {
        synchronized (chats) {
            if (closed) { throw new IllegalStateException("Agent 命令入口已关闭。"); }
            if (handle == null || handle.isClosed()) { handle = factory.create(new AgentDefinition("agent-command", new LLMModelOptionsSnapshot(null), Set.of(AgentActivity.values()))); }
            return handle;
        }
    }
    /**
     * 再读真实注册，未登记不能通过显式命令或管理写入开启业务。
     * @param scope 原作用域
     * @throws IllegalStateException 当作用域未登记或未启用时
     */
    private void requireEnabled(AgentScope scope) { if (!scopes.read(scope).enabled()) { throw new IllegalStateException("当前作用域 Agent 未登记或未启用。"); } }
    /**
     * 正式图文与模型选择的回复形式逐段等待，每段前观察原结果，拒绝迟到旧答案。
     * @param data 原发送目标
     * @param target 原实例
     * @param request 原请求
     * @param result 已完成的真实结果
     * @param control 2026/11/03：独立交付控制
     * @return 实际发送完成阶段
     */
    private CompletionStage<Void> deliver(ITeaNekoMessageData data, AgentHandle target, AgentRunRequest request, AgentRunResult result,
            @Nullable ExecutionControl control) {
        synchronized (chats) { if (closed) { return CompletableFuture.completedStage(null); } }
        requireEnabled(NativeCommandBridge.actor(data).scope());
        if (result.status() != AgentRunResult.Status.SUCCESS) { return reply(data, "Agent 请求结束：" + result.status()); }
        // 2026/11/03：冻结计划只复核不重解析，真实发送仍使用原入口检查。
        if (result.replyPlan() != null) {
            if (expressionDelivery == null || control == null) { return CompletableFuture.failedStage(new IllegalStateException("表达计划缺少实际交付装配。")); }
            return expressionDelivery.send(data, request, result, control, () -> agents.retry(target, request).thenAccept(checked -> {
                synchronized (chats) { if (closed) { throw new IllegalStateException("Agent 命令入口已关闭。"); } }
                if (checked.status() != AgentRunResult.Status.SUCCESS || !NativeCommandBridge.actor(data).equals(request.actor()) || !Objects.equals(checked.replyPlan(), result.replyPlan())) {
                    throw new IllegalStateException("表达回复的原结果或目标已失效。");
                }
            }));
        }
        var plan = AgentReplyPlan.from(result); if (plan.isEmpty()) { return CompletableFuture.completedStage(null); }
        CompletionStage<Void> chain = CompletableFuture.completedStage(null);
        int next = 0;
        for (var part : plan.get().parts()) {
            int index = next++;
            // 2026/11/03：真正发送前仍经原结果及目标复核，不因占用完成而放宽授权。
            java.util.function.Supplier<CompletionStage<Void>> sending = () -> agents.retry(target, request).thenCompose(checked -> {
                // 2026/11/03：旧协议同样响应已经生成后的发送取消。
                if (control != null) { control.checkActive(); }
                synchronized (chats) { if (closed) { throw new IllegalStateException("Agent 命令入口已关闭。"); } }
                if (!NativeCommandBridge.actor(data).equals(request.actor()) || !AgentReplyPlan.from(checked).equals(plan)) {
                    throw new IllegalStateException("显式回复的原结果或目标已失效。");
                }
                return AgentReplySender.send(data, part);
            });
            chain = chain.thenCompose(ignored -> delivery == null ? sending.get() : delivery.send(result.memoryReference(), request.actor(), request.requestId(),
                    index, plan.get().parts().size(), part, sending));
        }
        return chain;
    }
    /**
     * 通过原发送器和状态限制回复，不用内部客户端绕过平台开关。
     * @param data 原来源及目标
     * @param text 正文
     * @return 原发送完成信号
     */
    private CompletionStage<Void> reply(ITeaNekoMessageData data, String text) {
        try { return data.getMessageSender().sendReplyMessageAsync(text); }
        catch (RuntimeException failure) { return CompletableFuture.failedStage(failure); }
    }
    /** 关闭自有实例并解除注册，不关闭借用服务或其他调用方实例。 */
    @Override @PreDestroy public void close() {
        AgentHandle current; synchronized (chats) { closed = true; current = handle; handle = null; }
        // 2026/11/03：关闭入口同时解除自己持有的等待。
        if (coordinator != null) { coordinator.cancelOwner(replyOwner); }
        if (current != null) { registry.remove(current.id()); }
    }
    /**
     * 当前显式聊天的入口拥有关系，阶段包含最终发送，不进入 ECS 或持久化。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param actor 原真实来源
     * @param textDigest 原正文摘要
     * @param completion 整个聊天与发送的单次完成信号
     * @author zExNocs
     * @date 2026/11/01
     * @since 4.5.0-l
     * @version 4.5.0-l
     */
    private record Chat(AgentActorContext actor, String textDigest, CompletableFuture<Void> completion) {}
}
