package org.zexnocs.teanekoagent.memory.namespace;

import org.junit.jupiter.api.Test;
import org.zexnocs.teanekoagent.agent.AgentActorContext;
import org.zexnocs.teanekoagent.agent.AgentScope;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.zexnocs.teanekoagent.memory.namespace.MemoryNamespaceMutation.Status.CREATED;

/**
 * 验证注册服务固定身份、懒读策略和完整提交阶段，不把登记误报为 scope 切换。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
class MemoryNamespaceRegistryTest {
    private final IMemoryNamespaceStore store = mock(IMemoryNamespaceStore.class);
    private final IMemoryNamespaceDefaults defaults = mock(IMemoryNamespaceDefaults.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-29T00:00:00Z"), ZoneOffset.UTC);
    private final MemoryNamespaceRegistry registry = new MemoryNamespaceRegistry(store, defaults, clock);

    /**
     * 构造经宿主认证的人类来源。
     * @return 带 TeaNeko UUID 的创建者
     */
    private AgentActorContext actor() {
        return new AgentActorContext(new AgentScope("客户端", "群"), AgentActorContext.Origin.USER,
                Optional.of(UUID.randomUUID()), "平台来源", "消息", "目标");
    }

    /** 验证构造注册服务不解析策略或访问存储。 */
    @Test void constructionDoesNoWork() { verifyNoInteractions(store, defaults); }

    /** 验证创建请求完整保留真实来源与时间，只有提交完成才交付结果。 */
    @Test void createWaitsForCommittedResultAndKeepsTrustedSource() {
        when(defaults.maxCreated()).thenReturn(5); var source = new CompletableFuture<MemoryNamespaceMutation>();
        when(store.create(any(), eq(5))).thenReturn(source);
        var id = UUID.randomUUID(); var actor = actor(); var stage = registry.create(id, actor).toCompletableFuture();
        assertFalse(stage.isDone()); var captured = org.mockito.ArgumentCaptor.forClass(MemoryNamespace.class);
        verify(store).create(captured.capture(), eq(5)); var value = captured.getValue();
        assertEquals(MemoryNamespaceId.created(id), value.id()); assertEquals(new MemoryNamespace.Creation(id, actor), value.creation());
        assertEquals(clock.instant(), value.createdAt()); assertEquals(0, value.generation());
        var result = new MemoryNamespaceMutation(CREATED, value); source.complete(result); assertSame(result, stage.join());
    }

    /** 验证提交失败由原完成链传播，不能返回已登记成功。 */
    @Test void persistenceFailureDoesNotBecomeSuccess() {
        when(defaults.maxCreated()).thenReturn(5); var failure = new IllegalStateException("测试存储失败");
        when(store.create(any(), anyInt())).thenReturn(CompletableFuture.failedStage(failure));
        assertSame(failure, assertThrows(CompletionException.class, () -> registry.create(UUID.randomUUID(), actor()).toCompletableFuture().join()).getCause());
    }

    /** 验证每次创建懒读最新配额，配置失败可修正后恢复。 */
    @Test void quotaReloadAndRecoveryApplyToNextCreation() {
        when(defaults.maxCreated()).thenThrow(new IllegalStateException("测试配置无效")).thenReturn(1).thenReturn(8);
        when(store.create(any(), anyInt())).thenAnswer(call -> CompletableFuture.completedStage(new MemoryNamespaceMutation(CREATED, call.getArgument(0))));
        assertThrows(CompletionException.class, () -> registry.create(UUID.randomUUID(), actor()).toCompletableFuture().join());
        registry.create(UUID.randomUUID(), actor()).toCompletableFuture().join(); registry.create(UUID.randomUUID(), actor()).toCompletableFuture().join();
        verify(store).create(any(), eq(1)); verify(store).create(any(), eq(8));
    }

    /** 验证默认共享空间不读取个人配额，且只有显式调用才尝试登记。 */
    @Test void sharedNamespaceIsExplicitAndDoesNotConsumeUserQuota() {
        when(store.create(any(), eq(1))).thenAnswer(call -> CompletableFuture.completedStage(new MemoryNamespaceMutation(CREATED, call.getArgument(0))));
        var value = registry.ensureShared().toCompletableFuture().join().namespace(); assertNotNull(value);
        assertEquals(MemoryNamespaceId.SHARED, value.id()); assertNull(value.creation()); verifyNoInteractions(defaults);
    }

    /** 验证系统任务不能冒充用户创建或查询个人空间，失败返回已结束阶段。 */
    @Test void systemOriginCannotBorrowUserQuotaOrOwnership() {
        var actor = new AgentActorContext(new AgentScope("客户端", "群"), AgentActorContext.Origin.SYSTEM, Optional.empty(), "", "任务", "目标");
        assertThrows(CompletionException.class, () -> registry.create(UUID.randomUUID(), actor).toCompletableFuture().join());
        assertThrows(CompletionException.class, () -> registry.listCreated(actor, 0, 10).toCompletableFuture().join()); verifyNoInteractions(store, defaults);
    }

    /** 验证分页主体只来自真实调用者 UUID，不使用平台用户编号或目标字段。 */
    @Test void listingUsesAuthenticatedTeaNekoUuid() {
        var actor = actor(); when(store.listCreated(actor.userId().orElseThrow(), 3, 7)).thenReturn(CompletableFuture.completedStage(List.of()));
        assertTrue(registry.listCreated(actor, 3, 7).toCompletableFuture().join().isEmpty());
        verify(store).listCreated(actor.userId().orElseThrow(), 3, 7); verifyNoInteractions(defaults);
    }

    /** 验证无效标识不被自动修剪、改大小写或解释为旧存储定位。 */
    @Test void namespaceIdentifiersRejectAliasesAndOldStorageKeys() {
        for (var value : List.of("", " DEFAULT", "Default", "default ", "scope:1:agent:2", "a".repeat(65), "../secret")) {
            assertThrows(IllegalArgumentException.class, () -> new MemoryNamespaceId(value));
        }
        assertEquals("default", MemoryNamespaceId.SHARED.value());
    }

    /** 验证版本与数据代次独立，未来元数据变更不需要伪造逻辑清空。 */
    @Test void metadataRevisionMayAdvanceWithoutChangingDataGeneration() {
        var request = UUID.randomUUID(); var creation = new MemoryNamespace.Creation(request, actor());
        var metadata = new MemoryNamespace(MemoryNamespaceId.created(request), 4, 1, clock.instant(), creation, UUID.randomUUID());
        var changed = metadata.nextGeneration(UUID.randomUUID()); assertEquals(5, changed.revision()); assertEquals(2, changed.generation());
    }

    /** 验证共享/用户来源、版本和回执不一致时不能构造可持久化数据。 */
    @Test void invalidMetadataCannotPretendToBeRegisteredOrCommitted() {
        assertThrows(IllegalArgumentException.class, () -> new MemoryNamespace(new MemoryNamespaceId("custom"), 0, 0, clock.instant(), null, null));
        assertThrows(IllegalArgumentException.class, () -> new MemoryNamespace(MemoryNamespaceId.SHARED, 0, 1, clock.instant(), null, UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () -> new MemoryNamespaceMutation(CREATED, null));
        when(defaults.maxCreated()).thenReturn(0);
        assertThrows(CompletionException.class, () -> registry.create(UUID.randomUUID(), actor()).toCompletableFuture().join()); verifyNoInteractions(store);
    }
}
