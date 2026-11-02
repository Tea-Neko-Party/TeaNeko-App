package org.zexnocs.teanekoagent.agent;

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.agent.access.AgentAccessGate;
import org.zexnocs.teanekoagent.agent.history.IAgentHistoryRecorder;
import org.zexnocs.teanekoagent.agent.memory.IAgentMemoryReferences;
import org.zexnocs.teanekoagent.agent.personality.AgentPersonalitySnapshot;
import org.zexnocs.teanekoagent.agent.personality.IAgentPersonalitySource;
import org.zexnocs.teanekoagent.agent.prompt.AgentPromptException;
import org.zexnocs.teanekoagent.agent.prompt.AgentPromptInput;
import org.zexnocs.teanekoagent.agent.prompt.AgentPromptTranscript;
import org.zexnocs.teanekoagent.agent.prompt.IAgentPromptSource;
import org.zexnocs.teanekoagent.agent.summary.IAgentConversationSummaries;
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
 * <br>2026/11/02：在准入之后及交互之后运行受控插件 System，等待决策与异步环绕。
 * <br>2026/11/02：统一按可信 client 过滤工具并在实际执行时重新校验。
 * <br>2026/11/02：携带工具图像的交互强制使用历史投影，避免伪装成用户新消息。
 *
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.0.0
 * @version 4.6.0-l
 */
final class AgentTurnPipeline {
    SystemPipeline pipeline;
    // 2026/11/02：插件仅在服务开始接受轮次前安装，运行期目录及步骤不再变化。
    private org.zexnocs.teanekoagent.plugin.AgentPluginRuntime plugins;
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
    private final @Nullable IAgentHistoryRecorder history;
    private final @Nullable IAgentConversationSummaries summaries;

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
     * @param history 可选历史记录端口
     * @param summaries 可选摘要使用复核
     */
    AgentTurnPipeline(Function<EntityId, AgentTurn> turns, AgentAccessGate gate,
            ILLMInteractionService interactions, IAgentToolSource tools, ObjectMapper mapper, Executor executor,
            @Nullable IAgentMemoryReferences references, IAgentPromptSource prompts, @Nullable IAgentPersonalitySource personalities,
            @Nullable IAgentHistoryRecorder history, @Nullable IAgentConversationSummaries summaries) {
        this.turns = turns; this.gate = gate; this.interactions = interactions;
        this.tools = tools; this.mapper = mapper; this.executor = executor;
        this.references = references;
        this.prompts = prompts; this.personalities = personalities; this.history = history; this.summaries = summaries; codec = new LLMMessageCodec(mapper);
        // 2026/11/02：独立构造默认仍编译原四步，宿主安装插件后重新编译一次。
        pipeline = compile();
    }

    /**
     * 安装能力目录并编译明确的扩展位置，不替换核心步骤。
     * @param plugins 插件组合器
     */
    void installPlugins(org.zexnocs.teanekoagent.plugin.AgentPluginRuntime plugins) {
        this.plugins = Objects.requireNonNull(plugins); pipeline = compile();
    }

    /**
     * 编译稳定宿主步骤及插件数据写入范围。
     * @return 已校验顺序的管线
     */
    private SystemPipeline compile() {
        var steps = new java.util.ArrayList<IEntitySystem>();
        if (plugins != null) {
            var writes = new java.util.HashSet<>(plugins.componentKeys()); writes.add(org.zexnocs.teanekoagent.plugin.AgentPluginRuntime.SELECTION);
            steps.add(new Step(new SystemDescriptor("agent.plugins.prepare", Set.of(AgentComponents.REQUEST, AgentComponents.RUNTIME),
                    writes, Set.of("agent.admit")), context -> pluginStep(context, org.zexnocs.teanekoagent.plugin.AgentFeature.Phase.BEFORE_PROMPT)));
            steps.add(new Step(new SystemDescriptor("agent.plugins.review", Set.of(AgentComponents.INTERACTION),
                    writes, Set.of("agent.interact")), context -> pluginStep(context, org.zexnocs.teanekoagent.plugin.AgentFeature.Phase.AFTER_INTERACTION)));
        }
        steps.addAll(List.of(
                new Step(new SystemDescriptor("agent.admit", Set.of(AgentComponents.REQUEST, AgentComponents.RUNTIME,
                        AgentComponents.SCOPE_POLICY), Set.of(), Set.of()), this::admit),
                new Step(new SystemDescriptor("agent.prompt", Set.of(AgentComponents.REQUEST, AgentComponents.RUNTIME),
                        Set.of(AgentComponents.PROMPT_INPUT), Set.of(plugins == null ? "agent.admit" : "agent.plugins.prepare")), this::preparePrompt),
                new Step(new SystemDescriptor("agent.interact", Set.of(AgentComponents.REQUEST, AgentComponents.RUNTIME,
                        AgentComponents.ACTOR, AgentComponents.ACTIVITY, AgentComponents.INSTANCE, AgentComponents.DEFINITION, AgentComponents.PROMPT_INPUT),
                        Set.of(AgentComponents.INTERACTION, AgentComponents.PROMPT_TRANSCRIPT), Set.of("agent.prompt")), this::interact),
                new Step(new SystemDescriptor("agent.complete", Set.of(AgentComponents.INTERACTION),
                        Set.of(AgentComponents.OUTCOME), Set.of(plugins == null ? "agent.interact" : "agent.plugins.review")), this::complete)));
        return SystemPipeline.compile(steps);
    }

    /**
     * 2026/11/02：插件步骤前后复核宿主准入，停止决定不能绕过最终提交后仍形成成功。
     * @param context 本轮组件和控制
     * @param phase 明确插件阶段
     * @return 允许继续的组件补丁，拒绝通过原上下文终止路径传播
     */
    private CompletionStage<SystemResult> pluginStep(SystemContext context, org.zexnocs.teanekoagent.plugin.AgentFeature.Phase phase) {
        var turn = turns.apply(context.snapshot().id());
        return guard(turn, false).thenComposeAsync(ignored -> phase == org.zexnocs.teanekoagent.plugin.AgentFeature.Phase.BEFORE_PROMPT
                ? plugins.prepare(context) : plugins.runSystems(phase, context), executor).thenComposeAsync(result -> {
            if (result.outcome() == SystemResult.Outcome.STOP) { return turn.rejectContext(new AgentPromptException(AgentPromptException.Reason.DISABLED)); }
            return guard(turn, false).thenApply(ignored -> result);
        }, executor).exceptionallyCompose(turn::rejectContext);
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
                // 2026/11/02：异步环绕与事件完成后才预算裁剪，继续使用原人格及来源复核。
                java.util.function.Supplier<CompletionStage<List<org.zexnocs.teanekoagent.agent.prompt.AgentPromptFragment>>> source = () ->
                        Objects.requireNonNull(prompts.resolve(turn.handle.definition(), request,
                                context.snapshot().require(AgentComponents.RUNTIME), context.control()), "Prompt 资料端口不能返回空阶段。");
                return context.control().guard((plugins == null ? source.get() : plugins.prepareFragments(context, source))
                        .thenApply(values -> {
                            if (values == null || values.size() > 32) { throw new IllegalArgumentException("Prompt 资料列表范围无效。"); }
                            return List.copyOf(values);
                        })).thenComposeAsync(fragments -> {
                context.control().checkActive(); turn.guard(gate, false);
                var layout = AgentPromptInput.bounded(request.messages(), fragments, request.inputMaxBytes(), codec, personality, request.summary());
                var input = plugins == null ? layout : plugins.stamp(layout, context.snapshot());
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
        // 2026/11/02：与插件上下文复用同一固定引用派生逻辑。
        return request.withMemoryReference(reference);
    }

    /**
     * 在业务执行域选择工具并调用既有有限交互服务，不复制模型/工具循环。
     * @param context 本轮固定组件和实际控制
     * @return 实际交互结束后才完成的补丁
     */
    private CompletionStage<SystemResult> interact(SystemContext context) {
        return CompletableFuture.supplyAsync(() -> {
            var turn = turns.apply(context.snapshot().id());
            return history == null ? CompletableFuture.<Void>completedStage(null)
                    : history.record(fixedRequest(context.snapshot()), context.snapshot().require(AgentComponents.PROMPT_INPUT), null, turn.control()).exceptionallyCompose(turn::rejectContext);
        }, executor).thenCompose(Function.identity()).thenComposeAsync(ignored -> startInteraction(context), executor);
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
        final List<LLMToolBinding> selected;
        try {
            selected = definition.activities().contains(AgentActivity.TOOL)
                    ? List.copyOf(tools.select(definition, toolRequest, runtime, input)) : List.of();
        } catch (AgentPromptException rejected) { return turn.rejectContext(rejected); }
        // 2026/11/02：筛选决策必须完成且仍有效后才能启动交互。
        return (plugins == null ? CompletableFuture.completedStage(selected) : plugins.selectTools(context, selected))
                .exceptionallyCompose(turn::rejectContext).thenComposeAsync(values -> beginInteraction(context, values), executor);
    }

    /**
     * 使用已经冻结的工具启动原交互，插件结果仍通过逐次准入及来源检查。
     * @param context 可信组件与借用控制
     * @param selected 已冻结工具
     * @return 原交互完成阶段
     */
    private CompletionStage<SystemResult> beginInteraction(SystemContext context, List<LLMToolBinding> selected) {
        var turn = turns.apply(context.snapshot().id());
        var request = context.snapshot().require(AgentComponents.REQUEST);
        var runtime = context.snapshot().require(AgentComponents.RUNTIME);
        var input = context.snapshot().require(AgentComponents.PROMPT_INPUT);
        boolean guarded = references != null || !input.fragments().isEmpty() || input.personality() != null || input.summary() != null || !input.plugins().isEmpty();
        // 2026/11/02：插件筛选后仍过滤客户端，并在实际执行时使用可信组件重新检查。
        var bound = selected.stream().filter(binding -> binding.supportsClient(request.actor().scope().clientId()))
                .map(binding -> binding.guarded((call, trusted) -> {
                    var actor = trusted.bindings().require(AgentComponents.ACTOR);
                    if (!trusted.requestId().equals(request.requestId()) || !actor.equals(request.actor()) || !binding.supportsClient(actor.scope().clientId())) {
                        throw new IllegalStateException("工具客户端或请求来源不匹配。");
                    }
                })).map(binding -> !guarded
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
                        // 2026/11/02：显式无其他资料的工具图像也须经过历史身份与临时负载投影。
                        if (result.status() == LLMInteractionResult.Status.SUCCESS && (!input.fragments().isEmpty() || input.omittedFragments() > 0 || input.personality() != null || input.summary() != null || !input.plugins().isEmpty()
                                || result.tools().stream().anyMatch(tool -> !tool.images().isEmpty()))) {
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
     * 切回业务执行器复核交付，等待已启用的纯文本历史提交，再复核后写本轮结果。
     * 本步骤只借用控制和轮次，外层异步完成链负责统一回收。
     * @param context 已观察交互的组件快照
     * @return 完成结果补丁
     */
    private CompletionStage<SystemResult> complete(SystemContext context) {
        return CompletableFuture.supplyAsync(() -> {
            context.control().checkActive();
            var turn = turns.apply(context.snapshot().id());
            return guard(turn, false).thenComposeAsync(ignored -> {
                var answer = turn.result(null).answer().orElse(null);
                return history == null || answer == null ? CompletableFuture.completedStage(null)
                        : history.record(fixedRequest(context.snapshot()), context.snapshot().require(AgentComponents.PROMPT_INPUT), answer, turn.control())
                                .exceptionallyCompose(turn::rejectContext).thenComposeAsync(saved -> guard(turn, false), executor);
            }, executor).thenApply(ignored -> SystemResult.continueWith(
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
            if (input.summary() != null) {
                checked = checked.thenComposeAsync(ignored -> Objects.requireNonNull(summaries, "实际摘要端口不能为空。")
                        .verify(request, input.summary(), turn.control()), executor);
            }
            // 2026/11/02：没有资料的工具或组件插件也必须复核调用版本。
            return turn.control().guard(checked.thenComposeAsync(ignored -> input.fragments().isEmpty() && input.plugins().isEmpty() ? CompletableFuture.completedStage(null)
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
