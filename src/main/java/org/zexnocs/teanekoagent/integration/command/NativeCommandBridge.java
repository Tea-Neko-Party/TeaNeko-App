package org.zexnocs.teanekoagent.integration.command;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.component.AgentComponents;
import org.zexnocs.teanekoagent.component.llm.framework.tool.ToolInvocationContext;
import org.zexnocs.teanekoagent.component.memory.runtime.IAgentMemoryReferences;
import org.zexnocs.teanekoagent.component.personality.IAgentPersonalitySource;
import org.zexnocs.teanekoagent.component.tool.command.CommandInvocationReceipt;
import org.zexnocs.teanekoagent.component.tool.command.ICommandAttemptStore;
import org.zexnocs.teanekoagent.component.tool.command.ICommandInvocationPort;
import org.zexnocs.teanekoagent.integration.config.AgentScopeConfigData;
import org.zexnocs.teanekoagent.integration.config.AgentScopeConfigManager;
import org.zexnocs.teanekoagent.runtime.*;
import org.zexnocs.teanekoagent.runtime.access.AgentAccessGate;
import org.zexnocs.teanekoapp.command.TeaNekoCommandConverter;
import org.zexnocs.teanekoapp.command.TeaNekoCommandErrorHandler;
import org.zexnocs.teanekoapp.command.TeaNekoHelpSubCommandHandler;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekocore.command.interfaces.ICommandDispatcher;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataService;
import org.zexnocs.teanekocore.utils.StableDigest;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

/**
 * 在拥有者租约内保留原生消息，组件中只有真实请求数据，不序列化客户端或权限能力。
 * 防重占用提交后进入原分发链，异常或取消不假装回滚已启动业务。
 * <br>2026/11/02：向平台工具和资料适配器提供受请求租约保护的来源读取。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：命令工具同时遵守组件装配和原命令权限。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * <br>2026/11/05：通过明确的 getter 借用执行资源，保持异步完成后的原关闭责任。
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.5.0-l
 * @version 4.8.0-l
 */
@Service
public final class NativeCommandBridge implements ICommandInvocationPort {
    private final TeaNekoCommandConverter converter;
    private final ICommandDispatcher dispatcher;
    private final TeaNekoCommandErrorHandler errors;
    private final TeaNekoHelpSubCommandHandler help;
    private final ICommandAttemptStore attempts;
    private final Executor executor;
    private final AgentAccessGate gate;
    private final IAgentRegistry registry;
    private final IAgentMemoryReferences references;
    private final IAgentPersonalitySource personalities;
    private final IConfigDataService configs;
    private final AgentScopeConfigManager manager;
    private final Map<UUID, Lease> sources = new HashMap<>();
    /**
     * 组合原生分发及当前边界，不创建来源或执行命令。
     * @param converter 原文本及身份转换
     * @param dispatcher 原完整分发入口
     * @param errors 原生拒绝反馈
     * @param help 原生帮助反馈
     * @param attempts 持久化执行权
     * @param executor 借用业务执行器
     * @param gate 当前活动准入
     * @param registry 实际实例登记
     * @param references 当前空间复核
     * @param personalities 原人格复核
     * @param configs 实际作用域配置
     * @param manager Agent 注册声明
     */
    public NativeCommandBridge(TeaNekoCommandConverter converter, ICommandDispatcher dispatcher, TeaNekoCommandErrorHandler errors,
            TeaNekoHelpSubCommandHandler help, ICommandAttemptStore attempts, @Qualifier("virtualExecutor") Executor executor,
            AgentAccessGate gate, IAgentRegistry registry, IAgentMemoryReferences references, IAgentPersonalitySource personalities,
            IConfigDataService configs, AgentScopeConfigManager manager) {
        this.converter = converter; this.dispatcher = dispatcher; this.errors = errors; this.help = help; this.attempts = attempts; this.executor = executor;
        this.gate = gate; this.registry = registry; this.references = references; this.personalities = personalities; this.configs = configs; this.manager = manager;
    }
    /**
     * 可信宿主交接真实来源，租约必须保持到整个排队/执行阶段结束，容量不足明确失败。
     * @param request 尚未进入邮箱的原请求
     * @param data 实际原生消息对象
     * @return 不可序列化的来源拥有者
     * @throws IllegalArgumentException 当实际来源与请求不一致时
     * @throws IllegalStateException 当相同请求已绑定或来源容量已满时
     */
    public Lease open(AgentRunRequest request, ITeaNekoMessageData data) {
        if (!actor(data).equals(request.actor())) { throw new IllegalArgumentException("原生命令来源与请求不一致。"); }
        synchronized (sources) {
            if (sources.containsKey(request.requestId()) || sources.size() >= 1000) { throw new IllegalStateException("原生命令来源已登记或容量已满。"); }
            var lease = new Lease(request, data); sources.put(request.requestId(), lease); return lease;
        }
    }
    /**
     * 从真实原生消息形成数据身份，不允许模型指定用户、客户端或目标。
     * @param data 原消息
     * @return 固定发起者与作用域
     */
    public static AgentActorContext actor(ITeaNekoMessageData data) {
        return new AgentActorContext(new AgentScope(data.getClient().getClientId(), data.getScopeId()), AgentActorContext.Origin.USER,
                Optional.of(data.getUserData().getUuid()), data.getUserData().getUserIdInPlatform(), data.getMessageId(), data.getScopeId());
    }
    /**
     * 核对租约内原身份及原始文本，历史装配不会改变这两个来源数据。
     * @param request 本轮请求
     * @return 原来源仍被宿主持有时为真
     */
    @Override public boolean available(AgentRunRequest request) {
        final Lease lease;
        synchronized (sources) { lease = sources.get(request.requestId()); }
        return lease != null && !lease.closed && lease.request.actor().equals(request.actor()) && Objects.equals(lease.request.userText(), request.userText())
                && actor(lease.data).equals(request.actor());
    }

    /**
     * 2026/11/02：仅向宿主平台适配器借出当前仍有效的原生来源，不进入组件或模型 JSON。
     * @param request 已固定的真实请求，不能由模型替换来源身份
     * @return 该请求仍拥有来源租约时的原始消息
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public Optional<ITeaNekoMessageData> source(AgentRunRequest request) {
        synchronized (sources) {
            var lease = sources.get(request.requestId());
            return lease != null && available(request) ? Optional.of(lease.data) : Optional.empty();
        }
    }
    /**
     * 先取得持久化执行权，再复核当前边界并等待原命令实际完成；不回传任意业务对象。
     * 2026/11/05：通过明确的 getter 借用执行资源，保持异步完成后的原关闭责任。
     * @param request 原请求
     * @param callId 原工具调用标识
     * @param commandText 唯一业务文本
     * @param context 原类型化工具上下文
     * @return 明确原终态或不可自动重试状态
     */
    @Override public CompletionStage<CommandInvocationReceipt> invoke(AgentRunRequest request, String callId, String commandText, ToolInvocationContext context) {
        try {
            if (commandText == null || commandText.isBlank() || commandText.length() > 4096 || !available(request)) { return receipt(CommandInvocationReceipt.Status.UNAVAILABLE); }
            var actual = context.bindings().require(AgentComponents.REQUEST);
            if (!context.requestId().equals(request.requestId()) || !actual.requestId().equals(request.requestId()) || !actual.actor().equals(request.actor())
                    || !context.bindings().require(AgentComponents.ACTOR).equals(request.actor()) || !Objects.equals(actual.memoryReference(), request.memoryReference())
                    || !Objects.equals(actual.conversation(), request.conversation())) { return receipt(CommandInvocationReceipt.Status.UNAVAILABLE); }
            context.getControl().checkActive();
            var attempt = new ICommandAttemptStore.Attempt(request.requestId(), callId, request.actor(), Objects.requireNonNull(request.memoryReference()), StableDigest.sha256(commandText));
            return attempts.claim(attempt).thenComposeAsync(claim -> {
                if (!claim.acquired()) { return CompletableFuture.completedStage(claim.receipt()); }
                return check(request, context).thenComposeAsync(ignored -> {
                    final Lease lease; synchronized (sources) { lease = sources.get(request.requestId()); }
                    if (lease == null || !available(request)) { return receipt(CommandInvocationReceipt.Status.UNAVAILABLE); }
                    context.getControl().checkActive(); var data = converter.parseCommand(lease.data, commandText, context.getControl());
                    return data == null ? receipt(CommandInvocationReceipt.Status.INVALID_ARGUMENTS) : dispatcher.dispatch(data, errors, help)
                            .thenApply(result -> new CommandInvocationReceipt(CommandInvocationReceipt.Status.valueOf(result.status().name())));
                }, executor).handle((result, failure) -> failure == null ? result : new CommandInvocationReceipt(CommandInvocationReceipt.Status.FAILED))
                        .thenCompose(result -> attempts.finish(attempt, result));
            }, executor);
        } catch (RuntimeException invalid) { return receipt(CommandInvocationReceipt.Status.UNAVAILABLE); }
    }
    /**
     * 在占用提交后再次检查实际权限来源、开关、实例、空间及人格，不能沿用选择时的允许结果。
     * 2026/11/05：通过明确的 getter 借用执行资源，保持异步完成后的原关闭责任。
     * @param request 原请求
     * @param context 当前借用控制及组件
     * @return 当前仍可启动命令时完成
     */
    private CompletionStage<Void> check(AgentRunRequest request, ToolInvocationContext context) {
        try {
            requireSource(request, context); var scope = request.actor().scope();
            var basis = Objects.requireNonNull(context.bindings().require(AgentComponents.PROMPT_INPUT).personality());
            return context.getControl().guard(references.verify(Objects.requireNonNull(request.memoryReference())))
                    .thenComposeAsync(ignored -> personalities.verify(scope, basis, context.getControl()), executor)
                    .thenRunAsync(() -> requireSource(request, context), executor);
        } catch (RuntimeException failure) { return CompletableFuture.failedStage(failure); }
    }
    /**
     * 在异步引用检查前后重新读取真实开关、实例和原生来源，不把先前允许当作永久授权。
     * 2026/11/05：通过明确的 getter 借用执行资源，保持异步完成后的原关闭责任。
     * @param request 原请求
     * @param context 当前组件及借用控制
     * @throws IllegalStateException 当当前命令准入或原生来源不再有效时
     */
    private void requireSource(AgentRunRequest request, ToolInvocationContext context) {
        context.getControl().checkActive(); var scope = request.actor().scope();
        var enabled = configs.getConfigData(manager, AgentScopeConfigData.class, scope.scopeId())
                .map(data -> data.toPolicy().enabled() && data.isCommandToolsEnabled()).orElse(false);
        var handle = registry.find(context.bindings().require(AgentComponents.INSTANCE)).orElseThrow();
        if (!enabled || !available(request) || !gate.evaluate(handle, request.actor(), AgentActivity.COMMAND).allowed()) { throw new IllegalStateException("当前命令工具准入已失效。"); }
    }
    /**
     * 创建无副作用的固定回执。
     * @param status 固定状态
     * @return 已完成信号
     */
    private CompletionStage<CommandInvocationReceipt> receipt(CommandInvocationReceipt.Status status) { return CompletableFuture.completedStage(new CommandInvocationReceipt(status)); }
    /**
     * 原生来源拥有者，不保存到实体或数据库；关闭只移除自己的登记。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @author zExNocs
     * @date 2026/11/01
     * @since 4.5.0-l
     * @version 4.8.0-l
     */
    @JsonIgnoreType
    public final class Lease implements AutoCloseable {
        private final AgentRunRequest request;
        private final ITeaNekoMessageData data;
        private volatile boolean closed;
        /**
         * 保存可信宿主的当前来源，不对外暴露消息或客户端。
         * @param request 原请求
         * @param data 原实际消息
         */
        private Lease(AgentRunRequest request, ITeaNekoMessageData data) { this.request = request; this.data = data; }
        /** 解除自身登记；不会取消已开始命令或关闭原客户端。 */
        @Override public void close() { synchronized (sources) { closed = true; sources.remove(request.requestId(), this); } }
    }
}
