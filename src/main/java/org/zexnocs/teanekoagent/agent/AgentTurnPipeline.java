package org.zexnocs.teanekoagent.agent;

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.agent.access.AgentAccessGate;
import org.zexnocs.teanekoagent.agent.memory.IAgentMemoryReferences;
import org.zexnocs.teanekoagent.agent.personality.AgentPersonalitySnapshot;
import org.zexnocs.teanekoagent.agent.personality.IAgentPersonalitySource;
import org.zexnocs.teanekoagent.agent.prompt.AgentPromptException;
import org.zexnocs.teanekoagent.agent.prompt.AgentPromptInput;
import org.zexnocs.teanekoagent.agent.prompt.AgentPromptTranscript;
import org.zexnocs.teanekoagent.agent.prompt.IAgentPromptSource;
import org.zexnocs.teanekoagent.llm.framework.input.LLMOptionsPolicy;
import org.zexnocs.teanekoagent.llm.framework.input.LLMPrompt;
import org.zexnocs.teanekoagent.llm.framework.interaction.ILLMInteractionGuard;
import org.zexnocs.teanekoagent.llm.framework.interaction.ILLMInteractionService;
import org.zexnocs.teanekoagent.llm.framework.interaction.LLMInteractionResult;
import org.zexnocs.teanekoagent.llm.framework.message.LLMMessageCodec;
import org.zexnocs.teanekoagent.llm.framework.tool.LLMToolBinding;
import org.zexnocs.teanekoagent.llm.framework.tool.LLMToolSet;
import org.zexnocs.teanekoagent.llm.framework.tool.ToolInvocationContext;
import org.zexnocs.teanekocore.framework.composition.*;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Function;

/**
 * 预编译准入、临时输入、交互和交付步骤；业务检查及投影校验统一切入借用执行器。
 * 只通过专用轮次查找函数访问资源拥有者，不把服务或可执行绑定写入组件。
 *
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
final class AgentTurnPipeline {
    final SystemPipeline pipeline;
    private final Function<EntityId, AgentTurn> turns;
    private final AgentAccessGate gate;
    private final ILLMInteractionService interactions;
    private final IAgentToolSource tools;
    private final ObjectMapper mapper;
    private final Executor executor;
    private final @Nullable IAgentMemoryReferences references;
    private final IAgentPromptSource prompts;
    private final @Nullable IAgentPersonalitySource personalities;
    private final LLMMessageCodec codec;

    /**
     * 装配并校验稳定顺序，不在每条请求中重新编译或扫描 System。
     * @param turns 当前服务拥有的轮次查找端口
     * @param gate 统一门控
     * @param interactions 完整交互端口
     * @param tools 显式工具选择端口
     * @param mapper JSON 校验器
     * @param executor 借用业务执行器
     * @param references 可选持久化空间端口；为空时使用显式无记忆模式
     * @param prompts 可等待的本轮临时参考资料端口
     * @param personalities 可选独立人格端口
     */
    AgentTurnPipeline(Function<EntityId, AgentTurn> turns, AgentAccessGate gate,
            ILLMInteractionService interactions, IAgentToolSource tools, ObjectMapper mapper, Executor executor,
            @Nullable IAgentMemoryReferences references, IAgentPromptSource prompts, @Nullable IAgentPersonalitySource personalities) {
        this.turns = turns; this.gate = gate; this.interactions = interactions;
        this.tools = tools; this.mapper = mapper; this.executor = executor;
        this.references = references;
        this.prompts = prompts; this.personalities = personalities; codec = new LLMMessageCodec(mapper);
        pipeline = SystemPipeline.compile(List.of(
                new Step(new SystemDescriptor("agent.admit", Set.of(AgentComponents.REQUEST, AgentComponents.RUNTIME,
                        AgentComponents.SCOPE_POLICY), Set.of(), Set.of()), this::admit),
                new Step(new SystemDescriptor("agent.prompt", Set.of(AgentComponents.REQUEST, AgentComponents.RUNTIME),
                        Set.of(AgentComponents.PROMPT_INPUT), Set.of("agent.admit")), this::preparePrompt),
                new Step(new SystemDescriptor("agent.interact", Set.of(AgentComponents.REQUEST, AgentComponents.RUNTIME,
                        AgentComponents.ACTOR, AgentComponents.ACTIVITY, AgentComponents.INSTANCE, AgentComponents.DEFINITION, AgentComponents.PROMPT_INPUT),
                        Set.of(AgentComponents.INTERACTION, AgentComponents.PROMPT_TRANSCRIPT), Set.of("agent.prompt")), this::interact),
                new Step(new SystemDescriptor("agent.complete", Set.of(AgentComponents.INTERACTION),
                        Set.of(AgentComponents.OUTCOME), Set.of("agent.interact")), this::complete)));
    }

    /**
     * 排队后复核准入，准备阶段得到的允许结果不能充当永久授权。
     * 本步骤借用控制和轮次，统一由 AgentService 在整轮异步完成后关闭。
     * @param context 管线不可变快照及本轮控制
     * @return 继续或停止的步骤决定
     */
    private CompletionStage<SystemResult> admit(SystemContext context) {
        return CompletableFuture.supplyAsync(() -> {
            context.control().checkActive();
            var turn = turns.apply(context.snapshot().id());
            if (!turn.check(gate, turn.request.activity())) { return CompletableFuture.completedStage(SystemResult.stop(ComponentPatch.empty())); }
            return turn.verifyMemory(references).thenApply(ignored -> SystemResult.continueWith(ComponentPatch.empty()));
        }, executor).thenCompose(Function.identity());
    }

    /**
     * 在原总控制内等待临时资料，切回业务域度量完整输入并复核来源，不创建额外模型预算。
     * @param context 可信组件与借用控制
     * @return 只包含不可变输入布局的补丁
     */
    private CompletionStage<SystemResult> preparePrompt(SystemContext context) {
        var turn = turns.apply(context.snapshot().id());
        try {
            return CompletableFuture.supplyAsync(() -> {
                turn.guard(gate, false);
                return personalities == null ? CompletableFuture.<AgentPersonalitySnapshot>completedStage(null)
                        : context.control().guard(personalities.resolve(turn.request.actor().scope(), context.control()));
            }, executor).thenCompose(Function.identity()).thenComposeAsync(personality -> {
                context.control().checkActive(); turn.guard(gate, false); var request = fixedRequest(context.snapshot());
                var expected = request.personalityIdentity();
                if (expected != null && (personality == null || !expected.equals(personality.identity()))) {
                    throw new AgentPromptException(AgentPromptException.Reason.CHANGED);
                }
                return context.control().guard(Objects.requireNonNull(prompts.resolve(turn.handle.definition(), request,
                        context.snapshot().require(AgentComponents.RUNTIME), context.control()), "Prompt 资料端口不能返回空阶段。")
                        .thenApply(values -> {
                            if (values == null || values.size() > 32) { throw new IllegalArgumentException("Prompt 资料列表范围无效。"); }
                            return List.copyOf(values);
                        })).thenComposeAsync(fragments -> {
                context.control().checkActive(); turn.guard(gate, false);
                var input = AgentPromptInput.bounded(request.messages(), fragments, request.inputMaxBytes(), codec, personality);
                return verifyPrompt(turn, request, input).thenApplyAsync(ignored -> {
                    turn.guard(gate, false);
                    return SystemResult.continueWith(ComponentPatch.builder().put(AgentComponents.PROMPT_INPUT, input).build());
                }, executor);
                }, executor);
            }, executor).exceptionallyCompose(turn::rejectContext);
        } catch (RuntimeException failure) { return turn.rejectContext(failure); }
    }

    /**
     * 使用真实已解析引用和原固定字节预算向能力端口交接请求，不保留无记忆模式的外部引用提示。
     * @param snapshot 本轮组件快照
     * @return 原身份和原文保持不变的固定请求
     */
    private static AgentRunRequest fixedRequest(EntitySnapshot snapshot) {
        var request = snapshot.require(AgentComponents.REQUEST);
        var reference = snapshot.find(AgentComponents.MEMORY_REFERENCE).orElse(null);
        return Objects.equals(reference, request.memoryReference()) ? request : new AgentRunRequest(request.requestId(), request.actor(), request.activity(),
                request.messages(), request.conversation(), reference, request.userText(), request.inputMaxBytes(), request.personalityIdentity());
    }

    /**
     * 在业务执行域选择工具并调用既有有限交互服务，不复制模型/工具循环。
     * @param context 本轮固定组件和实际控制
     * @return 实际交互结束后才完成的补丁
     */
    private CompletionStage<SystemResult> interact(SystemContext context) {
        return CompletableFuture.supplyAsync(() -> startInteraction(context), executor).thenCompose(Function.identity());
    }

    /**
     * 固定工具及可信上下文，逐次模型与实际工具执行均复核动态门控。
     * 控制和轮次由 AgentService 拥有，工具与完成回调仍需使用，不能在本方法返回时关闭。
     * @param context 本轮组件快照，模型不能覆盖其中的发起者和目标
     * @return 保留诊断并提交交互组件的阶段
     * @throws RuntimeException 当工具选择、宿主准入或端口非空合同校验失败时
     */
    private CompletionStage<SystemResult> startInteraction(SystemContext context) {
        context.control().checkActive();
        var turn = turns.apply(context.snapshot().id());
        var request = context.snapshot().require(AgentComponents.REQUEST);
        var definition = context.snapshot().require(AgentComponents.DEFINITION);
        var runtime = context.snapshot().require(AgentComponents.RUNTIME);
        var input = context.snapshot().require(AgentComponents.PROMPT_INPUT);
        var toolRequest = fixedRequest(context.snapshot());
        List<LLMToolBinding> selected = definition.activities().contains(AgentActivity.TOOL)
                ? List.copyOf(tools.select(definition, toolRequest, runtime)) : List.of();
        boolean guarded = references != null || !input.fragments().isEmpty() || input.personality() != null;
        var bound = selected.stream().map(binding -> !guarded
                ? binding.guarded((call, trusted) -> turn.guard(gate, true))
                : binding.guardedAsync((call, trusted) -> guard(turn, true), executor)).toList();
        var toolSet = new LLMToolSet(mapper, bound);
        turn.guard(gate, false);
        var prompt = new LLMPrompt(input.messages(), runtime.modelOptions().toOptions(), LLMOptionsPolicy.RESOLVED);
        var trusted = new ToolInvocationContext(request.requestId(), context.snapshot(), context.control());
        turn.enteringInteraction();
        try {
            return Objects.requireNonNull(interactions.interact(prompt, toolSet, trusted, runtime.interaction(),
                    !guarded ? modelContext -> turn.guard(gate, false)
                            : ILLMInteractionGuard.asynchronous(modelContext -> guard(turn, false))), "交互端口返回的阶段不能为空。")
                    .thenApply(result -> {
                        turn.observe(result, null); return result;
                    }).thenApplyAsync(result -> {
                        var patch = ComponentPatch.builder().put(AgentComponents.INTERACTION, result);
                        if (result.status() == LLMInteractionResult.Status.SUCCESS && (!input.fragments().isEmpty() || input.omittedFragments() > 0 || input.personality() != null)) {
                            var projection = AgentPromptTranscript.create(input, result, codec); turn.observe(result, projection);
                            patch.put(AgentComponents.PROMPT_TRANSCRIPT, projection);
                        }
                        return SystemResult.continueWith(patch.build());
                    }, executor);
        } finally {
            turn.attachedInteraction();
        }
    }

    /**
     * 切回业务执行器复核交付；仅写本轮结果，不持久化历史或发送平台消息。
     * 本步骤只借用控制和轮次，外层异步完成链负责统一回收。
     * @param context 已观察交互的组件快照
     * @return 完成结果补丁
     */
    private CompletionStage<SystemResult> complete(SystemContext context) {
        return CompletableFuture.supplyAsync(() -> {
            context.control().checkActive();
            var turn = turns.apply(context.snapshot().id());
            return guard(turn, false).thenApply(ignored -> SystemResult.continueWith(
                    ComponentPatch.builder().put(AgentComponents.OUTCOME, turn.result(null)).build()));
        }, executor).thenCompose(Function.identity());
    }

    /**
     * 等待引用复核后切回业务域再检查门控，失败不会刷新本轮引用或继续副作用。
     * @param turn 当前资源拥有者
     * @param tool 是否需要额外工具活动检查
     * @return 动态门控及空间引用都成立时完成
     */
    private CompletionStage<Void> guard(AgentTurn turn, boolean tool) {
        try {
            turn.guard(gate, tool);
            var input = turn.entity.snapshot().find(AgentComponents.PROMPT_INPUT).orElse(null);
            return turn.verifyMemory(references).thenComposeAsync(ignored -> input == null ? CompletableFuture.completedStage(null)
                    : verifyPrompt(turn, fixedRequest(turn.entity.snapshot()), input), executor)
                    .thenRunAsync(() -> turn.guard(gate, tool), executor);
        } catch (RuntimeException failure) { return CompletableFuture.failedStage(failure); }
    }

    /**
     * 只复核实际保留的临时资料，全部省略或没有资料时不额外限制普通对话；仅借用轮次控制。
     * @param turn 本轮资源拥有者
     * @param request 本轮固定请求
     * @param input 已固定输入布局
     * @return 来源仍有效的信号，失败不替换成新的资料
     */
    private CompletionStage<Void> verifyPrompt(AgentTurn turn, AgentRunRequest request, AgentPromptInput input) {
        try {
            var personality = input.personality();
            CompletionStage<Void> checked = personality == null ? CompletableFuture.completedStage(null)
                    : Objects.requireNonNull(personalities, "实际人格源不能为空。").verify(request.actor().scope(), personality, turn.control());
            return turn.control().guard(checked.thenComposeAsync(ignored -> input.fragments().isEmpty() ? CompletableFuture.completedStage(null)
                    : Objects.requireNonNull(prompts.verify(request, input, turn.control()), "Prompt 来源复核不能返回空阶段。"), executor))
                .exceptionallyCompose(turn::rejectContext); }
        catch (RuntimeException failure) { return turn.rejectContext(failure); }
    }

    /**
     * 固定步骤声明和显式操作，执行依赖仍由组合根构造。
     * @param descriptor 数据依赖和写入范围
     * @param action 单个可等待步骤
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
     * @version 4.5.0-l
     */
    private record Step(SystemDescriptor descriptor, Function<SystemContext, CompletionStage<SystemResult>> action)
            implements IEntitySystem {
        /**
         * 将只读上下文交给当前显式步骤，不直接写实体。
         * @param context 当前快照及执行控制
         * @return 由 Core 校验并提交的步骤结果
         */
        @Override public CompletionStage<SystemResult> execute(SystemContext context) { return action.apply(context); }
    }
}
