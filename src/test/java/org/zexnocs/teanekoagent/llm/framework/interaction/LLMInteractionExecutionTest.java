package org.zexnocs.teanekoagent.llm.framework.interaction;

import org.junit.jupiter.api.Test;
import org.zexnocs.teanekoagent.llm.framework.input.LLMPrompt;
import org.zexnocs.teanekoagent.llm.framework.input.interfaces.ILLMPrompt;
import org.zexnocs.teanekoagent.llm.framework.message.LLMUserMessage;
import org.zexnocs.teanekoagent.llm.framework.message.content.LLMContentListBuilder;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptions;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.ILLMModelService;
import org.zexnocs.teanekoagent.llm.framework.response.LLMResult;
import org.zexnocs.teanekoagent.llm.framework.response.interfaces.ILLMResult;
import org.zexnocs.teanekoagent.llm.framework.tool.*;
import org.zexnocs.teanekoagent.llm.framework.tool.interfaces.IContextualToolExecutor;
import org.zexnocs.teanekocore.actuator.execution.BudgetExceededException;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.actuator.execution.ExecutionPolicy;
import org.zexnocs.teanekocore.actuator.execution.ExecutionStoppedException;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.framework.composition.ComponentKey;
import org.zexnocs.teanekocore.framework.composition.ComponentPatch;
import org.zexnocs.teanekocore.framework.composition.EntityId;
import org.zexnocs.teanekocore.framework.composition.EntitySnapshot;
import org.zexnocs.teanekocore.logger.ILogger;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.zexnocs.teanekoagent.llm.framework.interaction.LLMCallLimits.*;

/**
 * 用真实 Core 控制和工具集验证阶段时限、局部与父共享预算、末次关闭工具和顺序等待。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
class LLMInteractionExecutionTest {
    private final InteractionRuntime runtime = new InteractionRuntime();
    private final ILLMModelService models = mock(ILLMModelService.class);
    private final List<ILLMPrompt> prompts = new ArrayList<>();
    private final List<ExecutionPolicy> policies = new ArrayList<>();
    private final List<CompletableFuture<ILLMResult>> replies = new ArrayList<>();
    private final LLMInteractionSettings defaults = LLMInteractionSettings.defaults();

    /**
     * 捕获实际模型端口尝试，以主动完成的 Future 保持调用链可观察。
     */
    LLMInteractionExecutionTest() {
        when(models.call(any(ILLMPrompt.class), any(ExecutionPolicy.class))).thenAnswer(invocation -> {
            prompts.add(invocation.getArgument(0));
            policies.add(invocation.getArgument(1));
            var pending = new CompletableFuture<ILLMResult>();
            replies.add(pending);
            return new TaskFuture<>(mock(ILogger.class), "受控模型阶段", pending);
        });
    }

    /**
     * 构造固定宿主身份组件，不允许模型参数替换当前空间。
     *
     * @param parent 用例拥有的宿主执行控制
     * @return 带稳定请求标识及不可变空间绑定的可信上下文
     */
    private ToolInvocationContext context(ExecutionControl parent) {
        var data = EntitySnapshot.of(EntityId.random(), ComponentPatch.builder()
                .put(ComponentKey.of("test.namespace", String.class), "可信空间").build());
        return new ToolInvocationContext(UUID.randomUUID(), data, parent);
    }

    /**
     * 将受控异步行为绑定到唯一可见工具，参数沿用现有零参数 schema。
     *
     * @param executor 本用例需要观察的工具业务行为
     * @return 真实 LLMToolSet，包含固定定义和执行器引用
     */
    private LLMToolSet tools(IContextualToolExecutor executor) {
        return new LLMToolSet(new ObjectMapper(), List.of(new LLMToolBinding(
                LLMTool.builder().name("work").description("受控测试工具").build(), executor)));
    }

    /**
     * 装配阶段执行器，借用同一个手动业务队列及调度器。
     *
     * @param parent 共享总额度的宿主控制
     * @param settings 当前交互的局部设置
     * @param tools 本轮固定工具集
     * @return 由用例负责关闭的交互执行器
     */
    private LLMInteractionExecution execution(ExecutionControl parent, LLMInteractionSettings settings, LLMToolSet tools) {
        return new LLMInteractionExecution(models, tools, context(parent), settings, runtime, runtime.scheduler);
    }

    /**
     * 构造不含认证信息的最小文本模型输入。
     *
     * @return 默认选项的统一 Prompt
     */
    private LLMPrompt prompt() {
        return new LLMPrompt(LLMUserMessage.builder()
                .contents(LLMContentListBuilder.builder().addText("测试输入").build()).build());
    }

    /**
     * 完成最近一次实际发起的模型请求，不推进排队中尚未开始的请求。
     */
    private void reply() { replies.getLast().complete(LLMResult.builder().model("受控模型").build()); }

    /**
     * 提取已结束阶段的原始异常，避免仅观察完成阶段包装。
     *
     * @param stage 已经异常完成的阶段
     * @return CompletionException 内的直接失败原因
     */
    private Throwable failure(CompletionStage<?> stage) {
        return assertThrows(CompletionException.class, () -> stage.toCompletableFuture().join()).getCause();
    }

    /**
     * 验证请求只在执行器启动后计数，末次无工具且超限不再触达模型端口。
     */
    @Test
    void requestsAreCountedAtInvocationAndLastRequestDisablesTools() {
        try (var parent = runtime.parent(defaults.limits());
             var run = execution(parent, defaults, tools((call, ctx) -> CompletableFuture.completedStage("结果")))) {
            for (int i = 0; i < 3; i++) {
                var pending = run.callModel(prompt());
                assertEquals(i, run.usage().get(MODEL_REQUESTS).used());
                runtime.runAll();
                assertEquals(i + 1, policies.size());
                assertEquals(Duration.ofSeconds(60), policies.getLast().timeout());
                assertEquals(i < 2 ? "auto" : "none", prompts.getLast().getOptions().getToolChoice());
                assertEquals(i < 2 ? 1 : 0, prompts.getLast().getOptions().getTools().size());
                reply();
                assertNotNull(pending.toCompletableFuture().join());
            }
            var extra = run.callModel(prompt());
            runtime.runAll();
            assertInstanceOf(BudgetExceededException.class, failure(extra));
            assertEquals(3, replies.size());
            parent.checkActive();
        }
        assertEquals(0, runtime.pendingTimers());
    }

    /**
     * 验证单次模型上限为一时从开始就关闭工具，没有隐含的额外收束请求。
     */
    @Test
    void oneRequestLimitCannotExecuteToolsOrObtainAnExtraFinalCall() {
        var settings = new LLMInteractionSettings(new LLMCallLimits(1, 2, 6), defaults.modelTimeout(),
                defaults.toolTimeout(), defaults.turnTimeout());
        var toolsRun = new AtomicInteger();
        try (var parent = runtime.parent(defaults.limits());
             var run = execution(parent, settings, tools((call, ctx) -> {
                 toolsRun.incrementAndGet(); return CompletableFuture.completedStage("结果");
             }))) {
            var first = run.callModel(prompt()); runtime.runAll(); reply();
            first.toCompletableFuture().join();
            assertEquals("none", prompts.getFirst().getOptions().getToolChoice());
            assertInstanceOf(BudgetExceededException.class, failure(run.executeTools(List.of(new LLMToolCall("一", "function", "work", "{}")))));
            var second = run.callModel(prompt()); runtime.runAll();
            assertInstanceOf(BudgetExceededException.class, failure(second));
            assertEquals(1, replies.size()); assertEquals(0, toolsRun.get());
        }
    }

    /**
     * 验证同请求多个交互不能通过不同局部配置重置父模型额度。
     */
    @Test
    void separateExecutionsShareTheParentBudgetAndCloseToolsOnItsLastSlot() {
        var shared = new LLMCallLimits(2, 2, 6);
        var set = tools((call, ctx) -> CompletableFuture.completedStage("结果"));
        try (var parent = runtime.parent(shared);
             var first = execution(parent, defaults, set);
             var second = execution(parent, defaults, set)) {
            var a = first.callModel(prompt()); runtime.runAll(); reply(); a.toCompletableFuture().join();
            var b = second.callModel(prompt()); runtime.runAll(); reply(); b.toCompletableFuture().join();
            assertEquals("none", prompts.getLast().getOptions().getToolChoice());
            var denied = first.callModel(prompt()); runtime.runAll();
            assertInstanceOf(BudgetExceededException.class, failure(denied));
            assertEquals(1, first.usage().get(MODEL_REQUESTS).used());
            assertEquals(1, second.usage().get(MODEL_REQUESTS).used());
            assertEquals(2, parent.budget().used(MODEL_REQUESTS));
        }
    }

    /**
     * 验证异步模型失败不自动重试，显式新尝试消耗同一预算。
     */
    @Test
    void modelFailureIsNotRetriedAndAnotherAttemptSpendsAnotherSlot() {
        try (var parent = runtime.parent(defaults.limits());
             var run = execution(parent, defaults, tools((call, ctx) -> CompletableFuture.completedStage("结果")))) {
            var first = run.callModel(prompt()); runtime.runAll();
            var original = new IllegalStateException("受控模型失败。");
            replies.getFirst().completeExceptionally(original);
            assertSame(original, failure(first)); assertEquals(1, replies.size());
            var second = run.callModel(prompt()); runtime.runAll(); reply(); second.toCompletableFuture().join();
            assertEquals(2, run.usage().get(MODEL_REQUESTS).used());
        }
    }

    /**
     * 验证阶段到期结束等待并终止实际模型控制，借用父控制仍可用。
     */
    @Test
    void modelTimeoutClosesStageWithoutStoppingParentOrAcceptingLateResult() {
        try (var parent = runtime.parent(defaults.limits());
             var run = execution(parent, defaults, tools((call, ctx) -> CompletableFuture.completedStage("结果")))) {
            var result = run.callModel(prompt()); runtime.runAll();
            runtime.advance(Duration.ofSeconds(60));
            var stopped = assertInstanceOf(ExecutionStoppedException.class, failure(result));
            assertEquals(ExecutionControl.StopReason.DEADLINE_EXCEEDED, stopped.reason());
            parent.checkActive();
            assertTrue(policies.getFirst().control().termination().toCompletableFuture().isDone());
            reply();
            assertInstanceOf(ExecutionStoppedException.class, failure(result));
            assertEquals(2, runtime.pendingTimers());
        }
    }

    /**
     * 验证阶段时限包含执行器排队，排队到期后不会发出模型请求或消耗请求额度。
     */
    @Test
    void queuedTimeoutPreventsModelInvocationAndDoesNotSpendRequestBudget() {
        try (var parent = runtime.parent(defaults.limits());
             var run = execution(parent, defaults, tools((call, ctx) -> CompletableFuture.completedStage("结果")))) {
            var pending = run.callModel(prompt());
            runtime.advance(Duration.ofSeconds(60));
            assertInstanceOf(ExecutionStoppedException.class, failure(pending));
            runtime.runAll();
            assertTrue(replies.isEmpty());
            assertEquals(0, run.usage().get(MODEL_REQUESTS).used());
        }
    }

    /**
     * 验证同一交互在途阶段不重叠，取消排队或运行中的交互只向下传播。
     */
    @Test
    void overlapIsRejectedAndCancellationDoesNotAffectAnotherExecution() {
        var set = tools((call, ctx) -> CompletableFuture.completedStage("结果"));
        try (var parent = runtime.parent(defaults.limits());
             var first = execution(parent, defaults, set); var second = execution(parent, defaults, set)) {
            var pending = first.callModel(prompt());
            assertInstanceOf(IllegalStateException.class, failure(first.callModel(prompt())));
            first.cancel();
            assertEquals(ExecutionControl.StopReason.CANCELLED,
                    assertInstanceOf(ExecutionStoppedException.class, failure(pending)).reason());
            var other = second.callModel(prompt()); runtime.runAll();
            assertEquals(1, replies.size()); reply(); assertNotNull(other.toCompletableFuture().join());
            parent.checkActive();
        }
    }

    /**
     * 验证多个工具串行等待真实业务结果，且从宿主取得固定身份和请求标识。
     */
    @Test
    void toolsRunSequentiallyAndAwaitBusinessBeforeTheNextStage() {
        var pending = new ArrayList<CompletableFuture<String>>();
        var identities = new ArrayList<ToolInvocationContext>();
        try (var parent = runtime.parent(defaults.limits());
             var run = execution(parent, defaults, tools((call, ctx) -> {
                 identities.add(ctx); var result = new CompletableFuture<String>(); pending.add(result); return result;
             }))) {
            var first = run.callModel(prompt()); runtime.runAll(); reply(); first.toCompletableFuture().join();
            var batch = run.executeTools(List.of(new LLMToolCall("甲", "function", "work", "{}"),
                    new LLMToolCall("乙", "function", "work", "{}")));
            runtime.runAll(); assertEquals(1, pending.size());
            assertFalse(batch.toCompletableFuture().isDone());
            pending.getFirst().complete("甲结果"); runtime.runAll(); assertEquals(2, pending.size());
            pending.getLast().complete("乙结果");
            var results = batch.toCompletableFuture().join();
            assertEquals(List.of("甲", "乙"), results.stream().map(r -> r.toMessage().getToolCallId()).toList());
            assertEquals(identities.getFirst().requestId(), identities.getLast().requestId());
            assertEquals("可信空间", identities.getFirst().bindings().require(ComponentKey.of("test.namespace", String.class)));
            assertEquals(1, run.usage().get(TOOL_ROUNDS).used());
            assertEquals(2, run.usage().get(TOOL_EXECUTIONS).used());
            var after = run.callModel(prompt()); runtime.runAll(); reply(); after.toCompletableFuture().join();
        }
    }

    /**
     * 验证工具阶段超时回填固定错误后可以执行下一工具，迟到业务结果不覆盖超时状态。
     */
    @Test
    void toolTimeoutIsRecoverableButItsLateResultCannotReplaceTheReceipt() {
        var count = new AtomicInteger(); var late = new CompletableFuture<String>();
        try (var parent = runtime.parent(defaults.limits());
             var run = execution(parent, defaults, tools((call, ctx) -> count.incrementAndGet() == 1
                     ? late : CompletableFuture.completedStage("第二项完成")))) {
            var model = run.callModel(prompt()); runtime.runAll(); reply(); model.toCompletableFuture().join();
            var batch = run.executeTools(List.of(new LLMToolCall("甲", "function", "work", "{}"),
                    new LLMToolCall("乙", "function", "work", "{}")));
            runtime.runAll(); runtime.advance(Duration.ofSeconds(15)); runtime.runAll();
            var results = batch.toCompletableFuture().join();
            assertEquals(ToolInvocationResult.Status.TIMED_OUT, results.getFirst().status());
            assertEquals(ToolInvocationResult.Status.SUCCESS, results.getLast().status());
            late.complete("迟到结果");
            assertEquals(ToolInvocationResult.Status.TIMED_OUT, batch.toCompletableFuture().join().getFirst().status());
            parent.checkActive();
        }
    }

    /**
     * 验证明显超限、重复 ID 或非法批次在任何工具副作用之前整体拒绝。
     */
    @Test
    void oversizedOrDuplicateBatchIsRejectedBeforeAnySideEffect() {
        var count = new AtomicInteger();
        var settings = new LLMInteractionSettings(new LLMCallLimits(3, 2, 1), defaults.modelTimeout(),
                defaults.toolTimeout(), defaults.turnTimeout());
        try (var parent = runtime.parent(defaults.limits());
             var run = execution(parent, settings, tools((call, ctx) -> {
                 count.incrementAndGet(); return CompletableFuture.completedStage("完成");
             }))) {
            var model = run.callModel(prompt()); runtime.runAll(); reply(); model.toCompletableFuture().join();
            var a = new LLMToolCall("甲", "function", "work", "{}");
            assertInstanceOf(BudgetExceededException.class, failure(run.executeTools(List.of(a,
                    new LLMToolCall("乙", "function", "work", "{}")))));
            assertInstanceOf(IllegalArgumentException.class, failure(run.executeTools(List.of(a, a))));
            assertEquals(0, count.get()); assertEquals(0, run.usage().get(TOOL_ROUNDS).used());
            var valid = run.executeTools(List.of(a)); runtime.runAll(); valid.toCompletableFuture().join();
            assertEquals(1, count.get());
            assertInstanceOf(IllegalArgumentException.class, failure(run.executeTools(List.of(a))));
        }
    }

    /**
     * 验证整轮截止时间结束在途工具并阻止余项，拥有者关闭后所有计时任务可回收。
     */
    @Test
    void turnTimeoutStopsRemainingToolsAndCloseReleasesOwnedTimers() {
        var count = new AtomicInteger();
        var settings = new LLMInteractionSettings(defaults.limits(), Duration.ofSeconds(60),
                Duration.ofSeconds(60), Duration.ofSeconds(10));
        try (var parent = runtime.parent(defaults.limits())) {
            try (var run = execution(parent, settings, tools((call, ctx) -> {
                count.incrementAndGet(); return new CompletableFuture<>();
            }))) {
                var model = run.callModel(prompt()); runtime.runAll(); reply(); model.toCompletableFuture().join();
                var batch = run.executeTools(List.of(new LLMToolCall("甲", "function", "work", "{}"),
                        new LLMToolCall("乙", "function", "work", "{}")));
                runtime.runAll(); runtime.advance(Duration.ofSeconds(10)); runtime.runAll();
                assertEquals(ExecutionControl.StopReason.DEADLINE_EXCEEDED,
                        assertInstanceOf(ExecutionStoppedException.class, failure(batch)).reason());
                assertEquals(1, count.get()); parent.checkActive();
            }
            assertEquals(1, runtime.pendingTimers());
        }
        assertEquals(0, runtime.pendingTimers());
    }

    /**
     * 验证执行器拒绝排队和缺少共享预算维度均明确失败，不遗留计时器或假成功。
     */
    @Test
    void rejectedWorkerAndUndeclaredBudgetFailClosed() {
        try (var empty = ExecutionControl.create()) {
            assertThrows(IllegalArgumentException.class, () -> execution(empty, defaults,
                    tools((call, ctx) -> CompletableFuture.completedStage("完成"))));
        }
        try (var parent = runtime.parent(defaults.limits());
             var run = new LLMInteractionExecution(models, tools((call, ctx) -> CompletableFuture.completedStage("完成")),
                     context(parent), defaults, action -> { throw new RejectedExecutionException("受控队列拒绝。"); }, runtime.scheduler)) {
            assertInstanceOf(RejectedExecutionException.class, failure(run.callModel(prompt())));
            assertEquals(0, run.usage().get(MODEL_REQUESTS).used());
            assertEquals(2, runtime.pendingTimers());
        }
        assertEquals(0, runtime.pendingTimers());
    }

    /**
     * 验证限额和时限非法配置在执行前拒绝，工具批次数不会超过可用模型往返能力。
     */
    @Test
    void settingsRejectInvalidBoundsAndClampOnlyAvailableToolRounds() {
        assertEquals(0, new LLMCallLimits(1, 2, 6).maxToolRounds());
        assertThrows(IllegalArgumentException.class, () -> new LLMCallLimits(0, 2, 6));
        assertThrows(IllegalArgumentException.class, () -> new LLMCallLimits(3, -1, 6));
        assertThrows(IllegalArgumentException.class, () -> new LLMInteractionSettings(defaults.limits(),
                Duration.ZERO, defaults.toolTimeout(), defaults.turnTimeout()));
        assertThrows(NullPointerException.class, () -> new LLMInteractionSettings(null,
                defaults.modelTimeout(), defaults.toolTimeout(), defaults.turnTimeout()));
    }

    /**
     * 验证两批共六个工具严格计数，随后仅允许一次无工具模型收束。
     */
    @Test
    void defaultToolRoundAndExecutionLimitsAreEnforcedTogether() {
        var count = new AtomicInteger();
        try (var parent = runtime.parent(defaults.limits());
             var run = execution(parent, defaults, tools((call, ctx) -> {
                 count.incrementAndGet(); return CompletableFuture.completedStage("结果");
             }))) {
            for (int round = 0; round < 2; round++) {
                var model = run.callModel(prompt()); runtime.runAll(); reply(); model.toCompletableFuture().join();
                var batch = run.executeTools(List.of(new LLMToolCall(round + "甲", "function", "work", "{}"),
                        new LLMToolCall(round + "乙", "function", "work", "{}"),
                        new LLMToolCall(round + "丙", "function", "work", "{}")));
                runtime.runAll(); assertEquals(3, batch.toCompletableFuture().join().size());
            }
            assertEquals(6, count.get());
            assertEquals(2, run.usage().get(TOOL_ROUNDS).used());
            assertEquals(6, run.usage().get(TOOL_EXECUTIONS).used());
            var last = run.callModel(prompt()); runtime.runAll(); reply(); last.toCompletableFuture().join();
            assertEquals("none", prompts.getLast().getOptions().getToolChoice());
            assertEquals(6, parent.budget().used(TOOL_EXECUTIONS));
        }
    }

    /**
     * 验证不同交互共享父工具额度，不能通过更高的局部配置绕过已经消耗的总量。
     */
    @Test
    void toolQuotaCannotBeResetByAnotherExecution() {
        var count = new AtomicInteger();
        var set = tools((call, ctx) -> { count.incrementAndGet(); return CompletableFuture.completedStage("结果"); });
        try (var parent = runtime.parent(new LLMCallLimits(4, 3, 1));
             var first = execution(parent, defaults, set); var second = execution(parent, defaults, set)) {
            var a = first.callModel(prompt()); runtime.runAll(); reply(); a.toCompletableFuture().join();
            var b = second.callModel(prompt()); runtime.runAll(); reply(); b.toCompletableFuture().join();
            var batch = first.executeTools(List.of(new LLMToolCall("甲", "function", "work", "{}")));
            runtime.runAll(); batch.toCompletableFuture().join();
            assertInstanceOf(BudgetExceededException.class, failure(second.executeTools(
                    List.of(new LLMToolCall("乙", "function", "work", "{}")))));
            assertEquals(1, count.get());
            assertEquals(0, second.usage().get(TOOL_EXECUTIONS).used());
        }
    }

    /**
     * 验证子截止时间继承父时钟并取较早边界，超大时限不溢出或延长父执行。
     */
    @Test
    void childDeadlinesUseParentClockAndCannotExtendIt() {
        try (var parent = runtime.parent(defaults.limits());
             var shortChild = parent.child(Duration.ofSeconds(5), runtime.scheduler);
             var longChild = parent.child(Duration.ofSeconds(Long.MAX_VALUE), runtime.scheduler)) {
            assertSame(parent.budget(), shortChild.budget());
            assertEquals(Duration.ofDays(1), longChild.remainingTime().orElseThrow());
            runtime.advance(Duration.ofSeconds(5));
            assertEquals(ExecutionControl.StopReason.DEADLINE_EXCEEDED,
                    shortChild.termination().toCompletableFuture().join());
            parent.checkActive(); longChild.checkActive();
            assertEquals(2, runtime.pendingTimers());
        }
        assertEquals(0, runtime.pendingTimers());
    }

    /**
     * 验证子计时登记失败或参数非法时不影响父控制，也不关闭借用调度器。
     */
    @Test
    void invalidOrRejectedChildDeadlineDoesNotStopItsParent() {
        try (var parent = runtime.parent(defaults.limits())) {
            assertThrows(IllegalArgumentException.class, () -> parent.child(Duration.ZERO, runtime.scheduler));
            assertEquals(1, runtime.pendingTimers());
            doThrow(new RejectedExecutionException("受控计时拒绝。"))
                    .when(runtime.scheduler).schedule(org.mockito.ArgumentMatchers.<Callable<Object>>any(),
                            anyLong(), any(TimeUnit.class));
            assertThrows(RejectedExecutionException.class, () -> parent.child(Duration.ofSeconds(1), runtime.scheduler));
            parent.checkActive();
            assertEquals(1, runtime.pendingTimers());
            verify(runtime.scheduler, never()).shutdown();
        }
        assertEquals(0, runtime.pendingTimers());
    }

    /**
     * 验证关闭拥有者结束在途模型并先释放阶段资源，父控制和共享用量仍保留。
     */
    @Test
    void ownerCloseStopsPendingWorkAndCompletionCallbacksSeeReleasedTimers() {
        try (var parent = runtime.parent(defaults.limits())) {
            var run = execution(parent, defaults, tools((call, ctx) -> CompletableFuture.completedStage("结果")));
            var model = run.callModel(prompt()); runtime.runAll();
            run.close(); run.close();
            assertEquals(ExecutionControl.StopReason.CLOSED,
                    assertInstanceOf(ExecutionStoppedException.class, failure(model)).reason());
            assertEquals(1, runtime.pendingTimers());
            assertEquals(1, parent.budget().used(MODEL_REQUESTS));
            parent.checkActive();
        }
        try (var parent = runtime.parent(defaults.limits());
             var run = execution(parent, defaults, tools((call, ctx) -> CompletableFuture.completedStage("结果")))) {
            var model = run.callModel(prompt()).thenApply(value -> {
                assertEquals(2, runtime.pendingTimers()); return value;
            });
            runtime.runAll(); reply(); model.toCompletableFuture().join();
        }
    }

    /**
     * 验证输出视图不能取消实际执行；排队前固定选项容器并覆盖未授权工具声明。
     */
    @Test
    void exportedViewIsReadOnlyAndQueuedOptionsDoNotChangeToolBindings() {
        var options = LLMModelOptions.builder().toolChoice("required")
                .tools(List.of(LLMTool.builder().name("隐藏工具").build())).metadata(new HashMap<>()).build();
        try (var parent = runtime.parent(defaults.limits());
             var run = execution(parent, defaults, tools((call, ctx) -> CompletableFuture.completedStage("结果")))) {
            var original = prompt();
            var pending = run.callModel(new LLMPrompt(original.getMessages(), options));
            pending.toCompletableFuture().cancel(true);
            options.setToolChoice("none"); options.getMetadata().put("后加字段", true);
            runtime.runAll();
            assertEquals("required", prompts.getLast().getOptions().getToolChoice());
            assertEquals("work", prompts.getLast().getOptions().getTools().getFirst().getName());
            assertTrue(prompts.getLast().getOptions().getMetadata().isEmpty());
            reply(); assertNotNull(pending.toCompletableFuture().join());
        }
    }

    /**
     * 验证同步端口异常和空返回不会使阶段悬挂或丢失一次尝试的预算。
     */
    @Test
    void synchronousModelFailureAndNullTaskBothSettleAndRemainCounted() {
        var error = new IllegalStateException("受控同步异常。");
        doThrow(error).doAnswer(invocation -> null).when(models)
                .call(any(ILLMPrompt.class), any(ExecutionPolicy.class));
        try (var parent = runtime.parent(defaults.limits());
             var run = execution(parent, defaults, tools((call, ctx) -> CompletableFuture.completedStage("结果")))) {
            var first = run.callModel(prompt()); runtime.runAll(); assertSame(error, failure(first));
            var second = run.callModel(prompt()); runtime.runAll(); assertInstanceOf(NullPointerException.class, failure(second));
            assertEquals(2, run.usage().get(MODEL_REQUESTS).used());
            assertEquals(2, runtime.pendingTimers());
        }
    }
}
