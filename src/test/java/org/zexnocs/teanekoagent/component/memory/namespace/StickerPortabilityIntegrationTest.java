package org.zexnocs.teanekoagent.component.memory.namespace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.zexnocs.teanekoagent.component.sticker.EasyDataStickerCatalog;
import org.zexnocs.teanekoagent.component.sticker.IStickerCatalog;
import org.zexnocs.teanekoagent.component.sticker.StickerAnalysisSampling;
import org.zexnocs.teanekoagent.component.sticker.StickerCatalogTransaction;
import org.zexnocs.teanekoagent.component.sticker.Stickers;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionException;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.zexnocs.teanekoagent.component.memory.namespace.StickerCollectionIntegrationTest.png;
import static org.zexnocs.teanekoagent.component.memory.namespace.StickerCollectionIntegrationTest.source;

/**
 * 真实文件、原目录事务和受控模型替身共同验证迁移、回滚及概率边界。
 * <br>2026/11/07：不连接真实模型、平台或业务数据库。
 * <br>2026/11/07：从定时入口验证便携导入，覆盖维护执行预算的创建。
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.8.7-l
 * @version 4.8.7-l
 */
@Timeout(30)
class StickerPortabilityIntegrationTest {
    @TempDir Path directory;

    /**
     * 2026/11/07：复制素材后从真实定时入口恢复图库，不能绕过后台维护的预算创建。
     * @throws Exception 当测试图生成或文件复制失败时
     */
    @Test
    void scheduledMaintenanceImportsCopiedPairWithoutModel() throws Exception {
        try (var origin = harness("scheduled-origin"); var target = harness("scheduled-target")) {
            target.file.setCatalogId("portable");
            origin.collect(source("来源群", png(50)));
            var entry = origin.state().getEntries().values().iterator().next();
            copyPair(origin, target, entry);

            target.service.tick();

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                var imported = target.state().getEntries().get(entry.getHash());
                assertNotNull(imported, "定时维护应导入复制的素材与描述。");
                assertEquals(entry.getAnalysis(), imported.getAnalysis());
                assertEquals(Stickers.EntryStatus.ACTIVE, imported.getState());
            });
            assertEquals(0, target.analyzer.calls.get(), "本地图库维护不应调用视觉模型。");
        }
    }

    /**
     * 原图与旁车复制到空图库后直接恢复描述，不携带用户统计且不调用模型。
     * @throws Exception 当测试图生成或文件复制失败时
     */
    @Test
    void copiedPairImportsWithoutDatabaseOrModel() throws Exception {
        try (var origin = harness("origin"); var target = harness("target")) {
            target.file.setCatalogId("portable");
            var job = origin.collect(source("来源群", png(41)));
            var entry = origin.state().getEntries().values().iterator().next();
            var stored = origin.db.storedValue("agent.sticker.v1", "catalog:main", "state");
            assertNotNull(stored);
            assertFalse(stored.contains("\"analysis\":"));
            assertFalse(stored.contains("visualDescription"));
            assertTrue(stored.contains("analysisRef"));
            assertEquals(Stickers.JobStatus.SAVED, job.getStatus());
            copyPair(origin, target, entry);
            var report = maintain(target);
            assertEquals(1, report.imported());
            assertTrue(report.warnings().isEmpty());
            var imported = target.state().getEntries().get(entry.getHash());
            assertEquals(entry.getAnalysis(), imported.getAnalysis());
            assertNotEquals(entry.getId(), imported.getId());
            assertEquals(Stickers.EntryStatus.ACTIVE, imported.getState());
            assertTrue(imported.getHumanDays().isEmpty());
            assertEquals(0, target.analyzer.calls.get());
            assertEquals(0, maintain(target).imported());
        }
    }

    /**
     * 旧数据库正文先持久迁出；修改事务失败后原旁车及原描述保持一致。
     * @throws Exception 当测试图或文件读取失败时
     */
    @Test
    void legacyMigrationAndFailedEditKeepCommittedDescription() throws Exception {
        try (var h = harness("legacy")) {
            h.collect(source("旧群", png(42)));
            var legacy = h.state();
            // 旧格式只模拟描述内联，已经分流到临时表的终态作业不重新塞回主行。
            legacy.getJobs().values().removeIf(job -> !job.requiresRecovery());
            var entry = legacy.getEntries().values().iterator().next();
            var originalAnalysis = entry.getAnalysis();
            entry.setAnalysisRef("");
            h.db.seedFactRow("agent.sticker.v1", "catalog:main", "state", h.db.mapper.writeValueAsString(legacy));
            var reopened = new EasyDataStickerCatalog(h.db.data, h.db.mapper);
            assertEquals(originalAnalysis, reopened.change(h.file.snapshot(), state ->
                    state.getEntries().get(entry.getHash()).getAnalysis()).toCompletableFuture().join());
            String migrated = Objects.requireNonNull(h.db.storedValue("agent.sticker.v1", "catalog:main", "state"));
            assertFalse(migrated.contains("visualDescription"));
            h.state(); // 恢复上次实际提交的便携发布。
            byte[] originalSidecar = Files.readAllBytes(sidecar(h, entry));
            h.db.failingWritePrefix.set("catalog:");
            assertThrows(CompletionException.class, () -> reopened.change(h.file.snapshot(), state -> {
                state.getEntries().get(entry.getHash()).setAnalysis(revised());
                return true;
            }).toCompletableFuture().join());
            h.db.failingWritePrefix.set(null);
            assertArrayEquals(originalSidecar, Files.readAllBytes(sidecar(h, entry)));
            assertEquals(originalAnalysis, h.state().getEntries().get(entry.getHash()).getAnalysis());
            reopened.change(h.file.snapshot(), state -> {
                state.getEntries().get(entry.getHash()).setAnalysis(revised());
                return true;
            }).toCompletableFuture().join();
            h.state();
            assertTrue(Files.readString(sidecar(h, entry)).contains("修改后的猫咪"));
        }
    }

    /**
     * 人工禁用和删除不会因为重新复制同图而被覆盖或复活。
     * @throws Exception 当文件复制或测试编码失败时
     */
    @Test
    void copiedPairsCannotOverrideDisabledOrDeletedDecisions() throws Exception {
        try (var origin = harness("origin"); var target = harness("target")) {
            origin.collect(source("群", png(43)));
            var entry = origin.state().getEntries().values().iterator().next();
            copyPair(origin, target, entry);
            assertEquals(1, maintain(target).imported());
            target.catalog.change(target.file.snapshot(), state -> {
                state.getEntries().get(entry.getHash()).setState(Stickers.EntryStatus.DISABLED);
                return true;
            }).toCompletableFuture().join();
            target.state();
            copyPair(origin, target, entry);
            assertEquals(0, maintain(target).imported());
            assertEquals(Stickers.EntryStatus.DISABLED, target.state().getEntries().get(entry.getHash()).getState());
            target.catalog.change(target.file.snapshot(), state -> {
                var current = state.getEntries().get(entry.getHash());
                current.setState(Stickers.EntryStatus.DELETED);
                current.setBlob(null);
                return true;
            }).toCompletableFuture().join();
            target.state();
            assertFalse(Files.exists(sidecar(target, entry)));
            copyPair(origin, target, entry);
            assertEquals(0, maintain(target).imported());
            assertTrue(target.state().getEntries().isEmpty());
            assertEquals(1, target.catalog.transact(target.file.snapshot(), StickerCatalogTransaction::deletedCount)
                    .toCompletableFuture().join());
            String deleted = target.db.storedValue("agent.sticker.v1", "deleted:main", entry.getHash());
            assertNotNull(deleted);
            assertFalse(deleted.contains("visualDescription"));
        }
    }

    /**
     * 坏旁车不阻塞同页正常素材；实际原图与元数据须一致。
     * @throws Exception 当文件复制或编码失败时
     */
    @Test
    void invalidSidecarDoesNotStarveValidImport() throws Exception {
        try (var origin = harness("origin"); var target = harness("target")) {
            origin.collect(source("群", png(44)));
            var entry = origin.state().getEntries().values().iterator().next();
            copyPair(origin, target, entry);
            Files.writeString(assets(target).resolve("0".repeat(64) + ".sticker.json"), "{ invalid }");
            var report = maintain(target);
            assertEquals(1, report.imported());
            assertEquals(1, report.warnings().size());
            assertTrue(report.warnings().getFirst().contains("0".repeat(64)));
            assertFalse(report.warnings().getFirst().contains("{ invalid }"));
        }
    }

    /**
     * 描述文件丢失时重新打开明确失败，不能用空描述覆盖数据库引用。
     * @throws Exception 当测试文件读取或删除失败时
     */
    @Test
    void missingImmutableDescriptionDoesNotEraseReference() throws Exception {
        try (var h = harness("missing")) {
            h.collect(source("群", png(45)));
            // 关闭旧宿主的维护后模拟冷启动丢失文件，避免内存中的合法描述先恢复该文件。
            h.service.close();
            h.executor.shutdown();
            assertTrue(h.executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS));
            var entry = h.state().getEntries().values().iterator().next();
            String before = h.db.storedValue("agent.sticker.v1", "catalog:main", "state");
            Files.delete(Path.of(h.file.getStorageRoot(), "main", "descriptions", entry.getAnalysisRef() + ".json"));
            var reopened = new EasyDataStickerCatalog(h.db.data, h.db.mapper);
            var failure = assertThrows(CompletionException.class, () -> reopened.change(h.file.snapshot(), state -> true)
                    .toCompletableFuture().join());
            assertTrue(failure.getCause().getMessage().contains("描述缺失或损坏"));
            assertEquals(before, h.db.storedValue("agent.sticker.v1", "catalog:main", "state"));
        }
    }

    /**
     * 零概率不占模型额度；同消息之后明确收藏仍执行，缓存复用也不抽样。
     * @throws Exception 当测试图生成失败时
     */
    @Test
    void explicitCollectionBypassesSamplingAndCachedImagesStayReusable() throws Exception {
        try (var h = harness("sampling")) {
            h.file.setAnalysisProbabilityStatic(0);
            var source = source("群", png(46));
            assertEquals("PROBABILITY_SKIPPED", h.collect(source).getReason());
            assertEquals(0, h.analyzer.calls.get());
            assertTrue(h.state().getEntries().isEmpty());
            assertEquals(0, h.state().getQuotas().get("global").getAnalyses());
            var explicit = new Stickers.Source(source.scope(), source.author(), source.sender(), source.self(),
                    source.message(), source.image(), source.occurredAt(), true, source.requester(), source.url());
            assertEquals(Stickers.JobStatus.SAVED, h.collect(explicit).getStatus());
            assertEquals(1, h.analyzer.calls.get());
            assertEquals(Stickers.JobStatus.DUPLICATE, h.collect(source("另一群", png(46))).getStatus());
            assertEquals(1, h.analyzer.calls.get());
        }
    }

    /** 动态曲线、固定概率及非当前模式参数均遵守边界。 */
    @Test
    void samplingCurveAndValidationRemainDeterministic() {
        var dynamic = new StickerAnalysisSampling(StickerAnalysisSampling.Mode.DYNAMIC, .1, 1, .01, .2);
        assertEquals(1, dynamic.probability(0));
        assertEquals(.431091497, dynamic.probability(100), 0.000001);
        assertEquals(.116484075, dynamic.probability(200), 0.000001);
        assertEquals(.1, dynamic.probability(Long.MAX_VALUE));
        var fixed = new StickerAnalysisSampling(StickerAnalysisSampling.Mode.STATIC, .1, 1, .01, .2);
        assertEquals(.2, fixed.probability(1000));
        UUID job = UUID.randomUUID();
        assertEquals(fixed.accepts(job, 0), fixed.accepts(job, 1000));
        assertThrows(IllegalArgumentException.class, () -> new StickerAnalysisSampling(
                StickerAnalysisSampling.Mode.STATIC, .8, .2, .01, .2));
        assertThrows(IllegalArgumentException.class, () -> new StickerAnalysisSampling(
                StickerAnalysisSampling.Mode.DYNAMIC, .1, 1, Double.NaN, .2));
        assertThrows(IllegalArgumentException.class, () -> new StickerAnalysisSampling(
                StickerAnalysisSampling.Mode.DYNAMIC, .1, 1, .01, 1.1));
    }

    /**
     * 复制导入不会越过有效素材数量限制，超额素材保留为停用项。
     * @throws Exception 当测试图或文件复制失败时
     */
    @Test
    void importsBeyondActiveCapacityRemainDisabled() throws Exception {
        try (var origin = harness("origin"); var target = harness("target")) {
            origin.collect(source("群", png(47)));
            origin.collect(source("群", png(48)));
            target.file.setMaxActive(1);
            for (var entry : origin.state().getEntries().values()) {
                copyPair(origin, target, entry);
            }
            assertEquals(2, maintain(target).imported());
            var entries = target.state().getEntries().values();
            assertEquals(1, entries.stream().filter(entry -> entry.getState() == Stickers.EntryStatus.ACTIVE).count());
            assertEquals(1, entries.stream().filter(entry -> entry.getState() == Stickers.EntryStatus.DISABLED).count());
            assertEquals(0, target.analyzer.calls.get());
        }
    }

    /**
     * 历史独立删除行在无人查询的情况下也会分批迁出描述，不丢失原拒绝决定。
     * @throws Exception 当本地图编码失败时
     */
    @Test
    void maintenanceMigratesLegacyDeletedDescriptions() throws Exception {
        try (var h = harness("deleted")) {
            h.collect(source("群", png(49)));
            var state = h.state();
            var entry = state.getEntries().values().iterator().next();
            entry.setState(Stickers.EntryStatus.DELETED);
            entry.setBlob(null);
            entry.setAnalysisRef("");
            state.getEntries().clear();
            state.getJobs().clear();
            state.setDeletedDecisions(1);
            h.db.seedFactRow("agent.sticker.v1", "catalog:main", "state", h.db.mapper.writeValueAsString(state));
            h.db.seedFactRow("agent.sticker.v1", "deleted:main", entry.getHash(), h.db.mapper.writeValueAsString(entry));
            h.db.seedFactRow("agent.sticker.v1", "deleted-ids:main", entry.getId(), entry.getHash());
            maintain(h);
            String row = Objects.requireNonNull(h.db.storedValue("agent.sticker.v1", "deleted:main", entry.getHash()));
            assertFalse(row.contains("visualDescription"));
            var restored = h.catalog.transact(h.file.snapshot(), transaction -> transaction.deletedByHash(entry.getHash())
                    .orElseThrow()).toCompletableFuture().join();
            assertEquals(entry.getAnalysis(), restored.getAnalysis());
            assertEquals(Stickers.EntryStatus.DELETED, restored.getState());
        }
    }

    /**
     * 用生产 YAML 解析器验证字段命名、默认模式和切换后保留另一套参数。
     * @throws Exception 当测试输入流关闭失败时
     */
    @Test
    void yamlConfigKeepsBothSamplingModes() throws Exception {
        var parser = new org.zexnocs.teanekocore.file_config.parser.YAMLFileTypeParser();
        var configType = org.zexnocs.teanekoagent.component.sticker.file_config.AgentStickerFileConfig.class;
        byte[] yaml = "analysis-probability-mode: STATIC\nanalysis-probability-static: 0.35\nanalysis-probability-decay: 0.02\n"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        try (var input = new java.io.ByteArrayInputStream(yaml)) {
            var config = parser.fromFileToData(input, configType);
            assertEquals(.35, config.snapshot().sampling().probability(100));
            config.setAnalysisProbabilityMode(StickerAnalysisSampling.Mode.DYNAMIC);
            assertEquals(.116484075, config.snapshot().sampling().probability(100), .000001);
            assertEquals(.35, config.getAnalysisProbabilityStatic());
        }
        var defaults = parser.defaultDocument(configType);
        assertEquals("DYNAMIC", defaults.get("analysis-probability-mode").asString());
        assertEquals(.2, defaults.get("analysis-probability-static").asDouble());
    }

    /**
     * @param name 本测试隔离的素材目录
     * @return 使用真实文件与事务的测试宿主
     */
    private StickerCollectionIntegrationTest.Harness harness(String name) {
        return new StickerCollectionIntegrationTest.Harness(directory.resolve(name));
    }

    /**
     * @param h 已初始化宿主
     * @return 受测试目录保护的原图与旁车目录
     */
    private static Path assets(StickerCollectionIntegrationTest.Harness h) {
        return Path.of(h.file.getStorageRoot(), h.file.getCatalogId(), "assets");
    }

    /**
     * @param h 宿主
     * @param entry 原条目
     * @return 精确便携旁车路径
     */
    private static Path sidecar(StickerCollectionIntegrationTest.Harness h, Stickers.Entry entry) {
        return assets(h).resolve(entry.getHash() + ".sticker.json");
    }

    /**
     * @param from 源宿主
     * @param target 已初始化的目标宿主
     * @param entry 待复制条目
     * @throws Exception 当测试文件复制失败时
     */
    private static void copyPair(StickerCollectionIntegrationTest.Harness from,
                                 StickerCollectionIntegrationTest.Harness target, Stickers.Entry entry) throws Exception {
        target.registry.select(target.file.snapshot()).assets().identity(target.file.snapshot());
        String file = entry.getHash() + "." + Objects.requireNonNull(entry.getBlob()).format();
        Files.copy(assets(from).resolve(file), assets(target).resolve(file), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        Files.copy(sidecar(from, entry), sidecar(target, entry), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * @param h 本地测试宿主
     * @return 不调用模型的真实导入结果
     */
    private static IStickerCatalog.Maintenance maintain(StickerCollectionIntegrationTest.Harness h) {
        var settings = h.file.snapshot();
        var ports = h.registry.select(settings);
        try (var control = ExecutionControl.create()) {
            return ports.catalog().maintain(settings, ports.assets(), ports.policy(), control, () -> {});
        }
    }

    /** @return 与初始描述可区分的合法手工修改 */
    private static Stickers.Analysis revised() {
        return new Stickers.Analysis(Stickers.Kind.STICKER, .95, true, "修改后的猫咪", "高兴时使用",
                "happy", List.of("猫咪"), .95, "清晰可读");
    }
}
