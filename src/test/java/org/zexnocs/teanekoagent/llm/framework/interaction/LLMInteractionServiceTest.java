package org.zexnocs.teanekoagent.llm.framework.interaction;

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.zexnocs.teanekoagent.llm.framework.input.LLMPrompt;
import org.zexnocs.teanekoagent.llm.framework.input.interfaces.ILLMPrompt;
import org.zexnocs.teanekoagent.llm.framework.message.LLMAssistantMessage;
import org.zexnocs.teanekoagent.llm.framework.message.LLMContent;
import org.zexnocs.teanekoagent.llm.framework.message.LLMToolMessage;
import org.zexnocs.teanekoagent.llm.framework.message.LLMUserMessage;
import org.zexnocs.teanekoagent.llm.framework.message.content.LLMContentListBuilder;
import org.zexnocs.teanekoagent.llm.framework.message.content.TextLLMContentPart;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMToolMessage;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptions;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.ILLMModelService;
import org.zexnocs.teanekoagent.llm.framework.response.LLMChoice;
import org.zexnocs.teanekoagent.llm.framework.response.LLMResult;
import org.zexnocs.teanekoagent.llm.framework.response.LLMUsage;
import org.zexnocs.teanekoagent.llm.framework.response.interfaces.ILLMResult;
import org.zexnocs.teanekoagent.llm.framework.tool.LLMTool;
import org.zexnocs.teanekoagent.llm.framework.tool.LLMToolBinding;
import org.zexnocs.teanekoagent.llm.framework.tool.LLMToolCall;
import org.zexnocs.teanekoagent.llm.framework.tool.LLMToolSet;
import org.zexnocs.teanekoagent.llm.framework.tool.ToolInvocationContext;
import org.zexnocs.teanekoagent.llm.framework.tool.ToolInvocationResult;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.zexnocs.teanekoagent.llm.framework.interaction.LLMInteractionResult.Status.BUDGET_EXCEEDED;
import static org.zexnocs.teanekoagent.llm.framework.interaction.LLMInteractionResult.Status.CANCELLED;
import static org.zexnocs.teanekoagent.llm.framework.interaction.LLMInteractionResult.Status.EMPTY_OUTPUT;
import static org.zexnocs.teanekoagent.llm.framework.interaction.LLMInteractionResult.Status.FAILED;
import static org.zexnocs.teanekoagent.llm.framework.interaction.LLMInteractionResult.Status.INVALID_INPUT;
import static org.zexnocs.teanekoagent.llm.framework.interaction.LLMInteractionResult.Status.INVALID_RESPONSE;
import static org.zexnocs.teanekoagent.llm.framework.interaction.LLMInteractionResult.Status.SUCCESS;
import static org.zexnocs.teanekoagent.llm.framework.interaction.LLMInteractionResult.Status.TIMED_OUT;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

/**
 * 用真实阶段执行器验证自动往返、协议配对、深层快照和所有主要终止路径。
 * <br>2026/11/02：验证 HTTP 失败保留结构化诊断且不泄漏响应正文和原异常文本。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/05：明确非空选项断言并以两节点引用环验证拒绝行为，简化泛型推断。
 * <br>2026/11/05：验证初始化遇到已停止控制时不请求模型且只记录终止状态。
 * <br>2026/11/05：验证安全诊断传递与响应正文脱敏。
 * <br>2026/11/05：验证宿主遗漏模型预算时保留预检代码位置且不调用模型。
 * <br>2026/11/05：验证末轮保留可信定义、禁用工具及内部调用标记不泄漏到最终答案。
 * <br>2026/11/05：补充组件审查修复的离线回归及初始化夹具。
 * <br>2026/11/05：覆盖双竖线正文泄漏、一次协议纠正、原预算及已执行工具不重试。
 * <br>2026/11/06：验证观察器异常不改变结果或泄漏执行资源。
 * <br>2026/11/06：验证多次模型往返的单次汇总、真实输入和外层观察归属。
 * <br>2026/11/07：迁入独立 LLM 子模块，保留公开协议与原配置语义。
 * <br>2026/11/10：验证视觉模型选择、配置隔离与资源结束边界。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.5.0-l
 * @version 4.9.1-l
 */
class LLMInteractionServiceTest {
    /** 2026/11/10：主请求超时的逻辑结果不能冒充真实结束，备用请求须等待原资源收尾。 */
    @Test
    void settledInteractionWaitsForModelResourceAfterTimeout() {
        var logical = new CompletableFuture<ILLMResult>();
        var physical = new CompletableFuture<Void>();
        var task = spy(new TaskFuture<ILLMResult>(logger, "排空模型", logical));
        when(task.physicalCompletion()).thenReturn(physical);
        doReturn(task).when(models).call(any(ILLMPrompt.class), any(ExecutionPolicy.class));
        try (var parent = runtime.parent(LLMCallLimits.defaults())) {
            var prompt = new LLMPrompt(LLMUserMessage.builder()
                    .contents(new LLMContentListBuilder().addText("合成输入").build()).build());
            var result = service.interactSettled(prompt, new LLMToolSet(new ObjectMapper(), List.of()),
                    context(parent), settings, ignored -> { }).toCompletableFuture();
            runtime.runAll();
            runtime.advance(settings.modelTimeout());
            runtime.runAll();
            assertFalse(result.isDone());
            logical.completeExceptionally(new IllegalStateException("合成失败"));
            assertFalse(result.isDone());
            physical.complete(null);
            runtime.runAll();
            assertEquals(TIMED_OUT, result.join().status());
            parent.checkActive();
        }
        assertEquals(0, runtime.pendingTimers());
    }

    private final InteractionRuntime runtime = new InteractionRuntime();
    private final ILLMModelService models = mock(ILLMModelService.class);
    // 2026/11/02：复用原失败用例验证真实诊断入口。
    private final ILogger logger = mock(ILogger.class);
    private final List<ILLMPrompt> prompts = new ArrayList<>();
    private final List<CompletableFuture<ILLMResult>> replies = new ArrayList<>();
    private final LLMInteractionSettings settings = LLMInteractionSettings.defaults();
    private final LLMInteractionService service = new LLMInteractionService(models,
            LLMInteractionSettings::defaults, runtime, runtime.scheduler, new JsonLLMTokenEstimator(new ObjectMapper()), logger);

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

    /** 2026/11/06：观察器抛错不改变真实交互结果，子控制仍在结束后释放。 */
    @Test
    void observerFailureCannotChangeTheResultOrLeakTimers() {
        service.installObserver((request, elapsed, result) -> {
            throw new IllegalStateException("测试观察器不可用");
        });
        try (var parent = runtime.parent(settings.limits())) {
            var pending = service.interact(prompt(), tools((call, context) ->
                    CompletableFuture.completedStage("结果")), context(parent));
            runtime.runAll();
            reply(response("stop", "正常结果"));
            assertEquals(SUCCESS, pending.toCompletableFuture().join().status());
        }
        assertEquals(0, runtime.pendingTimers());
    }

    /**
     * 2026/11/06：两次模型调用和工具回填只通知一次；宿主接管时零次，保留实际输入。
     * @param owned 是否由 Agent 在更外层汇总
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void reportsOneInteractionWithExactAttemptsUnlessOwnedByAgent(boolean owned) {
        var reports = new ArrayList<LLMInteractionResult>();
        service.installObserver((request, elapsed, result) -> reports.add(result));
        try (var parent = runtime.parent(settings.limits())) {
            var base = context(parent);
            var trusted = new ToolInvocationContext(base.requestId(), base.bindings(), parent, owned);
            var pending = service.interact(prompt(), tools((call, context) ->
                    CompletableFuture.completedStage("工具返回测试文本")), trusted);
            runtime.runAll();
            reply(response("tool_calls", "调用工具", call("first")));
            reply(response("stop", "最终结果"));
            var result = pending.toCompletableFuture().join();
            assertEquals(SUCCESS, result.status());
            assertEquals(owned ? 0 : 1, reports.size());
            assertEquals(2, result.attempts().size());
            var first = result.attempts().getFirst().input().orElseThrow();
            assertEquals(1, first.messages().size());
            assertTrue(first.declaration().contains("work"));
            var second = result.attempts().getLast().input().orElseThrow();
            assertTrue(second.messages().stream().anyMatch(ILLMToolMessage.class::isInstance));
            assertFalse(new ObjectMapper().writeValueAsString(result).contains("declaration"));
            assertEquals(owned, trusted.withControl(parent).externallyObserved());
        }
        assertEquals(0, runtime.pendingTimers());
    }

    /**
     * 验证两轮六个工具、三次模型、严格 ID 回放及逐次 usage。
     * 2026/11/05：明确非空选项断言并以两节点引用环验证拒绝行为，简化泛型推断。
     * 2026/11/05：工具历史续轮保留定义供协议编码，但 none 仍禁止执行。
     */
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
            assertEquals("none", Objects.requireNonNull(prompts.getLast().getOptions()).getToolChoice());
            assertEquals("work", Objects.requireNonNull(prompts.getLast().getOptions()).getTools().getFirst().getName());
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

    /**
     * 2026/11/05：供应商再次泄漏时停止，双竖线及正文混排均不会当成工具执行。
     * @param text 已出现的内部标记及普通正文混排
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "<｜DSML｜calls>\n<｜DSML｜invoke name=\"work\">private-argument</｜DSML｜invoke>\n</｜DSML｜calls>",
            "我先查一下\n<|DSML|function_calls>private-argument</|DSML|function_calls>",
            "<｜｜DSML｜｜ calls>\n<｜｜DSML｜｜ invoke name=\"sticker_list\">\n\n</｜｜DSML｜｜ invoke>\n</｜｜DSML｜｜ calls>",
            "我先查询：< | | DSML | | invoke name=\"work\">private-argument</ | | DSML | | invoke>",
            "前缀<||DSML||function_calls>private-argument</||DSML||function_calls>后缀"
    })
    void rejectsInternalToolCallsInVisibleText(String text) {
        var effects = new AtomicInteger();
        try (var parent = runtime.parent(settings.limits())) {
            var pending = service.interact(prompt(), tools((call, context) -> {
                effects.incrementAndGet();
                return CompletableFuture.completedStage("不应执行");
            }), context(parent));
            runtime.runAll();
            reply(response("stop", text));
            assertFalse(pending.toCompletableFuture().isDone());
            assertEquals(0, effects.get());
            reply(response("stop", text));
            var result = pending.toCompletableFuture().join();
            assertEquals(INVALID_RESPONSE, result.status());
            assertEquals(2, result.modelAttempts());
            assertTrue(result.finalCandidate().isEmpty());
            assertEquals(0, effects.get());
            assertNotNull(result.failure());
            assertTrue(result.failure().description().contains("tool_calls"));
            assertFalse(result.failure().toString().contains("private-argument"));
        }
    }

    /** 2026/11/05：一次纠正后只执行真实结构化调用，错误正文和临时提示不进入正常历史。 */
    @Test
    void correctsToolProtocolOnceWithinOriginalBudget() {
        var effects = new AtomicInteger();
        try (var parent = runtime.parent(settings.limits())) {
            var pending = service.interact(prompt(), tools((call, context) -> {
                effects.incrementAndGet();
                return CompletableFuture.completedStage("真实目录结果");
            }), context(parent));
            runtime.runAll();
            reply(response("stop", "<｜｜DSML｜｜ invoke name=\"work\">private-argument</｜｜DSML｜｜ invoke>"));
            assertEquals(0, effects.get());
            assertEquals("auto", Objects.requireNonNull(prompts.getLast().getOptions()).getToolChoice());
            assertEquals("system", prompts.getLast().getMessages().getLast().getRole().getValue());
            assertTrue(prompts.getLast().getMessages().stream().flatMap(message -> message.getContents().stream())
                    .filter(content -> content.getContentPart() instanceof TextLLMContentPart)
                    .noneMatch(content -> ((TextLLMContentPart) content.getContentPart()).getText()
                            .contains("private-argument")));
            reply(response("tool_calls", "", call("real-call")));
            assertEquals(1, effects.get());
            assertEquals("none", Objects.requireNonNull(prompts.getLast().getOptions()).getToolChoice());
            reply(response("stop", "正常答案"));
            var result = pending.toCompletableFuture().join();
            assertEquals(SUCCESS, result.status());
            assertEquals(3, result.modelAttempts());
            assertEquals(1, result.tools().size());
            var history = result.committableTranscript().orElseThrow();
            assertEquals(4, history.size());
            assertTrue(history.stream().noneMatch(message -> "system".equals(message.getRole().getValue())));
            assertTrue(history.stream().flatMap(message -> message.getContents().stream())
                    .filter(content -> content.getContentPart() instanceof TextLLMContentPart)
                    .noneMatch(content -> ((TextLLMContentPart) content.getContentPart()).getText().contains("DSML")));
        }
    }

    /**
     * 2026/11/05：本地或父预算不足、无工具和显式禁用均不得额外请求模型。
     * @param scenario 原有预算或调用方式
     */
    @ParameterizedTest
    @ValueSource(strings = {"local", "parent", "empty", "none"})
    void doesNotCorrectWhenOriginalToolCapacityIsUnavailable(String scenario) {
        var limited = new LLMCallLimits(2, 1, 1);
        var local = scenario.equals("local") ? new LLMInteractionSettings(limited,
                settings.modelTimeout(), settings.toolTimeout(), settings.turnTimeout()) : settings;
        try (var parent = runtime.parent(scenario.equals("parent") ? limited : settings.limits())) {
            var input = scenario.equals("none")
                    ? new LLMPrompt(prompt().getMessages(), LLMModelOptions.builder().toolChoice("none").build()) : prompt();
            var selected = scenario.equals("empty") ? new LLMToolSet(new ObjectMapper(), List.of())
                    : tools((call, context) -> CompletableFuture.completedStage("不应执行"));
            var pending = service.interact(input, selected, context(parent), local);
            runtime.runAll();
            reply(response("stop", "<｜｜DSML｜｜ calls>"));
            var result = pending.toCompletableFuture().join();
            assertEquals(INVALID_RESPONSE, result.status());
            assertEquals(1, result.modelAttempts());
            assertTrue(result.tools().isEmpty());
        }
    }

    /** 2026/11/05：已经执行工具后不因错误正文重做动作，即使尚有模型额度。 */
    @Test
    void doesNotCorrectAfterToolSideEffects() {
        var expanded = new LLMInteractionSettings(new LLMCallLimits(5, 4, 6),
                settings.modelTimeout(), settings.toolTimeout(), settings.turnTimeout());
        var effects = new AtomicInteger();
        try (var parent = runtime.parent(expanded.limits())) {
            var pending = service.interact(prompt(), tools((call, context) -> {
                effects.incrementAndGet();
                return CompletableFuture.completedStage("已完成");
            }), context(parent), expanded);
            runtime.runAll();
            reply(response("tool_calls", "", call("done")));
            reply(response("stop", "<｜｜DSML｜｜ calls>"));
            assertEquals(INVALID_RESPONSE, pending.toCompletableFuture().join().status());
            assertEquals(2, prompts.size());
            assertEquals(1, effects.get());
        }
    }

    /** 2026/11/05：协议纠正中的取消仍使用原控制，不执行迟到调用或继续请求。 */
    @Test
    void cancelsProtocolCorrectionWithoutExecutingLateToolCalls() {
        try (var parent = runtime.parent(settings.limits())) {
            var pending = service.interact(prompt(), tools((call, context) -> {
                fail("取消的协议纠正不能执行工具。");
                return CompletableFuture.completedStage("不应执行");
            }), context(parent));
            runtime.runAll();
            reply(response("stop", "<｜｜DSML｜｜ calls>"));
            parent.cancel();
            var result = pending.toCompletableFuture().join();
            assertEquals(CANCELLED, result.status());
            assertEquals(2, result.modelAttempts());
            reply(response("tool_calls", "", call("late")));
            assertEquals(2, prompts.size());
            assertTrue(result.tools().isEmpty());
            assertEquals(0, runtime.pendingTimers());
        }
    }

    /** 2026/11/05：用户询问协议时，代码围栏中的字面示例仍是正常正文。 */
    @Test
    void preservesToolMarkupInsideCodeExamples() {
        try (var parent = runtime.parent(settings.limits())) {
            var pending = service.interact(prompt(), new LLMToolSet(new ObjectMapper(), List.of()), context(parent));
            runtime.runAll();
            reply(response("stop", "示例：\n```text\n<｜DSML｜calls>\n</｜DSML｜calls>\n```\n这是代码说明"));
            assertEquals(SUCCESS, pending.toCompletableFuture().join().status());
        }
    }

    /**
     * 验证末次违规请求工具不会产生额外模型尝试或副作用。
     * 2026/11/05：明确非空选项断言并以两节点引用环验证拒绝行为，简化泛型推断。
     */
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
            assertEquals("none", Objects.requireNonNull(prompts.getFirst().getOptions()).getToolChoice());
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

    /**
     * 验证已经配对的历史可回放，但其 ID 不能在新响应中重复执行。
     * 2026/11/05：明确非空选项断言并以两节点引用环验证拒绝行为，简化泛型推断。
     */
    @Test void rejectsReplayOfToolIdFromInitialHistory() {
        try (var parent = runtime.parent(settings.limits())) {
            var history = new ArrayList<>(prompt().getMessages());
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

    /**
     * 验证输入、选项及供应商响应在排队后均不再受原始可变数据影响。
     * 2026/11/05：明确非空选项断言并以两节点引用环验证拒绝行为，简化泛型推断。
     */
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
            assertEquals("原始状态", ((List<?>) Objects.requireNonNull(prompts.getFirst().getOptions()).getMetadata().get("state")).getFirst());
            assertTrue(Objects.requireNonNull(prompts.getFirst().getOptions()).findModel().isEmpty());
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

    /**
     * 验证任意运行对象、循环和凭据均无法进入可导出的快照。
     * 2026/11/05：明确非空选项断言并以两节点引用环验证拒绝行为，简化泛型推断。
     */
    @Test void rejectsRuntimeObjectsCyclesAndCredentialsWithoutCallingModel() {
        try (var parent = runtime.parent(settings.limits())) {
            var cycle = new HashMap<String, Object>();
            var nested = new HashMap<String, Object>();
            cycle.put("nested", nested);
            nested.put("parent", cycle);
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

    /** 2026/11/05：未声明模型预算的宿主在预检中失败，诊断保留真实预算错误位置。 */
    @Test
    void retainsPreflightFailureWhenHostBudgetIsMissing() {
        try (var parent = ExecutionControl.create()) {
            var result = service.interact(prompt(), new LLMToolSet(new ObjectMapper(), List.of()), context(parent))
                    .toCompletableFuture().join();
            assertEquals(INVALID_INPUT, result.status());
            var diagnostic = Objects.requireNonNull(result.failure());
            assertTrue(diagnostic.types().contains(IllegalArgumentException.class.getName()));
            assertTrue(diagnostic.location().contains("ResourceBudget.counter"));
            verifyNoInteractions(models);
        }
    }

    /** 2026/11/05：初始化前已停止的请求保留取消结果，不记录为供应商故障。 */
    @Test
    void reportsStoppedInitializationWithoutModelFailure() {
        try (var parent = runtime.parent(settings.limits())) {
            parent.cancel();
            var result = service.interact(prompt(), new LLMToolSet(new ObjectMapper(), List.of()), context(parent))
                    .toCompletableFuture().join();
            assertEquals(CANCELLED, result.status());
            verifyNoInteractions(models);
            verify(logger, never()).error(anyString(), anyString());
            // 2026/11/06：正常停止由终态观察统一报告，不再附加第二条 INFO。
            verify(logger, never()).info(anyString(), anyString());
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

    /** 2026/11/02：模型 HTTP 失败只记录状态、类型和关联，不重试或导出敏感正文。 */
    @Test void recordsFailedAttemptWithoutRetryOrRawException() {
        try (var parent = runtime.parent(settings.limits())) {
            var trusted = context(parent);
            var pending = service.interact(prompt(), new LLMToolSet(new ObjectMapper(), List.of()), trusted);
            runtime.runAll();
            var http = org.springframework.web.reactive.function.client.WebClientResponseException.create(400, "测试敏感异常",
                    new org.springframework.http.HttpHeaders(), "测试敏感异常".getBytes(java.nio.charset.StandardCharsets.UTF_8), java.nio.charset.StandardCharsets.UTF_8);
            replies.getLast().completeExceptionally(new IllegalStateException("测试敏感异常", http));
            runtime.runAll();
            var result = pending.toCompletableFuture().join();
            assertEquals(FAILED, result.status());
            // 2026/11/05：HTTP 原因进入结果且不携带第三方正文。
            assertNotNull(result.failure());
            assertTrue(result.failure().description().contains("HTTP 400"));
            assertFalse(result.failure().toString().contains("测试敏感异常"));
            assertEquals(1, result.modelAttempts());
            assertTrue(result.attempts().getFirst().response().isEmpty());
            assertFalse(result.toString().contains("测试敏感异常"));
            assertEquals(1, prompts.size());
            var diagnostic = org.mockito.ArgumentCaptor.forClass(String.class);
            verify(logger).error(eq(LLMInteractionService.class.getName()), diagnostic.capture());
            assertTrue(diagnostic.getValue().contains("请求=" + trusted.requestId()));
            assertTrue(diagnostic.getValue().contains("阶段=模型请求"));
            assertTrue(diagnostic.getValue().contains("HTTP状态=400"));
            assertTrue(diagnostic.getValue().contains("WebClientResponseException"));
            assertFalse(diagnostic.getValue().contains("测试敏感异常"));
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
