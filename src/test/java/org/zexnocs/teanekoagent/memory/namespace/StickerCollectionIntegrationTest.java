package org.zexnocs.teanekoagent.memory.namespace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.zexnocs.teanekoagent.agent.AgentScope;
import org.zexnocs.teanekoagent.file_config.AgentStickerFileConfig;
import org.zexnocs.teanekoagent.sticker.*;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekocore.utils.scanner.BeanScanner;

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
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * 复用原真实 DTO 事务夹具验证共享图库、一次分析、持久作业及资源回收。
 * <br>2026/11/03：管理收尾复用同一夹具，避免建立第二套事务替身。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.6.2-l
 * @version 4.6.2-l
 */
@Timeout(20)
class StickerCollectionIntegrationTest {
    @TempDir Path directory;
    /**
     * 仅替换视觉网络请求，文件及数据库走真实默认实现。
     * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
     *
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.6.2-l
     * @version 4.6.2-l
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
         * @param source 来源
         * @param prepared 真实预览
         * @param settings 配置
         * @param control 控制
         * @return 一次离线判定
         */
        @Override public CompletionStage<Stickers.Analysis> analyze(Stickers.Source source, IStickerAssetStore.Prepared prepared, StickerSettings settings, ExecutionControl control){
            calls.incrementAndGet();assertFalse(prepared.previews().isEmpty());return pending==null?CompletableFuture.completedStage(analysis(kind.get())):pending;
        }
    }
    /**
     * 局部容器只发现素材端口，不启动应用或外部数据库。
     * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
     *
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.6.2-l
     * @version 4.6.2-l
     */
    static final class Harness implements AutoCloseable {
        final NamespaceDatabaseFixture db=new NamespaceDatabaseFixture();
        final AnnotationConfigApplicationContext context=new AnnotationConfigApplicationContext();
        final AgentStickerFileConfig file=new AgentStickerFileConfig();
        final AtomicBoolean allowed=new AtomicBoolean(true);
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
            context.getBeanFactory().registerSingleton("data",db.data);context.getBeanFactory().registerSingleton("mapper",db.mapper);
            context.register(EasyDataStickerCatalog.class,LocalStickerAssets.class,WeightedStickerAdmission.class,Analyzer.class);context.refresh();
            analyzer=context.getBean(Analyzer.class);registry=new StickerExtensionRegistry(new BeanScanner(context,mock(ILogger.class)),null);
            // 2026/11/03：事件仅替换宿主边界，基础存储和文件仍是真实实现。
            catalog=context.getBean(IStickerCatalog.class);library=new StickerLibrary(registry,file::snapshot,s->allowed.get(),Clock.systemUTC(),lifecycle);
            service=new StickerCollectionService(library,registry,executor,scheduler,mock(ILogger.class));
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
