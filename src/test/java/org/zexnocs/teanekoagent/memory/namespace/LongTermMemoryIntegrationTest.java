package org.zexnocs.teanekoagent.memory.namespace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.zexnocs.teanekoagent.adapter.history.EasyDataHistoryStore;
import org.zexnocs.teanekoagent.adapter.memory.EasyDataLayeredMemoryStore;
import org.zexnocs.teanekoagent.adapter.memory.EasyDataLongTermMemoryStore;
import org.zexnocs.teanekoagent.adapter.memory.LongTermMemoryPromptSource;
import org.zexnocs.teanekoagent.adapter.memory.LongTermMemoryTools;
import org.zexnocs.teanekoagent.agent.*;
import org.zexnocs.teanekoagent.agent.access.AgentAccessGate;
import org.zexnocs.teanekoagent.agent.access.AgentReadiness;
import org.zexnocs.teanekoagent.agent.access.AgentRuntimeSnapshot;
import org.zexnocs.teanekoagent.agent.access.AgentScopePolicy;
import org.zexnocs.teanekoagent.agent.memory.IAgentMemoryReferences;
import org.zexnocs.teanekoagent.agent.personality.AgentPersonalitySnapshot;
import org.zexnocs.teanekoagent.agent.prompt.AgentPromptInput;
import org.zexnocs.teanekoagent.history.HistoryContext;
import org.zexnocs.teanekoagent.history.HistoryMessage;
import org.zexnocs.teanekoagent.llm.framework.interaction.ILLMInteractionService;
import org.zexnocs.teanekoagent.llm.framework.interaction.LLMInteractionResult;
import org.zexnocs.teanekoagent.llm.framework.interaction.LLMInteractionSettings;
import org.zexnocs.teanekoagent.llm.framework.message.LLMAssistantMessage;
import org.zexnocs.teanekoagent.llm.framework.message.content.LLMContentListBuilder;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptions;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptionsSnapshot;
import org.zexnocs.teanekoagent.llm.framework.model.ModelCapabilities;
import org.zexnocs.teanekoagent.memory.layered.*;
import org.zexnocs.teanekoagent.tool.TeaNekoAgentToolMethods;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.logger.ILogger;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 复用事务夹具检验从真实发言到长期画像、召回和本人维护的完整闭环。
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.0.0
 * @version 4.6.1-l
 */
@Timeout(25)
class LongTermMemoryIntegrationTest {
    private final Instant now = Instant.parse("2026-11-03T00:00:00Z");
    private final UUID user = new UUID(0, 1);
    private final AgentScope scope = new AgentScope("test", "群甲");
    private final MemoryNamespaceReference reference = new MemoryNamespaceReference(scope, MemoryNamespaceId.SHARED, 0, 0);
    private final LayeredMemorySettings settings = new LayeredMemorySettings(true, 6, 2, 4, 131072, Duration.ofSeconds(30), Duration.ofDays(90));
    /**
     * 持有真实适配器，所有证据均经原历史事务进入分层存储。
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.0.0
     * @version 4.6.1-l
     */
    private final class Harness implements AutoCloseable {
        final NamespaceDatabaseFixture db = new NamespaceDatabaseFixture();
        final EasyDataLayeredMemoryStore layers = new EasyDataLayeredMemoryStore(db.data, db.store, db.mapper);
        final EasyDataHistoryStore history = new EasyDataHistoryStore(db.data, db.mapper, layers);
        final EasyDataLongTermMemoryStore longs = new EasyDataLongTermMemoryStore(db.data, db.store, db.mapper, () -> settings);
        final IAgentMemoryReferences refs = mock(IAgentMemoryReferences.class);
        int sequence;
        /** 创建真实共享空间并固定原引用校验。 */
        Harness() {
            db.store.create(new MemoryNamespace(MemoryNamespaceId.SHARED, 0, 0, now, null, null), 10).toCompletableFuture().join();
            when(refs.verify(any())).thenReturn(CompletableFuture.completedStage(null));
        }
        /**
         * 留下足够真实消息并完成一次中期压缩。
         * @param statement 本轮作者原话
         */
        void medium(String statement) {
            int count = sequence == 0 ? 6 : 4;
            for (int i = 0; i < count; i++) {
                int number = ++sequence;
                var actor = actor("消息" + number);
                var message = new HistoryMessage(actor.sourceId(), actor, HistoryMessage.Role.USER,
                        statement + "。我们正在讨论日常偏好与项目进展，先保存真实交流，再自然整理重要的信息。".repeat(5), now.plusSeconds(number), false, false);
                var context = new HistoryContext(reference, new AgentPersonalitySnapshot("人格", "teaneko", 1, "补正", 0, "基础", "", null));
                history.append(context, message, now.plus(Duration.ofDays(7))).toCompletableFuture().join();
            }
            var batch = layers.claim(stream(), settings, now.plusSeconds(100)).toCompletableFuture().join().orElseThrow();
            assertTrue(layers.commit(batch, new MemoryLayers.Digest("用户交流偏好", "用户表达了自己的日常偏好，并讨论项目进展。"), now.plusSeconds(101)).toCompletableFuture().join());
        }
        /** @return 已持久的真实分区 */
        MemoryLayers.Stream stream() { return layers.streams("").toCompletableFuture().join().getFirst(); }
        /** @return 已空闲到可整理的固定中期输入 */
        LongMemories.Batch claim() { return longs.claim(stream(), now.plusSeconds(1000)).toCompletableFuture().join().orElseThrow(); }
        /**
         * 以最新可验证原话建立测试候选，不由测试直接写数据库。
         * @param batch 固定输入
         * @param text 长期正文
         * @param mode 更新语义
         * @return 合格事实候选
         */
        LongMemories.Candidate candidate(LongMemories.Batch batch, String text, LongMemories.Mode mode) {
            var evidence = batch.evidence().getLast();
            var segment = batch.segments().stream().filter(value -> evidence.sequence() >= value.from() && evidence.sequence() <= value.through()).findFirst().orElseThrow();
            return new LongMemories.Candidate(LongMemories.Kind.PREFERENCE, user, "preference.drink", text, mode,
                    List.of(new LongMemories.Quote(evidence.id(), evidence.message().text().split("。")[0])), List.of(segment.id()), "");
        }
        /**
         * 完成本批长期事实提交。
         * @param text 保存正文
         * @param mode 更新语义
         * @return 提交的公开条目
         */
        LongMemories.Item learn(String text, LongMemories.Mode mode) {
            var batch = claim(); assertTrue(longs.commit(batch, List.of(candidate(batch, text, mode)), now.plusSeconds(1001)).toCompletableFuture().join());
            return longs.query(reference, user, text, 20).toCompletableFuture().join().items().stream().filter(item -> item.text().equals(text)).findFirst().orElseThrow();
        }
        /** 关闭夹具拥有的执行器。 */
        @Override public void close() { db.close(); }
    }
    /**
     * 固定真实用户身份，不允许模型参数决定作者。
     * @param source 原消息标识
     * @return 真实作者
     */
    private AgentActorContext actor(String source) { return new AgentActorContext(scope, AgentActorContext.Origin.USER, Optional.of(user), "用户", source, "群甲"); }
    /**
     * 构造具有原文凭据的宿主请求。
     * @param text 原用户文本
     * @return 固定可信请求
     */
    private AgentRunRequest request(String text) {
        return new AgentRunRequest(UUID.randomUUID(), actor("本轮"), AgentActivity.COMMAND, List.of(), null, reference, new AgentUserText("本轮", text));
    }
    /** 自动整理无需“记住”指令；重启后跨群共享画像并通过原注解编译器注册工具。 */
    @Test void consolidatesAutomaticallyAndRecallsSharedProfile() {
        try (var h = new Harness(); var timers = Executors.newSingleThreadScheduledExecutor(); var control = ExecutionControl.create()) {
            h.medium("我平时喜欢喝茶");
            try (var processor = new LayeredMemoryProcessor(h.layers, (batch, current) -> { throw new AssertionError("不应重复压缩原消息"); }, h.refs,
                    () -> settings, ignored -> new LayeredMemoryPolicy(true, true, true), Runnable::run, timers, Clock.fixed(now.plusSeconds(1000), ZoneOffset.UTC),
                    mock(ILogger.class), h.longs, (batch, current) -> CompletableFuture.completedStage(List.of(h.candidate(batch, "喜欢喝茶", LongMemories.Mode.ADD))), ignored -> true)) {
                processor.drain().toCompletableFuture().join();
            }
            var restored = new EasyDataLongTermMemoryStore(h.db.data, h.db.store, h.db.mapper, () -> settings);
            var other = new MemoryNamespaceReference(new AgentScope("test", "群乙"), MemoryNamespaceId.SHARED, 9, 0);
            var items = restored.query(other, user, "喝茶", 8).toCompletableFuture().join().items();
            assertEquals("喜欢喝茶", items.getFirst().text());
            assertFalse(h.db.mapper.writeValueAsString(items).contains("evidenceId"));
            assertFalse(restored.verify(new MemoryNamespaceReference(scope, MemoryNamespaceId.SHARED, 0, 1), items).toCompletableFuture().join());
            var access = new LongTermMemoryAccess(restored, h.refs, ignored -> new LongTermMemoryAccess.Policy(true, true, true, true), Runnable::run, Clock.fixed(now, ZoneOffset.UTC));
            var definition = new AgentDefinition("测试", new LLMModelOptionsSnapshot(null), Set.of(AgentActivity.COMMAND, AgentActivity.TOOL));
            var source = new LongTermMemoryPromptSource(access, h.db.mapper, Runnable::run);
            var fragments = source.resolve(definition, request("你记得我的偏好吗"), null, control).toCompletableFuture().join();
            assertTrue(fragments.getFirst().text().contains("喜欢喝茶"));
            assertEquals(1, fragments.size());
            assertTrue(source.resolve(definition, request("请忘记我的饮料偏好"), null, control).toCompletableFuture().join().isEmpty());
            var original = List.<org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMMessage>of(
                    org.zexnocs.teanekoagent.llm.framework.message.LLMUserMessage.builder().contents(
                            new org.zexnocs.teanekoagent.llm.framework.message.content.LLMContentListBuilder().addText("你好").build()).build());
            source.verify(request("你好"), new AgentPromptInput(original, fragments, 0), control).toCompletableFuture().join();
            var tools = new LongTermMemoryTools(access);
            assertEquals(2, TeaNekoAgentToolMethods.select(tools, definition, request("你是不是忘记我了"), null, null, h.db.mapper, Runnable::run).size());
            assertEquals(3, TeaNekoAgentToolMethods.select(tools, definition, request("请忘记我的饮料偏好"), null, null, h.db.mapper, Runnable::run).size());
            var operation = UUID.randomUUID(); var item = items.getFirst();
            restored.change(reference, user, item.id(), item.revision(), null, operation, "真实维护消息", now).toCompletableFuture().join();
            assertThrows(CompletionException.class, () -> source.verify(request("你好"), new AgentPromptInput(original, fragments, 0), control).toCompletableFuture().join());
            // 重新绑定并重放同一平台消息，新的片段 ID 与正文不能绕过来源墓碑。
            var rebound = new MemoryNamespaceReference(scope, MemoryNamespaceId.SHARED, 1, 0);
            var reboundContext = new HistoryContext(rebound, new AgentPersonalitySnapshot("人格", "teaneko", 1, "补正", 0, "基础", "", null));
            for (var raw : h.layers.read(reference, now, settings.retention()).toCompletableFuture().join().recent()) {
                h.history.append(reboundContext, raw.message(), raw.expiresAt()).toCompletableFuture().join();
            }
            var stream = h.layers.streams("").toCompletableFuture().join().stream().filter(value -> value.reference().equals(rebound)).findFirst().orElseThrow();
            var shortBatch = h.layers.claim(stream, settings, now.plusSeconds(1100)).toCompletableFuture().join().orElseThrow();
            assertTrue(h.layers.commit(shortBatch, new MemoryLayers.Digest("重新导入", "旧偏好对话"), now.plusSeconds(1101)).toCompletableFuture().join());
            var replay = restored.claim(stream, now.plusSeconds(2000)).toCompletableFuture().join().orElseThrow();
            assertTrue(restored.commit(replay, List.of(h.candidate(replay, "日常偏爱茶饮", LongMemories.Mode.ADD)), now.plusSeconds(2001)).toCompletableFuture().join());
            assertTrue(restored.query(rebound, user, "", 8).toCompletableFuture().join().items().isEmpty());
        }
    }
    /** 伪造主体与原话不能推进水位；写入失败回滚记录和索引，旧租约不能覆盖重领任务。 */
    @Test void validatesEvidenceAndCommitsAtomically() {
        try (var h = new Harness()) {
            h.medium("我喜欢喝茶"); var batch = h.claim(); var valid = h.candidate(batch, "喜欢喝茶", LongMemories.Mode.ADD);
            var forged = new LongMemories.Candidate(valid.kind(), UUID.randomUUID(), valid.attribute(), valid.text(), valid.mode(), valid.quotes(), valid.segments(), "");
            assertThrows(CompletionException.class, () -> h.longs.commit(batch, List.of(forged), now.plusSeconds(1001)).toCompletableFuture().join());
            var fakeQuote = new LongMemories.Candidate(valid.kind(), user, valid.attribute(), valid.text(), valid.mode(),
                    List.of(new LongMemories.Quote(valid.quotes().getFirst().evidenceId(), "我喜欢咖啡")), valid.segments(), "");
            assertThrows(CompletionException.class, () -> h.longs.commit(batch, List.of(fakeQuote), now.plusSeconds(1001)).toCompletableFuture().join());
            h.db.failingWritePrefix.set("entries:");
            assertThrows(CompletionException.class, () -> h.longs.commit(batch, List.of(valid), now.plusSeconds(1001)).toCompletableFuture().join());
            h.db.failingWritePrefix.set(null);
            assertTrue(h.longs.query(reference, user, "", 8).toCompletableFuture().join().items().isEmpty());
            var replacement = h.longs.claim(h.stream(), now.plusSeconds(1100)).toCompletableFuture().join().orElseThrow();
            assertFalse(h.longs.commit(batch, List.of(valid), now.plusSeconds(1101)).toCompletableFuture().join());
            assertTrue(h.longs.commit(replacement, List.of(valid), now.plusSeconds(1101)).toCompletableFuture().join());
            assertFalse(h.longs.commit(replacement, List.of(valid), now.plusSeconds(1102)).toCompletableFuture().join());
        }
    }
    /** 变化替代旧值，本人维护按版本且幂等；同一旧证据换正文仍不能复活已遗忘信息。 */
    @Test void replacesAndProtectsManualChangesAndTombstones() {
        try (var h = new Harness()) {
            h.medium("我喜欢喝茶"); var old = h.learn("喜欢喝茶", LongMemories.Mode.ADD);
            h.medium("我现在改喝咖啡了"); var current = h.learn("现在喜欢咖啡", LongMemories.Mode.REPLACE);
            assertFalse(h.longs.verify(reference, List.of(old)).toCompletableFuture().join());
            assertEquals(1, h.longs.query(reference, user, "", 20).toCompletableFuture().join().items().size());
            assertThrows(CompletionException.class, () -> h.longs.change(reference, UUID.randomUUID(), current.id(), current.revision(), null,
                    UUID.randomUUID(), "伪造维护", now).toCompletableFuture().join());
            var correction = UUID.randomUUID();
            var corrected = h.longs.change(reference, user, current.id(), current.revision(), "只喝无咖啡因咖啡", correction, "纠正消息", now).toCompletableFuture().join();
            assertEquals(corrected, h.longs.change(reference, user, current.id(), current.revision(), "只喝无咖啡因咖啡", correction, "纠正消息", now).toCompletableFuture().join());
            assertThrows(CompletionException.class, () -> h.longs.change(reference, user, current.id(), current.revision(), "换参", correction, "纠正消息", now).toCompletableFuture().join());
            var forgotten = h.longs.change(reference, user, corrected.id(), corrected.revision(), null, UUID.randomUUID(), "遗忘消息", now).toCompletableFuture().join();
            assertEquals(LongMemories.Status.FORGOTTEN, forgotten.status());
            assertTrue(h.longs.query(reference, user, "", 20).toCompletableFuture().join().items().isEmpty());
            // 同批第二条候选在人工维护之后重读旧来源，不能以新正文、新属性重新登记。
            var sourceBatch = h.longs.claim(h.stream(), now.plusSeconds(2000)).toCompletableFuture().join();
            assertTrue(sourceBatch.isEmpty());
            h.medium("我现在改喝咖啡了"); var fresh = h.claim();
            var candidate = h.candidate(fresh, "现在喜欢咖啡", LongMemories.Mode.ADD);
            assertTrue(h.longs.commit(fresh, List.of(candidate), now.plusSeconds(1001)).toCompletableFuture().join());
            assertTrue(h.longs.query(reference, user, "", 20).toCompletableFuture().join().items().isEmpty());
        }
    }
    /** 模型返回前撤销学习权限不提交；关闭不让迟到结果交付，后续可恢复原中期任务。 */
    @Test void rejectsLateLearningAfterPolicyWithdrawal() {
        try (var h = new Harness(); var timers = Executors.newSingleThreadScheduledExecutor()) {
            h.medium("我喜欢喝茶"); var learning = new AtomicBoolean(true);
            var entered = new CompletableFuture<LongMemories.Batch>(); var late = new CompletableFuture<List<LongMemories.Candidate>>();
            try (var processor = new LayeredMemoryProcessor(h.layers, (batch, control) -> CompletableFuture.completedStage(new MemoryLayers.Digest("摘要", "中期")), h.refs,
                    () -> settings, ignored -> new LayeredMemoryPolicy(true, true, true), Runnable::run, timers, Clock.fixed(now.plusSeconds(1000), ZoneOffset.UTC), mock(ILogger.class),
                    h.longs, (batch, control) -> { entered.complete(batch); return late; }, ignored -> learning.get())) {
                var work = processor.drain().toCompletableFuture(); var batch = entered.orTimeout(5, TimeUnit.SECONDS).join();
                learning.set(false); late.complete(List.of(h.candidate(batch, "喜欢喝茶", LongMemories.Mode.ADD))); work.join();
                assertTrue(h.longs.query(reference, user, "", 8).toCompletableFuture().join().items().isEmpty());
                assertTrue(h.longs.claim(h.stream(), now.plusSeconds(1100)).toCompletableFuture().join().isPresent());
            }
        }
    }
    /** 实际学习适配器保持统一门控与无工具请求，严格拒绝模型额外字段，释放自有实例。 */
    @Test void usesGuardedModelAdapterAndStrictCandidateSchema() {
        try (var h = new Harness(); var registry = new AgentRegistry(); var control = ExecutionControl.create()) {
            h.medium("我喜欢喝茶"); var batch = h.claim();
            var runtime = new AgentRuntimeSnapshot(new LLMModelOptionsSnapshot(LLMModelOptions.builder().provider("test").model("离线模型").maxTokens(4096).build()),
                    ModelCapabilities.textOnly(), LLMInteractionSettings.defaults());
            var gate = new AgentAccessGate(registry, ignored -> new AgentScopePolicy(true, true, AgentScopePolicy.Mode.ACTIVE),
                    ignored -> new AgentReadiness(AgentReadiness.Status.READY, Optional.of(runtime)), mock(ILogger.class));
            var interactions = mock(ILLMInteractionService.class);
            var output = new java.util.concurrent.atomic.AtomicReference<>(h.db.mapper.writeValueAsString(List.of(h.candidate(batch, "喜欢喝茶", LongMemories.Mode.ADD))));
            when(interactions.interact(any(), any(), any(), any(), any())).thenAnswer(call -> {
                org.zexnocs.teanekoagent.llm.framework.input.interfaces.ILLMPrompt prompt = call.getArgument(0);
                assertTrue(Objects.requireNonNull(prompt.getOptions().getTools()).isEmpty());
                assertEquals(3072, prompt.getOptions().getMaxTokens());
                var answer = LLMAssistantMessage.builder().contents(new LLMContentListBuilder().addText(output.get()).build()).build();
                return CompletableFuture.completedStage(new LLMInteractionResult(LLMInteractionResult.Status.SUCCESS, List.of(answer), answer, List.of(), List.of(), Map.of()));
            });
            try (var generator = new LLMLongTermMemoryConsolidator(() -> new AgentFactory(registry), gate, h.refs, interactions,
                    () -> settings, ignored -> true, h.db.mapper, Runnable::run)) {
                assertEquals(0, registry.snapshots().size());
                var values = generator.consolidate(batch, control).toCompletableFuture().join();
                assertEquals("喜欢喝茶", values.getFirst().text());
                // 2026/11/03：同次联合协议保持严格结构，不把表达观察塞入用户事实。
                var basis = new org.zexnocs.teanekoagent.agent.personality.AgentPersonalitySnapshot("人格", "teaneko", 1, "scope", 0, "友善自然", "", null);
                var styleTarget = new org.zexnocs.teanekoagent.personality.StyleObservations.Target(basis, Set.of("speakingStyle"),
                        batch.evidence().stream().map(LongMemories.Evidence::id).collect(java.util.stream.Collectors.toSet()), List.of());
                var style = new org.zexnocs.teanekoagent.personality.StyleObservations.Candidate("speakingStyle", "先说结论", org.zexnocs.teanekoagent.personality.StyleObservations.Context.GENERAL,
                        values.getFirst().quotes(), values.getFirst().segments());
                var originalOutput = output.get();
                output.set(h.db.mapper.writeValueAsString(new org.zexnocs.teanekoagent.personality.StyleObservations.Analysis(values, List.of(style))));
                assertEquals(1, generator.analyze(batch, styleTarget, control).toCompletableFuture().join().styles().size());
                output.set(originalOutput);
                output.set(output.get().replace("\"kind\":", "\"unexpected\":true,\"kind\":"));
                assertThrows(CompletionException.class, () -> generator.consolidate(batch, control).toCompletableFuture().join());
                output.set("[]");
                assertTrue(generator.consolidate(batch, control).toCompletableFuture().join().isEmpty());
                assertTrue(h.longs.commit(batch, List.of(), now.plusSeconds(1001)).toCompletableFuture().join());
                assertTrue(h.longs.claim(h.stream(), now.plusSeconds(2000)).toCompletableFuture().join().isEmpty());
            }
            assertTrue(registry.find(registry.snapshots().getFirst().id()).orElseThrow().isClosed());
        }
    }
}
