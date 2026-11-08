package org.zexnocs.teanekoplugin.agent.automation.integration.command;

import org.zexnocs.teanekoagent.integration.config.AgentComponentAccess;

// 2026/11/07：按真实职责分离通用来源、防重底座与插件业务，持久标识保持不变。
// 2026/11/07：认知业务契约归入插件，纯会话数据沿用运行底座。

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

// 2026/11/07：按插件归属迁移业务实现，保留底层契约、持久标识和事务边界。

import org.zexnocs.teanekoagent.runtime.AgentActivity;
import org.zexnocs.teanekoagent.runtime.AgentRunRequest;
import org.zexnocs.teanekoagent.runtime.IAgentRegistry;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.zexnocs.teanekoagent.integration.message.NativeMessageSources;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.component.AgentComponents;
import org.zexnocs.teanekoagent.llm.framework.tool.ToolInvocationContext;
import org.zexnocs.teanekoagent.runtime.context.IAgentContextReferences;
import org.zexnocs.teanekoagent.runtime.context.IAgentPromptContexts;
import org.zexnocs.teanekoagent.runtime.operation.AgentOperationReceipt;
import org.zexnocs.teanekoagent.runtime.operation.IAgentOperationStore;
import org.zexnocs.teanekoplugin.agent.automation.command.ICommandInvocationPort;
import org.zexnocs.teanekoagent.runtime.access.AgentAccessGate;
import org.zexnocs.teanekoapp.command.TeaNekoCommandConverter;
import org.zexnocs.teanekoapp.command.TeaNekoCommandErrorHandler;
import org.zexnocs.teanekoapp.command.TeaNekoHelpSubCommandHandler;
import org.zexnocs.teanekocore.command.interfaces.ICommandDispatcher;
import org.zexnocs.teanekocore.utils.StableDigest;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

/**
 * 在宿主原生来源租约内执行命令工具，不序列化客户端或权限能力。
 * 防重占用提交后进入原分发链，异常或取消不假装回滚已启动业务。
 * <br>2026/11/02：向平台工具和资料适配器提供受请求租约保护的来源读取。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：命令工具同时遵守组件装配和原命令权限。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * <br>2026/11/05：通过明确的 getter 借用执行资源，保持异步完成后的原关闭责任。
 * <br>2026/11/07：命令适配归自动化插件，通用来源租约和防重由宿主独立维护。
 * <br>2026/11/07：业务归属与通用底座按职责分离，保留持久身份及完成边界。
 *
 * <br>2026/11/07：业务参数由所属组件解释，实时复核装配与插件资格。
 *
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.5.0-l
 * @version 4.9.0-l
 */
@Service
@org.zexnocs.teanekocore.plugin.PluginOwned("agent.automation")
public final class NativeCommandBridge implements ICommandInvocationPort {
    private final TeaNekoCommandConverter converter;
    private final ICommandDispatcher dispatcher;
    private final TeaNekoCommandErrorHandler errors;
    private final TeaNekoHelpSubCommandHandler help;
    private final IAgentOperationStore attempts;
    private final Executor executor;
    private final AgentAccessGate gate;
    private final IAgentRegistry registry;
    private final IAgentContextReferences references;
    private final IAgentPromptContexts personalities;
    private final AgentComponentAccess components;
    private final NativeMessageSources sources;
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
     * @param components 当前有效组件参数
     * @param sources 宿主持有的原生来源租约
     */
    public NativeCommandBridge(TeaNekoCommandConverter converter, ICommandDispatcher dispatcher, TeaNekoCommandErrorHandler errors,
            TeaNekoHelpSubCommandHandler help, IAgentOperationStore attempts, @Qualifier("virtualExecutor") Executor executor,
            AgentAccessGate gate, IAgentRegistry registry, IAgentContextReferences references, IAgentPromptContexts personalities,
            AgentComponentAccess components, NativeMessageSources sources) {
        this.converter = converter;
        this.dispatcher = dispatcher;
        this.errors = errors;
        this.help = help;
        this.attempts = attempts;
        this.executor = executor;
        this.gate = gate;
        this.registry = registry;
        this.references = references;
        this.personalities = personalities;
        this.components = components;
        this.sources = sources;
    }
    /**
     * 2026/11/07：命令插件借用宿主原生来源，不拥有聊天租约。
     * @param request 当前真实请求
     * @return 宿主仍持有可信来源时为真
     */
    @Override
    public boolean available(AgentRunRequest request) {
        return sources.available(request);
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
    @Override public CompletionStage<AgentOperationReceipt> invoke(AgentRunRequest request, String callId, String commandText, ToolInvocationContext context) {
        try {
            if (commandText == null || commandText.isBlank() || commandText.length() > 4096 || !available(request)) {
                return receipt(AgentOperationReceipt.Status.UNAVAILABLE);
            }
            var actual = context.bindings().require(AgentComponents.REQUEST);
            if (!context.requestId().equals(request.requestId()) || !actual.requestId().equals(request.requestId()) || !actual.actor().equals(request.actor())
                    || !context.bindings().require(AgentComponents.ACTOR).equals(request.actor()) || !Objects.equals(actual.memoryReference(), request.memoryReference())
                    || !Objects.equals(actual.conversation(), request.conversation())) {
                        return receipt(AgentOperationReceipt.Status.UNAVAILABLE);
                    }
            context.getControl().checkActive();
            var attempt = new IAgentOperationStore.Attempt(request.requestId(), callId, request.actor(), Objects.requireNonNull(request.memoryReference()), StableDigest.sha256(commandText));
            return attempts.claim(attempt).thenComposeAsync(claim -> {
                if (!claim.acquired()) {
                    return CompletableFuture.completedStage(claim.receipt());
                }
                return check(request, context).thenComposeAsync(ignored -> {
                    var source = sources.source(request).orElse(null);
                    if (source == null || !available(request)) {
                        return receipt(AgentOperationReceipt.Status.UNAVAILABLE);
                    }
                    context.getControl().checkActive();
                    var data = converter.parseCommand(source, commandText, context.getControl());
                    return data == null ? receipt(AgentOperationReceipt.Status.INVALID_ARGUMENTS) : dispatcher.dispatch(data, errors, help)
                            .thenApply(result -> new AgentOperationReceipt(AgentOperationReceipt.Status.valueOf(result.status().name())));
                }, executor).handle((result, failure) -> failure == null ? result : new AgentOperationReceipt(AgentOperationReceipt.Status.FAILED))
                        .thenCompose(result -> attempts.finish(attempt, result));
            }, executor);
        } catch (RuntimeException invalid) {
            return receipt(AgentOperationReceipt.Status.UNAVAILABLE);
        }
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
            requireSource(request, context);
            var scope = request.actor().scope();
            var basis = Objects.requireNonNull(context.bindings().require(AgentComponents.PROMPT_INPUT).personality());
            return context.getControl().guard(references.verify(Objects.requireNonNull(request.memoryReference())))
                    .thenComposeAsync(ignored -> personalities.verify(scope, basis, context.getControl()), executor)
                    .thenRunAsync(() -> requireSource(request, context), executor);
        } catch (RuntimeException failure) {
            return CompletableFuture.failedStage(failure);
        }
    }
    /**
     * 在异步引用检查前后重新读取真实开关、实例和原生来源，不把先前允许当作永久授权。
     * 2026/11/05：通过明确的 getter 借用执行资源，保持异步完成后的原关闭责任。
     * @param request 原请求
     * @param context 当前组件及借用控制
     * @throws IllegalStateException 当当前命令准入或原生来源不再有效时
     */
    private void requireSource(AgentRunRequest request, ToolInvocationContext context) {
        context.getControl().checkActive();
        var scope = request.actor().scope();
        var enabled = components.settings(scope, "command")
                .map(data -> data.path("commandToolsEnabled").asBoolean(false)).orElse(false);
        var handle = registry.find(context.bindings().require(AgentComponents.INSTANCE)).orElseThrow();
        if (!enabled || !available(request) || !gate.evaluate(handle, request.actor(), AgentActivity.COMMAND).allowed()) {
            throw new IllegalStateException("当前命令工具准入已失效。");
        }
    }
    /**
     * 创建无副作用的固定回执。
     * @param status 固定状态
     * @return 已完成信号
     */
    private CompletionStage<AgentOperationReceipt> receipt(AgentOperationReceipt.Status status) {
        return CompletableFuture.completedStage(new AgentOperationReceipt(status));
    }
}
