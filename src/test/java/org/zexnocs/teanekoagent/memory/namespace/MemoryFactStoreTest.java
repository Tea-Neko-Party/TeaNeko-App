package org.zexnocs.teanekoagent.memory.namespace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.zexnocs.teanekoagent.adapter.memory.EasyDataMemoryFactStore;
import org.zexnocs.teanekoagent.agent.AgentActorContext;
import org.zexnocs.teanekoagent.agent.AgentScope;
import org.zexnocs.teanekoagent.memory.fact.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 经真实事实适配器验证原子提交、来源/主语共享、幂等、损坏处理及换代提交竞争。
 * 事务替身共享 namespace 与事实两张表的测试状态，不连接真实 MySQL。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
@Timeout(15)
class MemoryFactStoreTest {
    private final Instant now = Instant.parse("2026-10-29T01:00:00Z");
    private final AgentScope scope = new AgentScope("客户端", "群");
    private final UUID subject = UUID.randomUUID();

    /**
     * 构造真实来源和显式内容，操作 ID 同时作为首次记录 ID。
     * @param operation 固定操作 ID
     * @param generation 预期数据代次
     * @param text 明确事实
     * @return 初始共享空间追加命令
     */
    private MemoryFactAppend command(UUID operation, long generation, String text) {
        var actor = new AgentActorContext(scope, AgentActorContext.Origin.USER, Optional.of(subject), "平台用户", "来源消息", "目标");
        return new MemoryFactAppend(operation, new MemoryNamespaceReference(scope, MemoryNamespaceId.SHARED, 0, generation), subject,
                new MemoryFactValue(MemoryFactValue.Kind.FACT, text, List.of("标签"), 1, MemoryFactValue.Importance.NORMAL, MemoryEventTime.unknown(), null),
                new MemoryFactSource(UUID.randomUUID(), "调用", actor));
    }

    /**
     * 为追加准备已登记默认空间，不在适配器构造期间执行隐式写入。
     * @param fixture 当前事务容器
     */
    private void register(NamespaceDatabaseFixture fixture) {
        fixture.store.create(new MemoryNamespace(MemoryNamespaceId.SHARED, 0, 0, now, null, null), 5).toCompletableFuture().join();
    }

    /**
     * 提取稳定代次异常，不根据原异常文本猜测失败类型。
     * @param stage 待检查阶段
     * @return 空间缺失或代次失效异常
     */
    private static MemoryFactGenerationException generationFailure(CompletionStage<?> stage) {
        var failure = assertThrows(CompletionException.class, () -> stage.toCompletableFuture().join());
        return assertInstanceOf(MemoryFactGenerationException.class, failure.getCause());
    }

    /**
     * 等待测试协调信号，超时以固定原因结束而不永久挂起构建。
     * @param signal 协调信号
     */
    private static void await(CountDownLatch signal) {
        try { assertTrue(signal.await(3, TimeUnit.SECONDS), "测试事务协调超时。"); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException("测试事务协调被中断。"); }
    }

    /** 验证构造没有仓库访问，缺失空间不自动创建且明确区别于无事实。 */
    @Test void missingNamespaceDoesNotInitializeOrPretendEmpty() {
        try (var fixture = new NamespaceDatabaseFixture()) {
            verifyNoInteractions(fixture.factRepository, fixture.namespaceLocks, fixture.repository);
            var command = command(UUID.randomUUID(), 0, "事实");
            assertTrue(generationFailure(fixture.facts.append(command, now)).missingNamespace());
            assertTrue(generationFailure(fixture.facts.find(MemoryNamespaceId.SHARED, 0, command.operationId())).missingNamespace());
            assertTrue(generationFailure(fixture.facts.list(MemoryNamespaceId.SHARED, 0, null, 10)).missingNamespace());
            verify(fixture.factRepository, never()).saveAndFlush(any());
        }
    }

    /** 验证记录、重复索引及回执提交前不可见，最终成功保留原时刻和来源。 */
    @Test void appendWaitsForTransactionCommit() {
        try (var fixture = new NamespaceDatabaseFixture()) {
            register(fixture); var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
            fixture.beforeCommit.set(() -> { entered.countDown(); await(release); });
            var command = command(UUID.randomUUID(), 0, "用户明确提供的事实"); var result = fixture.facts.append(command, now).toCompletableFuture();
            try { await(entered); assertFalse(result.isDone()); assertNull(fixture.storedValue("agent.fact.v1", "record:default:g:0", command.operationId().toString())); }
            finally { release.countDown(); }
            var receipt = result.join(); assertEquals(MemoryFactReceipt.Status.STORED, receipt.status());
            var record = Objects.requireNonNull(receipt.record()); assertEquals(now, record.createdAt()); assertEquals(command.source(), record.source());
        }
    }

    /** 验证同操作重放返回原 STORED 回执，改变内容或主体只冲突，不覆盖已接受事实。 */
    @Test void operationReplayReturnsOriginalAndChangedParametersConflict() {
        try (var fixture = new NamespaceDatabaseFixture()) {
            register(fixture); var command = command(UUID.randomUUID(), 0, "事实"); var original = fixture.facts.append(command, now).toCompletableFuture().join();
            assertEquals(original, fixture.facts.append(command, now.plusSeconds(60)).toCompletableFuture().join());
            var changed = new MemoryFactAppend(command.operationId(), command.reference(), UUID.randomUUID(), command.value(), command.source());
            assertEquals(MemoryFactReceipt.Status.CONFLICT, fixture.facts.append(changed, now).toCompletableFuture().join().status());
            var changedBody = command(command.operationId(), 0, "另一事实");
            assertEquals(MemoryFactReceipt.Status.CONFLICT, fixture.facts.append(changedBody, now).toCompletableFuture().join().status());
            assertEquals(original.record(), fixture.facts.find(MemoryNamespaceId.SHARED, 0, command.operationId()).toCompletableFuture().join().orElseThrow());
        }
    }

    /** 验证不同 scope 的同主语完全重复内容合并，首条来源不变且新来源保留在操作回执。 */
    @Test void exactDuplicatesShareRecordAndKeepEveryOperationSource() {
        try (var fixture = new NamespaceDatabaseFixture()) {
            register(fixture); var a = command(UUID.randomUUID(), 0, "喜欢茶"); var first = fixture.facts.append(a, now).toCompletableFuture().join();
            var actor = new AgentActorContext(new AgentScope("另一客户端", "私聊"), AgentActorContext.Origin.USER, Optional.of(subject), "另一平台用户", "另一消息", "目标");
            var b = new MemoryFactAppend(UUID.randomUUID(), new MemoryNamespaceReference(actor.scope(), MemoryNamespaceId.SHARED, 8, 0), subject, a.value(),
                    new MemoryFactSource(UUID.randomUUID(), "另一调用", actor));
            var duplicate = fixture.facts.append(b, now.plusSeconds(1)).toCompletableFuture().join();
            assertEquals(MemoryFactReceipt.Status.DUPLICATE, duplicate.status()); assertEquals(first.record(), duplicate.record());
            var stored = fixture.storedValue("agent.fact.v1", "operation:default:g:0", b.operationId().toString());
            assertNotNull(stored); assertTrue(stored.contains("另一消息")); assertEquals(duplicate, fixture.facts.append(b, now).toCompletableFuture().join());
        }
    }

    /** 验证不同主语、类型或事件时间不会被正文相同误合并。 */
    @Test void subjectKindAndEventTimeParticipateInDuplicateIdentity() {
        try (var fixture = new NamespaceDatabaseFixture()) {
            register(fixture); var a = command(UUID.randomUUID(), 0, "相同文本"); fixture.facts.append(a, now).toCompletableFuture().join();
            var b = new MemoryFactAppend(UUID.randomUUID(), a.reference(), UUID.randomUUID(), a.value(), a.source());
            assertEquals(MemoryFactReceipt.Status.STORED, fixture.facts.append(b, now).toCompletableFuture().join().status());
            var different = new MemoryFactValue(MemoryFactValue.Kind.PREFERENCE, a.value().text(), a.value().tags(), 1, a.value().importance(), MemoryEventTime.unknown(), null);
            assertEquals(MemoryFactReceipt.Status.STORED, fixture.facts.append(new MemoryFactAppend(UUID.randomUUID(), a.reference(), subject, different, a.source()), now).toCompletableFuture().join().status());
            var event = new MemoryFactValue(a.value().kind(), a.value().text(), a.value().tags(), 1, a.value().importance(), new MemoryEventTime(now, now, MemoryEventTime.Precision.EXACT, "明确时间"), null);
            assertEquals(MemoryFactReceipt.Status.STORED, fixture.facts.append(new MemoryFactAppend(UUID.randomUUID(), a.reference(), subject, event, a.source()), now).toCompletableFuture().join().status());
            assertEquals(4, fixture.facts.list(MemoryNamespaceId.SHARED, 0, null, 10).toCompletableFuture().join().records().size());
        }
    }

    /** 验证同空间跨主语共享扫描，分页游标无重复且原证据不因过期而静默丢失。 */
    @Test void boundedCursorPagesKeepAllEvidenceWithoutSubjectFiltering() {
        try (var fixture = new NamespaceDatabaseFixture()) {
            register(fixture); var expected = new TreeSet<UUID>(Comparator.comparing(UUID::toString));
            for (int i = 1; i <= 5; i++) {
                var command = command(new UUID(0, i), 0, "事实" + i); expected.add(command.operationId());
                fixture.facts.append(command, now).toCompletableFuture().join();
            }
            var ids = new ArrayList<UUID>(); UUID cursor = null;
            do {
                var page = fixture.facts.list(MemoryNamespaceId.SHARED, 0, cursor, 2).toCompletableFuture().join();
                ids.addAll(page.records().stream().map(MemoryFactRecord::id).toList()); cursor = page.nextCursor();
            } while (cursor != null);
            assertEquals(new ArrayList<>(expected), ids); verify(fixture.factRepository, never()).findAll();
            verify(fixture.factRepository, never()).findByNamespaceAndTarget(anyString(), anyString());
        }
    }

    /** 验证独立空间不共享事实或去重索引，即使主体、内容和动作 ID 相同。 */
    @Test void namespacesKeepIndependentRecordsAndDeduplication() {
        try (var fixture = new NamespaceDatabaseFixture()) {
            register(fixture); var command = command(UUID.randomUUID(), 0, "事实"); fixture.facts.append(command, now).toCompletableFuture().join();
            var creator = UUID.randomUUID(); var id = MemoryNamespaceId.created(creator);
            fixture.store.create(new MemoryNamespace(id, 0, 0, now, new MemoryNamespace.Creation(creator, command.source().actor()), null), 5).toCompletableFuture().join();
            var other = new MemoryFactAppend(command.operationId(), new MemoryNamespaceReference(scope, id, 1, 0), subject, command.value(), command.source());
            var receipt = fixture.facts.append(other, now).toCompletableFuture().join(); assertEquals(MemoryFactReceipt.Status.STORED, receipt.status());
            assertEquals(id, Objects.requireNonNull(receipt.record()).namespaceId());
        }
    }

    /** 验证清空后旧成功回执不能绕过 generation，旧读取也明确失效而不是返回旧事实。 */
    @Test void generationAdvanceRejectsOldWritesReadsAndSuccessfulReplay() {
        try (var fixture = new NamespaceDatabaseFixture()) {
            register(fixture); var command = command(UUID.randomUUID(), 0, "事实"); fixture.facts.append(command, now).toCompletableFuture().join();
            fixture.store.advanceGeneration(MemoryNamespaceId.SHARED, 0, UUID.randomUUID()).toCompletableFuture().join();
            assertFalse(generationFailure(fixture.facts.append(command, now)).missingNamespace());
            assertFalse(generationFailure(fixture.facts.find(MemoryNamespaceId.SHARED, 0, command.operationId())).missingNamespace());
            assertFalse(generationFailure(fixture.facts.list(MemoryNamespaceId.SHARED, 0, null, 10)).missingNamespace());
            assertTrue(fixture.facts.list(MemoryNamespaceId.SHARED, 1, null, 10).toCompletableFuture().join().records().isEmpty());
            assertEquals(MemoryFactReceipt.Status.STORED, fixture.facts.append(command(UUID.randomUUID(), 1, "事实"), now).toCompletableFuture().join().status());
        }
    }

    /** 验证追加在事务提交前持有 namespace 锁，换代在追加完成后才生效并隐藏旧事实。 */
    @Test void generationAdvanceWaitsForFactTransactionCommit() {
        try (var fixture = new NamespaceDatabaseFixture()) {
            register(fixture); var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var first = new AtomicBoolean(true);
            fixture.beforeCommit.set(() -> { if (first.compareAndSet(true, false)) { entered.countDown(); await(release); } });
            var command = command(UUID.randomUUID(), 0, "并发事实"); var append = fixture.facts.append(command, now).toCompletableFuture();
            CompletableFuture<MemoryNamespaceMutation> clear;
            try { await(entered); clear = fixture.store.advanceGeneration(MemoryNamespaceId.SHARED, 0, UUID.randomUUID()).toCompletableFuture(); assertFalse(clear.isDone()); }
            finally { release.countDown(); }
            assertEquals(MemoryFactReceipt.Status.STORED, append.join().status()); assertEquals(1, Objects.requireNonNull(clear.join().namespace()).generation());
            generationFailure(fixture.facts.find(MemoryNamespaceId.SHARED, 0, command.operationId()));
        }
    }

    /** 验证事务开始时的旧快照不掩盖已完成换代，锁定查询必须取得最新元数据。 */
    @Test void lockingReadRefreshesNamespaceAfterOldTransactionSnapshot() {
        try (var fixture = new NamespaceDatabaseFixture()) {
            register(fixture); var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
            fixture.beforeNamespaceLock.set(() -> { entered.countDown(); await(release); });
            var command = command(UUID.randomUUID(), 0, "旧快照事实"); var append = fixture.facts.append(command, now);
            try { await(entered); fixture.store.advanceGeneration(MemoryNamespaceId.SHARED, 0, UUID.randomUUID()).toCompletableFuture().join(); }
            finally { release.countDown(); }
            assertFalse(generationFailure(append).missingNamespace()); verify(fixture.factRepository, never()).saveAndFlush(any());
        }
    }

    /** 验证任一记录、索引或回执写入失败都回滚整次追加，修复后同动作可正常执行。 */
    @Test void failuresRollBackRecordIndexAndReceiptTogether() {
        for (var prefix : List.of("record:", "duplicate:", "operation:")) {
            try (var fixture = new NamespaceDatabaseFixture()) {
                register(fixture); var command = command(UUID.randomUUID(), 0, "原子事实"); fixture.failingWritePrefix.set(prefix);
                assertThrows(CompletionException.class, () -> fixture.facts.append(command, now).toCompletableFuture().join());
                fixture.failingWritePrefix.set(null); assertTrue(fixture.facts.list(MemoryNamespaceId.SHARED, 0, null, 10).toCompletableFuture().join().records().isEmpty());
                assertEquals(MemoryFactReceipt.Status.STORED, fixture.facts.append(command, now).toCompletableFuture().join().status());
            }
        }
    }

    /** 验证提交阶段故障和取消观察视图都不产生提前成功或部分提交。 */
    @Test void commitFailureAndCancelledViewPreserveTransactionSemantics() {
        try (var fixture = new NamespaceDatabaseFixture()) {
            register(fixture); var command = command(UUID.randomUUID(), 0, "事实"); fixture.beforeCommit.set(() -> { throw new IllegalStateException("测试整体提交失败"); });
            assertThrows(CompletionException.class, () -> fixture.facts.append(command, now).toCompletableFuture().join());
            fixture.beforeCommit.set(() -> {}); assertTrue(fixture.facts.find(MemoryNamespaceId.SHARED, 0, command.operationId()).toCompletableFuture().join().isEmpty());
            var entered = new CountDownLatch(1); var release = new CountDownLatch(1); fixture.beforeCommit.set(() -> { entered.countDown(); await(release); });
            var view = fixture.facts.append(command, now).toCompletableFuture();
            try { await(entered); assertTrue(view.cancel(false)); } finally { release.countDown(); }
            fixture.beforeCommit.set(() -> {});
            fixture.facts.append(command, now).toCompletableFuture().join();
            assertTrue(fixture.facts.find(MemoryNamespaceId.SHARED, 0, command.operationId()).toCompletableFuture().join().isPresent());
        }
    }

    /** 验证真实并发追加不同记录不丢失，同内容和同操作并发均保持单一事实。 */
    @Test void concurrentAppendsKeepDistinctRecordsAndDeduplicateRepeatedContent() {
        try (var fixture = new NamespaceDatabaseFixture()) {
            register(fixture); var distinct = new ArrayList<CompletableFuture<MemoryFactReceipt>>();
            for (int i = 0; i < 20; i++) { distinct.add(fixture.facts.append(command(UUID.randomUUID(), 0, "不同事实" + i), now).toCompletableFuture()); }
            CompletableFuture.allOf(distinct.toArray(CompletableFuture[]::new)).join();
            assertEquals(20, fixture.facts.list(MemoryNamespaceId.SHARED, 0, null, 100).toCompletableFuture().join().records().size());
            var duplicate = new ArrayList<CompletableFuture<MemoryFactReceipt>>();
            for (int i = 0; i < 20; i++) { duplicate.add(fixture.facts.append(command(UUID.randomUUID(), 0, "重复事实"), now).toCompletableFuture()); }
            CompletableFuture.allOf(duplicate.toArray(CompletableFuture[]::new)).join();
            assertEquals(1, duplicate.stream().filter(future -> future.join().status() == MemoryFactReceipt.Status.STORED).count());
            assertEquals(1, duplicate.stream().map(future -> Objects.requireNonNull(future.join().record()).id()).distinct().count());
            var same = command(UUID.randomUUID(), 0, "同一动作"); var replays = new ArrayList<CompletableFuture<MemoryFactReceipt>>();
            for (int i = 0; i < 20; i++) { replays.add(fixture.facts.append(same, now).toCompletableFuture()); }
            CompletableFuture.allOf(replays.toArray(CompletableFuture[]::new)).join(); assertEquals(1, replays.stream().map(CompletableFuture::join).distinct().count());
            assertEquals(22, fixture.facts.list(MemoryNamespaceId.SHARED, 0, null, 100).toCompletableFuture().join().records().size());
        }
    }

    /** 验证新适配器从持久化行恢复原结果，不依赖服务实例中的缓存或去重集合。 */
    @Test void recreatedAdapterReadsPersistedOperationReceipt() {
        try (var fixture = new NamespaceDatabaseFixture()) {
            register(fixture); var command = command(UUID.randomUUID(), 0, "事实"); var original = fixture.facts.append(command, now).toCompletableFuture().join();
            var recreated = new EasyDataMemoryFactStore(fixture.store, fixture.namespaceLocks, fixture.factRepository, fixture.mapper);
            assertEquals(original, recreated.append(command, now.plusSeconds(60)).toCompletableFuture().join());
        }
    }

    /** 验证损坏和未来记录版本明确失败，异常不携带敏感原正文。 */
    @Test void damagedAndFutureSchemasAreRejectedWithoutRawPayload() {
        for (var replacement : List.of("{损坏敏感正文}", "{\"schemaVersion\":9,\"record\":null}")) {
            try (var fixture = new NamespaceDatabaseFixture()) {
                register(fixture); var command = command(UUID.randomUUID(), 0, "事实"); fixture.facts.append(command, now).toCompletableFuture().join();
                fixture.rewriteRows("record:", ignored -> replacement);
                var failure = assertThrows(CompletionException.class, () -> fixture.facts.find(MemoryNamespaceId.SHARED, 0, command.operationId()).toCompletableFuture().join());
                assertFalse(failure.getCause().getMessage().contains("敏感正文")); assertNull(failure.getCause().getCause());
            }
        }
    }

    /** 验证正文定位、去重索引及操作回执损坏不会返回成功或被空值覆盖。 */
    @Test void corruptedRecordIndexAndReceiptNeverOverwriteOriginal() {
        for (var prefix : List.of("record:", "duplicate:", "operation:")) {
            try (var fixture = new NamespaceDatabaseFixture()) {
                register(fixture); var command = command(UUID.randomUUID(), 0, "事实"); fixture.facts.append(command, now).toCompletableFuture().join();
                fixture.rewriteRows(prefix, json -> json.replace("\"schemaVersion\":1", "\"schemaVersion\":2"));
                var action = prefix.equals("operation:") ? command : new MemoryFactAppend(UUID.randomUUID(), command.reference(), subject, command.value(), command.source());
                assertThrows(CompletionException.class, () -> fixture.facts.append(action, now).toCompletableFuture().join());
            }
        }
    }

    /** 验证旧 memory/profile 行与新事实域共存，未显式映射时不自动导入或改写。 */
    @Test void legacyRowsRemainUntouchedAndAreNotImplicitlyShared() {
        try (var fixture = new NamespaceDatabaseFixture()) {
            register(fixture); var target = "scope:旧群:agent:旧人格"; var key = "profile:旧用户"; var body = "[{\"content\":\"旧事实\"}]";
            fixture.seedFactRow("memory", target, key, body);
            var command = command(UUID.randomUUID(), 0, "新事实"); fixture.facts.append(command, now).toCompletableFuture().join();
            assertEquals(body, fixture.storedValue("memory", target, key));
            assertEquals(1, fixture.facts.list(MemoryNamespaceId.SHARED, 0, null, 10).toCompletableFuture().join().records().size());
        }
    }

    /** 验证非法分页、负代次在提交任何数据库工作前拒绝。 */
    @Test void invalidQueriesNeverReachDatabaseTasks() {
        try (var fixture = new NamespaceDatabaseFixture()) {
            assertThrows(IllegalArgumentException.class, () -> fixture.facts.list(MemoryNamespaceId.SHARED, 0, null, 0));
            assertThrows(IllegalArgumentException.class, () -> fixture.facts.list(MemoryNamespaceId.SHARED, 0, null, 101));
            assertThrows(IllegalArgumentException.class, () -> fixture.facts.find(MemoryNamespaceId.SHARED, -1, UUID.randomUUID()));
            verifyNoInteractions(fixture.database, fixture.factRepository, fixture.namespaceLocks);
        }
    }
}
