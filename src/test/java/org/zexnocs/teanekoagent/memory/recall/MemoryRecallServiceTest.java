package org.zexnocs.teanekoagent.memory.recall;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.zexnocs.teanekoagent.agent.AgentActorContext;
import org.zexnocs.teanekoagent.agent.AgentScope;
import org.zexnocs.teanekoagent.memory.fact.*;
import org.zexnocs.teanekoagent.memory.namespace.MemoryNamespaceId;
import org.zexnocs.teanekoagent.memory.namespace.MemoryNamespaceReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 验证共享证据筛选、确定性排序、完整性及实际 JSON 预算，不依赖数据库或模型。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
@Timeout(10)
class MemoryRecallServiceTest {
    private final Instant now = Instant.parse("2026-10-29T01:00:00Z");
    private final AgentScope scope = new AgentScope("客户端", "群");
    private final UUID firstSubject = UUID.randomUUID();
    private final UUID secondSubject = UUID.randomUUID();
    private final MemoryNamespaceReference reference = new MemoryNamespaceReference(scope, MemoryNamespaceId.SHARED, 0, 0);
    private final MemoryFactSource source = new MemoryFactSource(UUID.randomUUID(), "事实写入", new AgentActorContext(scope,
            AgentActorContext.Origin.USER, Optional.of(firstSubject), "平台用户", "原消息", "群目标"));
    private final ObjectMapper mapper = new ObjectMapper();
    private final MemoryRecallSettings settings = new MemoryRecallSettings(12, 8, 20, 1_000_000, 1000);

    /**
     * 构造带固定未知事件时间的明确事实。
     * @param text 正文
     * @return 有界事实内容
     */
    private MemoryFactValue value(String text) {
        return new MemoryFactValue(MemoryFactValue.Kind.FACT, text, List.of(), 1, MemoryFactValue.Importance.NORMAL, MemoryEventTime.unknown(), null);
    }
    /**
     * 构造稳定 ID 和记录时间，主语与来源操作者保持独立。
     * @param number 稳定 ID 后缀
     * @param subject 主体
     * @param value 内容
     * @return 投影证据
     */
    private MemoryRecallEvidence evidence(int number, UUID subject, MemoryFactValue value) {
        return MemoryRecallEvidence.from(new MemoryFactRecord(new UUID(0, number), MemoryNamespaceId.SHARED, 0, 0, subject,
                value, source, now.minusSeconds(100), now.minusSeconds(50), false));
    }
    /**
     * 生成候选前缀，真实端口必须提供相同稳定排序。
     * @param evidence 候选
     * @param complete 是否扫描完
     * @return 有界候选
     */
    private MemoryRecallScan scan(List<MemoryRecallEvidence> evidence, boolean complete) {
        return new MemoryRecallScan(MemoryNamespaceId.SHARED, 0, evidence.stream().sorted(Comparator.comparing(item -> item.id().toString())).toList(), complete);
    }
    /**
     * 用实际 JSON 数组计量并使用当前线程模拟已处于业务域的宿主。
     * @param store 候选端口
     * @return 独立检索服务
     */
    private MemoryRecallService service(IMemoryRecallStore store) {
        return new MemoryRecallService(store, () -> settings, Clock.fixed(now, ZoneOffset.UTC), Runnable::run, items -> mapper.writeValueAsString(items).length());
    }
    /**
     * 执行可信当前引用的工具用途查询。
     * @param evidence 候选
     * @param query 条件
     * @param limits 单次预算
     * @return 已完成结果
     */
    private MemoryRecallResult recall(List<MemoryRecallEvidence> evidence, MemoryRecallQuery query, MemoryRecallLimits limits) {
        return service((id, generation, max) -> CompletableFuture.completedFuture(scan(evidence, true)))
                .recall(new MemoryRecallRequest(reference, query, MemoryRecallRequest.Use.TOOL, limits)).toCompletableFuture().join();
    }
    /**
     * 提取明确失败，不将来源异常当作正常无命中。
     * @param stage 检索阶段
     * @return 固定失败
     */
    private static Throwable failure(CompletionStage<?> stage) { return assertThrows(CompletionException.class, () -> stage.toCompletableFuture().join()).getCause(); }

    /** 验证构造与调用不在调度前读取配置或来源，配置和度量均由业务域执行。 */
    @Test void constructorAndSchedulingAreLazy() {
        var store = mock(IMemoryRecallStore.class); var defaults = mock(IMemoryRecallDefaults.class); var queue = new ArrayDeque<Runnable>();
        var service = new MemoryRecallService(store, defaults, Clock.fixed(now, ZoneOffset.UTC), queue::add, items -> mapper.writeValueAsString(items).length());
        verifyNoInteractions(store, defaults); when(defaults.settings()).thenReturn(settings);
        when(store.scan(MemoryNamespaceId.SHARED, 0, 1000)).thenReturn(CompletableFuture.completedFuture(scan(List.of(), true)));
        var result = service.recall(new MemoryRecallRequest(reference, MemoryRecallQuery.all(), MemoryRecallRequest.Use.TOOL, null)).toCompletableFuture();
        verifyNoInteractions(store); assertFalse(result.isDone()); queue.remove().run(); assertFalse(result.isDone()); queue.remove().run();
        assertTrue(result.join().definitivelyEmpty()); verify(defaults, times(1)).settings();
    }

    /** 验证同空间不同主语和来源 scope 的证据默认都可检索，归属与来源没有混淆。 */
    @Test void sharedNamespaceDoesNotImposeActorOrScopeIsolation() {
        var first = evidence(1, firstSubject, value("甲喜欢茶")); var actor = new AgentActorContext(new AgentScope("其他客户端", "其他群"),
                AgentActorContext.Origin.USER, Optional.of(secondSubject), "乙平台用户", "另一消息", "另一目标");
        var second = MemoryRecallEvidence.from(new MemoryFactRecord(new UUID(0, 2), MemoryNamespaceId.SHARED, 0, 0, secondSubject,
                value("乙喜欢书"), new MemoryFactSource(UUID.randomUUID(), "另一次新增", actor), now.minusSeconds(10), now.minusSeconds(10), false));
        var result = recall(List.of(first, second), MemoryRecallQuery.all(), new MemoryRecallLimits(20, 100000, 1000));
        assertEquals(Set.of(firstSubject, secondSubject), result.evidence().stream().map(MemoryRecallEvidence::subjectId).collect(java.util.stream.Collectors.toSet()));
        assertEquals(actor, result.evidence().getFirst().source().actor()); assertTrue(result.limits().isEmpty());
    }

    /** 验证明确主语、来源、类型、全部标签和全部关键词组合，不搜索身份文本。 */
    @Test void explicitFiltersCombineWithoutSearchingAuditOrActor() {
        var preferred = new MemoryFactValue(MemoryFactValue.Kind.PREFERENCE, "喜欢 GREEN Tea", List.of("饮品", "Tea"), .8,
                MemoryFactValue.Importance.HIGH, MemoryEventTime.unknown(), null);
        var items = List.of(evidence(1, firstSubject, preferred), evidence(2, secondSubject, preferred), evidence(3, firstSubject, value("喜欢 GREEN Tea")));
        var query = new MemoryRecallQuery(firstSubject, scope, Set.of(MemoryFactValue.Kind.PREFERENCE), Set.of("饮品"), List.of("green", "TEA"), null, null, true);
        var result = recall(items, query, new MemoryRecallLimits(20, 100000, 1000)); assertEquals(List.of(items.getFirst()), result.evidence());
        var identityOnly = new MemoryRecallQuery(null, null, Set.of(), Set.of(), List.of("平台用户"), null, null, true);
        assertTrue(recall(items, identityOnly, new MemoryRecallLimits(20, 100000, 1000)).definitivelyEmpty());
        var otherSource = new MemoryRecallQuery(null, new AgentScope("客户端", "另一群"), Set.of(), Set.of(), List.of(), null, null, true);
        assertTrue(recall(items, otherSource, new MemoryRecallLimits(20, 100000, 1000)).definitivelyEmpty());
    }

    /** 验证关键词使用 ROOT 小写及字面匹配，不把正则、SQL 或多余空白解释成特殊指令。 */
    @Test void keywordMatchingIsLiteralAndLocaleIndependent() {
        var item = evidence(1, firstSubject, value("喜欢 Iced Tea，不喜欢其他饮品"));
        var query = new MemoryRecallQuery(null, null, Set.of(), Set.of(), List.of(" ICED ", "tea", "TEA"), null, null, true);
        assertEquals(List.of("iced", "tea"), query.keywords()); assertEquals(1, recall(List.of(item), query, new MemoryRecallLimits(1, 10000, 10)).matched());
        var regex = new MemoryRecallQuery(null, null, Set.of(), Set.of(), List.of(".*"), null, null, true);
        assertTrue(recall(List.of(item), regex, new MemoryRecallLimits(1, 10000, 10)).definitivelyEmpty());
    }

    /** 验证未知事件按记录时间估算且明确标记，严格查询排除估算，已知时间按闭区间命中。 */
    @Test void eventTimeAndRecordedEstimateStayDistinct() {
        var unknown = evidence(1, firstSubject, value("未知发生时刻")); var point = now.minusSeconds(100);
        var exact = evidence(2, firstSubject, new MemoryFactValue(MemoryFactValue.Kind.FACT, "延迟记录的已知事件", List.of(), 1,
                MemoryFactValue.Importance.NORMAL, new MemoryEventTime(point, point, MemoryEventTime.Precision.EXACT, "刚才"), null));
        var query = new MemoryRecallQuery(null, null, Set.of(), Set.of(), List.of(), point, point, true);
        var result = recall(List.of(unknown, exact), query, new MemoryRecallLimits(20, 100000, 1000)); assertEquals(2, result.matched());
        assertEquals(MemoryRecallEvidence.TimeBasis.RECORDED_TIME_ESTIMATE, result.evidence().getFirst().timeBasis());
        var strict = new MemoryRecallQuery(null, null, Set.of(), Set.of(), List.of(), point, point, false);
        assertEquals(List.of(exact), recall(List.of(unknown, exact), strict, new MemoryRecallLimits(20, 100000, 1000)).evidence());
        var missed = new MemoryRecallQuery(null, null, Set.of(), Set.of(), List.of(), point.plusNanos(1), null, true);
        assertTrue(recall(List.of(unknown, exact), missed, new MemoryRecallLimits(20, 100000, 1000)).definitivelyEmpty());
    }

    /** 验证到期瞬间排除事实，单次查询只读取一次 Clock 并沿用固定时刻。 */
    @Test void expiryUsesOneFixedQueryInstant() {
        var clock = mock(Clock.class); when(clock.instant()).thenReturn(now, now.plusSeconds(100));
        var expired = evidence(1, firstSubject, new MemoryFactValue(MemoryFactValue.Kind.FACT, "已到期", List.of(), 1,
                MemoryFactValue.Importance.NORMAL, MemoryEventTime.unknown(), now));
        var live = evidence(2, firstSubject, new MemoryFactValue(MemoryFactValue.Kind.FACT, "稍后到期", List.of(), 1,
                MemoryFactValue.Importance.NORMAL, MemoryEventTime.unknown(), now.plusNanos(1)));
        var service = new MemoryRecallService((id, gen, max) -> CompletableFuture.completedFuture(scan(List.of(expired, live), true)), () -> settings,
                clock, Runnable::run, items -> mapper.writeValueAsString(items).length());
        var result = service.recall(new MemoryRecallRequest(reference, MemoryRecallQuery.all(), MemoryRecallRequest.Use.INJECTION, null)).toCompletableFuture().join();
        assertEquals(List.of(live), result.evidence()); assertEquals(now, result.observedAt()); verify(clock, times(1)).instant();
    }

    /** 验证重要度、置信度、更新时间依次降序，完全相同时使用稳定 ID 升序。 */
    @Test void rankingHasStableTieBreaker() {
        var important = evidence(4, firstSubject, new MemoryFactValue(MemoryFactValue.Kind.FACT, "关键", List.of(), .1,
                MemoryFactValue.Importance.CRITICAL, MemoryEventTime.unknown(), null));
        var confident = evidence(3, firstSubject, value("高置信度"));
        var lower = new MemoryFactValue(MemoryFactValue.Kind.FACT, "同等级", List.of(), .5, MemoryFactValue.Importance.NORMAL, MemoryEventTime.unknown(), null);
        var one = evidence(1, firstSubject, lower); var two = evidence(2, firstSubject, lower);
        var newer = MemoryRecallEvidence.from(new MemoryFactRecord(new UUID(0, 5), MemoryNamespaceId.SHARED, 0, 0, firstSubject, lower, source, now, now, false));
        var result = recall(List.of(two, important, newer, one, confident), MemoryRecallQuery.all(), new MemoryRecallLimits(20, 100000, 1000));
        assertEquals(List.of(important, confident, newer, one, two), result.evidence());
    }

    /** 验证用途默认数和显式条数覆盖受本地硬上限约束，输出截断仍标记不完整。 */
    @Test void useDefaultsAndHardLimitApplyToExplicitRequests() {
        var items = new ArrayList<MemoryRecallEvidence>(); for (int i = 1; i <= 30; i++) { items.add(evidence(i, firstSubject, value("事实" + i))); }
        var service = service((id, gen, max) -> CompletableFuture.completedFuture(scan(items, true)));
        for (var use : MemoryRecallRequest.Use.values()) {
            var result = service.recall(new MemoryRecallRequest(reference, MemoryRecallQuery.all(), use, null)).toCompletableFuture().join();
            assertEquals(use == MemoryRecallRequest.Use.INJECTION ? 12 : 8, result.evidence().size()); assertEquals(Set.of(MemoryRecallResult.Limit.RESULTS), result.limits());
        }
        var explicit = service.recall(new MemoryRecallRequest(reference, MemoryRecallQuery.all(), MemoryRecallRequest.Use.TOOL,
                new MemoryRecallLimits(100, 1_000_000, 1000))).toCompletableFuture().join(); assertEquals(20, explicit.evidence().size());
    }

    /** 验证扫描前缀没有命中时仍保留未知覆盖状态，完整无命中有独立判断。 */
    @Test void partialScanNeverClaimsDefinitiveAbsence() {
        var items = List.of(evidence(1, firstSubject, value("无关")), evidence(2, firstSubject, value("仍无关")));
        var query = new MemoryRecallQuery(null, null, Set.of(), Set.of(), List.of("未检查的目标"), null, null, true);
        var result = service((id, gen, max) -> CompletableFuture.completedFuture(scan(items, false))).recall(
                new MemoryRecallRequest(reference, query, MemoryRecallRequest.Use.TOOL, new MemoryRecallLimits(20, 100000, 2))).toCompletableFuture().join();
        assertTrue(result.evidence().isEmpty()); assertFalse(result.definitivelyEmpty()); assertEquals(Set.of(MemoryRecallResult.Limit.SCAN), result.limits());
        assertEquals(2, result.scanned()); assertEquals(0, result.matched());
        assertTrue(recall(items, query, new MemoryRecallLimits(20, 100000, 2)).definitivelyEmpty());
    }

    /** 验证实际 JSON 转义、元数据和数组分隔符参与字符预算，过长证据不被截断。 */
    @Test void characterBudgetCountsWholeJsonAndCanKeepSmallerEvidence() {
        var huge = evidence(1, firstSubject, value("较长证据".repeat(300)));
        var small = evidence(2, firstSubject, value("含引号\"、换行\n与表情😀"));
        int exact = mapper.writeValueAsString(List.of(small)).length();
        var result = recall(List.of(huge, small), MemoryRecallQuery.all(), new MemoryRecallLimits(20, exact, 1000));
        assertEquals(List.of(small), result.evidence()); assertEquals(exact, result.characters()); assertEquals(2, result.matched());
        assertEquals(Set.of(MemoryRecallResult.Limit.CHARACTERS), result.limits());
        var empty = recall(List.of(small), MemoryRecallQuery.all(), new MemoryRecallLimits(20, exact - 1, 1000));
        assertTrue(empty.evidence().isEmpty()); assertFalse(empty.definitivelyEmpty()); assertEquals(2, empty.characters());
        var both = recall(List.of(huge, small), MemoryRecallQuery.all(), new MemoryRecallLimits(20, 100000, 1000));
        assertEquals(mapper.writeValueAsString(both.evidence()).length(), both.characters());
    }

    /** 验证没有空间写权限信息或原始审计 JSON 进入检索投影，原始证据定位仍可追溯。 */
    @Test void importedProjectionDoesNotExposeRawAuditJson() {
        var provenance = new MemoryFactProvenance("旧来源", "旧目标", "旧键", "旧标识", "0".repeat(64), "{\"仅供审计\":\"秘密原文\"}", Set.of("审计调整"));
        var evidence = MemoryRecallEvidence.from(new MemoryFactRecord(new UUID(0, 1), MemoryNamespaceId.SHARED, 0, 0, firstSubject,
                value("允许检索的事实"), source, now, now, true, provenance));
        var result = recall(List.of(evidence), MemoryRecallQuery.all(), new MemoryRecallLimits(20, 100000, 1000)); var json = mapper.writeValueAsString(result);
        assertFalse(json.contains("秘密原文")); assertFalse(json.contains("审计调整")); assertFalse(json.contains("originalJson"));
        assertEquals("旧标识", Objects.requireNonNull(result.evidence().getFirst().externalOrigin()).externalId()); assertTrue(result.evidence().getFirst().locked());
    }

    /** 验证非法配置与暂不可用分别失败，配置修复后重新读取并恢复。 */
    @Test void configurationFailureDoesNotQueryOrMasqueradeAsEmpty() {
        var valid = new AtomicBoolean(false); var calls = new AtomicInteger();
        var service = new MemoryRecallService((id, gen, max) -> { calls.incrementAndGet(); return CompletableFuture.completedFuture(scan(List.of(), true)); },
                () -> { if (!valid.get()) { throw new IllegalStateException("配置秘密值"); } return settings; }, Clock.fixed(now, ZoneOffset.UTC), Runnable::run,
                items -> mapper.writeValueAsString(items).length());
        var request = new MemoryRecallRequest(reference, MemoryRecallQuery.all(), MemoryRecallRequest.Use.TOOL, null);
        var error = assertInstanceOf(MemoryRecallException.class, failure(service.recall(request))); assertEquals(MemoryRecallException.Reason.CONFIGURATION_ERROR, error.reason());
        assertNull(error.getCause()); assertFalse(error.getMessage().contains("秘密")); assertEquals(0, calls.get());
        valid.set(true); assertTrue(service.recall(request).toCompletableFuture().join().definitivelyEmpty()); assertEquals(1, calls.get());
    }

    /** 验证来源同步/异步失败和损坏返回都异常结束，不将原异常链传播到调用端。 */
    @Test void invalidSourcesFailWithoutLeakingTheirCause() {
        var sources = List.<IMemoryRecallStore>of((id, gen, max) -> { throw new IllegalStateException("数据库秘密"); },
                (id, gen, max) -> CompletableFuture.failedFuture(new CompletionException(new IllegalStateException("数据库秘密"))),
                (id, gen, max) -> null, (id, gen, max) -> CompletableFuture.completedFuture(null),
                (id, gen, max) -> CompletableFuture.completedFuture(new MemoryRecallScan(new MemoryNamespaceId("other"), 0, List.of(), true)),
                (id, gen, max) -> CompletableFuture.completedFuture(scan(List.of(evidence(1, firstSubject, value("甲")), evidence(2, firstSubject, value("乙"))), true)));
        for (var source : sources) {
            var error = assertInstanceOf(MemoryRecallException.class, failure(service(source).recall(new MemoryRecallRequest(reference,
                    MemoryRecallQuery.all(), MemoryRecallRequest.Use.TOOL, new MemoryRecallLimits(20, 100000, 1)))));
            assertEquals(MemoryRecallException.Reason.UNAVAILABLE, error.reason()); assertNull(error.getCause()); assertFalse(error.getMessage().contains("秘密"));
        }
    }

    /** 验证代次失效与空间缺失保持独立分类，并去掉人为附加的敏感 suppressed 异常。 */
    @Test void generationFailuresRetainOnlySafeClassification() {
        for (boolean missing : List.of(true, false)) {
            var original = new MemoryFactGenerationException(missing); original.addSuppressed(new IllegalStateException("秘密正文"));
            var error = assertInstanceOf(MemoryFactGenerationException.class, failure(service((id, gen, max) -> CompletableFuture.failedFuture(original)).recall(
                    new MemoryRecallRequest(reference, MemoryRecallQuery.all(), MemoryRecallRequest.Use.TOOL, null))));
            assertEquals(missing, error.missingNamespace()); assertNull(error.getCause()); assertEquals(0, error.getSuppressed().length);
        }
    }

    /** 验证初次和完成回调调度被拒绝都结束 Future，不能留下永不完成的检索。 */
    @Test void executorRejectionAtEitherBoundaryFinishesResult() {
        for (int rejectedCall : List.of(1, 2)) {
            var calls = new AtomicInteger(); var service = new MemoryRecallService((id, gen, max) -> CompletableFuture.completedFuture(scan(List.of(), true)),
                    () -> settings, Clock.fixed(now, ZoneOffset.UTC), action -> { if (calls.incrementAndGet() == rejectedCall) { throw new RejectedExecutionException("测试拒绝调度。"); } action.run(); },
                    items -> mapper.writeValueAsString(items).length());
            var error = assertInstanceOf(MemoryRecallException.class, failure(service.recall(new MemoryRecallRequest(reference, MemoryRecallQuery.all(), MemoryRecallRequest.Use.TOOL, null))));
            assertEquals(MemoryRecallException.Reason.UNAVAILABLE, error.reason());
        }
    }

    /**
     * 验证数据库完成线程不执行筛选/度量，完成回调明确重新排队到业务域。
     * @throws InterruptedException 当测试完成线程等待被中断时
     */
    @Test void sourceCompletionMustReturnToBusinessExecutor() throws InterruptedException {
        var queue = new ConcurrentLinkedQueue<Runnable>(); var pending = new CompletableFuture<MemoryRecallScan>(); var measured = new AtomicReference<Thread>();
        var service = new MemoryRecallService((id, gen, max) -> pending, () -> settings, Clock.fixed(now, ZoneOffset.UTC), queue::add,
                items -> { measured.set(Thread.currentThread()); return mapper.writeValueAsString(items).length(); });
        var result = service.recall(new MemoryRecallRequest(reference, MemoryRecallQuery.all(), MemoryRecallRequest.Use.TOOL, null)).toCompletableFuture();
        Objects.requireNonNull(queue.poll()).run(); var databaseThread = Thread.ofPlatform().start(() -> pending.complete(scan(List.of(), true))); databaseThread.join();
        assertNull(measured.get()); assertFalse(result.isDone()); Objects.requireNonNull(queue.poll()).run(); assertTrue(result.join().definitivelyEmpty());
        assertNotSame(databaseThread, measured.get());
    }

    /** 验证取消观察不取消来源任务，错误度量器异常结束而不是越过字符预算。 */
    @Test void observerCancellationAndBrokenMeasurerHaveExplicitSemantics() {
        var pending = new CompletableFuture<MemoryRecallScan>(); var result = service((id, gen, max) -> pending).recall(
                new MemoryRecallRequest(reference, MemoryRecallQuery.all(), MemoryRecallRequest.Use.TOOL, null)).toCompletableFuture();
        assertTrue(result.cancel(false)); assertFalse(pending.isCancelled()); pending.complete(scan(List.of(), true));
        for (int invalid : List.of(-1, 0, 1, 20)) {
            var service = new MemoryRecallService((id, gen, max) -> CompletableFuture.completedFuture(scan(List.of(), true)), () -> settings,
                    Clock.fixed(now, ZoneOffset.UTC), Runnable::run, items -> invalid);
            assertInstanceOf(MemoryRecallException.class, failure(service.recall(new MemoryRecallRequest(reference, MemoryRecallQuery.all(), MemoryRecallRequest.Use.TOOL, null))));
        }
    }

    /** 验证条件防御性冻结、非法时间/预算及坏候选排序在入口被拒绝。 */
    @Test void queryAndCandidateContractsRejectAmbiguousInputs() {
        var tags = new HashSet<>(Set.of("茶")); var words = new ArrayList<>(List.of(" 茶 "));
        var query = new MemoryRecallQuery(null, null, Set.of(), tags, words, null, null, true); tags.clear(); words.clear();
        assertEquals(Set.of("茶"), query.tags()); assertEquals(List.of("茶"), query.keywords());
        assertThrows(IllegalArgumentException.class, () -> new MemoryRecallQuery(null, null, Set.of(), Set.of(), List.of(" "), null, null, true));
        assertThrows(IllegalArgumentException.class, () -> new MemoryRecallQuery(null, null, Set.of(), Set.of(), List.of(), now, now.minusNanos(1), true));
        assertThrows(IllegalArgumentException.class, () -> new MemoryRecallLimits(101, 1000, 1000));
        assertThrows(IllegalArgumentException.class, () -> new MemoryRecallLimits(20, 1000, 10001));
        var item = evidence(1, firstSubject, value("事实"));
        assertThrows(IllegalArgumentException.class, () -> new MemoryRecallScan(MemoryNamespaceId.SHARED, 0, List.of(item, item), true));
        assertThrows(IllegalArgumentException.class, () -> new MemoryRecallScan(MemoryNamespaceId.SHARED, 0, List.of(), false));
    }

    /** 验证在途查询固定首次策略，之后的查询才能使用更新后的条数和工作量预算。 */
    @Test void configurationChangesApplyOnlyToNextQuery() {
        var snapshot = new AtomicReference<>(new MemoryRecallSettings(1, 1, 20, 100000, 10));
        var pending = new CompletableFuture<MemoryRecallScan>(); var scannedLimit = new AtomicInteger();
        var service = new MemoryRecallService((id, gen, max) -> { scannedLimit.set(max); return pending; }, snapshot::get,
                Clock.fixed(now, ZoneOffset.UTC), Runnable::run, items -> mapper.writeValueAsString(items).length());
        var request = new MemoryRecallRequest(reference, MemoryRecallQuery.all(), MemoryRecallRequest.Use.TOOL, new MemoryRecallLimits(100, 1_000_000, 10000));
        var first = service.recall(new MemoryRecallRequest(reference, MemoryRecallQuery.all(), MemoryRecallRequest.Use.TOOL, null)); assertEquals(10, scannedLimit.get());
        snapshot.set(new MemoryRecallSettings(2, 2, 3, 100000, 5));
        pending.complete(scan(List.of(evidence(1, firstSubject, value("甲")), evidence(2, firstSubject, value("乙")), evidence(3, firstSubject, value("丙"))), true));
        assertEquals(1, first.toCompletableFuture().join().evidence().size());
        var second = service.recall(request).toCompletableFuture().join(); assertEquals(5, scannedLimit.get()); assertEquals(3, second.evidence().size());
    }
}
