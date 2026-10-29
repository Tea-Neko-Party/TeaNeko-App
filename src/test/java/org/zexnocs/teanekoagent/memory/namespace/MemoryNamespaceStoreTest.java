package org.zexnocs.teanekoagent.memory.namespace;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.zexnocs.teanekoagent.adapter.memory.EasyDataMemoryNamespaceStore;
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
import static org.zexnocs.teanekoagent.memory.namespace.MemoryNamespaceMutation.Status.*;

/**
 * 验证注册适配器的提交语义、并发配额、重放、代次仲裁和损坏数据隔离。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
class MemoryNamespaceStoreTest {
    private final NamespaceDatabaseFixture database = new NamespaceDatabaseFixture();
    private final IMemoryNamespaceStore store = database.store;
    private final UUID creator = UUID.randomUUID();

    /** 等待测试拥有的事务执行器结束。 */
    @AfterEach void cleanup() { database.close(); }

    /**
     * 构造真实人类来源，不用平台编号代替 UUID。
     * @param user 用户 UUID
     * @param scope 来源 scope
     * @return 可信宿主来源
     */
    private AgentActorContext actor(UUID user, String scope) {
        return new AgentActorContext(new AgentScope("客户端", scope), AgentActorContext.Origin.USER, Optional.of(user), "平台用户", "消息", "目标");
    }

    /**
     * 创建初始登记候选。
     * @param request 创建请求
     * @param user 创建者
     * @param scope 来源 scope
     * @return 可持久化候选
     */
    private MemoryNamespace candidate(UUID request, UUID user, String scope) {
        return new MemoryNamespace(MemoryNamespaceId.created(request), 0, 0, Instant.EPOCH,
                new MemoryNamespace.Creation(request, actor(user, scope)), null);
    }

    /**
     * 等待阶段并保留测试失败的确定边界。
     * @param stage 事务完成阶段
     * @param <T> 结果类型
     * @return 事务结果
     * @throws Exception 当事务失败或超过时限时
     */
    private static <T> T await(CompletionStage<T> stage) throws Exception { return stage.toCompletableFuture().get(5, TimeUnit.SECONDS); }

    /**
     * 验证构造和不存在读取不隐式登记共享空间。
     * @throws Exception 当读取失败时
     */
    @Test void missingReadDoesNotCreateDefaultOrWriteAnything() throws Exception {
        assertTrue(await(store.find(MemoryNamespaceId.SHARED)).isEmpty());
        verify(database.repository, never()).saveAndFlush(any());
    }

    /**
     * 验证创建来源、只读分页、默认空间免配额和新适配器恢复。
     * @throws Exception 当事务或读取失败时
     */
    @Test void createdMetadataAndOwnerIndexSurviveNewStoreInstance() throws Exception {
        var a = candidate(UUID.randomUUID(), creator, "群一"); var b = candidate(UUID.randomUUID(), creator, "群二");
        assertEquals(CREATED, await(store.create(a, 2)).status()); assertEquals(CREATED, await(store.create(b, 2)).status());
        var shared = new MemoryNamespace(MemoryNamespaceId.SHARED, 0, 0, Instant.EPOCH, null, null);
        assertEquals(CREATED, await(store.create(shared, 1)).status());
        var restored = new EasyDataMemoryNamespaceStore(database.repository, database.database, database.mapper);
        assertEquals(a, await(restored.find(a.id())).orElseThrow());
        var page = await(restored.listCreated(creator, 0, 1)); assertEquals(List.of(a), page);
        assertThrows(UnsupportedOperationException.class, page::clear);
        assertEquals(List.of(b), await(restored.listCreated(creator, 1, 100)));
        assertTrue(await(restored.listCreated(creator, Integer.MAX_VALUE, 100)).isEmpty());
        assertTrue(await(restored.listCreated(UUID.randomUUID(), 0, 10)).isEmpty());
        verify(database.repository, never()).findAll();
    }

    /**
     * 验证配额已满时相同创建请求仍可确认原记录，不覆盖创建时刻或占第二个配额。
     * @throws Exception 当事务失败时
     */
    @Test void duplicateCreationKeepsOriginalTimeEvenAtQuota() throws Exception {
        var a = candidate(UUID.randomUUID(), creator, "群"); await(store.create(a, 1));
        var retry = new MemoryNamespace(a.id(), 0, 0, Instant.EPOCH.plusSeconds(100), a.creation(), null);
        var result = await(store.create(retry, 1)); assertEquals(UNCHANGED, result.status()); assertEquals(a, result.namespace());
        assertEquals(QUOTA_EXCEEDED, await(store.create(candidate(UUID.randomUUID(), creator, "群"), 1)).status());
        assertEquals(List.of(a), await(store.listCreated(creator, 0, 100)));
    }

    /**
     * 验证相同请求 ID 的用户或来源变化返回冲突，不把原创建者改成新调用者。
     * @throws Exception 当事务失败时
     */
    @Test void changedActorOrSourceCannotReplaceOriginalCreation() throws Exception {
        var request = UUID.randomUUID(); var a = candidate(request, creator, "群一"); await(store.create(a, 5));
        assertEquals(CONFLICT, await(store.create(candidate(request, UUID.randomUUID(), "群一"), 5)).status());
        assertEquals(CONFLICT, await(store.create(candidate(request, creator, "群二"), 5)).status());
        assertEquals(a, await(store.find(a.id())).orElseThrow());
    }

    /**
     * 验证多人配额彼此独立，配额缩小不会自动删除已有空间。
     * @throws Exception 当事务失败时
     */
    @Test void quotasArePerUuidAndShrinkingDoesNotDeleteData() throws Exception {
        var first = candidate(UUID.randomUUID(), creator, "群"); var second = candidate(UUID.randomUUID(), creator, "群");
        await(store.create(first, 2)); await(store.create(second, 2));
        assertEquals(QUOTA_EXCEEDED, await(store.create(candidate(UUID.randomUUID(), creator, "群"), 1)).status());
        var other = candidate(UUID.randomUUID(), UUID.randomUUID(), "群"); assertEquals(CREATED, await(store.create(other, 1)).status());
        assertEquals(2, await(store.listCreated(creator, 0, 100)).size());
    }

    /**
     * 验证三十个真实并发事务至多创建五个空间，索引竞争后重读而不丢失登记。
     * @throws Exception 当并发事务失败或未能结束时
     */
    @Test void concurrentCreationCannotExceedQuotaOrLoseIndexEntries() throws Exception {
        var entered = new CountDownLatch(2); var release = new CountDownLatch(1); var arrivals = new AtomicInteger();
        database.beforeCommit.set(() -> {
            if (arrivals.incrementAndGet() <= 2) { entered.countDown(); waitFor(release); }
        });
        var first = store.create(candidate(UUID.randomUUID(), creator, "群"), 5);
        var second = store.create(candidate(UUID.randomUUID(), creator, "群"), 5);
        assertTrue(entered.await(3, TimeUnit.SECONDS)); release.countDown();
        var stages = new ArrayList<CompletionStage<MemoryNamespaceMutation>>(List.of(first, second));
        for (int i = 2; i < 30; i++) { stages.add(store.create(candidate(UUID.randomUUID(), creator, "群"), 5)); }
        var results = new ArrayList<MemoryNamespaceMutation>(); for (var stage : stages) { results.add(await(stage)); }
        assertEquals(5, results.stream().filter(result -> result.status() == CREATED).count());
        assertEquals(25, results.stream().filter(result -> result.status() == QUOTA_EXCEEDED).count());
        assertEquals(5, await(store.listCreated(creator, 0, 100)).size()); assertTrue(database.retries.get() > 0);
    }

    /**
     * 验证同一候选并发创建只登记一次，另一事务重试后确认相同元数据。
     * @throws Exception 当并发创建失败时
     */
    @Test void concurrentDuplicateCreationIsIdempotent() throws Exception {
        var a = candidate(UUID.randomUUID(), creator, "群"); var results = new ArrayList<CompletionStage<MemoryNamespaceMutation>>();
        for (int i = 0; i < 20; i++) { results.add(store.create(a, 5)); }
        int created = 0; for (var stage : results) { if (await(stage).status() == CREATED) { created++; } }
        assertEquals(1, created); assertEquals(List.of(a), await(store.listCreated(creator, 0, 100)));
    }

    /**
     * 验证提交前 Future 不成功；事务回滚后原创建记录与配额均不残留。
     * @throws Exception 当事务测试未能及时结束时
     */
    @Test void commitFailureDoesNotExposeSuccessOrConsumeQuota() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        database.beforeCommit.set(() -> { entered.countDown(); waitFor(release); throw new IllegalStateException("测试提交失败"); });
        var a = candidate(UUID.randomUUID(), creator, "群"); var stage = store.create(a, 1).toCompletableFuture();
        assertTrue(entered.await(3, TimeUnit.SECONDS)); assertFalse(stage.isDone()); release.countDown();
        assertThrows(ExecutionException.class, () -> stage.get(5, TimeUnit.SECONDS));
        database.beforeCommit.set(() -> {});
        assertTrue(await(store.find(a.id())).isEmpty()); assertTrue(await(store.listCreated(creator, 0, 100)).isEmpty());
        assertEquals(CREATED, await(store.create(a, 1)).status());
    }

    /**
     * 验证取消返回的视图不取消已交给数据库的事务，也不回滚已提交副作用。
     * @throws Exception 当受控提交未能结束时
     */
    @Test void cancellingReturnedViewDoesNotCancelSubmittedTransaction() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var committed = new CountDownLatch(1);
        database.beforeCommit.set(() -> { entered.countDown(); waitFor(release); });
        var a = candidate(UUID.randomUUID(), creator, "群"); var source = store.create(a, 1);
        source.whenComplete((result, failure) -> committed.countDown());
        assertTrue(entered.await(3, TimeUnit.SECONDS)); assertTrue(source.toCompletableFuture().cancel(true)); release.countDown();
        assertTrue(committed.await(3, TimeUnit.SECONDS)); database.beforeCommit.set(() -> {});
        assertEquals(a, await(store.find(a.id())).orElseThrow());
    }

    /**
     * 验证代次 CAS、原始版本重试、后续旧请求和新适配器读取都不会重复推进。
     * @throws Exception 当代次事务失败时
     */
    @Test void generationChangesRequireOriginalRevisionAndReplayAtMostOnce() throws Exception {
        var a = candidate(UUID.randomUUID(), creator, "群"); await(store.create(a, 5)); var operation = UUID.randomUUID();
        var result = await(store.advanceGeneration(a.id(), 0, operation)); assertEquals(GENERATION_ADVANCED, result.status());
        var current = result.namespace(); assertNotNull(current); assertEquals(1, current.generation()); assertEquals(a.creation(), current.creation());
        assertEquals(UNCHANGED, await(store.advanceGeneration(a.id(), 0, operation)).status());
        assertEquals(CONFLICT, await(store.advanceGeneration(a.id(), 1, operation)).status());
        assertEquals(CONFLICT, await(store.advanceGeneration(a.id(), 0, UUID.randomUUID())).status());
        assertEquals(GENERATION_ADVANCED, await(store.advanceGeneration(a.id(), 1, UUID.randomUUID())).status());
        assertEquals(CONFLICT, await(store.advanceGeneration(a.id(), 0, operation)).status());
        assertEquals(2, await(store.find(a.id())).orElseThrow().generation());
    }

    /**
     * 验证两个管理请求竞争相同版本时仅一个推进，另一请求返回业务冲突。
     * @throws Exception 当并发管理事务失败时
     */
    @Test void concurrentGenerationChangesHaveOneWinner() throws Exception {
        var a = candidate(UUID.randomUUID(), creator, "群"); await(store.create(a, 5));
        var stages = new ArrayList<CompletionStage<MemoryNamespaceMutation>>();
        for (int i = 0; i < 20; i++) { stages.add(store.advanceGeneration(a.id(), 0, UUID.randomUUID())); }
        int updated = 0; int conflicts = 0;
        for (var stage : stages) { var result = await(stage); if (result.status() == GENERATION_ADVANCED) { updated++; } else if (result.status() == CONFLICT) { conflicts++; } }
        assertEquals(1, updated); assertEquals(19, conflicts); assertEquals(1, await(store.find(a.id())).orElseThrow().generation());
    }

    /**
     * 验证不存在的空间换代不创建记录。
     * @throws Exception 当事务失败时
     */
    @Test void advancingUnknownNamespaceReturnsMissing() throws Exception {
        assertEquals(NOT_FOUND, await(store.advanceGeneration(MemoryNamespaceId.SHARED, 0, UUID.randomUUID())).status());
        verify(database.repository, never()).saveAndFlush(any());
    }

    /**
     * 验证损坏或未来 schema 均明确失败，不当作空空间覆盖旧行。
     * @throws Exception 当提交等待失败时
     */
    @Test void corruptOrFutureSchemaNeverBecomesDefaultState() throws Exception {
        var a = candidate(UUID.randomUUID(), creator, "群");
        for (var json : List.of("{敏感正文", "null", "{\"schemaVersion\":2,\"value\":" + database.mapper.writeValueAsString(a) + "}")) {
            database.seedNamespace(a.id(), json);
            assertThrows(ExecutionException.class, () -> await(store.find(a.id())));
            assertThrows(ExecutionException.class, () -> await(store.create(a, 5)));
        }
        verify(database.repository, never()).saveAndFlush(any());
    }

    /**
     * 验证损坏配额索引不能使已用配额被当作零，失败也不提交新空间。
     * @throws Exception 当事务失败时
     */
    @Test void corruptCreatorIndexCannotResetQuota() throws Exception {
        database.seedCreator(creator, "{\"schemaVersion\":2}"); var a = candidate(UUID.randomUUID(), creator, "群");
        assertThrows(ExecutionException.class, () -> await(store.create(a, 5))); assertTrue(await(store.find(a.id())).isEmpty());
    }

    /**
     * 验证存储键与正文中的空间 ID 不一致时失败，不读取为其他空间。
     * @throws Exception 当事务失败时
     */
    @Test void mismatchedStorageIdentityIsRejected() throws Exception {
        var a = candidate(UUID.randomUUID(), creator, "群"); var b = candidate(UUID.randomUUID(), creator, "群");
        database.seedNamespace(a.id(), database.mapper.writeValueAsString(Map.of("schemaVersion", 1, "value", b)));
        assertThrows(ExecutionException.class, () -> await(store.find(a.id())));
    }

    /**
     * 验证版本耗尽不能溢出后重新获得可写代次。
     * @throws Exception 当等待事务失败时
     */
    @Test void generationVersionExhaustionFailsWithoutWriting() throws Exception {
        var a = candidate(UUID.randomUUID(), creator, "群");
        var exhausted = new MemoryNamespace(a.id(), Long.MAX_VALUE, Long.MAX_VALUE, a.createdAt(), a.creation(), UUID.randomUUID());
        database.seedNamespace(a.id(), database.mapper.writeValueAsString(Map.of("schemaVersion", 1, "value", exhausted)));
        assertThrows(ExecutionException.class, () -> await(store.advanceGeneration(a.id(), Long.MAX_VALUE, UUID.randomUUID())));
        assertEquals(exhausted, await(store.find(a.id())).orElseThrow());
    }

    /** 验证非法候选与分页参数在提交前失败。 */
    @Test void invalidParametersNeverSubmitDatabaseWork() {
        var a = candidate(UUID.randomUUID(), creator, "群");
        assertThrows(IllegalArgumentException.class, () -> store.create(a, 0));
        assertThrows(IllegalArgumentException.class, () -> store.create(a.nextGeneration(UUID.randomUUID()), 5));
        assertThrows(IllegalArgumentException.class, () -> store.listCreated(creator, -1, 1));
        assertThrows(IllegalArgumentException.class, () -> store.listCreated(creator, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> store.listCreated(creator, 0, 101));
        assertThrows(IllegalArgumentException.class, () -> store.advanceGeneration(a.id(), -1, UUID.randomUUID()));
        verifyNoInteractions(database.database);
    }

    /**
     * 为事务屏障提供有界等待，失败时不会无限挂起执行器关闭。
     * @param latch 测试控制信号
     * @throws IllegalStateException 当等待超时或线程被中断时
     */
    private static void waitFor(CountDownLatch latch) {
        try { if (!latch.await(4, TimeUnit.SECONDS)) { throw new IllegalStateException("测试事务屏障超时。"); } }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException("测试事务屏障被中断。"); }
    }
}
