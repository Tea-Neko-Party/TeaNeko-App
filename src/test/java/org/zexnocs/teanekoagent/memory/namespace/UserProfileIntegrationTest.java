package org.zexnocs.teanekoagent.memory.namespace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.zexnocs.teanekoagent.agent.AgentActorContext;
import org.zexnocs.teanekoagent.agent.AgentScope;
import org.zexnocs.teanekoagent.memory.fact.*;
import org.zexnocs.teanekoagent.memory.fact.legacy.LegacyMemoryImport;
import org.zexnocs.teanekoagent.memory.fact.legacy.LegacyMemoryLocation;
import org.zexnocs.teanekoagent.memory.profile.UserProfileRequest;
import org.zexnocs.teanekoagent.memory.profile.UserProfileService;
import org.zexnocs.teanekoagent.memory.recall.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 经真实 namespace、事实、兼容导入和 Recall 适配验证画像重建，不连接真实数据库或模型。
 * 复用原事务替身的锁与回滚合同，仍需 MySQL/JPA 实机验收。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
@Timeout(20)
class UserProfileIntegrationTest {
    private final Instant now = Instant.parse("2026-10-29T01:00:00Z");
    private final AgentScope scope = new AgentScope("客户端", "群");
    private final UUID subject = UUID.randomUUID();
    private final UUID otherSubject = UUID.randomUUID();
    private final MemoryFactSource source = new MemoryFactSource(UUID.randomUUID(), "明确来源", new AgentActorContext(scope,
            AgentActorContext.Origin.USER, Optional.of(UUID.randomUUID()), "操作者平台身份", "来源消息", "目标"));
    private final MemoryNamespaceReference reference = new MemoryNamespaceReference(scope, MemoryNamespaceId.SHARED, 0, 0);

    /**
     * 显式登记空间，不在画像服务中隐式创建来源。
     * @param fixture 事务容器
     */
    private void prepare(NamespaceDatabaseFixture fixture) {
        fixture.store.create(new MemoryNamespace(MemoryNamespaceId.SHARED, 0, 0, now, null, null), 5).toCompletableFuture().join();
    }
    /**
     * 构造当前空间内已明确接受的原证据。
     * @param kind 类别
     * @param text 正文
     * @return 有界内容
     */
    private MemoryFactValue value(MemoryFactValue.Kind kind, String text) {
        return new MemoryFactValue(kind, text, List.of(), 1, MemoryFactValue.Importance.NORMAL, MemoryEventTime.unknown(), null);
    }
    /**
     * 组合真实 Recall 与画像业务，借用测试拥有的业务执行器。
     * @param fixture 事务容器
     * @param executor 业务执行器
     * @return 独立画像服务
     */
    private UserProfileService service(NamespaceDatabaseFixture fixture, Executor executor) {
        var recall = new MemoryRecallService(fixture.recall, MemoryRecallSettings::defaults, Clock.fixed(now, ZoneOffset.UTC), executor,
                evidence -> fixture.mapper.writeValueAsString(evidence).length());
        return new UserProfileService(recall, executor);
    }
    /**
     * 固定读取主体和用途，不改变来源权限或注册配置。
     * @param reference 已授权引用
     * @param subject 主体
     * @return 默认注入预算请求
     */
    private UserProfileRequest request(MemoryNamespaceReference reference, UUID subject) {
        return new UserProfileRequest(reference, subject, MemoryRecallRequest.Use.INJECTION, null);
    }
    /**
     * 提取异步根失败，不按空画像降级。
     * @param stage 画像阶段
     * @return 根失败
     */
    private static Throwable failure(CompletionStage<?> stage) { return assertThrows(CompletionException.class, () -> stage.toCompletableFuture().join()).getCause(); }
    /**
     * 有限等待线程协调信号，失败后不永久占用构建。
     * @param signal 测试信号
     */
    private static void await(CountDownLatch signal) {
        try { assertTrue(signal.await(5, TimeUnit.SECONDS), "画像测试协调超时。"); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException("画像测试协调被中断。"); }
    }
    /**
     * 取得已排队的投影动作，用于明确控制读取完成与后续 clear 的顺序。
     * @param queue 业务动作队列
     * @return 下一个动作
     * @throws InterruptedException 当测试等待被中断时
     */
    private static Runnable next(BlockingQueue<Runnable> queue) throws InterruptedException {
        return Objects.requireNonNull(queue.poll(5, TimeUnit.SECONDS), "画像业务动作未及时入队。");
    }

    /** 验证按 UUID 分组且同空间跨来源共享，过期证据不进入画像，没有读写旁路。 */
    @Test void acceptedFactsAndPreferencesBuildOneSubjectView() {
        try (var fixture = new NamespaceDatabaseFixture(); var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            prepare(fixture);
            fixture.facts.append(new MemoryFactAppend(UUID.randomUUID(), reference, subject, value(MemoryFactValue.Kind.FACT, "明确称呼"), source), now).toCompletableFuture().join();
            var otherScope = new AgentScope("另一客户端", "另一群"); var actor = new AgentActorContext(otherScope, AgentActorContext.Origin.USER,
                    source.actor().userId(), "原平台身份", "另一消息", "另一目标");
            var shared = new MemoryFactSource(UUID.randomUUID(), "跨来源明确新增", actor);
            fixture.facts.append(new MemoryFactAppend(UUID.randomUUID(), new MemoryNamespaceReference(otherScope, MemoryNamespaceId.SHARED, 0, 0),
                    subject, value(MemoryFactValue.Kind.PREFERENCE, "明确喜欢茶"), shared), now).toCompletableFuture().join();
            fixture.facts.append(new MemoryFactAppend(UUID.randomUUID(), reference, otherSubject, value(MemoryFactValue.Kind.FACT, "别人的资料"), source), now).toCompletableFuture().join();
            var expired = new MemoryFactValue(MemoryFactValue.Kind.FACT, "已过期", List.of(), 1, MemoryFactValue.Importance.HIGH, MemoryEventTime.unknown(), now);
            fixture.facts.append(new MemoryFactAppend(UUID.randomUUID(), reference, subject, expired, source), now).toCompletableFuture().join(); clearInvocations(fixture.factRepository);
            var view = service(fixture, executor).read(request(reference, subject)).toCompletableFuture().join();
            assertTrue(view.isComplete()); assertEquals(2, view.matched()); assertEquals(4, view.scanned()); assertEquals(1, view.facts().size()); assertEquals(1, view.preferences().size());
            assertEquals(shared, view.preferences().getFirst().source()); assertTrue(view.evidence().stream().allMatch(item -> item.subjectId().equals(subject)));
            assertEquals(MemoryRecallEvidence.TimeBasis.RECORDED_TIME_ESTIMATE, view.facts().getFirst().timeBasis()); verify(fixture.factRepository, never()).saveAndFlush(any());
        }
    }

    /** 验证同一画像服务换空间不复制旧资料，clear 后旧代次失败、新代次为空。 */
    @Test void switchingNamespaceAndClearingDoesNotCopyCachedProfiles() {
        try (var fixture = new NamespaceDatabaseFixture(); var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            prepare(fixture); fixture.facts.append(new MemoryFactAppend(UUID.randomUUID(), reference, subject, value(MemoryFactValue.Kind.FACT, "旧空间资料"), source), now).toCompletableFuture().join();
            var creation = new MemoryNamespace.Creation(UUID.randomUUID(), source.actor()); var id = MemoryNamespaceId.created(creation.requestId());
            fixture.store.create(new MemoryNamespace(id, 0, 0, now, creation, null), 5).toCompletableFuture().join();
            var other = new MemoryNamespaceReference(scope, id, 1, 0); var service = service(fixture, executor);
            var oldView = service.read(request(reference, subject)).toCompletableFuture().join(); assertEquals(1, oldView.evidence().size());
            assertTrue(service.read(request(other, subject)).toCompletableFuture().join().definitivelyEmpty());
            fixture.facts.append(new MemoryFactAppend(UUID.randomUUID(), other, subject, value(MemoryFactValue.Kind.PREFERENCE, "新空间偏好"), source), now).toCompletableFuture().join();
            assertEquals("新空间偏好", service.read(request(other, subject)).toCompletableFuture().join().preferences().getFirst().value().text());
            fixture.store.advanceGeneration(MemoryNamespaceId.SHARED, 0, UUID.randomUUID()).toCompletableFuture().join();
            assertFalse(assertInstanceOf(MemoryFactGenerationException.class, failure(service.read(request(reference, subject)))).missingNamespace());
            var next = new MemoryNamespaceReference(scope, MemoryNamespaceId.SHARED, 0, 1); assertTrue(service.read(request(next, subject)).toCompletableFuture().join().definitivelyEmpty());
            assertEquals(1, oldView.evidence().size()); assertEquals(0, oldView.reference().generation());
        }
    }

    /** 验证后页主体可命中，默认十二条、扫描和字符限制都不会被画像升级为完整。 */
    @Test void profileCoverageIncludesLaterPagesAndAllBudgetLimits() {
        try (var fixture = new NamespaceDatabaseFixture(); var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            prepare(fixture);
            for (int i = 1; i <= 205; i++) {
                var record = new MemoryFactRecord(new UUID(0, i), MemoryNamespaceId.SHARED, 0, 0, i <= 200 ? otherSubject : subject,
                        value(MemoryFactValue.Kind.FACT, "事实" + i), source, now, now, false);
                fixture.seedFactRow("agent.fact.v1", "record:default:g:0", record.id().toString(), fixture.mapper.writeValueAsString(Map.of("schemaVersion", 1, "record", record)));
            }
            var service = service(fixture, executor); var full = service.read(request(reference, subject)).toCompletableFuture().join();
            assertTrue(full.isComplete()); assertEquals(205, full.scanned()); assertEquals(5, full.evidence().size());
            var limited = service.read(request(reference, otherSubject)).toCompletableFuture().join(); assertEquals(12, limited.evidence().size());
            assertEquals(Set.of(MemoryRecallResult.Limit.RESULTS), limited.limits()); assertFalse(limited.isComplete());
            var scan = service.read(new UserProfileRequest(reference, subject, MemoryRecallRequest.Use.INJECTION, new MemoryRecallLimits(20, 16000, 100))).toCompletableFuture().join();
            assertTrue(scan.evidence().isEmpty()); assertFalse(scan.definitivelyEmpty()); assertEquals(Set.of(MemoryRecallResult.Limit.SCAN), scan.limits());
            var characters = service.read(new UserProfileRequest(reference, subject, MemoryRecallRequest.Use.INJECTION, new MemoryRecallLimits(20, 2, 1000))).toCompletableFuture().join();
            assertTrue(characters.evidence().isEmpty()); assertFalse(characters.definitivelyEmpty()); assertEquals(Set.of(MemoryRecallResult.Limit.CHARACTERS), characters.limits());
        }
    }

    /** 验证追加后的下一次读取重建画像，旧视图不可变且不写回一份独立画像真相。 */
    @Test void newAcceptedEvidenceAppearsOnNextReadWithoutProfileWrites() {
        try (var fixture = new NamespaceDatabaseFixture(); var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            prepare(fixture); var service = service(fixture, executor); var empty = service.read(request(reference, subject)).toCompletableFuture().join(); assertTrue(empty.definitivelyEmpty());
            var stored = fixture.facts.append(new MemoryFactAppend(UUID.randomUUID(), reference, subject, value(MemoryFactValue.Kind.PREFERENCE, "用户明确偏好"), source), now).toCompletableFuture().join();
            clearInvocations(fixture.factRepository); var view = service.read(request(reference, subject)).toCompletableFuture().join();
            assertEquals(Objects.requireNonNull(stored.record()).id(), view.preferences().getFirst().id()); assertTrue(empty.evidence().isEmpty());
            verify(fixture.factRepository, never()).saveAndFlush(any()); assertEquals(empty.projectionVersion(), view.projectionVersion());
        }
    }

    /** 验证导入证据可追溯且保留时间和 locked，原始旧私有 JSON 不经画像泄露。 */
    @Test void importedEvidenceKeepsTraceWithoutRawPrivatePayload() {
        try (var fixture = new NamespaceDatabaseFixture(); var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            prepare(fixture); var location = new LegacyMemoryLocation("旧群", "旧角色", "旧平台用户");
            var json = "[{\"id\":\"旧ID\",\"content\":\"明确称呼\",\"source\":\"旧来源秘密\",\"locked\":true,\"createdAt\":\"2020-01-01T00:00:00Z\",\"updatedAt\":\"2020-01-02T00:00:00Z\"}]";
            fixture.seedFactRow("memory", location.target(), location.key(), json); var snapshot = fixture.legacy.read(location, now).toCompletableFuture().join().orElseThrow();
            fixture.legacy.importSnapshot(new LegacyMemoryImport(UUID.randomUUID(), reference, location, snapshot.version(), snapshot.digest(), Map.of("旧平台用户", subject), source, now)).toCompletableFuture().join();
            var view = service(fixture, executor).read(request(reference, subject)).toCompletableFuture().join(); var item = view.facts().getFirst();
            assertEquals("旧ID", Objects.requireNonNull(item.externalOrigin()).externalId()); assertEquals(source, item.source()); assertTrue(item.locked());
            assertEquals(Instant.parse("2020-01-01T00:00:00Z"), item.createdAt()); assertFalse(fixture.mapper.writeValueAsString(view).contains("旧来源秘密"));
            assertEquals(json, fixture.storedValue("memory", location.target(), location.key()));
        }
    }

    /** 验证 clear 先提交时画像读取失败，事务开始时的旧快照不提供旧资料回退。 */
    @Test void clearBeforeReadLockRejectsOldGeneration() {
        try (var fixture = new NamespaceDatabaseFixture(); var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            prepare(fixture); var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
            fixture.beforeNamespaceLock.set(() -> { entered.countDown(); await(release); }); var view = service(fixture, executor).read(request(reference, subject));
            try { await(entered); fixture.store.advanceGeneration(MemoryNamespaceId.SHARED, 0, UUID.randomUUID()).toCompletableFuture().join(); }
            finally { release.countDown(); }
            assertFalse(assertInstanceOf(MemoryFactGenerationException.class, failure(view)).missingNamespace());
        }
    }

    /**
     * 验证读取完成后 clear 与投影交付交错时，旧视图始终携带旧引用，不冒充新代次或污染新画像。
     * @throws InterruptedException 当业务动作等待被中断时
     */
    @Test void lateProjectionRetainsItsOriginalReference() throws InterruptedException {
        try (var fixture = new NamespaceDatabaseFixture(); var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            prepare(fixture); fixture.facts.append(new MemoryFactAppend(UUID.randomUUID(), reference, subject, value(MemoryFactValue.Kind.FACT, "原证据"), source), now).toCompletableFuture().join();
            var recall = new MemoryRecallService(fixture.recall, MemoryRecallSettings::defaults, Clock.fixed(now, ZoneOffset.UTC), executor, evidence -> fixture.mapper.writeValueAsString(evidence).length());
            var queue = new LinkedBlockingQueue<Runnable>(); var profiles = new UserProfileService(recall, queue::add);
            var old = profiles.read(request(reference, subject)); next(queue).run(); var projection = next(queue);
            fixture.store.advanceGeneration(MemoryNamespaceId.SHARED, 0, UUID.randomUUID()).toCompletableFuture().join(); projection.run();
            var oldView = old.toCompletableFuture().join(); assertEquals(reference, oldView.reference()); assertEquals(1, oldView.evidence().size());
            var current = new MemoryNamespaceReference(scope, MemoryNamespaceId.SHARED, 0, 1); var fresh = profiles.read(request(current, subject));
            next(queue).run(); next(queue).run(); assertTrue(fresh.toCompletableFuture().join().definitivelyEmpty());
            assertInstanceOf(MemoryFactGenerationException.class, failure(fixture.recall.scan(MemoryNamespaceId.SHARED, 0, 1)));
        }
    }

    /** 验证空间缺失与损坏事实均失败，不用完整空画像隐藏基础设施问题。 */
    @Test void missingOrDamagedStorageDoesNotBecomeAnEmptyProfile() {
        try (var fixture = new NamespaceDatabaseFixture(); var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var service = service(fixture, executor); assertTrue(assertInstanceOf(MemoryFactGenerationException.class, failure(service.read(request(reference, subject)))).missingNamespace());
            prepare(fixture); fixture.seedFactRow("agent.fact.v1", "record:default:g:0", UUID.randomUUID().toString(), "{损坏敏感原文}");
            var error = assertInstanceOf(MemoryRecallException.class, failure(service.read(request(reference, subject))));
            assertEquals(MemoryRecallException.Reason.UNAVAILABLE, error.reason()); assertNull(error.getCause()); assertFalse(error.getMessage().contains("敏感原文"));
        }
    }
}
