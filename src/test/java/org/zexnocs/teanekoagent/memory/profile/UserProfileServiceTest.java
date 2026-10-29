package org.zexnocs.teanekoagent.memory.profile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.ArgumentCaptor;
import org.zexnocs.teanekoagent._app_config.MemoryFactConfig;
import org.zexnocs.teanekoagent.agent.AgentActorContext;
import org.zexnocs.teanekoagent.agent.AgentScope;
import org.zexnocs.teanekoagent.memory.fact.*;
import org.zexnocs.teanekoagent.memory.namespace.MemoryNamespaceId;
import org.zexnocs.teanekoagent.memory.namespace.MemoryNamespaceReference;
import org.zexnocs.teanekoagent.memory.recall.*;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 验证画像的主体、证据版本、覆盖程度和异步边界，不将检索结果再写成新的个人事实。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
@Timeout(10)
class UserProfileServiceTest {
    private final Instant now = Instant.parse("2026-10-29T01:00:00Z");
    private final UUID subject = UUID.randomUUID();
    private final AgentScope scope = new AgentScope("客户端", "当前群");
    private final MemoryNamespaceReference reference = new MemoryNamespaceReference(scope, MemoryNamespaceId.SHARED, 3, 0);
    private final UserProfileRequest request = new UserProfileRequest(reference, subject, MemoryRecallRequest.Use.INJECTION, null);
    private final ObjectMapper mapper = new ObjectMapper();
    private final MemoryFactSource source = new MemoryFactSource(UUID.randomUUID(), "明确新增", new AgentActorContext(
            new AgentScope("其他客户端", "其他群"), AgentActorContext.Origin.USER, Optional.of(UUID.randomUUID()), "平台来源", "原消息", "原目标"));

    /**
     * 构造独立来源的可追溯事实，不以来源操作者代替画像主体。
     * @param id 稳定标识
     * @param owner 事实主体
     * @param kind 事实或偏好
     * @param text 原文
     * @return 保留内容 revision 的证据
     */
    private MemoryRecallEvidence evidence(int id, UUID owner, MemoryFactValue.Kind kind, String text) {
        var value = new MemoryFactValue(kind, text, List.of("明确资料"), .8, MemoryFactValue.Importance.HIGH, MemoryEventTime.unknown(), null);
        return MemoryRecallEvidence.from(new MemoryFactRecord(new UUID(0, id), MemoryNamespaceId.SHARED, 0, 7, owner, value, source,
                now.minusSeconds(100), now.minusSeconds(10), true));
    }

    /**
     * 模拟一份已验证的独立 Recall 结果，字符使用实际 JSON 度量。
     * @param evidence 有序证据
     * @param matched 已检查范围命中数
     * @param limits 覆盖限制
     * @return 原证据结果
     */
    private MemoryRecallResult result(List<MemoryRecallEvidence> evidence, int matched, Set<MemoryRecallResult.Limit> limits) {
        return new MemoryRecallResult(reference, now, evidence, Math.max(5, matched), matched, limits, mapper.writeValueAsString(evidence).length());
    }

    /**
     * 用已完成端口构造显式业务域内的画像服务。
     * @param result 原结果
     * @return 不拥有执行资源的画像服务
     */
    private UserProfileService service(MemoryRecallResult result) { return new UserProfileService(ignored -> CompletableFuture.completedFuture(result), Runnable::run); }

    /**
     * 提取失败类别，不把异常当成正常空画像。
     * @param stage 画像阶段
     * @return 根失败
     */
    private static Throwable failure(CompletionStage<?> stage) { return assertThrows(CompletionException.class, () -> stage.toCompletableFuture().join()).getCause(); }

    /** 验证真实 Bean 构造没有 I/O，并且业务执行器仍由宿主拥有。 */
    @Test void compositionIsLazyAndBorrowsExecutor() {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var recall = mock(IMemoryRecallService.class); var service = new MemoryFactConfig().userProfileService(recall, executor);
            verifyNoInteractions(recall); assertInstanceOf(IUserProfileService.class, service); assertFalse(executor.isShutdown());
        }
    }

    /** 验证初始读取只在调度后发生，来源完成后投影也必须回到业务执行域。 */
    @Test void requestAndProjectionUseExplicitBusinessExecutor() {
        var queue = new ArrayDeque<Runnable>(); var pending = new CompletableFuture<MemoryRecallResult>(); var calls = new AtomicInteger();
        var service = new UserProfileService(ignored -> { calls.incrementAndGet(); return pending; }, queue::add);
        var view = service.read(request).toCompletableFuture(); assertEquals(0, calls.get()); assertFalse(view.isDone());
        queue.remove().run(); assertEquals(1, calls.get()); pending.complete(result(List.of(), 0, Set.of())); assertFalse(view.isDone());
        queue.remove().run(); assertTrue(view.join().definitivelyEmpty());
    }

    /** 验证一次查询固定唯一主体及全部事实/偏好，来源 scope 与调用操作者不成为隐式过滤。 */
    @Test void profileUsesOneExplicitSubjectQueryAndRetainsEvidenceRevision() {
        var recall = mock(IMemoryRecallService.class); var item = evidence(1, subject, MemoryFactValue.Kind.FACT, "明确事实");
        when(recall.recall(any())).thenReturn(CompletableFuture.completedFuture(result(List.of(item), 1, Set.of())));
        var limits = new MemoryRecallLimits(20, 100000, 1000); var explicit = new UserProfileRequest(reference, subject, MemoryRecallRequest.Use.TOOL, limits);
        var view = new UserProfileService(recall, Runnable::run).read(explicit).toCompletableFuture().join();
        var captured = ArgumentCaptor.forClass(MemoryRecallRequest.class); verify(recall, times(1)).recall(captured.capture()); var sent = captured.getValue();
        assertEquals(reference, sent.reference()); assertEquals(subject, sent.query().subjectId()); assertNull(sent.query().sourceScope());
        assertEquals(Set.of(MemoryFactValue.Kind.FACT, MemoryFactValue.Kind.PREFERENCE), sent.query().kinds());
        assertTrue(sent.query().tags().isEmpty()); assertTrue(sent.query().keywords().isEmpty()); assertTrue(sent.query().includeEstimated());
        assertNull(sent.query().start()); assertNull(sent.query().end()); assertEquals(explicit.use(), sent.use()); assertSame(limits, sent.limits());
        assertSame(item, view.evidence().getFirst()); assertEquals(7, view.evidence().getFirst().revision()); assertEquals(source, view.evidence().getFirst().source());
        assertEquals(UserProfileView.PROJECTION_VERSION, view.projectionVersion()); assertEquals(reference, view.reference()); assertEquals(now, view.observedAt());
    }

    /** 验证分类仅由原类型决定，矛盾偏好和同标签事实保留来源，不推断性格或覆盖为单一结论。 */
    @Test void categoriesRetainConflictingEvidenceAndOriginalOrder() {
        var fact = evidence(1, subject, MemoryFactValue.Kind.FACT, "用户明确提供的称呼");
        var like = evidence(2, subject, MemoryFactValue.Kind.PREFERENCE, "喜欢茶"); var dislike = evidence(3, subject, MemoryFactValue.Kind.PREFERENCE, "不喜欢茶");
        var original = result(List.of(dislike, fact, like), 3, Set.of()); var view = service(original).read(request).toCompletableFuture().join();
        assertEquals(original.evidence(), view.evidence()); assertEquals(List.of(fact), view.facts()); assertEquals(List.of(dislike, like), view.preferences());
        assertEquals(original.characters(), view.characters()); assertTrue(view.isComplete()); assertFalse(view.definitivelyEmpty());
        assertReadOnly(view.facts()); assertReadOnly(view.preferences());
    }

    /**
     * 通过通用集合引用验证只读合同，修改尝试应抛出异常且不改变原视图。
     * @param values 待验证的分类列表
     */
    private static void assertReadOnly(List<?> values) {
        assertThrows(UnsupportedOperationException.class, values::clear);
    }

    /** 验证全部覆盖限制与原统计保留，空部分画像不能被当成确定无资料。 */
    @Test void coverageIsNeverUpgradedToComplete() {
        for (var limit : MemoryRecallResult.Limit.values()) {
            int matched = limit == MemoryRecallResult.Limit.SCAN ? 0 : 2;
            var original = result(List.of(), matched, Set.of(limit)); var view = service(original).read(request).toCompletableFuture().join();
            assertFalse(view.isComplete()); assertFalse(view.definitivelyEmpty()); assertTrue(view.facts().isEmpty()); assertTrue(view.preferences().isEmpty());
            assertEquals(original.limits(), view.limits()); assertEquals(original.matched(), view.matched()); assertEquals(original.scanned(), view.scanned());
        }
        assertTrue(service(result(List.of(), 0, Set.of())).read(request).toCompletableFuture().join().definitivelyEmpty());
    }

    /** 验证错误主体不能被悄悄丢弃后生成貌似完整的画像。 */
    @Test void wrongSubjectFailsInsteadOfSilentlyFiltering() {
        var item = evidence(1, UUID.randomUUID(), MemoryFactValue.Kind.FACT, "其他主体的敏感资料");
        var error = assertInstanceOf(MemoryRecallException.class, failure(service(result(List.of(item), 1, Set.of())).read(request)));
        assertEquals(MemoryRecallException.Reason.UNAVAILABLE, error.reason()); assertNull(error.getCause()); assertFalse(error.getMessage().contains("敏感"));
    }

    /** 验证目标空间、数据代次、绑定版本或作用域变化均不能冒充本次固定引用。 */
    @Test void everyReferenceDimensionMustMatch() {
        for (var different : List.of(new MemoryNamespaceReference(scope, new MemoryNamespaceId("other"), 3, 0),
                new MemoryNamespaceReference(scope, MemoryNamespaceId.SHARED, 3, 1), new MemoryNamespaceReference(scope, MemoryNamespaceId.SHARED, 4, 0),
                new MemoryNamespaceReference(new AgentScope("其他客户端", "当前群"), MemoryNamespaceId.SHARED, 3, 0))) {
            var result = new MemoryRecallResult(different, now, List.of(), 0, 0, Set.of(), 2);
            assertInstanceOf(MemoryRecallException.class, failure(service(result).read(request)));
        }
    }

    /** 验证到点过期的错误来源结果整体失败，画像不改变过滤时刻或保留旧证据。 */
    @Test void expiredSourceEvidenceCannotEnterProfile() {
        var value = new MemoryFactValue(MemoryFactValue.Kind.PREFERENCE, "已过期资料", List.of(), 1,
                MemoryFactValue.Importance.NORMAL, MemoryEventTime.unknown(), now);
        var expired = MemoryRecallEvidence.from(new MemoryFactRecord(new UUID(0, 1), MemoryNamespaceId.SHARED, 0, 0, subject, value, source, now, now, false));
        assertInstanceOf(MemoryRecallException.class, failure(service(result(List.of(expired), 1, Set.of())).read(request)));
    }

    /** 验证来源报告的输出、字符或扫描数不能超出本次显式预算。 */
    @Test void explicitBudgetRemainsAnOutputBoundary() {
        var one = evidence(1, subject, MemoryFactValue.Kind.FACT, "甲"); var two = evidence(2, subject, MemoryFactValue.Kind.FACT, "乙");
        var result = result(List.of(one, two), 2, Set.of());
        for (var limits : List.of(new MemoryRecallLimits(1, 100000, 10), new MemoryRecallLimits(20, 2, 10), new MemoryRecallLimits(20, 100000, 1))) {
            assertInstanceOf(MemoryRecallException.class, failure(service(result).read(new UserProfileRequest(reference, subject, request.use(), limits))));
        }
    }

    /** 验证同步/异步故障和空来源都明确失败，不返回默认画像或泄露原异常链。 */
    @Test void unavailableOrBrokenRecallCannotBecomeAnEmptyProfile() {
        var ports = List.<IMemoryRecallService>of(ignored -> { throw new IllegalStateException("来源秘密"); },
                ignored -> CompletableFuture.failedFuture(new CompletionException(new IllegalStateException("来源秘密"))),
                ignored -> null, ignored -> CompletableFuture.completedFuture(null));
        for (var port : ports) {
            var error = assertInstanceOf(MemoryRecallException.class, failure(new UserProfileService(port, Runnable::run).read(request)));
            assertEquals(MemoryRecallException.Reason.UNAVAILABLE, error.reason()); assertNull(error.getCause()); assertFalse(error.getMessage().contains("秘密"));
        }
    }

    /** 验证配置、空间缺失和旧代次的分类保留，但移除来源附加的 suppressed 敏感异常。 */
    @Test void knownFailureClassificationSurvivesWithoutOriginalChains() {
        for (boolean missing : List.of(true, false)) {
            var original = new MemoryFactGenerationException(missing); original.addSuppressed(new IllegalStateException("原始秘密"));
            var error = assertInstanceOf(MemoryFactGenerationException.class, failure(new UserProfileService(
                    ignored -> CompletableFuture.failedFuture(original), Runnable::run).read(request)));
            assertEquals(missing, error.missingNamespace()); assertEquals(0, error.getSuppressed().length); assertNull(error.getCause());
        }
        var original = new MemoryRecallException(MemoryRecallException.Reason.CONFIGURATION_ERROR);
        var error = assertInstanceOf(MemoryRecallException.class, failure(new UserProfileService(ignored -> CompletableFuture.failedFuture(original), Runnable::run).read(request)));
        assertEquals(original.reason(), error.reason()); assertNotSame(original, error);
    }

    /** 验证初次调用和来源完成后的业务调度被拒绝都能结束结果。 */
    @Test void executorRejectionAtBothBoundariesFinishesFuture() {
        for (int rejected : List.of(1, 2)) {
            var calls = new AtomicInteger(); var service = new UserProfileService(ignored -> CompletableFuture.completedFuture(result(List.of(), 0, Set.of())), action -> {
                if (calls.incrementAndGet() == rejected) { throw new RejectedExecutionException("测试拒绝业务调度。"); } action.run();
            });
            var error = assertInstanceOf(MemoryRecallException.class, failure(service.read(request))); assertEquals(MemoryRecallException.Reason.UNAVAILABLE, error.reason());
        }
    }

    /** 验证取消观察不会取消底层读取，也不使下一次读取复用取消结果或陈旧缓存。 */
    @Test void observerCancellationDoesNotCancelSourceOrPoisonNextRead() {
        var pending = new CompletableFuture<MemoryRecallResult>(); var calls = new AtomicInteger();
        var service = new UserProfileService(ignored -> { calls.incrementAndGet(); return pending; }, Runnable::run);
        var first = service.read(request).toCompletableFuture(); assertTrue(first.cancel(false)); assertFalse(pending.isCancelled());
        pending.complete(result(List.of(), 0, Set.of())); assertTrue(service.read(request).toCompletableFuture().join().definitivelyEmpty()); assertEquals(2, calls.get());
    }

    /** 验证重复读取重新构建，不把旧投影作为另一份持续存在的个人事实。 */
    @Test void eachReadRebuildsFromCurrentAcceptedEvidence() {
        var recall = mock(IMemoryRecallService.class); var first = result(List.of(evidence(1, subject, MemoryFactValue.Kind.FACT, "较早事实")), 1, Set.of());
        var next = result(List.of(evidence(2, subject, MemoryFactValue.Kind.PREFERENCE, "当前偏好")), 1, Set.of());
        when(recall.recall(any())).thenReturn(CompletableFuture.completedFuture(first)).thenReturn(CompletableFuture.completedFuture(next));
        var service = new UserProfileService(recall, Runnable::run); var oldView = service.read(request).toCompletableFuture().join(); var newView = service.read(request).toCompletableFuture().join();
        assertEquals(first.evidence(), oldView.facts()); assertTrue(newView.facts().isEmpty()); assertEquals(next.evidence(), newView.preferences()); verify(recall, times(2)).recall(any());
    }

    /** 验证画像规则版本不可伪造，返回集合冻结且与原输入集合后续修改无关。 */
    @Test void viewHasExplicitProjectionVersionAndImmutableEvidence() {
        var items = new ArrayList<>(List.of(evidence(1, subject, MemoryFactValue.Kind.FACT, "明确事实")));
        var result = result(items, 1, Set.of()); var view = UserProfileView.from(request, result); items.clear();
        assertEquals(1, view.evidence().size()); assertThrows(UnsupportedOperationException.class, () -> view.evidence().clear());
        assertThrows(IllegalArgumentException.class, () -> new UserProfileView(reference, subject, 99, now, List.of(), 0, 0, Set.of(), 2));
    }
}
