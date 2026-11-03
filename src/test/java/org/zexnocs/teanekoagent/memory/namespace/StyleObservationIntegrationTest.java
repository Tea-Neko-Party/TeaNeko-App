package org.zexnocs.teanekoagent.memory.namespace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.zexnocs.teanekoagent.adapter.history.EasyDataHistoryStore;
import org.zexnocs.teanekoagent.adapter.memory.EasyDataLayeredMemoryStore;
import org.zexnocs.teanekoagent.adapter.memory.EasyDataLongTermMemoryStore;
import org.zexnocs.teanekoagent.adapter.personality.EasyDataPersonalityStore;
import org.zexnocs.teanekoagent.adapter.personality.EasyDataStyleObservationStore;
import org.zexnocs.teanekoagent.adapter.personality.StyleObservationTools;
import org.zexnocs.teanekoagent.agent.AgentActorContext;
import org.zexnocs.teanekoagent.agent.AgentScope;
import org.zexnocs.teanekoagent.agent.memory.IAgentMemoryReferences;
import org.zexnocs.teanekoagent.agent.personality.AgentPersonalitySnapshot;
import org.zexnocs.teanekoagent.history.HistoryContext;
import org.zexnocs.teanekoagent.history.HistoryMessage;
import org.zexnocs.teanekoagent.memory.layered.*;
import org.zexnocs.teanekoagent.personality.*;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.logger.ILogger;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 复用真实事务夹具验证观察积累、渐进采用、原人格隔离与联合提交回滚。
 * <br>2026/11/03：补充观察原话和补正到期清理、容量释放及不可变备份保护。
 * <br>2026/11/03：核对默认送达账本与分层资料的联合装配。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.6.1-l
 * @version 4.6.2-l
 */
@Timeout(25)
class StyleObservationIntegrationTest {
    private final Instant now = Instant.parse("2026-11-03T00:00:00Z");
    private final AgentScope scope = new AgentScope("test", "群甲");
    private final MemoryNamespaceReference reference = new MemoryNamespaceReference(scope, MemoryNamespaceId.SHARED, 0, 0);
    private final LayeredMemorySettings settings = new LayeredMemorySettings(true, 6, 2, 4, 131072, Duration.ofSeconds(30), Duration.ofDays(90));
    /**
     * 原人格、历史、短中期与表达域共享现有事务基础设施。
     * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
     *
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.6.1-l
     * @version 4.6.2-l
     */
    private final class Harness implements AutoCloseable {
        final NamespaceDatabaseFixture db = new NamespaceDatabaseFixture();
        final EasyDataPersonalityStore personalities = new EasyDataPersonalityStore(db.data, db.mapper);
        final EasyDataStyleObservationStore styles = new EasyDataStyleObservationStore(db.data, personalities, db.mapper);
        final EasyDataLayeredMemoryStore layers = new EasyDataLayeredMemoryStore(db.data, db.store, db.mapper);
        final EasyDataHistoryStore history = new EasyDataHistoryStore(db.data, db.mapper, layers);
        final EasyDataLongTermMemoryStore longs = new EasyDataLongTermMemoryStore(db.data, db.store, db.mapper, () -> settings, styles);
        final IPersonalityConfiguration configuration = mock(IPersonalityConfiguration.class);
        final AtomicReference<BasePersonality> base = new AtomicReference<>(new BasePersonality("teaneko", 1, "小茶", List.of("友善自然地交流"), Set.of("speakingStyle")));
        final AtomicBoolean enabled = new AtomicBoolean(true);
        final PersonalityService source = new PersonalityService(configuration, personalities, Runnable::run, Clock.fixed(now, ZoneOffset.UTC), db.mapper);
        final StyleObservationService observer = new StyleObservationService(source, configuration, styles, ignored -> enabled.get(), Runnable::run, Clock.fixed(now.plusSeconds(1000), ZoneOffset.UTC));
        int sequence;
        /** 初始化真实空间及原配置，所有样本使用实际解析的人格身份。 */
        Harness() {
            db.store.create(new MemoryNamespace(MemoryNamespaceId.SHARED, 0, 0, now, null, null), 10).toCompletableFuture().join();
            when(configuration.settings(any())).thenAnswer(call -> new PersonalitySettings("", false, "", "", true, enabled.get()));
            when(configuration.base(any())).thenAnswer(call -> base.get());
        }
        /**
         * 保存真实人类交流与一段中期结果，避免在测试中伪造观察存储。
         * @return 新中期片段所属分区
         */
        MemoryLayers.Stream medium() {
            try (var control = ExecutionControl.create()) {
                var basis = source.resolve(scope, control).toCompletableFuture().join();
                int count = sequence == 0 ? 6 : 4;
                for (int i = 0; i < count; i++) {
                    int number = ++sequence;
                    var actor = new AgentActorContext(scope, AgentActorContext.Origin.USER, Optional.of(new UUID(0, 1)), "用户", "原消息" + number, "群甲");
                    var message = new HistoryMessage(actor.sourceId(), actor, HistoryMessage.Role.USER,
                            "先讲结论，再举个小例子呀。我们再一起看看具体的步骤和问题。".repeat(8), now.plusSeconds(number), false, false);
                    history.append(new HistoryContext(reference, basis), message, now.plus(Duration.ofDays(7))).toCompletableFuture().join();
                }
                var stream = layers.streams("").toCompletableFuture().join().getFirst();
                var batch = layers.claim(stream, settings, now.plusSeconds(100)).toCompletableFuture().join().orElseThrow();
                assertTrue(layers.commit(batch, new MemoryLayers.Digest("交流表达", "真实用户通过结论和例子展开讨论。"), now.plusSeconds(101)).toCompletableFuture().join());
                return stream;
            }
        }
        /** @return 原租约固定的中期输入 */
        LongMemories.Batch claim() { return longs.claim(layers.streams("").toCompletableFuture().join().getFirst(), now.plusSeconds(1000)).toCompletableFuture().join().orElseThrow(); }
        /**
         * 用本批两条不同原话构造同一表达模式。
         * @param batch 固定输入
         * @return 已定位原样本的候选
         */
        StyleObservations.Candidate candidate(LongMemories.Batch batch) {
            return new StyleObservations.Candidate("speakingStyle", "先说结论，再用简短例子说明", StyleObservations.Context.TECHNICAL,
                    batch.evidence().stream().limit(2).map(value -> new LongMemories.Quote(value.id(), "先讲结论，再举个小例子呀")).toList(),
                    batch.segments().stream().map(MemoryLayers.Segment::id).toList());
        }
        /**
         * 读取真实人格中的生效补正。
         * @param target 实际作用域
         * @return 当前补正投影
         */
        AgentPersonalitySnapshot snapshot(AgentScope target) {
            try (var control = ExecutionControl.create()) { return source.resolve(target, control).toCompletableFuture().join(); }
        }
        /** 关闭本夹具拥有的数据库执行器。 */
        @Override public void close() { db.close(); }
    }
    /** 重复样本不增加支持；第二段独立证据才采用，观察/补正/水位任意写失败一起回滚。 */
    @Test void accumulatesDistinctEvidenceAndAdoptsAtomicallyWithinScope() {
        try (var h = new Harness(); var control = ExecutionControl.create()) {
            h.medium(); var first = h.claim(); var target = Objects.requireNonNull(h.observer.prepare(first, control).toCompletableFuture().join());
            var candidate = h.candidate(first);
            assertTrue(h.longs.commit(first, new StyleObservations.Analysis(List.of(), List.of(candidate, candidate)), target, now.plusSeconds(1001),
                    h.observer.guard(first, target, control)).toCompletableFuture().join());
            var view = h.styles.inspect(scope, target.basis(), now.plusSeconds(1001)).toCompletableFuture().join().getFirst();
            assertEquals(2, view.messages()); assertEquals(1, view.segments()); assertFalse(view.adopted());
            assertTrue(h.snapshot(scope).adjustmentsJson().isEmpty());
            h.medium(); var second = h.claim(); var next = Objects.requireNonNull(h.observer.prepare(second, control).toCompletableFuture().join());
            var analysis = new StyleObservations.Analysis(List.of(), List.of(h.candidate(second)));
            h.db.failingWritePrefix.set("patterns:");
            assertThrows(CompletionException.class, () -> h.longs.commit(second, analysis, next, now.plusSeconds(1001),
                    h.observer.guard(second, next, control)).toCompletableFuture().join());
            h.db.failingWritePrefix.set(null);
            assertTrue(h.snapshot(scope).adjustmentsJson().isEmpty());
            assertTrue(h.longs.commit(second, analysis, next, now.plusSeconds(1001), h.observer.guard(second, next, control)).toCompletableFuture().join());
            assertTrue(h.snapshot(scope).adjustmentsJson().contains("先说结论"));
            assertTrue(h.snapshot(new AgentScope("test", "群乙")).adjustmentsJson().isEmpty());
            var restored = new EasyDataStyleObservationStore(h.db.data, h.personalities, h.db.mapper);
            var learned = restored.inspect(scope, h.snapshot(scope), now.plusSeconds(1002)).toCompletableFuture().join().getFirst();
            assertEquals(4, learned.messages()); assertTrue(learned.adopted());
            assertFalse(h.db.mapper.writeValueAsString(learned).contains("quote"));
            assertTrue(h.longs.query(reference, null, "", 8).toCompletableFuture().join().items().isEmpty());
        }
    }
    /** 伪造样本、人格切换和空间 clear 拒绝迟到观察，不用新的目标续用旧结果。 */
    @Test void rejectsForgedSamplesAndLatePersonaOrNamespaceTargets() {
        try (var h = new Harness(); var control = ExecutionControl.create()) {
            h.medium(); var batch = h.claim(); var target = Objects.requireNonNull(h.observer.prepare(batch, control).toCompletableFuture().join());
            var candidate = h.candidate(batch);
            var forged = new StyleObservations.Candidate(candidate.field(), candidate.pattern(), candidate.context(),
                    List.of(new LongMemories.Quote(candidate.quotes().getFirst().evidenceId(), "并不存在的原话")), candidate.segments());
            assertThrows(CompletionException.class, () -> h.longs.commit(batch, new StyleObservations.Analysis(List.of(), List.of(forged)), target,
                    now.plusSeconds(1001), h.observer.guard(batch, target, control)).toCompletableFuture().join());
            h.base.set(new BasePersonality("teaneko", 2, "小茶", List.of("继续自然交流"), Set.of("speakingStyle"))); h.snapshot(scope);
            assertThrows(CompletionException.class, () -> h.longs.commit(batch, new StyleObservations.Analysis(List.of(), List.of(candidate)), target,
                    now.plusSeconds(1001), h.observer.guard(batch, target, control)).toCompletableFuture().join());
            assertTrue(h.styles.inspect(scope, target.basis(), now).toCompletableFuture().join().isEmpty());
            h.db.store.advanceGeneration(MemoryNamespaceId.SHARED, 0, UUID.randomUUID()).toCompletableFuture().join();
            assertFalse(h.longs.commit(batch, new StyleObservations.Analysis(List.of(), List.of(candidate)), target, now.plusSeconds(1001), () -> {}).toCompletableFuture().join());
        }
    }
    /** 事实学习关闭时仍可观察表达；调度只分析一次，撤销人格学习后不提交迟到样本。 */
    @Test void processesStylesWithIndependentLearningPolicy() {
        try (var h = new Harness(); var timers = Executors.newSingleThreadScheduledExecutor()) {
            h.medium(); var entered = new CompletableFuture<Void>(); var late = new CompletableFuture<StyleObservations.Analysis>(); var calls = new AtomicInteger();
            var generator = new ILongTermMemoryConsolidator() {
                /**
                 * 本验证应通过联合入口执行，不得另开一次事实生成。
                 * @param batch 原输入
                 * @param control 原控制
                 * @return 拒绝额外调用
                 */
                @Override public CompletionStage<List<LongMemories.Candidate>> consolidate(LongMemories.Batch batch, ExecutionControl control) { throw new AssertionError("不应额外生成"); }
                /**
                 * 暂停真实异步边界，验证返回前资格撤销。
                 * @param batch 固定来源
                 * @param target 已复核目标
                 * @param control 本批控制
                 * @return 可暂停的生成阶段
                 */
                @Override public CompletionStage<StyleObservations.Analysis> analyze(LongMemories.Batch batch, StyleObservations.Target target, ExecutionControl control) {
                    assertNotNull(target); calls.incrementAndGet(); entered.complete(null); return late;
                }
            };
            var refs = mock(IAgentMemoryReferences.class); when(refs.verify(any())).thenReturn(CompletableFuture.completedStage(null));
            try (var processor = new LayeredMemoryProcessor(h.layers, (batch, control) -> { throw new AssertionError("不应重压缩"); }, refs, () -> settings,
                    ignored -> new LayeredMemoryPolicy(true, true, true), Runnable::run, timers, Clock.fixed(now.plusSeconds(1000), ZoneOffset.UTC), mock(ILogger.class),
                    h.longs, generator, ignored -> false, h.observer)) {
                var work = processor.drain().toCompletableFuture(); entered.orTimeout(5, TimeUnit.SECONDS).join(); h.enabled.set(false);
                late.complete(new StyleObservations.Analysis(List.of(), List.of())); work.join();
                assertEquals(1, calls.get()); assertTrue(h.styles.inspect(scope, h.snapshot(scope), now).toCompletableFuture().join().isEmpty());
                assertTrue(h.longs.claim(h.layers.streams("").toCompletableFuture().join().getFirst(), now.plusSeconds(1100)).toCompletableFuture().join().isPresent());
            }
        }
    }
    /** 2026/11/03：默认 Spring 组合能找到观察与送达端口，构造期间不读取配置、创建模型实例或启动学习。 */
    @Test void wiresDefaultObservationWithoutStartingModels() {
        try (var h = new Harness(); var executor = Executors.newVirtualThreadPerTaskExecutor(); var timers = Executors.newSingleThreadScheduledExecutor();
             var context = new org.springframework.context.annotation.AnnotationConfigApplicationContext()) {
            var models = mock(org.zexnocs.teanekoagent.llm.framework.interaction.ILLMInteractionService.class);
            var gate = mock(org.zexnocs.teanekoagent.agent.access.AgentAccessGate.class);
            var refs = mock(IAgentMemoryReferences.class);
            var configs = mock(org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataService.class);
            var beans = context.getBeanFactory();
            beans.registerSingleton("data", h.db.data); beans.registerSingleton("namespaces", h.db.store); beans.registerSingleton("mapper", h.db.mapper);
            beans.registerSingleton("personalityStore", h.personalities); beans.registerSingleton("configuration", h.configuration); beans.registerSingleton("personalities", h.source);
            beans.registerSingleton("interactions", models); beans.registerSingleton("gate", gate); beans.registerSingleton("references", refs); beans.registerSingleton("configs", configs);
            beans.registerSingleton("manager", new org.zexnocs.teanekoagent.adapter.config.AgentScopeConfigManager());
            beans.registerSingleton("virtualExecutor", executor); beans.registerSingleton("taskExecutor", timers); beans.registerSingleton("logger", mock(ILogger.class));
            context.register(org.zexnocs.teanekoagent._app_config.StyleObservationConfig.class, org.zexnocs.teanekoagent._app_config.LayeredMemoryConfig.class,
                    org.zexnocs.teanekoagent._app_config.PersonalityLearningConfig.class, org.zexnocs.teanekoagent._app_config.AgentDeliveryConfig.class);
            context.refresh();
            assertInstanceOf(EasyDataStyleObservationStore.class, context.getBean(IStyleObservationStore.class));
            assertInstanceOf(org.zexnocs.teanekoagent.adapter.memory.EasyDataDeliveryStore.class, context.getBean(org.zexnocs.teanekoagent.history.IAgentDeliveryStore.class));
            assertNotNull(context.getBean(org.zexnocs.teanekoagent.history.AgentDeliveryService.class));
            assertNotNull(context.getBean(StyleObservationService.class));
            assertEquals(List.of("personality_observations"), org.zexnocs.teanekoagent.tool.TeaNekoAgentToolMethods.names(context.getBean(StyleObservationTools.class).getClass()));
            verifyNoInteractions(models, gate, refs, configs);
        }
    }
    /** 过期观察去掉原话，普通补正释放容量，基础变化留下的不可变备份保持原内容。 */
    @Test void expiresObservationSamplesAndCorrectionsWithoutChangingBackups() {
        try (var h = new Harness(); var control = ExecutionControl.create()) {
            // 2026/11/03：用真实跨片段观察产生补正，再验证整个维护路径。
            h.medium(); var first = h.claim(); var firstTarget = Objects.requireNonNull(h.observer.prepare(first, control).toCompletableFuture().join());
            assertTrue(h.longs.commit(first, new StyleObservations.Analysis(List.of(), List.of(h.candidate(first))), firstTarget,
                    now.plusSeconds(1001), h.observer.guard(first, firstTarget, control)).toCompletableFuture().join());
            h.medium(); var second = h.claim(); var target = Objects.requireNonNull(h.observer.prepare(second, control).toCompletableFuture().join());
            assertTrue(h.longs.commit(second, new StyleObservations.Analysis(List.of(), List.of(h.candidate(second))), target,
                    now.plusSeconds(1001), h.observer.guard(second, target, control)).toCompletableFuture().join());
            var originalKey = h.snapshot(scope).correctionKey();
            h.base.set(new BasePersonality("teaneko", 2, "小茶", List.of("新基础人格"), Set.of("speakingStyle"))); h.snapshot(scope);
            var backup = h.personalities.backups(originalKey).toCompletableFuture().join().getFirst();
            var saved = h.personalities.state(backup).toCompletableFuture().join().orElseThrow();
            var future = now.plus(Duration.ofDays(31));
            for (int i = 0; i < 20; i++) {
                h.styles.maintain(future).toCompletableFuture().join(); h.personalities.maintain(future).toCompletableFuture().join();
            }
            assertTrue(h.personalities.state(originalKey).toCompletableFuture().join().orElseThrow().corrections().isEmpty());
            assertEquals(saved, h.personalities.state(backup).toCompletableFuture().join().orElseThrow());
            var remaining = h.db.data.inNamespace("agent.personality.observations.v1");
            assertTrue(remaining.transaction("检查观察原话已回收", () -> remaining.get("maintenance").atomic().recentNamespace(Long.MAX_VALUE, 100)
                    .stream().filter(row -> row.getTarget().startsWith("patterns:"))
                    .allMatch(row -> h.db.mapper.readTree(row.getValue()).get("samples").isEmpty())).toCompletableFuture().join());
            assertTrue(remaining.transaction("检查采用水位仍保留", () -> remaining.get("maintenance").atomic().recentNamespace(Long.MAX_VALUE, 100)
                    .stream().anyMatch(row -> row.getTarget().startsWith("retired-patterns:"))).toCompletableFuture().join());
        }
    }
}
