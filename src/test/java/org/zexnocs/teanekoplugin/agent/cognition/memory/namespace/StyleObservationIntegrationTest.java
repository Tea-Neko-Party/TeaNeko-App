package org.zexnocs.teanekoplugin.agent.cognition.memory.namespace;

import org.zexnocs.teanekoplugin.contract.agent.AgentComponentFixtures;

// 2026/11/07：认知业务契约归入插件，纯会话数据沿用运行底座。

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

import org.zexnocs.teanekoplugin.agent.cognition.memory.layered.ILongTermMemoryConsolidator;
import org.zexnocs.teanekoplugin.agent.cognition.memory.layered.LayeredMemoryPolicy;
import org.zexnocs.teanekoplugin.agent.cognition.memory.layered.LayeredMemorySettings;
import org.zexnocs.teanekoplugin.agent.cognition.memory.layered.LongMemories;
import org.zexnocs.teanekoplugin.agent.cognition.memory.layered.MemoryEvidenceException;
import org.zexnocs.teanekoplugin.agent.cognition.memory.layered.MemoryLayers;
import org.zexnocs.teanekoplugin.agent.cognition.personality.AgentPersonalitySnapshot;
import org.zexnocs.teanekoplugin.agent.cognition.personality.BasePersonality;
import org.zexnocs.teanekoplugin.agent.cognition.personality.IPersonalityConfiguration;
import org.zexnocs.teanekoplugin.agent.cognition.personality.IStyleObservationStore;
import org.zexnocs.teanekoplugin.agent.cognition.personality.PersonalitySettings;
import org.zexnocs.teanekoplugin.agent.cognition.personality.StyleObservations;
import org.zexnocs.teanekoplugin.agent.cognition.memory.layered.LayeredMemoryProcessor;
import org.zexnocs.teanekoplugin.agent.cognition.personality.PersonalityService;
import org.zexnocs.teanekoplugin.agent.cognition.personality.StyleObservationService;
import org.zexnocs.teanekoplugin.agent.cognition.personality.StylePatternSimilarity;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.zexnocs.teanekoplugin.agent.cognition.memory.adapter.EasyDataLayeredMemoryStore;
import org.zexnocs.teanekoplugin.agent.cognition.memory.adapter.EasyDataLongTermMemoryStore;
import org.zexnocs.teanekoplugin.agent.cognition.memory.history.HistoryContext;
import org.zexnocs.teanekoplugin.agent.cognition.memory.history.HistoryMessage;
import org.zexnocs.teanekoplugin.agent.cognition.memory.history.adapter.EasyDataHistoryStore;
import org.zexnocs.teanekoplugin.agent.cognition.memory.layered.*;
import org.zexnocs.teanekoplugin.agent.cognition.memory.runtime.IAgentMemoryReferences;
import org.zexnocs.teanekoplugin.agent.cognition.personality.*;
import org.zexnocs.teanekoplugin.agent.cognition.personality.adapter.EasyDataPersonalityStore;
import org.zexnocs.teanekoplugin.agent.cognition.personality.adapter.EasyDataStyleObservationStore;
import org.zexnocs.teanekoplugin.agent.cognition.personality.adapter.StyleObservationTools;
import org.zexnocs.teanekoagent.runtime.AgentActorContext;
import org.zexnocs.teanekoagent.runtime.AgentScope;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.database.easydata.core.EasyDataLock;
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
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：适配可选组件装配及迁移后的数据键，保留原核心行为验证。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/05：补充组件审查修复的离线回归及初始化夹具。
 * <br>2026/11/05：验证空观察前缀可推进、后续有效样本可学习及排队后的真实撤权。
 * <br>2026/11/06：核对混合批次中错误风格的原因、序号及事实和风格整体回滚。
 * <br>2026/11/06：合并同字段同场景的近义模式和真实证据，保留原采用水位。
 *
 * <br>2026/11/07：测试宿主显式管理插件后台生命周期。
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.6.1-l
 * @version 4.9.0-l
 */
@Timeout(25)
class StyleObservationIntegrationTest {
    /** 2026/11/06：近义风格复用原记录并累加独立证据，否定与不同场景仍隔离。 */
    @Test
    void mergesSemanticStyleVariantsWithNewEvidence() {
        try (var h = new Harness(); var control = ExecutionControl.create()) {
            h.medium();
            var first = h.claim();
            var target = Objects.requireNonNull(h.observer.prepare(first, control).toCompletableFuture().join());
            var original = h.candidate(first);
            var greeting = new StyleObservations.Candidate(original.field(), "用户常用单个词重复打招呼",
                    StyleObservations.Context.CASUAL, original.quotes(), original.segments());
            assertTrue(h.longs.commit(first, new StyleObservations.Analysis(List.of(), List.of(greeting)), target,
                    now.plusSeconds(1001), h.observer.guard(first, target, control)).toCompletableFuture().join());
            h.medium();
            var second = h.claim();
            var next = Objects.requireNonNull(h.observer.prepare(second, control).toCompletableFuture().join());
            assertEquals(1, next.known().size());
            var fresh = h.candidate(second);
            var variant = new StyleObservations.Candidate(fresh.field(), "用户经常重复使用单词问候",
                    StyleObservations.Context.CASUAL, fresh.quotes(), fresh.segments());
            assertTrue(h.longs.commit(second, new StyleObservations.Analysis(List.of(), List.of(variant)), next,
                    now.plusSeconds(1001), h.observer.guard(second, next, control)).toCompletableFuture().join());
            var patterns = h.styles.inspect(scope, next.basis(), now.plusSeconds(1002)).toCompletableFuture().join();
            assertEquals(1, patterns.size());
            assertEquals(4, patterns.getFirst().messages());
            assertEquals(greeting.pattern(), patterns.getFirst().pattern());
            assertTrue(patterns.getFirst().adopted());
            assertFalse(StylePatternSimilarity.equivalent(greeting.pattern(), "用户不使用单词重复问候"));
            assertFalse(StylePatternSimilarity.equivalent(greeting.pattern(), "用户仅在技术问题中使用单词重复问候"));
            h.medium();
            var third = h.claim();
            var latest = Objects.requireNonNull(h.observer.prepare(third, control).toCompletableFuture().join());
            assertEquals(1, latest.known().size());
        }
    }
    /** 2026/11/06：按键命中窗口外的已采用模式时，保留原证据和采用时间，不重新学习同一模式。 */
    @Test
    void preservesExactPatternOutsideSimilarityWindow() {
        try (var h = new Harness(); var control = ExecutionControl.create()) {
            for (int i = 0; i < 2; i++) {
                h.medium();
                var batch = h.claim();
                var target = Objects.requireNonNull(h.observer.prepare(batch, control).toCompletableFuture().join());
                assertTrue(h.longs.commit(batch, new StyleObservations.Analysis(List.of(), List.of(h.candidate(batch))),
                        target, now.plusSeconds(1001), h.observer.guard(batch, target, control))
                        .toCompletableFuture().join());
            }
            var data = h.db.data.inNamespace("agent.personality.observations.v1");
            var original = data.transaction("将真实已采用记录移出最近比较窗口", () -> {
                var row = data.get("maintenance").atomic().recentNamespace(Long.MAX_VALUE, 100).stream()
                        .filter(value -> value.getTarget().startsWith("patterns:")).findFirst().orElseThrow();
                var dto = data.get(row.getTarget()).atomic();
                for (int i = 0; i < 64; i++) {
                    String id = "其他字段模式-" + i;
                    var filler = h.db.mapper.createObjectNode()
                            .put("id", id).put("field", "其他字段").put("text", "其他模式")
                            .put("context", "TECHNICAL");
                    filler.set("samples", h.db.mapper.createArrayNode());
                    dto.save(dto.create(id, h.db.mapper.writeValueAsString(filler)));
                }
                assertTrue(dto.recent(Long.MAX_VALUE, 64).stream().noneMatch(value -> value.getKey().equals(row.getKey())));
                return row;
            }).toCompletableFuture().join();
            var before = h.db.mapper.readTree(original.getValue());
            assertEquals(4, before.get("samples").size());
            assertFalse(before.get("adoptedAt").isNull());
            h.medium();
            var batch = h.claim();
            var target = Objects.requireNonNull(h.observer.prepare(batch, control).toCompletableFuture().join());
            assertTrue(h.longs.commit(batch, new StyleObservations.Analysis(List.of(), List.of(h.candidate(batch))),
                    target, now.plusSeconds(1002), h.observer.guard(batch, target, control))
                    .toCompletableFuture().join());
            var after = data.transaction("读取按键合并后的模式", () -> h.db.mapper.readTree(Objects.requireNonNull(
                    data.get(original.getTarget()).atomic().find(original.getKey(), EasyDataLock.NONE)).getValue()))
                    .toCompletableFuture().join();
            assertEquals(6, after.get("samples").size());
            assertEquals(before.get("adoptedAt"), after.get("adoptedAt"));
            assertEquals(before.get("adoptedUntil"), after.get("adoptedUntil"));
        }
    }

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
     * <br>2026/11/04：夹具明确保留历史装配及原业务行为，适配可选组件路径。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     * <br>2026/11/07：认知业务与宿主生命周期解耦，保留原身份、事务及异步完成边界。
     *
     *
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.6.1-l
     * @version 4.9.0-l
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
            // 2026/11/04：本夹具明确安装人格，模拟接口不能依赖默认布尔返回值。
            when(configuration.installed(any())).thenReturn(true);
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
    /**
     * 2026/11/05：切换人格、无可学习字段及证据到期产生的空筛选均推进，避免堵塞后续样本。
     * @throws Exception 当有界离线整理失败时
     */
    @Test
    void emptyObservationPrefixAdvancesAndLaterSamplesRemainLearnable() throws Exception {
        for (String reason : List.of("personality", "fields", "expired")) {
            try (var h = new Harness(); var timers = Executors.newSingleThreadScheduledExecutor()) {
                var stream = h.medium();
                Instant processingAt = reason.equals("expired") ? now.plus(Duration.ofDays(8)) : now.plusSeconds(1000);
                if (!reason.equals("expired")) {
                    h.base.set(new BasePersonality("teaneko", 2, "小茶", List.of("新表达习惯"),
                            reason.equals("fields") ? Set.of() : Set.of("speakingStyle")));
                }
                var clock = Clock.fixed(processingAt, ZoneOffset.UTC);
                var observer = new StyleObservationService(h.source, h.configuration, h.styles,
                        ignored -> h.enabled.get(), Runnable::run, clock);
                var references = mock(IAgentMemoryReferences.class, org.mockito.Mockito.CALLS_REAL_METHODS);
                when(references.verify(reference)).thenReturn(CompletableFuture.completedStage(null));
                var generator = mock(ILongTermMemoryConsolidator.class);
                when(generator.analyze(any(), any(), any())).thenAnswer(call -> {
                    LongMemories.Batch batch = call.getArgument(0);
                    StyleObservations.Target target = Objects.requireNonNull(call.getArgument(1));
                    var quotes = batch.evidence().stream().filter(item -> target.evidenceIds().contains(item.id()))
                            .limit(2).map(item -> new LongMemories.Quote(item.id(), "先讲结论，再举个小例子呀")).toList();
                    assertEquals(2, quotes.size());
                    return CompletableFuture.completedStage(new StyleObservations.Analysis(List.of(), List.of(
                            new StyleObservations.Candidate("speakingStyle", "先说结论，再举例", StyleObservations.Context.TECHNICAL,
                                    quotes, batch.segments().stream().map(MemoryLayers.Segment::id).toList()))));
                });
                try (var processor = new LayeredMemoryProcessor(h.layers,
                        (batch, control) -> { throw new AssertionError("本例只整理已有中期片段"); }, references,
                        () -> settings, ignored -> new LayeredMemoryPolicy(true, true, false), Runnable::run,
                        timers, clock, mock(ILogger.class), h.longs, generator, ignored -> false, observer)) {
                    // 2026/11/07：独立测试宿主显式启用本代后台任务。
                    processor.start().toCompletableFuture().join();
                    processor.drain().toCompletableFuture().get(5, TimeUnit.SECONDS);
                    verifyNoInteractions(generator);
                    assertTrue(h.longs.claim(stream, processingAt.plusSeconds(1)).toCompletableFuture().join().isEmpty());
                    if (!reason.equals("expired")) {
                        h.base.set(new BasePersonality("teaneko", 3, "小茶", List.of("新表达习惯"), Set.of("speakingStyle")));
                        h.medium();
                        // 2026/11/05：首轮游标已越过该分区；下一次空页复位后才重新访问，不能绕开轮转合同。
                        processor.drain().toCompletableFuture().get(5, TimeUnit.SECONDS);
                        verifyNoInteractions(generator);
                        processor.drain().toCompletableFuture().get(5, TimeUnit.SECONDS);
                        verify(generator).analyze(any(), any(), any());
                        assertEquals(1, h.styles.inspect(scope, h.snapshot(scope), processingAt)
                                .toCompletableFuture().join().size());
                        assertTrue(h.longs.claim(stream, processingAt.plusSeconds(1)).toCompletableFuture().join().isEmpty());
                    }
                }
            }
        }
    }

    /**
     * 2026/11/05：合法空筛选排队后关闭观察仍须拒绝，不以空结果绕过动态资格。
     * @throws Exception 当事务协调或有界等待失败时
     */
    @Test
    void queuedEmptyObservationStillRejectsRevocation() throws Exception {
        try (var h = new Harness(); var timers = Executors.newSingleThreadScheduledExecutor()) {
            var stream = h.medium();
            h.base.set(new BasePersonality("teaneko", 2, "小茶", List.of("不学习字段"), Set.of()));
            var references = mock(IAgentMemoryReferences.class, org.mockito.Mockito.CALLS_REAL_METHODS);
            when(references.verify(reference)).thenReturn(CompletableFuture.completedStage(null));
            var entered = new CompletableFuture<Void>();
            var release = new CompletableFuture<Void>();
            h.db.beforeTask.set(name -> {
                if (name.contains("提交长期记忆和画像证据")) {
                    entered.complete(null);
                    release.orTimeout(8, TimeUnit.SECONDS).join();
                }
            });
            try (var processor = new LayeredMemoryProcessor(h.layers,
                    (batch, control) -> { throw new AssertionError("不应生成中期摘要"); }, references,
                    () -> settings, ignored -> new LayeredMemoryPolicy(true, true, false), Runnable::run,
                    timers, Clock.fixed(now.plusSeconds(1000), ZoneOffset.UTC), mock(ILogger.class), h.longs,
                    (batch, control) -> { throw new AssertionError("空观察不应请求模型"); }, ignored -> false, h.observer)) {
                // 2026/11/07：独立测试宿主显式启用本代后台任务。
                processor.start().toCompletableFuture().join();
                var work = processor.drain().toCompletableFuture();
                try {
                    entered.get(5, TimeUnit.SECONDS);
                    h.enabled.set(false);
                } finally {
                    release.complete(null);
                }
                work.get(5, TimeUnit.SECONDS);
                assertTrue(h.longs.claim(stream, now.plusSeconds(2000)).toCompletableFuture().join().isPresent());
            }
        }
    }

    /**
     * 2026/11/05：只含风格的提交排队后撤销人格学习，事务不得保存观察或推进长期水位。
     * @throws Exception 当事务协调超时或被中断时
     */
    @Test
    void queuedStyleCommitRechecksLearningInsideTransaction() throws Exception {
        try (var h = new Harness(); var control = ExecutionControl.create()) {
            h.medium();
            var batch = h.claim();
            var target = Objects.requireNonNull(h.observer.prepare(batch, control).toCompletableFuture().join());
            var entered = new CompletableFuture<Void>();
            var release = new CompletableFuture<Void>();
            h.db.beforeTask.set(name -> {
                if (name.contains("提交长期记忆和画像证据")) {
                    entered.complete(null);
                    release.orTimeout(8, TimeUnit.SECONDS).join();
                }
            });
            var commit = h.longs.commit(batch, new StyleObservations.Analysis(List.of(), List.of(h.candidate(batch))),
                    target, now.plusSeconds(1001), h.observer.guard(batch, target, control)).toCompletableFuture();
            try {
                entered.get(5, TimeUnit.SECONDS);
                control.cancel();
            } finally {
                release.complete(null);
            }
            assertThrows(ExecutionException.class, () -> commit.get(5, TimeUnit.SECONDS));
            assertTrue(h.styles.inspect(scope, target.basis(), now).toCompletableFuture().join().isEmpty());
            assertTrue(h.longs.claim(batch.stream(), now.plusSeconds(2000)).toCompletableFuture().join().isPresent());
        }
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
    /** 2026/11/06：合法事实与风格混入一条非法原话时，完整批次不提交且准确标明 styles 位置。 */
    @Test
    void invalidStyleRollsBackValidFactsAndOtherStyles() {
        try (var h = new Harness(); var control = ExecutionControl.create()) {
            h.medium();
            var batch = h.claim();
            var target = Objects.requireNonNull(h.observer.prepare(batch, control).toCompletableFuture().join());
            var valid = h.candidate(batch);
            var fact = new LongMemories.Candidate(LongMemories.Kind.PREFERENCE,
                    batch.evidence().getFirst().message().source().userId().orElseThrow(), "preference.explanation",
                    "希望先说结论再举例", LongMemories.Mode.ADD, valid.quotes(), valid.segments(), "");
            var invalid = new StyleObservations.Candidate(valid.field(), valid.pattern(), valid.context(),
                    List.of(new LongMemories.Quote(valid.quotes().getFirst().evidenceId(), "私密改写的原话")), valid.segments());
            var failure = assertThrows(CompletionException.class, () -> h.longs.commit(batch,
                    new StyleObservations.Analysis(List.of(fact), List.of(valid, invalid)), target,
                    now.plusSeconds(1001), h.observer.guard(batch, target, control)).toCompletableFuture().join());
            var detail = assertInstanceOf(MemoryEvidenceException.class, failure.getCause());
            assertEquals(MemoryEvidenceException.Code.QUOTE_MISMATCH, detail.code());
            assertEquals(MemoryEvidenceException.Domain.STYLES, detail.domain());
            assertEquals(1, detail.index());
            assertFalse(detail.failureReason().contains("私密"));
            assertTrue(h.longs.query(reference, null, "", 20).toCompletableFuture().join().items().isEmpty());
            assertTrue(h.styles.inspect(scope, target.basis(), now).toCompletableFuture().join().isEmpty());
            assertEquals(0, h.longs.consolidationState(reference, now, () -> {}).toCompletableFuture().join().through());
            assertTrue(h.longs.commit(batch, new StyleObservations.Analysis(List.of(fact), List.of(valid)), target,
                    now.plusSeconds(1001), h.observer.guard(batch, target, control)).toCompletableFuture().join());
            assertEquals(1, h.longs.query(reference, null, "", 20).toCompletableFuture().join().items().size());
            assertEquals(1, h.styles.inspect(scope, target.basis(), now).toCompletableFuture().join().size());
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
            var refs = mock(IAgentMemoryReferences.class, org.mockito.Mockito.CALLS_REAL_METHODS);
            when(refs.verify(any())).thenReturn(CompletableFuture.completedStage(null));
            try (var processor = new LayeredMemoryProcessor(h.layers, (batch, control) -> { throw new AssertionError("不应重压缩"); }, refs, () -> settings,
                    ignored -> new LayeredMemoryPolicy(true, true, true), Runnable::run, timers, Clock.fixed(now.plusSeconds(1000), ZoneOffset.UTC), mock(ILogger.class),
                    h.longs, generator, ignored -> false, h.observer)) {
                // 2026/11/07：独立测试宿主显式启用本代后台任务。
                processor.start().toCompletableFuture().join();
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
            var gate = mock(org.zexnocs.teanekoagent.runtime.access.AgentAccessGate.class);
            var refs = mock(IAgentMemoryReferences.class, org.mockito.Mockito.CALLS_REAL_METHODS);
            var configs = mock(org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataService.class);
            var beans = context.getBeanFactory();
            // 2026/11/06：局部装配复用真实 JSON 合同，启动仍不调用模型。
            beans.registerSingleton("structuredOutput", org.zexnocs.teanekoagent.llm.framework.structured.LLMJsonTestSupport.create(models, h.db.mapper));
            beans.registerSingleton("data", h.db.data); beans.registerSingleton("namespaces", h.db.store); beans.registerSingleton("mapper", h.db.mapper);
            beans.registerSingleton("personalityStore", h.personalities); beans.registerSingleton("configuration", h.configuration); beans.registerSingleton("personalities", h.source);
            beans.registerSingleton("interactions", models); beans.registerSingleton("gate", gate); beans.registerSingleton("references", refs); beans.registerSingleton("configs", configs);
            beans.registerSingleton("manager", new org.zexnocs.teanekoagent.integration.config.AgentScopeConfigManager());
            // 2026/11/07：后台调度归属插件生命周期，独立容器提供原 Core 调度端口。
            beans.registerSingleton("coreTimers", mock(org.zexnocs.teanekocore.actuator.timer.interfaces.ITimerService.class));
            beans.registerSingleton("virtualExecutor", executor); beans.registerSingleton("taskExecutor", timers); beans.registerSingleton("logger", mock(ILogger.class));
            context.register(org.zexnocs.teanekoplugin.agent.cognition.personality.configuration.StyleObservationConfig.class, org.zexnocs.teanekoplugin.agent.cognition.memory.configuration.LayeredMemoryConfig.class,
                    org.zexnocs.teanekoplugin.agent.cognition.personality.configuration.PersonalityLearningConfig.class, org.zexnocs.teanekoplugin.agent.cognition.configuration.AgentDeliveryConfig.class);
            AgentComponentFixtures.register(context);
            context.refresh();
            assertInstanceOf(EasyDataStyleObservationStore.class, context.getBean(IStyleObservationStore.class));
            assertInstanceOf(org.zexnocs.teanekoplugin.agent.cognition.memory.adapter.EasyDataDeliveryStore.class, context.getBean(org.zexnocs.teanekoplugin.agent.cognition.memory.history.IAgentDeliveryStore.class));
            assertNotNull(context.getBean(org.zexnocs.teanekoplugin.agent.cognition.memory.history.AgentDeliveryService.class));
            assertNotNull(context.getBean(StyleObservationService.class));
            assertEquals(List.of("personality_observations"), org.zexnocs.teanekoagent.component.tool.TeaNekoAgentToolMethods.names(context.getBean(StyleObservationTools.class).getClass()));
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
