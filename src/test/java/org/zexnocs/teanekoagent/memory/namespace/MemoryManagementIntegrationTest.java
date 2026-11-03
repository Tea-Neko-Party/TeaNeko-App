package org.zexnocs.teanekoagent.memory.namespace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.mock.env.MockEnvironment;
import org.zexnocs.teanekoagent.adapter.memory.EasyDataMemoryManagementStore;
import org.zexnocs.teanekoagent.agent.AgentActorContext;
import org.zexnocs.teanekoagent.agent.AgentScope;
import org.zexnocs.teanekoagent.memory.admin.MemoryManagementCommand;
import org.zexnocs.teanekoagent.memory.admin.MemoryManagementResult;
import org.zexnocs.teanekoagent.memory.fact.*;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 复用原事务夹具覆盖编辑索引、独立克隆、清空幂等及取消/失败回滚，不连接真实数据库。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.5.0-l
 * @version 4.5.0-l
 */
@Timeout(20)
class MemoryManagementIntegrationTest {
    private final Instant now = Instant.parse("2026-11-01T01:00:00Z");
    private final AgentScope scope = new AgentScope("客户端", "群");
    private final UUID subject = UUID.randomUUID();
    private final AgentActorContext actor = new AgentActorContext(scope, AgentActorContext.Origin.USER,
            Optional.of(subject), "用户", "管理消息", "目标");

    /**
     * 使用实际存储及默认有界策略，不创建第二套测试数据库。
     * @param db 原事务夹具
     * @return 管理适配器
     */
    private EasyDataMemoryManagementStore store(NamespaceDatabaseFixture db) {
        return new EasyDataMemoryManagementStore(db.store, db.facts, db.data, () -> 5, db.mapper, new MockEnvironment());
    }

    /**
     * 准备真实主体及明确来源的追加命令。
     * @param text 事实正文
     * @return 共享空间的首次追加
     */
    private MemoryFactAppend append(String text) {
        return new MemoryFactAppend(UUID.randomUUID(), new MemoryNamespaceReference(scope, MemoryNamespaceId.SHARED, 0, 0), subject,
                new MemoryFactValue(MemoryFactValue.Kind.FACT, text, List.of(), 1, MemoryFactValue.Importance.NORMAL, MemoryEventTime.unknown(), null),
                new MemoryFactSource(UUID.randomUUID(), "新增", actor));
    }

    /**
     * 固定管理来源和原始空间预期版本。
     * @param action 动作
     * @return 不含编辑参数的管理命令
     */
    private MemoryManagementCommand command(MemoryManagementCommand.Action action) {
        return new MemoryManagementCommand(UUID.randomUUID(), actor, MemoryNamespaceId.SHARED, 0, 0, action, null, null, null);
    }

    /** 验证编辑保留证据、正确重建去重索引，克隆原子复制并在重建服务后复用原回执。 */
    @Test void editAndCloneKeepEvidenceAndDeduplication() {
        try (var db = new NamespaceDatabaseFixture()) {
            db.store.create(new MemoryNamespace(MemoryNamespaceId.SHARED, 0, 0, now, null, null), 5).toCompletableFuture().join();
            var original = append("原事实"); db.facts.append(original, now).toCompletableFuture().join(); var management = store(db);
            var edit = new MemoryManagementCommand(UUID.randomUUID(), actor, MemoryNamespaceId.SHARED, 0, 0,
                    MemoryManagementCommand.Action.EDIT, original.operationId(), "修正事实", true);
            var result = management.apply(edit, now.plusSeconds(1), () -> {}).toCompletableFuture().join();
            var record = Objects.requireNonNull(result.record()); assertTrue(record.locked()); assertEquals(1, record.revision());
            assertEquals(original.source(), record.source()); assertEquals(now, record.createdAt());
            assertEquals(result, store(db).apply(edit, now.plusSeconds(2), () -> {}).toCompletableFuture().join());
            assertEquals(MemoryFactReceipt.Status.DUPLICATE, db.facts.append(append("修正事实"), now).toCompletableFuture().join().status());
            assertEquals(MemoryFactReceipt.Status.STORED, db.facts.append(append("原事实"), now).toCompletableFuture().join().status());
            var stale = new MemoryManagementCommand(UUID.randomUUID(), actor, MemoryNamespaceId.SHARED, 0, 0,
                    MemoryManagementCommand.Action.EDIT, original.operationId(), "迟到修改", null);
            assertEquals(MemoryManagementResult.Status.CONFLICT, management.apply(stale, now, () -> {}).toCompletableFuture().join().status());
            var clone = command(MemoryManagementCommand.Action.CLONE); var cloned = management.apply(clone, now, () -> {}).toCompletableFuture().join();
            assertEquals(2, cloned.records()); assertEquals(cloned, store(db).apply(clone, now.plusSeconds(5), () -> {}).toCompletableFuture().join());
            var copied = management.page(Objects.requireNonNull(cloned.namespace()).id(), 0, null, 10).toCompletableFuture().join().records();
            assertEquals(2, copied.size()); assertTrue(copied.stream().allMatch(value -> value.provenance() != null && value.subjectId().equals(subject)));
            assertTrue(copied.stream().anyMatch(value -> value.locked() && value.value().text().equals("修正事实")));
        }
    }

    /** 验证提交前授权失效整体回滚，clear 重放不再次换代且回收不删除管理防重账本。 */
    @Test void clearRejectsOldWritesAndPreservesDurableReceipt() {
        try (var db = new NamespaceDatabaseFixture()) {
            db.store.create(new MemoryNamespace(MemoryNamespaceId.SHARED, 0, 0, now, null, null), 5).toCompletableFuture().join();
            var original = append("旧事实"); db.facts.append(original, now).toCompletableFuture().join();
            var management = store(db); var clear = command(MemoryManagementCommand.Action.CLEAR); var checks = new AtomicInteger();
            assertThrows(CompletionException.class, () -> management.apply(clear, now, () -> {
                if (checks.incrementAndGet() == 2) { throw new IllegalStateException("授权已关闭。"); }
            }).toCompletableFuture().join());
            assertEquals(0, db.store.find(MemoryNamespaceId.SHARED).toCompletableFuture().join().orElseThrow().generation());
            var first = management.apply(clear, now, () -> {});
            var repeated = management.apply(clear, now.plusSeconds(1), () -> {});
            var cleared = first.toCompletableFuture().join(); assertEquals(cleared, repeated.toCompletableFuture().join());
            assertEquals(1, Objects.requireNonNull(cleared.namespace()).generation());
            assertThrows(CompletionException.class, () -> db.facts.append(original, now).toCompletableFuture().join());
            assertThrows(CompletionException.class, () -> management.page(MemoryNamespaceId.SHARED, 0, null, 10).toCompletableFuture().join());
            assertEquals(3, management.purge(MemoryNamespaceId.SHARED, 0, 100, () -> {}).toCompletableFuture().join());
            assertEquals(0, management.purge(MemoryNamespaceId.SHARED, 0, 100, () -> {}).toCompletableFuture().join());
            assertEquals(cleared, store(db).apply(clear, now.plusSeconds(20), () -> {}).toCompletableFuture().join());
            assertNotNull(db.storedValue("agent.namespace.v1", "management-operation", clear.operationId().toString()));
            assertThrows(CompletionException.class, () -> management.purge(MemoryNamespaceId.SHARED, 1, 100, () -> {}).toCompletableFuture().join());
        }
    }

    /** 验证克隆规模限制及中途写入失败均不留下空目标、部分事实或创建配额。 */
    @Test void cloneLimitAndWriteFailureDoNotLeavePartialTargets() {
        try (var db = new NamespaceDatabaseFixture()) {
            db.store.create(new MemoryNamespace(MemoryNamespaceId.SHARED, 0, 0, now, null, null), 5).toCompletableFuture().join();
            db.facts.append(append("事实甲"), now).toCompletableFuture().join(); db.facts.append(append("事实乙"), now).toCompletableFuture().join();
            var limited = new EasyDataMemoryManagementStore(db.store, db.facts, db.data, () -> 5, db.mapper,
                    new MockEnvironment().withProperty("tea-neko.agent.memory.management.max-records", "1"));
            var clone = command(MemoryManagementCommand.Action.CLONE);
            assertEquals(MemoryManagementResult.Status.TOO_LARGE, limited.apply(clone, now, () -> {}).toCompletableFuture().join().status());
            var target = MemoryNamespaceId.created(clone.operationId()); db.failingWritePrefix.set("record:" + target.value());
            assertThrows(CompletionException.class, () -> store(db).apply(clone, now, () -> {}).toCompletableFuture().join());
            assertTrue(db.store.find(target).toCompletableFuture().join().isEmpty());
            assertTrue(db.store.listCreated(subject, 0, 10).toCompletableFuture().join().isEmpty());
            assertEquals(2, db.facts.list(MemoryNamespaceId.SHARED, 0, null, 10).toCompletableFuture().join().records().size());
        }
    }
}
