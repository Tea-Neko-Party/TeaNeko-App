package org.zexnocs.teanekoagent.agent.conversation;

import org.zexnocs.teanekoagent.agent.*;
import org.zexnocs.teanekoagent.agent.access.AgentAccessDecision;
import org.zexnocs.teanekoagent.agent.access.AgentReadiness;
import org.zexnocs.teanekoagent.agent.access.AgentRuntimeSnapshot;
import org.zexnocs.teanekoagent.agent.access.AgentScopePolicy;
import org.zexnocs.teanekoagent.llm.framework.interaction.LLMCallLimits;
import org.zexnocs.teanekoagent.llm.framework.interaction.LLMInteractionResult;
import org.zexnocs.teanekoagent.llm.framework.interaction.LLMInteractionSettings;
import org.zexnocs.teanekoagent.llm.framework.message.LLMAssistantMessage;
import org.zexnocs.teanekoagent.llm.framework.message.content.LLMContentListBuilder;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptions;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptionsSnapshot;
import org.zexnocs.teanekoagent.llm.framework.model.ModelCapabilities;
import org.zexnocs.teanekocore.actuator.execution.BudgetExceededException;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 不执行模型的可控冷轮次端口，业务完成与取消回执分别由用例推进。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
final class AgentConversationTestBackend implements IAgentTurnService, AutoCloseable {
    final Map<UUID, AgentConversationTestCall> calls = new ConcurrentHashMap<>();
    final List<UUID> started = Collections.synchronizedList(new ArrayList<>());
    final AtomicBoolean autoAck = new AtomicBoolean(true);
    final AtomicBoolean immediate = new AtomicBoolean();
    final AtomicBoolean charge = new AtomicBoolean();
    final AtomicBoolean racing = new AtomicBoolean();
    final AtomicInteger concurrentStarts = new AtomicInteger();
    final AtomicInteger maxConcurrentStarts = new AtomicInteger();
    final AtomicReference<Runnable> beforePrepare = new AtomicReference<>(() -> {});

    /**
     * 为重放测试提供不执行模型的准入决定。
     * @param handle 目标实例
     * @param request 原始请求
     * @return 离线允许决定
     */
    @Override public CompletionStage<AgentAccessDecision> checkAccess(AgentHandle handle, AgentRunRequest request) {
        return CompletableFuture.completedStage(AgentConversationTestCall.allowedAccess());
    }

    /**
     * 仅创建独立控制及冷句柄。
     * @param handle 目标实例
     * @param request 固定请求
     * @return 尚未开始业务的句柄
     */
    @Override public AgentRunExecution prepare(AgentHandle handle, AgentRunRequest request) {
        return create(handle, request, ExecutionControl.create(LLMCallLimits.defaults().newBudget()));
    }

    /**
     * 保留同一父预算及链路。
     * @param handle 目标实例
     * @param request 固定请求
     * @param parent 借用的总控制
     * @return 冷执行句柄
     */
    @Override public AgentRunExecution prepare(AgentHandle handle, AgentRunRequest request, ExecutionControl parent) {
        return create(handle, request, parent.child());
    }

    /**
     * 为邮箱状态测试显式支持引用请求；真实逐次守卫由独立管线集成测试覆盖。
     * @param handle 目标实例
     * @param request 邮箱固定的请求
     * @param parent 可选借用父控制
     * @return 可控冷句柄
     */
    @Override public AgentRunExecution prepareReferenced(AgentHandle handle, AgentRunRequest request,
            @org.jspecify.annotations.Nullable ExecutionControl parent) {
        return parent == null ? prepare(handle, request) : prepare(handle, request, parent);
    }

    /**
     * 先创建资源和观察关系，只有显式 start 才登记实际业务调用。
     * @param handle 实例
     * @param request 请求
     * @param control 本用例拥有的控制
     * @return 受控冷句柄
     */
    private AgentRunExecution create(AgentHandle handle, AgentRunRequest request, ExecutionControl control) {
        beforePrepare.get().run();
        var call = new AgentConversationTestCall(handle, request, control);
        calls.put(request.requestId(), call);
        var link = control.onTermination(reason -> {
            if (autoAck.get()) { call.complete(reason == ExecutionControl.StopReason.DEADLINE_EXCEEDED
                    ? AgentRunResult.Status.TIMED_OUT : AgentRunResult.Status.CANCELLED); }
        });
        call.result.whenComplete((value, failure) -> { link.close(); control.close(); });
        return new AgentRunExecution(call.result, () -> {
            try {
                control.checkActive();
                if (charge.get()) { control.consume(LLMCallLimits.MODEL_REQUESTS, 1); }
                started.add(request.requestId());
                if (racing.get()) {
                    int current = concurrentStarts.incrementAndGet(); maxConcurrentStarts.accumulateAndGet(current, Math::max);
                    try { Thread.yield(); } finally { concurrentStarts.decrementAndGet(); }
                }
                if (immediate.get()) { call.complete(AgentRunResult.Status.SUCCESS); }
            } catch (BudgetExceededException failure) { call.complete(AgentRunResult.Status.BUDGET_EXCEEDED); }
        }, control::cancel, candidate -> candidate.isWithin(control.id()));
    }

    /** 释放本用例仍持有的控制和未完成回执。 */
    @Override public void close() { calls.values().forEach(call -> call.control.cancel()); }
}

/**
 * 单次替身业务，成功结果使用真实不可变协议容器。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
final class AgentConversationTestCall {
    final AgentHandle handle;
    final AgentRunRequest request;
    final ExecutionControl control;
    final CompletableFuture<AgentRunResult> result = new CompletableFuture<>();

    /**
     * 保存当前替身拥有的数据与控制。
     * @param handle 目标实例
     * @param request 当前请求
     * @param control 当前控制
     */
    AgentConversationTestCall(AgentHandle handle, AgentRunRequest request, ExecutionControl control) {
        this.handle = handle; this.request = request; this.control = control;
    }

    /**
     * 完成真实回执；取消后的重复成功不会改写已交付结果。
     * @param status 业务状态
     */
    void complete(AgentRunResult.Status status) {
        var access = allowedAccess();
        var message = LLMAssistantMessage.builder().contents(new LLMContentListBuilder().addText("替身回答").build()).build();
        var transcript = new ArrayList<>(request.messages()); transcript.add(message);
        var interaction = new LLMInteractionResult(LLMInteractionResult.Status.SUCCESS, transcript, message,
                List.of(), List.of(), Map.of());
        result.complete(new AgentRunResult(handle.id(), request.requestId(), status, access,
                status == AgentRunResult.Status.SUCCESS ? interaction : null, control.budget().snapshot(),
                request.conversation(), request.memoryReference()));
    }

    /**
     * 创建与冷端口相同的离线就绪数据，不读取配置或持有外部资源。
     * @return 允许决定及固定参数
     */
    static AgentAccessDecision allowedAccess() {
        var runtime = new AgentRuntimeSnapshot(new LLMModelOptionsSnapshot(LLMModelOptions.builder()
                .provider("test").model("离线模型").build()), ModelCapabilities.textOnly(), LLMInteractionSettings.defaults());
        return new AgentAccessDecision(AgentAccessDecision.Reason.ALLOWED,
                Optional.of(new AgentScopePolicy(true, true, AgentScopePolicy.Mode.ACTIVE)),
                Optional.of(new AgentReadiness(AgentReadiness.Status.READY, Optional.of(runtime))));
    }
}
