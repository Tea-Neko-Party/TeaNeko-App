package org.zexnocs.teanekoplugin.agent.cognition.memory.namespace;

// 2026/11/07：接口统一使用 I 前缀，调用合同与持久数据保持不变。

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.zexnocs.teanekoplugin.agent.expression.sticker.EasyDataStickerCatalog;
import org.zexnocs.teanekoplugin.agent.expression.sticker.IStickerCatalogTransaction;
import org.zexnocs.teanekoplugin.agent.expression.sticker.StickerLibrary;
import org.zexnocs.teanekoplugin.agent.expression.sticker.StickerManagement;
import org.zexnocs.teanekoplugin.agent.expression.sticker.StickerSettings;
import org.zexnocs.teanekoplugin.agent.expression.sticker.Stickers;
import org.zexnocs.teanekoplugin.agent.expression.sticker.file_config.AgentStickerFileConfig;
import org.zexnocs.teanekocore.utils.StableDigest;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 复用事务夹具验证删除目录增长、旧数据迁移、稀疏分页及失败回滚，不替代数据库压测。
 * <br>2026/11/05：覆盖超过旧主行容量的真实条目集合。
 * <br>2026/11/06：验证根锁竞争、缺省计数迁移与混合目录分页不遗漏。
 * <br>2026/11/07：使用独立临时素材卷验证本地描述迁移，避免访问业务目录。
 * <br>2026/11/07：批量迁移单独设置有界等待，保留后续分页查询的短等待期限。
 * @author zExNocs
 * @date 2026/11/05
 * @since 4.8.3-l
 * @version 4.9.0-l
 */
@Timeout(30)
class StickerDeletionStorageTest {
    // 2026/11/07：描述迁入本地后，删除目录回归也必须使用测试拥有的独立素材卷。
    @TempDir Path directory;

    /** @return 使用测试目录的真实图库配置 */
    private StickerSettings settings() {
        var config = new AgentStickerFileConfig();
        config.setStorageRoot(directory.toString());
        return config.snapshot();
    }
    /**
     * 大量删除记录分行保存，主目录仍可写入新素材，按键和分页查询不读取全部历史。
     * 2026/11/06：分页逐次有界等待，失败不挂起整轮回归。
     * 2026/11/07：万条迁移包含真实文件校验，单独允许 30 秒；该用例验证容量与完整性，不作吞吐基准。
     * @throws Exception 当分页或迁移未在期限内完成时
     */
    @Test
    @Timeout(60)
    void largeDeletionHistoryDoesNotFillRootAndPagesRemainBounded() throws Exception {
        try (var db = new NamespaceDatabaseFixture()) {
            var settings = settings();
            var catalog = new EasyDataStickerCatalog(db.data, db.mapper);
            var legacy = new Stickers.State();
            for (int index = 0; index < 12000; index++) {
                var entry = deleted(index);
                legacy.getEntries().put(entry.getHash(), entry);
            }
            assertTrue(db.mapper.writeValueAsString(legacy).length() > 8 * 1024 * 1024);
            catalog.change(settings, state -> {
                state.getEntries().putAll(legacy.getEntries());
                return true;
            }).toCompletableFuture().get(30, java.util.concurrent.TimeUnit.SECONDS);
            var rebuilt = new EasyDataStickerCatalog(db.data, db.mapper);
            assertTrue(rebuilt.transact(settings, transaction -> {
                assertEquals(12000, transaction.deletedCount());
                assertTrue(transaction.catalogCharacters() < 1024);
                assertEquals(0, StickerLibrary.cacheSize(transaction.state()));
                assertEquals(deleted(11999).getRevision(), transaction.deletedById(deleted(11999).getId()).orElseThrow().getRevision());
                var first = transaction.page(new StickerManagement.Query(StickerManagement.QueryKind.CATALOG, "", 100, "", null));
                assertEquals(100, first.items().size());
                assertEquals(deleted(99).getId(), first.next());
                var sparse = transaction.page(new StickerManagement.Query(StickerManagement.QueryKind.CATALOG, "", 100, "sad", Stickers.EntryStatus.DELETED));
                assertTrue(sparse.items().isEmpty());
                assertEquals(deleted(127).getId(), sparse.next());
                var active = new Stickers.Entry();
                active.setId("stk_zzzz");
                active.setHash(StableDigest.sha256("新增素材"));
                active.setCreated(Instant.now());
                transaction.state().getEntries().put(active.getHash(), active);
                return true;
            }).toCompletableFuture().get(10, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(1, rebuilt.change(settings, state -> state.getEntries().size())
                    .toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(12000, db.values("agent.sticker.v1", "deleted:" + settings.catalogId()).size());
        }
    }

    /**
     * 2026/11/06：用跨三个索引页的集合验证完整翻页，容量增长另由万条用例验证。
     * @throws Exception 当有界查询未完成时
     */
    @Test
    void mergedPagesIncludeActiveEntriesWithoutSkippingOrRepeating() throws Exception {
        try (var db = new NamespaceDatabaseFixture()) {
            var settings = settings();
            var catalog = new EasyDataStickerCatalog(db.data, db.mapper);
            catalog.change(settings, state -> {
                for (int index = 0; index < 300; index++) {
                    var entry = deleted(index);
                    state.getEntries().put(entry.getHash(), entry);
                }
                for (String id : List.of(deleted(100).getId() + "a", "stk_zzzz")) {
                    var active = new Stickers.Entry();
                    active.setId(id);
                    active.setHash(StableDigest.sha256(id));
                    state.getEntries().put(active.getHash(), active);
                }
                return true;
            }).toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS);
            var seen = new java.util.HashSet<String>();
            String after = "";
            int pages = 0;
            do {
                var query = new StickerManagement.Query(StickerManagement.QueryKind.CATALOG, after, 100, "", null);
                var page = catalog.transact(settings, transaction -> transaction.page(query))
                        .toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS);
                assertTrue(page.items().size() <= 100);
                page.items().forEach(entry -> assertTrue(seen.add(entry.getId()), "分页不能重复返回同一素材"));
                assertTrue(page.next().isEmpty() || page.next().compareTo(after) > 0);
                after = page.next();
                assertTrue(++pages <= 4, "有界页面应在全部条目读取后结束");
            } while (!after.isEmpty());
            assertEquals(302, seen.size());
        }
    }

    /** 旧目录迁移与操作一同回滚；重试迁移不重复计数，显式恢复失败仍保留原拒绝决定。 */
    @Test
    void migrationAndRestoreRollbackKeepDeletionDecisions() {
        try (var db = new NamespaceDatabaseFixture()) {
            var settings = settings();
            var catalog = new EasyDataStickerCatalog(db.data, db.mapper);
            var legacy = new Stickers.State();
            var entry = deleted(1);
            legacy.getEntries().put(entry.getHash(), entry);
            // 2026/11/06：模拟新增计数字段之前的旧目录，而不是用当前结构冒充旧数据。
            var legacyJson = db.mapper.valueToTree(legacy);
            assertInstanceOf(tools.jackson.databind.node.ObjectNode.class, legacyJson).remove("deletedDecisions");
            String original = db.mapper.writeValueAsString(legacyJson);
            db.seedFactRow("agent.sticker.v1", "catalog:" + settings.catalogId(), "state", original);
            db.failingWritePrefix.set("deleted-ids:");
            assertThrows(CompletionException.class, () -> catalog.change(settings, state -> true).toCompletableFuture().join());
            assertEquals(original, db.storedValue("agent.sticker.v1", "catalog:" + settings.catalogId(), "state"));
            assertTrue(db.values("agent.sticker.v1", "deleted:").isEmpty());
            db.failingWritePrefix.set(null);
            assertEquals(1, catalog.transact(settings, IStickerCatalogTransaction::deletedCount).toCompletableFuture().join());
            assertEquals(1, catalog.transact(settings, IStickerCatalogTransaction::deletedCount).toCompletableFuture().join());
            db.failingWritePrefix.set("catalog:");
            assertThrows(CompletionException.class, () -> catalog.transact(settings, transaction -> {
                var archived = transaction.deletedById(entry.getId()).orElseThrow();
                transaction.restoreDeleted(archived);
                archived.setState(Stickers.EntryStatus.CACHED);
                archived.setRevision(archived.getRevision() + 1);
                transaction.state().getEntries().put(archived.getHash(), archived);
                return true;
            }).toCompletableFuture().join());
            db.failingWritePrefix.set(null);
            assertTrue(catalog.transact(settings, transaction -> {
                assertEquals(1, transaction.deletedCount());
                assertTrue(transaction.state().getEntries().isEmpty());
                assertEquals(entry.getRevision(), transaction.deletedByHash(entry.getHash()).orElseThrow().getRevision());
                return true;
            }).toCompletableFuture().join());
        }
    }

    /**
     * 2026/11/06：两个宿主争用同一根锁时，独立删除计数与主目录修改不会互相覆盖。
     * @throws Exception 当有界事务协调失败时
     */
    @Test
    void competingCataloguesKeepAtomicCountersAndState() throws Exception {
        try (var db = new NamespaceDatabaseFixture()) {
            var settings = settings();
            var first = new EasyDataStickerCatalog(db.data, db.mapper);
            var second = new EasyDataStickerCatalog(db.data, db.mapper);
            first.change(settings, state -> true).toCompletableFuture().join();
            var entered = new java.util.concurrent.CompletableFuture<Void>();
            var release = new java.util.concurrent.CompletableFuture<Void>();
            var queued = new java.util.concurrent.CompletableFuture<Void>();
            var a = first.change(settings, state -> {
                entered.complete(null);
                release.orTimeout(5, java.util.concurrent.TimeUnit.SECONDS).join();
                var entry = deleted(1);
                state.getEntries().put(entry.getHash(), entry);
                state.setRevision(state.getRevision() + 1);
                return true;
            }).toCompletableFuture();
            java.util.concurrent.CompletableFuture<Boolean> b;
            try {
                entered.get(3, java.util.concurrent.TimeUnit.SECONDS);
                db.beforeTask.set(ignored -> queued.complete(null));
                b = second.change(settings, state -> {
                    assertEquals(1, state.getRevision());
                    var entry = deleted(2);
                    state.getEntries().put(entry.getHash(), entry);
                    state.setRevision(state.getRevision() + 1);
                    return true;
                }).toCompletableFuture();
                queued.get(3, java.util.concurrent.TimeUnit.SECONDS);
            } finally {
                release.complete(null);
            }
            java.util.concurrent.CompletableFuture.allOf(a, b).get(5, java.util.concurrent.TimeUnit.SECONDS);
            assertTrue(first.transact(settings, transaction -> {
                assertEquals(2, transaction.state().getRevision());
                assertEquals(2, transaction.deletedCount());
                assertTrue(transaction.deletedByHash(deleted(1).getHash()).isPresent());
                assertTrue(transaction.deletedByHash(deleted(2).getHash()).isPresent());
                return true;
            }).toCompletableFuture().join());
        }
    }

    /**
     * 构造字段长度合法、无字节与读占用的删除项，描述保留以验证真实增长而非空条目。
     * @param index 可排序序号
     * @return 有稳定 ID 和指纹的删除决定
     */
    private static Stickers.Entry deleted(int index) {
        var entry = new Stickers.Entry();
        entry.setId("stk_" + String.format(java.util.Locale.ROOT, "%032d", index));
        entry.setHash(StableDigest.sha256("删除记录" + index));
        entry.setState(Stickers.EntryStatus.DELETED);
        entry.setRevision(7);
        entry.setCreated(Instant.parse("2026-11-05T00:00:00Z"));
        entry.setAnalysis(new Stickers.Analysis(Stickers.Kind.STICKER, 0.9, true,
                "图".repeat(160), "用".repeat(80), "happy", List.of("标签"), 0.9, "据".repeat(160)));
        return entry;
    }
}
