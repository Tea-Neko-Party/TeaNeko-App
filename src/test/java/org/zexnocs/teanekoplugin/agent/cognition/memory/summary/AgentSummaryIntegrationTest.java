package org.zexnocs.teanekoplugin.agent.cognition.memory.summary;

// 2026/11/07：认知业务契约归入插件，纯会话数据沿用运行底座。

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。


// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.zexnocs.teanekoagent.llm.framework.input.interfaces.ILLMPrompt;
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
import org.zexnocs.teanekoagent.runtime.AgentActivity;
import org.zexnocs.teanekoagent.runtime.AgentActorContext;
import org.zexnocs.teanekoagent.runtime.AgentDefinition;
import org.zexnocs.teanekoagent.runtime.AgentFactory;
import org.zexnocs.teanekoagent.runtime.AgentHandle;
import org.zexnocs.teanekoagent.runtime.AgentRegistry;
import org.zexnocs.teanekoagent.runtime.AgentRunRequest;
import org.zexnocs.teanekoagent.runtime.AgentRunResult;
import org.zexnocs.teanekoagent.runtime.AgentScope;
import org.zexnocs.teanekoagent.runtime.AgentService;
import org.zexnocs.teanekoagent.runtime.AgentUserText;
import org.zexnocs.teanekoagent.runtime.access.AgentAccessGate;
import org.zexnocs.teanekoagent.runtime.access.AgentReadiness;
import org.zexnocs.teanekoagent.runtime.access.AgentRuntimeSnapshot;
import org.zexnocs.teanekoagent.runtime.access.AgentScopePolicy;
import org.zexnocs.teanekoagent.runtime.conversation.AgentConversationOptions;
import org.zexnocs.teanekoagent.runtime.conversation.AgentConversationService;
import org.zexnocs.teanekoagent.runtime.conversation.AgentConversationSettings;
import org.zexnocs.teanekoagent.runtime.conversation.AgentConversationWindow;
import org.zexnocs.teanekoagent.runtime.conversation.AgentQueueSettings;
import org.zexnocs.teanekoagent.runtime.prompt.AgentPromptException;
import org.zexnocs.teanekoagent.runtime.prompt.IAgentPromptSource;
import org.zexnocs.teanekocore.actuator.execution.ExecutionPolicy;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekoplugin.agent.cognition.memory.namespace.MemoryNamespaceId;
import org.zexnocs.teanekoplugin.agent.cognition.memory.namespace.MemoryNamespaceReference;
import org.zexnocs.teanekoplugin.agent.cognition.memory.runtime.AgentMemoryReferenceException;
import org.zexnocs.teanekoplugin.agent.cognition.memory.runtime.IAgentMemoryReferences;
import org.zexnocs.teanekoplugin.agent.cognition.personality.AgentPersonalitySnapshot;
import org.zexnocs.teanekoplugin.agent.cognition.personality.IAgentPersonalitySource;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 集中验证真实会话、LLM 交互及后台摘要的生成、采用、失败和代次失效，不重复测试普通数据结构。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：使用可选显式预算合同验证摘要生成与超时。
 * <br>2026/11/04：校正当前实现的版本归属为 v4.7.2-l。
 * <br>2026/11/04：适配可选组件装配及迁移后的数据键，保留原核心行为验证。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/07：根据原提交职责恢复编码损坏的历史注释。
 *
 * @author zExNocs
 * @date 2026/10/30
 * @since 4.5.0-l
 * @version 4.9.0-l
 */
@Timeout(20)
class AgentSummaryIntegrationTest {
    private final AgentScope scope = new AgentScope("客户端", "群甲");
    /**
     * 使用真实执行器和 LLM 循环，仅替换模型传输与已测试的配置/引用端口。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     * <br>2026/11/04：摘要夹具使用可空显式总预算契约。
     * <br>2026/11/04：本次夹具调整归入 v4.7.2-l。
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     * <br>2026/11/07：认知业务与宿主生命周期解耦，保留原身份、事务及异步完成边界。
     *
     *
     * @author zExNocs
     * @date 2026/10/30
     * @since 4.5.0-l
     * @version 4.9.0-l
     */
    private final class Harness implements AutoCloseable {
        final ObjectMapper mapper = new ObjectMapper();
        final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
        final ScheduledExecutorService timers = Executors.newScheduledThreadPool(2);
        final AgentRegistry registry = new AgentRegistry();
        final AgentHandle handle = new AgentFactory(registry).create(new AgentDefinition("摘要验证", new LLMModelOptionsSnapshot(null), Set.of(AgentActivity.values())));
        final List<ILLMPrompt> foreground = new CopyOnWriteArrayList<>();
        final List<ILLMPrompt> background = new CopyOnWriteArrayList<>();
        final BlockingQueue<CompletableFuture<LLMResult>> pending = new LinkedBlockingQueue<>();
        final BlockingQueue<AgentConversationSummaryService.Usage> usage = new LinkedBlockingQueue<>();
        final AtomicBoolean enabled = new AtomicBoolean(true);
        final AtomicReference<String> personalityId = new AtomicReference<>("原人格");
        final ConcurrentMap<AgentScope, Long> revisions = new ConcurrentHashMap<>();
        // 2026/11/04：测试显式声明整轮总预算，默认模型额度不再被业务缩小。
        final AtomicReference<AgentSummarySettings> settings = new AtomicReference<>(new AgentSummarySettings(true, 3, 1, Long.MAX_VALUE, 65536, 1000, 1, 4096L, Duration.ofSeconds(5), Duration.ZERO));
        final AgentConversationSummaryService summaries;
        final AgentService turns;
        final AgentConversationService mailbox;
        /** 组合固定准入、实际协议管线和可暂停的摘要模型。 */
        Harness() {
            var logger = mock(ILogger.class);
            var references = mock(IAgentMemoryReferences.class, org.mockito.Mockito.CALLS_REAL_METHODS);
            var personalities = mock(IAgentPersonalitySource.class, org.mockito.Mockito.CALLS_REAL_METHODS);
            when(references.resolve(any())).thenAnswer(call -> CompletableFuture.completedStage(reference(call.getArgument(0))));
            when(references.verify(any())).thenAnswer(call -> {
                MemoryNamespaceReference expected = call.getArgument(0);
                return expected.equals(reference(expected.scope())) ? CompletableFuture.completedStage(null)
                        : CompletableFuture.failedStage(new AgentMemoryReferenceException(AgentMemoryReferenceException.Reason.CHANGED));
            });
            when(personalities.resolve(any(), any())).thenAnswer(call -> CompletableFuture.completedStage(new AgentPersonalitySnapshot(personalityId.get(), "teaneko", 1, "补正", 0, "基础人格", "", null)));
            when(personalities.verify(any(), any(), any())).thenAnswer(call -> personalityId.get().equals(call.<AgentPersonalitySnapshot>getArgument(1).identity())
                    ? CompletableFuture.completedStage(null) : CompletableFuture.failedStage(new AgentPromptException(AgentPromptException.Reason.CHANGED)));
            var runtime = new AgentRuntimeSnapshot(new LLMModelOptionsSnapshot(LLMModelOptions.builder().provider("test").model("离线模型").maxTokens(30).build()), ModelCapabilities.textOnly(), LLMInteractionSettings.defaults());
            var gate = new AgentAccessGate(registry, ignored -> new AgentScopePolicy(true, true, AgentScopePolicy.Mode.ACTIVE), ignored -> new AgentReadiness(AgentReadiness.Status.READY, Optional.of(runtime)), logger);
            var models = mock(ILLMModelService.class);
            when(models.resolveOptions(any(ILLMPrompt.class))).thenAnswer(call -> call.<ILLMPrompt>getArgument(0).getOptions());
            when(models.call(any(ILLMPrompt.class), any(ExecutionPolicy.class))).thenAnswer(call -> {
                ILLMPrompt prompt = call.getArgument(0);
                if (mapper.writeValueAsString(prompt.getMessages().getFirst()).contains("将资料压缩为")) {
                    background.add(prompt); var future = new CompletableFuture<LLMResult>(); pending.add(future); return new TaskFuture<>(logger, "后台摘要模型", future);
                }
                foreground.add(prompt); return new TaskFuture<>(logger, "前台对话模型", CompletableFuture.completedFuture(response("正常答案")));
            });
            var interactions = new LLMInteractionService(models, LLMInteractionSettings::defaults, workers, timers);
            summaries = new AgentConversationSummaryService(gate, interactions, references, personalities, ignored -> {
                var value = settings.get(); return new AgentSummarySettings(enabled.get(), value.triggerTurns(), value.keepTurns(), value.triggerBytes(), value.maxInputBytes(), value.maxCharacters(), value.maxModelCalls(), value.maxTokens(), value.timeout(), value.cooldown());
            }, mapper, workers, timers, Clock.systemUTC(), usage::add);
            // 2026/11/07：独立测试宿主显式启用摘要后台生命周期。
            summaries.start().toCompletableFuture().join();
            turns = new AgentService(gate, interactions, (definition, request, config) -> List.of(), mapper, workers, timers, Clock.systemUTC(), logger,
                    references, IAgentPromptSource.empty(), personalities, null, summaries);
            mailbox = new AgentConversationService(turns, AgentQueueSettings::defaults, workers, timers, Clock.systemUTC(), logger,
                    AgentConversationSettings::defaults, (definition, request) -> AgentConversationOptions.defaults(), mapper, references, personalities, summaries);
        }
        /**
         * 提供当前持久化引用，版本推进模拟真实绑定切换。
         * @param target 实际来源
         * @return 当前完整引用
         */
        MemoryNamespaceReference reference(AgentScope target) { return new MemoryNamespaceReference(target, MemoryNamespaceId.SHARED, revisions.getOrDefault(target, 1L), 0); }
        /**
         * 运行一次真实邮箱请求。
         * @param text 本轮原文
         * @return 已完成请求与结果
         */
        Map.Entry<AgentRunRequest, AgentRunResult> run(String text) {
            var actor = new AgentActorContext(scope, AgentActorContext.Origin.USER, Optional.of(UUID.randomUUID()), "用户", UUID.randomUUID().toString(), "目标");
            var request = new AgentRunRequest(UUID.randomUUID(), actor, AgentActivity.SERVICE, List.of(LLMUserMessage.builder()
                    .contents(new LLMContentListBuilder().addText(text).build()).build()), null, null, new AgentUserText(actor.sourceId(), text));
            var result = mailbox.run(handle, request).toCompletableFuture().join();
            assertEquals(AgentRunResult.Status.SUCCESS, result.status()); return Map.entry(request, result);
        }
        /**
         * 有界等待真实后台模型进入，避免轮询或任意睡眠。
         * @return 暂停的模型响应
         * @throws InterruptedException 当测试等待被中断时
         */
        CompletableFuture<LLMResult> awaitSummary() throws InterruptedException {
            var response = pending.poll(5, TimeUnit.SECONDS);
            assertNotNull(response, "后台摘要未按时进入模型，前台次数=" + foreground.size() + "，用量=" + summaries.reports()); return response;
        }
        /**
         * 有界等待后台完成及资源释放。
         * @return 非敏感用量回执
         * @throws InterruptedException 当测试等待被中断时
         */
        AgentConversationSummaryService.Usage awaitUsage() throws InterruptedException { return Objects.requireNonNull(usage.poll(5, TimeUnit.SECONDS), "后台摘要未按时完成。"); }
        /** 关闭自身拥有者，结束暂停的后台交互后释放借用资源。 */
        @Override public void close() { mailbox.close(); turns.close(); summaries.close(); registry.close(); timers.shutdownNow(); workers.close(); }
    }
    /**
     * 创建纯文本响应及供应商 usage。
     * @param text 实际候选
     * @return 可交付模型结果
     */
    private LLMResult response(String text) { return LLMResult.builder().usage(LLMUsage.builder().totalTokens(2).build())
            .choices(List.of(LLMChoice.builder().index(0).finishReason("stop").message(LLMAssistantMessage.builder().contents(new LLMContentListBuilder().addText(text).build()).build()).build())).build(); }
    /**
     * 验证后台不阻塞前台，成功后仅压缩匹配完整前缀，摘要实际注入且不沉积窗口，重放不再次调用。
     * @throws Exception 当后台等待超时或中断时
     */
    @Test void backgroundCompressionPromptAndReplayFormUsableFeature() throws Exception {
        try (var h = new Harness()) {
            h.run("第一轮旧主题"); h.run("第二轮旧主题"); h.run("第三轮最近主题");
            var pending = h.awaitSummary(); assertEquals(AgentRunResult.Status.SUCCESS, h.run("后台等待期间仍可回答").getValue().status());
            pending.complete(response("已压缩的旧话题")); var usage = h.awaitUsage(); assertTrue(usage.accepted());
            assertEquals(1, usage.budgetUsage().get(LLMCallLimits.MODEL_REQUESTS).used());
            var completed = h.run("继续最近话题"); var result = completed.getValue(); assertEquals(AgentRunResult.Status.SUCCESS, result.status());
            var input = Objects.requireNonNull(result.promptTranscript()).input(); assertNotNull(input.summary());
            var actual = h.mapper.writeValueAsString(h.foreground.getLast().getMessages());
            assertTrue(actual.contains("已压缩的旧话题")); assertFalse(actual.contains("第一轮旧主题")); assertTrue(actual.contains("第三轮最近主题"));
            var window = h.mailbox.snapshot(h.handle, scope).orElseThrow().require(AgentConversationWindow.COMPONENT);
            assertEquals(3, window.turns().size()); assertFalse(h.mapper.writeValueAsString(window.messages()).contains("已压缩的旧话题"));
            int calls = h.foreground.size(); assertSame(result, h.mailbox.retry(h.handle, completed.getKey()).toCompletableFuture().join()); assertEquals(calls, h.foreground.size());
        }
    }
    /**
     * 验证失败或超长摘要保持原窗口和前台答案，开关关闭后不再启动后台模型。
     * @throws Exception 当后台等待超时或中断时
     */
    @Test void failedOrInvalidSummaryDoesNotDiscardConversation() throws Exception {
        try (var h = new Harness()) {
            h.run("保留一"); h.run("保留二"); h.run("保留三"); var pending = h.awaitSummary(); pending.complete(response("过长".repeat(1000)));
            assertFalse(h.awaitUsage().accepted()); assertEquals(AgentRunResult.Status.SUCCESS, h.run("保留四").getValue().status());
            assertTrue(h.mapper.writeValueAsString(h.foreground.getLast().getMessages()).contains("保留一"));
            h.awaitSummary().completeExceptionally(new IllegalStateException("测试模拟供应商失败，不能进入用户答案。")); assertFalse(h.awaitUsage().accepted());
            h.enabled.set(false); assertEquals(AgentRunResult.Status.SUCCESS, h.run("禁用后仍普通对话").getValue().status()); assertEquals(2, h.background.size());
        }
    }
    /**
     * 验证绑定/人格切换使暂停旧任务失效，并且禁用后旧摘要答案不可重放。
     * @throws Exception 当后台等待超时或中断时
     */
    @Test void staleBackgroundAndReplayCannotRestoreOldContext() throws Exception {
        try (var h = new Harness()) {
            h.run("旧一"); h.run("旧二"); h.run("旧三"); var old = h.awaitSummary();
            h.revisions.put(scope, 2L); h.personalityId.set("新人格"); assertEquals(AgentRunResult.Status.SUCCESS, h.run("切换后的问题").getValue().status());
            old.complete(response("旧空间不得回灌")); assertFalse(h.awaitUsage().accepted());
            h.run("新二"); h.run("新三"); h.awaitSummary().complete(response("新空间摘要")); assertTrue(h.awaitUsage().accepted());
            var completed = h.run("新四"); assertNotNull(Objects.requireNonNull(completed.getValue().promptTranscript()).input().summary());
            h.enabled.set(false); var replay = h.mailbox.retry(h.handle, completed.getKey()).toCompletableFuture().join();
            assertNotEquals(AgentRunResult.Status.SUCCESS, replay.status()); assertTrue(replay.answer().isEmpty());
        }
    }
    /**
     * 验证独立 token/时间预算及关闭拥有者会结束暂停后台工作，前台已完成答案保持成功。
     * @throws Exception 当后台等待超时或中断时
     */
    @Test void backgroundTimeoutAndOwnerCloseReleaseWork() throws Exception {
        try (var h = new Harness()) {
            // 2026/11/04：显式总预算使用可空 Long 契约，超时测试仍保留原额度。
            h.settings.set(new AgentSummarySettings(true, 3, 1, Long.MAX_VALUE, 65536, 1000, 1, 4096L, Duration.ofMillis(200), Duration.ZERO));
            h.run("一"); h.run("二"); assertEquals(AgentRunResult.Status.SUCCESS, h.run("三").getValue().status()); h.awaitSummary();
            assertFalse(h.awaitUsage().accepted());
            h.settings.set(new AgentSummarySettings(true, 3, 1, Long.MAX_VALUE, 65536, 1000, 1, 4096L, Duration.ofSeconds(5), Duration.ZERO));
            assertEquals(AgentRunResult.Status.SUCCESS, h.run("四").getValue().status()); h.awaitSummary(); h.handle.close();
            assertFalse(h.awaitUsage().accepted());
        }
    }
}
