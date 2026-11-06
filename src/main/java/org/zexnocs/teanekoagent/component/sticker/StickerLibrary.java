package org.zexnocs.teanekoagent.component.sticker;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.utils.StableDigest;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * 宿主管理候选、指纹缓存、实际人类统计和容量仲裁；文件 I/O 不进入目录事务。
 * 每个实例只有一个持久后台租约，迟到结果不能越过原代次激活。
 *
 * <br>2026/11/03：采集决策、管理墓碑、后台刷新及真实提交观察共用原事务，分析和字节回收推进公开版本。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/05：已回收文件的删除决定与分析缓存独立计量。
 * <br>2026/11/05：下载后按指纹复核独立删除记录，禁止重复采集恢复人工拒绝。
 * <br>2026/11/06：维护清理与存储分流使用同一作业恢复条件。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.6.2-l
 * @version 4.8.5-l
 */
public final class StickerLibrary {
    private final StickerExtensionRegistry registry;
    private final Supplier<StickerSettings> settings;
    private final Predicate<Stickers.Source> allowed;
    private final Clock clock;
    private final @Nullable IStickerLifecycle lifecycle;
    /**
     * @param registry 原扩展目录
     * @param settings 本地快照
     * @param allowed 真实来源复核
     * @param clock 时间来源
     */
    public StickerLibrary(StickerExtensionRegistry registry,Supplier<StickerSettings> settings,Predicate<Stickers.Source> allowed,Clock clock){this(registry,settings,allowed,clock,null);}
    /**
     * 2026/11/03：默认宿主接入生命周期，独立旧宿主保留原构造。
     * @param registry 原扩展
     * @param settings 配置
     * @param allowed 真实来源
     * @param clock 时钟
     * @param lifecycle 可选受限事件
     */
    public StickerLibrary(StickerExtensionRegistry registry,Supplier<StickerSettings> settings,Predicate<Stickers.Source> allowed,Clock clock,@Nullable IStickerLifecycle lifecycle){this.registry=registry;this.settings=settings;this.allowed=allowed;this.clock=clock;this.lifecycle=lifecycle;}
    /** @return 当前不可变配置 */
    public StickerSettings settings(){return settings.get();}
    /**
     * @param source 原来源
     * @return 当前可采集时为真
     */
    public boolean allows(Stickers.Source source){try{var config=settings.get();return config.enabled()&&config.collectEnabled()&&(source.manual()||config.passiveEnabled())&&allowed.test(source);}catch(RuntimeException invalid){return false;}}
    /**
     * 只登记有界短期引用并预留最坏文件容量，普通聊天不等待下载或视觉任务。
     * @param source 宿主固定消息
     * @return 持久作业回执；拒绝的候选没有作业 ID
     */
    public CompletionStage<Stickers.Receipt> offer(Stickers.Source source){
        var config=settings();var ports=registry.select(config);var now=clock.instant();validateSource(source,now);check(config,ports,source,null);
        // 2026/11/03：没有锁跨事件等待；监听结果不能替代当前来源、预算和事务检查。
        var deciding=lifecycle==null?CompletableFuture.completedStage(new IStickerLifecycle.Decision(true,"")):lifecycle.before(source,config);
        return deciding.thenCompose(decision->{
        if(!decision.allowed()){return CompletableFuture.completedStage(new Stickers.Receipt(null,Stickers.JobStatus.REJECTED,"EVENT_REJECTED",""));}
        if(decision.hint()==null||decision.hint().length()>256){throw new IllegalArgumentException("素材识别建议超过边界。");}
        return ports.catalog().change(config,state->{
            check(config,ports,source,null);prune(state,now);var receipt=sourceKey(source);
            if(state.getReceipts().containsKey(receipt)){
                return state.getJobs().values().stream().filter(j->sourceKey(j.getSource()).equals(receipt)).findFirst().map(StickerLibrary::receipt)
                        .orElseGet(()->new Stickers.Receipt(null,Stickers.JobStatus.DUPLICATE,"SOURCE_SEEN",""));
            }
            if(state.getJobs().values().stream().filter(j->pending(j)||needsCleanup(j)).count()>=config.maxPending()
                    ||state.getJobs().values().stream().mapToLong(Stickers.Job::getReserved).sum()+config.maxFileBytes()>config.maxPendingBytes()
                    ||bytes(state)+config.maxFileBytes()>config.maxStorageBytes()||state.getReceipts().size()>=8192||state.getJobs().size()>=384){return new Stickers.Receipt(null,Stickers.JobStatus.REJECTED,"CAPACITY","");}
            var global=quota(state,"global",now);var scope=quota(state,scopeKey(source),now);
            if(global.getDownloads()>=config.downloadsPerHour()||scope.getDownloads()>=config.scopeDownloadsPerHour()){return new Stickers.Receipt(null,Stickers.JobStatus.REJECTED,"DOWNLOAD_RATE","");}
            global.setDownloads(global.getDownloads()+1);scope.setDownloads(scope.getDownloads()+1);
            var job=new Stickers.Job();job.setSource(source);job.setCreated(now);job.setNextAttempt(now);job.setReserved(config.maxFileBytes());
            job.setHint(decision.hint());
            state.getReceipts().put(receipt,now);state.getJobs().put(job.getId(),job);return receipt(job);
        });});
    }
    /**
     * 选择清理或正常作业，过期在途任务标为不确定，不自动重发视觉请求。
     * @param config 本批配置
     * @param ports 固定扩展
     * @param identity 实际共享卷身份
     * @return 当前拥有的后台工作，空表示忙碌或无任务
     */
    public CompletionStage<Optional<Work>> claim(StickerSettings config,StickerExtensionRegistry.Selected ports,String identity){
        var now=clock.instant();return ports.catalog().change(config,state->{
            registry.verify(ports);if(!settings().equals(config)){return Optional.empty();}
            if(!state.getStorageIdentity().isEmpty()&&!state.getStorageIdentity().equals(identity)){throw new IllegalStateException("表情包目录与素材卷身份不一致；共享数据库须同时共享素材卷。");}
            state.setStorageIdentity(identity);validatePinned(state,config);
            if(state.getWorkerUntil().isAfter(now)){return Optional.empty();}
            for(var job:state.getJobs().values()){
                if(job.getStatus()==Stickers.JobStatus.RUNNING){terminal(job,Stickers.JobStatus.FAILED,"INTERRUPTED",now);}
                if(pending(job)&&(!config.enabled()||!config.collectEnabled())){terminal(job,Stickers.JobStatus.FAILED,"COLLECTION_DISABLED",now);}
                if(pending(job)&&job.getCreated().plus(Duration.ofHours(24)).isBefore(now)){terminal(job,Stickers.JobStatus.FAILED,"EXPIRED",now);}
            }
            prune(state,now);var jobs=state.getJobs().values();
            var selected=jobs.stream().filter(j->!pending(j)&&needsCleanup(j)).findFirst().orElse(null);
            boolean cleanup=selected!=null;
            if(selected==null){selected=jobs.stream().filter(j->j.getStatus()==Stickers.JobStatus.QUEUED&&!j.getNextAttempt().isAfter(now))
                    .min(Comparator.<Stickers.Job,Boolean>comparing(j->!j.getSource().manual()).thenComparing(Stickers.Job::getCreated)).orElse(null);}
            if(selected==null){return Optional.empty();}
            var owner=UUID.randomUUID();state.setWorker(owner);state.setWorkerUntil(now.plusSeconds(300));
            if(!cleanup){selected.setStatus(Stickers.JobStatus.RUNNING);selected.setLease(UUID.randomUUID());selected.setLeaseUntil(state.getWorkerUntil());}
            return Optional.of(new Work(owner,selected,cleanup));
        });
    }
    /**
     * @param config 本批配置
     * @param ports 扩展
     * @param work 原工作
     * @param delay 延后时长
     * @return 实际释放阶段
     */
    public CompletionStage<Boolean> defer(StickerSettings config,StickerExtensionRegistry.Selected ports,Work work,Duration delay){return ports.catalog().change(config,s->{var j=running(s,work);j.setStatus(Stickers.JobStatus.QUEUED);j.setNextAttempt(clock.instant().plus(delay));release(s);return true;});}
    /**
     * 下载后先持久化元数据，重复指纹不进入视觉调用；人类统计只在真实入站时计数。
     * @param config 配置
     * @param ports 扩展
     * @param work 原租约
     * @param blob 验证后的文件
     * @param control 当前控制
     * @return ANALYZE、CACHED 或终态原因
     */
    public CompletionStage<String> attach(StickerSettings config,StickerExtensionRegistry.Selected ports,Work work,Stickers.Blob blob,ExecutionControl control){
        // 2026/11/05：删除决定不再常驻主目录，仍在相同根事务内优先仲裁。
        return ports.catalog().transact(config,transaction->{
            var s = transaction.state();
            check(config,ports,work.job().getSource(),control);var job=running(s,work);job.setBlob(blob);
            if(blob.bytes()<1||blob.bytes()>job.getReserved()||!blob.hash().matches("[a-f0-9]{64}")){throw new IllegalArgumentException("素材文件超出预约或摘要无效。");}
            var deleted = transaction.deletedByHash(blob.hash());
            if (deleted.isPresent()) {
                job.setExpectedEntryRevision(deleted.get().getRevision());
                terminal(job, Stickers.JobStatus.REJECTED, "MANAGED_BLOCKED", clock.instant());
                return "DONE";
            }
            var entry=s.getEntries().get(blob.hash());var now=clock.instant();
            if(entry!=null){
                // 2026/11/03：下载后固定版本，后续人工修改不能被迟到分析覆盖。
                job.setExpectedEntryRevision(entry.getRevision());
                // 2026/11/03：人工停用与删除不因重复聊天自动恢复。
                if(Set.of(Stickers.EntryStatus.DISABLED,Stickers.EntryStatus.DELETED).contains(entry.getState())){terminal(job,Stickers.JobStatus.REJECTED,"MANAGED_BLOCKED",now);return "DONE";}
                observe(entry,job.getSource(),now);
                if((entry.getState()==Stickers.EntryStatus.ACTIVE)){job.setStickerId(entry.getId());terminal(job,Stickers.JobStatus.DUPLICATE,"CONTENT_SEEN",now);return "DONE";}
                if(Set.of(Stickers.EntryStatus.RETIRED,Stickers.EntryStatus.UNAVAILABLE,Stickers.EntryStatus.DELETING).contains(entry.getState())){terminal(job,Stickers.JobStatus.REJECTED,"FILE_RETIRING",now);return "DONE";}
                if(entry.getAnalysisVersion()==config.analysisVersion()&&entry.getCachedUntil().isAfter(now)){
                    if(entry.getAnalysis()==null){terminal(job,Stickers.JobStatus.REJECTED,"ANALYSIS_UNCERTAIN",now);return "DONE";}return "CACHED";
                }
            }else{
                if(cacheSize(s)>=2048){terminal(job,Stickers.JobStatus.REJECTED,"CACHE_CAPACITY",now);return "DONE";}
                entry=new Stickers.Entry();entry.setHash(blob.hash());entry.setId("stk_"+StableDigest.sha256(config.catalogId(),blob.hash()).substring(0,32));entry.setCreated(now);observe(entry,job.getSource(),now);s.getEntries().put(blob.hash(),entry);
                job.setExpectedEntryRevision(entry.getRevision());
            }
            var global=quota(s,"global",now);var scope=quota(s,scopeKey(job.getSource()),now);
            if(global.getAnalyses()>=config.analysesPerHour()||scope.getAnalyses()>=config.scopeAnalysesPerHour()){
                terminal(job,Stickers.JobStatus.REJECTED,"ANALYSIS_RATE",now);entry.setCachedUntil(now);return "DONE";
            }
            global.setAnalyses(global.getAnalyses()+1);scope.setAnalyses(scope.getAnalyses()+1);
            entry.setAnalysis(null);entry.setAnalysisVersion(config.analysisVersion());entry.setCachedUntil(now.plus(Duration.ofDays(30)));return "ANALYZE";
        });
    }
    /**
     * 2026/11/03：已有文件的显式刷新仍消耗原分析额度，和普通候选串行仲裁。
     * @param config 配置
     * @param ports 原端口
     * @param work 原后台租约
     * @param control 借用控制
     * @return 是否可启动一次分析
     */
    public CompletionStage<Boolean> beginRefresh(StickerSettings config,StickerExtensionRegistry.Selected ports,Work work,ExecutionControl control){
        return ports.catalog().change(config,s->{check(config,ports,work.job().getSource(),control);var job=running(s,work);var e=currentEntry(s,job);
            var now=clock.instant();var global=quota(s,"global",now);var scope=quota(s,scopeKey(job.getSource()),now);
            if(global.getAnalyses()>=config.analysesPerHour()||scope.getAnalyses()>=config.scopeAnalysesPerHour()){terminal(job,Stickers.JobStatus.REJECTED,"ANALYSIS_RATE",now);return false;}
            global.setAnalyses(global.getAnalyses()+1);scope.setAnalyses(scope.getAnalyses()+1);e.setCachedUntil(now.plus(Duration.ofDays(30)));return true;
        });
    }
    /**
     * @param config 配置
     * @param ports 扩展
     * @param work 原工作
     * @return 当前缓存判定
     */
    public CompletionStage<Stickers.Analysis> cached(StickerSettings config,StickerExtensionRegistry.Selected ports,Work work){return ports.catalog().change(config,s->Objects.requireNonNull(s.getEntries().get(Objects.requireNonNull(running(s,work).getBlob()).hash()).getAnalysis()));}
    /**
     * 固定视觉结果后决定是否值得发布，类型门槛不允许插件绕过。
     * @param config 配置
     * @param ports 扩展
     * @param work 原租约
     * @param analysis 一次判定
     * @param control 控制
     * @return 是否允许发布文件
     */
    public CompletionStage<Boolean> analyzed(StickerSettings config,StickerExtensionRegistry.Selected ports,Work work,Stickers.Analysis analysis,ExecutionControl control){
        return ports.catalog().change(config,s->{check(config,ports,work.job().getSource(),control);var j=running(s,work);var entry=currentEntry(s,j);
            entry.setAnalysis(analysis);entry.setAnalysisVersion(config.analysisVersion());entry.setCachedUntil(clock.instant().plus(Duration.ofDays(30)));
            // 2026/11/03：分析同时推进条目与目录版本，停用项刷新也可被管理观察发现。
            entry.setRevision(entry.getRevision()+1);s.setRevision(s.getRevision()+1);j.setExpectedEntryRevision(entry.getRevision());
            boolean accept=analysis.kind()==Stickers.Kind.STICKER&&analysis.confidence()>=config.minConfidence()&&analysis.quality()>=config.minQuality()
                    &&config.categories().containsKey(analysis.primaryCategory())&&ports.policy().accepts(analysis,config);
            if(!accept){terminal(j,Stickers.JobStatus.REJECTED,"VISUAL_REJECTED",clock.instant());}
            else if(j.isRefresh()&&!j.isActivateAfterRefresh()){j.setStickerId(entry.getId());terminal(j,Stickers.JobStatus.SAVED,"REFRESHED_DISABLED",clock.instant());return false;}
            return accept;
        });
    }
    /**
     * 文件发布后才原子替换旧条目并激活；固定、发送占用与观察期得到保护。
     * @param config 配置
     * @param ports 扩展
     * @param work 原租约
     * @param control 控制
     * @return 实际保存回执
     */
    public CompletionStage<Stickers.Receipt> activate(StickerSettings config,StickerExtensionRegistry.Selected ports,Work work,ExecutionControl control){
        return ports.catalog().change(config,s->{check(config,ports,work.job().getSource(),control);var job=running(s,work);var blob=Objects.requireNonNull(job.getBlob());var entry=currentEntry(s,job);var now=clock.instant();
            var active=s.getEntries().values().stream().filter(e->(e.getState()==Stickers.EntryStatus.ACTIVE)).toList();
            if(active.stream().filter(Stickers.Entry::isPinned).count()>config.maxActive()){throw new IllegalStateException("图库容量小于固定素材数量。");}
            String category=Objects.requireNonNull(entry.getAnalysis()).primaryCategory();long count=active.stream().filter(e->Objects.requireNonNull(e.getAnalysis()).primaryCategory().equals(category)).count();
            if(active.size()>config.maxActive()||count>config.maxPerCategory()){entry.setBlob(blob);entry.setState(job.isRefresh()?Stickers.EntryStatus.DISABLED:Stickers.EntryStatus.RETIRED);terminal(job,Stickers.JobStatus.REJECTED,"CAPACITY_RECONCILING",now);return receipt(job);}
            if(active.size()>=config.maxActive()||count>=config.maxPerCategory()){
                boolean categoryFull=count>=config.maxPerCategory();
                var victim=active.stream().filter(e->!e.isPinned()&&e.getReaders().values().stream().noneMatch(t->t.isAfter(now))&&Objects.requireNonNullElse(e.getActivatedAt(),e.getCreated()).plus(Duration.ofDays(config.probationDays())).isBefore(now)
                        &&(!categoryFull||Objects.requireNonNull(e.getAnalysis()).primaryCategory().equals(category)))
                        .min(Comparator.comparingDouble(e->ports.policy().score(e,categoryCount(active,e),now,config))).orElse(null);
                double incoming=ports.policy().score(entry,count,now,config);
                if(victim==null||!Double.isFinite(incoming)||incoming<=ports.policy().score(victim,categoryCount(active,victim),now,config)+config.replacementMargin()){
                    entry.setBlob(blob);entry.setState(job.isRefresh()?Stickers.EntryStatus.DISABLED:Stickers.EntryStatus.RETIRED);terminal(job,Stickers.JobStatus.REJECTED,"QUALITY_CAPACITY",now);return receipt(job);
                }
                victim.setState(Stickers.EntryStatus.RETIRED);victim.setRevision(victim.getRevision()+1);
            }
            entry.setBlob(blob);entry.setActivatedAt(now);entry.setState(Stickers.EntryStatus.ACTIVE);entry.setRevision(entry.getRevision()+1);s.setRevision(s.getRevision()+1);
            job.setStickerId(entry.getId());terminal(job,Stickers.JobStatus.SAVED,"SAVED",now);return receipt(job);
        });
    }
    /**
     * @param config 配置
     * @param ports 扩展
     * @param work 原工作
     * @return 已记录固定失败原因的阶段
     */
    public CompletionStage<Boolean> fail(StickerSettings config,StickerExtensionRegistry.Selected ports,Work work){return ports.catalog().change(config,s->{var job=owned(s,work);if(pending(job)){terminal(job,Stickers.JobStatus.FAILED,"PROCESSING_FAILED",clock.instant());}return true;});}
    /**
     * 暂存已回收后释放预约；发布但未激活的文件进入独立待删状态。
     * @param config 配置
     * @param ports 扩展
     * @param work 原工作
     * @return 实际清理确认
     */
    public CompletionStage<Boolean> cleaned(StickerSettings config,StickerExtensionRegistry.Selected ports,Work work){return ports.catalog().change(config,s->{
        var job=owned(s,work);if(pending(job)){throw new IllegalStateException("在途素材不能提前释放预约。");}
        // 2026/11/03：刷新使用已有文件，清理只释放它自己的占用，保留人工状态和墓碑。
        var blob=job.getBlob();if(blob!=null){var entry=s.getEntries().get(blob.hash());if(entry!=null){
            if(job.isRefresh()){entry.getReaders().remove(job.getId());}
            else if(entry.getState()!=Stickers.EntryStatus.ACTIVE&&entry.getBlob()==null){
                // 2026/11/03：发布后遇到人工删除也保留待删文件定位，不留下未计账孤儿。
                entry.setBlob(blob);if(!Set.of(Stickers.EntryStatus.DELETED,Stickers.EntryStatus.DISABLED).contains(entry.getState())){entry.setState(Stickers.EntryStatus.RETIRED);}
            }
        }}
        job.setReserved(0);job.setCleanup(true);release(s);
        return new IStickerLifecycle.Notice(config.catalogId(),"COLLECTION_COMPLETED",job.getId(),job.getStickerId(),s.getRevision(),clock.instant(),job.getStatus().name());
    }).thenApply(notice->{notifyCommitted(notice);return true;});}
    /**
     * 无在途工作时取得有限维护租约，撤销过期配置下的候选与超额未固定素材。
     * @param config 配置
     * @param ports 扩展
     * @return 至多八项待删除内容
     */
    public CompletionStage<List<Stickers.Blob>> garbage(StickerSettings config,StickerExtensionRegistry.Selected ports){return ports.catalog().change(config,s->{
        if(s.getWorkerUntil().isAfter(clock.instant())){return List.of();}var now=clock.instant();
        validatePinned(s,config);
        var active=new ArrayList<>(s.getEntries().values().stream().filter(e->(e.getState()==Stickers.EntryStatus.ACTIVE)).toList());
        if(active.stream().filter(Stickers.Entry::isPinned).count()>config.maxActive()){throw new IllegalStateException("图库容量小于固定素材数量。");}
        active.sort(Comparator.comparingDouble(e->ports.policy().score(e,categoryCount(active,e),now,config)));
        for(var entry:List.copyOf(active)){
            boolean invalid=!config.categories().containsKey(Objects.requireNonNull(entry.getAnalysis()).primaryCategory());
            if(config.enabled()&&!entry.isPinned()&&(invalid||active.size()>config.maxActive()||categoryCount(active,entry)>config.maxPerCategory())){entry.setState(Stickers.EntryStatus.RETIRED);entry.setRevision(entry.getRevision()+1);s.setRevision(s.getRevision()+1);active.remove(entry);}
        }
        var blobs=new ArrayList<Stickers.Blob>();for(var e:s.getEntries().values()){
            e.getReaders().values().removeIf(t->!t.isAfter(now));
            // 2026/11/03：人工删除的墓碑在字节回收后仍保留；回收期间禁止恢复。
            if(Set.of(Stickers.EntryStatus.RETIRED,Stickers.EntryStatus.UNAVAILABLE,Stickers.EntryStatus.DELETING,Stickers.EntryStatus.DELETED).contains(e.getState())&&e.getReaders().isEmpty()&&e.getBlob()!=null&&blobs.size()<8){
                // 2026/11/03：首次回收推进公开版本，幂等重试不重复推进。
                if(!e.isDeleting()){e.setRevision(e.getRevision()+1);s.setRevision(s.getRevision()+1);}if(e.getState()!=Stickers.EntryStatus.DELETED){e.setState(Stickers.EntryStatus.DELETING);}e.setDeleting(true);blobs.add(e.getBlob());}
        }return List.copyOf(blobs);
    });}
    /**
     * 2026/11/03：实际字节回收改变可恢复状态，更新版本使旧管理请求明确冲突。
     * @param config 配置
     * @param ports 扩展
     * @param blob 实际删除的内容
     * @return 元数据确认
     */
    public CompletionStage<Boolean> deleted(StickerSettings config,StickerExtensionRegistry.Selected ports,Stickers.Blob blob){return ports.catalog().change(config,s->{var entry=s.getEntries().get(blob.hash());if(entry!=null&&Set.of(Stickers.EntryStatus.DELETED,Stickers.EntryStatus.DELETING).contains(entry.getState())&&blob.equals(entry.getBlob())){entry.setBlob(null);entry.setDeleting(false);entry.setRevision(entry.getRevision()+1);s.setRevision(s.getRevision()+1);if(entry.getState()==Stickers.EntryStatus.DELETING){entry.setState(Stickers.EntryStatus.CACHED);}}return true;});}
    /**
     * @param config 配置
     * @param ports 扩展
     * @return 轮转选择的至多八项有效文件
     */
    public CompletionStage<List<Stickers.Blob>> inspect(StickerSettings config,StickerExtensionRegistry.Selected ports){return ports.catalog().change(config,s->{
        var entries=s.getEntries().values().stream().filter(e->(e.getState()==Stickers.EntryStatus.ACTIVE)&&e.getBlob()!=null).toList();if(entries.isEmpty()){return List.of();}
        var result=new ArrayList<Stickers.Blob>();int start=Math.floorMod(s.getInspection(),entries.size());for(int i=0;i<Math.min(8,entries.size());i++){result.add(entries.get((start+i)%entries.size()).getBlob());}
        s.setInspection((start+result.size())%entries.size());return List.copyOf(result);
    });}
    /**
     * @param config 配置
     * @param ports 扩展
     * @param blob 实际缺失或损坏文件
     * @return 立即撤销有效状态的事务
     */
    public CompletionStage<Boolean> unavailable(StickerSettings config,StickerExtensionRegistry.Selected ports,Stickers.Blob blob){return ports.catalog().change(config,s->{
        var entry=s.getEntries().get(blob.hash());if(entry!=null&&(entry.getState()==Stickers.EntryStatus.ACTIVE)&&blob.equals(entry.getBlob())){entry.setState(Stickers.EntryStatus.UNAVAILABLE);entry.setRevision(entry.getRevision()+1);s.setRevision(s.getRevision()+1);}return true;
    });}
    /**
     * @param id 原作业
     * @param scope 当前区域
     * @param requester 当前用户
     * @return 仅本人同区域可见的状态
     */
    public CompletionStage<Stickers.Receipt> status(UUID id,org.zexnocs.teanekoagent.runtime.AgentScope scope,UUID requester){var config=settings();var ports=registry.select(config);return ports.catalog().change(config,s->{var j=s.getJobs().get(id);if(j==null||!j.getSource().scope().equals(scope)||!j.getSource().requester().equals(requester)){throw new IllegalArgumentException("当前用户没有该采集作业。");}return receipt(j);});}
    /**
     * @param config 固定配置
     * @param ports 固定端口
     * @param source 真实来源
     * @param control 可选控制
     */
    public void check(StickerSettings config,StickerExtensionRegistry.Selected ports,Stickers.Source source,@Nullable ExecutionControl control){if(control!=null){control.checkActive();}registry.verify(ports);if(!config.equals(settings())||!allows(source)){throw new IllegalStateException("素材采集配置或来源资格已失效。");}}
    /**
     * @param source 固定来源
     * @param now 接收时间
     */
    private void validateSource(Stickers.Source source,Instant now){
        Objects.requireNonNull(source.author());Objects.requireNonNull(source.requester());
        if(source.sender().equals(source.self())||source.self().isBlank()||source.message().isBlank()||source.message().length()>128||source.image()<0||source.image()>15
                ||source.url().isBlank()||source.url().length()>16384||source.occurredAt().isAfter(now.plusSeconds(60))
                ||(!source.manual()&&source.occurredAt().isBefore(now.minus(Duration.ofHours(24))))){throw new IllegalArgumentException("素材候选不是真实有效的聊天图片。");}
    }
    /**
     * @param state 原目录
     * @param work 原工作
     * @return 原作业
     */
    private Stickers.Job owned(Stickers.State state,Work work){if(!Objects.equals(state.getWorker(),work.owner())||!state.getWorkerUntil().isAfter(clock.instant())){throw new IllegalStateException("素材后台租约已失效。");}return Objects.requireNonNull(state.getJobs().get(work.job().getId()));}
    /**
     * @param state 原目录
     * @param work 原租约
     * @return 尚未形成终态的原作业
     */
    private Stickers.Job running(Stickers.State state,Work work){var job=owned(state,work);if(job.getStatus()!=Stickers.JobStatus.RUNNING){throw new IllegalStateException("素材作业已有终态，不能再次发布或激活。");}return job;}
    /** @param state 原目录 */
    private void release(Stickers.State state){state.setWorker(null);state.setWorkerUntil(Instant.EPOCH);}
    /**
     * @param job 原作业
     * @return 是否未完成
     */
    private static boolean pending(Stickers.Job job){return job.getStatus()==Stickers.JobStatus.QUEUED||job.getStatus()==Stickers.JobStatus.RUNNING;}
    /** 2026/11/03：刷新无下载预约，仍需释放文件占用。
     * @param job 原作业
     * @return 是否尚有清理责任
     */
    private static boolean needsCleanup(Stickers.Job job){return !job.isCleanup()&&(job.getReserved()>0||job.isRefresh());}
    /** 2026/11/03：人工修改或删除使迟到分析和激活失效。
     * @param state 原目录
     * @param job 原任务
     * @return 仍属于原版本的条目
     */
    private static Stickers.Entry currentEntry(Stickers.State state,Stickers.Job job){
        var e=state.getEntries().get(Objects.requireNonNull(job.getBlob()).hash());
        if(e==null||e.isDeleting()||Set.of(Stickers.EntryStatus.DELETED,Stickers.EntryStatus.DELETING).contains(e.getState())||(job.getExpectedEntryRevision()>0&&e.getRevision()!=job.getExpectedEntryRevision())){throw new IllegalStateException("素材在后台分析期间已被管理操作更改。");}
        return e;
    }
    /** 2026/11/03：观察失败不回滚已经提交的状态。
     * @param notice 真实提交快照
     */
    private void notifyCommitted(IStickerLifecycle.Notice notice){if(lifecycle!=null){try{lifecycle.committed(notice);}catch(RuntimeException ignored){/* 通知不是提交协议或审计账本。 */}}}
    /**
     * @param job 原作业
     * @param status 终态
     * @param reason 固定原因
     * @param now 时刻
     */
    private static void terminal(Stickers.Job job,Stickers.JobStatus status,String reason,Instant now){job.setStatus(status);job.setReason(reason);job.setNextAttempt(now);}
    /**
     * @param job 原作业
     * @return 脱敏回执
     */
    private static Stickers.Receipt receipt(Stickers.Job job){return new Stickers.Receipt(job.getId(),job.getStatus(),job.getReason(),job.getStickerId());}
    /**
     * @param state 原目录
     * @return 保守容量，包括预约与待删原文件
     */
    private static long bytes(Stickers.State state){return state.getEntries().values().stream().map(Stickers.Entry::getBlob).filter(Objects::nonNull).mapToLong(Stickers.Blob::bytes).sum()+state.getJobs().values().stream().mapToLong(Stickers.Job::getReserved).sum();}
    /**
     * @param source 固定消息
     * @return 不含原文的来源摘要
     */
    private static String sourceKey(Stickers.Source source){return StableDigest.sha256(source.scope().clientId(),source.scope().scopeId(),source.self(),source.message(),Integer.toString(source.image()));}
    /**
     * @param source 固定消息
     * @return 单来源额度键
     */
    private static String scopeKey(Stickers.Source source){return StableDigest.sha256(source.scope().clientId(),source.scope().scopeId());}
    /**
     * @param state 原目录
     * @param key 限额键
     * @param now 时刻
     * @return 本小时计数
     */
    private static Stickers.Quota quota(Stickers.State state,String key,Instant now){long hour=now.getEpochSecond()/3600;state.getQuotas().values().removeIf(q->q.getHour()<hour-1);if(!state.getQuotas().containsKey(key)&&state.getQuotas().size()>=2048){throw new IllegalStateException("素材来源限流目录已满。");}var q=state.getQuotas().computeIfAbsent(key,k->new Stickers.Quota());if(q.getHour()!=hour){q.setHour(hour);q.setDownloads(0);q.setAnalyses(0);}return q;}
    /**
     * @param entries 有效条目
     * @param entry 当前条目
     * @return 同类别数量
     */
    private static long categoryCount(List<Stickers.Entry> entries,Stickers.Entry entry){return entries.stream().filter(e->Objects.requireNonNull(e.getAnalysis()).primaryCategory().equals(Objects.requireNonNull(entry.getAnalysis()).primaryCategory())).count();}
    /**
     * @param entry 内容条目
     * @param source 当前真实入站
     * @param now 当前时刻
     */
    private static void observe(Stickers.Entry entry,Stickers.Source source,Instant now){
        if(source.manual()){return;}long day=now.atZone(ZoneOffset.UTC).toLocalDate().toEpochDay();var counts=entry.getHumanDays();
        counts.keySet().removeIf(k->Long.parseLong(k.substring(0,k.indexOf(':')))<day-30);String key=day+":"+source.author();
        if(counts.containsKey(key)||counts.size()<32){counts.compute(key,(k,v)->Math.min(3,v==null?1:v+1));}entry.setLastHuman(now);
    }
    /**
     * @param state 原目录
     * @param now 时刻
     */
    private static void prune(Stickers.State state,Instant now){
        state.getReceipts().values().removeIf(t->t.plus(Duration.ofHours(48)).isBefore(now));
        // 2026/11/06：回收和存储分流使用同一恢复合同，未释放的资源不能误归档。
        var completed=state.getJobs().values().stream().filter(j->!j.requiresRecovery()).sorted(Comparator.comparing(Stickers.Job::getNextAttempt).reversed()).toList();
        var retained=completed.stream().limit(128).map(Stickers.Job::getId).collect(java.util.stream.Collectors.toSet());
        state.getJobs().values().removeIf(j->!j.requiresRecovery()&&(!retained.contains(j.getId())||j.getNextAttempt().plus(Duration.ofHours(24)).isBefore(now)));
        // 2026/11/03：仅回收自动缓存，不自动遗忘人工删除和停用决定。
        state.getEntries().values().removeIf(e->e.getState()==Stickers.EntryStatus.CACHED&&e.getBlob()==null&&e.getCachedUntil()!=null&&e.getCachedUntil().isBefore(now)&&state.getJobs().values().stream().noneMatch(j->pending(j)&&j.getBlob()!=null&&j.getBlob().hash().equals(e.getHash())));
        if(cacheSize(state)>=2048){
            var inFlight=state.getJobs().values().stream().filter(StickerLibrary::pending).map(Stickers.Job::getBlob).filter(Objects::nonNull).map(Stickers.Blob::hash).collect(java.util.stream.Collectors.toSet());
            var evictable=state.getEntries().values().stream().filter(e->e.getState()==Stickers.EntryStatus.CACHED&&e.getBlob()==null&&!inFlight.contains(e.getHash())).sorted(Comparator.comparing(Stickers.Entry::getCreated)).toList();
            for(var entry:evictable){if(cacheSize(state)<2048){break;}state.getEntries().remove(entry.getHash());}
        }
    }
    /**
     * 2026/11/05：已回收文件的人工删除决定单独计量，不占用分析缓存且不被自动遗忘。
     * @param state 当前原子目录
     * @return 仍占用缓存或文件的条目数
     */
    public static long cacheSize(Stickers.State state) {
        return state.getEntries().values().stream()
                .filter(entry -> entry.getState() != Stickers.EntryStatus.DELETED || entry.getBlob() != null)
                .count();
    }
    /**
     * @param state 原目录
     * @param config 当前数量与类别限制
     * @throws IllegalStateException 当容量低于固定素材需求时
     */
    private static void validatePinned(Stickers.State state,StickerSettings config){
        var pinned=state.getEntries().values().stream().filter(e->e.isPinned()&&(e.getState()==Stickers.EntryStatus.ACTIVE)).toList();
        if(pinned.size()>config.maxActive()||pinned.stream().anyMatch(e->!config.categories().containsKey(Objects.requireNonNull(e.getAnalysis()).primaryCategory())||categoryCount(pinned,e)>config.maxPerCategory())){
            throw new IllegalStateException("图库数量或类别配置无法容纳固定素材，请先显式调整固定项。");
        }
    }
    /**
     * 固定后台代次，不进入组件、模型或持久状态。
     * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
     *
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @param owner 根租约
     * @param job 已提交作业快照
     * @param cleanup 是否只清理
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.6.2-l
     * @version 4.8.0-l
     */
    @JsonIgnoreType public record Work(UUID owner,Stickers.Job job,boolean cleanup){}
}
