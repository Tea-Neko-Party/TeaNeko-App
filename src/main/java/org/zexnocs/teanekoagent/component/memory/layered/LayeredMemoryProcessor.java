package org.zexnocs.teanekoagent.component.memory.layered;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.jspecify.annotations.Nullable;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.zexnocs.teanekoagent.component.memory.runtime.IAgentMemoryReferences;
import org.zexnocs.teanekoagent.component.personality.StyleObservationService;
import org.zexnocs.teanekoagent.component.personality.StyleObservations;
import org.zexnocs.teanekoagent.runtime.AgentScope;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.actuator.timer.TimerDisplay;
import org.zexnocs.teanekocore.logger.FailureDiagnostic;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekocore.utils.StableDigest;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
 * <br>2026/11/04：补全定时任务的详细中文介绍，原调度和控制意图保持不变。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/05：目录与领取后的整理分别计时，等待实际退避完成，避免重复超时诊断和重叠调度。
 *
 * <br>2026/11/05：长期事实与风格提交分别在真实事务内复核生命周期及学习资格。
 * <br>2026/11/05：合法空观察批次不再因缺少目标被误判撤权，保留提交前动态开关复核。
 * <br>2026/11/06：目录与租约领取失败分别退避，坏分区不阻塞其他分区，不误报模型失败。
 * <br>2026/11/06：按有效新证据聚合长期提取，保留版本幂等、租约和事务校验。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.6.1-l
 * @version 4.8.6-l
 */
// 2026/11/04：展示声明不改变原任务调度和生命周期。
// 2026/11/04：补充任务用途、执行边界与网页控制说明。
@TimerDisplay(category = "agent.memory", categoryName = "Agent 记忆", name = "分层记忆整理",
        description = "轮询持久化的记忆分区，优先领取可整理的中期片段生成长期记忆，再按阈值压缩较旧的原始消息。"
        + "同一处理器最多一个在途生成任务，继续检查学习开关、原话证据、模型容量和租约；成功候选与进度一起提交，失败按持久退避重试。"
        + "扫描入口只读；停止生成应修改分层记忆或对应聊天的摘要、学习配置。", controllable = false)
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
    // 2026/11/06：未取得租约时不能写批次退避，使用有界进程状态控制实际读取频率。
    private final MemoryScanBackoff scanBackoff = new MemoryScanBackoff();
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
     * 2026/11/06：扫描完成处统一保存退避和报告，定时入口不重复记录同一异常。
     */
    @Scheduled(fixedDelayString = "${tea-neko.agent.memory.layered.poll-ms:5000}", initialDelayString = "${tea-neko.agent.memory.layered.poll-ms:5000}")
    public void tick() {
        drain();
    }
    /**
     * 执行一次有界扫描，可供显式宿主或测试等待真实完成。
     * 2026/11/04：按本次输入容量配置创建父 token 预算，覆盖一次完整的生成输出。
     * 2026/11/05：扫描只保护查询与领取，批次自行控制生成时限；结束前等待真实提交或退避。
     * 2026/11/06：目录及未处理的基础设施失败也限频，冷却期间不重复读库或输出日志。
     * @return 本次扫描和至多一个压缩任务完成阶段
     */
    public CompletionStage<Void> drain() {
        if (closed || !running.compareAndSet(false, true)) {
            return CompletableFuture.completedStage(null);
        }
        try {
            if (!scanBackoff.allows("processor", clock.instant())) {
                running.set(false);
                return CompletableFuture.completedStage(null);
            }
            return CompletableFuture.supplyAsync(() -> {
                var policy = settings.get();
                if (closed || !policy.enabled()) {
                    return CompletableFuture.<Void>completedStage(null);
                }
                // 2026/11/04：父控制保留调用次数，token 容量由选定模型的上下文声明约束。
                var control = ExecutionControl.withDeadline(policy.modelCallLimits().newBudget(),
                        clock.instant().plus(policy.timeout()), clock, scheduler);
                current = control;
                if (closed) {
                    control.cancel();
                }
                CompletionStage<Void> work;
                try {
                    control.checkActive();
                    work = readOrDefer("directory", "目录扫描", () -> store.streams(cursor), control).thenComposeAsync(read -> {
                        if (read.deferred()) {
                            return CompletableFuture.completedStage(null);
                        }
                        var streams = read.value();
                        if (streams.isEmpty()) {
                            cursor = "";
                            return CompletableFuture.completedStage(null);
                        }
                        return visit(streams, 0, control);
                    }, executor);
                } catch (RuntimeException failure) {
                    work = CompletableFuture.failedStage(failure);
                }
                // 2026/11/05：不能再用扫描控制包装整条链，否则超时会抢先释放 running 并重复报告批次失败。
                return work.whenComplete((ignored, failure) -> {
                    control.close();
                    if (current == control) {
                        current = null;
                    }
                });
            }, executor).thenCompose(stage -> stage).whenComplete((ignored, failure) -> scanFinished(failure));
        } catch (RuntimeException failure) {
            scanFinished(failure);
            return CompletableFuture.failedStage(failure);
        }
    }
    /**
     * 逐个检查目录直至领取一个任务，持久目录游标轮转避免单分区饥饿。
     * 2026/11/05：领取成功后转交独立批次控制，扫描耗时不再占用生成额度。
     * 2026/11/06：长期领取失败只暂缓该阶段，仍可压缩当前分区和访问后续分区。
     * @param streams 本次目录页
     * @param index 当前位置
     * @param control 本页目录读取和租约领取的控制
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
                    ? readOrDefer("long:" + stream.id(), "长期租约领取", () -> longStore.claim(stream, clock.instant()), control)
                    : CompletableFuture.completedStage(new ScanRead<>(false, Optional.<LongMemories.Batch>empty()));
            return candidates.thenComposeAsync(read -> {
                if (!read.deferred() && read.value().isPresent()) {
                    return processClaimed(control, policySettings, batchControl -> processLong(read.value().orElseThrow(), batchControl));
                }
                if (!policy.compress()) { return visit(streams, index + 1, control); }
                return readOrDefer("medium:" + stream.id(), "中期租约领取",
                        () -> store.claim(stream, policySettings, clock.instant()), control).thenComposeAsync(shortRead -> {
                    if (!shortRead.deferred() && shortRead.value().isPresent()) {
                        return processClaimed(control, policySettings, batchControl -> process(shortRead.value().orElseThrow(), batchControl));
                    }
                    return visit(streams, index + 1, control);
                }, executor);
            }, executor);
        }, executor);
    }
    /**
     * 2026/11/06：读取失败明确返回暂缓，不将损坏记录当作合法空批次或写回默认值。
     * @param key 目录或完整分区阶段键
     * @param phase 固定阶段说明
     * @param source 实际异步读取，冷却时不调用
     * @param control 本轮扫描控制
     * @param <T> 原读取结果类型
     * @return 实际结果或已报告、待重试的暂缓状态
     */
    private <T> CompletionStage<ScanRead<T>> readOrDefer(String key, String phase,
            Supplier<CompletionStage<T>> source, ExecutionControl control) {
        control.checkActive();
        if (!scanBackoff.allows(key, clock.instant())) {
            return CompletableFuture.completedStage(new ScanRead<>(true, null));
        }
        CompletionStage<T> reading;
        try {
            reading = source.get();
        } catch (RuntimeException failure) {
            reading = CompletableFuture.failedStage(failure);
        }
        return control.guard(reading).handleAsync((value, failure) -> {
            control.checkActive();
            if (failure == null) {
                scanBackoff.succeeded(key);
                return new ScanRead<>(false, Objects.requireNonNull(value));
            }
            var next = scanBackoff.failed(key, clock.instant());
            reportReadFailure(phase, key, control.id(), failure, next);
            return new ScanRead<>(true, null);
        }, executor);
    }

    /**
     * 2026/11/06：未被分区恢复处理的扫描、时限或退避写入失败也不能每个 tick 重复执行。
     * @param failure 最终真实失败，成功时为空
     */
    private void scanFinished(@Nullable Throwable failure) {
        try {
            if (failure == null) {
                scanBackoff.succeeded("processor");
            } else {
                var next = scanBackoff.failed("processor", clock.instant());
                reportReadFailure("扫描或退避保存", "processor", null, failure, next);
            }
        } finally {
            running.set(false);
        }
    }

    /**
     * 2026/11/06：无租约故障不套用模型三次暂停说明，不导出分区名、原文或异常消息。
     * @param phase 固定阶段
     * @param key 完整目标，仅输出摘要
     * @param request 扫描控制编号，尚未分配时为空
     * @param failure 实际失败
     * @param next 本进程允许再次尝试的时刻
     */
    private void reportReadFailure(String phase, String key, @Nullable UUID request, Throwable failure, Instant next) {
        logger.warn("Agent 分层记忆", "记忆扫描或领取未完成。请求=" + (request == null ? "未分配" : request)
                + "，阶段=" + phase + "，目标摘要=" + StableDigest.sha256(key).substring(0, 12)
                + "，" + FailureDiagnostic.from(failure) + "。当前进程退避至=" + next
                + "；冷却期间不重复读取或告警，不据此重置记录或跳过整理进度；此失败不计为模型无效生成。");
    }

    /**
     * 区分暂缓读取与实际返回的空结果，暂缓不代表不存在待处理记录。
     * @param deferred 是否因失败或冷却暂缓
     * @param result 实际读取结果，暂缓时为空
     * @param <T> 原读取类型
     * @author zExNocs
     * @date 2026/11/06
     * @since 4.8.3-l
     * @version 4.8.3-l
     */
    private record ScanRead<T>(boolean deferred, @Nullable T result) {
        /** @return 仅在读取成功后访问的实际结果 */
        private T value() { return Objects.requireNonNull(result); }
    }
    /**
     * 2026/11/05：领取后开始完整的生成时限，批次结束前保留在途占用及实际持久化结果。
     * @param scanning 即将释放的扫描控制
     * @param policy 领取本批时使用的预算
     * @param processing 借用批次控制并完成提交或退避的处理
     * @return 实际批次完成阶段；控制由本方法在其完成后关闭
     */
    private CompletionStage<Void> processClaimed(ExecutionControl scanning, LayeredMemorySettings policy,
            Function<ExecutionControl, CompletionStage<Void>> processing) {
        scanning.checkActive();
        var control = ExecutionControl.withDeadline(policy.modelCallLimits().newBudget(),
                clock.instant().plus(policy.timeout()), clock, scheduler);
        current = control;
        scanning.close();
        if (closed) {
            control.cancel();
        }
        CompletionStage<Void> work;
        try {
            work = processing.apply(control);
        } catch (RuntimeException failure) {
            work = CompletableFuture.failedStage(failure);
        }
        return work.whenComplete((ignored, failure) -> {
            control.close();
            if (current == control) {
                current = null;
            }
        });
    }
    /**
     * 2026/11/02：等待语义选择后重新检查学习和空间，零候选也按真实提交推进进度。
     * 2026/11/04：区分观察准备、模型生成、资格复核和持久提交，不导出候选原文。
     * 2026/11/05：无合格观察目标不等于撤权；空批次仍在事务内复核真实学习开关。
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
            if (batch.evidence().isEmpty()
                    || basis == null && !Boolean.TRUE.equals(learning.apply(batch.stream().reference().scope()))) {
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
                boolean factsAllowed = Boolean.TRUE.equals(learning.apply(batch.stream().reference().scope()));
                var facts = factsAllowed ? values.analysis().memories() : List.<LongMemories.Candidate>of();
                boolean styleAllowed = observations != null && observations.enabled(batch.stream().reference().scope()) && values.basis() != null;
                var styles = styleAllowed ? values.analysis().styles() : List.<StyleObservations.Candidate>of();
                var styleGuard = styleAllowed ? java.util.Objects.requireNonNull(observations)
                        .guard(batch, java.util.Objects.requireNonNull(values.basis()), control) : (Runnable) () -> {};
                // 2026/11/05：在真实事务入口及提交前分别核对两个学习域，不能以人格授权代替事实授权。
                Runnable guard = () -> {
                    control.checkActive();
                    if (closed || !settings.get().enabled()
                            || factsAllowed && !Boolean.TRUE.equals(learning.apply(batch.stream().reference().scope()))
                            || !factsAllowed && (observations == null
                            || !observations.enabled(batch.stream().reference().scope()))) {
                        throw new MemoryProcessingException(MemoryProcessingException.Reason.ACCESS_REVOKED);
                    }
                    styleGuard.run();
                };
                phase.set("长期持久提交");
                return target.commit(batch, new StyleObservations.Analysis(facts, styles), values.basis(), clock.instant(), guard).thenApply(committed -> {
                    if (!committed) {
                        throw new MemoryProcessingException(MemoryProcessingException.Reason.LEASE_CHANGED);
                    }
                    return (Void) null;
                });
            }, executor);
        }, executor).exceptionallyCompose(failure -> target.retry(batch, clock.instant(), failure).thenRun(() ->
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
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @param basis 原目标
     * @param analysis 模型候选
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.6.1-l
     * @version 4.8.0-l
     */
    private record Generated(StyleObservations.@Nullable Target basis, StyleObservations.Analysis analysis) { }
    /**
     * 等待真实模型结果、复核当前策略并提交，失败只保留退避，不推进水位。
     * 2026/11/04：记录实际失败阶段、模型状态和原请求编号，继续使用原租约退避。
     * @param batch 原租约及输入
     * @param control 领取成功后建立的本批控制
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
     * 2026/11/05：明确区分达到时限、主动取消及资源关闭，并提供超时参数入口。
     * 2026/11/06：持久格式错误独立分类，仅对无效模型候选说明三次暂停策略。
     * @param namespace 当前记忆处理域
     * @param phase 最后实际进入的处理阶段
     * @param request 原模型请求编号，扫描尚未建立控制时为空
     * @param lease 原租约编号，目录任务为空
     * @param failure 实际失败
     */
    private void reportFailure(String namespace, String phase, @Nullable UUID request,
            @Nullable UUID lease, Throwable failure) {
        // 2026/11/05：关系校验异常保留候选位置，不与基础设施失败或模型配置错误混为一类。
        MemoryProcessingException classified = null;
        MemoryEvidenceException evidence = null;
        boolean storageFailure = false;
        ExecutionControl.StopReason stopped = null;
        Throwable currentFailure = failure;
        label:
        for (int depth = 0; currentFailure != null && depth < 16; depth++) {
            switch (currentFailure) {
                case MemoryStorageException _:
                    storageFailure = true;
                    break label;
                case MemoryEvidenceException value:
                    evidence = value;
                    break label;
                case MemoryProcessingException value:
                    classified = value;
                    break label;
                case org.zexnocs.teanekocore.actuator.execution.ExecutionStoppedException value:
                    stopped = value.reason();
                    break;
                default:
                    break;
            }
            currentFailure = currentFailure.getCause();
        }
        String code = "PROCESSING_FAILED";
        String explanation = "检查异常类型及代码位置";
        if (storageFailure) {
            code = "MEMORY_STORAGE_FORMAT";
            explanation = "持久记忆数据与当前结构不匹配；核对记录类型和字段，不会自动重置原记录";
        } else if (evidence != null) {
            code = evidence.code().name();
            explanation = evidence.code().description();
        } else if (classified != null) {
            code = classified.reason().name();
            explanation = classified.reason().description();
        } else if (stopped != null) {
            code = "EXECUTION_" + stopped.name();
            explanation = switch (stopped) {
                case DEADLINE_EXCEEDED -> "当前阶段达到截止时间；请检查模型响应或数据库耗时，时限由 "
                        + "tea-neko.agent.memory.layered.timeout-seconds 配置";
                case CANCELLED -> "整理任务已取消";
                case CLOSED -> "整理执行资源已关闭";
            };
        }
        String status = classified == null || classified.modelStatus() == null ? "未取得" : classified.modelStatus().name();
        if (evidence != null && evidence.modelStatus() != null) {
            status = evidence.modelStatus().name();
        }
        String previousRequest = MDC.get("requestId");
        try {
            if (request != null) {
                MDC.put("requestId", request.toString());
            }
            logger.warn(namespace, "记忆整理未完成。请求=" + (request == null ? "未分配" : request)
                    + "，批次=" + (lease == null ? "无租约" : lease) + "，阶段=" + phase + "，原因=" + code
                    + "（" + explanation + "），模型状态=" + status + "，准入状态="
                    + (classified == null ? "未取得" : classified.accessStatus()) + "，" + FailureDiagnostic.from(failure)
                    + (namespace.equals("Agent 长期记忆") && LongMemoryJobs.Failure.from(failure).invalidCandidate()
                    ? "。本批不推进整理水位；按持久状态退避，同输入三次无效生成后暂停，可在工作区查看并恢复。"
                    : "。本批不推进整理水位，将按持久状态重试。"));
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
