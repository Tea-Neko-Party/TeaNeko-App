package org.zexnocs.teanekoagent.llm.framework.structured;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.zexnocs.teanekoagent.llm.framework.input.LLMPrompt;
import org.zexnocs.teanekoagent.llm.framework.input.interfaces.ILLMPrompt;
import org.zexnocs.teanekoagent.llm.framework.interaction.ILLMInteractionGuard;
import org.zexnocs.teanekoagent.llm.framework.interaction.InteractionRuntime;
import org.zexnocs.teanekoagent.llm.framework.interaction.LLMCallLimits;
import org.zexnocs.teanekoagent.llm.framework.interaction.LLMInteractionResult;
import org.zexnocs.teanekoagent.llm.framework.interaction.LLMInteractionService;
import org.zexnocs.teanekoagent.llm.framework.interaction.LLMInteractionSettings;
import org.zexnocs.teanekoagent.llm.framework.interaction.LLMTokenEstimate;
import org.zexnocs.teanekoagent.llm.framework.message.LLMAssistantMessage;
import org.zexnocs.teanekoagent.llm.framework.message.LLMUserMessage;
import org.zexnocs.teanekoagent.llm.framework.message.content.LLMContentListBuilder;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptions;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.ILLMModelService;
import org.zexnocs.teanekoagent.llm.framework.response.LLMChoice;
import org.zexnocs.teanekoagent.llm.framework.response.LLMResult;
import org.zexnocs.teanekoagent.llm.framework.response.LLMUsage;
import org.zexnocs.teanekoagent.llm.framework.response.interfaces.ILLMResult;
import org.zexnocs.teanekoagent.llm.framework.tool.ToolInvocationContext;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.actuator.execution.ExecutionPolicy;
import org.zexnocs.teanekocore.actuator.execution.ExecutionStoppedException;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.framework.composition.ComponentPatch;
import org.zexnocs.teanekocore.framework.composition.EntityId;
import org.zexnocs.teanekocore.framework.composition.EntitySnapshot;
import org.zexnocs.teanekoplugin.agent.cognition.personality.StyleObservations;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 用真实交互、Core 预算和可推进时钟验证结构纠错，模型传输保持离线且可控。
 * <br>2026/11/10：覆盖缺字段反馈、纠错上限、真实排空、总时限、准入和两级预算。
 *
 * @author zExNocs
 * @date 2026/11/10
 * @since 4.9.1-l
 * @version 4.9.1-l
 */
@Timeout(15)
class LLMJsonCorrectionTest {
    private static final String INVALID = "{\"memories\":[],\"styles\":[{\"field\":\"speakingStyle\","
            + "\"pattern\":\"private-model-value\"}]}";
    private static final String VALID = "{\"memories\":[],\"styles\":[]}";
    private final ObjectMapper mapper = new ObjectMapper();
    private final InteractionRuntime runtime = new InteractionRuntime();
    private final ILLMModelService models = mock(ILLMModelService.class);
    private final List<ILLMPrompt> prompts = new ArrayList<>();
    private final List<CompletableFuture<ILLMResult>> replies = new ArrayList<>();
    private final List<CompletableFuture<Void>> drained = new ArrayList<>();
    private final List<LLMInteractionResult> observed = new ArrayList<>();
    private final LLMJsonService json;
    private final LLMJsonService.Request<StyleObservations.Analysis> request;
    private final AtomicInteger checks = new AtomicInteger();

    /** 构造可观察的真实交互链，分别控制逻辑响应和底层资源完成。 */
    LLMJsonCorrectionTest() {
        when(models.prepareRequest(any())).thenCallRealMethod();
        when(models.resolveOptions(any())).thenAnswer(call -> {
            ILLMPrompt prompt = call.getArgument(0);
            return LLMModelOptions.copyOf(prompt.getOptions());
        });
        when(models.call(any(ILLMPrompt.class), any(ExecutionPolicy.class))).thenAnswer(call -> {
            prompts.add(call.getArgument(0));
            var reply = new CompletableFuture<ILLMResult>();
            var released = new CompletableFuture<Void>();
            replies.add(reply);
            drained.add(released);
            TaskFuture<ILLMResult> task = mock();
            when(task.asStage()).thenReturn(reply);
            when(task.physicalCompletion()).thenReturn(released);
            return task;
        });
        var interactions = new LLMInteractionService(models, LLMInteractionSettings::defaults,
                runtime, runtime.scheduler, ignored -> new LLMTokenEstimate(10, "fixed-v1"));
        json = LLMJsonTestSupport.create(interactions, mapper, runtime.scheduler);
        var input = new LLMPrompt(LLMUserMessage.builder()
                .contents(new LLMContentListBuilder().addText("合成资料，无合格事实或风格候选。").build()).build(),
                LLMModelOptions.builder().provider("test").model("test").maxTokens(20).build());
        request = json.prepare(StyleObservations.Analysis.class, input);
    }

    /**
     * @param limits 本次局部预算
     * @return 共同三十秒总时限与二十秒模型阶段的设置
     */
    private LLMInteractionSettings settings(LLMCallLimits limits) {
        return new LLMInteractionSettings(limits, Duration.ofSeconds(20), Duration.ofSeconds(5), Duration.ofSeconds(30));
    }

    /**
     * @param parent 测试拥有的父控制
     * @param limits 局部限额
     * @param guard 动态准入
     * @param inputCheck 输入容量复核
     * @return 包含唯一一次可选纠错的结果
     */
    private CompletionStage<StyleObservations.Analysis> start(ExecutionControl parent, LLMCallLimits limits,
            ILLMInteractionGuard guard, Consumer<ILLMPrompt> inputCheck) {
        var context = new ToolInvocationContext(parent.id(), EntitySnapshot.of(EntityId.random(), ComponentPatch.empty()), parent);
        return json.generateWithCorrection(request, context, settings(limits), guard, observed::add, inputCheck);
    }

    /** @return 记录每次真实模型准入的守卫 */
    private ILLMInteractionGuard guard() {
        return ILLMInteractionGuard.asynchronous(context -> {
            checks.incrementAndGet();
            return CompletableFuture.completedStage(null);
        });
    }

    /**
     * @param text 受控响应正文
     * @param finish 供应商终止原因
     * @return 含真实用量字段的模型响应
     */
    private ILLMResult response(String text, String finish) {
        return LLMResult.builder().model("test").usage(LLMUsage.builder()
                .promptTokens(10).completionTokens(10).totalTokens(20).build())
                .choices(List.of(LLMChoice.builder().index(0).finishReason(finish)
                        .message(LLMAssistantMessage.builder().contents(new LLMContentListBuilder()
                                .addText(text).build()).build()).build())).build();
    }

    /**
     * @param text 本次受控正文
     */
    private void complete(String text) {
        replies.getLast().complete(response(text, "stop"));
        drained.getLast().complete(null);
        runtime.runAll();
    }

    /** 缺失 context 时反馈真实字段和完整 Schema；实际排空后才开始纠错，来源不被改写。 */
    @Test
    void repairsMissingNestedFieldAfterPhysicalCompletion() {
        try (var parent = runtime.parent(new LLMCallLimits(5, 0, 0))) {
            var inputs = new ArrayList<ILLMPrompt>();
            var pending = start(parent, new LLMCallLimits(2, 0, 0), guard(), inputs::add);
            runtime.runAll();
            replies.getFirst().complete(response(INVALID, "stop"));
            runtime.runAll();
            assertEquals(1, prompts.size());
            assertFalse(pending.toCompletableFuture().isDone());
            drained.getFirst().complete(null);
            runtime.runAll();
            assertEquals(2, prompts.size());
            var feedback = mapper.writeValueAsString(prompts.getLast().getMessages());
            assertTrue(feedback.contains("$.styles[0].context：缺少必需字段"));
            assertTrue(feedback.contains("TECHNICAL"));
            assertTrue(feedback.contains("additionalProperties"));
            assertFalse(feedback.contains("private-model-value"));
            assertEquals(2, request.prompt().getMessages().size());
            assertEquals(2, inputs.size());
            complete(VALID);
            assertTrue(pending.toCompletableFuture().join().styles().isEmpty());
            assertEquals(2, checks.get());
            assertEquals(2, observed.size());
            assertEquals(2, parent.budget().used(LLMCallLimits.MODEL_REQUESTS));
            assertEquals(0, parent.budget().used(LLMCallLimits.TOOL_EXECUTIONS));
            parent.checkActive();
        }
        assertEquals(0, runtime.pendingTimers());
    }

    /** 连续格式错误最多两次，不使用父预算的额外空位继续无限纠错。 */
    @Test
    void repeatedInvalidOutputStopsAfterOneCorrection() {
        try (var parent = runtime.parent(new LLMCallLimits(8, 0, 0))) {
            var pending = start(parent, new LLMCallLimits(8, 0, 0), guard(), ignored -> { });
            runtime.runAll();
            complete(INVALID);
            complete(INVALID);
            var failure = assertThrows(CompletionException.class, () -> pending.toCompletableFuture().join());
            assertEquals(LLMJsonException.Code.JSON_SHAPE, assertInstanceOf(LLMJsonException.class, failure.getCause()).code());
            assertEquals(2, prompts.size());
        }
        assertEquals(0, runtime.pendingTimers());
    }

    /**
     * @param sharedLimit 是否由共享父预算限制为一次
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void correctionCannotResetLocalOrSharedRequestBudget(boolean sharedLimit) {
        try (var parent = runtime.parent(new LLMCallLimits(sharedLimit ? 1 : 4, 0, 0))) {
            var pending = start(parent, new LLMCallLimits(sharedLimit ? 4 : 1, 0, 0), guard(), ignored -> { });
            runtime.runAll();
            complete(INVALID);
            assertThrows(CompletionException.class, () -> pending.toCompletableFuture().join());
            assertEquals(1, prompts.size());
            assertEquals(1, parent.budget().used(LLMCallLimits.MODEL_REQUESTS));
        }
    }

    /** 纠错沿用剩余局部 token 额度，即使父额度宽松也不得重置为初始数额。 */
    @Test
    void correctionRetainsSpentLocalTokenBudget() {
        try (var parent = runtime.parent(new LLMCallLimits(4, 0, 0, 1000L))) {
            var pending = start(parent, new LLMCallLimits(2, 0, 0, 40L), guard(), ignored -> { });
            runtime.runAll();
            complete(INVALID);
            var failure = assertThrows(CompletionException.class, () -> pending.toCompletableFuture().join());
            assertEquals(LLMInteractionResult.Status.BUDGET_EXCEEDED,
                    assertInstanceOf(LLMJsonException.class, failure.getCause()).status());
            assertEquals(1, prompts.size());
            assertEquals(20, parent.budget().used(LLMCallLimits.TOTAL_TOKENS));
        }
    }

    /**
     * @param finish 不能作为结构纠错依据的模型终态
     */
    @ParameterizedTest
    @ValueSource(strings = {"length", "content_filter"})
    void truncatedOrRefusedOutputIsNotRetried(String finish) {
        try (var parent = runtime.parent(new LLMCallLimits(2, 0, 0))) {
            var pending = start(parent, new LLMCallLimits(2, 0, 0), guard(), ignored -> { });
            runtime.runAll();
            replies.getFirst().complete(response(INVALID, finish));
            drained.getFirst().complete(null);
            runtime.runAll();
            assertThrows(CompletionException.class, () -> pending.toCompletableFuture().join());
            assertEquals(1, prompts.size());
        }
    }

    /**
     * 传输失败或用户取消不能被当作格式错误；迟到正文不交付，仍等待实际资源释放。
     * @param cancelled 是否由父控制主动取消
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void transportFailureAndCancellationDoNotTriggerCorrection(boolean cancelled) {
        try (var parent = runtime.parent(new LLMCallLimits(2, 0, 0))) {
            var pending = start(parent, new LLMCallLimits(2, 0, 0), guard(), ignored -> { });
            runtime.runAll();
            if (cancelled) {
                parent.cancel();
                replies.getFirst().complete(response(INVALID, "stop"));
            } else {
                replies.getFirst().completeExceptionally(new IllegalStateException("合成传输失败。"));
            }
            runtime.runAll();
            assertFalse(pending.toCompletableFuture().isDone());
            drained.getFirst().complete(null);
            runtime.runAll();
            assertThrows(CompletionException.class, () -> pending.toCompletableFuture().join());
            assertEquals(1, prompts.size());
        }
        assertEquals(0, runtime.pendingTimers());
    }

    /** 首次花费十八秒后，纠错仍只能使用共同三十秒内的余量，取消结果必须等待真实排空。 */
    @Test
    void correctionDoesNotExtendDeadlineOrReleaseRunningModel() {
        try (var parent = runtime.parent(new LLMCallLimits(2, 0, 0))) {
            var pending = start(parent, new LLMCallLimits(2, 0, 0), guard(), ignored -> { });
            runtime.runAll();
            runtime.advance(Duration.ofSeconds(18));
            complete(INVALID);
            assertEquals(2, prompts.size());
            runtime.advance(Duration.ofSeconds(12));
            assertFalse(pending.toCompletableFuture().isDone());
            complete(VALID);
            var failure = assertThrows(CompletionException.class, () -> pending.toCompletableFuture().join());
            assertEquals(ExecutionControl.StopReason.DEADLINE_EXCEEDED,
                    assertInstanceOf(ExecutionStoppedException.class, failure.getCause()).reason());
            parent.checkActive();
        }
        assertEquals(0, runtime.pendingTimers());
    }

    /** 纠错追加内容重新检查输入容量，准入撤销也必须在第二次模型调用前拒绝。 */
    @Test
    void rechecksExpandedInputAndDynamicPermission() {
        for (boolean rejectInput : List.of(true, false)) {
            prompts.clear();
            replies.clear();
            drained.clear();
            var preflights = new AtomicInteger();
            var permissions = new AtomicInteger();
            try (var parent = runtime.parent(new LLMCallLimits(2, 0, 0))) {
                var pending = start(parent, new LLMCallLimits(2, 0, 0),
                        ILLMInteractionGuard.asynchronous(context -> {
                            if (permissions.incrementAndGet() == 2) {
                                throw new SecurityException("测试准入已撤销。");
                            }
                            return CompletableFuture.completedStage(null);
                        }), prompt -> {
                            if (preflights.incrementAndGet() == 2 && rejectInput) {
                                throw new IllegalArgumentException("测试完整输入超过上限。");
                            }
                        });
                runtime.runAll();
                complete(INVALID);
                assertThrows(CompletionException.class, () -> pending.toCompletableFuture().join());
                assertEquals(2, preflights.get());
                assertEquals(1, prompts.size());
            }
        }
    }
}
