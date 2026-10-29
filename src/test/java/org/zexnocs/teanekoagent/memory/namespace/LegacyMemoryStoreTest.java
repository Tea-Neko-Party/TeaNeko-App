package org.zexnocs.teanekoagent.memory.namespace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.zexnocs.teanekoagent.adapter.memory.EasyDataLegacyMemoryStore;
import org.zexnocs.teanekoagent.agent.AgentActorContext;
import org.zexnocs.teanekoagent.agent.AgentScope;
import org.zexnocs.teanekoagent.memory.fact.*;
import org.zexnocs.teanekoagent.memory.fact.legacy.LegacyMemoryImport;
import org.zexnocs.teanekoagent.memory.fact.legacy.LegacyMemoryImportResult;
import org.zexnocs.teanekoagent.memory.fact.legacy.LegacyMemoryLocation;
import org.zexnocs.teanekoagent.memory.fact.legacy.LegacyMemorySnapshot;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.zexnocs.teanekoagent.memory.fact.legacy.LegacyMemoryImportResult.Status;

/**
 * 用真实导入适配器和两表事务替身验证完整映射、幂等、回滚及来源/代次竞争。
 * 不连接 MySQL；锁、Version 和事务完成合同另需部署实机验收。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
@Timeout(20)
class LegacyMemoryStoreTest {
    private static final String ORIGINAL = "[{\"id\":\"原记录\",\"content\":\"喜欢清茶\",\"source\":\"原消息\",\"subjectId\":\"平台用户\",\"locked\":true,\"createdAt\":\"2020-01-01T00:00:00Z\",\"updatedAt\":\"2020-01-02T00:00:00Z\"}]";
    private final Instant now = Instant.parse("2026-10-29T01:00:00Z");
    private final LegacyMemoryLocation location = new LegacyMemoryLocation("旧群", "旧角色", "平台用户");
    private final AgentScope scope = new AgentScope("客户端", "管理私聊");
    private final UUID subject = UUID.randomUUID();
    private final AgentActorContext actor = new AgentActorContext(scope, AgentActorContext.Origin.USER,
            Optional.of(UUID.randomUUID()), "导入者平台 ID", "管理请求", "私聊目标");
    private final MemoryFactSource source = new MemoryFactSource(UUID.randomUUID(), "明确导入", actor);
    private final MemoryNamespaceReference reference = new MemoryNamespaceReference(scope, MemoryNamespaceId.SHARED, 0, 0);

    /**
     * 显式登记目标和旧行，没有通过普通事实读取触发导入。
     * @param fixture 事务容器
     * @param json 原行 JSON
     * @return 转换预览
     */
    private LegacyMemorySnapshot prepare(NamespaceDatabaseFixture fixture, String json) {
        fixture.store.create(new MemoryNamespace(MemoryNamespaceId.SHARED, 0, 0, now, null, null), 5).toCompletableFuture().join();
        fixture.seedFactRow("memory", location.target(), location.key(), json); return preview(fixture);
    }

    /**
     * 用同一个固定估算读取当前源行。
     * @param fixture 事务容器
     * @return 当前原行快照
     */
    private LegacyMemorySnapshot preview(NamespaceDatabaseFixture fixture) { return fixture.legacy.read(location, now).toCompletableFuture().join().orElseThrow(); }

    /**
     * 明确旧主体到真实 UUID 的映射，不解析旧字符串外观。
     * @param snapshot 已读取预览
     * @return 新的导入命令
     */
    private LegacyMemoryImport command(LegacyMemorySnapshot snapshot) { return mapped(snapshot, UUID.randomUUID(), reference, Map.of("平台用户", subject)); }

    /**
     * 为冲突、重放和目标隔离场景指定完整固定命令。
     * @param snapshot 原行预览
     * @param operation 操作 ID
     * @param destination 目标引用
     * @param subjects 真实主体映射
     * @return 不可变命令
     */
    private LegacyMemoryImport mapped(LegacyMemorySnapshot snapshot, UUID operation, MemoryNamespaceReference destination, Map<String, UUID> subjects) {
        return new LegacyMemoryImport(operation, destination, location, snapshot.version(), snapshot.digest(), subjects, source, now);
    }

    /**
     * 创建第二个实际登记空间，不复用默认空间 ID 或隐式提升权限。
     * @param fixture 事务容器
     * @return 第二空间固定引用
     */
    private MemoryNamespaceReference secondNamespace(NamespaceDatabaseFixture fixture) {
        var creation = new MemoryNamespace.Creation(UUID.randomUUID(), actor); var id = MemoryNamespaceId.created(creation.requestId());
        fixture.store.create(new MemoryNamespace(id, 0, 0, now, creation, null), 5).toCompletableFuture().join();
        return new MemoryNamespaceReference(scope, id, 0, 0);
    }

    /**
     * 有界等待事务协调，保证失败也不会永久占用构建。
     * @param signal 测试信号
     */
    private static void await(CountDownLatch signal) {
        try { assertTrue(signal.await(5, TimeUnit.SECONDS), "导入事务协调超时。"); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException("导入事务协调被中断。"); }
    }

    /**
     * 提取异步失败，不依赖可能包含正文的异常文本。
     * @param stage 待完成操作
     * @return 原失败
     */
    private static Throwable failure(CompletionStage<?> stage) { return assertThrows(CompletionException.class, () -> stage.toCompletableFuture().join()).getCause(); }

    /** 验证构造无 I/O，缺失行与空数组不同，读取和普通事实查询均不自动导入。 */
    @Test void constructionAndPreviewNeverImportImplicitly() {
        try (var fixture = new NamespaceDatabaseFixture()) {
            verifyNoInteractions(fixture.repository, fixture.factRepository, fixture.namespaceLocks);
            assertTrue(fixture.legacy.read(location, now).toCompletableFuture().join().isEmpty());
            prepare(fixture, "[]"); assertTrue(preview(fixture).entries().isEmpty());
            fixture.seedFactRow("memory", location.target(), location.key(), ORIGINAL); assertEquals(1, preview(fixture).entries().size());
            assertTrue(fixture.facts.list(MemoryNamespaceId.SHARED, 0, null, 100).toCompletableFuture().join().records().isEmpty());
            verify(fixture.factRepository, never()).lockLegacyProfile(anyString(), anyString());
        }
    }

    /** 验证首次导入保留旧来源、时间和锁定，记录可信操作者，原行完全不变。 */
    @Test void importKeepsOriginalEvidenceAndExplicitIdentity() {
        try (var fixture = new NamespaceDatabaseFixture()) {
            var snapshot = prepare(fixture, ORIGINAL); var command = command(snapshot); var result = fixture.legacy.importSnapshot(command).toCompletableFuture().join();
            assertEquals(Status.IMPORTED, result.status()); var record = result.records().getFirst();
            assertEquals(subject, record.subjectId()); assertEquals(source, record.source()); assertNotEquals(actor.userId().orElseThrow(), record.subjectId());
            assertEquals(Instant.parse("2020-01-01T00:00:00Z"), record.createdAt()); assertEquals(Instant.parse("2020-01-02T00:00:00Z"), record.updatedAt());
            assertTrue(record.locked()); assertEquals(snapshot.entries().getFirst().provenance(), record.provenance());
            assertEquals(record, fixture.facts.find(MemoryNamespaceId.SHARED, 0, record.id()).toCompletableFuture().join().orElseThrow());
            assertEquals(ORIGINAL, fixture.storedValue("memory", location.target(), location.key()));
            assertEquals(result, fixture.legacy.importSnapshot(command).toCompletableFuture().join());
            var rebuilt = new EasyDataLegacyMemoryStore(fixture.store, fixture.facts, fixture.factRepository, fixture.mapper);
            assertEquals(result, rebuilt.importSnapshot(command).toCompletableFuture().join());
        }
    }

    /** 验证没有主体映射时不猜 UUID；后续原命令可在提供明确映射后导入。 */
    @Test void missingSubjectMappingFailsWithoutPartialWrites() {
        try (var fixture = new NamespaceDatabaseFixture()) {
            var snapshot = prepare(fixture, ORIGINAL); var unmapped = mapped(snapshot, UUID.randomUUID(), reference, Map.of());
            assertInstanceOf(IllegalArgumentException.class, failure(fixture.legacy.importSnapshot(unmapped)));
            assertTrue(fixture.facts.list(MemoryNamespaceId.SHARED, 0, null, 100).toCompletableFuture().join().records().isEmpty());
            assertNull(fixture.storedValue("agent.fact.legacy.v1", "request", unmapped.operationId().toString()));
            assertEquals(Status.IMPORTED, fixture.legacy.importSnapshot(command(snapshot)).toCompletableFuture().join().status());
        }
    }

    /** 验证旧主体即使外观为 UUID，仍只使用明确映射而不是原字符串推断。 */
    @Test void uuidLookingOldSubjectIsStillOpaque() {
        try (var fixture = new NamespaceDatabaseFixture()) {
            var legacyId = UUID.randomUUID().toString(); var snapshot = prepare(fixture, ORIGINAL.replace("平台用户", legacyId));
            assertInstanceOf(IllegalArgumentException.class, failure(fixture.legacy.importSnapshot(command(snapshot))));
            var result = fixture.legacy.importSnapshot(mapped(snapshot, UUID.randomUUID(), reference, Map.of(legacyId, subject))).toCompletableFuture().join();
            assertEquals(subject, result.records().getFirst().subjectId()); assertNotEquals(UUID.fromString(legacyId), result.records().getFirst().subjectId());
        }
    }

    /** 验证同请求改变目标、主体、来源或估算时刻只冲突，不覆盖历史成功。 */
    @Test void committedRequestCannotChangeMappingOrImporter() {
        try (var fixture = new NamespaceDatabaseFixture()) {
            var snapshot = prepare(fixture, ORIGINAL); var command = command(snapshot); var original = fixture.legacy.importSnapshot(command).toCompletableFuture().join();
            var other = secondNamespace(fixture);
            assertEquals(Status.CONFLICT, fixture.legacy.importSnapshot(mapped(snapshot, command.operationId(), other, command.subjects())).toCompletableFuture().join().status());
            assertEquals(Status.CONFLICT, fixture.legacy.importSnapshot(mapped(snapshot, command.operationId(), reference, Map.of("平台用户", UUID.randomUUID()))).toCompletableFuture().join().status());
            var changed = new LegacyMemoryImport(command.operationId(), reference, location, snapshot.version(), snapshot.digest(), command.subjects(),
                    new MemoryFactSource(UUID.randomUUID(), "另一个导入动作", actor), now);
            assertEquals(Status.CONFLICT, fixture.legacy.importSnapshot(changed).toCompletableFuture().join().status());
            var timeChanged = new LegacyMemoryImport(command.operationId(), reference, location, snapshot.version(), snapshot.digest(), command.subjects(), source, now.plusSeconds(1));
            assertEquals(Status.CONFLICT, fixture.legacy.importSnapshot(timeChanged).toCompletableFuture().join().status());
            assertEquals(original, fixture.legacy.importSnapshot(command).toCompletableFuture().join());
        }
    }

    /** 验证新请求复用稳定旧记录映射，不将同一旧证据转投新目标或主体。 */
    @Test void newRequestReusesMappingAndRejectsRetargeting() {
        try (var fixture = new NamespaceDatabaseFixture()) {
            var snapshot = prepare(fixture, ORIGINAL); var original = fixture.legacy.importSnapshot(command(snapshot)).toCompletableFuture().join();
            assertEquals(original, fixture.legacy.importSnapshot(command(snapshot)).toCompletableFuture().join());
            assertEquals(Status.CONFLICT, fixture.legacy.importSnapshot(mapped(snapshot, UUID.randomUUID(), secondNamespace(fixture), Map.of("平台用户", subject))).toCompletableFuture().join().status());
            assertEquals(Status.CONFLICT, fixture.legacy.importSnapshot(mapped(snapshot, UUID.randomUUID(), reference, Map.of("平台用户", UUID.randomUUID()))).toCompletableFuture().join().status());
            assertEquals(1, fixture.facts.list(MemoryNamespaceId.SHARED, 0, null, 100).toCompletableFuture().join().records().size());
        }
    }

    /** 验证相同内容的不同旧 ID 保留独立历史；普通新增依旧使用原内容索引且不改锁。 */
    @Test void distinctLegacyIdsKeepEvidenceWhileOrdinaryAppendDeduplicates() {
        try (var fixture = new NamespaceDatabaseFixture()) {
            var json = ORIGINAL.substring(0, ORIGINAL.length() - 1) + "," + ORIGINAL.substring(1).replace("原记录", "另一个原 ID");
            var result = fixture.legacy.importSnapshot(command(prepare(fixture, json))).toCompletableFuture().join();
            assertEquals(2, result.records().size()); assertNotEquals(result.records().getFirst().id(), result.records().getLast().id());
            var imported = result.records().getFirst();
            var append = new MemoryFactAppend(UUID.randomUUID(), reference, subject, imported.value(), source);
            var duplicate = fixture.facts.append(append, now).toCompletableFuture().join();
            assertEquals(MemoryFactReceipt.Status.DUPLICATE, duplicate.status()); assertEquals(imported, duplicate.record());
            assertEquals(2, fixture.facts.list(MemoryNamespaceId.SHARED, 0, null, 100).toCompletableFuture().join().records().size());
        }
    }

    /** 验证旧端新增记录可增量导入，既有来源条目和首次导入操作者不被重写。 */
    @Test void newLegacyEntriesCanBeImportedWithoutDuplicatingPriorOnes() {
        try (var fixture = new NamespaceDatabaseFixture()) {
            var first = fixture.legacy.importSnapshot(command(prepare(fixture, ORIGINAL))).toCompletableFuture().join();
            var expanded = ORIGINAL.substring(0, ORIGINAL.length() - 1) + ",{\"id\":\"新增\",\"content\":\"喜欢看书\"}]";
            fixture.updateLegacy(location, expanded).toCompletableFuture().join();
            var result = fixture.legacy.importSnapshot(command(preview(fixture))).toCompletableFuture().join();
            assertEquals(Status.IMPORTED, result.status()); assertEquals(2, result.records().size()); assertEquals(first.records().getFirst(), result.records().getFirst());
            assertEquals(expanded, fixture.storedValue("memory", location.target(), location.key()));
        }
    }

    /** 验证源行中已导入条目改变时整批拒绝，即使新条目先出现也无部分导入。 */
    @Test void changedOldEvidenceConflictsBeforeAnyNewWrite() {
        try (var fixture = new NamespaceDatabaseFixture()) {
            fixture.legacy.importSnapshot(command(prepare(fixture, ORIGINAL))).toCompletableFuture().join();
            var changed = "[{\"id\":\"新增\",\"content\":\"尚未导入\"}," + ORIGINAL.substring(1).replace("喜欢清茶", "原事实已修改");
            fixture.updateLegacy(location, changed).toCompletableFuture().join();
            assertEquals(Status.CONFLICT, fixture.legacy.importSnapshot(command(preview(fixture))).toCompletableFuture().join().status());
            assertEquals(1, fixture.facts.list(MemoryNamespaceId.SHARED, 0, null, 100).toCompletableFuture().join().records().size());
        }
    }

    /** 验证旧行缺失、原文变化或只有 Version 变化都明确拒绝过期预览。 */
    @Test void missingOrChangedSourceCannotImportStalePreview() {
        try (var fixture = new NamespaceDatabaseFixture()) {
            var snapshot = prepare(fixture, ORIGINAL); var command = command(snapshot);
            var missingLocation = new LegacyMemoryLocation("不存在", "旧角色", "平台用户");
            var missing = new LegacyMemoryImport(UUID.randomUUID(), reference, missingLocation, 0, snapshot.digest(), command.subjects(), source, now);
            assertEquals(Status.SOURCE_MISSING, fixture.legacy.importSnapshot(missing).toCompletableFuture().join().status());
            fixture.updateLegacy(location, ORIGINAL).toCompletableFuture().join();
            assertEquals(Status.SOURCE_CHANGED, fixture.legacy.importSnapshot(command).toCompletableFuture().join().status());
            assertEquals(snapshot.digest(), preview(fixture).digest()); assertEquals(1, preview(fixture).version());
            var wrongDigest = new LegacyMemoryImport(UUID.randomUUID(), reference, location, 1, "0".repeat(64), command.subjects(), source, now);
            assertEquals(Status.SOURCE_CHANGED, fixture.legacy.importSnapshot(wrongDigest).toCompletableFuture().join().status());
            fixture.updateLegacy(location, "{\"future\":\"格式\"}").toCompletableFuture().join();
            assertEquals(Status.SOURCE_CHANGED, fixture.legacy.importSnapshot(command).toCompletableFuture().join().status());
            var invalid = assertInstanceOf(IllegalArgumentException.class, failure(fixture.legacy.read(location, now))); assertNull(invalid.getCause());
            assertTrue(fixture.facts.list(MemoryNamespaceId.SHARED, 0, null, 100).toCompletableFuture().join().records().isEmpty());
        }
    }

    /** 验证旧成功重放和换新操作 ID 都不能在 clear 后把旧证据重新导入。 */
    @Test void clearBlocksOldReplayAndReimportIntoNewGeneration() {
        try (var fixture = new NamespaceDatabaseFixture()) {
            var snapshot = prepare(fixture, ORIGINAL); var command = command(snapshot); fixture.legacy.importSnapshot(command).toCompletableFuture().join();
            fixture.store.advanceGeneration(MemoryNamespaceId.SHARED, 0, UUID.randomUUID()).toCompletableFuture().join();
            assertInstanceOf(MemoryFactGenerationException.class, failure(fixture.legacy.importSnapshot(command)));
            var next = new MemoryNamespaceReference(scope, MemoryNamespaceId.SHARED, 0, 1);
            assertEquals(Status.CONFLICT, fixture.legacy.importSnapshot(mapped(snapshot, UUID.randomUUID(), next, command.subjects())).toCompletableFuture().join().status());
            assertTrue(fixture.facts.list(MemoryNamespaceId.SHARED, 1, null, 100).toCompletableFuture().join().records().isEmpty());
        }
    }

    /** 验证每个写入阶段失败均回滚记录、索引、映射和请求回执，随后重试正常。 */
    @Test void everyWriteFailureRollsBackEntireImport() {
        for (var target : List.of("record:", "duplicate:", "mapping", "request")) {
            try (var fixture = new NamespaceDatabaseFixture()) {
                var command = command(prepare(fixture, ORIGINAL)); fixture.failingWritePrefix.set(target);
                assertInstanceOf(IllegalStateException.class, failure(fixture.legacy.importSnapshot(command))); fixture.failingWritePrefix.set(null);
                assertTrue(fixture.facts.list(MemoryNamespaceId.SHARED, 0, null, 100).toCompletableFuture().join().records().isEmpty());
                assertNull(fixture.storedValue("agent.fact.legacy.v1", "request", command.operationId().toString()));
                assertEquals(Status.IMPORTED, fixture.legacy.importSnapshot(command).toCompletableFuture().join().status());
                assertEquals(ORIGINAL, fixture.storedValue("memory", location.target(), location.key()));
            }
        }
    }

    /** 验证提交前不交付成功，整体提交失败无写入，取消观察不撤销已经提交的事务。 */
    @Test void completionWaitsForCommitAndObserverCancellationDoesNotRollback() {
        try (var fixture = new NamespaceDatabaseFixture()) {
            var command = command(prepare(fixture, ORIGINAL)); fixture.beforeCommit.set(() -> { throw new IllegalStateException("测试提交失败。"); });
            assertInstanceOf(IllegalStateException.class, failure(fixture.legacy.importSnapshot(command))); fixture.beforeCommit.set(() -> {});
            assertTrue(fixture.facts.list(MemoryNamespaceId.SHARED, 0, null, 100).toCompletableFuture().join().records().isEmpty());
            var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var first = new AtomicBoolean(true);
            fixture.beforeCommit.set(() -> { if (first.compareAndSet(true, false)) { entered.countDown(); await(release); } });
            var view = fixture.legacy.importSnapshot(command).toCompletableFuture();
            try {
                await(entered); assertFalse(view.isDone()); assertNull(fixture.storedValue("agent.fact.legacy.v1", "request", command.operationId().toString()));
                assertTrue(view.cancel(false));
            } finally { release.countDown(); }
            assertEquals(Status.IMPORTED, fixture.legacy.importSnapshot(command).toCompletableFuture().join().status());
            assertEquals(1, fixture.facts.list(MemoryNamespaceId.SHARED, 0, null, 100).toCompletableFuture().join().records().size());
        }
    }

    /** 验证真实线程并发重复导入及不同操作导入只产生一条事实，原记录 ID 稳定。 */
    @Test void concurrentImportsShareStableMappingAndCommittedReceipts() {
        try (var fixture = new NamespaceDatabaseFixture()) {
            var snapshot = prepare(fixture, ORIGINAL); var command = command(snapshot); var stages = new ArrayList<CompletableFuture<LegacyMemoryImportResult>>();
            for (int i = 0; i < 20; i++) { stages.add(fixture.legacy.importSnapshot(command).toCompletableFuture()); }
            var original = stages.getFirst().join(); for (var stage : stages) { assertEquals(original, stage.join()); }
            stages.clear();
            for (int i = 0; i < 20; i++) { stages.add(fixture.legacy.importSnapshot(command(snapshot)).toCompletableFuture()); }
            for (var stage : stages) { assertEquals(original, stage.join()); }
            assertEquals(1, fixture.facts.list(MemoryNamespaceId.SHARED, 0, null, 100).toCompletableFuture().join().records().size());
        }
    }

    /** 验证不同目标同时导入同一来源，只有一个全局映射可提交，另一个冲突且无孤立事实。 */
    @Test void concurrentDestinationsCannotDuplicateOneOldRecord() {
        try (var fixture = new NamespaceDatabaseFixture()) {
            var snapshot = prepare(fixture, ORIGINAL); var other = secondNamespace(fixture);
            var ready = new CountDownLatch(2); var firstTwo = new AtomicInteger(2);
            fixture.beforeLegacyLock.set(() -> { if (firstTwo.getAndDecrement() > 0) { ready.countDown(); await(ready); } });
            var first = fixture.legacy.importSnapshot(command(snapshot)); var second = fixture.legacy.importSnapshot(mapped(snapshot, UUID.randomUUID(), other, Map.of("平台用户", subject)));
            var statuses = List.of(first.toCompletableFuture().join().status(), second.toCompletableFuture().join().status());
            assertEquals(1, statuses.stream().filter(status -> status == Status.IMPORTED).count()); assertTrue(statuses.contains(Status.CONFLICT)); assertTrue(fixture.retries.get() > 0);
            assertEquals(1, fixture.facts.list(MemoryNamespaceId.SHARED, 0, null, 100).toCompletableFuture().join().records().size()
                    + fixture.facts.list(other.namespaceId(), 0, null, 100).toCompletableFuture().join().records().size());
        }
    }

    /** 验证不同源行和空间同时重用一个请求 ID，由全局唯一键仲裁且完整回滚败方。 */
    @Test void concurrentSourcesCannotReuseOneRequestIdAcrossDestinations() {
        try (var fixture = new NamespaceDatabaseFixture()) {
            var snapshot = prepare(fixture, ORIGINAL); var other = secondNamespace(fixture);
            var otherLocation = new LegacyMemoryLocation("另一旧群", "旧角色", "平台用户");
            fixture.seedFactRow("memory", otherLocation.target(), otherLocation.key(), ORIGINAL);
            var command = command(snapshot); var collision = new LegacyMemoryImport(command.operationId(), other, otherLocation, 0, snapshot.digest(), command.subjects(), source, now);
            var ready = new CountDownLatch(2); var firstTwo = new AtomicInteger(2);
            fixture.beforeCommit.set(() -> { if (firstTwo.getAndDecrement() > 0) { ready.countDown(); await(ready); } });
            var first = fixture.legacy.importSnapshot(command); var second = fixture.legacy.importSnapshot(collision);
            var statuses = List.of(first.toCompletableFuture().join().status(), second.toCompletableFuture().join().status());
            assertEquals(1, statuses.stream().filter(status -> status == Status.IMPORTED).count()); assertTrue(statuses.contains(Status.CONFLICT)); assertTrue(fixture.retries.get() > 0);
            assertEquals(1, fixture.facts.list(MemoryNamespaceId.SHARED, 0, null, 100).toCompletableFuture().join().records().size()
                    + fixture.facts.list(other.namespaceId(), 0, null, 100).toCompletableFuture().join().records().size());
        }
    }

    /** 验证数据库排序规则返回近似定位时明确拒绝，不能把另一物理行冒充原资料。 */
    @Test void physicalLocatorMismatchDoesNotBecomeSharedEvidence() {
        try (var fixture = new NamespaceDatabaseFixture()) {
            var snapshot = prepare(fixture, ORIGINAL); var other = new LegacyMemoryLocation("其他旧群", "旧角色", "平台用户");
            doAnswer(call -> fixture.factRepository.findByNamespaceAndTargetAndKey("memory", location.target(), location.key()))
                    .when(fixture.factRepository).findByNamespaceAndTargetAndKey("memory", other.target(), other.key());
            assertInstanceOf(IllegalStateException.class, failure(fixture.legacy.read(other, now)));
            doAnswer(call -> fixture.factRepository.lockLegacyProfile(location.target(), location.key()))
                    .when(fixture.factRepository).lockLegacyProfile(other.target(), other.key());
            var mismatch = new LegacyMemoryImport(UUID.randomUUID(), reference, other, snapshot.version(), snapshot.digest(), Map.of("平台用户", subject), source, now);
            assertInstanceOf(IllegalStateException.class, failure(fixture.legacy.importSnapshot(mismatch)));
            assertTrue(fixture.facts.list(MemoryNamespaceId.SHARED, 0, null, 100).toCompletableFuture().join().records().isEmpty());
        }
    }

    /** 验证映射引用丢失后不会重新插入事实，即使旧原文和目标 generation 仍匹配。 */
    @Test void lostMappedFactIsNotSilentlyResurrected() {
        try (var fixture = new NamespaceDatabaseFixture()) {
            var snapshot = prepare(fixture, ORIGINAL); var result = fixture.legacy.importSnapshot(command(snapshot)).toCompletableFuture().join();
            fixture.removeFactRow("agent.fact.v1", "record:default:g:0", result.records().getFirst().id().toString());
            assertInstanceOf(IllegalStateException.class, failure(fixture.legacy.importSnapshot(command(snapshot))));
            assertTrue(fixture.facts.list(MemoryNamespaceId.SHARED, 0, null, 100).toCompletableFuture().join().records().isEmpty());
        }
    }

    /** 验证旧写入先提交后，导入锁读取最新源行而不是事务开始时的旧快照。 */
    @Test void sourceLockRefreshesOldTransactionSnapshot() {
        try (var fixture = new NamespaceDatabaseFixture()) {
            var command = command(prepare(fixture, ORIGINAL)); var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
            fixture.beforeLegacyLock.set(() -> { entered.countDown(); await(release); }); var importing = fixture.legacy.importSnapshot(command);
            try { await(entered); fixture.updateLegacy(location, ORIGINAL.replace("清茶", "红茶")).toCompletableFuture().join(); }
            finally { release.countDown(); }
            assertEquals(Status.SOURCE_CHANGED, importing.toCompletableFuture().join().status());
            assertTrue(fixture.facts.list(MemoryNamespaceId.SHARED, 0, null, 100).toCompletableFuture().join().records().isEmpty());
        }
    }

    /** 验证导入持有真实旧行锁至提交，旧列表更新不能穿透导入的快照确认。 */
    @Test void legacyWriterWaitsUntilImportCommits() {
        try (var fixture = new NamespaceDatabaseFixture()) {
            var command = command(prepare(fixture, ORIGINAL)); var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var first = new AtomicBoolean(true);
            var writerReady = new CountDownLatch(1);
            fixture.beforeCommit.set(() -> { if (first.compareAndSet(true, false)) { entered.countDown(); await(release); } else { writerReady.countDown(); } });
            var importing = fixture.legacy.importSnapshot(command); CompletableFuture<Void> writer;
            try { await(entered); writer = fixture.updateLegacy(location, ORIGINAL.replace("清茶", "红茶")).toCompletableFuture(); await(writerReady); assertFalse(writer.isDone()); }
            finally { release.countDown(); }
            assertEquals(Status.IMPORTED, importing.toCompletableFuture().join().status()); writer.join();
            assertTrue(Objects.requireNonNull(fixture.storedValue("memory", location.target(), location.key())).contains("红茶"));
        }
    }

    /** 验证 clear 先完成时锁定读取拒绝旧代次；导入先提交时 clear 随后隐藏原事实。 */
    @Test void generationAndImportCommitHaveOneOrderedBoundary() {
        try (var fixture = new NamespaceDatabaseFixture()) {
            var command = command(prepare(fixture, ORIGINAL)); var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
            fixture.beforeNamespaceLock.set(() -> { entered.countDown(); await(release); }); var importing = fixture.legacy.importSnapshot(command);
            try { await(entered); fixture.store.advanceGeneration(MemoryNamespaceId.SHARED, 0, UUID.randomUUID()).toCompletableFuture().join(); }
            finally { release.countDown(); }
            assertInstanceOf(MemoryFactGenerationException.class, failure(importing)); verify(fixture.factRepository, never()).lockLegacyProfile(anyString(), anyString());
        }
        try (var fixture = new NamespaceDatabaseFixture()) {
            var command = command(prepare(fixture, ORIGINAL)); var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var first = new AtomicBoolean(true);
            fixture.beforeCommit.set(() -> { if (first.compareAndSet(true, false)) { entered.countDown(); await(release); } });
            var importing = fixture.legacy.importSnapshot(command); CompletableFuture<MemoryNamespaceMutation> clear;
            try { await(entered); clear = fixture.store.advanceGeneration(MemoryNamespaceId.SHARED, 0, UUID.randomUUID()).toCompletableFuture(); assertFalse(clear.isDone()); }
            finally { release.countDown(); }
            assertEquals(Status.IMPORTED, importing.toCompletableFuture().join().status()); clear.join();
            assertTrue(fixture.facts.list(MemoryNamespaceId.SHARED, 1, null, 100).toCompletableFuture().join().records().isEmpty());
        }
    }

    /** 验证空行和已知非事实类型可提交明确回执，不被强行转换成个人事实。 */
    @Test void emptyAndNonFactRowsProduceExplicitUnmodifiedResults() {
        for (var json : List.of("[]", "[{\"id\":\"人格\",\"type\":\"PERSONALITY_DELTA\"}]")) {
            try (var fixture = new NamespaceDatabaseFixture()) {
                var command = command(prepare(fixture, json)); var result = fixture.legacy.importSnapshot(command).toCompletableFuture().join();
                assertEquals(Status.IMPORTED, result.status()); assertTrue(result.records().isEmpty()); assertEquals(json.equals("[]") ? 0 : 1, result.skipped().size());
                assertEquals(json, fixture.storedValue("memory", location.target(), location.key())); assertEquals(result, fixture.legacy.importSnapshot(command).toCompletableFuture().join());
            }
        }
    }

    /** 验证损坏、未来账本及映射引用缺失不会变成空状态或悄悄重新插入事实。 */
    @Test void damagedLedgersAndMissingFactsFailWithoutRestoration() {
        for (var target : List.of("mapping", "request", "record:")) {
            try (var fixture = new NamespaceDatabaseFixture()) {
                var snapshot = prepare(fixture, ORIGINAL); var command = command(snapshot); fixture.legacy.importSnapshot(command).toCompletableFuture().join();
                fixture.rewriteRows(target, json -> json.replace("\"schemaVersion\":1", "\"schemaVersion\":99"));
                var attempt = target.equals("request") ? command : command(snapshot);
                var failure = assertInstanceOf(IllegalStateException.class, failure(fixture.legacy.importSnapshot(attempt))); assertNull(failure.getCause());
            }
        }
    }

    /** 验证目标引用与导入来源 scope 不一致被拒绝，旧新格式记录仍可兼容读取。 */
    @Test void commandScopeAndOldFactSchemaAreChecked() {
        var otherScope = new AgentScope("客户端", "其他群"); var wrong = new MemoryNamespaceReference(otherScope, MemoryNamespaceId.SHARED, 0, 0);
        assertThrows(IllegalArgumentException.class, () -> new LegacyMemoryImport(UUID.randomUUID(), wrong, location, 0, "0".repeat(64), Map.of("平台用户", subject), source, now));
        try (var fixture = new NamespaceDatabaseFixture()) {
            prepare(fixture, ORIGINAL); var value = new MemoryFactValue(MemoryFactValue.Kind.FACT, "新格式早期事实", List.of(), 1,
                    MemoryFactValue.Importance.NORMAL, MemoryEventTime.unknown(), null);
            var appended = fixture.facts.append(new MemoryFactAppend(UUID.randomUUID(), reference, subject, value, source), now).toCompletableFuture().join().record();
            fixture.rewriteRows("record:", json -> json.replace(",\"provenance\":null", ""));
            assertEquals(appended, fixture.facts.find(MemoryNamespaceId.SHARED, 0, Objects.requireNonNull(appended).id()).toCompletableFuture().join().orElseThrow());
        }
    }
}
