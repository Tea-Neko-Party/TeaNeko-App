package org.zexnocs.teanekoagent.agent.prompt;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.zexnocs.teanekoagent.agent.*;
import org.zexnocs.teanekoagent.agent.access.AgentAccessGate;
import org.zexnocs.teanekoagent.agent.access.AgentReadiness;
import org.zexnocs.teanekoagent.agent.access.AgentRuntimeSnapshot;
import org.zexnocs.teanekoagent.agent.access.AgentScopePolicy;
import org.zexnocs.teanekoagent.agent.conversation.*;
import org.zexnocs.teanekoagent.llm.framework.input.interfaces.ILLMPrompt;
import org.zexnocs.teanekoagent.llm.framework.interaction.InteractionRuntime;
import org.zexnocs.teanekoagent.llm.framework.interaction.LLMCallLimits;
import org.zexnocs.teanekoagent.llm.framework.interaction.LLMInteractionService;
import org.zexnocs.teanekoagent.llm.framework.interaction.LLMInteractionSettings;
import org.zexnocs.teanekoagent.llm.framework.message.LLMAssistantMessage;
import org.zexnocs.teanekoagent.llm.framework.message.LLMMessageCodec;
import org.zexnocs.teanekoagent.llm.framework.message.LLMMessageHistory;
import org.zexnocs.teanekoagent.llm.framework.message.LLMUserMessage;
import org.zexnocs.teanekoagent.llm.framework.message.content.LLMContentListBuilder;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMMessage;
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
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.actuator.execution.ExecutionPolicy;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.logger.ILogger;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 通过真实 Agent 邮箱和 LLM 管线验证异步资料、历史隔离、资源停止和重放边界。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
class AgentPromptIntegrationTest {
    private final InteractionRuntime runtime = new InteractionRuntime();
    private final AgentRegistry registry = new AgentRegistry();
    private final AgentHandle handle = new AgentFactory(registry).create(new AgentDefinition("test",
            new LLMModelOptionsSnapshot(null), Set.of(AgentActivity.values())));
    private final ILogger logger = mock(ILogger.class);
    private final ILLMModelService models = mock(ILLMModelService.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final LLMMessageCodec codec = new LLMMessageCodec(mapper);
    private final List<ILLMPrompt> prompts = new ArrayList<>();
    private final List<CompletableFuture<ILLMResult>> replies = new ArrayList<>();
    private final AtomicReference<AgentConversationOptions> options = new AtomicReference<>(AgentConversationOptions.defaults());
    private final SwitchableSource source = new SwitchableSource();
    private final AtomicReference<AgentRunRequest> toolRequest = new AtomicReference<>();
    private final AgentScope scope = new AgentScope("客户端", "群");
    private final AgentAccessGate gate = new AgentAccessGate(registry,
            ignored -> new AgentScopePolicy(true, true, AgentScopePolicy.Mode.ACTIVE),
            ignored -> new AgentReadiness(AgentReadiness.Status.READY, Optional.of(new AgentRuntimeSnapshot(
                    new LLMModelOptionsSnapshot(LLMModelOptions.builder().provider("test").model("离线模型").maxTokens(20).build()),
                    ModelCapabilities.textOnly(), LLMInteractionSettings.defaults()))), logger);
    private final AgentService turns = new AgentService(gate, new LLMInteractionService(models,
            LLMInteractionSettings::defaults, runtime, runtime.scheduler), (definition, request, config) -> {
                toolRequest.set(request);
                return List.of(new LLMToolBinding(LLMTool.builder().name("work").build(), (call, context) -> CompletableFuture.completedStage("工具完成")));
            }, mapper, runtime, runtime.scheduler, runtime.clock, logger, null, source);
    private final AgentConversationService mailbox = new AgentConversationService(turns, AgentQueueSettings::defaults,
            runtime, runtime.scheduler, runtime.clock, logger, AgentConversationSettings::defaults, (agent, request) -> options.get(), mapper);

    /** 注册受控模型响应，资料及轮次均使用真实异步管线。 */
    AgentPromptIntegrationTest() {
        when(models.call(any(ILLMPrompt.class), any(ExecutionPolicy.class))).thenAnswer(invocation -> {
            prompts.add(invocation.getArgument(0)); var pending = new CompletableFuture<ILLMResult>(); replies.add(pending);
            return new TaskFuture<>(logger, "临时资料离线模型", pending);
        });
    }

    /** 关闭自己拥有的服务，确认资料等待和 LLM 阶段没有遗留计时器。 */
    @AfterEach void cleanup() {
        mailbox.close(); turns.close(); registry.close(); runtime.runAll(); assertEquals(0, runtime.pendingTimers());
    }

    /**
     * 构造具有当前原文凭据的真实用户请求，历史不能生成该凭据。
     * @return 同一群的独立请求
     */
    private AgentRunRequest request() {
        var actor = new AgentActorContext(scope, AgentActorContext.Origin.USER, Optional.of(UUID.randomUUID()), "用户", UUID.randomUUID().toString(), "目标");
        return new AgentRunRequest(UUID.randomUUID(), actor, AgentActivity.SERVICE,
                List.of(LLMUserMessage.builder().contents(new LLMContentListBuilder().addText("原始问题").build()).build()),
                null, null, new AgentUserText(actor.sourceId(), "原始问题"));
    }

    /**
     * 构造带原始工具 ID 和透明供应商状态的统一响应。
     * @param tools 是否请求真实工具往返
     * @return 明确 usage 的模型结果
     */
    private ILLMResult response(boolean tools) {
        return LLMResult.builder().usage(LLMUsage.builder().totalTokens(2).build()).choices(List.of(LLMChoice.builder()
                .index(0).finishReason(tools ? "tool_calls" : "stop").message(LLMAssistantMessage.builder()
                        .contents(new LLMContentListBuilder().addText("正常回答").build()).providerMetadata(Map.of("reasoning_content", "透明状态"))
                        .toolCalls(tools ? List.of(LLMToolCall.builder().id("固定调用").name("work").arguments("{}").build()) : List.of()).build()).build())).build();
    }

    /**
     * 读取真实邮箱已提交窗口。
     * @return 当前成功历史窗口
     */
    private AgentConversationWindow window() {
        return mailbox.snapshot(handle, scope).orElseThrow().require(AgentConversationWindow.COMPONENT);
    }

    /**
     * 度量消息内容，断言时只读取真实协议内容。
     * @param messages 实际消息列表
     * @return 完整序列化文本
     */
    private String text(List<ILLMMessage> messages) { return mapper.writeValueAsString(messages); }

    /** 验证连续轮次只携带本轮资料，原文授权与工具选择不接收资料伪造，实际诊断仍保留资料。 */
    @Test void temporaryDataReachesModelWithoutAccumulatingInHistoryOrAuthorization() {
        var first = request(); var result = mailbox.run(handle, first).toCompletableFuture(); runtime.runAll();
        assertTrue(text(prompts.getFirst().getMessages()).contains("临时资料甲"));
        assertFalse(text(toolRequest.get().messages()).contains("临时资料甲")); assertSame(first.userText(), toolRequest.get().userText());
        replies.getFirst().complete(response(false)); runtime.runAll(); assertEquals(AgentRunResult.Status.SUCCESS, result.join().status());
        assertNotNull(result.join().promptTranscript()); assertTrue(text(result.join().interaction().orElseThrow().transcript()).contains("临时资料甲"));
        assertFalse(text(result.join().committableTranscript().orElseThrow()).contains("临时资料甲")); assertEquals(2, window().messages().size());
        source.resolver = () -> CompletableFuture.completedStage(List.of(new AgentPromptFragment("memory", "临时资料乙")));
        var next = mailbox.run(handle, request()).toCompletableFuture(); runtime.runAll();
        assertTrue(text(prompts.getLast().getMessages()).contains("临时资料乙")); assertFalse(text(prompts.getLast().getMessages()).contains("临时资料甲"));
        replies.getLast().complete(response(false)); runtime.runAll(); assertEquals(AgentRunResult.Status.SUCCESS, next.join().status());
        assertEquals(4, window().messages().size()); assertFalse(text(window().messages()).contains("临时资料"));
    }

    /** 验证真实工具往返后仍保留调用 ID、供应商状态和原消息，临时资料不进入完整工具历史。 */
    @Test void actualToolRoundKeepsProtocolAndProjectionSeparate() {
        var result = mailbox.run(handle, request()).toCompletableFuture(); runtime.runAll();
        replies.getFirst().complete(response(true)); runtime.runAll(); assertEquals(2, prompts.size());
        assertTrue(source.verifications.get() >= 4); assertTrue(text(prompts.getLast().getMessages()).contains("临时资料甲"));
        replies.getLast().complete(response(false)); runtime.runAll(); var done = result.join();
        assertEquals(AgentRunResult.Status.SUCCESS, done.status()); LLMMessageHistory.validateComplete(window().messages());
        assertEquals(4, window().messages().size()); assertTrue(text(window().messages()).contains("固定调用"));
        assertTrue(text(window().messages()).contains("透明状态")); assertFalse(text(window().messages()).contains("临时资料甲"));
        assertEquals(2, done.interaction().orElseThrow().attempts().size());
    }

    /** 验证等待来源期间不启动模型；完成线程只冻结列表，随后业务域使用冻结结果。 */
    @Test void pendingSourceIsAwaitedAndFrozenBeforeBusinessDispatch() {
        var pending = new CompletableFuture<List<AgentPromptFragment>>(); source.resolver = () -> pending;
        var result = mailbox.run(handle, request()).toCompletableFuture(); runtime.runAll(); assertTrue(prompts.isEmpty());
        var data = new ArrayList<>(List.of(new AgentPromptFragment("memory", "完成时资料"))); pending.complete(data); data.clear();
        assertTrue(prompts.isEmpty()); runtime.runAll(); assertTrue(text(prompts.getFirst().getMessages()).contains("完成时资料"));
        replies.getFirst().complete(response(false)); runtime.runAll(); assertEquals(AgentRunResult.Status.SUCCESS, result.join().status());
    }

    /** 验证取消只停止借用观察，迟到资料不发起模型或写历史，不取消来源自己的事务阶段。 */
    @Test void cancellationDuringSourceWaitIgnoresLateResult() {
        var pending = new CompletableFuture<List<AgentPromptFragment>>(); source.resolver = () -> pending; var request = request();
        var result = mailbox.run(handle, request).toCompletableFuture(); runtime.runAll();
        assertEquals(IAgentService.CancelResult.REQUESTED, mailbox.cancel(handle, scope, request.requestId(), request.actor().userId().orElseThrow()));
        runtime.runAll(); assertEquals(AgentRunResult.Status.CANCELLED, result.join().status()); assertFalse(pending.isCancelled());
        pending.complete(List.of(new AgentPromptFragment("memory", "取消后到达"))); runtime.runAll(); assertTrue(prompts.isEmpty()); assertTrue(window().messages().isEmpty());
    }

    /** 验证资料等待属于整轮截止时间，超时不用真实休眠，也不提前消耗模型次数。 */
    @Test void sourceWaitUsesWholeTurnDeadline() {
        var pending = new CompletableFuture<List<AgentPromptFragment>>(); source.resolver = () -> pending;
        var result = mailbox.run(handle, request()).toCompletableFuture(); runtime.runAll(); runtime.advance(Duration.ofSeconds(121)); runtime.runAll();
        assertEquals(AgentRunResult.Status.TIMED_OUT, result.join().status()); assertTrue(prompts.isEmpty()); assertFalse(pending.isCancelled());
        pending.complete(List.of()); runtime.runAll(); assertTrue(window().messages().isEmpty());
    }

    /** 验证无效阶段/值、重复来源及同步/异步异常均安全终止，不泄漏来源错误正文或调用模型。 */
    @Test void malformedAndFailedSourcesFinishWithoutSensitiveErrors() {
        List<Supplier<CompletionStage<List<AgentPromptFragment>>>> failures = List.of(
                () -> null, () -> CompletableFuture.completedStage(null),
                () -> CompletableFuture.completedStage(List.of(new AgentPromptFragment("same", "甲"), new AgentPromptFragment("same", "乙"))),
                () -> { throw new IllegalStateException("私密来源正文"); },
                () -> CompletableFuture.failedStage(new IllegalStateException("私密来源正文")));
        for (var failure : failures) {
            source.resolver = failure; var result = mailbox.run(handle, request()).toCompletableFuture(); runtime.runAll();
            assertEquals(AgentRunResult.Status.CONFIGURATION_ERROR, result.join().status()); assertFalse(mapper.writeValueAsString(result.join()).contains("私密来源正文"));
        }
        assertTrue(prompts.isEmpty()); assertTrue(window().messages().isEmpty());
    }

    /** 验证模型前的动态来源换代和关闭分别映射为过期与跳过，异步校验失败不继续模型。 */
    @Test void changingOrDisabledSourceStopsBeforeModel() {
        for (var reason : List.of(AgentPromptException.Reason.CHANGED, AgentPromptException.Reason.DISABLED)) {
            source.verifications.set(0);
            source.verifier = input -> source.verifications.get() == 1 ? CompletableFuture.completedStage(null)
                    : CompletableFuture.failedStage(new AgentPromptException(reason));
            var result = mailbox.run(handle, request()).toCompletableFuture(); runtime.runAll();
            assertEquals(reason == AgentPromptException.Reason.CHANGED ? AgentRunResult.Status.STALE_CONVERSATION : AgentRunResult.Status.SKIPPED, result.join().status());
        }
        assertTrue(prompts.isEmpty());
    }

    /** 验证回答交付前资料失效会隐藏回答，保留实际交互/usage 且不提交正常历史。 */
    @Test void finalSourceInvalidationHidesAnswerButRetainsActualDiagnostic() {
        var result = mailbox.run(handle, request()).toCompletableFuture(); runtime.runAll();
        source.verifier = input -> CompletableFuture.failedStage(new AgentPromptException(AgentPromptException.Reason.CHANGED));
        replies.getFirst().complete(response(false)); runtime.runAll(); var done = result.join();
        assertEquals(AgentRunResult.Status.STALE_CONVERSATION, done.status()); assertTrue(done.answer().isEmpty());
        assertEquals(1, done.interaction().orElseThrow().attempts().size()); assertTrue(text(done.interaction().orElseThrow().transcript()).contains("临时资料甲"));
        assertTrue(window().messages().isEmpty());
    }

    /** 验证资料结果的进行中及完成后重放明确不可用，不重新读取资料、调用模型或重复工具。 */
    @Test void supplementaryDataResultsCannotReplayWithoutSourceAuthorizationProtocol() {
        var request = request(); var original = mailbox.run(handle, request).toCompletableFuture(); runtime.runAll();
        var inflight = mailbox.retry(handle, request).toCompletableFuture(); runtime.runAll();
        replies.getFirst().complete(response(false)); runtime.runAll(); assertEquals(AgentRunResult.Status.SUCCESS, original.join().status());
        assertEquals(AgentRunResult.Status.REPLAY_UNAVAILABLE, inflight.join().status()); assertTrue(inflight.join().answer().isEmpty());
        var replay = mailbox.retry(handle, request).toCompletableFuture(); var duplicate = mailbox.run(handle, request).toCompletableFuture(); runtime.runAll();
        assertEquals(AgentRunResult.Status.REPLAY_UNAVAILABLE, replay.join().status()); assertEquals(AgentRunResult.Status.REPLAY_UNAVAILABLE, duplicate.join().status());
        assertEquals(1, source.resolutions.get()); assertEquals(1, prompts.size()); assertEquals(1, window().turns().size());
    }

    /** 验证原输入恰好填满预算时整项省略资料，实际选项覆盖调用者提示且普通缓存可重放。 */
    @Test void exactInputBudgetOmitsAllDataAndPreservesNormalReplay() {
        var request = request(); var stamp = new AgentConversationStamp(new AgentConversationKey(handle.id(), scope), UUID.randomUUID());
        var prepared = new AgentConversationEngine(mapper).prepare(request, stamp, AgentConversationWindow.empty(), AgentConversationOptions.defaults());
        var bytes = codec.signature(prepared.request().messages()).bytes(); options.set(new AgentConversationOptions(40, bytes, 32, 1048576));
        var hinted = new AgentRunRequest(request.requestId(), request.actor(), request.activity(), request.messages(), null, null, request.userText(), 1);
        var result = mailbox.run(handle, hinted).toCompletableFuture(); runtime.runAll();
        assertEquals(bytes, codec.signature(prompts.getFirst().getMessages()).bytes()); assertEquals(0, source.verifications.get());
        replies.getFirst().complete(response(false)); runtime.runAll(); assertEquals(AgentRunResult.Status.SUCCESS, result.join().status());
        assertEquals(1, Objects.requireNonNull(result.join().promptTranscript()).input().omittedFragments());
        var replay = mailbox.retry(handle, hinted).toCompletableFuture(); runtime.runAll(); assertSame(result.join(), replay.join()); assertEquals(1, prompts.size());
    }

    /** 验证独立轮次同样执行宿主输入上限，原问题超限不通过删资料或增加模型预算绕过。 */
    @Test void independentTurnRejectsOversizedOriginalBeforeModel() {
        var request = request(); var tooSmall = new AgentRunRequest(request.requestId(), request.actor(), request.activity(), request.messages(), null, null, request.userText(), 1);
        var result = turns.run(handle, tooSmall).toCompletableFuture(); runtime.runAll();
        assertEquals(AgentRunResult.Status.INPUT_TOO_LARGE, result.join().status()); assertTrue(prompts.isEmpty());
    }

    /** 验证资料重放等待有独立期限和取消，迟到允许不公开回答，不停止原请求或取消来源阶段。 */
    @Test void promptReplayObservationHasIndependentCancellationAndDeadline() {
        var request = request(); var original = mailbox.run(handle, request).toCompletableFuture(); runtime.runAll();
        replies.getFirst().complete(response(false)); runtime.runAll(); assertEquals(AgentRunResult.Status.SUCCESS, original.join().status());
        var cancelledCheck = new CompletableFuture<Boolean>(); source.replayVerifier = () -> cancelledCheck;
        try (var parent = runtime.parent(LLMCallLimits.defaults())) {
            var cancelled = mailbox.retry(handle, request, parent).toCompletableFuture(); runtime.runAll(); assertFalse(cancelled.isDone());
            parent.cancel(); runtime.runAll(); assertEquals(AgentRunResult.Status.CANCELLED, cancelled.join().status());
            assertFalse(cancelledCheck.isCancelled()); cancelledCheck.complete(true); runtime.runAll(); assertTrue(cancelled.join().answer().isEmpty());
        }
        var timedCheck = new CompletableFuture<Boolean>(); source.replayVerifier = () -> timedCheck;
        var timed = mailbox.retry(handle, request).toCompletableFuture(); runtime.runAll(); runtime.advance(Duration.ofSeconds(31)); runtime.runAll();
        assertEquals(AgentRunResult.Status.TIMED_OUT, timed.join().status()); timedCheck.complete(true); runtime.runAll(); assertTrue(timed.join().answer().isEmpty());
        source.replayVerifier = () -> CompletableFuture.completedStage(true);
        var allowed = mailbox.retry(handle, request).toCompletableFuture(); runtime.runAll(); assertSame(original.join(), allowed.join());
        assertEquals(1, prompts.size()); assertEquals(1, source.resolutions.get());
    }

    /**
     * 可控制资料等待和动态校验的端口替身，不替换真实执行/提交/资源拥有者。
     * @author zExNocs
     * @date 2026/10/29
     * @since 4.0.0
     * @version 4.5.0-l
     */
    private static final class SwitchableSource implements IAgentPromptSource {
        Supplier<CompletionStage<List<AgentPromptFragment>>> resolver = () -> CompletableFuture.completedStage(List.of(new AgentPromptFragment("memory", "临时资料甲")));
        Function<AgentPromptInput, CompletionStage<Void>> verifier = input -> CompletableFuture.completedStage(null);
        Supplier<CompletionStage<Boolean>> replayVerifier = () -> CompletableFuture.completedStage(false);
        final AtomicInteger resolutions = new AtomicInteger();
        final AtomicInteger verifications = new AtomicInteger();

        /**
         * 返回当前受控阶段，构造时不读取来源。
         * @param definition 实例定义
         * @param request 固定原始请求
         * @param runtime 运行快照
         * @param control 借用轮次控制
         * @return 由测试推进的来源阶段
         */
        @Override public CompletionStage<List<AgentPromptFragment>> resolve(AgentDefinition definition, AgentRunRequest request,
                AgentRuntimeSnapshot runtime, ExecutionControl control) { resolutions.incrementAndGet(); return resolver.get(); }

        /**
         * 动态检查实际保留资料，不改变旧输入。
         * @param request 固定请求
         * @param input 已冻结的实际布局
         * @param control 借用控制
         * @return 当前校验阶段
         */
        @Override public CompletionStage<Void> verify(AgentRunRequest request, AgentPromptInput input, ExecutionControl control) {
            verifications.incrementAndGet(); return verifier.apply(input);
        }

        /**
         * 显式控制当前资料重放资格，未指定时继续使用默认拒绝。
         * @param request 原可信请求
         * @param input 原资料布局
         * @param control 观察借用控制
         * @return 由测试推进的只读校验
         */
        @Override public CompletionStage<Boolean> verifyReplay(AgentRunRequest request, AgentPromptInput input, ExecutionControl control) {
            return replayVerifier.get();
        }
    }
}
