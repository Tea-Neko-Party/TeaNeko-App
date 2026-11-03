package org.zexnocs.teanekoagent.memory.layered;

import org.jspecify.annotations.Nullable;
import org.springframework.scheduling.annotation.Scheduled;
import org.zexnocs.teanekoagent.agent.AgentScope;
import org.zexnocs.teanekoagent.agent.memory.IAgentMemoryReferences;
import org.zexnocs.teanekoagent.llm.framework.interaction.LLMCallLimits;
import org.zexnocs.teanekoagent.personality.StyleObservationService;
import org.zexnocs.teanekoagent.personality.StyleObservations;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.logger.ILogger;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 在借用执行器上消费持久目录和租约，未启动或失败任务由后续调度重新发现。
 * 单个处理器最多一个在途模型任务，关闭只结束自有控制，不关闭共享资源。
 * <br>2026/11/02：同一调度与预算按需整理中期为长期，不创建第二套后台模型队列。
 * <br>2026/11/03：同次整理可观察人格表达，事实学习与人格学习使用各自开关。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.6.1-l
 * @version 4.6.1-l
 */
public final class LayeredMemoryProcessor implements AutoCloseable {
    private final ILayeredMemoryStore store;
    private final ILayeredMemoryCompressor compressor;
    private final @Nullable ILongTermMemoryStore longStore;
    private final @Nullable ILongTermMemoryConsolidator consolidator;
    private final Function<AgentScope, Boolean> learning;
    private final @Nullable StyleObservationService observations;
    private final IAgentMemoryReferences references;
    private final Supplier<LayeredMemorySettings> settings;
    private final Function<AgentScope, LayeredMemoryPolicy> policies;
    private final Executor executor;
    private final ScheduledExecutorService scheduler;
    private final Clock clock;
    private final ILogger logger;
    private final AtomicBoolean running = new AtomicBoolean();
    private volatile boolean closed;
    private volatile ExecutionControl current;
    private String cursor = "";
    /**
     * 借用统一资源，构造不读库或创建模型实例。
     * @param store 持久作业与消息
     * @param compressor 可替换摘要生成
     * @param references 当前引用复核，不能由替换生成器绕过
     * @param settings 本地预算
     * @param policies 动态作用域策略
     * @param executor 借用业务执行器
     * @param scheduler 借用计时器
     * @param clock 实际时钟
     * @param logger 固定诊断
     */
    public LayeredMemoryProcessor(ILayeredMemoryStore store, ILayeredMemoryCompressor compressor, IAgentMemoryReferences references, Supplier<LayeredMemorySettings> settings,
            Function<AgentScope, LayeredMemoryPolicy> policies, Executor executor, ScheduledExecutorService scheduler, Clock clock, ILogger logger) {
        // 2026/11/02：独立旧宿主保留仅短期到中期的入口。
        this(store, compressor, references, settings, policies, executor, scheduler, clock, logger, null, null, ignored -> false);
    }
    /**
     * 2026/11/02：注入可选长期整理端口，两个阶段共享单个在途任务限制。
     * @param store 短中期持久化
     * @param compressor 中期生成
     * @param references 当前引用
     * @param settings 统一预算
     * @param policies 读取和压缩策略
     * @param executor 原业务域
     * @param scheduler 原计时器
     * @param clock 当前时钟
     * @param logger 固定诊断
     * @param longStore 可选长期持久化
     * @param consolidator 可选长期选择器
     * @param learning 真实自动学习开关
     */
    public LayeredMemoryProcessor(ILayeredMemoryStore store, ILayeredMemoryCompressor compressor, IAgentMemoryReferences references, Supplier<LayeredMemorySettings> settings,
            Function<AgentScope, LayeredMemoryPolicy> policies, Executor executor, ScheduledExecutorService scheduler, Clock clock, ILogger logger,
            @Nullable ILongTermMemoryStore longStore, @Nullable ILongTermMemoryConsolidator consolidator, Function<AgentScope, Boolean> learning) {
        // 2026/11/03：独立原宿主保持仅事实整理。
        this(store, compressor, references, settings, policies, executor, scheduler, clock, logger, longStore, consolidator, learning, null);
    }
    /**
     * 2026/11/03：接入可选观察目标，两个学习域共用一个模型交互与持久水位。
     * @param store 短中期存储
     * @param compressor 中期生成
     * @param references 原空间引用
     * @param settings 本地预算
     * @param policies 压缩开关
     * @param executor 原业务域
     * @param scheduler 原计时器
     * @param clock 当前时钟
     * @param logger 固定诊断
     * @param longStore 长期事务
     * @param consolidator 联合选择器
     * @param learning 用户事实学习开关
     * @param observations 可选人格观察
     */
    public LayeredMemoryProcessor(ILayeredMemoryStore store, ILayeredMemoryCompressor compressor, IAgentMemoryReferences references, Supplier<LayeredMemorySettings> settings,
            Function<AgentScope, LayeredMemoryPolicy> policies, Executor executor, ScheduledExecutorService scheduler, Clock clock, ILogger logger,
            @Nullable ILongTermMemoryStore longStore, @Nullable ILongTermMemoryConsolidator consolidator, Function<AgentScope, Boolean> learning,
            @Nullable StyleObservationService observations) {
        this.store = store; this.compressor = compressor; this.references = references; this.settings = settings; this.policies = policies; this.executor = executor;
        this.scheduler = scheduler; this.clock = clock; this.logger = logger;
        this.longStore = longStore; this.consolidator = consolidator; this.learning = learning; this.observations = observations;
    }
    /** 在既有调度设施中恢复有界工作，不因上一批等待模型而启动重复任务。 */
    @Scheduled(fixedDelayString = "${tea-neko.agent.memory.layered.poll-ms:5000}", initialDelayString = "${tea-neko.agent.memory.layered.poll-ms:5000}")
    public void tick() {
        drain().whenComplete((ignored, failure) -> {
            if (failure != null) { logger.warn("Agent 分层记忆", "分层记忆处理未完成，将按持久进度再次尝试。"); }
        });
    }
    /**
     * 执行一次有界扫描，可供显式宿主或测试等待真实完成。
     * @return 本次扫描和至多一个压缩任务完成阶段
     */
    public CompletionStage<Void> drain() {
        if (closed || !running.compareAndSet(false, true)) { return CompletableFuture.completedStage(null); }
        try {
            return CompletableFuture.supplyAsync(() -> {
                var policy = settings.get();
                if (closed || !policy.enabled()) { return CompletableFuture.<Void>completedStage(null); }
                var control = ExecutionControl.withDeadline(new LLMCallLimits(1, 0, 0, 16384L).newBudget(),
                        clock.instant().plus(policy.timeout()), clock, scheduler);
                current = control; if (closed) { control.cancel(); }
                CompletionStage<Void> work;
                try { control.checkActive(); work = control.guard(store.streams(cursor)).thenComposeAsync(streams -> {
                    if (streams.isEmpty()) { cursor = ""; return CompletableFuture.completedStage(null); }
                    return visit(streams, 0, control);
                }, executor); }
                catch (RuntimeException failure) { work = CompletableFuture.failedStage(failure); }
                return control.guard(work).whenComplete((ignored, failure) -> { control.close(); if (current == control) { current = null; } });
            }, executor).thenCompose(stage -> stage).whenComplete((ignored, failure) -> running.set(false));
        } catch (RuntimeException failure) { running.set(false); return CompletableFuture.failedStage(failure); }
    }
    /**
     * 逐个检查目录直至领取一个任务，持久目录游标轮转避免单分区饥饿。
     * @param streams 本次目录页
     * @param index 当前位置
     * @param control 包含目录读取、领取、生成及提交观察的本批控制
     * @return 本次扫描完成阶段
     */
    private CompletionStage<Void> visit(List<MemoryLayers.Stream> streams, int index, ExecutionControl control) {
        if (closed || index == streams.size()) { return CompletableFuture.completedStage(null); }
        control.checkActive();
        var stream = streams.get(index); cursor = stream.id();
        var policy = policies.apply(stream.reference().scope());
        if (!policy.enabled()) { return visit(streams, index + 1, control); }
        var policySettings = settings.get();
        return control.guard(references.verify(stream.reference())).handle((ignored, failure) -> failure == null).thenComposeAsync(valid -> {
            control.checkActive();
            if (!valid) { return visit(streams, index + 1, control); }
            // 2026/11/02：先处理已就绪的长期整理，再尝试原始消息压缩。
            // 2026/11/03：人格观察与事实学习独立授权，任一启用即可消费新中期片段。
            var candidates = longStore != null && consolidator != null && learningEnabled(stream.reference().scope())
                    ? control.guard(longStore.claim(stream, clock.instant())) : CompletableFuture.<java.util.Optional<LongMemories.Batch>>completedStage(java.util.Optional.empty());
            return candidates.thenComposeAsync(longBatch -> {
                if (longBatch.isPresent()) { return processLong(longBatch.get(), control); }
                if (!policy.compress()) { return visit(streams, index + 1, control); }
                return control.guard(store.claim(stream, policySettings, clock.instant())).thenComposeAsync(batch -> batch.map(value -> process(value, control)).orElseGet(() -> visit(streams, index + 1, control)), executor);
            }, executor);
        }, executor);
    }
    /**
     * 2026/11/02：等待语义选择后重新检查学习和空间，零候选也按真实提交推进进度。
     * @param batch 原长期租约
     * @param control 本批统一控制
     * @return 提交或退避完成阶段
     */
    private CompletionStage<Void> processLong(LongMemories.Batch batch, ExecutionControl control) {
        var target = java.util.Objects.requireNonNull(longStore); var generator = java.util.Objects.requireNonNull(consolidator);
        CompletionStage<StyleObservations.Target> prepared;
        try { control.checkActive(); prepared = observations == null ? CompletableFuture.completedStage(null) : observations.prepare(batch, control); }
        catch (RuntimeException failure) { prepared = CompletableFuture.failedStage(failure); }
        var generation = control.guard(prepared).thenComposeAsync(basis -> {
            // 2026/11/03：事实关闭且没有当前人格样本时直接完成空筛选，避免浪费模型请求。
            if (basis == null && !Boolean.TRUE.equals(learning.apply(batch.stream().reference().scope()))) {
                return CompletableFuture.completedStage(new Generated(null, new StyleObservations.Analysis(List.of(), List.of())));
            }
            control.checkActive(); return generator.analyze(batch, basis, control).thenApply(values -> new Generated(basis, values));
        }, executor);
        return control.guard(generation).thenComposeAsync(values -> {
            control.checkActive();
            return control.guard(references.verify(batch.stream().reference())).thenComposeAsync(ignored -> {
                control.checkActive();
                if (closed || !settings.get().enabled() || !learningEnabled(batch.stream().reference().scope())) {
                    throw new IllegalStateException("长期记忆学习资格已失效。");
                }
                // 2026/11/03：生成器不能自行选择写入域；提交时按两个实际开关筛选。
                var facts = Boolean.TRUE.equals(learning.apply(batch.stream().reference().scope())) ? values.analysis().memories() : List.<LongMemories.Candidate>of();
                boolean styleAllowed = observations != null && observations.enabled(batch.stream().reference().scope()) && values.basis() != null;
                var styles = styleAllowed ? values.analysis().styles() : List.<StyleObservations.Candidate>of();
                var guard = styleAllowed ? java.util.Objects.requireNonNull(observations).guard(batch, java.util.Objects.requireNonNull(values.basis()), control) : (Runnable) control::checkActive;
                return target.commit(batch, new StyleObservations.Analysis(facts, styles), values.basis(), clock.instant(), guard).thenApply(committed -> {
                    if (!committed) { throw new IllegalStateException("长期整理原输入或租约已失效。"); } return (Void) null;
                });
            }, executor);
        }, executor).exceptionallyCompose(failure -> target.retry(batch, clock.instant()).thenRun(() ->
                logger.warn("Agent 长期记忆", "长期候选未提交，保留原片段和整理进度，稍后重试。")));
    }
    /**
     * 2026/11/03：任一独立学习域开启时处理新片段，不默认启用关闭的另一个域。
     * @param scope 真实作用域
     * @return 当前有学习工作时为真
     */
    private boolean learningEnabled(AgentScope scope) { return Boolean.TRUE.equals(learning.apply(scope)) || observations != null && observations.enabled(scope); }
    /**
     * 生成结果与宿主原目标一起交回，模型不能替换目标身份。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param basis 原目标
     * @param analysis 模型候选
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.6.1-l
     * @version 4.6.1-l
     */
    private record Generated(StyleObservations.@Nullable Target basis, StyleObservations.Analysis analysis) { }
    /**
     * 等待真实模型结果、复核当前策略并提交，失败只保留退避，不推进水位。
     * @param batch 原租约及输入
     * @param control 不重新创建时限或预算的本批控制
     * @return 摘要或失败退避的实际完成阶段
     */
    private CompletionStage<Void> process(MemoryLayers.Batch batch, ExecutionControl control) {
        CompletionStage<MemoryLayers.Digest> generation;
        try { control.checkActive(); generation = compressor.compress(batch, control); }
        catch (RuntimeException failure) { generation = CompletableFuture.failedStage(failure); }
        return control.guard(generation).thenComposeAsync(digest -> {
            control.checkActive();
            return control.guard(references.verify(batch.stream().reference())).thenComposeAsync(ignored -> {
            control.checkActive(); var latest = policies.apply(batch.stream().reference().scope());
            if (closed || !settings.get().enabled() || !latest.enabled() || !latest.compress()) { throw new IllegalStateException("记忆压缩提交资格已失效。"); }
            return store.commit(batch, digest, clock.instant()).thenApply(committed -> {
                if (!committed) { throw new IllegalStateException("记忆压缩租约或输入已变化。"); } return (Void) null;
            });
            }, executor);
        }, executor).exceptionallyCompose(failure -> store.retry(batch, clock.instant()).thenRun(() ->
                logger.warn("Agent 分层记忆", "中期片段未提交；原消息和水位保留，稍后重新尝试。")));
    }
    /** 结束自有工作控制，未提交的持久任务仍可在重启后重新领取。 */
    @Override public void close() { closed = true; var control = current; if (control != null) { control.cancel(); } }
}
