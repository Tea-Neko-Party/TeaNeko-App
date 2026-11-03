package org.zexnocs.teanekoagent.sticker;

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.zexnocs.teanekoagent.agent.AgentScope;
import org.zexnocs.teanekoagent.file_config.AgentStickerFileConfig;
import org.zexnocs.teanekoapp.client.api.ITeaNekoClient;
import org.zexnocs.teanekocore.file_config.interfaces.IFileConfigService;
import org.zexnocs.teanekocore.plugin.PluginRegistry;
import org.zexnocs.teanekocore.plugin.management.ManagementAuthority;
import org.zexnocs.teanekocore.plugin.management.ManagementContext;
import org.zexnocs.teanekocore.plugin.management.ManagementResource;
import org.zexnocs.teanekocore.utils.StableDigest;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IBeanScanner;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/**
 * 全实例图库的授权管理用例，版本写入和幂等回执共用专用 DTO 根事务。
 * 没有显式授权实现时默认拒绝；Web 身份不转为聊天 USER，刷新始终通过 SYSTEM 视觉活动。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.0.0
 * @version 4.6.2-l
 */
@Component
public final class StickerManagementService {
    private final StickerExtensionRegistry registry;
    private final StickerLibrary library;
    private final IFileConfigService files;
    private final ObjectProvider<ManagementAuthority> authorities;
    private final ObjectProvider<IStickerLifecycle> lifecycle;
    private final PluginRegistry plugins;
    private final IBeanScanner scanner;
    private final tools.jackson.databind.ObjectMapper mapper;
    private final Executor executor;
    private final Semaphore previews=new Semaphore(2,true);
    /**
     * 构造只保存设施，授权与文件均在请求阶段读取。
     * @param registry 原存储选择
     * @param library 原采集资格
     * @param files 本地配置
     * @param authorities 唯一显式授权
     * @param lifecycle 可选通知
     * @param plugins 原插件代次
     * @param scanner 当前真实客户端
     * @param mapper 原转换器
     * @param executor 借用业务域
     */
    public StickerManagementService(StickerExtensionRegistry registry,StickerLibrary library,IFileConfigService files,ObjectProvider<ManagementAuthority> authorities,
            ObjectProvider<IStickerLifecycle> lifecycle,PluginRegistry plugins,IBeanScanner scanner,tools.jackson.databind.ObjectMapper mapper,@Qualifier("virtualExecutor") Executor executor){
        this.registry=registry;this.library=library;this.files=files;this.authorities=authorities;this.lifecycle=lifecycle;this.plugins=plugins;this.scanner=scanner;this.mapper=mapper;this.executor=executor;
    }
    /**
     * @param context 已认证全局身份
     * @param query 有界过滤
     * @return 同快照分页及容量
     */
    public CompletionStage<StickerManagement.Page> query(ManagementContext context,StickerManagement.Query query){
        return begin(context,StickerManagementResources.QUERY).thenCompose(s->finish(context,s,StickerManagementResources.QUERY,s.storage().catalog().change(s.settings(),state->{
            verify(context,s);var active=state.getEntries().values().stream().filter(e->e.getState()==Stickers.EntryStatus.ACTIVE).toList();var categories=new TreeMap<String,Long>();
            s.settings().categories().keySet().forEach(c->categories.put(c,active.stream().filter(e->e.getAnalysis()!=null&&e.getAnalysis().primaryCategory().equals(c)).count()));
            long bytes=state.getEntries().values().stream().map(Stickers.Entry::getBlob).filter(Objects::nonNull).mapToLong(Stickers.Blob::bytes).sum()+state.getJobs().values().stream().mapToLong(Stickers.Job::getReserved).sum();
            var capacity=new StickerManagement.Capacity(active.size(),(int)state.getEntries().values().stream().filter(e->e.getState()==Stickers.EntryStatus.DISABLED).count(),(int)state.getJobs().values().stream().filter(StickerManagementService::pending).count(),s.settings().maxActive(),bytes,s.settings().maxStorageBytes(),categories);
            if(query.kind()==StickerManagement.QueryKind.JOBS){
                var jobs=state.getJobs().values().stream().filter(j->j.getId().toString().compareTo(query.after())>0).sorted(Comparator.comparing(j->j.getId().toString())).limit(query.limit()+1L).toList();
                var page=jobs.stream().limit(query.limit()).map(j->new StickerManagement.JobView(j.getId(),j.getStatus(),j.getReason(),j.getStickerId(),j.getSource().scope(),j.isRefresh(),j.getCreated())).toList();
                return new StickerManagement.Page(state.getRevision(),List.of(),page,jobs.size()>query.limit()?page.getLast().id().toString():"",capacity);
            }
            var entries=state.getEntries().values().stream().filter(e->e.getId().compareTo(query.after())>0&&(query.state()==null||e.getState()==query.state())
                    &&(query.category().isEmpty()||e.getAnalysis()!=null&&e.getAnalysis().primaryCategory().equals(query.category())))
                    .sorted(Comparator.comparing(Stickers.Entry::getId)).limit(query.limit()+1L).toList();
            var page=entries.stream().limit(query.limit()).map(StickerManagementService::view).toList();
            return new StickerManagement.Page(state.getRevision(),page,List.of(),entries.size()>query.limit()?page.getLast().id():"",capacity);
        })));
    }
    /**
     * @param context 全局已认证主体
     * @param query 原操作 ID
     * @return 本人尚在保留期内的真实回执
     */
    public CompletionStage<StickerManagement.ChangeResult> operation(ManagementContext context,StickerManagement.OperationQuery query){
        return begin(context,StickerManagementResources.OPERATION).thenCompose(s->finish(context,s,StickerManagementResources.OPERATION,s.storage().catalog().change(s.settings(),state->{
            verify(context,s);var op=state.getOperations().get(query.operationId());if(op==null||!op.subject().equals(subject(context))||op.at().plus(Duration.ofHours(24)).isBefore(Instant.now())){throw new IllegalArgumentException("管理操作回执不存在、已过期或不属于当前主体。");}return replay(op.result());
        })));
    }
    /**
     * 根据明确类型写入，幂等重放不覆盖当前条目，也不重复发布提交事件。
     * @param context 真实全局授权
     * @param request 固定动作和版本
     * @return 实际提交的回执
     */
    public CompletionStage<StickerManagement.ChangeResult> change(ManagementContext context,StickerManagement.Change request){
        String digest=StableDigest.sha256(mapper.writeValueAsString(request));
        return begin(context,StickerManagementResources.CHANGE).thenComposeAsync(s->s.storage().catalog().change(s.settings(),state->{
            verify(context,s);var previous=original(state,context,request,digest);if(previous!=null){return new Preparation(previous,null);}
            var entry=entry(state,request.id(),request.expectedRevision());return new Preparation(null,entry.getBlob());
        }).thenComposeAsync(prepared->{
            if(prepared.previous()!=null){return finish(context,s,StickerManagementResources.CHANGE,CompletableFuture.completedStage(replay(prepared.previous())));}
            Stickers.Source source=null;
            if(request.action()==StickerManagement.Action.REFRESH){source=refreshSource(Objects.requireNonNull(request.analysisScope()),request.operationId());if(!library.allows(source)){throw new IllegalStateException("所选区域尚未允许素材分析或客户端账号未就绪。");}}
            String volume="";
            if(request.action()==StickerManagement.Action.REFRESH||request.action()==StickerManagement.Action.ENABLE){
                var blob=prepared.blob();if(blob==null||!s.storage().assets().exists(blob,s.settings())){throw new IllegalStateException("现有素材文件不可用，不能启用或重新分析。");}
                volume=verifyVolume(s);
            }
            var fixedSource=source;var expectedVolume=volume;
            return authorize(context,StickerManagementResources.CHANGE).thenCompose(ignored->s.storage().catalog().change(s.settings(),state->{
                verify(context,s);var previous=original(state,context,request,digest);if(previous!=null){return replay(previous);}
                var now=Instant.now();state.getOperations().values().removeIf(op->op.at().plus(Duration.ofHours(24)).isBefore(now));
                if(state.getOperations().size()>=512){throw new IllegalStateException("管理操作回执窗口已满，请等待过期记录回收。");}
                var e=entry(state,request.id(),request.expectedRevision());if(e.isDeleting()||e.getState()==Stickers.EntryStatus.DELETING){throw new IllegalStateException("素材字节正在回收，请稍后按最新版本操作。");}
                if(!expectedVolume.isEmpty()&&!state.getStorageIdentity().equals(expectedVolume)){throw new IllegalStateException("目录与实际素材卷不一致。");}
                UUID jobId=null;switch(request.action()){
                    case PIN -> {existing(e);e.setPinned(true);}
                    case UNPIN -> e.setPinned(false);
                    case DISABLE -> {existing(e);e.setState(Stickers.EntryStatus.DISABLED);}
                    case ENABLE -> {if(e.getState()!=Stickers.EntryStatus.DISABLED){throw new IllegalStateException("只可启用已人工停用的素材。");}eligible(e,s.settings());capacity(state,e,s.settings());e.setState(Stickers.EntryStatus.ACTIVE);e.setActivatedAt(now);}
                    case DELETE -> {e.setState(Stickers.EntryStatus.DELETED);e.setPinned(false);}
                    case RESTORE -> {if(e.getState()!=Stickers.EntryStatus.DELETED){throw new IllegalStateException("只有删除墓碑可以显式恢复。");}e.setState(e.getBlob()==null?Stickers.EntryStatus.CACHED:Stickers.EntryStatus.DISABLED);e.setCachedUntil(now);}
                    case PATCH -> {existing(e);var old=Objects.requireNonNull(e.getAnalysis());var details=Objects.requireNonNull(request.details());if(old.kind()!=Stickers.Kind.STICKER||!s.settings().categories().containsKey(details.category())){throw new IllegalArgumentException("只能修正表情包的受控类别和描述。");}
                        if(e.getState()==Stickers.EntryStatus.ACTIVE){capacityForCategory(state,e,details.category(),s.settings());}
                        e.setAnalysis(new Stickers.Analysis(old.kind(),old.confidence(),true,details.visualDescription(),details.usageDescription(),details.category(),details.tags(),old.quality(),"管理员修正用途与类别"));}
                    case REFRESH -> {existing(e);if(!library.allows(Objects.requireNonNull(fixedSource))){throw new IllegalStateException("重新分析来源已失效。");}
                        if(state.getJobs().values().stream().filter(j->pending(j)||j.isRefresh()&&!j.isCleanup()).count()>=s.settings().maxPending()||state.getJobs().size()>=384){throw new IllegalStateException("素材后台作业已满。");}
                        if(state.getJobs().values().stream().anyMatch(j->pending(j)&&j.getBlob()!=null&&j.getBlob().hash().equals(e.getHash()))){throw new IllegalStateException("该素材已有在途任务。");}
                        var job=new Stickers.Job();job.setSource(fixedSource);job.setCreated(now);job.setNextAttempt(now);job.setRefresh(true);job.setBlob(e.getBlob());job.setExpectedEntryRevision(e.getRevision()+1);job.setActivateAfterRefresh(e.getState()==Stickers.EntryStatus.ACTIVE);
                        e.getReaders().put(job.getId(),now.plus(Duration.ofHours(24)).plusSeconds(300));state.getJobs().put(job.getId(),job);jobId=job.getId();e.setState(Stickers.EntryStatus.DISABLED);
                    }
                }
                e.setRevision(e.getRevision()+1);state.setRevision(state.getRevision()+1);
                var result=new StickerManagement.ChangeResult(request.operationId(),e.getId(),e.getRevision(),e.getState(),state.getRevision(),jobId,false);
                state.getOperations().put(request.operationId(),new StickerManagement.Operation(subject(context),digest,now,result));return result;
            })).thenApply(result->{
                if(!result.replayed()){notifyCommitted(new IStickerLifecycle.Notice(s.settings().catalogId(),"MANAGEMENT",result.operationId(),result.id(),result.catalogRevision(),Instant.now(),request.action().name()));}return result;
            }).thenCompose(result->finish(context,s,StickerManagementResources.CHANGE,CompletableFuture.completedStage(result)));
        },executor),executor);
    }
    /**
     * 持占用读取一幅合成 PNG，文件/版本或权限变化后不返回原图数据。
     * @param context 全局预览权限
     * @param query 原条目版本
     * @return 有界预览
     */
    public CompletionStage<StickerManagement.PreviewView> preview(ManagementContext context,StickerManagement.Preview query){
        return begin(context,StickerManagementResources.PREVIEW).thenComposeAsync(s->{
            acquirePreview(context);
            CompletionStage<StickerManagement.PreviewView> work;
            try{String volume=verifyVolume(s);work=s.storage().catalog().change(s.settings(),state->{
                verify(context,s);var e=entry(state,query.id(),query.expectedRevision());existing(e);if(e.isDeleting()||!state.getStorageIdentity().equals(volume)){throw new IllegalStateException("素材卷或文件状态无效。");}
                e.getReaders().values().removeIf(t->!t.isAfter(Instant.now()));if(e.getReaders().size()>=32){throw new IllegalStateException("素材预览占用已满。");}var token=UUID.randomUUID();e.getReaders().put(token,Instant.now().plusSeconds(600));return new PreviewLease(s,token,Objects.requireNonNull(e.getBlob()));
            }).thenCompose(lease->{
                CompletionStage<StickerManagement.PreviewView> reading;
                try{reading=CompletableFuture.supplyAsync(()->{
                    verify(context,s);var prepared=s.storage().assets().preview(lease.blob(),s.settings(),context.control());var image=prepared.previews().getFirst();if(!image.startsWith("data:image/png;base64,")||image.length()>1572864){throw new IllegalStateException("素材预览超过公开预算。");}
                    return new StickerManagement.PreviewView(query.id(),query.expectedRevision(),image,prepared.note());
                },executor).thenCompose(value->authorize(context,StickerManagementResources.PREVIEW).thenCompose(ignored->s.storage().catalog().change(s.settings(),state->{verify(context,s);var e=entry(state,query.id(),query.expectedRevision());existing(e);return value;})));}
                catch(RuntimeException failure){reading=CompletableFuture.failedStage(failure);}
                return releasePreview(lease,reading);
            });}catch(RuntimeException failure){work=CompletableFuture.failedStage(failure);}
            return work.whenComplete((ignored,failure)->previews.release());
        },executor);
    }
    /**
     * @param lease 原文件占用
     * @param physical 实际读取
     * @param <T> 输出
     * @return 实际读取及无条件释放
     */
    private <T> CompletionStage<T> releasePreview(PreviewLease lease,CompletionStage<T> physical){
        return physical.handle(PreviewOutcome::new).thenCompose(outcome->lease.session().storage().catalog().change(lease.session().settings(), state->{var e=state.getEntries().get(lease.blob().hash());if(e!=null){e.getReaders().remove(lease.token());}return true;})
                .handle((ignored,cleanup)->{var failure=outcome.failure();if(failure!=null){if(cleanup!=null){failure.addSuppressed(cleanup);}throw new CompletionException(failure);}if(cleanup!=null){throw new CompletionException(cleanup);}return outcome.value();}));
    }
    /** @param context 原控制 */
    private void acquirePreview(ManagementContext context){try{while(true){context.control().checkActive();if(previews.tryAcquire(100,TimeUnit.MILLISECONDS)){try{context.control().checkActive();return;}catch(RuntimeException failure){previews.release();throw failure;}}}}catch(InterruptedException failure){Thread.currentThread().interrupt();throw new IllegalStateException("管理预览等待已中断。");}}
    /**
     * @param context 原主体
     * @param descriptor 明确权限
     * @return 授权后才读取配置的会话
     */
    private CompletionStage<Session> begin(ManagementContext context,ManagementResource.Descriptor descriptor){return authorize(context,descriptor).thenApplyAsync(ignored->{
        var token=plugins.capture("teaneko.agent");var settings=files.requireCurrent(AgentStickerFileConfig.class).snapshot();return new Session(settings,registry.storage(settings),token);
    },executor);}
    /**
     * @param context 已认证主体
     * @param descriptor 固定资源
     * @return 当前授权信号
     */
    private CompletionStage<Void> authorize(ManagementContext context,ManagementResource.Descriptor descriptor){
        context.control().checkActive();if(!context.scopeRef().equals("global")){throw new IllegalArgumentException("共享素材管理要求明确的全局授权范围。");}
        var authority=authorities.getIfAvailable();if(authority==null){throw new IllegalStateException("尚未安装可信管理授权适配器。");}return authority.authorize(context,descriptor).thenRun(context.control()::checkActive);
    }
    /**
     * @param context 原控制
     * @param s 原会话
     */
    private void verify(ManagementContext context,Session s){context.control().checkActive();plugins.check(s.token());registry.verifyStorage(s.storage());if(!s.settings().equals(files.requireCurrent(AgentStickerFileConfig.class).snapshot())){throw new IllegalStateException("素材管理配置已经变化，请重新读取。");}}
    /**
     * @param context 原主体
     * @param s 原选择
     * @param descriptor 权限
     * @param stage 实际事务
     * @param <T> 输出类型
     * @return 返回前再次授权
     */
    private <T> CompletionStage<T> finish(ManagementContext context,Session s,ManagementResource.Descriptor descriptor,CompletionStage<T> stage){return stage.thenCompose(value->authorize(context,descriptor).thenApply(ignored->{verify(context,s);return value;}));}
    /**
     * @param s 原会话
     * @return 实际素材卷身份，仅在事务外读取
     */
    private String verifyVolume(Session s){String identity=s.storage().assets().identity(s.settings());if(identity.isBlank()){throw new IllegalStateException("素材卷身份无效。");}return identity;}
    /**
     * @param state 原目录
     * @param context 原主体
     * @param request 原请求
     * @param digest 请求摘要
     * @return 原幂等回执或空
     */
    private StickerManagement.@Nullable ChangeResult original(Stickers.State state,ManagementContext context,StickerManagement.Change request,String digest){
        var op=state.getOperations().get(request.operationId());if(op==null||op.at().plus(Duration.ofHours(24)).isBefore(Instant.now())){return null;}
        if(!op.subject().equals(subject(context))||!op.digest().equals(digest)){throw new IllegalArgumentException("管理操作 ID 已绑定其他主体或参数。");}return op.result();
    }
    /**
     * @param context 管理主体
     * @return 仅用于回执归属的摘要
     */
    private static String subject(ManagementContext context){return StableDigest.sha256(context.subject(),context.scopeRef());}
    /**
     * @param state 原目录
     * @param id ID
     * @param revision 原版本
     * @return 固定当前版本条目
     */
    private static Stickers.Entry entry(Stickers.State state,String id,long revision){var e=state.getEntries().values().stream().filter(v->v.getId().equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("素材不存在。"));if(e.getRevision()!=revision){throw new IllegalStateException("素材版本已变化，请读取最新版本。");}return e;}
    /** @param e 原条目 */
    private static void existing(Stickers.Entry e){if(e.getBlob()==null||!Set.of(Stickers.EntryStatus.ACTIVE,Stickers.EntryStatus.DISABLED).contains(e.getState())){throw new IllegalStateException("此操作只适用于有文件的有效或人工停用素材。");}}
    /**
     * @param e 待启用条目
     * @param settings 质量阈值
     */
    private static void eligible(Stickers.Entry e,StickerSettings settings){var a=e.getAnalysis();if(a==null||a.kind()!=Stickers.Kind.STICKER||a.confidence()<settings.minConfidence()||a.quality()<settings.minQuality()||!a.worthKeeping()||!settings.categories().containsKey(a.primaryCategory())){throw new IllegalStateException("该素材未通过当前类型、质量或类别门槛。");}}
    /**
     * @param state 原目录
     * @param e 原项
     * @param settings 当前容量
     */
    private static void capacity(Stickers.State state,Stickers.Entry e,StickerSettings settings){if(state.getEntries().values().stream().filter(v->v.getState()==Stickers.EntryStatus.ACTIVE).count()>=settings.maxActive()){throw new IllegalStateException("有效素材总量已满。");}capacityForCategory(state,e,Objects.requireNonNull(e.getAnalysis()).primaryCategory(),settings);}
    /**
     * @param state 原目录
     * @param e 原项
     * @param category 新类别
     * @param settings 当前容量
     */
    private static void capacityForCategory(Stickers.State state,Stickers.Entry e,String category,StickerSettings settings){if(state.getEntries().values().stream().filter(v->v!=e&&v.getState()==Stickers.EntryStatus.ACTIVE&&v.getAnalysis()!=null&&v.getAnalysis().primaryCategory().equals(category)).count()>=settings.maxPerCategory()){throw new IllegalStateException("目标类别容量已满。");}}
    /**
     * @param scope 明确分析区域
     * @param operation 原操作
     * @return 不携带用户权限的真实区域定位
     */
    private Stickers.Source refreshSource(AgentScope scope,UUID operation){var clients=scanner.getBeansOfType(ITeaNekoClient.class).values().stream().filter(c->c.getClientId().equals(scope.clientId())).toList();if(clients.size()!=1){throw new IllegalArgumentException("重新分析区域没有唯一真实客户端。");}var self=clients.getFirst().getTeaNekoToolbox().getSelfPlatformId().orElseThrow(()->new IllegalStateException("重新分析客户端尚未就绪。"));var system=new UUID(0,0);return new Stickers.Source(scope,system,"",self,"refresh-"+operation,0,Instant.now(),true,system,"");}
    /**
     * @param job 后台任务
     * @return 是否尚未形成终态
     */
    private static boolean pending(Stickers.Job job){return job.getStatus()==Stickers.JobStatus.QUEUED||job.getStatus()==Stickers.JobStatus.RUNNING;}
    /**
     * @param e 原条目
     * @return 脱敏明确视图
     */
    private static StickerManagement.ItemView view(Stickers.Entry e){var a=e.getAnalysis();var b=e.getBlob();return new StickerManagement.ItemView(e.getId(),e.getRevision(),e.getState(),e.isPinned(),a==null?"":a.primaryCategory(),a==null?"":a.visualDescription(),a==null?"":a.usageDescription(),a==null?0:a.quality(),b==null?0:b.bytes(),b==null?0:b.width(),b==null?0:b.height(),b==null?0:b.frames(),e.getHumanDays().values().stream().mapToLong(Integer::longValue).sum(),e.getRobotShows(),e.getLastHuman(),e.getLastRobot());}
    /**
     * @param original 原真实回执
     * @return 标记为幂等重放的回执
     */
    private static StickerManagement.ChangeResult replay(StickerManagement.ChangeResult original){return new StickerManagement.ChangeResult(original.operationId(),original.id(),original.revision(),original.state(),original.catalogRevision(),original.jobId(),true);}
    /** @param notice 真实提交结果 */
    private void notifyCommitted(IStickerLifecycle.Notice notice){try{var observer=lifecycle.getIfAvailable();if(observer!=null){observer.committed(notice);}}catch(RuntimeException ignored){/* 通知不改变真实提交。 */}}
    /**
     * 临时管理选择，不导出服务或权限能力。
     * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
     *
     * @param settings 原设置
     * @param storage 原存储
     * @param token 原宿主插件版本
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.0.0
     * @version 4.6.2-l
     */
    @JsonIgnoreType private record Session(StickerSettings settings,StickerExtensionRegistry.Storage storage,PluginRegistry.Token token){}
    /**
     * 写入前观察，不提前返回未提交结果。
     * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
     *
     * @param previous 已提交幂等回执
     * @param blob 当前文件
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.0.0
     * @version 4.6.2-l
     */
    @JsonIgnoreType private record Preparation(StickerManagement.@Nullable ChangeResult previous,Stickers.@Nullable Blob blob){}
    /**
     * 预览真实占用，仅由宿主释放。
     * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
     *
     * @param session 原端口
     * @param token 占用 ID
     * @param blob 原文件
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.0.0
     * @version 4.6.2-l
     */
    @JsonIgnoreType private record PreviewLease(Session session,UUID token,Stickers.Blob blob){}
    /**
     * 保留物理阶段结果以完成资源清理。
     * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
     *
     * @param value 实际值
     * @param failure 原失败
     * @param <T> 结果类型
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.0.0
     * @version 4.6.2-l
     */
    private record PreviewOutcome<T>(@Nullable T value,@Nullable Throwable failure){}
}
