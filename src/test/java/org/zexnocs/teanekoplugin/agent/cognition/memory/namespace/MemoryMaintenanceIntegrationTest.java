package org.zexnocs.teanekoplugin.agent.cognition.memory.namespace;

// 2026/11/07：认知业务契约归入插件，纯会话数据沿用运行底座。

import org.zexnocs.teanekoplugin.agent.cognition.memory.layered.IMemoryMaintenance;
import org.zexnocs.teanekoplugin.agent.cognition.memory.layered.LayeredMemorySettings;
import org.zexnocs.teanekoplugin.agent.cognition.memory.layered.LongMemories;
import org.zexnocs.teanekoplugin.agent.cognition.memory.layered.MemoryLayers;
import org.zexnocs.teanekoplugin.agent.cognition.memory.layered.MemoryMaintenanceService;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.zexnocs.teanekoplugin.agent.cognition.memory.adapter.EasyDataLayeredMemoryStore;
import org.zexnocs.teanekoplugin.agent.cognition.memory.adapter.EasyDataLongTermMemoryStore;
import org.zexnocs.teanekoplugin.agent.cognition.memory.adapter.EasyDataMemoryLifecycle;
import org.zexnocs.teanekoplugin.agent.cognition.memory.history.HistoryContext;
import org.zexnocs.teanekoplugin.agent.cognition.memory.history.HistoryMessage;
import org.zexnocs.teanekoplugin.agent.cognition.memory.history.adapter.EasyDataHistoryStore;
import org.zexnocs.teanekoplugin.agent.cognition.memory.layered.*;
import org.zexnocs.teanekoplugin.agent.cognition.personality.AgentPersonalitySnapshot;
import org.zexnocs.teanekoagent.runtime.AgentActorContext;
import org.zexnocs.teanekoagent.runtime.AgentScope;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekocore.utils.StableDigest;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * 复用事务夹具验证物理回收、持久水位、证据精简与遗忘保护，不运行外部数据库或模型。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * <br>2026/11/07：测试宿主显式管理插件后台生命周期。
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.6.1-l
 * @version 4.9.0-l
 */
@Timeout(30)
class MemoryMaintenanceIntegrationTest {
    private final Instant now = Instant.parse("2026-11-03T00:00:00Z");
    private final AgentScope scope = new AgentScope("test", "群甲");
    private final UUID user = new UUID(0, 1);
    private final MemoryNamespaceReference reference = new MemoryNamespaceReference(scope, MemoryNamespaceId.SHARED, 0, 0);
    private final LayeredMemorySettings settings = new LayeredMemorySettings(true, 6, 2, 4, 131072, Duration.ofSeconds(30), Duration.ofDays(90));
    /**
     * 组合专用入口的真实历史、分层和长期实现。
     * <br>2026/11/04：校正 @since 为首次引入版本。
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
        final EasyDataLayeredMemoryStore layers = new EasyDataLayeredMemoryStore(db.data, db.store, db.mapper, () -> settings);
        final EasyDataHistoryStore history = new EasyDataHistoryStore(db.data, db.mapper, layers);
        final EasyDataLongTermMemoryStore longs = new EasyDataLongTermMemoryStore(db.data, db.store, db.mapper, () -> settings);
        /** 初始化真实共享空间。 */
        Harness() { db.store.create(new MemoryNamespace(MemoryNamespaceId.SHARED, 0, 0, now, null, null), 10).toCompletableFuture().join(); }
        /**
         * 原样捕获指定来源，支持模拟保留期变更后的旧消息重新导入。
         * @param target 原空间
         * @param first 首个来源编号
         * @param count 数量
         * @param at 事件起点
         * @param expires 真实保留截止
         */
        void append(MemoryNamespaceReference target, int first, int count, Instant at, Instant expires) {
            var basis = new AgentPersonalitySnapshot("人格", "teaneko", 1, "scope", 0, "基础人格", "", null);
            for (int i = first; i < first + count; i++) {
                var actor = new AgentActorContext(target.scope(), AgentActorContext.Origin.USER, Optional.of(user), "用户", "消息" + i, "群");
                var message = new HistoryMessage(actor.sourceId(), actor, HistoryMessage.Role.USER, "我平时喜欢喝茶。".repeat(40), at.plusSeconds(i), false, false);
                history.append(new HistoryContext(target, basis), message, expires).toCompletableFuture().join();
            }
        }
        /**
         * 从指定有效分区取得真实原始输入。
         * @param target 当前引用
         * @return 对应分区
         */
        MemoryLayers.Stream stream(MemoryNamespaceReference target) { return new MemoryLayers.Stream(MemoryLayers.stream(target), target); }
        /**
         * 完成一段真实短期压缩。
         * @param target 实际引用
         * @param at 当前时刻
         */
        void medium(MemoryNamespaceReference target, Instant at) {
            var batch = layers.claim(stream(target), settings, at).toCompletableFuture().join().orElseThrow();
            assertTrue(layers.commit(batch, new MemoryLayers.Digest("饮食偏好", "用户表达了饮食偏好。"), at).toCompletableFuture().join());
        }
        /**
         * 创建经过真实原话引用的偏好候选。
         * @param batch 固定输入
         * @param text 待保存正文
         * @return 合格候选
         */
        LongMemories.Candidate candidate(LongMemories.Batch batch, String text) {
            var evidence = batch.evidence().getFirst();
            return new LongMemories.Candidate(LongMemories.Kind.PREFERENCE, user, "preference.drink", text, LongMemories.Mode.ADD,
                    List.of(new LongMemories.Quote(evidence.id(), "我平时喜欢喝茶")), List.of(batch.segments().getFirst().id()), "");
        }
        /**
         * 保存本批长期事实。
         * @return 已提交条目
         */
        LongMemories.Item learn() {
            var batch = longs.claim(stream(reference), now.plusSeconds(1000)).toCompletableFuture().join().orElseThrow();
            assertTrue(longs.commit(batch, List.of(candidate(batch, "喜欢喝茶")), now.plusSeconds(1001)).toCompletableFuture().join());
            return longs.query(reference, user, "", 8).toCompletableFuture().join().items().getFirst();
        }
        /** 关闭本夹具拥有的数据库执行器。 */
        @Override public void close() { db.close(); }
    }
    /** 删除过期未压缩前缀仍保留精确序号；维护失败回滚删除和游标，旧模型不能迟到提交。 */
    @Test void retiresExpiredSourcesWithoutResettingSequenceAndRollsBackFailures() {
        try (var h = new Harness()) {
            h.append(reference, 1, 6, now, now.plus(Duration.ofDays(1)));
            var original = h.layers.claim(h.stream(reference), settings, now.plusSeconds(100)).toCompletableFuture().join().orElseThrow();
            var expired = now.plus(Duration.ofDays(2)); h.db.failingWritePrefix.set("maintenance");
            assertThrows(CompletionException.class, () -> h.layers.maintain(expired).toCompletableFuture().join());
            assertNotNull(h.db.storedValue("agent.memory.layers.v1", "short:" + h.stream(reference).id(), "00000000000000000001"));
            h.db.failingWritePrefix.set(null); assertEquals(6, h.layers.maintain(expired).toCompletableFuture().join());
            assertNull(h.db.storedValue("agent.memory.layers.v1", "short:" + h.stream(reference).id(), "00000000000000000001"));
            assertNull(h.db.storedValue("agent.memory.layers.v1", "source:" + h.stream(reference).id(), StableDigest.sha256("USER", "消息1")));
            assertFalse(h.layers.commit(original, new MemoryLayers.Digest("迟到结果", "不应提交"), expired).toCompletableFuture().join());
            h.append(reference, 7, 6, expired, expired.plus(Duration.ofDays(1)));
            var restored = new EasyDataLayeredMemoryStore(h.db.data, h.db.store, h.db.mapper, () -> settings);
            var fresh = restored.claim(h.stream(reference), settings, expired.plusSeconds(100)).toCompletableFuture().join().orElseThrow();
            assertEquals(7, fresh.messages().getFirst().sequence());
            assertTrue(restored.commit(fresh, new MemoryLayers.Digest("新消息", "继续保存真实新消息。"), expired.plusSeconds(101)).toCompletableFuture().join());
        }
    }
    /** 原话精简不删除共享事实；遗忘回执与来源摘要在原聊天回收后仍能阻止重新导入复活。 */
    @Test void prunesRawQuotesAndPreservesForgottenEvidence() {
        try (var h = new Harness()) {
            h.append(reference, 1, 6, now, now.plus(Duration.ofDays(1))); h.medium(reference, now.plusSeconds(100)); var item = h.learn();
            var expired = now.plus(Duration.ofDays(2));
            for (int i = 0; i < 12; i++) { h.longs.maintain(expired).toCompletableFuture().join(); }
            var raw = h.db.storedValue("agent.memory.long.v1", "entries:" + EasyDataMemoryLifecycle.space(reference), item.id().toString());
            var entry = h.db.mapper.readValue(Objects.requireNonNull(raw), LongMemories.Entry.class);
            assertTrue(entry.quotes().isEmpty()); assertFalse(entry.sourceKeys().isEmpty());
            var other = new MemoryNamespaceReference(new AgentScope("test", "群乙"), MemoryNamespaceId.SHARED, 0, 0);
            assertEquals(item, h.longs.query(other, user, "", 8).toCompletableFuture().join().items().getFirst());
            var operation = UUID.randomUUID();
            var forgotten = h.longs.change(reference, user, item.id(), item.revision(), null, operation, "遗忘消息", expired).toCompletableFuture().join();
            h.layers.maintain(expired).toCompletableFuture().join();
            var rebound = new MemoryNamespaceReference(scope, MemoryNamespaceId.SHARED, 1, 0);
            h.append(rebound, 1, 6, now, expired.plus(Duration.ofDays(7))); h.medium(rebound, expired.plusSeconds(100));
            var replay = h.longs.claim(h.stream(rebound), expired.plusSeconds(1000)).toCompletableFuture().join().orElseThrow();
            assertTrue(h.longs.commit(replay, List.of(h.candidate(replay, "日常偏爱茶饮")), expired.plusSeconds(1001)).toCompletableFuture().join());
            for (int i = 0; i < 16; i++) { h.longs.maintain(expired.plusSeconds(1100)).toCompletableFuture().join(); }
            assertTrue(h.longs.query(reference, user, "", 8).toCompletableFuture().join().items().isEmpty());
            assertEquals(forgotten, h.longs.change(reference, user, item.id(), item.revision(), null, operation, "遗忘消息", expired).toCompletableFuture().join());
        }
    }
    /** clear 后回收旧正文、作业、索引和回执，当前代次仍可写，旧引用不能提交迟到结果。 */
    @Test void clearsRetiredGenerationWithoutTouchingCurrentData() {
        try (var h = new Harness()) {
            h.append(reference, 1, 6, now, now.plus(Duration.ofDays(7))); h.medium(reference, now.plusSeconds(100)); var item = h.learn();
            h.longs.change(reference, user, item.id(), item.revision(), null, UUID.randomUUID(), "遗忘", now.plusSeconds(1002)).toCompletableFuture().join();
            h.append(reference, 7, 4, now, now.plus(Duration.ofDays(7))); h.medium(reference, now.plusSeconds(100));
            var pending = h.longs.claim(h.stream(reference), now.plusSeconds(2000)).toCompletableFuture().join().orElseThrow();
            h.db.store.advanceGeneration(MemoryNamespaceId.SHARED, 0, UUID.randomUUID()).toCompletableFuture().join();
            var current = new MemoryNamespaceReference(scope, MemoryNamespaceId.SHARED, 0, 1);
            h.append(current, 11, 1, now, now.plus(Duration.ofDays(7)));
            for (int i = 0; i < 40; i++) {
                h.layers.maintain(now.plusSeconds(2100)).toCompletableFuture().join();
                h.longs.maintain(now.plusSeconds(2100)).toCompletableFuture().join();
            }
            assertNull(h.db.storedValue("agent.memory.layers.v1", "streams", h.stream(reference).id()));
            assertNull(h.db.storedValue("agent.memory.layers.v1", "states", h.stream(reference).id()));
            assertNull(h.db.storedValue("agent.memory.long.v1", "jobs", h.stream(reference).id()));
            assertNull(h.db.storedValue("agent.memory.long.v1", "entries:" + EasyDataMemoryLifecycle.space(reference), item.id().toString()));
            assertEquals(1, h.layers.read(current, now, settings.retention()).toCompletableFuture().join().recent().size());
            assertFalse(h.longs.commit(pending, List.of(), now.plusSeconds(2100)).toCompletableFuture().join());
        }
    }
    /** 慢事务不重复启动，单域失败不阻断后续域，关闭不宣称撤销已开始事务。 */
    @Test void waitsForActualMaintenanceAndStopsNewWorkOnClose() {
        var pending = new CompletableFuture<Integer>(); var calls = new AtomicInteger(); var afterFailure = new AtomicInteger();
        IMemoryMaintenance first = instant -> { calls.incrementAndGet(); return pending; };
        IMemoryMaintenance failed = instant -> CompletableFuture.failedStage(new IllegalStateException("测试维护失败"));
        IMemoryMaintenance last = instant -> { afterFailure.incrementAndGet(); return CompletableFuture.completedStage(2); };
        try (var service = new MemoryMaintenanceService(List.of(first, failed, last), () -> true, Runnable::run, Clock.fixed(now, ZoneOffset.UTC), mock(ILogger.class))) {
            // 2026/11/07：独立测试宿主显式启用本代后台任务。
            service.start().toCompletableFuture().join();
            var work = service.drain().toCompletableFuture(); assertFalse(work.isDone());
            assertEquals(0, service.drain().toCompletableFuture().join()); assertEquals(1, calls.get());
            pending.complete(3); assertEquals(5, work.join()); assertEquals(1, afterFailure.get());
            service.close(); assertEquals(0, service.drain().toCompletableFuture().join()); assertEquals(1, calls.get());
        }
    }
}
