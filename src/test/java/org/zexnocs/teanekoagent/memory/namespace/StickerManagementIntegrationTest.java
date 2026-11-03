package org.zexnocs.teanekoagent.memory.namespace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.zexnocs.teanekoagent.agent.AgentScope;
import org.zexnocs.teanekoagent.file_config.AgentStickerFileConfig;
import org.zexnocs.teanekoagent.sticker.*;
import org.zexnocs.teanekoapp.fake_client.FakeClient;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskResult;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskService;
import org.zexnocs.teanekocore.command.api.CommandPermission;
import org.zexnocs.teanekocore.event.EventService;
import org.zexnocs.teanekocore.event.core.EventHandler;
import org.zexnocs.teanekocore.event.core.EventHandlerScanner;
import org.zexnocs.teanekocore.event.interfaces.IEvent;
import org.zexnocs.teanekocore.file_config.interfaces.IFileConfigService;
import org.zexnocs.teanekocore.framework.function.MethodCallable;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekocore.plugin.PluginAccess;
import org.zexnocs.teanekocore.plugin.PluginDescriptor;
import org.zexnocs.teanekocore.plugin.PluginOwned;
import org.zexnocs.teanekocore.plugin.PluginRegistry;
import org.zexnocs.teanekocore.plugin.management.ManagementAuthority;
import org.zexnocs.teanekocore.plugin.management.ManagementContext;
import org.zexnocs.teanekocore.plugin.management.ManagementGateway;
import org.zexnocs.teanekocore.plugin.management.ManagementResource;
import org.zexnocs.teanekocore.utils.scanner.BeanScanner;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IClassScanner;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.zexnocs.teanekoagent.memory.namespace.StickerCollectionIntegrationTest.*;

/**
 * 复用真实 DTO 与文件夹具，仅检查全局管理、删除保护及刷新与人工写入的仲裁。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.6.2-l
 * @version 4.6.2-l
 */
@Timeout(30)
class StickerManagementIntegrationTest {
    @TempDir Path directory;
    /**
     * 测试监听器仍由真实 Core scanner、插件校验与事件分发器调用。
     * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
     *
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.6.2-l
     * @version 4.6.2-l
     */
    @org.zexnocs.teanekocore.event.core.EventListener
    @PluginOwned("test.stickers")
    public static final class Observer {
        final List<IStickerLifecycle.Notice> notices=new CopyOnWriteArrayList<>();
        volatile StickerCollectingEvent candidate;
        boolean veto;
        /** @param event 只能建议和拒绝的真实草稿 */
        @EventHandler public void collecting(StickerCollectingEvent event){candidate=event;event.suggest("只记录可辨认的表情动作");if(veto){event.setCancelled(true);event.setCancelled(false);}}
        /** @param event 已经提交的只读通知 */
        @EventHandler public void committed(StickerCommittedEvent event){notices.add(event.getData());assertThrows(UnsupportedOperationException.class,()->event.setCancelled(true));}
    }
    /**
     * 待容器装配完成后绑定真实事件适配器，不替换业务行为。
     * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
     *
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.6.2-l
     * @version 4.6.2-l
     */
    private static final class Hook implements IStickerLifecycle {
        IStickerLifecycle target;
        /**
         * @param source 原候选
         * @param settings 当前配置
         * @return 实际事件决策
         */
        @Override public CompletionStage<Decision> before(Stickers.Source source, StickerSettings settings){return target.before(source,settings);}
        /** @param notice 已提交结果 */
        @Override public void committed(Notice notice){target.committed(notice);}
    }
    /**
     * 共用既有存储，新增真实资源注册与显式授权；不启动 HTTP 或真实模型。
     * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
     *
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.6.2-l
     * @version 4.6.2-l
     */
    private static final class Fixture implements AutoCloseable {
        final Hook hook=new Hook();
        final Harness h;
        final ExecutionControl control=ExecutionControl.create();
        final AtomicBoolean authorized=new AtomicBoolean(true);
        final Observer observer=new Observer();
        final ManagementGateway gateway;
        final AgentScope refreshScope;
        /** @param path 测试目录 */
        @SuppressWarnings("unchecked")
        Fixture(Path path){
            h=new Harness(path,hook);var logger=mock(ILogger.class);var beans=h.context.getBeanFactory();
            var files=mock(IFileConfigService.class);when(files.requireCurrent(AgentStickerFileConfig.class)).thenReturn(h.file);
            var plugins=new PluginRegistry(List.of(()->new PluginDescriptor("teaneko.agent","1",1,Set.of(),true),()->new PluginDescriptor("test.stickers","1",1,Set.of())));
            var client=new FakeClient(logger).handleGroupMessage("管理分析群","用户","昵称",CommandPermission.MEMBER,"你好").getData().getClient();refreshScope=new AgentScope(client.getClientId(),"管理分析群");
            beans.registerSingleton("client",client);beans.registerSingleton("observer",observer);beans.registerSingleton("plugins",plugins);
            beans.registerSingleton("files",files);beans.registerSingleton("library",h.library);beans.registerSingleton("registry",h.registry);
            beans.registerSingleton("logger",logger);beans.registerSingleton("virtualExecutor",h.executor);
            var scanner=new BeanScanner(h.context,logger);beans.registerSingleton("scanner",scanner);var classes=mock(IClassScanner.class);
            doReturn(Set.of(StickerCollectingEvent.class,StickerCommittedEvent.class)).when(classes).getClassesWithInterface(IEvent.class);
            var handlers=new EventHandlerScanner(scanner,logger,classes);handlers.init();var tasks=mock(ITaskService.class);
            when(tasks.subscribeWithFuture(anyString(),anyString(),any(MethodCallable.class),eq(Void.class))).thenAnswer(call->{
                try{return TaskFuture.fromStage(logger,"素材事件",CompletableFuture.completedStage(call.<MethodCallable<ITaskResult<Void>>>getArgument(2).call()));}
                catch(Exception failure){return TaskFuture.fromStage(logger,"素材事件",CompletableFuture.failedStage(failure));}
            });
            var events=new EventService(logger,handlers,tasks);events.installPluginAccess(new PluginAccess(plugins));beans.registerSingleton("events",events);
            h.context.register(StickerEvents.class,StickerManagementService.class,StickerManagementResources.Catalog.class,StickerManagementResources.Preview.class,StickerManagementResources.Change.class,StickerManagementResources.Operation.class);
            hook.target=h.context.getBean(StickerEvents.class);h.file.setEventPlugins(List.of("test.stickers"));
            gateway=new ManagementGateway(plugins,List.of(h.context.getBean(StickerManagementResources.Catalog.class),h.context.getBean(StickerManagementResources.Preview.class),h.context.getBean(StickerManagementResources.Change.class),h.context.getBean(StickerManagementResources.Operation.class)),beans.getBeanProvider(ManagementAuthority.class));
        }
        /** 显式安装授权，不存在任何默认开放逻辑。 */
        void authorize(){h.context.getBeanFactory().registerSingleton("authority",(ManagementAuthority)(context,descriptor)->authorized.get()?CompletableFuture.completedStage(null):CompletableFuture.failedStage(new SecurityException("测试授权已撤销。")));}
        /** @return 当前可信全局上下文 */
        ManagementContext context(){return new ManagementContext("测试管理员","global",UUID.randomUUID(),control);}
        /**
         * @param descriptor 明确资源
         * @param request 固定请求
         * @return 真实网关结果
         */
        Object call(ManagementResource.Descriptor descriptor,Object request){return gateway.execute(descriptor.key(),context(),request).toCompletableFuture().join();}
        /**
         * @param id 原 ID
         * @return 当前目录条目
         */
        Stickers.Entry entry(String id){return h.state().getEntries().values().stream().filter(e->e.getId().equals(id)).findFirst().orElseThrow();}
        /**
         * @param id 原 ID
         * @param action 明确动作
         * @return 带当前版本的请求
         */
        StickerManagement.Change request(String id,StickerManagement.Action action){return new StickerManagement.Change(UUID.randomUUID(),id,entry(id).getRevision(),action,null,action==StickerManagement.Action.REFRESH?refreshScope:null);}
        /**
         * @param request 完整请求
         * @return 已提交结果
         */
        StickerManagement.ChangeResult change(StickerManagement.Change request){return (StickerManagement.ChangeResult)call(StickerManagementResources.CHANGE,request);}
        /**
         * @param id 后台作业
         * @return 清理完成的实际终态
         */
        Stickers.Job settled(UUID id){await().atMost(Duration.ofSeconds(8)).pollInterval(Duration.ofMillis(25)).until(()->{h.service.tick();var j=h.state().getJobs().get(id);return j!=null&&j.getStatus()!=Stickers.JobStatus.QUEUED&&j.getStatus()!=Stickers.JobStatus.RUNNING&&j.isCleanup();});return h.state().getJobs().get(id);}
        /** 关闭本测试拥有的控制与原夹具。 */
        @Override public void close(){control.close();h.close();}
    }
    /**
     * 默认拒绝、全局范围、真实预览、CAS、持久幂等与事务回滚走同一管理入口。
     * @throws Exception 当图像编码失败时
     */
    @Test void authorizesAndCommitsVersionedIdempotentChanges()throws Exception{
        try(var f=new Fixture(directory)){
            var query=new StickerManagement.Query(StickerManagement.QueryKind.CATALOG,"",10,"",null);
            assertThrows(CompletionException.class,()->f.call(StickerManagementResources.QUERY,query));f.authorize();
            var seed=f.h.collect(source("群甲",png(31)));var id=seed.getStickerId();var e=f.entry(id);
            var page=(StickerManagement.Page)f.call(StickerManagementResources.QUERY,query);assertEquals(1,page.capacity().active());assertEquals(id,page.items().getFirst().id());
            var preview=(StickerManagement.PreviewView)f.call(StickerManagementResources.PREVIEW,new StickerManagement.Preview(id,e.getRevision()));assertTrue(preview.image().startsWith("data:image/png;base64,"));assertTrue(f.entry(id).getReaders().isEmpty());
            var pin=f.request(id,StickerManagement.Action.PIN);var result=f.change(pin);assertTrue(f.entry(id).isPinned());int notices=f.observer.notices.size();
            assertTrue(f.change(pin).replayed());assertEquals(notices,f.observer.notices.size());
            assertEquals(result.revision(),((StickerManagement.ChangeResult)f.call(StickerManagementResources.OPERATION,new StickerManagement.OperationQuery(pin.operationId()))).revision());
            assertThrows(CompletionException.class,()->f.change(new StickerManagement.Change(pin.operationId(),id,result.revision(),StickerManagement.Action.DISABLE,null,null)));
            assertThrows(CompletionException.class,()->f.change(new StickerManagement.Change(UUID.randomUUID(),id,pin.expectedRevision(),StickerManagement.Action.DISABLE,null,null)));
            var disable=f.request(id,StickerManagement.Action.DISABLE);f.h.db.failingWritePrefix.set("catalog:");
            assertThrows(CompletionException.class,()->f.change(disable));f.h.db.failingWritePrefix.set(null);
            assertEquals(Stickers.EntryStatus.ACTIVE,f.entry(id).getState());assertFalse(f.h.state().getOperations().containsKey(disable.operationId()));assertEquals(notices,f.observer.notices.size());
            f.authorized.set(false);assertThrows(CompletionException.class,()->f.change(disable));f.authorized.set(true);
            var wrongScope=new ManagementContext("测试管理员","某个群",UUID.randomUUID(),f.control);
            assertThrows(CompletionException.class,()->f.gateway.execute(StickerManagementResources.CHANGE.key(),wrongScope,disable).toCompletableFuture().join());
        }
    }
    /**
     * 删除墓碑阻止同图自动复活，已有发送占用阻止回收，显式恢复后才允许重新采集。
     * @throws Exception 当本地图像处理失败时
     */
    @Test void deletionRespectsReadersAndRequiresExplicitRestore()throws Exception{
        try(var f=new Fixture(directory)){
            f.authorize();var image=png(32);var id=f.h.collect(source("群甲",image)).getStickerId();var lease=UUID.randomUUID();
            f.h.catalog.change(f.h.file.snapshot(),s->{s.getEntries().values().stream().filter(e->e.getId().equals(id)).findFirst().orElseThrow().getReaders().put(lease,Instant.now().plusSeconds(600));return true;}).toCompletableFuture().join();
            f.change(f.request(id,StickerManagement.Action.DELETE));var settings=f.h.file.snapshot();var ports=f.h.registry.select(settings);
            assertTrue(f.h.library.garbage(settings,ports).toCompletableFuture().join().isEmpty());
            assertThrows(CompletionException.class,()->f.call(StickerManagementResources.PREVIEW,new StickerManagement.Preview(id,f.entry(id).getRevision())));
            var blocked=f.h.collect(source("群乙",image));assertEquals("MANAGED_BLOCKED",blocked.getReason());assertEquals(1,f.h.analyzer.calls.get());
            f.h.catalog.change(settings,s->{s.getEntries().values().stream().filter(e->e.getId().equals(id)).findFirst().orElseThrow().getReaders().remove(lease);return true;}).toCompletableFuture().join();
            var staleRestore=f.request(id,StickerManagement.Action.RESTORE);var blobs=f.h.library.garbage(settings,ports).toCompletableFuture().join();assertEquals(1,blobs.size());assertThrows(CompletionException.class,()->f.change(f.request(id,StickerManagement.Action.RESTORE)));
            ports.assets().delete(blobs.getFirst(),settings);f.h.library.deleted(settings,ports,blobs.getFirst()).toCompletableFuture().join();assertEquals(Stickers.EntryStatus.DELETED,f.entry(id).getState());assertNull(f.entry(id).getBlob());
            assertThrows(CompletionException.class,()->f.change(staleRestore));f.change(f.request(id,StickerManagement.Action.RESTORE));assertEquals(Stickers.EntryStatus.CACHED,f.entry(id).getState());
            var recollected=f.h.collect(source("群丙",image));assertEquals(Stickers.JobStatus.SAVED,recollected.getStatus());assertEquals(id,recollected.getStickerId());
        }
    }
    /**
     * Core 事件可拒绝与建议但不可改来源；人工修正赢过迟到模型，停用项刷新仍需显式启用。
     * @throws Exception 当本地图像处理失败时
     */
    @Test void eventsFreezeAndRefreshCannotOverwriteManualCorrections()throws Exception{
        try(var f=new Fixture(directory)){
            f.authorize();var id=f.h.collect(source("群甲",png(33))).getStickerId();assertFalse(f.observer.notices.isEmpty());
            var candidate=Objects.requireNonNull(f.observer.candidate);assertThrows(IllegalStateException.class,()->candidate.suggest("迟到建议"));assertThrows(UnsupportedOperationException.class,()->candidate.setData(candidate.getData()));
            f.h.analyzer.pending=new CompletableFuture<>();var refresh=f.change(f.request(id,StickerManagement.Action.REFRESH));
            await().atMost(Duration.ofSeconds(8)).until(()->{f.h.service.tick();return f.h.analyzer.calls.get()==2;});
            var patch=new StickerManagement.Change(UUID.randomUUID(),id,f.entry(id).getRevision(),StickerManagement.Action.PATCH,new StickerManagement.Details("happy","管理员确认的画面","管理员确认的用途",List.of("开心")),null);f.change(patch);
            f.h.analyzer.pending.complete(new Stickers.Analysis(Stickers.Kind.STICKER,.99,true,"迟到画面","迟到用途","happy",List.of(),.95,"测试"));
            assertEquals(Stickers.JobStatus.FAILED,f.settled(Objects.requireNonNull(refresh.jobId())).getStatus());assertEquals("管理员确认的用途",Objects.requireNonNull(f.entry(id).getAnalysis()).usageDescription());assertEquals(Stickers.EntryStatus.DISABLED,f.entry(id).getState());assertTrue(f.entry(id).getReaders().isEmpty());
            f.h.analyzer.pending=null;var humanBefore=f.entry(id).getHumanDays();var second=f.change(f.request(id,StickerManagement.Action.REFRESH));var completed=f.settled(Objects.requireNonNull(second.jobId()));
            assertEquals(Stickers.JobStatus.SAVED,completed.getStatus());assertEquals("REFRESHED_DISABLED",completed.getReason());assertEquals(Stickers.EntryStatus.DISABLED,f.entry(id).getState());assertEquals(humanBefore,f.entry(id).getHumanDays());assertTrue(f.h.state().getRevision()>second.catalogRevision());
            f.change(f.request(id,StickerManagement.Action.ENABLE));assertEquals(Stickers.EntryStatus.ACTIVE,f.entry(id).getState());
            f.observer.veto=true;int jobs=f.h.state().getJobs().size();var rejected=f.h.library.offer(source("群丁",png(34))).toCompletableFuture().join();assertEquals("EVENT_REJECTED",rejected.reason());assertEquals(jobs,f.h.state().getJobs().size());assertTrue(Objects.requireNonNull(f.observer.candidate).isCancelled());
            f.h.file.setEventPlugins(List.of());var ignored=f.h.library.offer(source("群戊",png(35))).toCompletableFuture().join();assertNotNull(ignored.jobId());
        }
    }
}
