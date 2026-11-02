package org.zexnocs.teanekoagent.agent.workflow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.zexnocs.teanekoagent.agent.*;
import org.zexnocs.teanekoagent.agent.access.AgentAccessGate;
import org.zexnocs.teanekoagent.agent.access.AgentReadiness;
import org.zexnocs.teanekoagent.agent.access.AgentRuntimeSnapshot;
import org.zexnocs.teanekoagent.agent.access.AgentScopePolicy;
import org.zexnocs.teanekoagent.agent.prompt.AgentPromptFragment;
import org.zexnocs.teanekoagent.agent.prompt.AgentPromptInput;
import org.zexnocs.teanekoagent.agent.prompt.IAgentPromptSource;
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
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.actuator.execution.ExecutionPolicy;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.logger.ILogger;
import tools.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 复用手动执行夹具验证真实协作、总预算、身份及独立资源，不启动真实模型或线程池。
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.0.0
 * @version 4.5.0-l
 */
@Timeout(10)
class AgentWorkflowIntegrationTest {
    /**
     * 真实 Agent/LLM 组合，只替换模型传输，不复制执行和预算实现。
     * @author zExNocs
     * @date 2026/11/01
     * @since 4.0.0
     * @version 4.5.0-l
     */
    private static final class Harness implements AutoCloseable {
        final InteractionRuntime runtime = new InteractionRuntime();
        final AgentRegistry registry = new AgentRegistry();
        final AgentFactory factory = new AgentFactory(registry);
        final AgentHandle first = factory.create(new AgentDefinition("draft", new LLMModelOptionsSnapshot(null), Set.of(AgentActivity.values())));
        final AgentHandle second = factory.create(new AgentDefinition("review", new LLMModelOptionsSnapshot(null), Set.of(AgentActivity.values())));
        final AgentHandle third = factory.create(new AgentDefinition("finish", new LLMModelOptionsSnapshot(null), Set.of(AgentActivity.values())));
        final List<ILLMPrompt> prompts = new ArrayList<>();
        final List<CompletableFuture<ILLMResult>> replies = new ArrayList<>();
        final List<AgentRunRequest> requests = new ArrayList<>();
        final ObjectMapper mapper = new ObjectMapper();
        final AtomicBoolean replay = new AtomicBoolean(true);
        final AgentService turns;
        final AgentWorkflowService workflows;
        final AgentActorContext actor = new AgentActorContext(new AgentScope("客户端", "群"), AgentActorContext.Origin.USER,
                Optional.of(UUID.randomUUID()), "原用户", "原消息", "原目标");
        /** 构建真实轮次管线，记录实际工具选择请求以检查原文与身份边界。 */
        Harness() {
            var logger = mock(ILogger.class); var models = mock(ILLMModelService.class);
            when(models.resolveOptions(any())).thenAnswer(call -> call.<ILLMPrompt>getArgument(0).getOptions());
            when(models.call(any(ILLMPrompt.class), any(ExecutionPolicy.class))).thenAnswer(call -> {
                prompts.add(call.getArgument(0)); var response = new CompletableFuture<ILLMResult>(); replies.add(response);
                return new TaskFuture<>(logger, "协作离线模型", response);
            });
            var state = new AgentRuntimeSnapshot(new LLMModelOptionsSnapshot(LLMModelOptions.builder().provider("test").model("离线模型").maxTokens(20).build()),
                    ModelCapabilities.textOnly(), LLMInteractionSettings.defaults());
            var gate = new AgentAccessGate(registry, ignored -> new AgentScopePolicy(true, true, AgentScopePolicy.Mode.ACTIVE),
                    ignored -> new AgentReadiness(AgentReadiness.Status.READY, Optional.of(state)), logger);
            var source = new IAgentPromptSource() {
                /**
                 * 提供固定纯数据，不创建模型或工具预算。
                 * @param definition 定义
                 * @param request 请求
                 * @param snapshot 运行快照
                 * @param control 借用控制
                 * @return 完整来源
                 */
                @Override public CompletionStage<List<AgentPromptFragment>> resolve(AgentDefinition definition, AgentRunRequest request, AgentRuntimeSnapshot snapshot, ExecutionControl control) {
                    return CompletableFuture.completedStage(List.of(new AgentPromptFragment("fixture", "当前可信来源的只读资料")));
                }
                /**
                 * 模拟来源被撤销后的只读复核，不重跑模型。
                 * @param request 原请求
                 * @param input 原布局
                 * @param control 借用控制
                 * @return 当前资格
                 */
                @Override public CompletionStage<Boolean> verifyReplay(AgentRunRequest request, AgentPromptInput input, ExecutionControl control) {
                    return CompletableFuture.completedStage(replay.get());
                }
            };
            turns = new AgentService(gate, new LLMInteractionService(models, LLMInteractionSettings::defaults, runtime, runtime.scheduler),
                    (definition, request, snapshot) -> { requests.add(request); return List.of(); }, mapper, runtime, runtime.scheduler, runtime.clock, logger, null, source);
            workflows = new AgentWorkflowService(turns, null, runtime, mapper);
        }
        /** @return 含真实原文及原供应商状态的宿主请求 */
        AgentRunRequest origin() {
            return new AgentRunRequest(UUID.randomUUID(), actor, AgentActivity.SERVICE,
                    List.of(LLMUserMessage.builder().contents(new LLMContentListBuilder().addText("原始问题").build()).providerMetadata(Map.of("source-session", "仅限原实例")).build()),
                    null, null, new AgentUserText(actor.sourceId(), "原始问题"));
        }
        /** @return 明确的两个不同实例，不自动分派 */
        List<AgentWorkflowStep> plan() { return List.of(new AgentWorkflowStep("draft", first, "起草建议"), new AgentWorkflowStep("review", second, "复核已有证据，不虚构结果")); }
        /**
         * 完成当前真实模型观察并推进业务队列。
         * @param text 原模型正文
         */
        void reply(String text) {
            replies.getLast().complete(LLMResult.builder().usage(LLMUsage.builder().totalTokens(2).build()).choices(List.of(LLMChoice.builder().index(0).finishReason("stop")
                    .message(LLMAssistantMessage.builder().contents(new LLMContentListBuilder().addText(text).build()).build()).build())).build()); runtime.runAll();
        }
        /** 只关闭夹具拥有资源，验证未遗留阶段计时器。 */
        @Override public void close() { turns.close(); registry.close(); runtime.runAll(); assertEquals(0, runtime.pendingTimers()); }
    }
    /** 验证真实顺序交接、身份保留、原文凭据清除、供应商状态隔离及累计预算。 */
    @Test void twoInstancesShareBudgetAndKeepTrustedBoundaries() {
        try (var h = new Harness(); var parent = ExecutionControl.create(new LLMCallLimits(3, 2, 6, 100000L).newBudget())) {
            var origin = h.origin(); var result = h.workflows.run(UUID.randomUUID(), origin, h.plan(), parent, () -> {}).toCompletableFuture(); h.runtime.runAll();
            assertEquals(1, h.prompts.size()); h.reply("前一步的明确建议"); assertEquals(2, h.prompts.size());
            assertTrue(h.mapper.writeValueAsString(h.prompts.getLast().getMessages()).contains("前一步的明确建议"));
            assertTrue(h.prompts.stream().flatMap(prompt -> prompt.getMessages().stream()).allMatch(message -> message.getProviderMetadata().isEmpty()));
            assertTrue(h.requests.stream().allMatch(request -> request.actor().equals(origin.actor()) && request.userText() == null && request.conversation() == null));
            h.reply("复核后的答案"); var done = result.join();
            assertEquals(AgentRunResult.Status.SUCCESS, done.status()); assertTrue(done.answer().isPresent());
            assertNotEquals(done.steps().getFirst().result().agentId(), done.steps().getLast().result().agentId());
            assertEquals(2, parent.budget().used(LLMCallLimits.MODEL_REQUESTS)); assertEquals(4, parent.budget().used(LLMCallLimits.TOTAL_TOKENS));
            parent.checkActive(); assertFalse(h.first.isClosed()); assertFalse(h.second.isClosed());
        }
    }
    /** 验证总额度耗尽不能由下个实例重置；来源撤销后不交接旧答案。 */
    @Test void sharedLimitAndRevokedSourceStopLaterSteps() {
        try (var h = new Harness(); var parent = ExecutionControl.create(new LLMCallLimits(1, 0, 0).newBudget())) {
            var result = h.workflows.run(UUID.randomUUID(), h.origin(), h.plan(), parent, () -> {}).toCompletableFuture(); h.runtime.runAll(); h.reply("首步答案");
            assertEquals(AgentRunResult.Status.BUDGET_EXCEEDED, result.join().status()); assertTrue(result.join().answer().isEmpty()); assertEquals(1, h.prompts.size());
        }
        try (var h = new Harness(); var parent = ExecutionControl.create(LLMCallLimits.defaults().newBudget())) {
            var result = h.workflows.run(UUID.randomUUID(), h.origin(), h.plan(), parent, () -> {}).toCompletableFuture(); h.runtime.runAll();
            h.replay.set(false); h.reply("不能继续公开的旧来源答案");
            assertEquals(AgentRunResult.Status.STALE_CONVERSATION, result.join().status()); assertEquals(1, h.prompts.size()); assertTrue(result.join().answer().isEmpty());
        }
    }
    /** 验证只取消调用方派生控制，不关闭其他实例或父控制，也不启动后续步骤。 */
    @Test void cancellationDoesNotCloseBorrowedInstancesOrStartThirdStep() {
        try (var h = new Harness(); var parent = ExecutionControl.create(LLMCallLimits.defaults().newBudget()); var branch = parent.child()) {
            var plan = new ArrayList<>(h.plan()); plan.add(new AgentWorkflowStep("finish", h.third, "最终整理"));
            var result = h.workflows.run(UUID.randomUUID(), h.origin(), plan, branch, () -> {}).toCompletableFuture(); h.runtime.runAll(); h.reply("首步完成");
            branch.cancel(); h.runtime.runAll(); assertEquals(AgentRunResult.Status.CANCELLED, result.join().status()); parent.checkActive();
            h.reply("迟到的第二步结果"); assertEquals(2, h.prompts.size()); assertFalse(h.first.isClosed()); assertFalse(h.third.isClosed());
        }
    }
}
