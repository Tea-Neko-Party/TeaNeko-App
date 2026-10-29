package org.zexnocs.teanekoagent.memory.recall;

import org.zexnocs.teanekoagent.memory.fact.MemoryFactGenerationException;

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.ToIntFunction;

/**
 * 组合有界候选、确定性筛选排序与证据预算；存储和 JSON 度量通过明确依赖替换。
 * 配置、时钟、筛选和度量均在借用的业务执行域运行，不占数据库完成线程或拥有线程池。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
public final class MemoryRecallService implements IMemoryRecallService {
    private static final Comparator<MemoryRecallEvidence> ORDER = Comparator.comparing((MemoryRecallEvidence item) -> item.value().importance()).reversed()
            .thenComparing(Comparator.comparingDouble((MemoryRecallEvidence item) -> item.value().confidence()).reversed())
            .thenComparing(MemoryRecallEvidence::updatedAt, Comparator.reverseOrder()).thenComparing(item -> item.id().toString());
    private final IMemoryRecallStore store;
    private final IMemoryRecallDefaults defaults;
    private final Clock clock;
    private final Executor executor;
    private final ToIntFunction<List<MemoryRecallEvidence>> measure;

    /**
     * 保存依赖，不解析配置、不访问存储或度量器。
     * @param store 稳定候选端口
     * @param defaults 每次查询策略
     * @param clock 查询时钟
     * @param executor 借用的业务执行器，不由本服务关闭
     * @param measure 可信证据数组 JSON 字符度量器，空数组必须为两个字符
     * @throws NullPointerException 当依赖为空时
     */
    public MemoryRecallService(IMemoryRecallStore store, IMemoryRecallDefaults defaults, Clock clock, Executor executor,
            ToIntFunction<List<MemoryRecallEvidence>> measure) {
        this.store = Objects.requireNonNull(store, "记忆候选来源不能为空。"); this.defaults = Objects.requireNonNull(defaults, "记忆检索默认策略不能为空。");
        this.clock = Objects.requireNonNull(clock, "记忆检索时钟不能为空。"); this.executor = Objects.requireNonNull(executor, "记忆检索业务执行器不能为空。");
        this.measure = Objects.requireNonNull(measure, "记忆证据度量器不能为空。");
    }
    /**
     * 在业务域固定本次策略与时间，候选完成后再次切回业务域；失败不转换为空结果。
     * 取消返回观察视图不撤销已提交的只读数据库任务。
     * @param request 可信固定请求
     * @return 完整或受限的证据结果，失败携带固定分类
     * @throws NullPointerException 当请求为空时
     */
    @Override public CompletionStage<MemoryRecallResult> recall(MemoryRecallRequest request) {
        Objects.requireNonNull(request, "记忆检索请求不能为空。"); var result = new CompletableFuture<MemoryRecallResult>();
        dispatch(result, () -> {
            final MemoryRecallLimits limits;
            try { limits = Objects.requireNonNull(defaults.settings()).resolve(request); }
            catch (RuntimeException invalid) { throw new MemoryRecallException(MemoryRecallException.Reason.CONFIGURATION_ERROR); }
            var now = Objects.requireNonNull(clock.instant(), "检索时钟不能返回空时间。");
            var reference = request.reference();
            var stage = Objects.requireNonNull(store.scan(reference.namespaceId(), reference.generation(), limits.maxScanned()), "检索来源不能返回空阶段。");
            stage.whenComplete((scan, failure) -> dispatch(result, () -> {
                if (failure != null) { throw classify(failure); }
                result.complete(select(request, limits, now, Objects.requireNonNull(scan, "检索来源不能返回空候选。")));
            }));
        });
        return result.minimalCompletionStage();
    }
    /**
     * 有界排序并装入完整证据，过长条目跳过但不截断正文或伪装成完整结果。
     * @param request 固定查询
     * @param budget 实际预算
     * @param now 固定时刻
     * @param scan 当前读取边界的候选
     * @return 带覆盖程度和实际负载字符数的结果
     * @throws IllegalStateException 当来源或度量器违反合同、跨空间或越界时
     */
    private MemoryRecallResult select(MemoryRecallRequest request, MemoryRecallLimits budget, Instant now, MemoryRecallScan scan) {
        if (!scan.namespaceId().equals(request.reference().namespaceId()) || scan.generation() != request.reference().generation()
                || scan.evidence().size() > budget.maxScanned() || !scan.complete() && scan.evidence().size() != budget.maxScanned()) {
            throw new IllegalStateException("检索来源违反固定空间或扫描预算合同。");
        }
        var matched = scan.evidence().stream().filter(item -> request.query().matches(item, now)).sorted(ORDER).toList();
        var selected = new ArrayList<MemoryRecallEvidence>(); var limits = EnumSet.noneOf(MemoryRecallResult.Limit.class);
        if (!scan.complete()) { limits.add(MemoryRecallResult.Limit.SCAN); }
        int characters = measure.applyAsInt(List.of());
        if (characters != 2) { throw new IllegalStateException("检索度量器必须返回独立证据数组的实际字符数。"); }
        for (var evidence : matched) {
            if (selected.size() == budget.maxResults()) { limits.add(MemoryRecallResult.Limit.RESULTS); break; }
            selected.add(evidence); int next = measure.applyAsInt(List.copyOf(selected));
            if (next <= characters) { throw new IllegalStateException("检索证据度量器返回了无效长度。"); }
            if (next > budget.maxCharacters()) { selected.removeLast(); limits.add(MemoryRecallResult.Limit.CHARACTERS); }
            else { characters = next; }
        }
        return new MemoryRecallResult(request.reference(), now, selected, scan.evidence().size(), matched.size(), limits, characters);
    }
    /**
     * 在共享执行器上调度并统一完成异常，拒绝调度也必须结束 Future。
     * @param result 本次独立结果拥有者
     * @param action 业务动作
     */
    private void dispatch(CompletableFuture<MemoryRecallResult> result, Runnable action) {
        try {
            executor.execute(() -> {
                if (result.isDone()) { return; }
                try { action.run(); } catch (RuntimeException failure) { result.completeExceptionally(classify(failure)); }
            });
        } catch (RuntimeException rejected) { result.completeExceptionally(new MemoryRecallException(MemoryRecallException.Reason.UNAVAILABLE)); }
    }
    /**
     * 按已知分类重新构造异常以去掉原文与链，包装深度有限以避免异常结构影响查询。
     * @param failure 原失败
     * @return 固定分类异常
     */
    private static RuntimeException classify(Throwable failure) {
        var current = failure;
        for (int depth = 0; depth < 32 && (current instanceof CompletionException || current instanceof ExecutionException); depth++) {
            var cause = current.getCause(); if (cause == null || cause == current) { break; } current = cause;
        }
        if (current instanceof MemoryFactGenerationException generation) { return new MemoryFactGenerationException(generation.missingNamespace()); }
        if (current instanceof MemoryRecallException known) { return new MemoryRecallException(known.reason()); }
        return new MemoryRecallException(MemoryRecallException.Reason.UNAVAILABLE);
    }
}
