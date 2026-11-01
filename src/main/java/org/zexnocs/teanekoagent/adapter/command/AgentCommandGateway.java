package org.zexnocs.teanekoagent.adapter.command;

import jakarta.annotation.PreDestroy;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.agent.*;
import org.zexnocs.teanekoagent.agent.access.IAgentScopePolicyPort;
import org.zexnocs.teanekoagent.agent.command.CommandInvocationReceipt;
import org.zexnocs.teanekoagent.agent.command.ICommandAttemptStore;
import org.zexnocs.teanekoagent.agent.conversation.IAgentConversationControl;
import org.zexnocs.teanekoagent.agent.memory.IAgentMemoryReferences;
import org.zexnocs.teanekoagent.llm.framework.message.LLMUserMessage;
import org.zexnocs.teanekoagent.llm.framework.message.content.LLMContentListBuilder;
import org.zexnocs.teanekoagent.llm.framework.message.content.TextLLMContentPart;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMContent;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptionsSnapshot;
import org.zexnocs.teanekoagent.memory.namespace.IMemoryNamespaceDefaults;
import org.zexnocs.teanekoagent.memory.namespace.MemoryNamespaceBindings;
import org.zexnocs.teanekoagent.memory.namespace.MemoryNamespaceId;
import org.zexnocs.teanekoagent.memory.namespace.MemoryNamespaceRegistry;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.actuator.execution.ResourceBudget;
import org.zexnocs.teanekocore.command.api.CommandPermission;
import org.zexnocs.teanekocore.utils.StableDigest;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;

/**
 * 原生命令的应用边界，按需拥有一个共享定义实例，原生来源仅在请求生命周期内绑定。
 * 不自动监听平台消息，不接管旧 Client，不恢复未完成执行或任意跨 scope 原文。
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.0.0
 * @version 4.5.0-l
 */
@Service
public final class AgentCommandGateway implements AutoCloseable {
    private final IAgentFactory factory;
    private final IAgentRegistry registry;
    private final IAgentService agents;
    private final IAgentConversationControl conversations;
    private final IAgentScopePolicyPort scopes;
    private final NativeCommandBridge nativeCommands;
    private final IAgentMemoryReferences references;
    private final MemoryNamespaceBindings bindings;
    private final MemoryNamespaceRegistry namespaces;
    private final IMemoryNamespaceDefaults defaults;
    private final ScheduledExecutorService scheduler;
    private final ICommandAttemptStore attempts;
    private final Map<UUID, Chat> chats = new LinkedHashMap<>();
    private @Nullable AgentHandle handle;
    private boolean closed;
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
        this.factory = factory; this.registry = registry; this.agents = agents; this.conversations = conversations; this.scopes = scopes;
        this.nativeCommands = nativeCommands; this.references = references; this.bindings = bindings; this.namespaces = namespaces;
        this.defaults = defaults; this.scheduler = scheduler; this.attempts = attempts;
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
        try {
            var actor = NativeCommandBridge.actor(data); requireEnabled(actor.scope());
            if (text.isBlank() || text.length() > 65536) { return reply(data, "请输入一至65536字符的对话文本。"); }
            var id = operation(data, parent == null ? "chat" : "chat-child:" + parent.id()); var target = instance();
            var request = new AgentRunRequest(id, actor, AgentActivity.COMMAND, List.of(LLMUserMessage.builder().contents(new LLMContentListBuilder().addText(text).build()).build()),
                    null, null, parent == null ? new AgentUserText(actor.sourceId(), text) : null);
            if (parent != null) { return agents.run(target, request, parent).thenCompose(result -> deliver(data, result)); }
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
            try { started = startChat(data, request, target); } catch (RuntimeException failure) { started = CompletableFuture.failedStage(failure); }
            started.whenComplete((ignored, failure) -> {
                if (failure == null) { chat.completion().complete(null); } else { chat.completion().completeExceptionally(failure); }
            });
            return chat.completion().minimalCompletionStage();
        } catch (RuntimeException invalid) { return reply(data, "Agent 当前不可执行，请检查注册、配置和运行状态。"); }
    }
    /**
     * 先提交原生消息的唯一准入，重启后不恢复旧模型循环或以新工具 ID 重跑同一消息。
     * @param data 原发送来源
     * @param request 宿主固定请求
     * @param target 当前实例
     * @return 包含生成、来源释放、交付复核和发送的完整阶段
     */
    private CompletionStage<Void> startChat(ITeaNekoMessageData data, AgentRunRequest request, AgentHandle target) {
        return references.resolve(request.actor().scope()).thenCompose(reference -> {
            requireEnabled(request.actor().scope());
            var attempt = new ICommandAttemptStore.Attempt(request.requestId(), "entry", request.actor(), reference,
                    StableDigest.sha256(Objects.requireNonNull(request.userText()).text()), ICommandAttemptStore.Kind.CHAT_ENTRY);
            return attempts.claim(attempt).thenCompose(claim -> {
                if (!claim.acquired()) { return reply(data, "该消息已有处理记录（" + claim.receipt().status() + "），不会重新执行模型或命令；如需新操作请发送新消息。"); }
                CompletionStage<Void> execution;
                NativeCommandBridge.Lease lease = null;
                try {
                    requireEnabled(request.actor().scope());
                    synchronized (chats) { if (closed) { throw new IllegalStateException("Agent 命令入口已关闭。"); } }
                    // 来源租约交给异步完成链释放，包含排队和完整模型/工具往返。
                    lease = nativeCommands.open(request, data); var owner = lease;
                    execution = agents.run(target, request).whenComplete((result, failure) -> owner.close())
                            .thenCompose(result -> result.status() == AgentRunResult.Status.SUCCESS ? agents.retry(target, request) : CompletableFuture.completedStage(result))
                            .thenCompose(result -> deliver(data, result));
                } catch (RuntimeException failure) {
                    if (lease != null) { lease.close(); } execution = CompletableFuture.failedStage(failure);
                }
                return execution.handle((ignored, failure) -> attempts.finish(attempt, new CommandInvocationReceipt(failure == null
                        ? CommandInvocationReceipt.Status.COMPLETED : CommandInvocationReceipt.Status.FAILED)).handle((receipt, writeFailure) -> {
                    if (failure != null) { throw new CompletionException(failure); }
                    if (writeFailure != null) { throw new CompletionException(writeFailure); }
                    return (Void) null;
                })).thenCompose(stage -> stage);
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
        if (current == null) { return "没有在途请求。"; }
        return "停止请求：" + agents.cancel(current, actor.scope(), UUID.fromString(requestId), actor.userId().orElseThrow());
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
     * @return 真实提交及同步观察结果，不声称删除旧数据
     * @throws IllegalStateException 当作用域未启用时
     */
    public CompletionStage<String> changeNamespace(ITeaNekoMessageData data, @Nullable String target, @Nullable ExecutionControl parent) {
        var actor = NativeCommandBridge.actor(data); requireEnabled(actor.scope());
        if (data.getUserData().getRole().getLevel() > CommandPermission.OWNER.getLevel()) { return CompletableFuture.completedStage("只有当前作用域 OWNER 或 DEBUG 可以切换空间。"); }
        var control = parent == null ? ExecutionControl.withDeadline(new ResourceBudget(Map.of()), Clock.systemUTC().instant().plusSeconds(30), Clock.systemUTC(), scheduler) : parent;
        try {
            var id = operation(data, target == null ? "reset" : "setnamespace");
            var work = control.guard(references.resolve(actor.scope())).thenCompose(reference -> {
                control.checkActive(); requireEnabled(actor.scope());
                return control.guard(target == null ? bindings.createAndBind(id, actor, reference.bindingRevision())
                        : bindings.bindExisting(id, actor, new MemoryNamespaceId(target), reference.bindingRevision()));
            }).thenCompose(outcome -> {
                control.checkActive();
                if (outcome.reference() == null) { return CompletableFuture.completedStage("空间操作未提交：" + outcome.status() + "；原数据保留。"); }
                return conversations.synchronizeMemory(actor.scope(), control).handle((count, failure) -> failure == null
                        ? "空间绑定已提交并同步会话；请使用 /agent status 查看当前空间。旧空间数据保留。"
                        : "空间绑定已提交，同步观察未完成；请使用 /agent status 核对当前空间。旧空间数据保留。");
            });
            return control.guard(work).whenComplete((ignored, failure) -> { if (parent == null) { control.close(); } });
        } catch (RuntimeException failure) { if (parent == null) { control.close(); } return CompletableFuture.failedStage(failure); }
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
     * 只提取正式文本候选，供应商推理状态不会作为回复输出。
     * @param data 原发送目标
     * @param result 已完成的真实结果
     * @return 实际发送完成阶段
     */
    private CompletionStage<Void> deliver(ITeaNekoMessageData data, AgentRunResult result) {
        synchronized (chats) { if (closed) { return CompletableFuture.completedStage(null); } }
        requireEnabled(NativeCommandBridge.actor(data).scope());
        var answer = result.answer().map(message -> message.getContents().stream().map(ILLMContent::getContentPart).filter(TextLLMContentPart.class::isInstance)
                .map(TextLLMContentPart.class::cast).map(TextLLMContentPart::getText).collect(Collectors.joining("\n"))).orElse("Agent 请求结束：" + result.status());
        return reply(data, answer);
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
        if (current != null) { registry.remove(current.id()); }
    }
    /**
     * 当前显式聊天的入口拥有关系，阶段包含最终发送，不进入 ECS 或持久化。
     * @param actor 原真实来源
     * @param textDigest 原正文摘要
     * @param completion 整个聊天与发送的单次完成信号
     * @author zExNocs
     * @date 2026/11/01
     * @since 4.0.0
     * @version 4.5.0-l
     */
    private record Chat(AgentActorContext actor, String textDigest, CompletableFuture<Void> completion) {}
}
