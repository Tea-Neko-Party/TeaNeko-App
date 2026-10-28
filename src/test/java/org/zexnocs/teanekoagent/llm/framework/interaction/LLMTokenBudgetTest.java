package org.zexnocs.teanekoagent.llm.framework.interaction;

import org.junit.jupiter.api.Test;
import org.zexnocs.teanekoagent.llm.framework.input.LLMPrompt;
import org.zexnocs.teanekoagent.llm.framework.input.interfaces.ILLMPrompt;
import org.zexnocs.teanekoagent.llm.framework.message.LLMAssistantMessage;
import org.zexnocs.teanekoagent.llm.framework.message.LLMUserMessage;
import org.zexnocs.teanekoagent.llm.framework.message.content.LLMContentListBuilder;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptions;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.ILLMModelService;
import org.zexnocs.teanekoagent.llm.framework.response.LLMChoice;
import org.zexnocs.teanekoagent.llm.framework.response.LLMResult;
import org.zexnocs.teanekoagent.llm.framework.response.LLMUsage;
import org.zexnocs.teanekoagent.llm.framework.response.interfaces.ILLMResult;
import org.zexnocs.teanekoagent.llm.framework.tool.*;
import org.zexnocs.teanekoagent.llm.framework.tool.interfaces.ILLMToolCall;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.actuator.execution.ExecutionPolicy;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.framework.composition.ComponentPatch;
import org.zexnocs.teanekocore.framework.composition.EntityId;
import org.zexnocs.teanekocore.framework.composition.EntitySnapshot;
import org.zexnocs.teanekocore.logger.ILogger;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.zexnocs.teanekoagent.llm.framework.interaction.LLMCallLimits.TOTAL_TOKENS;
import static org.zexnocs.teanekoagent.llm.framework.interaction.LLMInteractionResult.Status.*;

/**
 * 使用真实预算与阶段执行验证 token 准入、结算、共享、未知用量和取消边界。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
class LLMTokenBudgetTest {
    private final InteractionRuntime runtime = new InteractionRuntime();
    private final ILLMModelService models = mock(ILLMModelService.class);
    private final List<ILLMPrompt> prompts = new ArrayList<>();
    private final List<CompletableFuture<ILLMResult>> replies = new ArrayList<>();
    private final AtomicInteger effects = new AtomicInteger();
    private final ILLMTokenEstimator estimator = mock(ILLMTokenEstimator.class);
    private final LLMInteractionService service = new LLMInteractionService(models,
            LLMInteractionSettings::defaults, runtime, runtime.scheduler, estimator);

    /** 用固定十个输入 token 和二十个输出上限建立可手算的离线模型。 */
    LLMTokenBudgetTest() {
        when(estimator.estimate(any())).thenReturn(new LLMTokenEstimate(10, "fixed-v1"));
        when(models.resolveOptions(any())).thenAnswer(invocation -> {
            ILLMPrompt prompt = invocation.getArgument(0);
            return LLMModelOptions.merge(LLMModelOptions.builder().provider("test").model("test")
                    .maxTokens(20).build(), prompt.getOptions());
        });
        when(models.call(any(ILLMPrompt.class), any(ExecutionPolicy.class))).thenAnswer(invocation -> {
            prompts.add(invocation.getArgument(0));
            var future = new CompletableFuture<ILLMResult>(); replies.add(future);
            return new TaskFuture<>(mock(ILogger.class), "token 离线模型", future);
        });
    }

    /**
     * 构造可选局部 token 配置，次数与时间沿用计划默认值。
     * @param tokens 局部上限，空值仍继承父限制
     * @return 本轮设置
     */
    private LLMInteractionSettings settings(Long tokens) {
        return new LLMInteractionSettings(new LLMCallLimits(3, 2, 6, tokens), Duration.ofSeconds(60),
                Duration.ofSeconds(15), Duration.ofSeconds(120));
    }

    /**
     * 创建独立请求的宿主预算与时钟。
     * @param tokens 可选父总上限
     * @return 用例负责关闭的宿主控制
     */
    private ExecutionControl parent(Long tokens) { return runtime.parent(new LLMCallLimits(6, 4, 12, tokens)); }

    /**
     * 发起带真实工具绑定的交互，不为子交互创建新父总量。
     * @param parent 共享宿主控制
     * @param tokens 局部 token 上限
     * @return 本次交互结果阶段
     */
    private CompletionStage<LLMInteractionResult> start(ExecutionControl parent, Long tokens) {
        var context = new ToolInvocationContext(UUID.randomUUID(),
                EntitySnapshot.of(EntityId.random(), ComponentPatch.builder().build()), parent);
        var tools = new LLMToolSet(new ObjectMapper(), List.of(new LLMToolBinding(
                LLMTool.builder().name("work").build(), (call, trusted) -> {
                    effects.incrementAndGet();
                    return CompletableFuture.completedStage("工具结果");
                })));
        return service.interact(new LLMPrompt(LLMUserMessage.builder()
                .contents(new LLMContentListBuilder().addText("离线输入").build()).build()), tools, context, settings(tokens));
    }

    /**
     * 构造供应商报告或缺失用量的统一响应。
     * @param tokens 报告总量，空值表示未提供 usage
     * @param calls 本次工具调用
     * @return 可由用例主动完成的模型响应
     */
    private LLMResult response(Integer tokens, ILLMToolCall... calls) {
        return LLMResult.builder().usage(tokens == null ? null : LLMUsage.builder().totalTokens(tokens).build())
                .choices(List.of(LLMChoice.builder().index(0).finishReason(calls.length == 0 ? "stop" : "tool_calls")
                        .message(LLMAssistantMessage.builder().toolCalls(List.of(calls))
                                .contents(new LLMContentListBuilder().addText("答案").build()).build()).build())).build();
    }

    /**
     * 创建一个合法且可观察副作用的零参数调用。
     * @param id 本轮唯一调用 ID
     * @return 模型工具调用
     */
    private LLMToolCall call(String id) { return LLMToolCall.builder().id(id).name("work").arguments("{}").build(); }

    /**
     * 完成当前模型并推进后续排队阶段。
     * @param result 本次供应商结果
     */
    private void reply(ILLMResult result) { replies.getLast().complete(result); runtime.runAll(); }

    /** 验证三次模型往返逐次退还未用预留，最终计入供应商报告总量。 */
    @Test void settlesReportedUsageAcrossCompleteToolLoop() {
        try (var parent = parent(100L)) {
            var pending = start(parent, 100L); runtime.runAll();
            assertEquals(30, parent.budget().used(TOTAL_TOKENS));
            reply(response(15, call("a")));
            assertEquals(45, parent.budget().used(TOTAL_TOKENS));
            reply(response(15, call("b")));
            assertEquals("none", prompts.getLast().getOptions().getToolChoice());
            reply(response(15));
            var result = pending.toCompletableFuture().join();
            assertEquals(SUCCESS, result.status());
            assertEquals(3, result.modelAttempts());
            assertEquals(2, effects.get());
            assertEquals(45, result.budgetUsage().get(TOTAL_TOKENS).used());
            assertEquals(45, parent.budget().used(TOTAL_TOKENS));
            assertTrue(result.attempts().stream().allMatch(attempt ->
                    attempt.tokenUsage().orElseThrow().source() == LLMTokenUsage.Source.REPORTED));
        }
    }

    /** 验证局部上限收紧实际模型输出参数，不能扩大父总额度。 */
    @Test void clampsOutputToRemainingLocalBudget() {
        try (var parent = parent(100L)) {
            var pending = start(parent, 17L); runtime.runAll();
            assertEquals(7, prompts.getLast().getOptions().getMaxTokens());
            assertEquals(17, parent.budget().used(TOTAL_TOKENS));
            reply(response(12));
            var result = pending.toCompletableFuture().join();
            assertEquals(SUCCESS, result.status());
            assertEquals(12, result.budgetUsage().get(TOTAL_TOKENS).used());
            assertEquals(12, parent.budget().used(TOTAL_TOKENS));
        }
    }

    /** 验证零额度或连输入都无法容纳时不进入模型端口，也不消耗尝试次数。 */
    @Test void rejectsInsufficientAdmissionWithoutModelAttempt() {
        for (long limit : List.of(0L, 10L)) {
            try (var parent = parent(100L)) {
                var pending = start(parent, limit); runtime.runAll();
                var result = pending.toCompletableFuture().join();
                assertEquals(BUDGET_EXCEEDED, result.status());
                assertEquals(TOTAL_TOKENS, result.rejectedBudget().orElseThrow());
                assertEquals(0, result.modelAttempts());
                assertEquals(0, parent.budget().used(TOTAL_TOKENS));
                assertTrue(prompts.isEmpty());
            }
        }
    }

    /** 验证缺失 usage 时保守保留预留，已耗尽额度阻止工具副作用及下一次请求。 */
    @Test void missingUsageIsEstimatedAndCannotExecuteToolsAfterExhaustion() {
        try (var parent = parent(100L)) {
            var pending = start(parent, 30L); runtime.runAll();
            reply(response(null, call("a")));
            var result = pending.toCompletableFuture().join();
            assertEquals(BUDGET_EXCEEDED, result.status());
            var usage = result.attempts().getFirst().tokenUsage().orElseThrow();
            assertEquals(LLMTokenUsage.Source.ESTIMATED, usage.source());
            assertEquals(30, usage.tokens());
            assertEquals("fixed-v1", usage.estimator());
            assertTrue(result.attempts().getFirst().usage().isEmpty());
            assertEquals(0, effects.get());
            assertEquals(1, prompts.size());
        }
    }

    /** 验证已经发起的失败请求不会退还未知消耗，也不会自动重试。 */
    @Test void failedAttemptConservativelyKeepsReservation() {
        try (var parent = parent(100L)) {
            var pending = start(parent, 100L); runtime.runAll();
            replies.getLast().completeExceptionally(new IllegalStateException("测试传输失败。"));
            var result = pending.toCompletableFuture().join();
            assertEquals(FAILED, result.status());
            assertEquals(30, parent.budget().used(TOTAL_TOKENS));
            assertEquals(LLMTokenUsage.Source.ESTIMATED, result.attempts().getFirst().tokenUsage().orElseThrow().source());
            assertEquals(1, prompts.size());
        }
    }

    /** 验证在途取消只结算一次，迟到 usage 不退还已经交付的保守记账。 */
    @Test void cancellationIgnoresLateUsageAndDoesNotSettleTwice() {
        try (var parent = parent(100L)) {
            var pending = start(parent, 100L); runtime.runAll();
            parent.cancel();
            var result = pending.toCompletableFuture().join();
            assertEquals(CANCELLED, result.status());
            reply(response(1));
            assertEquals(30, parent.budget().used(TOTAL_TOKENS));
            assertEquals(30, result.attempts().getFirst().tokenUsage().orElseThrow().tokens());
            assertEquals(0, runtime.pendingTimers());
        }
    }

    /** 验证排队取消发生在预留前时，次数和 token 都保持未消耗。 */
    @Test void queuedCancellationDoesNotReserveTokens() {
        try (var parent = parent(100L)) {
            var pending = start(parent, 100L); parent.cancel(); runtime.runAll();
            assertEquals(CANCELLED, pending.toCompletableFuture().join().status());
            assertEquals(0, parent.budget().used(TOTAL_TOKENS));
            assertTrue(prompts.isEmpty());
            verifyNoInteractions(estimator);
        }
    }

    /** 验证模型阶段超时保守结算预留，释放阶段计时资源。 */
    @Test void modelTimeoutKeepsUnknownReservation() {
        try (var parent = parent(100L)) {
            var pending = start(parent, 100L); runtime.runAll();
            runtime.advance(Duration.ofSeconds(60));
            var result = pending.toCompletableFuture().join();
            assertEquals(TIMED_OUT, result.status());
            assertEquals(30, parent.budget().used(TOTAL_TOKENS));
            assertEquals(1, runtime.pendingTimers());
        }
    }

    /** 验证局部未配置 token 限额时仍继承宿主上限，不能通过新交互重置。 */
    @Test void omittedLocalLimitStillInheritsParentBudget() {
        try (var parent = parent(25L)) {
            var first = start(parent, null); runtime.runAll();
            assertEquals(15, prompts.getLast().getOptions().getMaxTokens());
            reply(response(25, call("a")));
            assertEquals(BUDGET_EXCEEDED, first.toCompletableFuture().join().status());
            var second = start(parent, null); runtime.runAll();
            assertEquals(BUDGET_EXCEEDED, second.toCompletableFuture().join().status());
            assertEquals(1, prompts.size());
            assertEquals(0, effects.get());
        }
    }

    /** 验证并行交互的在途预留共同占用父额度，后发请求不能越过限额。 */
    @Test void simultaneousInteractionsShareInFlightReservations() {
        try (var parent = parent(50L)) {
            var first = start(parent, null); var second = start(parent, null);
            runtime.runAll();
            assertEquals(50, parent.budget().used(TOTAL_TOKENS));
            assertEquals(20, prompts.get(0).getOptions().getMaxTokens());
            assertEquals(10, prompts.get(1).getOptions().getMaxTokens());
            var third = start(parent, null); runtime.runAll();
            assertEquals(BUDGET_EXCEEDED, third.toCompletableFuture().join().status());
            replies.get(0).complete(response(10)); replies.get(1).complete(response(10));
            assertEquals(SUCCESS, first.toCompletableFuture().join().status());
            assertEquals(SUCCESS, second.toCompletableFuture().join().status());
            assertEquals(20, parent.budget().used(TOTAL_TOKENS));
            assertEquals(2, prompts.size());
        }
    }

    /** 验证报告超过预留但未超过总额时补记实际量，不把估算当作供应商事实。 */
    @Test void reportedUsageMayCorrectAnUnderestimateWithinBudget() {
        try (var parent = parent(100L)) {
            var pending = start(parent, 100L); runtime.runAll(); reply(response(50));
            var result = pending.toCompletableFuture().join();
            assertEquals(SUCCESS, result.status());
            var usage = result.attempts().getFirst().tokenUsage().orElseThrow();
            assertEquals(30, usage.reservedTokens()); assertEquals(50, usage.tokens());
            assertFalse(usage.exceeded());
            assertEquals(50, parent.budget().used(TOTAL_TOKENS));
        }
    }

    /** 验证真实报告超额保留响应与事实，封锁宿主后续交互且不执行工具。 */
    @Test void actualOverrunRetainsResponseAndBlocksFurtherEffects() {
        try (var parent = parent(100L)) {
            var pending = start(parent, 100L); runtime.runAll(); reply(response(101, call("a")));
            var result = pending.toCompletableFuture().join();
            assertEquals(BUDGET_EXCEEDED, result.status());
            assertEquals(101, result.attempts().getFirst().usage().orElseThrow().getTotalTokens());
            assertTrue(result.attempts().getFirst().tokenUsage().orElseThrow().exceeded());
            assertEquals(2, result.transcript().size());
            assertEquals(101, parent.budget().used(TOTAL_TOKENS));
            assertEquals(TOTAL_TOKENS, result.rejectedBudget().orElseThrow());
            assertTrue(parent.budget().snapshot().get(TOTAL_TOKENS).exceeded());
            var next = start(parent, null); runtime.runAll();
            assertEquals(BUDGET_EXCEEDED, next.toCompletableFuture().join().status());
            assertEquals(0, effects.get()); assertEquals(1, prompts.size());
        }
    }

    /** 验证非法负数报告不会释放预留或进入正常历史。 */
    @Test void invalidUsageKeepsConservativeChargeAndRejectsResponse() {
        try (var parent = parent(100L)) {
            var pending = start(parent, 100L); runtime.runAll(); reply(response(-1));
            var result = pending.toCompletableFuture().join();
            assertEquals(INVALID_RESPONSE, result.status());
            assertEquals(30, parent.budget().used(TOTAL_TOKENS));
            assertTrue(result.committableTranscript().isEmpty());
        }
    }

    /** 验证没有正输出上限时拒绝预检，不猜测模型默认值或提前扣除次数。 */
    @Test void missingOutputLimitFailsConfigurationBeforeAttempt() {
        doReturn(LLMModelOptions.empty()).when(models).resolveOptions(any());
        try (var parent = parent(100L)) {
            var pending = start(parent, 100L); runtime.runAll();
            var result = pending.toCompletableFuture().join();
            assertEquals(CONFIGURATION_ERROR, result.status());
            assertEquals(0, result.modelAttempts());
            assertEquals(0, parent.budget().used(TOTAL_TOKENS));
            assertTrue(prompts.isEmpty());
        }
    }

    /** 验证未启用总预算时兼容旧端口，不调用预检或估算，缺失报告继续表示未知。 */
    @Test void disabledTokenBudgetPreservesLegacyModelPort() {
        try (var parent = parent(null)) {
            var pending = start(parent, null); runtime.runAll(); reply(response(null));
            var result = pending.toCompletableFuture().join();
            assertEquals(SUCCESS, result.status());
            assertTrue(result.attempts().getFirst().tokenUsage().isEmpty());
            assertFalse(result.budgetUsage().containsKey(TOTAL_TOKENS));
            verify(models, never()).resolveOptions(any()); verifyNoInteractions(estimator);
        }
    }

    /** 验证局部开启 token 预算而宿主未声明时明确拒绝，不创建隐含新总额度。 */
    @Test void localLimitRequiresExplicitParentDimension() {
        try (var parent = parent(null)) {
            var result = start(parent, 100L).toCompletableFuture().join();
            assertEquals(INVALID_INPUT, result.status());
            assertEquals(0, result.modelAttempts());
            verifyNoInteractions(models, estimator);
        }
    }

    /** 验证接近 Long.MAX_VALUE 的输入预留不溢出，实际报告仍可正确退款。 */
    @Test void largeTokenEstimateCannotOverflowAdmission() {
        when(estimator.estimate(any())).thenReturn(new LLMTokenEstimate(Long.MAX_VALUE - 1, "large-v1"));
        try (var parent = parent(Long.MAX_VALUE)) {
            var pending = start(parent, Long.MAX_VALUE); runtime.runAll();
            assertEquals(1, prompts.getLast().getOptions().getMaxTokens());
            assertEquals(Long.MAX_VALUE, parent.budget().used(TOTAL_TOKENS));
            reply(response(20));
            assertEquals(SUCCESS, pending.toCompletableFuture().join().status());
            assertEquals(20, parent.budget().used(TOTAL_TOKENS));
        }
    }

    /** 验证实际默认 JSON 估算器能够贯穿交互预留和供应商 usage 结算。 */
    @Test void defaultJsonEstimatorWorksThroughRealInteraction() {
        var configured = new LLMInteractionService(models, LLMInteractionSettings::defaults, runtime, runtime.scheduler);
        try (var parent = parent(10000L)) {
            var context = new ToolInvocationContext(UUID.randomUUID(),
                    EntitySnapshot.of(EntityId.random(), ComponentPatch.builder().build()), parent);
            var prompt = new LLMPrompt(LLMUserMessage.builder()
                    .contents(new LLMContentListBuilder().addText("默认估算输入").build()).build());
            var pending = configured.interact(prompt, new LLMToolSet(new ObjectMapper(), List.of()), context, settings(10000L));
            runtime.runAll(); reply(response(15));
            var result = pending.toCompletableFuture().join();
            assertEquals(SUCCESS, result.status());
            var usage = result.attempts().getFirst().tokenUsage().orElseThrow();
            assertEquals("utf8-json-v1", usage.estimator());
            assertTrue(usage.reservedTokens() > 30);
            assertEquals(15, usage.tokens());
            assertEquals(15, parent.budget().used(TOTAL_TOKENS));
            verifyNoInteractions(estimator);
        }
    }

    /** 验证空响应是非法协议，已经发起的请求仍保留保守估算消耗。 */
    @Test void nullResponseCannotReleaseReservationOrHangResult() {
        try (var parent = parent(100L)) {
            var pending = start(parent, 100L); runtime.runAll();
            replies.getLast().complete(null);
            var result = pending.toCompletableFuture().join();
            assertEquals(INVALID_RESPONSE, result.status());
            assertEquals(1, result.modelAttempts());
            assertEquals(30, parent.budget().used(TOTAL_TOKENS));
            assertTrue(result.attempts().getFirst().response().isEmpty());
        }
    }

    /** 验证报告字段相加使用长整数，不能通过 int 溢出低估实际报告总量。 */
    @Test void reportedCountersUseLongArithmetic() {
        try (var parent = parent(10000000000L)) {
            var pending = start(parent, 10000000000L); runtime.runAll();
            var result = response(0);
            result.setUsage(LLMUsage.builder().promptTokens(Integer.MAX_VALUE).completionTokens(Integer.MAX_VALUE).build());
            reply(result);
            var completed = pending.toCompletableFuture().join();
            assertEquals(SUCCESS, completed.status());
            assertEquals(2L * Integer.MAX_VALUE, completed.attempts().getFirst().tokenUsage().orElseThrow().tokens());
            assertEquals(2L * Integer.MAX_VALUE, parent.budget().used(TOTAL_TOKENS));
        }
    }

    /** 验证一个在途请求触发超额后，另一个请求退款不能重新开放同一父预算。 */
    @Test void parentOverrunRemainsBlockedAfterConcurrentResponseRefunds() {
        try (var parent = parent(60L)) {
            var first = start(parent, null); var second = start(parent, null); runtime.runAll();
            replies.get(0).complete(response(40));
            assertEquals(BUDGET_EXCEEDED, first.toCompletableFuture().join().status());
            replies.get(1).complete(response(10));
            assertEquals(BUDGET_EXCEEDED, second.toCompletableFuture().join().status());
            assertEquals(50, parent.budget().used(TOTAL_TOKENS));
            assertEquals(0, parent.budget().remaining(TOTAL_TOKENS));
            var third = start(parent, null); runtime.runAll();
            assertEquals(BUDGET_EXCEEDED, third.toCompletableFuture().join().status());
            assertEquals(2, prompts.size());
        }
    }

    /** 验证估算准备也受阶段时限约束，到期后不预留 token 或调用模型。 */
    @Test void timeoutDuringEstimationPreventsReservationAndModelCall() {
        when(estimator.estimate(any())).thenAnswer(invocation -> {
            runtime.advance(Duration.ofSeconds(60));
            return new LLMTokenEstimate(10, "fixed-v1");
        });
        try (var parent = parent(100L)) {
            var pending = start(parent, 100L); runtime.runAll();
            var result = pending.toCompletableFuture().join();
            assertEquals(TIMED_OUT, result.status());
            assertEquals(0, result.modelAttempts());
            assertEquals(0, parent.budget().used(TOTAL_TOKENS));
            assertTrue(prompts.isEmpty());
        }
    }
}
