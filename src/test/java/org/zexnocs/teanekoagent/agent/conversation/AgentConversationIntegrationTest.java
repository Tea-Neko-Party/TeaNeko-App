package org.zexnocs.teanekoagent.agent.conversation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.zexnocs.teanekoagent.agent.*;
import org.zexnocs.teanekoagent.agent.access.AgentAccessGate;
import org.zexnocs.teanekoagent.agent.access.AgentReadiness;
import org.zexnocs.teanekoagent.agent.access.AgentRuntimeSnapshot;
import org.zexnocs.teanekoagent.agent.access.AgentScopePolicy;
import org.zexnocs.teanekoagent.llm.framework.input.interfaces.ILLMPrompt;
import org.zexnocs.teanekoagent.llm.framework.interaction.InteractionRuntime;
import org.zexnocs.teanekoagent.llm.framework.interaction.LLMCallLimits;
import org.zexnocs.teanekoagent.llm.framework.interaction.LLMInteractionService;
import org.zexnocs.teanekoagent.llm.framework.interaction.LLMInteractionSettings;
import org.zexnocs.teanekoagent.llm.framework.message.LLMAssistantMessage;
import org.zexnocs.teanekoagent.llm.framework.message.LLMUserMessage;
import org.zexnocs.teanekoagent.llm.framework.message.content.LLMContentListBuilder;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptions;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptionsSnapshot;
import org.zexnocs.teanekoagent.llm.framework.model.ModelCapabilities;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.ILLMModelService;
import org.zexnocs.teanekoagent.llm.framework.response.LLMChoice;
import org.zexnocs.teanekoagent.llm.framework.response.LLMResult;
import org.zexnocs.teanekoagent.llm.framework.response.LLMUsage;
import org.zexnocs.teanekoagent.llm.framework.response.interfaces.ILLMResult;
import org.zexnocs.teanekoagent.llm.framework.tool.LLMTool;
import org.zexnocs.teanekoagent.llm.framework.tool.LLMToolBinding;
import org.zexnocs.teanekoagent.llm.framework.tool.LLMToolCall;
import org.zexnocs.teanekoagent.llm.framework.tool.interfaces.IContextualToolExecutor;
import org.zexnocs.teanekocore.actuator.execution.ExecutionPolicy;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.logger.ILogger;
import tools.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 从默认会话服务经过真实轮次和交互管线验证完成等待、防重入及诊断保留。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
class AgentConversationIntegrationTest {
    private final InteractionRuntime runtime = new InteractionRuntime();
    private final AgentRegistry registry = new AgentRegistry();
    private final AgentHandle handle = new AgentFactory(registry).create(new AgentDefinition("test",
            new LLMModelOptionsSnapshot(null), Set.of(AgentActivity.values())));
    private final ILogger logger = mock(ILogger.class);
    private final ILLMModelService models = mock(ILLMModelService.class);
    private final List<CompletableFuture<ILLMResult>> replies = new ArrayList<>();
    private final AtomicReference<IContextualToolExecutor> tool = new AtomicReference<>((call, context) -> CompletableFuture.completedStage("工具结果"));
    private final AgentRuntimeSnapshot ready = new AgentRuntimeSnapshot(new LLMModelOptionsSnapshot(LLMModelOptions.builder()
            .provider("test").model("离线模型").maxTokens(20).build()), ModelCapabilities.textOnly(), LLMInteractionSettings.defaults());
    private final AgentAccessGate gate = new AgentAccessGate(registry,
            scope -> new AgentScopePolicy(true, true, AgentScopePolicy.Mode.ACTIVE),
            definition -> new AgentReadiness(AgentReadiness.Status.READY, Optional.of(ready)), logger);
    private final AgentService turns = new AgentService(gate, new LLMInteractionService(models,
            LLMInteractionSettings::defaults, runtime, runtime.scheduler),
            (definition, request, config) -> List.of(new LLMToolBinding(LLMTool.builder().name("work").build(), tool.get())),
            new ObjectMapper(), runtime, runtime.scheduler, runtime.clock, logger);
    private final AgentConversationService service = new AgentConversationService(turns, AgentQueueSettings::defaults,
            runtime, runtime.scheduler, runtime.clock, logger);

    /** 注册受控供应商结果，不替换 Agent 或 LLM 的执行及取消逻辑。 */
    AgentConversationIntegrationTest() {
        when(models.call(any(ILLMPrompt.class), any(ExecutionPolicy.class))).thenAnswer(ignored -> {
            var pending = new CompletableFuture<ILLMResult>(); replies.add(pending);
            return new TaskFuture<>(logger, "会话离线模型", pending);
        });
    }

    /** 关闭自有服务和实例，不允许遗留计时任务。 */
    @AfterEach void cleanup() {
        service.close(); turns.close(); registry.close(); runtime.runAll(); assertEquals(0, runtime.pendingTimers());
    }

    /**
     * 构造同群不同用户的固定请求。
     * @return 可信请求
     */
    private AgentRunRequest request() {
        var actor = new AgentActorContext(new AgentScope("客户端", "群"), AgentActorContext.Origin.USER,
                Optional.of(UUID.randomUUID()), "平台用户", UUID.randomUUID().toString(), "固定目标");
        return new AgentRunRequest(UUID.randomUUID(), actor, AgentActivity.SERVICE,
                List.of(LLMUserMessage.builder().contents(new LLMContentListBuilder().addText("请求").build()).build()));
    }

    /**
     * 构造有明确 usage 的模型响应。
     * @param useTool 是否请求工具
     * @return 统一模型结果
     */
    private LLMResult response(boolean useTool) {
        return LLMResult.builder().usage(LLMUsage.builder().totalTokens(2).build())
                .choices(List.of(LLMChoice.builder().index(0).finishReason(useTool ? "tool_calls" : "stop")
                        .message(LLMAssistantMessage.builder().contents(new LLMContentListBuilder().addText("回答").build())
                                .toolCalls(useTool ? List.of(LLMToolCall.builder().id("调用").name("work").arguments("{}").build()) : List.of())
                                .build()).build())).build();
    }

    /** 验证会话代次以可信组件进入真实工具上下文，不进入模型参数或消息透明状态。 */
    @Test void conversationEpochIsBoundToTrustedToolContext() {
        var seen = new AtomicReference<org.zexnocs.teanekocore.framework.composition.EntitySnapshot>();
        tool.set((call, context) -> { seen.set(context.bindings()); return CompletableFuture.completedStage("完成"); });
        var request = request(); var result = service.run(handle, request).toCompletableFuture(); runtime.runAll();
        var stamp = service.snapshot(handle, request.actor().scope()).orElseThrow().require(AgentConversationState.COMPONENT).stamp();
        replies.getFirst().complete(response(true)); runtime.runAll();
        assertEquals(stamp, seen.get().require(AgentComponents.CONVERSATION));
        assertEquals(stamp, seen.get().require(AgentComponents.REQUEST).conversation());
        var prompts = org.mockito.ArgumentCaptor.forClass(ILLMPrompt.class);
        verify(models, times(2)).call(prompts.capture(), any(ExecutionPolicy.class));
        for (var prompt : prompts.getAllValues()) {
            var options = prompt.getOptions();
            assertNotNull(options, "实际模型调用必须携带已解析参数。");
            assertFalse(options.getMetadata().toString().contains(stamp.epoch().toString()));
            assertTrue(prompt.getMessages().stream().noneMatch(message -> message.getProviderMetadata().containsKey("epoch")));
        }
        replies.getLast().complete(response(false)); runtime.runAll(); assertEquals(AgentRunResult.Status.SUCCESS, result.join().status());
    }

    /** 验证真实工具等待中失效会停止旧交互、保留 usage，新请求绑定新代次且迟到工具不能续跑。 */
    @Test void invalidatingRealTurnCancelsOldWorkAndPreservesObservedUsage() {
        var a = request(); var waiting = new CompletableFuture<String>(); tool.set((call, context) -> waiting);
        var first = service.run(handle, a).toCompletableFuture(); var queued = service.run(handle, request()).toCompletableFuture(); runtime.runAll();
        replies.getFirst().complete(response(true)); runtime.runAll();
        var next = service.invalidate(handle, a.actor().scope()).orElseThrow();
        var current = service.run(handle, request()).toCompletableFuture(); runtime.runAll();
        assertEquals(AgentRunResult.Status.STALE_CONVERSATION, queued.join().status());
        var stale = first.join(); assertEquals(AgentRunResult.Status.STALE_CONVERSATION, stale.status()); assertTrue(stale.answer().isEmpty());
        assertEquals(2, stale.interaction().orElseThrow().attempts().getFirst().usage().orElseThrow().getTotalTokens());
        assertEquals(2, replies.size()); waiting.complete("旧工具迟到完成"); runtime.runAll(); assertEquals(2, replies.size());
        replies.getLast().complete(response(false)); runtime.runAll(); assertEquals(AgentRunResult.Status.SUCCESS, current.join().status());
        assertEquals(next, service.snapshot(handle, a.actor().scope()).orElseThrow().require(AgentConversationState.COMPONENT).stamp());
    }

    /** 验证独立端口拒绝其他实例的代次，且不会为无效输入泄漏父控制监听。 */
    @Test void independentTurnRejectsForeignInstanceStampBeforeCreatingControl() {
        var raw = request();
        var stamp = new AgentConversationStamp(new AgentConversationKey(
                org.zexnocs.teanekocore.framework.composition.EntityId.random(), raw.actor().scope()), UUID.randomUUID());
        var invalid = new AgentRunRequest(raw.requestId(), raw.actor(), raw.activity(), raw.messages(), stamp);
        try (var parent = runtime.parent(LLMCallLimits.defaults())) {
            var cold = turns.prepare(handle, invalid, parent); cold.start();
            assertEquals(AgentRunResult.Status.INVALID_INPUT, cold.result().toCompletableFuture().join().status());
            assertTrue(replies.isEmpty()); assertDoesNotThrow(parent::checkActive);
        }
        var otherScope = new AgentConversationStamp(new AgentConversationKey(handle.id(), new AgentScope("客户端", "另一群")), UUID.randomUUID());
        assertThrows(IllegalArgumentException.class, () -> new AgentRunRequest(raw.requestId(), raw.actor(), raw.activity(), raw.messages(), otherScope));
    }

    /** 验证完整工具往返结束后才启动同群下一用户，并保留各自可信身份。 */
    @Test void nextUserWaitsForWholeInteraction() {
        var a = request(); var b = request(); var waiting = new CompletableFuture<String>(); var seen = new AtomicReference<AgentActorContext>();
        tool.set((call, context) -> { seen.set(context.bindings().require(AgentComponents.ACTOR)); return waiting; });
        var first = service.run(handle, a).toCompletableFuture(); var second = service.run(handle, b).toCompletableFuture(); runtime.runAll();
        assertEquals(1, replies.size()); replies.getFirst().complete(response(true)); runtime.runAll();
        assertEquals(a.actor(), seen.get()); assertEquals(1, replies.size()); assertFalse(second.isDone());
        waiting.complete("工具真正结束"); runtime.runAll(); assertEquals(2, replies.size()); assertFalse(first.isDone());
        replies.getLast().complete(response(false)); runtime.runAll(); assertEquals(AgentRunResult.Status.SUCCESS, first.join().status());
        assertEquals(3, replies.size()); replies.getLast().complete(response(false)); runtime.runAll(); assertEquals(AgentRunResult.Status.SUCCESS, second.join().status());
    }

    /** 验证真实工具内递归进入同群时立即返回，不等待占用自己的邮箱。 */
    @Test void toolReentryIntoSameConversationIsRejected() {
        var nested = new AtomicReference<CompletionStage<AgentRunResult>>();
        tool.set((call, context) -> {
            var result = service.run(handle, request(), context.control()); nested.set(result);
            assertTrue(result.toCompletableFuture().isDone());
            return result.thenApply(value -> value.status().name());
        });
        var result = service.run(handle, request()).toCompletableFuture(); runtime.runAll();
        replies.getFirst().complete(response(true)); runtime.runAll(); assertEquals(AgentRunResult.Status.REENTRANT, nested.get().toCompletableFuture().join().status());
        assertEquals(2, replies.size()); replies.getLast().complete(response(false)); runtime.runAll(); assertEquals(AgentRunResult.Status.SUCCESS, result.join().status());
    }

    /** 验证请求级取消保留实际 usage，推进下一用户，迟到工具结果不能触发旧轮追加请求。 */
    @Test void cancelRunningToolRetainsUsageAndAdvancesQueue() {
        var a = request(); var waiting = new CompletableFuture<String>(); tool.set((call, context) -> waiting);
        var first = service.run(handle, a).toCompletableFuture(); var second = service.run(handle, request()).toCompletableFuture(); runtime.runAll();
        replies.getFirst().complete(response(true)); runtime.runAll();
        assertEquals(IAgentService.CancelResult.REQUESTED, service.cancel(handle, a.actor().scope(), a.requestId(), a.actor().userId().orElseThrow()));
        runtime.runAll(); var cancelled = first.join(); assertEquals(AgentRunResult.Status.CANCELLED, cancelled.status());
        assertEquals(2, cancelled.interaction().orElseThrow().attempts().getFirst().usage().orElseThrow().getTotalTokens());
        assertEquals(2, replies.size()); waiting.complete("迟到结果"); runtime.runAll(); assertEquals(2, replies.size());
        replies.getLast().complete(response(false)); runtime.runAll(); assertEquals(AgentRunResult.Status.SUCCESS, second.join().status());
        assertSame(cancelled, first.join());
    }

    /** 验证真实交互出队后仍使用共享父预算，不因队列包装重置额度。 */
    @Test void queuedRealInteractionsShareParentAllowance() {
        try (var parent = runtime.parent(new LLMCallLimits(1, 0, 0))) {
            var first = service.run(handle, request(), parent).toCompletableFuture(); var second = service.run(handle, request(), parent).toCompletableFuture(); runtime.runAll();
            assertEquals(1, replies.size()); replies.getFirst().complete(response(false)); runtime.runAll();
            assertEquals(AgentRunResult.Status.SUCCESS, first.join().status()); assertEquals(AgentRunResult.Status.BUDGET_EXCEEDED, second.join().status());
            assertEquals(1, replies.size()); assertEquals(1, parent.budget().used(LLMCallLimits.MODEL_REQUESTS)); assertDoesNotThrow(parent::checkActive);
        }
    }
}
