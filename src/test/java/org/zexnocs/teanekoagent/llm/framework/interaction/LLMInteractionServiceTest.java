package org.zexnocs.teanekoagent.llm.framework.interaction;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.zexnocs.teanekoagent.llm.framework.input.LLMPrompt;
import org.zexnocs.teanekoagent.llm.framework.input.interfaces.ILLMPrompt;
import org.zexnocs.teanekoagent.llm.framework.message.LLMAssistantMessage;
import org.zexnocs.teanekoagent.llm.framework.message.LLMContent;
import org.zexnocs.teanekoagent.llm.framework.message.LLMToolMessage;
import org.zexnocs.teanekoagent.llm.framework.message.LLMUserMessage;
import org.zexnocs.teanekoagent.llm.framework.message.content.LLMContentListBuilder;
import org.zexnocs.teanekoagent.llm.framework.message.content.TextLLMContentPart;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMMessage;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMToolMessage;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptions;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.ILLMModelService;
import org.zexnocs.teanekoagent.llm.framework.response.LLMChoice;
import org.zexnocs.teanekoagent.llm.framework.response.LLMResult;
import org.zexnocs.teanekoagent.llm.framework.response.LLMUsage;
import org.zexnocs.teanekoagent.llm.framework.response.interfaces.ILLMResult;
import org.zexnocs.teanekoagent.llm.framework.tool.*;
import org.zexnocs.teanekoagent.llm.framework.tool.interfaces.IContextualToolExecutor;
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
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.zexnocs.teanekoagent.llm.framework.interaction.LLMInteractionResult.Status.*;

/**
 * 用真实阶段执行器验证自动往返、协议配对、深层快照和所有主要终止路径。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
class LLMInteractionServiceTest {
    private final InteractionRuntime runtime = new InteractionRuntime();
    private final ILLMModelService models = mock(ILLMModelService.class);
    private final List<ILLMPrompt> prompts = new ArrayList<>();
    private final List<CompletableFuture<ILLMResult>> replies = new ArrayList<>();
    private final LLMInteractionSettings settings = LLMInteractionSettings.defaults();
    private final LLMInteractionService service = new LLMInteractionService(models,
            LLMInteractionSettings::defaults, runtime, runtime.scheduler);

    /** 登记每次真实模型尝试，由用例手动完成供应商响应。 */
    LLMInteractionServiceTest() {
        when(models.call(any(ILLMPrompt.class), any(ExecutionPolicy.class))).thenAnswer(invocation -> {
            prompts.add(invocation.getArgument(0));
            var pending = new CompletableFuture<ILLMResult>();
            replies.add(pending);
            return new TaskFuture<>(mock(ILogger.class), "离线交互模型", pending);
        });
    }

    /**
     * 绑定不含可变身份对象的宿主上下文。
     * @param parent 用例拥有的总控制
     * @return 本次可信调用上下文
     */
    private ToolInvocationContext context(ExecutionControl parent) {
        return new ToolInvocationContext(UUID.randomUUID(),
                EntitySnapshot.of(EntityId.random(), ComponentPatch.builder().build()), parent);
    }

    /**
     * 构造一个真实工具白名单。
     * @param action 受控异步工具行为
     * @return 本次固定的工具绑定
     */
    private LLMToolSet tools(IContextualToolExecutor action) {
        return new LLMToolSet(new ObjectMapper(), List.of(new LLMToolBinding(
                LLMTool.builder().name("work").description("离线工具").build(), action)));
    }

    /**
     * 构造可观察工具调用 ID 的有效零参数请求。
     * @param id 唯一调用 ID
     * @return 可变原始模型调用 DTO
     */
    private LLMToolCall call(String id) {
        return LLMToolCall.builder().id(id).name("work").arguments("{}").build();
    }

    /**
     * 构造纯文本初始输入。
     * @return 不含工具历史的 Prompt
     */
    private LLMPrompt prompt() {
        return new LLMPrompt(LLMUserMessage.builder()
                .contents(new LLMContentListBuilder().addText("离线输入").build()).build());
    }

    /**
     * 构造具有透明状态和明确结束原因的单候选响应。
     * @param reason 统一结束标识
     * @param text 可见正文
     * @param calls 模型工具请求
     * @return 原始可变供应商结果
     */
    private LLMResult response(String reason, String text, ILLMToolCall... calls) {
        return LLMResult.builder().model("受控模型")
                .usage(LLMUsage.builder().promptTokens(10).completionTokens(2).totalTokens(12).build())
                .choices(List.of(LLMChoice.builder().index(0).finishReason(reason)
                        .message(LLMAssistantMessage.builder()
                                .contents(new LLMContentListBuilder().addText(text).build())
                                .toolCalls(List.of(calls)).providerMetadata(Map.of("state", List.of(Map.of("opaque", "回放状态"))))
                                .build()).build())).build();
    }

    /**
     * 完成当前模型响应并推进后续工具/模型队列。
     * @param response 本次模型结果
     */
    private void reply(ILLMResult response) {
        replies.getLast().complete(response);
        runtime.runAll();
    }

    /** 验证两轮六个工具、三次模型、严格 ID 回放及逐次 usage。 */
    @Test void completesMultipleRoundsWithOrderedToolsAndProviderState() {
        var effects = new ArrayList<String>();
        try (var parent = runtime.parent(settings.limits())) {
            var pending = service.interact(prompt(), tools((call, context) -> {
                effects.add(call.getId());
                return CompletableFuture.completedStage("结果-" + call.getId());
            }), context(parent));
            runtime.runAll();
            reply(response("tool_calls", "提前答案不作为最终答案", call("a"), call("b"), call("c")));
            reply(response("tool_calls", "", call("d"), call("e"), call("f")));
            assertEquals("none", prompts.getLast().getOptions().getToolChoice());
            assertTrue(prompts.getLast().getOptions().getTools().isEmpty());
            reply(response("stop", "最终答案"));
            var result = pending.toCompletableFuture().join();
            assertEquals(SUCCESS, result.status());
            assertEquals(3, result.modelAttempts());
            assertEquals(List.of("a", "b", "c", "d", "e", "f"), effects);
            assertEquals(10, result.transcript().size());
            assertEquals(result.transcript(), result.committableTranscript().orElseThrow());
            assertEquals(36, result.attempts().stream().mapToInt(attempt -> attempt.usage().orElseThrow().getTotalTokens()).sum());
            var replay = prompts.get(1).getMessages();
            assertEquals(List.of("a", "b", "c"), replay.subList(2, 5).stream()
                    .map(message -> ((ILLMToolMessage) message).getToolCallId()).toList());
            assertEquals(Map.of("state", List.of(Map.of("opaque", "回放状态"))), replay.get(1).getProviderMetadata());
            assertEquals(1, runtime.pendingTimers());
            assertDoesNotThrow(parent::checkActive);
        }
        assertEquals(0, runtime.pendingTimers());
    }

    /** 验证末次违规请求工具不会产生额外模型尝试或副作用。 */
    @Test void stopsWhenFinalModelStillRequestsTools() {
        var once = new LLMInteractionSettings(new LLMCallLimits(1, 2, 6),
                settings.modelTimeout(), settings.toolTimeout(), settings.turnTimeout());
        var effects = new AtomicInteger();
        try (var parent = runtime.parent(settings.limits())) {
            var pending = service.interact(prompt(), tools((call, context) -> {
                effects.incrementAndGet();
                return CompletableFuture.completedStage("结果");
            }), context(parent), once);
            runtime.runAll();
            assertEquals("none", prompts.getFirst().getOptions().getToolChoice());
            reply(response("tool_calls", "", call("a")));
            var result = pending.toCompletableFuture().join();
            assertEquals(BUDGET_EXCEEDED, result.status());
            assertEquals(1, result.modelAttempts());
            assertEquals(0, effects.get());
            assertTrue(result.finalCandidate().isEmpty());
            assertTrue(result.committableTranscript().isEmpty());
        }
    }

    /** 验证跨轮重复 ID 在第二次执行前被拒绝。 */
    @Test void rejectsRepeatedToolIdAcrossResponses() {
        var effects = new AtomicInteger();
        try (var parent = runtime.parent(settings.limits())) {
            var pending = service.interact(prompt(), tools((call, context) -> {
                effects.incrementAndGet();
                return CompletableFuture.completedStage("完成");
            }), context(parent));
            runtime.runAll();
            reply(response("tool_calls", "", call("a")));
            reply(response("tool_calls", "", call("a")));
            var result = pending.toCompletableFuture().join();
            assertEquals(INVALID_RESPONSE, result.status());
            assertEquals(1, effects.get());
            assertEquals(2, result.modelAttempts());
            assertTrue(result.committableTranscript().isEmpty());
        }
    }

    /** 验证同一批重复 ID 不会先执行其中第一项。 */
    @Test void validatesWholeToolBatchBeforeAnySideEffect() {
        try (var parent = runtime.parent(settings.limits())) {
            var pending = service.interact(prompt(), tools((call, context) -> {
                fail("非法批次不能进入工具。");
                return null;
            }), context(parent));
            runtime.runAll();
            reply(response("tool_calls", "", call("a"), call("a")));
            assertEquals(INVALID_RESPONSE, pending.toCompletableFuture().join().status());
        }
    }

    /**
     * 验证可观察的终止状态，不把空白、拒绝、截断或非法结束标识算作成功。
     * @param reason 结束标识
     * @param text 输出正文
     * @param expected 预期终止状态
     */
    @ParameterizedTest
    @CsvSource({"stop,答案,SUCCESS", "stop,' ',EMPTY_OUTPUT", "length,半句,TRUNCATED",
            "max_output_tokens,半句,TRUNCATED", "content_filter,拒绝,REFUSED", "refusal,拒绝,REFUSED",
            "tool_calls,缺少调用,INVALID_RESPONSE", "unknown,未知状态,INVALID_RESPONSE",
            "failed,供应商失败,FAILED", "cancelled,供应商取消,CANCELLED"})
    void distinguishesResponseTerminations(String reason, String text, LLMInteractionResult.Status expected) {
        try (var parent = runtime.parent(settings.limits())) {
            var pending = service.interact(prompt(), new LLMToolSet(new ObjectMapper(), List.of()), context(parent));
            runtime.runAll();
            reply(response(reason, text));
            var result = pending.toCompletableFuture().join();
            assertEquals(expected, result.status());
            assertEquals(1, result.modelAttempts());
            assertEquals(expected == SUCCESS, result.committableTranscript().isPresent());
        }
    }

    /** 验证空候选和缺失 usage 不被伪造成正常答案或已知 token 数据。 */
    @Test void preservesMissingUsageAndEmptyChoices() {
        try (var parent = runtime.parent(settings.limits())) {
            var pending = service.interact(prompt(), new LLMToolSet(new ObjectMapper(), List.of()), context(parent));
            runtime.runAll();
            reply(LLMResult.builder().usage(null).build());
            var result = pending.toCompletableFuture().join();
            assertEquals(EMPTY_OUTPUT, result.status());
            assertTrue(result.attempts().getFirst().usage().isEmpty());
        }
    }

    /** 验证多个候选不能隐式选取一条执行有副作用的工具。 */
    @Test void rejectsAmbiguousChoicesBeforeExecutingTools() {
        try (var parent = runtime.parent(settings.limits())) {
            var pending = service.interact(prompt(), tools((call, context) -> {
                fail("多候选不能执行工具。");
                return null;
            }), context(parent));
            runtime.runAll();
            var raw = response("tool_calls", "", call("a"));
            raw.setChoices(List.of(raw.getChoices().getFirst(), raw.getChoices().getFirst()));
            reply(raw);
            assertEquals(INVALID_RESPONSE, pending.toCompletableFuture().join().status());
        }
    }

    /** 验证未配对的初始工具历史在模型请求前拒绝。 */
    @Test void rejectsOrphanAndIncompleteInitialHistory() {
        try (var parent = runtime.parent(settings.limits())) {
            var tools = new LLMToolSet(new ObjectMapper(), List.of());
            var orphan = LLMToolMessage.builder().name("work").toolCallId("a").contents(List.of()).build();
            var incomplete = response("tool_calls", "", call("a")).getFirstMessage().orElseThrow();
            assertEquals(INVALID_INPUT, service.interact(new LLMPrompt(orphan), tools, context(parent))
                    .toCompletableFuture().join().status());
            assertEquals(INVALID_INPUT, service.interact(new LLMPrompt(incomplete), tools, context(parent))
                    .toCompletableFuture().join().status());
            runtime.runAll();
            verifyNoInteractions(models);
        }
    }

    /** 验证已经配对的历史可回放，但其 ID 不能在新响应中重复执行。 */
    @Test void rejectsReplayOfToolIdFromInitialHistory() {
        try (var parent = runtime.parent(settings.limits())) {
            var history = new ArrayList<ILLMMessage>(prompt().getMessages());
            history.add(response("tool_calls", "", call("old")).getFirstMessage().orElseThrow());
            history.add(LLMToolMessage.builder().name("work").toolCallId("old")
                    .contents(new LLMContentListBuilder().addText("已执行").build()).build());
            var pending = service.interact(new LLMPrompt(history), tools((call, context) -> {
                fail("历史 ID 不能重放。");
                return null;
            }), context(parent));
            runtime.runAll();
            reply(response("tool_calls", "", call("old")));
            assertEquals(INVALID_RESPONSE, pending.toCompletableFuture().join().status());
        }
    }

    /** 验证输入、选项及供应商响应在排队后均不再受原始可变数据影响。 */
    @Test @SuppressWarnings("unchecked")
    void freezesNestedInputOptionsAndResponseState() {
        try (var parent = runtime.parent(settings.limits())) {
            var text = new TextLLMContentPart("原始正文");
            var state = new ArrayList<Object>(Arrays.asList("原始状态", null));
            var metadata = new LinkedHashMap<String, Object>();
            metadata.put("state", state);
            metadata.put("array", new int[]{1, 2});
            var input = LLMUserMessage.builder().contents(List.of(LLMContent.builder().type("text").contentPart(text).build()))
                    .providerMetadata(metadata).build();
            var options = LLMModelOptions.builder().metadata(metadata).build();
            var pending = service.interact(new LLMPrompt(input, options), tools((call, context) ->
                    CompletableFuture.completedStage("结果")), context(parent));
            text.setText("被修改");
            state.set(0, "被修改");
            options.setModel("被修改");
            runtime.runAll();
            assertEquals("原始正文", ((TextLLMContentPart) prompts.getFirst().getMessages().getFirst()
                    .getContents().getFirst().getContentPart()).getText());
            assertEquals("原始状态", ((List<?>) prompts.getFirst().getOptions().getMetadata().get("state")).getFirst());
            assertTrue(prompts.getFirst().getOptions().findModel().isEmpty());
            var raw = response("tool_calls", "", call("a"));
            replies.getLast().complete(raw);
            ((LLMAssistantMessage) raw.getFirstMessage().orElseThrow()).setProviderMetadata(Map.of("state", "被修改"));
            ((LLMToolCall) raw.getFirstMessage().orElseThrow().getToolCalls().getFirst()).setId("被修改");
            runtime.runAll();
            assertEquals("a", ((ILLMToolMessage) prompts.getLast().getMessages().getLast()).getToolCallId());
            reply(response("stop", "答案"));
            var result = pending.toCompletableFuture().join();
            var frozen = result.transcript().getFirst();
            assertThrows(UnsupportedOperationException.class, () -> ((TextLLMContentPart) frozen.getContents()
                    .getFirst().getContentPart()).setText("不能修改"));
            assertThrows(UnsupportedOperationException.class, () -> ((List<Object>) frozen.getProviderMetadata()
                    .get("state")).set(0, "不能修改"));
            assertThrows(UnsupportedOperationException.class, () -> result.transcript().clear());
            assertEquals(List.of(1, 2), frozen.getProviderMetadata().get("array"));
            assertNull(((List<?>) frozen.getProviderMetadata().get("state")).get(1));
        }
    }

    /** 验证任意运行对象、循环和凭据均无法进入可导出的快照。 */
    @Test void rejectsRuntimeObjectsCyclesAndCredentialsWithoutCallingModel() {
        try (var parent = runtime.parent(settings.limits())) {
            var cycle = new HashMap<String, Object>();
            cycle.put("self", cycle);
            for (var metadata : List.of(Map.<String, Object>of("state", parent), cycle,
                    Map.<String, Object>of("state", Double.NaN),
                    Map.<String, Object>of("nested", Map.of("api-key", "测试敏感值")))) {
                var options = LLMModelOptions.builder().metadata(metadata).build();
                var pending = service.interact(new LLMPrompt(prompt().getMessages(), options),
                        new LLMToolSet(new ObjectMapper(), List.of()), context(parent));
                assertEquals(INVALID_INPUT, pending.toCompletableFuture().join().status());
            }
            runtime.runAll();
            verifyNoInteractions(models);
        }
    }

    /** 验证在途模型取消会及时完成结果，迟到响应不推进工具或后续请求。 */
    @Test void cancelsPendingModelAndIgnoresLateResponse() {
        try (var parent = runtime.parent(settings.limits())) {
            var pending = service.interact(prompt(), tools((call, context) -> {
                fail("取消后不能执行工具。");
                return null;
            }), context(parent));
            runtime.runAll();
            parent.cancel();
            var result = pending.toCompletableFuture().join();
            assertEquals(CANCELLED, result.status());
            assertEquals(1, result.modelAttempts());
            assertEquals(1, result.attempts().size());
            reply(response("tool_calls", "", call("a")));
            assertEquals(1, prompts.size());
            assertEquals(0, runtime.pendingTimers());
        }
    }

    /** 验证排队阶段超时不会发起模型端口或消耗次数。 */
    @Test void timesOutQueuedModelWithoutSpendingAttempt() {
        try (var parent = runtime.parent(settings.limits())) {
            var pending = service.interact(prompt(), new LLMToolSet(new ObjectMapper(), List.of()), context(parent));
            runtime.advance(Duration.ofSeconds(60));
            var result = pending.toCompletableFuture().join();
            assertEquals(TIMED_OUT, result.status());
            assertEquals(0, result.modelAttempts());
            assertTrue(result.attempts().isEmpty());
            runtime.runAll();
            verifyNoInteractions(models);
            assertEquals(1, runtime.pendingTimers());
        }
    }

    /** 验证已开始的模型失败计入尝试且不自动重试，不输出原始异常文本。 */
    @Test void recordsFailedAttemptWithoutRetryOrRawException() {
        try (var parent = runtime.parent(settings.limits())) {
            var pending = service.interact(prompt(), new LLMToolSet(new ObjectMapper(), List.of()), context(parent));
            runtime.runAll();
            replies.getLast().completeExceptionally(new IllegalStateException("测试敏感异常"));
            runtime.runAll();
            var result = pending.toCompletableFuture().join();
            assertEquals(FAILED, result.status());
            assertEquals(1, result.modelAttempts());
            assertTrue(result.attempts().getFirst().response().isEmpty());
            assertFalse(result.toString().contains("测试敏感异常"));
            assertEquals(1, prompts.size());
        }
    }

    /** 验证工具阶段超时可以回填后继续收束，迟到结果不改变既有输出。 */
    @Test void recoversToolTimeoutIntoNextModelPrompt() {
        var tool = new CompletableFuture<String>();
        try (var parent = runtime.parent(settings.limits())) {
            var pending = service.interact(prompt(), tools((call, context) -> tool), context(parent));
            runtime.runAll();
            reply(response("tool_calls", "", call("a")));
            runtime.advance(Duration.ofSeconds(15));
            runtime.runAll();
            assertEquals(2, prompts.size());
            reply(response("stop", "工具暂时不可用"));
            var result = pending.toCompletableFuture().join();
            assertEquals(SUCCESS, result.status());
            assertEquals(ToolInvocationResult.Status.TIMED_OUT, result.tools().getFirst().status());
            tool.complete("迟到结果");
            assertFalse(result.tools().getFirst().output().contains("迟到结果"));
        }
    }

    /** 验证整轮时限会停止未完成工具，不继续请求模型。 */
    @Test void turnTimeoutStopsPendingToolsAndFinalModel() {
        var shortTurn = new LLMInteractionSettings(settings.limits(), Duration.ofSeconds(60),
                Duration.ofSeconds(15), Duration.ofSeconds(5));
        try (var parent = runtime.parent(settings.limits())) {
            var pending = service.interact(prompt(), tools((call, context) -> new CompletableFuture<>()),
                    context(parent), shortTurn);
            runtime.runAll();
            reply(response("tool_calls", "", call("a"), call("b")));
            runtime.advance(Duration.ofSeconds(5));
            runtime.runAll();
            var result = pending.toCompletableFuture().join();
            assertEquals(TIMED_OUT, result.status());
            assertEquals(1, prompts.size());
            assertTrue(result.committableTranscript().isEmpty());
            assertEquals(1, runtime.pendingTimers());
        }
    }

    /** 验证共享额度在批次中途耗尽时保留已完成动作，并停止余项。 */
    @Test void retainsPartialToolDiagnosticsWhenParentBudgetIsConsumedElsewhere() {
        var first = new CompletableFuture<String>();
        var effects = new AtomicInteger();
        var limits = new LLMCallLimits(3, 2, 2);
        try (var parent = runtime.parent(limits)) {
            var pending = service.interact(prompt(), tools((call, context) -> {
                effects.incrementAndGet();
                return first;
            }), context(parent));
            runtime.runAll();
            reply(response("tool_calls", "", call("a"), call("b")));
            parent.consume(LLMCallLimits.TOOL_EXECUTIONS, 1);
            first.complete("第一项已产生效果");
            runtime.runAll();
            var result = pending.toCompletableFuture().join();
            assertEquals(BUDGET_EXCEEDED, result.status());
            assertEquals(1, effects.get());
            assertEquals(1, result.tools().size());
            assertEquals(3, result.transcript().size());
            assertEquals("a", ((ILLMToolMessage) result.transcript().getLast()).getToolCallId());
            assertTrue(result.committableTranscript().isEmpty());
        }
    }

    /** 验证同请求中的第二个交互不会重置父模型预算。 */
    @Test void sharesParentBudgetAcrossInteractions() {
        try (var parent = runtime.parent(new LLMCallLimits(1, 0, 0))) {
            var tools = new LLMToolSet(new ObjectMapper(), List.of());
            var first = service.interact(prompt(), tools, context(parent));
            runtime.runAll();
            reply(response("stop", "首个答案"));
            assertEquals(SUCCESS, first.toCompletableFuture().join().status());
            var second = service.interact(prompt(), tools, context(parent));
            runtime.runAll();
            assertEquals(BUDGET_EXCEEDED, second.toCompletableFuture().join().status());
            assertEquals(1, prompts.size());
        }
    }

    /** 验证显式禁用工具时，模型违规请求不会被归类为基础设施故障。 */
    @Test void distinguishesForbiddenToolRequestFromInfrastructureFailure() {
        try (var parent = runtime.parent(settings.limits())) {
            var options = LLMModelOptions.builder().toolChoice("none").build();
            var pending = service.interact(new LLMPrompt(prompt().getMessages(), options), tools((call, context) -> {
                fail("显式禁用的工具不能执行。");
                return null;
            }), context(parent));
            runtime.runAll();
            reply(response("tool_calls", "", call("a")));
            assertEquals(INVALID_RESPONSE, pending.toCompletableFuture().join().status());
        }
    }

    /** 验证工具阶段无法登记截止时间时报告执行故障，不误报模型协议错误。 */
    @Test void preservesInfrastructureFailureDuringToolScheduling() {
        try (var parent = runtime.parent(settings.limits())) {
            var pending = service.interact(prompt(), tools((call, context) -> {
                fail("调度失败不能进入工具。");
                return null;
            }), context(parent));
            runtime.runAll();
            doThrow(new RejectedExecutionException("测试计时池关闭。"))
                    .when(runtime.scheduler).schedule(org.mockito.ArgumentMatchers.<Callable<Object>>any(), anyLong(), any());
            reply(response("tool_calls", "", call("a")));
            assertEquals(FAILED, pending.toCompletableFuture().join().status());
            assertEquals(1, runtime.pendingTimers());
        }
    }

    /** 验证取消返回视图不会改变根交互，宿主仍能取得真实完成结果。 */
    @Test void exportedStageCannotCancelOrCompleteRootInteraction() {
        try (var parent = runtime.parent(settings.limits())) {
            var pending = service.interact(prompt(), new LLMToolSet(new ObjectMapper(), List.of()), context(parent));
            var view = pending.toCompletableFuture();
            assertTrue(view.cancel(false));
            runtime.runAll();
            reply(response("stop", "真实答案"));
            var result = pending.toCompletableFuture().join();
            assertEquals(SUCCESS, result.status());
            assertEquals(1, result.modelAttempts());
        }
    }

    /** 验证不同请求共享服务时，各自的 transcript、用量和候选相互独立。 */
    @Test void serviceKeepsConcurrentRequestsIndependent() {
        try (var firstParent = runtime.parent(settings.limits()); var secondParent = runtime.parent(settings.limits())) {
            var tools = new LLMToolSet(new ObjectMapper(), List.of());
            var first = service.interact(prompt(), tools, context(firstParent));
            var second = service.interact(prompt(), tools, context(secondParent));
            runtime.runAll();
            replies.get(1).complete(response("stop", "第二个答案"));
            assertTrue(second.toCompletableFuture().isDone());
            assertFalse(first.toCompletableFuture().isDone());
            firstParent.cancel();
            assertEquals(CANCELLED, first.toCompletableFuture().join().status());
            assertEquals(SUCCESS, second.toCompletableFuture().join().status());
            assertEquals(2, second.toCompletableFuture().join().transcript().size());
        }
    }

    /** 验证建立整轮计时失败能返回执行故障并释放父监听，不伪装成输入错误。 */
    @Test void reportsTurnSchedulingFailureWithoutLeakingControl() {
        try (var parent = runtime.parent(settings.limits())) {
            doThrow(new IllegalStateException("测试计时服务不可用。"))
                    .when(runtime.scheduler).schedule(org.mockito.ArgumentMatchers.<Callable<Object>>any(), anyLong(), any());
            var result = service.interact(prompt(), new LLMToolSet(new ObjectMapper(), List.of()), context(parent))
                    .toCompletableFuture().join();
            assertEquals(FAILED, result.status());
            assertEquals(0, result.modelAttempts());
            assertEquals(1, runtime.pendingTimers());
            verifyNoInteractions(models);
            assertDoesNotThrow(parent::checkActive);
        }
    }
}
