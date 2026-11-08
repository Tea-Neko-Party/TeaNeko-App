package org.zexnocs.teanekoplugin.agent.cognition.memory.namespace;

// 2026/11/07：认知业务契约归入插件，纯会话数据沿用运行底座。

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.zexnocs.teanekoagent.database.easydata.EasyDataMaintenancePage;
import org.zexnocs.teanekoagent.runtime.AgentActorContext;
import org.zexnocs.teanekoagent.runtime.AgentScope;
import org.zexnocs.teanekocore.utils.StableDigest;
import org.zexnocs.teanekoplugin.agent.automation.schedule.AgentScheduledTask;
import org.zexnocs.teanekoplugin.agent.automation.schedule.EasyDataScheduleStore;
import org.zexnocs.teanekoplugin.agent.automation.schedule.ScheduleManagement;
import org.zexnocs.teanekoplugin.agent.cognition.memory.adapter.EasyDataDeliveryStore;
import org.zexnocs.teanekoplugin.agent.cognition.memory.history.IAgentDeliveryStore;
import org.zexnocs.teanekoplugin.agent.cognition.memory.layered.MemoryLayers;
import org.zexnocs.teanekoplugin.agent.expression.sticker.EasyDataStickerCatalog;
import org.zexnocs.teanekoplugin.agent.expression.sticker.Stickers;
import org.zexnocs.teanekoplugin.agent.expression.sticker.file_config.AgentStickerFileConfig;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 用真实 EasyData 事务入口验证跨表迁移、失败回滚及清理后的业务防重。
 * <br>2026/11/06：仅操作离线仓库夹具，不访问用户数据库。
 * <br>2026/11/07：认知业务与宿主生命周期解耦，保留原身份、事务及异步完成边界。
 *
 *
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.5-l
 * @version 4.9.0-l
 */
@Timeout(20)
class AgentTransientStorageTest {
    private static final Instant NOW = Instant.parse("2026-11-06T00:00:00Z");
    private static final AgentScope SCOPE = new AgentScope("test", "chat");
    private static final AgentActorContext ACTOR = new AgentActorContext(SCOPE, AgentActorContext.Origin.USER,
            Optional.of(UUID.randomUUID()), "user", "message", "chat");
    private static final MemoryNamespaceReference REFERENCE = new MemoryNamespaceReference(SCOPE, MemoryNamespaceId.SHARED, 0, 0);

    /** 迁移失败保留旧记录；清空短期表后，已确认和未知段都不会再次取得发送权。 */
    @Test
    void deliveryMigrationRollsBackAndTemporaryCleanupCannotResend() {
        try (var db = new NamespaceDatabaseFixture()) {
            db.store.create(new MemoryNamespace(MemoryNamespaceId.SHARED, 0, 0, NOW, null, null), 10)
                    .toCompletableFuture().join();
            var store = new EasyDataDeliveryStore(db.data, db.store, db.mapper);
            var sent = part("已发送正文");
            var unknown = part("未确认正文");
            String target = "receipts:" + MemoryLayers.stream(REFERENCE);
            String original = db.mapper.writeValueAsString(Map.of("part", sent, "state", "ACKNOWLEDGED", "updatedAt", NOW));
            db.seedFactRow("agent.delivery.v1", target, sent.requestId() + ".00", original);
            db.seedFactRow("agent.delivery.v1", target, unknown.requestId() + ".00",
                    db.mapper.writeValueAsString(Map.of("part", unknown, "state", "UNKNOWN", "updatedAt", NOW)));
            var requests = Set.of(sent.requestId(), unknown.requestId());
            db.failingWritePrefix.set("receipts:");
            assertThrows(CompletionException.class, () -> store.read(REFERENCE, requests, NOW).toCompletableFuture().join());
            assertEquals(original, db.storedValue("agent.delivery.v1", target, sent.requestId() + ".00"));
            assertNull(db.storedValue("agent.delivery.v1", "claims:" + target, sent.requestId() + ".00"));
            assertNull(db.temporaryValue("agent.delivery.v1", target, sent.requestId() + ".00"));

            db.failingWritePrefix.set(null);
            var views = store.read(REFERENCE, requests, NOW).toCompletableFuture().join();
            assertEquals("已发送正文", views.get(sent.requestId()).text());
            assertEquals("UNKNOWN", views.get(unknown.requestId()).state());
            assertNull(db.storedValue("agent.delivery.v1", target, sent.requestId() + ".00"));
            assertFalse(db.storedValue("agent.delivery.v1", "claims:" + target, sent.requestId() + ".00").contains("已发送正文"));
            assertTrue(db.temporaryValue("agent.delivery.v1", target, sent.requestId() + ".00").contains("已发送正文"));
            db.clearTemporary();
            var restarted = new EasyDataDeliveryStore(db.data, db.store, db.mapper);
            assertTrue(restarted.read(REFERENCE, requests, NOW).toCompletableFuture().join().isEmpty());
            assertFalse(restarted.begin(sent).toCompletableFuture().join());
            assertThrows(CompletionException.class, () -> restarted.begin(unknown).toCompletableFuture().join());

            var pending = part("在途正文");
            assertTrue(restarted.begin(pending).toCompletableFuture().join());
            db.clearTemporary();
            restarted.finish(pending, true, NOW.plusSeconds(1)).toCompletableFuture().join();
            assertFalse(restarted.begin(pending).toCompletableFuture().join());
            restarted.maintain(NOW.plusSeconds(7200)).toCompletableFuture().join();
            assertTrue(restarted.read(REFERENCE, Set.of(pending.requestId()), NOW.plusSeconds(7200))
                    .toCompletableFuture().join().isEmpty());
            assertFalse(restarted.begin(pending).toCompletableFuture().join());
        }
    }

    /** 管理回执迁移和取消同事务；临时回执清除后版本仍拒绝旧取消，任务不复活。 */
    @Test
    void scheduleReceiptMigrationPreservesCancelledTask() {
        try (var db = new NamespaceDatabaseFixture()) {
            var store = new EasyDataScheduleStore(db.data, db.mapper);
            var spec = new AgentScheduledTask.Spec("测试提醒", ACTOR, "remind", Map.of("text", "提醒"),
                    NOW.plusSeconds(60), "Asia/Shanghai", 0);
            var task = new AgentScheduledTask(UUID.randomUUID(), spec, spec.firstFire(), AgentScheduledTask.Status.ACTIVE, 1, "等待");
            store.create(task).toCompletableFuture().join();
            var query = new ScheduleManagement.Query("CANCEL", "", task.id(), 1, UUID.randomUUID());
            var receipt = store.manage(SCOPE, "admin", query, () -> {}).toCompletableFuture().join();
            String original = db.temporaryValue("agent.schedule.v1", "management", "operations");
            assertNotNull(original);
            db.clearTemporary();
            db.seedFactRow("agent.schedule.v1", "management", "operations", original);
            var lookup = new ScheduleManagement.Query("OPERATION", "", task.id(), 1, query.operationId());
            db.failingWritePrefix.set("management");
            assertThrows(CompletionException.class, () -> store.manage(SCOPE, "admin", lookup, () -> {}).toCompletableFuture().join());
            assertEquals(original, db.storedValue("agent.schedule.v1", "management", "operations"));
            db.failingWritePrefix.set(null);
            assertEquals(receipt, store.manage(SCOPE, "admin", lookup, () -> {}).toCompletableFuture().join());
            assertNull(db.storedValue("agent.schedule.v1", "management", "operations"));
            assertNotNull(db.temporaryValue("agent.schedule.v1", "management", "operations"));
            db.clearTemporary();
            assertThrows(CompletionException.class, () -> store.manage(SCOPE, "admin", query, () -> {}).toCompletableFuture().join());
            assertThrows(CompletionException.class, () -> store.manage(SCOPE, "admin", lookup, () -> {}).toCompletableFuture().join());
            assertEquals(AgentScheduledTask.Status.CANCELLED, store.create(task).toCompletableFuture().join().status());
            assertTrue(store.due(NOW.plusSeconds(600), 10).toCompletableFuture().join().isEmpty());
        }
    }

    /** 终态观察可丢弃，仍需清理文件的失败作业和待执行作业不能随临时表丢失。 */
    @Test
    void stickerJobsSplitAtomicallyWithoutLosingRecoveryWork() {
        try (var db = new NamespaceDatabaseFixture()) {
            var settings = new AgentStickerFileConfig().snapshot();
            var catalog = new EasyDataStickerCatalog(db.data, db.mapper);
            var legacy = new Stickers.State();
            var done = job(Stickers.JobStatus.SAVED, 0, true);
            var waiting = job(Stickers.JobStatus.QUEUED, 0, false);
            var cleaning = job(Stickers.JobStatus.FAILED, 1024, false);
            List.of(done, waiting, cleaning).forEach(job -> legacy.getJobs().put(job.getId(), job));
            String target = "catalog:" + settings.catalogId();
            String original = db.mapper.writeValueAsString(legacy);
            db.seedFactRow("agent.sticker.v1", target, "state", original);
            db.failingWritePrefix.set("catalog:");
            assertThrows(CompletionException.class, () -> catalog.change(settings, state -> true).toCompletableFuture().join());
            assertEquals(original, db.storedValue("agent.sticker.v1", target, "state"));
            assertNull(db.temporaryValue("agent.sticker.v1", target, "observations"));
            db.failingWritePrefix.set(null);
            assertEquals(3, catalog.change(settings, state -> state.getJobs().size()).toCompletableFuture().join());
            var durable = db.mapper.readValue(db.storedValue("agent.sticker.v1", target, "state"), Stickers.State.class);
            assertEquals(Set.of(waiting.getId(), cleaning.getId()), durable.getJobs().keySet());
            assertTrue(db.temporaryValue("agent.sticker.v1", target, "observations").contains(done.getId().toString()));
            var reopened = new EasyDataStickerCatalog(db.data, db.mapper);
            assertEquals(3, reopened.change(settings, state -> state.getJobs().size()).toCompletableFuture().join());
            db.clearTemporary();
            assertEquals(Set.of(waiting.getId(), cleaning.getId()), reopened.change(settings,
                    state -> Set.copyOf(state.getJobs().keySet())).toCompletableFuture().join());
            reopened.change(settings, state -> {
                state.getJobs().get(cleaning.getId()).setCleanup(true);
                return true;
            }).toCompletableFuture().join();
            db.clearTemporary();
            assertEquals(Set.of(waiting.getId()), reopened.change(settings,
                    state -> Set.copyOf(state.getJobs().keySet())).toCompletableFuture().join());
        }
    }

    /** 游标可重建而业务正文不丢失；失败时不能提前删除旧游标或推进页面。 */
    @Test
    void maintenanceCursorMigratesAndRebuildsAfterCleanup() {
        try (var db = new NamespaceDatabaseFixture()) {
            var data = db.data.inNamespace("agent.cursor.test");
            db.seedFactRow("agent.cursor.test", "maintenance", "cursor", Long.toString(Long.MAX_VALUE));
            db.seedFactRow("agent.cursor.test", "business", "item", "长期状态");
            db.failingWritePrefix.set("maintenance:");
            assertThrows(CompletionException.class, () -> EasyDataMaintenancePage.run(data, "验证游标", List::size)
                    .toCompletableFuture().join());
            assertNotNull(db.storedValue("agent.cursor.test", "maintenance", "cursor"));
            db.failingWritePrefix.set(null);
            assertEquals(1, EasyDataMaintenancePage.run(data, "验证游标", List::size).toCompletableFuture().join());
            assertNull(db.storedValue("agent.cursor.test", "maintenance", "cursor"));
            assertNotNull(db.temporaryValue("agent.cursor.test", "maintenance:TeaNekoAgentEasyDataObject", "cursor"));
            db.clearTemporary();
            assertEquals(1, EasyDataMaintenancePage.run(data, "重建游标", List::size).toCompletableFuture().join());
            assertEquals("长期状态", db.storedValue("agent.cursor.test", "business", "item"));
        }
    }

    /**
     * 创建固定空间的单段发送，不包含真实客户端。
     * @param text 实际待投影正文
     * @return 一小时内可复查的发送意图
     */
    private static IAgentDeliveryStore.Part part(String text) {
        return new IAgentDeliveryStore.Part(REFERENCE, ACTOR, UUID.randomUUID(), 0, 1,
                StableDigest.sha256(text), text, NOW, NOW.plusSeconds(3600));
    }

    /**
     * 构造不同清理责任的作业，不下载或删除素材。
     * @param status 实际状态
     * @param reserved 尚需释放的资源额度
     * @param cleanup 是否已完成资源清理
     * @return 持久作业候选
     */
    private static Stickers.Job job(Stickers.JobStatus status, long reserved, boolean cleanup) {
        var job = new Stickers.Job();
        job.setStatus(status);
        job.setReserved(reserved);
        job.setCleanup(cleanup);
        job.setCreated(NOW);
        return job;
    }
}
