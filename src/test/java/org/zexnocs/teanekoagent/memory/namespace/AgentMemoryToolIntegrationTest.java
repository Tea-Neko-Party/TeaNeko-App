package org.zexnocs.teanekoagent.memory.namespace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.zexnocs.teanekoagent._app_config.MemoryPromptConfig;
import org.zexnocs.teanekoagent.adapter.memory.AgentMemoryPromptSource;
import org.zexnocs.teanekoagent.adapter.memory.AgentMemoryToolSource;
import org.zexnocs.teanekoagent.adapter.memory.EasyDataMemoryNamespaceBindingStore;
import org.zexnocs.teanekoagent.adapter.memory.StoreAgentMemoryReferences;
import org.zexnocs.teanekoagent.agent.*;
import org.zexnocs.teanekoagent.agent.access.AgentAccessGate;
import org.zexnocs.teanekoagent.agent.access.AgentReadiness;
import org.zexnocs.teanekoagent.agent.access.AgentRuntimeSnapshot;
import org.zexnocs.teanekoagent.agent.access.AgentScopePolicy;
import org.zexnocs.teanekoagent.agent.conversation.*;
import org.zexnocs.teanekoagent.agent.memory.IAgentMemoryReferences;
import org.zexnocs.teanekoagent.agent.prompt.AgentPromptFragment;
import org.zexnocs.teanekoagent.agent.prompt.IAgentPromptSource;
import org.zexnocs.teanekoagent.llm.framework.input.interfaces.ILLMPrompt;
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
import org.zexnocs.teanekoagent.llm.framework.tool.LLMToolCall;
import org.zexnocs.teanekoagent.llm.framework.tool.ToolInvocationResult;
import org.zexnocs.teanekoagent.memory.access.IMemoryAccessPolicy;
import org.zexnocs.teanekoagent.memory.access.MemoryAccessPolicy;
import org.zexnocs.teanekoagent.memory.access.MemoryAccessService;
import org.zexnocs.teanekoagent.memory.fact.*;
import org.zexnocs.teanekoagent.memory.profile.UserProfileService;
import org.zexnocs.teanekoagent.memory.recall.IMemoryRecallDefaults;
import org.zexnocs.teanekoagent.memory.recall.IMemoryRecallService;
import org.zexnocs.teanekoagent.memory.recall.MemoryRecallService;
import org.zexnocs.teanekoagent.memory.recall.MemoryRecallSettings;
import org.zexnocs.teanekocore.actuator.execution.ExecutionPolicy;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.logger.ILogger;

import java.time.Clock;
import java.time.Instant;
import java.time.InstantSource;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntConsumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 经过真实默认邮箱、Agent、有限 LLM 往返、工具集与事务适配验证记忆工具闭环。
 * 模型与数据库传输使用离线替身，不消费真实供应商或发送平台消息。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
@Timeout(20)
class AgentMemoryToolIntegrationTest {
    private final Instant now = Instant.parse("2026-10-29T01:00:00Z");
    private final AgentScope scope = new AgentScope("客户端", "群");
    private final AgentActorContext actor = new AgentActorContext(scope, AgentActorContext.Origin.USER, Optional.of(UUID.randomUUID()), "平台用户", "原消息", "目标");

    /**
     * 构造可有独立原文凭据的当前请求，默认入口自行解析空间引用。
     * @param text 正常模型消息
     * @param original 当前可信原文；为空时不提供明确新增授权
     * @return 未绑定空间/会话的宿主输入
     */
    private AgentRunRequest request(String text, String original) {
        return new AgentRunRequest(UUID.randomUUID(), actor, AgentActivity.SERVICE,
                List.of(LLMUserMessage.builder().contents(new LLMContentListBuilder().addText(text).build()).build()), null, null,
                original == null ? null : new AgentUserText(actor.sourceId(), original));
    }

    /**
     * 构造有明确原调用 ID 的工具请求。
     * @param id 原调用标识
     * @param name 工具名称
     * @param args 业务参数
     * @return 供应商工具调用替身
     */
    private LLMToolCall call(String id, String name, String args) { return LLMToolCall.builder().id(id).name(name).arguments(args).build(); }

    /**
     * 构造一次工具轮或最终响应，不替换真实 LLM 调度与协议校验。
     * @param calls 本次工具调用；空表示最终回答
     * @return 有用量的统一模型结果
     */
    private LLMResult response(LLMToolCall... calls) {
        return LLMResult.builder().usage(LLMUsage.builder().totalTokens(2).build()).choices(List.of(LLMChoice.builder().index(0)
                .finishReason(calls.length == 0 ? "stop" : "tool_calls")
                .message(LLMAssistantMessage.builder().contents(new LLMContentListBuilder().addText(calls.length == 0 ? "离线回答" : "").build())
                        .toolCalls(List.of(calls)).build()).build())).build();
    }

    /**
     * 独立拥有离线测试资源和真实组合服务，借用关系与生产装配一致。
     * @author zExNocs
     * @date 2026/10/29
     * @since 4.0.0
     * @version 4.5.0-l
     */
    private final class Harness implements AutoCloseable {
        final NamespaceDatabaseFixture db = new NamespaceDatabaseFixture();
        final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        final AgentRegistry registry = new AgentRegistry();
        final AgentHandle handle = new AgentFactory(registry).create(new AgentDefinition("记忆工具闭环", new LLMModelOptionsSnapshot(null), Set.of(AgentActivity.values())));
        final AtomicReference<MemoryAccessPolicy> policy = new AtomicReference<>(new MemoryAccessPolicy(true, true, true, false));
        final AtomicInteger modelCalls = new AtomicInteger();
        final AtomicInteger memoryReads = new AtomicInteger();
        final AtomicReference<MemoryRecallSettings> recallSettings = new AtomicReference<>(MemoryRecallSettings.defaults());
        final AtomicReference<Instant> instant = new AtomicReference<>(now);
        final Clock clock = ((InstantSource) instant::get).withZone(ZoneOffset.UTC);
        final Queue<ILLMResult> responses = new ConcurrentLinkedQueue<>();
        final List<ILLMPrompt> prompts = new CopyOnWriteArrayList<>();
        volatile IntConsumer beforeReply = ignored -> {};
        final IAgentMemoryReferences references;
        final MemoryAccessService memory;
        final AgentService turns;
        final AgentConversationService mailbox;

        /** 保留仅工具的原有验收方式，自动资料测试显式启用实际源。 */
        Harness() { this(false); }

        /**
         * 初始化纯离线适配，模型传输以外仍使用真实业务、资料和持久化服务。
         * @param inject 是否使用实际自动记忆资料源
         */
        Harness(boolean inject) {
            var logger = mock(ILogger.class);
            var namespaces = new MemoryNamespaceRegistry(db.store, () -> 5, clock); namespaces.ensureShared().toCompletableFuture().join();
            references = new StoreAgentMemoryReferences(ignored -> new AgentScopePolicy(true, policy.get().enabled(), AgentScopePolicy.Mode.ACTIVE),
                    new EasyDataMemoryNamespaceBindingStore(db.store), namespaces, executor);
            var recall = new MemoryRecallService(db.recall, recallSettings::get, clock, executor, evidence -> db.mapper.writeValueAsString(evidence).length());
            IMemoryRecallService counted = request -> { memoryReads.incrementAndGet(); return recall.recall(request); };
            memory = new MemoryAccessService(ignored -> policy.get(), references, counted, new UserProfileService(counted, executor), new MemoryFacts(db.facts, clock), executor);
            var tools = new AgentMemoryToolSource(memory, ignored -> policy.get(), recallSettings::get, db.mapper, executor);
            var ready = new AgentRuntimeSnapshot(new LLMModelOptionsSnapshot(LLMModelOptions.builder().provider("test").model("离线模型").maxTokens(30).build()),
                    ModelCapabilities.textOnly(), LLMInteractionSettings.defaults());
            var gate = new AgentAccessGate(registry, ignored -> new AgentScopePolicy(true, policy.get().enabled(), AgentScopePolicy.Mode.ACTIVE),
                    ignored -> new AgentReadiness(AgentReadiness.Status.READY, Optional.of(ready)), logger);
            var models = mock(ILLMModelService.class);
            when(models.call(any(ILLMPrompt.class), any(ExecutionPolicy.class))).thenAnswer(invocation -> {
                prompts.add(invocation.getArgument(0)); beforeReply.accept(modelCalls.incrementAndGet());
                return new TaskFuture<>(logger, "记忆工具离线模型", CompletableFuture.completedFuture(Objects.requireNonNull(responses.poll(), "缺少离线模型响应。")));
            });
            turns = new AgentService(gate, new LLMInteractionService(models, LLMInteractionSettings::defaults, executor, scheduler), tools,
                    db.mapper, executor, scheduler, clock, logger, references,
                    inject ? new AgentMemoryPromptSource(memory, ignored -> policy.get(), recallSettings::get, db.mapper, executor, clock) : IAgentPromptSource.empty());
            mailbox = new AgentConversationService(turns, AgentQueueSettings::defaults, executor, scheduler, clock, logger,
                    AgentConversationSettings::defaults, (definition, request) -> AgentConversationOptions.defaults(), db.mapper, references);
        }

        /**
         * 通过真实事实事务准备不同主体的证据，不用模型替身伪造检索结果。
         * @param subject 事实主体
         * @param text 完整正文
         * @param expiry 可选到期时间
         */
        void seed(UUID subject, String text, Instant expiry) {
            var reference = references.resolve(scope).toCompletableFuture().join();
            var value = new MemoryFactValue(MemoryFactValue.Kind.PREFERENCE, text, List.of(), 1,
                    MemoryFactValue.Importance.NORMAL, MemoryEventTime.unknown(), expiry);
            new MemoryFacts(db.facts, clock).append(new MemoryFactAppend(UUID.randomUUID(), reference, subject, value,
                    new MemoryFactSource(UUID.randomUUID(), "初始化事实", actor))).toCompletableFuture().join();
        }

        /** 关闭测试拥有者，业务服务不能关闭共享执行器或遗留进行中的数据库任务。 */
        @Override public void close() { mailbox.close(); turns.close(); registry.close(); scheduler.shutdownNow(); executor.close(); db.close(); }
    }

    /** 验证真实记忆自动进入模型而不沉积历史，授权重放不再检索，关闭/clear 后隐藏旧答案并重建空视图。 */
    @Test void automaticMemoryFlowsThroughPromptHistoryAndAuthorizedReplay() {
        try (var h = new Harness(true)) {
            h.seed(actor.userId().orElseThrow(), "本人喜欢花茶", null); var other = UUID.randomUUID();
            h.seed(other, "另一位喜欢红茶", null); h.seed(UUID.randomUUID(), "无关咖啡", null);
            h.responses.add(response()); var input = request("茶", "茶"); var result = h.mailbox.run(h.handle, input).toCompletableFuture().join();
            assertEquals(AgentRunResult.Status.SUCCESS, result.status()); var projection = Objects.requireNonNull(result.promptTranscript());
            assertEquals(List.of("memory_profile", "memory_related"), projection.input().fragments().stream().map(AgentPromptFragment::name).toList());
            var actual = h.db.mapper.writeValueAsString(h.prompts.getFirst().getMessages());
            assertTrue(actual.contains("本人喜欢花茶")); assertTrue(actual.contains("另一位喜欢红茶")); assertTrue(actual.contains(other.toString())); assertFalse(actual.contains("无关咖啡"));
            var related = h.db.mapper.readTree(projection.input().fragments().getLast().text().split("\n", 2)[1]);
            assertEquals(1, related.path("evidence").size());
            var window = h.mailbox.snapshot(h.handle, scope).orElseThrow().require(AgentConversationWindow.COMPONENT);
            assertEquals(2, window.messages().size()); assertFalse(h.db.mapper.writeValueAsString(window.messages()).contains("喜欢花茶"));
            int reads = h.memoryReads.get(); assertSame(result, h.mailbox.retry(h.handle, input).toCompletableFuture().join());
            assertSame(result, h.mailbox.run(h.handle, input).toCompletableFuture().join()); assertEquals(reads, h.memoryReads.get()); assertEquals(1, h.modelCalls.get());
            h.policy.set(new MemoryAccessPolicy(true, false, true, false));
            var disabled = h.mailbox.retry(h.handle, input).toCompletableFuture().join(); assertEquals(AgentRunResult.Status.SKIPPED, disabled.status()); assertTrue(disabled.answer().isEmpty());
            h.policy.set(new MemoryAccessPolicy(true, true, true, false));
            h.db.store.advanceGeneration(MemoryNamespaceId.SHARED, 0, UUID.randomUUID()).toCompletableFuture().join();
            assertEquals(AgentRunResult.Status.STALE_CONVERSATION, h.mailbox.retry(h.handle, input).toCompletableFuture().join().status());
            h.responses.add(response()); var fresh = h.mailbox.run(h.handle, request("新请求", "新请求")).toCompletableFuture().join();
            assertEquals(AgentRunResult.Status.SUCCESS, fresh.status()); assertEquals(1, Objects.requireNonNull(fresh.memoryReference()).generation());
            assertFalse(h.db.mapper.writeValueAsString(h.prompts.getLast().getMessages()).contains("喜欢花茶")); assertEquals(2, h.modelCalls.get());
        }
    }

    /** 验证总注入条数及部分覆盖使用真实 Recall，空的部分结果不会被描述成确定无资料。 */
    @Test void injectionSharesBudgetsAndRetainsPartialCoverage() {
        try (var h = new Harness(true)) {
            h.seed(actor.userId().orElseThrow(), "本人花茶", null); h.seed(UUID.randomUUID(), "其他红茶", null);
            h.recallSettings.set(new MemoryRecallSettings(1, 1, 20, 16000, 1000)); h.responses.add(response());
            var limited = h.mailbox.run(h.handle, request("茶", "茶")).toCompletableFuture().join();
            assertEquals(1, Objects.requireNonNull(limited.promptTranscript()).input().fragments().size());
            assertEquals(1, h.memoryReads.get()); assertFalse(h.db.mapper.writeValueAsString(h.prompts.getFirst().getMessages()).contains("其他红茶"));
            h.recallSettings.set(new MemoryRecallSettings(12, 8, 20, 2, 1)); h.responses.add(response());
            var partial = h.mailbox.run(h.handle, request("茶", "茶")).toCompletableFuture().join();
            var fragment = Objects.requireNonNull(partial.promptTranscript()).input().fragments().getFirst();
            var data = h.db.mapper.readTree(fragment.text().split("\n", 2)[1]);
            assertTrue(data.path("evidence").isEmpty()); assertFalse(data.path("definitivelyEmpty").booleanValue()); assertFalse(data.path("limits").isEmpty());
        }
    }

    /** 验证初始读关闭及系统任务不读取用户资料，模型期间关闭读取则停止工具/后继模型和正常交付。 */
    @Test void disabledAndSystemRequestsDoNotReadAndLateDisableStopsDelivery() {
        try (var h = new Harness(true)) {
            h.policy.set(new MemoryAccessPolicy(true, false, true, false)); h.responses.add(response());
            assertEquals(AgentRunResult.Status.SUCCESS, h.mailbox.run(h.handle, request("普通对话", "普通对话")).toCompletableFuture().join().status()); assertEquals(0, h.memoryReads.get());
            h.policy.set(new MemoryAccessPolicy(true, true, true, false)); h.responses.add(response());
            var system = new AgentActorContext(scope, AgentActorContext.Origin.SYSTEM, Optional.empty(), "", "系统任务", "目标");
            var base = request("系统对话", null); var task = new AgentRunRequest(UUID.randomUUID(), system, AgentActivity.SERVICE, base.messages());
            assertEquals(AgentRunResult.Status.SUCCESS, h.mailbox.run(h.handle, task).toCompletableFuture().join().status()); assertEquals(0, h.memoryReads.get());
            h.seed(actor.userId().orElseThrow(), "本人喜欢茶", null);
            h.beforeReply = ignored -> h.policy.set(new MemoryAccessPolicy(true, false, true, false));
            h.responses.add(response(call("拒绝的查询", AgentMemoryToolSource.QUERY, "{}")));
            var stopped = h.mailbox.run(h.handle, request("询问", "询问")).toCompletableFuture().join();
            assertEquals(AgentRunResult.Status.SKIPPED, stopped.status()); assertTrue(stopped.answer().isEmpty());
            assertTrue(stopped.interaction().orElseThrow().tools().isEmpty()); assertEquals(3, h.modelCalls.get());
        }
    }

    /** 验证已缓存证据到期后不能凭未变化的 namespace/generation 重放原答案。 */
    @Test void expiredEvidenceCannotReplayFromUnchangedNamespace() {
        try (var h = new Harness(true)) {
            h.seed(actor.userId().orElseThrow(), "即将过期的茶偏好", now.plusSeconds(10)); h.responses.add(response()); var input = request("茶", "茶");
            var result = h.mailbox.run(h.handle, input).toCompletableFuture().join(); assertEquals(AgentRunResult.Status.SUCCESS, result.status());
            int reads = h.memoryReads.get(); h.instant.set(now.plusSeconds(11));
            var replay = h.mailbox.retry(h.handle, input).toCompletableFuture().join();
            assertEquals(AgentRunResult.Status.STALE_CONVERSATION, replay.status()); assertTrue(replay.answer().isEmpty());
            assertEquals(reads, h.memoryReads.get()); assertEquals(1, h.modelCalls.get());
        }
    }

    /** 验证真实 Spring 默认源和自定义替换均可装配，构造期间不读取任何记忆。 */
    @Test void defaultPromptAssemblyIsLazyAndCanBeReplaced() {
        try (var h = new Harness(true)) {
            for (boolean custom : List.of(false, true)) {
                try (var context = new AnnotationConfigApplicationContext()) {
                    var beans = context.getBeanFactory(); beans.registerSingleton("access", h.memory);
                    beans.registerSingleton("policies", (IMemoryAccessPolicy) ignored -> h.policy.get());
                    beans.registerSingleton("defaults", (IMemoryRecallDefaults) h.recallSettings::get);
                    beans.registerSingleton("mapper", h.db.mapper); beans.registerSingleton("virtualExecutor", h.executor);
                    var selected = IAgentPromptSource.empty(); if (custom) { beans.registerSingleton("customPrompt", selected); }
                    context.register(MemoryPromptConfig.class); context.refresh();
                    if (custom) { assertSame(selected, context.getBean(IAgentPromptSource.class)); }
                    else { assertInstanceOf(AgentMemoryPromptSource.class, context.getBean(IAgentPromptSource.class)); }
                    assertEquals(0, h.memoryReads.get()); assertFalse(h.executor.isShutdown());
                }
            }
        }
    }

    /** 验证一次真实顺序工具轮完成明确新增、共享查询和本人画像，并保留原动作/主体/来源。 */
    @Test void realAgentWritesQueriesAndProfilesThroughOneToolRound() {
        try (var h = new Harness()) {
            h.responses.add(response(call("原新增调用", AgentMemoryToolSource.ADD, "{\"kind\":\"PREFERENCE\"}"),
                    call("原查询调用", AgentMemoryToolSource.QUERY, "{\"keywords\":[\"茶\"]}"), call("原画像调用", AgentMemoryToolSource.PROFILE, "{}")));
            h.responses.add(response()); var input = request("记住：我喜欢茶", "记住：我喜欢茶");
            var result = h.mailbox.run(h.handle, input).toCompletableFuture().join(); assertEquals(AgentRunResult.Status.SUCCESS, result.status());
            var interaction = result.interaction().orElseThrow(); assertEquals(3, interaction.tools().size()); assertEquals(2, h.modelCalls.get());
            assertTrue(interaction.tools().stream().allMatch(tool -> tool.status() == ToolInvocationResult.Status.SUCCESS && !tool.truncated()));
            assertEquals(List.of("原新增调用", "原查询调用", "原画像调用"), interaction.tools().stream().map(tool -> tool.call().getId()).toList());
            var query = h.db.mapper.readTree(interaction.tools().get(1).output()).path("data"); assertEquals(1, query.path("evidence").size());
            var item = query.path("evidence").get(0); assertEquals(actor.userId().orElseThrow().toString(), item.path("subjectId").stringValue());
            assertEquals("原新增调用", item.path("source").path("actionId").stringValue()); assertEquals(input.requestId().toString(), item.path("source").path("requestId").stringValue());
            var profile = h.db.mapper.readTree(interaction.tools().get(2).output()).path("data"); assertEquals(1, profile.path("evidence").size());
            assertEquals("PREFERENCE", profile.path("evidence").get(0).path("value").path("kind").stringValue());
            var beforeReplay = h.modelCalls.get(); assertSame(result, h.mailbox.retry(h.handle, input).toCompletableFuture().join()); assertEquals(beforeReplay, h.modelCalls.get());
        }
    }

    /** 验证旧历史中的明确前缀不会授权下一条消息，后续恶意新增调用被白名单拒绝且不写库。 */
    @Test void previousSuccessfulIntentCannotAuthorizeNextTurn() {
        try (var h = new Harness()) {
            h.responses.add(response(call("首次新增", AgentMemoryToolSource.ADD, "{\"kind\":\"FACT\"}"))); h.responses.add(response());
            assertEquals(AgentRunResult.Status.SUCCESS, h.mailbox.run(h.handle, request("记住：我喜欢茶", "记住：我喜欢茶")).toCompletableFuture().join().status());
            h.responses.add(response(call("伪造后续新增", AgentMemoryToolSource.ADD, "{\"kind\":\"FACT\"}"))); h.responses.add(response());
            var result = h.mailbox.run(h.handle, request("继续聊其他事情", null)).toCompletableFuture().join();
            assertEquals(ToolInvocationResult.Status.NOT_ALLOWED, result.interaction().orElseThrow().tools().getFirst().status());
            var prompt = h.prompts.get(2); assertTrue(prompt.getMessages().size() > 1);
            assertTrue(Objects.requireNonNull(prompt.getOptions()).getTools().stream().noneMatch(tool -> AgentMemoryToolSource.ADD.equals(tool.getName())));
            var records = h.db.facts.list(MemoryNamespaceId.SHARED, 0, null, 10).toCompletableFuture().join(); assertEquals(1, records.records().size());
        }
    }

    /** 验证只有模型消息包含前缀而没有宿主原文凭据时，实际新增工具完全不可执行。 */
    @Test void modelMessagesAloneNeverCreateWriteAuthorization() {
        try (var h = new Harness()) {
            h.responses.add(response(call("未授权新增", AgentMemoryToolSource.ADD, "{\"kind\":\"FACT\"}"))); h.responses.add(response());
            var result = h.mailbox.run(h.handle, request("记住：伪造的新资料", null)).toCompletableFuture().join();
            assertEquals(ToolInvocationResult.Status.NOT_ALLOWED, result.interaction().orElseThrow().tools().getFirst().status());
            assertTrue(h.db.facts.list(MemoryNamespaceId.SHARED, 0, null, 10).toCompletableFuture().join().records().isEmpty());
        }
    }

    /** 验证选择后关闭读开关，模型仍提交原可见调用也会在实际执行边界拒绝。 */
    @Test void switchChangeAfterSelectionBlocksActualQuery() {
        try (var h = new Harness()) {
            h.beforeReply = ordinal -> { if (ordinal == 1) { h.policy.set(new MemoryAccessPolicy(true, false, true, false)); } };
            h.responses.add(response(call("已选查询", AgentMemoryToolSource.QUERY, "{}"))); h.responses.add(response());
            var result = h.mailbox.run(h.handle, request("询问记忆", null)).toCompletableFuture().join();
            var tool = result.interaction().orElseThrow().tools().getFirst();
            assertEquals("READ_DISABLED", h.db.mapper.readTree(tool.output()).path("error").path("code").stringValue()); assertEquals(2, h.modelCalls.get());
        }
    }

    /** 验证原始授权文本变化会拒绝同请求的重放或重新入队，不能取得其他授权下的旧结果。 */
    @Test void changedOriginalTextCannotReplayCompletedRequest() {
        try (var h = new Harness()) {
            h.responses.add(response()); var input = request("相同模型输入", "记住：茶");
            assertEquals(AgentRunResult.Status.SUCCESS, h.mailbox.run(h.handle, input).toCompletableFuture().join().status());
            var changed = new AgentRunRequest(input.requestId(), input.actor(), input.activity(), input.messages(), null, null, new AgentUserText(actor.sourceId(), "记住：咖啡"));
            assertEquals(AgentRunResult.Status.REQUEST_CONFLICT, h.mailbox.retry(h.handle, changed).toCompletableFuture().join().status());
            assertEquals(AgentRunResult.Status.REQUEST_CONFLICT, h.mailbox.run(h.handle, changed).toCompletableFuture().join().status()); assertEquals(1, h.modelCalls.get());
        }
    }

    /** 验证工具真实提交后 clear 保留真实动作诊断，旧轮不能向当前代次交付回答或恢复旧事实。 */
    @Test void clearAfterCommittedToolPreventsOldTurnDelivery() {
        try (var h = new Harness()) {
            h.beforeReply = ordinal -> { if (ordinal == 2) { h.db.store.advanceGeneration(MemoryNamespaceId.SHARED, 0, UUID.randomUUID()).toCompletableFuture().join(); } };
            h.responses.add(response(call("已提交新增", AgentMemoryToolSource.ADD, "{\"kind\":\"PREFERENCE\"}"))); h.responses.add(response());
            var result = h.mailbox.run(h.handle, request("记住：我喜欢茶", "记住：我喜欢茶")).toCompletableFuture().join();
            assertEquals(AgentRunResult.Status.STALE_CONVERSATION, result.status()); assertTrue(result.answer().isEmpty());
            assertEquals(ToolInvocationResult.Status.SUCCESS, result.interaction().orElseThrow().tools().getFirst().status());
            assertTrue(h.db.facts.list(MemoryNamespaceId.SHARED, 1, null, 10).toCompletableFuture().join().records().isEmpty());
        }
    }
}
