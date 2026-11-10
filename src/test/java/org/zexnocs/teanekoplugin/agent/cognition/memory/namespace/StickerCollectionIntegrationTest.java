package org.zexnocs.teanekoplugin.agent.cognition.memory.namespace;

// 2026/11/07：接口统一使用 I 前缀，调用合同与持久数据保持不变。

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.zexnocs.teanekoagent.llm.framework.interaction.LLMCallLimits;
import org.zexnocs.teanekoagent.runtime.AgentScope;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekocore.utils.scanner.BeanScanner;
import org.zexnocs.teanekoplugin.agent.expression.sticker.EasyDataStickerCatalog;
import org.zexnocs.teanekoplugin.agent.expression.sticker.IStickerAnalyzer;
import org.zexnocs.teanekoplugin.agent.expression.sticker.IStickerAssetStore;
import org.zexnocs.teanekoplugin.agent.expression.sticker.IStickerCatalog;
import org.zexnocs.teanekoplugin.agent.expression.sticker.IStickerCatalogTransaction;
import org.zexnocs.teanekoplugin.agent.expression.sticker.IStickerLifecycle;
import org.zexnocs.teanekoplugin.agent.expression.sticker.LocalStickerAssets;
import org.zexnocs.teanekoplugin.agent.expression.sticker.StickerAccess;
import org.zexnocs.teanekoplugin.agent.expression.sticker.StickerAccessException;
import org.zexnocs.teanekoplugin.agent.expression.sticker.StickerAnalysisSampling;
import org.zexnocs.teanekoplugin.agent.expression.sticker.StickerCollectionService;
import org.zexnocs.teanekoplugin.agent.expression.sticker.StickerExtensionRegistry;
import org.zexnocs.teanekoplugin.agent.expression.sticker.StickerLibrary;
import org.zexnocs.teanekoplugin.agent.expression.sticker.StickerSettings;
import org.zexnocs.teanekoplugin.agent.expression.sticker.Stickers;
import org.zexnocs.teanekoplugin.agent.expression.sticker.TeaNekoStickerAnalyzer;
import org.zexnocs.teanekoplugin.agent.expression.sticker.WeightedStickerAdmission;
import org.zexnocs.teanekoplugin.agent.expression.sticker.file_config.AgentStickerFileConfig;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * 复用原真实 DTO 事务夹具验证共享图库、一次分析、持久作业及资源回收。
 * <br>2026/11/03：管理收尾复用同一夹具，避免建立第二套事务替身。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/05：采集调用边界验证真实模型交互必需的父预算，防止模拟视觉端口掩盖空预算错误。
 * <br>2026/11/05：补充组件审查修复的离线回归及初始化夹具。
 * <br>2026/11/07：原采集合同使用固定全量概率，概率边界另行验证。
 * <br>2026/11/07：测试宿主显式管理插件后台生命周期。
 * <br>2026/11/10：覆盖来源撤销、客户端等待恢复及迟到结果的原因与资源释放。
 * <br>2026/11/10：超限素材正常拒绝，验证持久终态、清理预约且不调用模型。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.6.2-l
 * @version 4.9.1-l
 */
@Timeout(20)
class StickerCollectionIntegrationTest {
    @TempDir Path directory;

    /** 2026/11/10：大图保留明确回执，清理完成后不重试，也不占用视觉额度或报告采集故障。 */
    @Test
    void oversizedCandidateIsRejectedAndCleanedWithoutModelCall() {
        try (var h = new Harness(directory)) {
            h.file.setMaxFileBytes(1024);
            String image = "data:image/png;base64," + Base64.getEncoder().encodeToString(new byte[1025]);
            var job = h.collect(source("超限素材", image));
            assertEquals(Stickers.JobStatus.REJECTED, job.getStatus());
            assertEquals("FILE_TOO_LARGE", job.getReason());
            assertTrue(job.isCleanup());
            assertEquals(0, job.getReserved());
            assertTrue(h.state().getEntries().isEmpty());
            assertTrue(h.state().getQuotas().values().stream().allMatch(quota -> quota.getAnalyses() == 0));
            assertEquals(0, h.analyzer.calls.get());
            var receipt = h.library.status(job.getId(), job.getSource().scope(), job.getSource().requester())
                    .toCompletableFuture().join();
            assertEquals("FILE_TOO_LARGE", receipt.reason());
            org.mockito.Mockito.verify(h.logger).info(org.mockito.ArgumentMatchers.anyString(),
                    org.mockito.ArgumentMatchers.contains("max-file-bytes=1024"));
            org.mockito.Mockito.verifyNoMoreInteractions(h.logger);
        }
    }

    /**
     * 仅替换视觉网络请求，文件及数据库走真实默认实现。
     * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
     *
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     * <br>2026/11/05：视觉替身保留真实交互的宿主预算契约。
     * <br>2026/11/07：认知业务与宿主生命周期解耦，保留原身份、事务及异步完成边界。
     *
     *
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.6.2-l
     * @version 4.9.0-l
     */
    @TeaNekoStickerAnalyzer("test") public static final class Analyzer implements IStickerAnalyzer {
        final AtomicInteger calls=new AtomicInteger();
        final AtomicReference<Stickers.Kind> kind=new AtomicReference<>(Stickers.Kind.STICKER);
        volatile CompletableFuture<Stickers.Analysis> pending;
        /**
         * @param source 来源
         * @param settings 配置
         * @return 本地固定可用
         */
        @Override public boolean ready(Stickers.Source source, StickerSettings settings){return true;}
        /**
         * 2026/11/05：同真实 LLM 预检一样读取三个预算维度，缺少声明时测试立即失败。
         * @param source 来源
         * @param prepared 真实预览
         * @param settings 配置
         * @param control 控制
         * @return 一次离线判定
         */
        @Override public CompletionStage<Stickers.Analysis> analyze(Stickers.Source source, IStickerAssetStore.Prepared prepared, StickerSettings settings, ExecutionControl control){
            assertEquals(1, control.budget().remaining(LLMCallLimits.MODEL_REQUESTS));
            assertEquals(0, control.budget().remaining(LLMCallLimits.TOOL_ROUNDS));
            assertEquals(0, control.budget().remaining(LLMCallLimits.TOOL_EXECUTIONS));
            calls.incrementAndGet();assertFalse(prepared.previews().isEmpty());return pending==null?CompletableFuture.completedStage(analysis(kind.get())):pending;
        }
    }
    /**
     * 局部容器只发现素材端口，不启动应用或外部数据库。
     * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
     *
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     * <br>2026/11/07：认知业务与宿主生命周期解耦，保留原身份、事务及异步完成边界。
     *
     *
     * <br>2026/11/10：独立控制来源资格和连接就绪，观察原任务与诊断结果。
     *
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.6.2-l
     * @version 4.9.1-l
     */
    static final class Harness implements AutoCloseable {
        final NamespaceDatabaseFixture db=new NamespaceDatabaseFixture();
        final AnnotationConfigApplicationContext context=new AnnotationConfigApplicationContext();
        final AgentStickerFileConfig file=new AgentStickerFileConfig();
        final AtomicBoolean allowed=new AtomicBoolean(true);
        // 2026/11/10：客户端就绪与组件资格独立变化，覆盖启动恢复及撤销边界。
        final AtomicBoolean connected = new AtomicBoolean(true);
        final ILogger logger = mock(ILogger.class);
        final ExecutorService executor=Executors.newVirtualThreadPerTaskExecutor();
        final ScheduledExecutorService scheduler=Executors.newSingleThreadScheduledExecutor();
        final Analyzer analyzer;
        final StickerExtensionRegistry registry;
        final StickerLibrary library;
        final StickerCollectionService service;
        final IStickerCatalog catalog;
        /** @param path 测试拥有目录 */
        Harness(Path path){this(path,null);}
        /**
         * 2026/11/03：可选接入真实生命周期适配器，复用原文件、事务和后台执行器。
         * @param path 测试目录
         * @param lifecycle 可选受限事件
         */
        Harness(Path path,@org.jspecify.annotations.Nullable IStickerLifecycle lifecycle){
            file.setStorageRoot(path.toString());file.setAnalyzer("test");file.setMaintenanceSeconds(1);
            // 2026/11/07：原采集合同测试固定全量，概率边界由专门回归验证，避免随机失败。
            file.setAnalysisProbabilityMode(StickerAnalysisSampling.Mode.STATIC);
            file.setAnalysisProbabilityStatic(1);
            context.getBeanFactory().registerSingleton("data",db.data);context.getBeanFactory().registerSingleton("mapper",db.mapper);
            context.register(EasyDataStickerCatalog.class,LocalStickerAssets.class,WeightedStickerAdmission.class,Analyzer.class);context.refresh();
            // 2026/11/07：迁出后的真实素材端口使用明确的插件目录。
            analyzer = context.getBean(Analyzer.class);
            var plugins = new org.zexnocs.teanekocore.plugin.PluginRegistry(List.of(() ->
                    new org.zexnocs.teanekocore.plugin.PluginDescriptor("agent.expression", "4.9.0-l", 1, Set.of())));
            registry = new StickerExtensionRegistry(new BeanScanner(context, mock(ILogger.class)), plugins);
            // 2026/11/03：事件仅替换宿主边界，基础存储和文件仍是真实实现。
            // 2026/11/10：复用真实事务和文件端口，只替换宿主的来源就绪查询。
            catalog = context.getBean(IStickerCatalog.class);
            library = new StickerLibrary(registry, file::snapshot, source -> {
                if (!allowed.get()) {
                    return StickerAccess.SOURCE_DISABLED;
                }
                return connected.get() ? StickerAccess.ALLOWED : StickerAccess.CLIENT_UNAVAILABLE;
            }, Clock.systemUTC(), lifecycle);
            service = new StickerCollectionService(library, registry, executor, scheduler, logger);
            // 2026/11/07：独立测试宿主显式启用本代后台任务。
            service.start().toCompletableFuture().join();
        }
        /** @return 本次提交的目录副本 */
        Stickers.State state(){return catalog.change(file.snapshot(),s->s).toCompletableFuture().join();}
        /**
         * @param source 当前原生候选
         * @return 实际终态作业
         */
        Stickers.Job collect(Stickers.Source source){
            var receipt=library.offer(source).toCompletableFuture().join();assertNotNull(receipt.jobId());
            await().atMost(Duration.ofSeconds(8)).pollInterval(Duration.ofMillis(25)).until(()->{service.tick();var job=state().getJobs().get(receipt.jobId());return job!=null&&job.getReserved()==0;});
            return state().getJobs().get(receipt.jobId());
        }
        /** 释放本测试拥有的资源，随后关闭事务夹具。 */
        @Override public void close(){service.close();executor.close();scheduler.shutdownNow();context.close();db.close();}
    }
    /**
     * 2026/11/10：备用请求在原持久配额内追加计次，达到限额不继续预约。
     * @throws Exception 当合成图片准备失败时
     */
    @Test
    void fallbackConsumesOriginalGlobalAndScopeQuotas() throws Exception {
        try (var h = new Harness(directory); var control = ExecutionControl.create()) {
            h.file.setAnalysesPerHour(2);
            h.file.setScopeAnalysesPerHour(2);
            var config = h.file.snapshot();
            var ports = h.registry.select(config);
            h.library.offer(source("备用配额", png(2))).toCompletableFuture().join();
            var work = h.library.claim(config, ports, ports.assets().identity(config))
                    .toCompletableFuture().join().orElseThrow();
            var prepared = ports.assets().prepare(work.job(), config, control);
            work.job().setBlob(prepared.blob());
            assertEquals("ANALYZE", h.library.attach(config, ports, work, prepared.blob(), control)
                    .toCompletableFuture().join());
            assertTrue(h.library.reserveAdditionalAnalysis(config, ports, work, control).toCompletableFuture().join());
            assertFalse(h.library.reserveAdditionalAnalysis(config, ports, work, control).toCompletableFuture().join());
            assertTrue(h.state().getQuotas().values().stream().allMatch(value -> value.getAnalyses() == 2));
            h.library.fail(config, ports, work, null).toCompletableFuture().join();
            ports.assets().discard(work.job(), config);
            h.library.cleaned(config, ports, work).toCompletableFuture().join();
        }
    }

    /**
     * 2026/11/10：已排队的旧作业等待客户端，不调用视觉；恢复后只处理一次并释放全部预约。
     * @throws Exception 当合成图像编码失败时
     */
    @Test
    void waitsForClientBeforeStartingQueuedCollection() throws Exception {
        try (var h = new Harness(directory)) {
            var receipt = h.library.offer(source("等待连接", png(3))).toCompletableFuture().join();
            h.connected.set(false);
            await().atMost(Duration.ofSeconds(5)).until(() -> {
                h.service.tick();
                return "CLIENT_UNAVAILABLE".equals(h.state().getJobs().get(receipt.jobId()).getReason());
            });
            var queued = h.state().getJobs().get(receipt.jobId());
            assertEquals(Stickers.JobStatus.QUEUED, queued.getStatus());
            assertTrue(queued.getReserved() > 0);
            assertEquals(0, h.analyzer.calls.get());
            h.connected.set(true);
            h.catalog.change(h.file.snapshot(), state -> {
                state.getJobs().get(receipt.jobId()).setNextAttempt(Instant.EPOCH);
                return true;
            }).toCompletableFuture().join();
            await().atMost(Duration.ofSeconds(5)).until(() -> {
                h.service.tick();
                return h.state().getJobs().get(receipt.jobId()).getReserved() == 0;
            });
            assertEquals(Stickers.JobStatus.SAVED, h.state().getJobs().get(receipt.jobId()).getStatus());
            assertEquals(1, h.analyzer.calls.get());
            org.mockito.Mockito.verifyNoInteractions(h.logger);
        }
    }

    /**
     * 2026/11/10：关闭来源后排队项明确拒绝并释放预约；新候选不入队，配置变更可安全诊断。
     * @throws Exception 当合成图像编码失败时
     */
    @Test
    void rejectsRevokedSourcesWithoutPretendingProcessingFailure() throws Exception {
        try (var h = new Harness(directory)) {
            var candidate = source("关闭来源", png(4));
            var receipt = h.library.offer(candidate).toCompletableFuture().join();
            h.allowed.set(false);
            await().atMost(Duration.ofSeconds(5)).until(() -> {
                h.service.tick();
                return h.state().getJobs().get(receipt.jobId()).getReserved() == 0;
            });
            var rejected = h.state().getJobs().get(receipt.jobId());
            assertEquals(Stickers.JobStatus.REJECTED, rejected.getStatus());
            assertEquals("SOURCE_DISABLED", rejected.getReason());
            var newReceipt = h.library.offer(source("关闭来源", png(5))).toCompletableFuture().join();
            assertEquals(Stickers.JobStatus.REJECTED, newReceipt.status());
            assertEquals("SOURCE_DISABLED", newReceipt.reason());
            assertEquals(1, h.state().getJobs().size());
            assertEquals(0, h.analyzer.calls.get());
            h.allowed.set(true);
            var config = h.file.snapshot();
            var ports = h.registry.select(config);
            h.file.setPassiveEnabled(false);
            var failure = assertThrows(StickerAccessException.class,
                    () -> h.library.check(config, ports, candidate, null));
            assertEquals(StickerAccess.CONFIG_CHANGED, failure.access());
            assertEquals(StickerAccess.CONFIG_CHANGED.description(),
                    org.zexnocs.teanekocore.logger.FailureDiagnostic.from(failure).description());
        }
    }
    /**
     * 2026/11/05：已回收文件的删除决定不挤占新缓存，同图再次出现仍不得复活。
     * @throws Exception 当本地图像编码失败时
     */
    @Test
    void reclaimedDeletionDecisionsDoNotFillAnalysisCache() throws Exception {
        try (var h = new Harness(directory)) {
            String blocked = org.zexnocs.teanekocore.utils.StableDigest.sha256("已删除图片");
            h.catalog.change(h.file.snapshot(), state -> {
                for (int index = 0; index < 2048; index++) {
                    var entry = new Stickers.Entry();
                    entry.setHash(index == 0 ? blocked : org.zexnocs.teanekocore.utils.StableDigest.sha256("删除" + index));
                    entry.setId("stk_" + entry.getHash().substring(0, 32));
                    entry.setState(Stickers.EntryStatus.DELETED);
                    entry.setCreated(Instant.now());
                    state.getEntries().put(entry.getHash(), entry);
                }
                return true;
            }).toCompletableFuture().join();
            assertEquals(Stickers.JobStatus.SAVED, h.collect(source("群甲", png(11))).getStatus());
            assertEquals(1, StickerLibrary.cacheSize(h.state()));
            var settings = h.file.snapshot();
            var ports = h.registry.select(settings);
            var receipt = h.library.offer(source("群乙", png(12))).toCompletableFuture().join();
            var work = h.library.claim(settings, ports, ports.assets().identity(settings)).toCompletableFuture().join().orElseThrow();
            assertEquals(receipt.jobId(), work.job().getId());
            try (var control = ExecutionControl.create()) {
                assertEquals("DONE", h.library.attach(settings, ports, work,
                        new Stickers.Blob(blocked, "png", 32, 8, 8, 1), control).toCompletableFuture().join());
            }
            assertEquals("MANAGED_BLOCKED", h.state().getJobs().get(receipt.jobId()).getReason());
            // 2026/11/05：主目录只保留有界活动状态，人工决定已按指纹独立存储。
            assertEquals(1, h.state().getEntries().size());
            assertEquals(2048, h.catalog.transact(settings, IStickerCatalogTransaction::deletedCount).toCompletableFuture().join());
        }
    }

    /**
     * 同图跨 scope 只分析一次，画作拒绝也缓存；重建宿主后仍能继续并回收损坏素材。
     * @throws Exception 当本地图像编码失败时
     */
    @Test void collectsSharedLibraryAndCachesVisualRejection()throws Exception{
        try(var h=new Harness(directory)){
            var first=h.collect(source("群甲",png(1)));assertEquals(Stickers.JobStatus.SAVED,first.getStatus());
            var other=h.collect(source("群乙",png(1)));assertEquals(first.getStickerId(),other.getStickerId());assertEquals(Stickers.JobStatus.DUPLICATE,other.getStatus());assertEquals(1,h.analyzer.calls.get());
            var entry=h.state().getEntries().values().stream().filter(e->(e.getState()==Stickers.EntryStatus.ACTIVE)).findFirst().orElseThrow();
            assertEquals(2,entry.getHumanDays().values().stream().mapToInt(Integer::intValue).sum());
            h.analyzer.kind.set(Stickers.Kind.ILLUSTRATION);assertEquals(Stickers.JobStatus.REJECTED,h.collect(source("群甲",png(2))).getStatus());
            assertEquals(Stickers.JobStatus.REJECTED,h.collect(source("群乙",png(2))).getStatus());assertEquals(2,h.analyzer.calls.get());
            var restored=new EasyDataStickerCatalog(h.db.data,h.db.mapper);assertEquals(1,restored.change(h.file.snapshot(),s->s.getEntries().values().stream().filter(e->(e.getState()==Stickers.EntryStatus.ACTIVE)).count()).toCompletableFuture().join());
            var ports=h.registry.select(h.file.snapshot());ports.assets().delete(Objects.requireNonNull(entry.getBlob()),h.file.snapshot());
            await().atMost(Duration.ofSeconds(5)).until(()->{h.service.tick();return h.state().getEntries().values().stream().noneMatch(e->(e.getState()==Stickers.EntryStatus.ACTIVE));});
        }
    }
    /**
     * 关闭原来源后模型迟到结果不能激活；下载暂存与容量预约仍会回收。
     * @throws Exception 当本地图像编码失败时
     */
    @Test void rejectsLateAnalysisAfterSourceDisabled()throws Exception{
        try(var h=new Harness(directory)){
            var pending=new CompletableFuture<Stickers.Analysis>();h.analyzer.pending=pending;
            var receipt=h.library.offer(source("群甲",png(3))).toCompletableFuture().join();
            await().atMost(Duration.ofSeconds(5)).until(()->{h.service.tick();return h.analyzer.calls.get()==1;});
            var queued=h.library.offer(source("群乙",png(10))).toCompletableFuture().join();
            h.allowed.set(false);pending.complete(analysis(Stickers.Kind.STICKER));
            await().atMost(Duration.ofSeconds(5)).until(()->h.state().getJobs().get(receipt.jobId()).getReserved()==0);
            // 2026/11/10：迟到结果只记录资格撤销，不能变成未知处理失败或被再次请求。
            assertEquals("SOURCE_DISABLED", h.state().getJobs().get(receipt.jobId()).getReason());
            org.mockito.Mockito.verify(h.logger, org.mockito.Mockito.never())
                    .error(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
            assertEquals(Stickers.JobStatus.FAILED,h.state().getJobs().get(receipt.jobId()).getStatus());assertTrue(h.state().getEntries().values().stream().noneMatch(e->(e.getState()==Stickers.EntryStatus.ACTIVE)));
            h.file.setEnabled(false);
            await().atMost(Duration.ofSeconds(5)).until(()->{h.service.tick();return h.state().getJobs().get(queued.jobId()).getReserved()==0;});
            assertEquals("COLLECTION_DISABLED",h.state().getJobs().get(queued.jobId()).getReason());assertEquals(1,h.analyzer.calls.get());
        }
    }
    /**
     * 队列预约、固定及发送租约约束由同一事务仲裁，失败不发布目录。
     * @throws Exception 当本地图像编码失败时
     */
    @Test void protectsQuotaPinsAndTransactionRollback()throws Exception{
        try(var h=new Harness(directory);var control=ExecutionControl.create()){
            h.file.setMaxActive(1);h.file.setProbationDays(0);var first=h.collect(source("群甲",png(4)));
            h.catalog.change(h.file.snapshot(),s->{s.getEntries().values().stream().filter(e->e.getId().equals(first.getStickerId())).forEach(e->e.setPinned(true));return true;}).toCompletableFuture().join();
            assertEquals(Stickers.JobStatus.REJECTED,h.collect(source("群乙",png(5))).getStatus());
            var config=h.file.snapshot();var ports=h.registry.select(config);
            var request=source("群甲",png(6));var queued=h.library.offer(request).toCompletableFuture().join();
            var work=h.library.claim(config,ports,ports.assets().identity(config)).toCompletableFuture().join().orElseThrow();
            var prepared=ports.assets().prepare(work.job(),config,control);work.job().setBlob(prepared.blob());
            h.library.attach(config,ports,work,prepared.blob(),control).toCompletableFuture().join();
            h.catalog.change(config,s->{s.getEntries().values().stream().filter(e->e.getId().equals(first.getStickerId())).forEach(e->{e.setPinned(false);e.getReaders().put(UUID.randomUUID(),Instant.now().plusSeconds(60));});return true;}).toCompletableFuture().join();
            h.library.analyzed(config,ports,work,analysis(Stickers.Kind.STICKER),control).toCompletableFuture().join();ports.assets().publish(work.job(),config,control);
            h.db.failingWritePrefix.set("catalog:");assertThrows(CompletionException.class,()->h.library.activate(config,ports,work,control).toCompletableFuture().join());h.db.failingWritePrefix.set(null);
            assertEquals(Stickers.JobStatus.RUNNING,h.state().getJobs().get(queued.jobId()).getStatus());
            assertEquals(Stickers.JobStatus.REJECTED,h.library.activate(config,ports,work,control).toCompletableFuture().join().status());
            assertThrows(CompletionException.class,()->h.library.activate(config,ports,work,control).toCompletableFuture().join());
            ports.assets().discard(work.job(),config);h.library.cleaned(config,ports,work).toCompletableFuture().join();
            h.catalog.change(config,s->{s.getEntries().values().stream().filter(e->e.getId().equals(first.getStickerId())).forEach(e->{
                e.getReaders().clear();var old=Objects.requireNonNull(e.getAnalysis());e.setAnalysis(new Stickers.Analysis(old.kind(),old.confidence(),old.worthKeeping(),old.visualDescription(),old.usageDescription(),old.primaryCategory(),old.secondaryTags(),.65,old.reasons()));
            });return true;}).toCompletableFuture().join();
            assertEquals(Stickers.JobStatus.SAVED,h.collect(source("群乙",png(9))).getStatus());
            assertEquals(1,h.state().getEntries().values().stream().filter(e->(e.getState()==Stickers.EntryStatus.ACTIVE)).count());
            assertTrue(h.state().getEntries().values().stream().filter(e->e.getId().equals(first.getStickerId())).noneMatch(e->(e.getState()==Stickers.EntryStatus.ACTIVE)));
            h.file.setMaxPending(1);assertNotNull(h.library.offer(source("群甲",png(7))).toCompletableFuture().join().jobId());
            assertEquals("CAPACITY",h.library.offer(source("群乙",png(8))).toCompletableFuture().join().reason());
            assertThrows(CompletionException.class,()->h.library.status(first.getId(),new AgentScope("test","其他群"),UUID.randomUUID()).toCompletableFuture().join());
        }
    }
    /**
     * @param kind 视觉类型
     * @return 有界离线判定
     */
    private static Stickers.Analysis analysis(Stickers.Kind kind){return new Stickers.Analysis(kind,.99,true,"小猫举起爪子","表达开心","happy",List.of("开心"),.9,"表情动作清楚");}
    /**
     * @param group 真实区域
     * @param image 内联图片
     * @return 原来源
     */
    static Stickers.Source source(String group,String image){var user=UUID.nameUUIDFromBytes(group.getBytes(java.nio.charset.StandardCharsets.UTF_8));return new Stickers.Source(new AgentScope("test",group),user,"用户","机器人",UUID.randomUUID().toString(),0,Instant.now(),false,user,image);}
    /**
     * @param value 用于区分内容的像素
     * @return 测试本地编码图片
     * @throws Exception 当编码失败时
     */
    static String png(int value)throws Exception{var image=new BufferedImage(16,16,BufferedImage.TYPE_INT_RGB);image.setRGB(0,0,value);try(var output=new ByteArrayOutputStream()){ImageIO.write(image,"png",output);return "data:image/png;base64,"+Base64.getEncoder().encodeToString(output.toByteArray());}}
}
