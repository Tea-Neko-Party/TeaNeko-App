package org.zexnocs.teanekoagent.agent;

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.agent.access.AgentAccessGate;
import org.zexnocs.teanekoagent.agent.memory.IAgentMemoryReferences;
import org.zexnocs.teanekoagent.llm.framework.input.LLMOptionsPolicy;
import org.zexnocs.teanekoagent.llm.framework.input.LLMPrompt;
import org.zexnocs.teanekoagent.llm.framework.interaction.ILLMInteractionGuard;
import org.zexnocs.teanekoagent.llm.framework.interaction.ILLMInteractionService;
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
 * 预编译准入、交互和交付三个步骤；业务检查统一切入借用执行器，不阻塞模型完成线程。
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

    /**
     * 装配并校验稳定顺序，不在每条请求中重新编译或扫描 System。
     * @param turns 当前服务拥有的轮次查找端口
     * @param gate 统一门控
     * @param interactions 完整交互端口
     * @param tools 显式工具选择端口
     * @param mapper JSON 校验器
     * @param executor 借用业务执行器
     * @param references 可选持久化空间端口；为空时使用显式无记忆模式
     */
    AgentTurnPipeline(Function<EntityId, AgentTurn> turns, AgentAccessGate gate,
            ILLMInteractionService interactions, IAgentToolSource tools, ObjectMapper mapper, Executor executor,
            @Nullable IAgentMemoryReferences references) {
        this.turns = turns; this.gate = gate; this.interactions = interactions;
        this.tools = tools; this.mapper = mapper; this.executor = executor;
        this.references = references;
        pipeline = SystemPipeline.compile(List.of(
                new Step(new SystemDescriptor("agent.admit", Set.of(AgentComponents.REQUEST, AgentComponents.RUNTIME,
                        AgentComponents.SCOPE_POLICY), Set.of(), Set.of()), this::admit),
                new Step(new SystemDescriptor("agent.interact", Set.of(AgentComponents.REQUEST, AgentComponents.RUNTIME,
                        AgentComponents.ACTOR, AgentComponents.ACTIVITY, AgentComponents.INSTANCE, AgentComponents.DEFINITION),
                        Set.of(AgentComponents.INTERACTION), Set.of("agent.admit")), this::interact),
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
        List<LLMToolBinding> selected = definition.activities().contains(AgentActivity.TOOL)
                ? List.copyOf(tools.select(definition, request, runtime)) : List.of();
        var bound = selected.stream().map(binding -> references == null
                ? binding.guarded((call, trusted) -> turn.guard(gate, true))
                : binding.guardedAsync((call, trusted) -> guard(turn, true), executor)).toList();
        var toolSet = new LLMToolSet(mapper, bound);
        turn.guard(gate, false);
        var prompt = new LLMPrompt(request.messages(), runtime.modelOptions().toOptions(), LLMOptionsPolicy.RESOLVED);
        var trusted = new ToolInvocationContext(request.requestId(), context.snapshot(), context.control());
        turn.enteringInteraction();
        try {
            return Objects.requireNonNull(interactions.interact(prompt, toolSet, trusted, runtime.interaction(),
                    references == null ? modelContext -> turn.guard(gate, false)
                            : ILLMInteractionGuard.asynchronous(modelContext -> guard(turn, false))), "交互端口返回的阶段不能为空。")
                    .thenApply(result -> {
                        turn.observe(result);
                        return SystemResult.continueWith(ComponentPatch.builder().put(AgentComponents.INTERACTION, result).build());
                    });
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
            if (references == null) { return CompletableFuture.completedStage(null); }
            return turn.verifyMemory(references).thenRunAsync(() -> turn.guard(gate, tool), executor);
        } catch (RuntimeException failure) { return CompletableFuture.failedStage(failure); }
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
