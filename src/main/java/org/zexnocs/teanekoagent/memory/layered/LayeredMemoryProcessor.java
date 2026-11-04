package org.zexnocs.teanekoagent.memory.layered;

import org.jspecify.annotations.Nullable;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.zexnocs.teanekoagent.agent.AgentScope;
import org.zexnocs.teanekoagent.agent.memory.IAgentMemoryReferences;
import org.zexnocs.teanekoagent.personality.StyleObservationService;
import org.zexnocs.teanekoagent.personality.StyleObservations;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.actuator.timer.TimerDisplay;
import org.zexnocs.teanekocore.logger.FailureDiagnostic;
import org.zexnocs.teanekocore.logger.ILogger;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 在借用执行器上消费持久目录和租约，未启动或失败任务由后续调度重新发现。
 * 单个处理器最多一个在途模型任务，关闭只结束自有控制，不关闭共享资源。
 * <br>2026/11/02：同一调度与预算按需整理中期为长期，不创建第二套后台模型队列。
 * <br>2026/11/03：同次整理可观察人格表达，事实学习与人格学习使用各自开关。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：按处理阶段记录原因、模型状态和请求号，保留原退避及提交边界。
 *
 * <br>2026/11/04：声明定时任务分类及网页控制边界。
 * <br>2026/11/04：统一父子输入加输出预算，避免大输入耗尽固定 token 额度。
 *
 * <br>2026/11/04�������Ʋ����������� token ��ģ����������ʵ����ǰͳһ��顣
 *
 * <br>2026/11/04��У������ʵ�ֵİ汾����Ϊ v4.7.2-l��
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.6.1-l
 * @version 4.7.2-l
 */
// 2026/11/04：展示声明不改变原任务调度和生命周期。
@TimerDisplay(category = "agent.memory", categoryName = "Agent 记忆", name = "分层记忆整理",
        description = "原调度方法发起后台整理，方法结束不代表模型或数据库事务结束。", controllable = false)
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
    /**
     * 在既有调度设施中恢复有界工作，不因上一批等待模型而启动重复任务。
     * 2026/11/04：目录或退避失败同样报告安全的异常结构。
     */
    @Scheduled(fixedDelayString = "${tea-neko.agent.memory.layered.poll-ms:5000}", initialDelayString = "${tea-neko.agent.memory.layered.poll-ms:5000}")
    public void tick() {
        drain().whenComplete((ignored, failure) -> {
            if (failure != null) {
                reportFailure("Agent 分层记忆", "目录扫描或退避", null, null, failure);
            }
        });
    }
    /**
     * 执行一次有界扫描，可供显式宿主或测试等待真实完成。
     * 2026/11/04：按本次输入容量配置创建父 token 预算，覆盖一次完整的生成输出。
     * @return 本次扫描和至多一个压缩任务完成阶段
     */
    public CompletionStage<Void> drain() {
        if (closed || !running.compareAndSet(false, true)) { return CompletableFuture.completedStage(null); }
        try {
            return CompletableFuture.supplyAsync(() -> {
                var policy = settings.get();
                if (closed || !policy.enabled()) { return CompletableFuture.<Void>completedStage(null); }
                // 2026/11/04：父控制保留调用次数，token 容量由选定模型的上下文声明约束。
                var control = ExecutionControl.withDeadline(policy.modelCallLimits().newBudget(),
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
     * 2026/11/04：区分观察准备、模型生成、资格复核和持久提交，不导出候选原文。
     * @param batch 原长期租约
     * @param control 本批统一控制
     * @return 提交或退避完成阶段
     */
    private CompletionStage<Void> processLong(LongMemories.Batch batch, ExecutionControl control) {
        var target = java.util.Objects.requireNonNull(longStore);
        var generator = java.util.Objects.requireNonNull(consolidator);
        var phase = new AtomicReference<>("人格观察准备");
        CompletionStage<StyleObservations.Target> prepared;
        try { control.checkActive(); prepared = observations == null ? CompletableFuture.completedStage(null) : observations.prepare(batch, control); }
        catch (RuntimeException failure) { prepared = CompletableFuture.failedStage(failure); }
        var generation = control.guard(prepared).thenComposeAsync(basis -> {
            phase.set("长期模型生成");
            // 2026/11/03：事实关闭且没有当前人格样本时直接完成空筛选，避免浪费模型请求。
            if (basis == null && !Boolean.TRUE.equals(learning.apply(batch.stream().reference().scope()))) {
                return CompletableFuture.completedStage(new Generated(null, new StyleObservations.Analysis(List.of(), List.of())));
            }
            control.checkActive(); return generator.analyze(batch, basis, control).thenApply(values -> new Generated(basis, values));
        }, executor);
        return control.guard(generation).thenComposeAsync(values -> {
            phase.set("长期资格复核");
            control.checkActive();
            return control.guard(references.verify(batch.stream().reference())).thenComposeAsync(ignored -> {
                control.checkActive();
                if (closed || !settings.get().enabled() || !learningEnabled(batch.stream().reference().scope())) {
                    throw new MemoryProcessingException(MemoryProcessingException.Reason.ACCESS_REVOKED);
                }
                // 2026/11/03：生成器不能自行选择写入域；提交时按两个实际开关筛选。
                var facts = Boolean.TRUE.equals(learning.apply(batch.stream().reference().scope())) ? values.analysis().memories() : List.<LongMemories.Candidate>of();
                boolean styleAllowed = observations != null && observations.enabled(batch.stream().reference().scope()) && values.basis() != null;
                var styles = styleAllowed ? values.analysis().styles() : List.<StyleObservations.Candidate>of();
                var guard = styleAllowed ? java.util.Objects.requireNonNull(observations).guard(batch, java.util.Objects.requireNonNull(values.basis()), control) : (Runnable) control::checkActive;
                phase.set("长期持久提交");
                return target.commit(batch, new StyleObservations.Analysis(facts, styles), values.basis(), clock.instant(), guard).thenApply(committed -> {
                    if (!committed) {
                        throw new MemoryProcessingException(MemoryProcessingException.Reason.LEASE_CHANGED);
                    }
                    return (Void) null;
                });
            }, executor);
        }, executor).exceptionallyCompose(failure -> target.retry(batch, clock.instant()).thenRun(() ->
                reportFailure("Agent 长期记忆", phase.get(), control.id(), batch.leaseId(), failure)));
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
     * 2026/11/04：记录实际失败阶段、模型状态和原请求编号，继续使用原租约退避。
     * @param batch 原租约及输入
     * @param control 不重新创建时限或预算的本批控制
     * @return 摘要或失败退避的实际完成阶段
     */
    private CompletionStage<Void> process(MemoryLayers.Batch batch, ExecutionControl control) {
        var phase = new AtomicReference<>("中期模型生成");
        CompletionStage<MemoryLayers.Digest> generation;
        try { control.checkActive(); generation = compressor.compress(batch, control); }
        catch (RuntimeException failure) { generation = CompletableFuture.failedStage(failure); }
        return control.guard(generation).thenComposeAsync(digest -> {
            phase.set("中期资格复核");
            control.checkActive();
            return control.guard(references.verify(batch.stream().reference())).thenComposeAsync(ignored -> {
            control.checkActive(); var latest = policies.apply(batch.stream().reference().scope());
            if (closed || !settings.get().enabled() || !latest.enabled() || !latest.compress()) {
                throw new MemoryProcessingException(MemoryProcessingException.Reason.ACCESS_REVOKED);
            }
            phase.set("中期持久提交");
            return store.commit(batch, digest, clock.instant()).thenApply(committed -> {
                if (!committed) {
                    throw new MemoryProcessingException(MemoryProcessingException.Reason.LEASE_CHANGED);
                }
                return (Void) null;
            });
            }, executor);
        }, executor).exceptionallyCompose(failure -> store.retry(batch, clock.instant()).thenRun(() ->
                reportFailure("Agent 分层记忆", phase.get(), control.id(), batch.leaseId(), failure)));
    }

    /**
     * 只读取宿主原因、异常结构及真实编号，不读取 Throwable 消息或模型原文。
     * @param namespace 当前记忆处理域
     * @param phase 最后实际进入的处理阶段
     * @param request 原模型请求编号，扫描尚未建立控制时为空
     * @param lease 原租约编号，目录任务为空
     * @param failure 实际失败
     */
    private void reportFailure(String namespace, String phase, @Nullable UUID request,
            @Nullable UUID lease, Throwable failure) {
        MemoryProcessingException classified = null;
        String stopped = null;
        Throwable currentFailure = failure;
        for (int depth = 0; currentFailure != null && depth < 16; depth++) {
            if (currentFailure instanceof MemoryProcessingException value) {
                classified = value;
                break;
            }
            if (currentFailure instanceof org.zexnocs.teanekocore.actuator.execution.ExecutionStoppedException value) {
                stopped = value.reason().name();
            }
            currentFailure = currentFailure.getCause();
        }
        String code = "PROCESSING_FAILED";
        String explanation = "检查异常类型及代码位置";
        if (classified != null) {
            code = classified.reason().name();
            explanation = classified.reason().description();
        } else if (stopped != null) {
            code = "EXECUTION_" + stopped;
            explanation = "执行已取消、关闭或达到截止时间";
        }
        String status = classified == null || classified.modelStatus() == null ? "未取得" : classified.modelStatus().name();
        String previousRequest = MDC.get("requestId");
        try {
            if (request != null) {
                MDC.put("requestId", request.toString());
            }
            logger.warn(namespace, "记忆整理未完成。请求=" + (request == null ? "未分配" : request)
                    + "，批次=" + (lease == null ? "无租约" : lease) + "，阶段=" + phase + "，原因=" + code
                    + "（" + explanation + "），模型状态=" + status + "，准入状态="
                    + (classified == null ? "未取得" : classified.accessStatus()) + "，" + FailureDiagnostic.from(failure)
                    + "。本批不会以失败候选推进整理进度，将按持久状态重试。");
        } finally {
            if (previousRequest == null) {
                MDC.remove("requestId");
            } else {
                MDC.put("requestId", previousRequest);
            }
        }
    }
    /** 结束自有工作控制，未提交的持久任务仍可在重启后重新领取。 */
    @Override public void close() { closed = true; var control = current; if (control != null) { control.cancel(); } }
}
