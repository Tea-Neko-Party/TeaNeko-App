package org.zexnocs.teanekoagent.memory.namespace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.zexnocs.teanekoagent.adapter.memory.EasyDataMemoryRecallStore;
import org.zexnocs.teanekoagent.agent.AgentActorContext;
import org.zexnocs.teanekoagent.agent.AgentScope;
import org.zexnocs.teanekoagent.memory.fact.*;
import org.zexnocs.teanekoagent.memory.recall.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 经真实存储适配器验证有界多页快照、代次竞争、原证据共享及业务层组合。
 * 事务替身的独占锁比实际共享锁更严格，不代替 MySQL 隔离及读并发性能验收。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
@Timeout(20)
class MemoryRecallStoreTest {
    private final Instant now = Instant.parse("2026-10-29T01:00:00Z");
    private final AgentScope scope = new AgentScope("客户端", "群");
    private final UUID subject = UUID.randomUUID();
    private final MemoryFactSource source = new MemoryFactSource(UUID.randomUUID(), "写入", new AgentActorContext(scope,
            AgentActorContext.Origin.USER, Optional.of(subject), "平台用户", "原消息", "目标"));
    private final MemoryNamespaceReference reference = new MemoryNamespaceReference(scope, MemoryNamespaceId.SHARED, 0, 0);

    /**
     * 构造可复现稳定键的原事实。
     * @param index 记录标识后缀
     * @param text 正文
     * @return 已验证原记录
     */
    private MemoryFactRecord record(int index, String text) {
        return new MemoryFactRecord(new UUID(0, index), MemoryNamespaceId.SHARED, 0, 0, subject,
                new MemoryFactValue(MemoryFactValue.Kind.FACT, text, List.of(), 1, MemoryFactValue.Importance.NORMAL, MemoryEventTime.unknown(), null),
                source, now, now, false);
    }
    /**
     * 登记空间并注入既有 schema 的事实行，不通过待测试检索接口准备数据。
     * @param fixture 事务容器
     * @param count 原记录数
     */
    private void prepare(NamespaceDatabaseFixture fixture, int count) {
        fixture.store.create(new MemoryNamespace(MemoryNamespaceId.SHARED, 0, 0, now, null, null), 5).toCompletableFuture().join();
        for (int i = 1; i <= count; i++) {
            var record = record(i, "事实" + i);
            fixture.seedFactRow("agent.fact.v1", "record:default:g:0", record.id().toString(), fixture.mapper.writeValueAsString(Map.of("schemaVersion", 1, "record", record)));
        }
    }
    /**
     * 将真实事务来源接到独立 Recall，不启动模型或配置装配。
     * @param fixture 事务容器
     * @return 可信检索服务
     */
    private MemoryRecallService service(NamespaceDatabaseFixture fixture) {
        return new MemoryRecallService(fixture.recall, MemoryRecallSettings::defaults, Clock.fixed(now, ZoneOffset.UTC), Runnable::run,
                items -> fixture.mapper.writeValueAsString(items).length());
    }
    /**
     * 提取异步根失败，调用方可据类型区分无命中与来源不可用。
     * @param stage 查询阶段
     * @return 失败
     */
    private static Throwable failure(CompletionStage<?> stage) { return assertThrows(CompletionException.class, () -> stage.toCompletableFuture().join()).getCause(); }
    /**
     * 等待有限测试信号，失败时不永久阻塞构建。
     * @param signal 协调信号
     */
    private static void await(CountDownLatch signal) {
        try { assertTrue(signal.await(5, TimeUnit.SECONDS), "记忆快照测试协调超时。"); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException("记忆快照测试协调被中断。"); }
    }

    /** 验证构造没有 I/O，空间缺失明确异常且不读取事实或自动登记 default。 */
    @Test void missingNamespaceIsNotACompleteEmptyResult() {
        try (var fixture = new NamespaceDatabaseFixture()) {
            verifyNoInteractions(fixture.repository, fixture.factRepository, fixture.namespaceLocks);
            var missing = assertInstanceOf(MemoryFactGenerationException.class, failure(fixture.recall.scan(MemoryNamespaceId.SHARED, 0, 1000)));
            assertTrue(missing.missingNamespace()); verifyNoInteractions(fixture.factRepository);
            assertTrue(assertInstanceOf(MemoryFactGenerationException.class, failure(service(fixture).recall(
                    new MemoryRecallRequest(reference, MemoryRecallQuery.all(), MemoryRecallRequest.Use.TOOL, null)))).missingNamespace());
        }
    }

    /** 验证大于一页的前缀有界读取，额外行只判断后续存在，恰好读完时报告 complete。 */
    @Test void boundedPagingReportsExactCoverage() {
        try (var fixture = new NamespaceDatabaseFixture()) {
            prepare(fixture, 205); var partial = fixture.recall.scan(MemoryNamespaceId.SHARED, 0, 201).toCompletableFuture().join();
            assertEquals(201, partial.evidence().size()); assertFalse(partial.complete()); assertEquals(new UUID(0, 201), partial.evidence().getLast().id());
            var pages = ArgumentCaptor.forClass(Pageable.class);
            verify(fixture.factRepository, times(3)).findByNamespaceAndTargetAndKeyGreaterThanOrderByKeyAsc(eq("agent.fact.v1"), eq("record:default:g:0"), anyString(), pages.capture());
            assertTrue(pages.getAllValues().stream().allMatch(page -> page.getPageSize() <= 101));
            var complete = fixture.recall.scan(MemoryNamespaceId.SHARED, 0, 205).toCompletableFuture().join(); assertTrue(complete.complete()); assertEquals(205, complete.evidence().size());
            verify(fixture.factRepository, never()).saveAndFlush(any());
        }
    }

    /** 验证过滤发生在候选扫描之后，第二页之后的命中可找到；较小扫描预算则不伪装无结果。 */
    @Test void queryCanFindLaterPagesAndDoesNotOverstateSmallScan() {
        try (var fixture = new NamespaceDatabaseFixture()) {
            prepare(fixture, 205); var query = new MemoryRecallQuery(null, null, Set.of(), Set.of(), List.of("事实205"), null, null, true);
            var service = service(fixture);
            var full = service.recall(new MemoryRecallRequest(reference, query, MemoryRecallRequest.Use.TOOL, null)).toCompletableFuture().join();
            assertEquals(205, full.scanned()); assertEquals(1, full.matched()); assertEquals("事实205", full.evidence().getFirst().value().text()); assertTrue(full.limits().isEmpty());
            var partial = service.recall(new MemoryRecallRequest(reference, query, MemoryRecallRequest.Use.TOOL, new MemoryRecallLimits(8, 16000, 100))).toCompletableFuture().join();
            assertTrue(partial.evidence().isEmpty()); assertFalse(partial.definitivelyEmpty()); assertEquals(Set.of(MemoryRecallResult.Limit.SCAN), partial.limits());
        }
    }

    /** 验证 namespace/generation 独立定位，清空后新代次为空且旧引用明确失效。 */
    @Test void namespacesAndGenerationsRemainSeparate() {
        try (var fixture = new NamespaceDatabaseFixture()) {
            prepare(fixture, 1); var creation = new MemoryNamespace.Creation(UUID.randomUUID(), source.actor()); var id = MemoryNamespaceId.created(creation.requestId());
            fixture.store.create(new MemoryNamespace(id, 0, 0, now, creation, null), 5).toCompletableFuture().join();
            fixture.facts.append(new MemoryFactAppend(UUID.randomUUID(), new MemoryNamespaceReference(scope, id, 0, 0), subject, record(2, "另一空间").value(), source), now).toCompletableFuture().join();
            assertEquals("事实1", fixture.recall.scan(MemoryNamespaceId.SHARED, 0, 100).toCompletableFuture().join().evidence().getFirst().value().text());
            assertEquals("另一空间", fixture.recall.scan(id, 0, 100).toCompletableFuture().join().evidence().getFirst().value().text());
            fixture.store.advanceGeneration(MemoryNamespaceId.SHARED, 0, UUID.randomUUID()).toCompletableFuture().join();
            assertFalse(assertInstanceOf(MemoryFactGenerationException.class, failure(fixture.recall.scan(MemoryNamespaceId.SHARED, 0, 100))).missingNamespace());
            var next = fixture.recall.scan(MemoryNamespaceId.SHARED, 1, 100).toCompletableFuture().join(); assertTrue(next.complete()); assertTrue(next.evidence().isEmpty());
        }
    }

    /** 验证扫描预算之外的损坏不被误报为检查完成；读取到损坏行后不能交付前面部分证据。 */
    @Test void lateDamagedRecordFailsWholeQueryWithoutPartialSuccess() {
        try (var fixture = new NamespaceDatabaseFixture()) {
            prepare(fixture, 201); fixture.seedFactRow("agent.fact.v1", "record:default:g:0", new UUID(0, 201).toString(), "{秘密损坏正文}");
            assertFalse(fixture.recall.scan(MemoryNamespaceId.SHARED, 0, 100).toCompletableFuture().join().complete());
            var error = assertInstanceOf(MemoryRecallException.class, failure(service(fixture).recall(new MemoryRecallRequest(reference, MemoryRecallQuery.all(), MemoryRecallRequest.Use.TOOL, null))));
            assertEquals(MemoryRecallException.Reason.UNAVAILABLE, error.reason()); assertNull(error.getCause()); assertFalse(error.getMessage().contains("秘密"));
        }
    }

    /** 验证非法预算在提交任何数据库任务前拒绝。 */
    @Test void invalidArgumentsNeverReachStorage() {
        try (var fixture = new NamespaceDatabaseFixture()) {
            assertThrows(IllegalArgumentException.class, () -> fixture.recall.scan(MemoryNamespaceId.SHARED, -1, 100));
            assertThrows(IllegalArgumentException.class, () -> fixture.recall.scan(MemoryNamespaceId.SHARED, 0, 0));
            assertThrows(IllegalArgumentException.class, () -> fixture.recall.scan(MemoryNamespaceId.SHARED, 0, 10001));
            verifyNoInteractions(fixture.database, fixture.factRepository, fixture.namespaceLocks);
        }
    }

    /** 验证旧数据和其他物理域不隐式加入共享检索，也不被查询改写。 */
    @Test void legacyRowsAndOtherDomainsAreNotImplicitlyRecalled() {
        try (var fixture = new NamespaceDatabaseFixture()) {
            prepare(fixture, 1); var original = "[{\"content\":\"旧私有资料\"}]";
            fixture.seedFactRow("memory", "scope:旧群:agent:角色", "profile:用户", original);
            fixture.seedFactRow("其他域", "record:default:g:0", new UUID(0, 2).toString(), "其他资料");
            var result = fixture.recall.scan(MemoryNamespaceId.SHARED, 0, 100).toCompletableFuture().join(); assertEquals(1, result.evidence().size());
            assertEquals(original, fixture.storedValue("memory", "scope:旧群:agent:角色", "profile:用户"));
            verify(fixture.factRepository, never()).lockLegacyProfile(anyString(), anyString());
        }
    }

    /** 验证事务提交前不交付成功，取消观察不持有永久快照锁，完成后新写入仍可进行。 */
    @Test void completionWaitsForCommitAndCancelledViewDoesNotOwnLock() {
        try (var fixture = new NamespaceDatabaseFixture()) {
            prepare(fixture, 1); var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var first = new AtomicBoolean(true);
            fixture.beforeCommit.set(() -> { if (first.compareAndSet(true, false)) { entered.countDown(); await(release); } });
            var result = fixture.recall.scan(MemoryNamespaceId.SHARED, 0, 100).toCompletableFuture();
            try { await(entered); assertFalse(result.isDone()); assertTrue(result.cancel(false)); } finally { release.countDown(); }
            var command = new MemoryFactAppend(UUID.randomUUID(), reference, subject, record(2, "随后追加").value(), source);
            assertEquals(MemoryFactReceipt.Status.STORED, fixture.facts.append(command, now).toCompletableFuture().join().status());
            assertEquals(2, fixture.recall.scan(MemoryNamespaceId.SHARED, 0, 100).toCompletableFuture().join().evidence().size());
        }
    }

    /** 验证 clear 在获取共享锁之前已完成时，事务旧快照不会让旧代次进入查询。 */
    @Test void snapshotLockRefreshesGenerationBeforeReadingFacts() {
        try (var fixture = new NamespaceDatabaseFixture()) {
            prepare(fixture, 1); var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
            fixture.beforeNamespaceLock.set(() -> { entered.countDown(); await(release); }); var result = fixture.recall.scan(MemoryNamespaceId.SHARED, 0, 100);
            try { await(entered); fixture.store.advanceGeneration(MemoryNamespaceId.SHARED, 0, UUID.randomUUID()).toCompletableFuture().join(); }
            finally { release.countDown(); }
            assertFalse(assertInstanceOf(MemoryFactGenerationException.class, failure(result)).missingNamespace());
            verify(fixture.factRepository, never()).findByNamespaceAndTargetAndKeyGreaterThanOrderByKeyAsc(anyString(), anyString(), anyString(), any());
        }
    }

    /** 验证等待共享锁前已追加的较小 ID 也在首次一致性读取中出现，不沿用事务开始的旧候选。 */
    @Test void firstPageSeesAppendCommittedBeforeSnapshotLock() {
        try (var fixture = new NamespaceDatabaseFixture()) {
            prepare(fixture, 1); var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var first = new AtomicBoolean(true);
            fixture.beforeNamespaceLock.set(() -> { if (first.compareAndSet(true, false)) { entered.countDown(); await(release); } });
            var result = fixture.recall.scan(MemoryNamespaceId.SHARED, 0, 100);
            try { await(entered); fixture.facts.append(new MemoryFactAppend(new UUID(0, 0), reference, subject, record(0, "更小 ID").value(), source), now).toCompletableFuture().join(); }
            finally { release.countDown(); }
            var snapshot = result.toCompletableFuture().join(); assertEquals(2, snapshot.evidence().size()); assertEquals(new UUID(0, 0), snapshot.evidence().getFirst().id());
        }
    }

    /** 验证 clear 在多页读取期间等待同一快照锁，读取先完成后新代次不再返回旧证据。 */
    @Test void clearCannotCrossMultiPageSnapshotBoundary() {
        try (var fixture = new NamespaceDatabaseFixture()) {
            prepare(fixture, 205); var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var clearReady = new CountDownLatch(1); var first = new AtomicBoolean(true);
            fixture.afterFactPage.set(() -> { if (first.compareAndSet(true, false)) { entered.countDown(); await(release); } });
            fixture.beforeCommit.set(clearReady::countDown); var result = fixture.recall.scan(MemoryNamespaceId.SHARED, 0, 1000);
            CompletableFuture<MemoryNamespaceMutation> clear;
            try { await(entered); clear = fixture.store.advanceGeneration(MemoryNamespaceId.SHARED, 0, UUID.randomUUID()).toCompletableFuture(); await(clearReady); assertFalse(clear.isDone()); }
            finally { release.countDown(); }
            var snapshot = result.toCompletableFuture().join(); assertTrue(snapshot.complete()); assertEquals(205, snapshot.evidence().size()); clear.join();
            assertTrue(fixture.recall.scan(MemoryNamespaceId.SHARED, 1, 1000).toCompletableFuture().join().evidence().isEmpty());
        }
    }

    /** 验证新追加的较小 ID 不能穿透多页边界产生漏读却宣称完整，下一查询可见新事实。 */
    @Test void appendWaitsForSnapshotAndAppearsInNextQuery() {
        try (var fixture = new NamespaceDatabaseFixture()) {
            prepare(fixture, 205); var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var writerReady = new CountDownLatch(1);
            var firstPage = new AtomicBoolean(true); var lockCalls = new AtomicInteger();
            fixture.afterFactPage.set(() -> { if (firstPage.compareAndSet(true, false)) { entered.countDown(); await(release); } });
            fixture.beforeNamespaceLock.set(() -> { if (lockCalls.incrementAndGet() > 1) { writerReady.countDown(); } });
            var result = fixture.recall.scan(MemoryNamespaceId.SHARED, 0, 1000); CompletableFuture<MemoryFactReceipt> append;
            try {
                await(entered); append = fixture.facts.append(new MemoryFactAppend(new UUID(0, 0), reference, subject, record(0, "追加小 ID").value(), source), now).toCompletableFuture();
                await(writerReady); assertFalse(append.isDone());
            } finally { release.countDown(); }
            var original = result.toCompletableFuture().join(); assertTrue(original.complete()); assertEquals(205, original.evidence().size()); append.join();
            var next = new EasyDataMemoryRecallStore(fixture.store, fixture.namespaceLocks, fixture.facts).scan(MemoryNamespaceId.SHARED, 0, 1000).toCompletableFuture().join();
            assertEquals(206, next.evidence().size()); assertEquals(new UUID(0, 0), next.evidence().getFirst().id());
        }
    }
}
