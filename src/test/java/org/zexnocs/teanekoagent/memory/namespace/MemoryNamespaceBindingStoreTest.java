package org.zexnocs.teanekoagent.memory.namespace;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.zexnocs.teanekoagent.adapter.memory.EasyDataMemoryNamespaceBindingStore;
import org.zexnocs.teanekoagent.agent.AgentActorContext;
import org.zexnocs.teanekoagent.agent.AgentScope;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.zexnocs.teanekoagent.memory.namespace.MemoryNamespaceBindingOutcome.Status.*;

/**
 * 验证真实绑定适配器的原子创建、持久化回执、版本竞争和来源隔离。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
class MemoryNamespaceBindingStoreTest {
    private final NamespaceDatabaseFixture database = new NamespaceDatabaseFixture();
    private final IMemoryNamespaceBindingStore store = new EasyDataMemoryNamespaceBindingStore(database.store);
    private final AgentActorContext actor = actor(UUID.randomUUID(), new AgentScope("客户端", "群"));

    /** 等待测试拥有的事务结束，关闭本地替身执行器。 */
    @AfterEach void cleanup() { database.close(); }

    /**
     * 构造不可由模型替换的测试宿主身份。
     * @param user TeaNeko UUID
     * @param scope 真实作用域
     * @return 人类来源
     */
    private static AgentActorContext actor(UUID user, AgentScope scope) {
        return new AgentActorContext(scope, AgentActorContext.Origin.USER, Optional.of(user), "平台来源", "消息", "目标");
    }

    /**
     * 构造具有独立请求 ID 的创建并绑定命令。
     * @param source 原始发起者
     * @param revision 原绑定版本
     * @return 固定的原始命令
     */
    private static MemoryNamespaceBindingRequest create(AgentActorContext source, long revision) {
        var id = UUID.randomUUID();
        return new MemoryNamespaceBindingRequest(id, source, MemoryNamespaceId.created(id), revision, MemoryNamespaceBindingRequest.Mode.CREATE_AND_BIND);
    }

    /**
     * 创建已有目标的绑定命令，不授予额外所有权。
     * @param source 真实操作者
     * @param target 已有目标
     * @param revision 原始版本
     * @return 固定命令
     */
    private static MemoryNamespaceBindingRequest bind(AgentActorContext source, MemoryNamespaceId target, long revision) {
        return new MemoryNamespaceBindingRequest(UUID.randomUUID(), source, target, revision, MemoryNamespaceBindingRequest.Mode.BIND_EXISTING);
    }

    /**
     * 为测试等待完整事务而非提交动作本身。
     * @param stage 完成阶段
     * @param <T> 结果类型
     * @return 已提交结果
     * @throws Exception 当事务失败或未能及时结束时
     */
    private static <T> T await(CompletionStage<T> stage) throws Exception { return stage.toCompletableFuture().get(5, TimeUnit.SECONDS); }

    /**
     * 执行一次固定时刻、五个配额的绑定事务。
     * @param request 原始命令
     * @return 已提交结果
     * @throws Exception 当事务失败时
     */
    private MemoryNamespaceBindingOutcome apply(MemoryNamespaceBindingRequest request) throws Exception {
        return await(store.apply(request, 5, Instant.EPOCH));
    }

    /**
     * 在当前事务域单独登记一个已有空间，用于跨创建者共享绑定验证。
     * @param owner 原始创建者
     * @return 已登记目标
     * @throws Exception 当创建失败时
     */
    private MemoryNamespaceId existing(AgentActorContext owner) throws Exception {
        var request = UUID.randomUUID(); var id = MemoryNamespaceId.created(request);
        await(database.store.create(new MemoryNamespace(id, 0, 0, Instant.EPOCH, new MemoryNamespace.Creation(request, owner), null), 5));
        return id;
    }

    /**
     * 验证读取默认引用不会登记 scope、创建空间或写入任何行。
     * @throws Exception 当读取失败时
     */
    @Test void defaultReadsNeverRegisterOrWriteState() throws Exception {
        assertEquals(MemoryNamespaceBinding.initial(actor.scope()), await(store.readBinding(actor.scope())));
        assertTrue(await(store.readReference(actor.scope())).isEmpty()); verify(database.repository, never()).saveAndFlush(any());
    }

    /**
     * 验证空间、索引、绑定和来源一并提交，新的适配器从持久数据恢复结果。
     * @throws Exception 当事务失败时
     */
    @Test void createAndBindCommitsAllStateAndCanBeReadByNewAdapter() throws Exception {
        var request = create(actor, 0); var result = apply(request); assertEquals(BOUND, result.status());
        assertEquals(1, result.binding().revision()); assertEquals(request, result.binding().lastChange());
        var reference = result.reference(); assertNotNull(reference); assertEquals(0, reference.generation());
        assertEquals(request.target(), reference.namespaceId());
        assertTrue(await(database.store.find(request.target())).isPresent());
        assertEquals(1, await(database.store.listCreated(actor.userId().orElseThrow(), 0, 100)).size());
        var restored = new EasyDataMemoryNamespaceBindingStore(database.store);
        assertEquals(result.binding(), await(restored.readBinding(actor.scope())));
        assertEquals(reference, await(restored.readReference(actor.scope())).orElseThrow());
        verify(database.repository, never()).findAll();
    }

    /**
     * 验证已授权调用者可以绑定他人创建的空间，原创建来源与双方配额保持正确。
     * @throws Exception 当事务失败时
     */
    @Test void bindingExistingSpaceDoesNotRequireCreatorOwnershipOrConsumeQuota() throws Exception {
        var owner = actor(UUID.randomUUID(), new AgentScope("另一客户端", "另一群")); var target = existing(owner);
        assertEquals(BOUND, apply(bind(actor, target, 0)).status());
        var value = await(database.store.find(target)).orElseThrow(); assertNotNull(value.creation()); assertEquals(owner, value.creation().actor());
        assertTrue(await(database.store.listCreated(actor.userId().orElseThrow(), 0, 100)).isEmpty());
        assertEquals(1, await(database.store.listCreated(owner.userId().orElseThrow(), 0, 100)).size());
    }

    /**
     * 验证缺失目标不自动创建，拒绝回执在目标后来出现后仍不会重试成新绑定。
     * @throws Exception 当事务失败时
     */
    @Test void missingTargetReceiptCannotLaterTurnIntoNewBinding() throws Exception {
        var id = UUID.randomUUID(); var target = MemoryNamespaceId.created(id); var request = bind(actor, target, 0);
        var original = apply(request); assertEquals(TARGET_NOT_FOUND, original.status()); assertEquals(0, original.binding().revision());
        await(database.store.create(new MemoryNamespace(target, 0, 0, Instant.EPOCH, new MemoryNamespace.Creation(id, actor), null), 5));
        assertEquals(original, apply(request)); assertEquals(0, await(store.readBinding(actor.scope())).revision());
        assertEquals(BOUND, apply(bind(actor, target, 0)).status());
    }

    /**
     * 验证预期版本过时先拒绝，不创建候选空间或消耗配额，回执不会被后续状态改写。
     * @throws Exception 当事务失败时
     */
    @Test void staleRevisionDoesNotLeaveNamespaceOrQuotaAndRemainsStable() throws Exception {
        var rejected = create(actor, 1); var original = apply(rejected); assertEquals(CONFLICT, original.status());
        assertTrue(await(database.store.find(rejected.target())).isEmpty());
        assertTrue(await(database.store.listCreated(actor.userId().orElseThrow(), 0, 100)).isEmpty());
        assertEquals(BOUND, apply(create(actor, 0)).status()); assertEquals(original, apply(rejected));
        assertEquals(1, await(store.readBinding(actor.scope())).revision());
    }

    /**
     * 验证同目标绑定不推进版本或更新时间，但过期版本依然不能成功。
     * @throws Exception 当事务失败时
     */
    @Test void sameTargetDoesNotResetVersionOrHistoryBoundary() throws Exception {
        var initial = apply(create(actor, 0)); var unchanged = apply(bind(actor, initial.binding().namespaceId(), 1));
        assertEquals(UNCHANGED, unchanged.status()); assertEquals(initial.binding(), unchanged.binding());
        assertEquals(CONFLICT, apply(bind(actor, initial.binding().namespaceId(), 0)).status());
    }

    /**
     * 验证回到相同 namespace 仍有新的绑定版本，不能复用切换前的运行引用。
     * @throws Exception 当事务失败时
     */
    @Test void returningToPreviousNamespaceDoesNotReuseOldReference() throws Exception {
        var first = apply(create(actor, 0)); apply(create(actor, 1));
        var returned = apply(bind(actor, first.binding().namespaceId(), 2)); assertEquals(3, returned.binding().revision());
        assertNotEquals(first.reference(), returned.reference()); assertEquals(first.binding().namespaceId(), returned.binding().namespaceId());
    }

    /**
     * 验证旧成功请求在新绑定之后仅返回历史回执，不恢复原空间或重复占用配额。
     * @throws Exception 当事务失败时
     */
    @Test void replayOfOldSuccessNeverRewindsCurrentBinding() throws Exception {
        var a = create(actor, 0); var first = apply(a); var second = apply(create(actor, 1));
        var restored = new EasyDataMemoryNamespaceBindingStore(database.store);
        assertEquals(first, await(restored.apply(a, 1, Instant.EPOCH.plusSeconds(100))));
        assertEquals(second.binding(), await(store.readBinding(actor.scope())));
        assertEquals(2, await(database.store.listCreated(actor.userId().orElseThrow(), 0, 100)).size());
    }

    /**
     * 验证相同 scope/requestId 的目标、版本、方式或操作者变化不覆盖原操作。
     * @throws Exception 当事务失败时
     */
    @Test void changedParametersForSameOperationAreRejected() throws Exception {
        var original = create(actor, 0); var result = apply(original); var target = existing(actor);
        var anotherActor = actor(UUID.randomUUID(), actor.scope());
        var changed = List.of(
                new MemoryNamespaceBindingRequest(original.requestId(), actor, target, 1, MemoryNamespaceBindingRequest.Mode.BIND_EXISTING),
                new MemoryNamespaceBindingRequest(original.requestId(), actor, original.target(), 1, original.mode()),
                new MemoryNamespaceBindingRequest(original.requestId(), actor, original.target(), 0, MemoryNamespaceBindingRequest.Mode.BIND_EXISTING),
                new MemoryNamespaceBindingRequest(original.requestId(), anotherActor, original.target(), 0, original.mode()));
        for (var request : changed) { assertEquals(CONFLICT, apply(request).status()); }
        assertEquals(result, apply(original)); assertEquals(result.binding(), await(store.readBinding(actor.scope())));
    }

    /**
     * 验证 client/scope 含分隔符或大小写变化都保持独立，操作 ID 不跨来源作用域复用。
     * @throws Exception 当事务失败时
     */
    @Test void clientScopeBoundariesAndCaseRemainDistinct() throws Exception {
        var target = existing(actor); var operation = UUID.randomUUID();
        for (var scope : List.of(new AgentScope("a:b", "c"), new AgentScope("a", "b:c"), new AgentScope("A", "b:c"), new AgentScope("a", "B:c"))) {
            var source = actor(actor.userId().orElseThrow(), scope);
            var request = new MemoryNamespaceBindingRequest(operation, source, target, 0, MemoryNamespaceBindingRequest.Mode.BIND_EXISTING);
            assertEquals(BOUND, apply(request).status()); assertEquals(scope, await(store.readBinding(scope)).scope());
            assertEquals(1, await(store.readBinding(scope)).revision());
        }
        assertEquals(0, await(store.readBinding(actor.scope())).revision());
    }

    /**
     * 验证二十个同 scope 原版本的创建竞争只产生一个绑定与一个配额记录，无孤立候选。
     * @throws Exception 当并发事务失败时
     */
    @Test void concurrentCreateAndBindHasOneWinnerAndNoOrphanNamespaces() throws Exception {
        var requests = new ArrayList<MemoryNamespaceBindingRequest>(); var stages = new ArrayList<CompletionStage<MemoryNamespaceBindingOutcome>>();
        var entered = new CountDownLatch(2); var release = new CountDownLatch(1); var arrivals = new AtomicInteger();
        database.beforeCommit.set(() -> { if (arrivals.incrementAndGet() <= 2) { entered.countDown(); waitFor(release); } });
        for (int i = 0; i < 2; i++) { var request = create(actor, 0); requests.add(request); stages.add(store.apply(request, 100, Instant.EPOCH)); }
        assertTrue(entered.await(3, TimeUnit.SECONDS)); release.countDown();
        for (int i = 2; i < 20; i++) { var request = create(actor, 0); requests.add(request); stages.add(store.apply(request, 100, Instant.EPOCH)); }
        int winners = 0; int conflicts = 0;
        for (int i = 0; i < stages.size(); i++) {
            var result = await(stages.get(i));
            if (result.status() == BOUND) { winners++; assertTrue(await(database.store.find(requests.get(i).target())).isPresent()); }
            else { assertEquals(CONFLICT, result.status()); conflicts++; assertTrue(await(database.store.find(requests.get(i).target())).isEmpty()); }
        }
        assertEquals(1, winners); assertEquals(19, conflicts);
        assertEquals(1, await(database.store.listCreated(actor.userId().orElseThrow(), 0, 100)).size());
        assertTrue(database.retries.get() > 0);
    }

    /**
     * 验证两个 scope 共用同一创建者配额，失败一方不留下绑定、空间或额外配额。
     * @throws Exception 当并发事务失败时
     */
    @Test void differentScopesStillShareOneAtomicCreatorQuota() throws Exception {
        var a = create(actor, 0); var b = create(actor(actor.userId().orElseThrow(), new AgentScope("客户端", "另一群")), 0);
        var first = store.apply(a, 1, Instant.EPOCH); var second = store.apply(b, 1, Instant.EPOCH);
        var results = List.of(await(first), await(second));
        assertEquals(1, results.stream().filter(result -> result.status() == BOUND).count());
        assertEquals(1, results.stream().filter(result -> result.status() == QUOTA_EXCEEDED).count());
        for (var result : results) { assertEquals(result.status() == BOUND ? 1 : 0, await(store.readBinding(result.binding().scope())).revision()); }
        assertEquals(1, await(database.store.listCreated(actor.userId().orElseThrow(), 0, 100)).size());
    }

    /**
     * 验证相同原始请求并发投递复用持久回执，不重复推进引用或占配额。
     * @throws Exception 当并发事务失败时
     */
    @Test void concurrentDuplicateRequestsReturnSameCommittedOutcome() throws Exception {
        var request = create(actor, 0); var stages = new ArrayList<CompletionStage<MemoryNamespaceBindingOutcome>>();
        for (int i = 0; i < 20; i++) { stages.add(store.apply(request, 5, Instant.EPOCH.plusSeconds(i))); }
        var original = await(stages.getFirst()); for (var stage : stages) { assertEquals(original, await(stage)); }
        assertEquals(1, await(store.readBinding(actor.scope())).revision());
        assertEquals(1, await(database.store.listCreated(actor.userId().orElseThrow(), 0, 100)).size());
    }

    /**
     * 验证绑定写入失败回滚此前的空间和索引写入，同一原请求修正后可完整重试。
     * @throws Exception 当事务读取失败时
     */
    @Test void bindingWriteFailureRollsBackNamespaceIndexAndReceipt() throws Exception {
        var request = create(actor, 0); database.failingWritePrefix.set("binding:");
        assertThrows(ExecutionException.class, () -> apply(request)); database.failingWritePrefix.set(null);
        assertTrue(await(database.store.find(request.target())).isEmpty());
        assertTrue(await(database.store.listCreated(actor.userId().orElseThrow(), 0, 100)).isEmpty());
        assertEquals(0, await(store.readBinding(actor.scope())).revision()); assertEquals(BOUND, apply(request).status());
    }

    /**
     * 验证最后的操作回执写入失败同样回滚绑定和配额，不能留下不可确认的半次成功。
     * @throws Exception 当事务读取失败时
     */
    @Test void receiptWriteFailureRollsBackEveryEarlierWrite() throws Exception {
        var request = create(actor, 0); database.failingWritePrefix.set("binding-op:");
        assertThrows(ExecutionException.class, () -> apply(request)); database.failingWritePrefix.set(null);
        assertEquals(0, await(store.readBinding(actor.scope())).revision()); assertTrue(await(database.store.find(request.target())).isEmpty());
        assertTrue(await(database.store.listCreated(actor.userId().orElseThrow(), 0, 100)).isEmpty()); assertEquals(BOUND, apply(request).status());
    }

    /**
     * 验证未提交时回执不提前完成，提交整体失败后所有写入均不可见。
     * @throws Exception 当受控提交未能结束时
     */
    @Test void outcomeWaitsForCommitAndCommitFailureLeavesOriginalBinding() throws Exception {
        var request = create(actor, 0); var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        database.beforeCommit.set(() -> { entered.countDown(); waitFor(release); throw new IllegalStateException("测试提交失败。"); });
        var pending = store.apply(request, 5, Instant.EPOCH).toCompletableFuture();
        assertTrue(entered.await(3, TimeUnit.SECONDS)); assertFalse(pending.isDone()); release.countDown();
        assertThrows(ExecutionException.class, () -> pending.get(5, TimeUnit.SECONDS)); database.beforeCommit.set(() -> {});
        assertEquals(0, await(store.readBinding(actor.scope())).revision()); assertTrue(await(database.store.find(request.target())).isEmpty());
        assertEquals(BOUND, apply(request).status());
    }

    /**
     * 验证观察视图取消不取消已提交的绑定事务，原始阶段仍能确认实际完成。
     * @throws Exception 当提交未能结束时
     */
    @Test void cancellingViewDoesNotUndoSubmittedBinding() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var completed = new CountDownLatch(1); var request = create(actor, 0);
        database.beforeCommit.set(() -> { entered.countDown(); waitFor(release); });
        var source = store.apply(request, 5, Instant.EPOCH); source.whenComplete((result, failure) -> completed.countDown());
        assertTrue(entered.await(3, TimeUnit.SECONDS)); assertTrue(source.toCompletableFuture().cancel(true)); release.countDown();
        assertTrue(completed.await(3, TimeUnit.SECONDS)); database.beforeCommit.set(() -> {});
        assertEquals(request.target(), await(store.readBinding(actor.scope())).namespaceId()); assertEquals(BOUND, apply(request).status());
    }

    /**
     * 验证绑定版本与数据代次独立，历史回执不冒充当前引用或把 clear 后的代次回退。
     * @throws Exception 当事务失败时
     */
    @Test void generationChangeInvalidatesReferenceWithoutChangingBindingRevision() throws Exception {
        var request = create(actor, 0); var original = apply(request);
        await(database.store.advanceGeneration(request.target(), 0, UUID.randomUUID()));
        var current = await(store.readReference(actor.scope())).orElseThrow();
        assertEquals(1, current.bindingRevision()); assertEquals(1, current.generation()); assertNotEquals(original.reference(), current);
        assertEquals(original, apply(request)); assertEquals(current, await(store.readReference(actor.scope())).orElseThrow());
    }

    /**
     * 验证默认共享空间登记后可读代次，逻辑清空不需要伪造显式绑定。
     * @throws Exception 当事务失败时
     */
    @Test void defaultReferenceTracksSharedGenerationWithoutWritingBinding() throws Exception {
        await(database.store.create(new MemoryNamespace(MemoryNamespaceId.SHARED, 0, 0, Instant.EPOCH, null, null), 1));
        assertEquals(new MemoryNamespaceReference(actor.scope(), MemoryNamespaceId.SHARED, 0, 0), await(store.readReference(actor.scope())).orElseThrow());
        await(database.store.advanceGeneration(MemoryNamespaceId.SHARED, 0, UUID.randomUUID()));
        assertEquals(1, await(store.readReference(actor.scope())).orElseThrow().generation()); assertEquals(0, await(store.readBinding(actor.scope())).revision());
    }

    /**
     * 验证切回默认空间仍保留新的显式版本，不与从未切换过的隐式引用混淆。
     * @throws Exception 当事务失败时
     */
    @Test void returningToSharedNamespaceKeepsMonotonicExplicitRevision() throws Exception {
        await(database.store.create(new MemoryNamespace(MemoryNamespaceId.SHARED, 0, 0, Instant.EPOCH, null, null), 1));
        apply(create(actor, 0)); var changed = apply(bind(actor, MemoryNamespaceId.SHARED, 1));
        assertEquals(BOUND, changed.status()); assertEquals(2, changed.binding().revision()); assertNotNull(changed.binding().lastChange());
        assertEquals(new MemoryNamespaceReference(actor.scope(), MemoryNamespaceId.SHARED, 2, 0), await(store.readReference(actor.scope())).orElseThrow());
    }

    /**
     * 验证配额拒绝保留原回执，提高配额后重放旧请求不会悄悄创建或切换。
     * @throws Exception 当事务失败时
     */
    @Test void quotaIncreaseDoesNotReexecutePreviouslyRejectedOperation() throws Exception {
        apply(create(actor, 0)); var request = create(actor, 1);
        var rejected = await(store.apply(request, 1, Instant.EPOCH)); assertEquals(QUOTA_EXCEEDED, rejected.status());
        assertEquals(rejected, await(store.apply(request, 2, Instant.EPOCH.plusSeconds(10))));
        assertEquals(1, await(store.readBinding(actor.scope())).revision()); assertTrue(await(database.store.find(request.target())).isEmpty());
        assertEquals(BOUND, await(store.apply(create(actor, 1), 2, Instant.EPOCH)).status());
        assertEquals(2, await(database.store.listCreated(actor.userId().orElseThrow(), 0, 100)).size());
    }

    /**
     * 验证显式引用目标丢失时失败，不回退到默认共享空间。
     * @throws Exception 当事务失败时
     */
    @Test void explicitMissingTargetFailsClosed() throws Exception {
        var request = create(actor, 0); apply(request); database.removeNamespace(request.target());
        assertThrows(ExecutionException.class, () -> await(store.readReference(actor.scope())));
        assertEquals(request.target(), await(store.readBinding(actor.scope())).namespaceId());
    }

    /**
     * 验证未来绑定 schema 或 scope 定位损坏时不能按空绑定覆盖旧数据。
     * @throws Exception 当事务失败时
     */
    @Test void corruptBindingOrScopeIdentityNeverBecomesDefaultState() throws Exception {
        var request = create(actor, 0); var result = apply(request);
        database.rewriteRows("binding:", ignored -> "{\"schemaVersion\":2}");
        assertThrows(ExecutionException.class, () -> await(store.readBinding(actor.scope())));
        assertThrows(ExecutionException.class, () -> apply(create(actor, 0)));
        var other = actor(UUID.randomUUID(), new AgentScope("客户端", "另一群")); var otherRequest = create(other, 0);
        var foreign = MemoryNamespaceBinding.initial(other.scope()).next(otherRequest, Instant.EPOCH);
        var json = database.mapper.writeValueAsString(Map.of("schemaVersion", 1, "value", foreign));
        database.rewriteRows("binding:", ignored -> json);
        assertThrows(ExecutionException.class, () -> await(store.readBinding(actor.scope())));
        assertTrue(await(database.store.find(result.binding().namespaceId())).isPresent());
    }

    /**
     * 验证损坏操作回执使重复执行失败，不被解释成从未执行后重新绑定。
     * @throws Exception 当事务失败时
     */
    @Test void corruptReceiptNeverAllowsDuplicateExecution() throws Exception {
        var request = create(actor, 0); var result = apply(request);
        database.rewriteRows("binding-op:", ignored -> "{敏感损坏正文");
        assertThrows(ExecutionException.class, () -> apply(request)); assertEquals(result.binding(), await(store.readBinding(actor.scope())));
        assertEquals(1, await(database.store.listCreated(actor.userId().orElseThrow(), 0, 100)).size());
    }

    /**
     * 验证引用版本耗尽时整体事务失败，不残留新空间、配额或回执。
     * @throws Exception 当事务失败时
     */
    @Test void exhaustedBindingVersionRollsBackCandidateCreation() throws Exception {
        var original = apply(create(actor, 0)); var last = bind(actor, original.binding().namespaceId(), Long.MAX_VALUE - 1);
        var exhausted = new MemoryNamespaceBinding(actor.scope(), original.binding().namespaceId(), Long.MAX_VALUE, Instant.EPOCH, last);
        var json = database.mapper.writeValueAsString(Map.of("schemaVersion", 1, "value", exhausted)); database.rewriteRows("binding:", ignored -> json);
        var request = create(actor, Long.MAX_VALUE); assertThrows(ExecutionException.class, () -> apply(request));
        assertTrue(await(database.store.find(request.target())).isEmpty()); assertEquals(exhausted, await(store.readBinding(actor.scope())));
        assertEquals(1, await(database.store.listCreated(actor.userId().orElseThrow(), 0, 100)).size());
    }

    /** 验证非法配额在向数据库提交之前明确失败。 */
    @Test void invalidQuotaDoesNotSubmitWork() {
        assertThrows(IllegalArgumentException.class, () -> store.apply(create(actor, 0), 0, Instant.EPOCH)); verifyNoInteractions(database.database);
    }

    /**
     * 使用有界屏障避免异常用例阻塞测试执行器关闭。
     * @param latch 测试控制信号
     * @throws IllegalStateException 当等待超时或线程被中断时
     */
    private static void waitFor(CountDownLatch latch) {
        try { if (!latch.await(4, TimeUnit.SECONDS)) { throw new IllegalStateException("测试绑定事务屏障超时。"); } }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException("测试绑定事务屏障被中断。"); }
    }
}
