package org.zexnocs.teanekoagent.memory.namespace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.zexnocs.teanekoagent.adapter.memory.EasyDataMemoryNamespaceBindingStore;
import org.zexnocs.teanekoagent.adapter.memory.StoreAgentMemoryReferences;
import org.zexnocs.teanekoagent.agent.AgentActorContext;
import org.zexnocs.teanekoagent.agent.AgentScope;
import org.zexnocs.teanekoagent.agent.access.AgentScopePolicy;
import org.zexnocs.teanekoagent.agent.memory.AgentMemoryReferenceException;
import org.zexnocs.teanekoagent.agent.memory.IAgentMemoryReferences;
import org.zexnocs.teanekoagent.memory.access.*;
import org.zexnocs.teanekoagent.memory.fact.*;
import org.zexnocs.teanekoagent.memory.profile.UserProfileService;
import org.zexnocs.teanekoagent.memory.recall.*;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 将可信访问边界与真实绑定、事实、Recall 事务适配组合，验证提交、重放和切换竞争。
 * 使用既有事务替身，不把模拟锁或离线结果当作 MySQL 实机验收。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
@Timeout(20)
class MemoryAccessIntegrationTest {
    private final Instant now = Instant.parse("2026-10-29T01:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private final AgentScope scope = new AgentScope("客户端", "群");
    private final AgentActorContext actor = new AgentActorContext(scope, AgentActorContext.Origin.USER, Optional.of(UUID.randomUUID()), "平台用户", "来源消息", "目标");
    private final MemoryNamespaceReference reference = new MemoryNamespaceReference(scope, MemoryNamespaceId.SHARED, 0, 0);
    private final MemoryAccessContext context = new MemoryAccessContext(UUID.randomUUID(), actor, reference);

    /**
     * 显式登记默认空间，不通过查询隐式迁移或写入旧数据。
     * @param fixture 事务容器
     */
    private void prepare(NamespaceDatabaseFixture fixture) {
        fixture.store.create(new MemoryNamespace(MemoryNamespaceId.SHARED, 0, 0, now, null, null), 5).toCompletableFuture().join();
    }

    /**
     * 组合真实只读引用校验，与业务共用借用执行域。
     * @param fixture 事务容器
     * @param executor 业务执行器
     * @return 当前绑定端口
     */
    private IAgentMemoryReferences references(NamespaceDatabaseFixture fixture, Executor executor) {
        return new StoreAgentMemoryReferences(ignored -> new AgentScopePolicy(true, true, AgentScopePolicy.Mode.PASSIVE_BASIC),
                new EasyDataMemoryNamespaceBindingStore(fixture.store), new MemoryNamespaceRegistry(fixture.store, () -> 5, clock), executor);
    }

    /**
     * 组合真实候选读取与有界查询。
     * @param fixture 事务容器
     * @param executor 业务执行器
     * @return 独立检索业务
     */
    private IMemoryRecallService recall(NamespaceDatabaseFixture fixture, Executor executor) {
        return new MemoryRecallService(fixture.recall, MemoryRecallSettings::defaults, clock, executor,
                items -> fixture.mapper.writeValueAsString(items).length());
    }

    /**
     * 组合当前批次与已有真实事务业务。
     * @param fixture 事务容器
     * @param executor 业务执行器
     * @return 没有自建资源的访问服务
     */
    private MemoryAccessService service(NamespaceDatabaseFixture fixture, Executor executor) {
        var recall = recall(fixture, executor);
        return new MemoryAccessService(ignored -> new MemoryAccessPolicy(true, true, true, false), references(fixture, executor), recall,
                new UserProfileService(recall, executor), new MemoryFacts(fixture.facts, clock), executor);
    }

    /**
     * 固定当前用户提供的偏好内容。
     * @param importance 不同命令内容用于验证相同动作冲突
     * @return 保留明确原文的内容
     */
    private MemoryFactValue value(MemoryFactValue.Importance importance) {
        return new MemoryFactValue(MemoryFactValue.Kind.PREFERENCE, "我喜欢茶", List.of("饮品"), 1, importance, MemoryEventTime.unknown(), null);
    }

    /**
     * 识别当前来源的明确意图。
     * @return 宿主绑定意图
     */
    private MemoryAddIntent intent() { return MemoryAddIntent.fromUserText(context, "记住：我喜欢茶").orElseThrow(); }

    /**
     * 有限等待竞争屏障，失败时保留线程中断。
     * @param signal 待观察信号
     */
    private static void await(CountDownLatch signal) {
        try { assertTrue(signal.await(5, TimeUnit.SECONDS), "记忆访问事务协调超时。"); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException("记忆访问事务协调被中断。"); }
    }

    /**
     * 提取异步失败，不降级成空记忆。
     * @param stage 业务阶段
     * @return 根错误
     */
    private static Throwable failure(CompletionStage<?> stage) { return assertThrows(CompletionException.class, () -> stage.toCompletableFuture().join()).getCause(); }

    /** 验证明确新增可由真实画像重建，主体/来源保留，相同动作重放与新动作重复不多写事实。 */
    @Test void committedAppendReplayAndDuplicateBuildSelfProfile() {
        try (var fixture = new NamespaceDatabaseFixture(); var executor = Executors.newVirtualThreadPerTaskExecutor(); var control = ExecutionControl.create()) {
            prepare(fixture); var service = service(fixture, executor); var value = value(MemoryFactValue.Importance.NORMAL);
            var first = service.append(context, "动作一", intent(), value, control).toCompletableFuture().join();
            assertEquals(MemoryFactReceipt.Status.STORED, first.status());
            assertEquals(first, service.append(context, "动作一", intent(), value, control).toCompletableFuture().join());
            var duplicate = service.append(context, "动作二", intent(), value, control).toCompletableFuture().join();
            assertEquals(MemoryFactReceipt.Status.DUPLICATE, duplicate.status()); assertEquals(first.recordId(), duplicate.recordId());
            var profile = service.profile(context, actor.userId().orElseThrow(), MemoryRecallRequest.Use.TOOL, null, control).toCompletableFuture().join();
            assertTrue(profile.isComplete()); assertEquals(1, profile.evidence().size()); assertEquals(value, profile.preferences().getFirst().value());
            assertEquals(actor, profile.preferences().getFirst().source().actor()); assertEquals(context.requestId(), profile.preferences().getFirst().source().requestId());
            assertTrue(service.profile(context, UUID.randomUUID(), MemoryRecallRequest.Use.TOOL, null, control).toCompletableFuture().join().definitivelyEmpty());
        }
    }

    /** 验证同请求/动作改参数只返回原存储冲突，不覆盖已经提交的内容或来源。 */
    @Test void changedPayloadUnderSameActionReturnsConflictWithoutOverwrite() {
        try (var fixture = new NamespaceDatabaseFixture(); var executor = Executors.newVirtualThreadPerTaskExecutor(); var control = ExecutionControl.create()) {
            prepare(fixture); var service = service(fixture, executor);
            var first = service.append(context, "动作", intent(), value(MemoryFactValue.Importance.NORMAL), control).toCompletableFuture().join();
            var conflict = service.append(context, "动作", intent(), value(MemoryFactValue.Importance.HIGH), control).toCompletableFuture().join();
            assertEquals(MemoryFactReceipt.Status.CONFLICT, conflict.status()); assertNull(conflict.recordId());
            var record = fixture.facts.find(reference.namespaceId(), 0, Objects.requireNonNull(first.recordId())).toCompletableFuture().join().orElseThrow();
            assertEquals(MemoryFactValue.Importance.NORMAL, record.value().importance());
        }
    }

    /** 验证真实事务提交前没有成功回执；取消交付不撤销已经持锁的追加。 */
    @Test void cancellationAtCommitDoesNotPretendToRollbackStartedWrite() {
        try (var fixture = new NamespaceDatabaseFixture(); var executor = Executors.newVirtualThreadPerTaskExecutor(); var control = ExecutionControl.create()) {
            prepare(fixture); var service = service(fixture, executor); var transactions = new AtomicInteger();
            var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
            fixture.beforeCommit.set(() -> { if (transactions.incrementAndGet() == 2) { entered.countDown(); await(release); } });
            var future = service.append(context, "动作", intent(), value(MemoryFactValue.Importance.NORMAL), control);
            try {
                await(entered); assertFalse(future.toCompletableFuture().isDone()); control.cancel();
                assertEquals(MemoryAccessException.Reason.STOPPED, assertInstanceOf(MemoryAccessException.class, failure(future)).reason());
            } finally { release.countDown(); fixture.beforeCommit.set(() -> {}); }
            try (var observer = ExecutionControl.create()) {
                var replay = service.append(context, "动作", intent(), value(MemoryFactValue.Importance.NORMAL), observer).toCompletableFuture().join();
                assertEquals(MemoryFactReceipt.Status.STORED, replay.status());
                assertEquals(1, service.profile(context, actor.userId().orElseThrow(), MemoryRecallRequest.Use.TOOL, null, observer).toCompletableFuture().join().evidence().size());
            }
        }
    }

    /** 验证校验允许后、事实取得写锁前 clear 仍在原事务拒绝旧 generation。 */
    @Test void clearBetweenAdmissionAndWriteLockRejectsOldGeneration() {
        try (var fixture = new NamespaceDatabaseFixture(); var executor = Executors.newVirtualThreadPerTaskExecutor(); var control = ExecutionControl.create()) {
            prepare(fixture); var service = service(fixture, executor); var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
            fixture.beforeNamespaceLock.set(() -> { entered.countDown(); await(release); });
            var future = service.append(context, "动作", intent(), value(MemoryFactValue.Importance.NORMAL), control);
            try {
                await(entered); fixture.store.advanceGeneration(MemoryNamespaceId.SHARED, 0, UUID.randomUUID()).toCompletableFuture().join();
            } finally { release.countDown(); fixture.beforeNamespaceLock.set(() -> {}); }
            assertFalse(assertInstanceOf(MemoryFactGenerationException.class, failure(future)).missingNamespace());
            var current = new MemoryAccessContext(context.requestId(), actor, new MemoryNamespaceReference(scope, MemoryNamespaceId.SHARED, 0, 1));
            assertTrue(service.profile(current, actor.userId().orElseThrow(), MemoryRecallRequest.Use.TOOL, null, control).toCompletableFuture().join().definitivelyEmpty());
        }
    }

    /** 验证实际绑定变化后旧上下文拒绝，不能用原意图写入新空间或自动回退旧空间。 */
    @Test void namespaceSwitchRejectsOldContextAndDoesNotMoveIntent() {
        try (var fixture = new NamespaceDatabaseFixture(); var executor = Executors.newVirtualThreadPerTaskExecutor(); var control = ExecutionControl.create()) {
            prepare(fixture); var service = service(fixture, executor); var id = UUID.randomUUID();
            var bindings = new EasyDataMemoryNamespaceBindingStore(fixture.store);
            bindings.apply(new MemoryNamespaceBindingRequest(id, actor, MemoryNamespaceId.created(id), 0, MemoryNamespaceBindingRequest.Mode.CREATE_AND_BIND), 5, now).toCompletableFuture().join();
            var changed = assertInstanceOf(AgentMemoryReferenceException.class, failure(service.append(context, "动作", intent(), value(MemoryFactValue.Importance.NORMAL), control)));
            assertEquals(AgentMemoryReferenceException.Reason.CHANGED, changed.reason());
            var current = new MemoryAccessContext(context.requestId(), actor, bindings.readReference(scope).toCompletableFuture().join().orElseThrow());
            assertEquals(MemoryAccessException.Reason.INTENT_REQUIRED, assertInstanceOf(MemoryAccessException.class,
                    failure(service.append(current, "动作", intent(), value(MemoryFactValue.Importance.NORMAL), control))).reason());
            assertTrue(service.profile(current, actor.userId().orElseThrow(), MemoryRecallRequest.Use.TOOL, null, control).toCompletableFuture().join().definitivelyEmpty());
        }
    }

    /** 验证读取完成后 clear 的迟到证据被交付复核拒绝，不把旧引用改写成新 generation。 */
    @Test void clearAfterStableReadRejectsLateEvidenceDelivery() {
        try (var fixture = new NamespaceDatabaseFixture(); var executor = Executors.newVirtualThreadPerTaskExecutor(); var control = ExecutionControl.create()) {
            prepare(fixture); var base = recall(fixture, executor);
            IMemoryRecallService clearing = request -> base.recall(request).thenCompose(result ->
                    fixture.store.advanceGeneration(MemoryNamespaceId.SHARED, 0, UUID.randomUUID()).thenApply(ignored -> result));
            var service = new MemoryAccessService(ignored -> new MemoryAccessPolicy(true, true, true, false), references(fixture, executor), clearing,
                    new UserProfileService(base, executor), new MemoryFacts(fixture.facts, clock), executor);
            var query = new MemoryRecallQuery(null, null, Set.of(), Set.of(), List.of(), null, null, true);
            var error = assertInstanceOf(AgentMemoryReferenceException.class, failure(service.recall(context, query, MemoryRecallRequest.Use.INJECTION, null, control)));
            assertEquals(AgentMemoryReferenceException.Reason.CHANGED, error.reason());
        }
    }

    /** 验证二十个并发相同动作重用原持久回执，最终只有一个主体事实。 */
    @Test void concurrentSameActionUsesOneDurableOperation() {
        try (var fixture = new NamespaceDatabaseFixture(); var executor = Executors.newVirtualThreadPerTaskExecutor(); var control = ExecutionControl.create()) {
            prepare(fixture); var service = service(fixture, executor); var start = new CountDownLatch(1); var results = new ArrayList<CompletableFuture<MemoryAddResult>>();
            for (int i = 0; i < 20; i++) {
                results.add(CompletableFuture.supplyAsync(() -> { await(start); return service.append(context, "动作", intent(), value(MemoryFactValue.Importance.NORMAL), control); }, executor)
                        .thenCompose(stage -> stage));
            }
            start.countDown(); CompletableFuture.allOf(results.toArray(CompletableFuture[]::new)).join();
            var first = results.getFirst().join(); assertTrue(results.stream().allMatch(result -> first.equals(result.join())));
            assertEquals(1, service.profile(context, actor.userId().orElseThrow(), MemoryRecallRequest.Use.TOOL, null, control).toCompletableFuture().join().evidence().size());
        }
    }
}
