package org.zexnocs.teanekoagent.memory.namespace;

import org.junit.jupiter.api.Test;
import org.zexnocs.teanekoagent._app_config.MemoryNamespaceConfig;
import org.zexnocs.teanekoagent.agent.AgentActorContext;
import org.zexnocs.teanekoagent.agent.AgentScope;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.zexnocs.teanekoagent.memory.namespace.MemoryNamespaceBindingOutcome.Status.BOUND;

/**
 * 验证绑定服务保持可信来源、预期版本和原始提交结果，不把管理权限或会话同步混入存储。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
class MemoryNamespaceBindingsTest {
    private final IMemoryNamespaceBindingStore store = mock(IMemoryNamespaceBindingStore.class);
    private final IMemoryNamespaceDefaults defaults = mock(IMemoryNamespaceDefaults.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-29T00:00:00Z"), ZoneOffset.UTC);
    private final MemoryNamespaceBindings service = new MemoryNamespaceBindings(store, defaults, clock);
    private final AgentActorContext actor = new AgentActorContext(new AgentScope("客户端", "群"), AgentActorContext.Origin.USER,
            Optional.of(UUID.randomUUID()), "平台用户", "原始消息", "固定目标");

    /**
     * 为受控存储替身构造提交后回执，不执行实际绑定或权限判断。
     * @param request 原始命令
     * @return 定位一致的提交结果
     */
    private MemoryNamespaceBindingOutcome committed(MemoryNamespaceBindingRequest request) {
        var binding = MemoryNamespaceBinding.initial(request.actor().scope()).next(request, clock.instant());
        return new MemoryNamespaceBindingOutcome(BOUND, binding, new MemoryNamespaceReference(binding.scope(), binding.namespaceId(), binding.revision(), 0));
    }

    /** 验证服务和组合根构造不访问配置、存储或自动创建共享空间。 */
    @Test void constructorsAndCompositionRootDoNoWork() {
        assertNotNull(new MemoryNamespaceConfig().memoryNamespaceBindings(store, defaults)); verifyNoInteractions(store, defaults);
    }

    /** 验证原始请求、actor、创建目标、配额和时刻固定，提交前 Stage 不成功。 */
    @Test void createAndBindPreservesOriginalCommandAndWaitsForCommit() {
        when(defaults.maxCreated()).thenReturn(5); var source = new CompletableFuture<MemoryNamespaceBindingOutcome>();
        when(store.apply(any(), eq(5), eq(clock.instant()))).thenReturn(source);
        var id = UUID.randomUUID(); var stage = service.createAndBind(id, actor, 0).toCompletableFuture(); assertFalse(stage.isDone());
        var captured = org.mockito.ArgumentCaptor.forClass(MemoryNamespaceBindingRequest.class);
        verify(store).apply(captured.capture(), eq(5), eq(clock.instant())); var request = captured.getValue();
        assertEquals(id, request.requestId()); assertEquals(actor, request.actor()); assertEquals(MemoryNamespaceId.created(id), request.target());
        assertEquals(MemoryNamespaceBindingRequest.Mode.CREATE_AND_BIND, request.mode());
        var result = committed(request); source.complete(result); assertSame(result, stage.join());
    }

    /** 验证绑定已有空间不读取创建配额，也不替换调用者提供的原始预期版本。 */
    @Test void existingBindDoesNotReadQuotaOrChangeExpectedVersion() {
        var target = new MemoryNamespaceId("existing"); var id = UUID.randomUUID();
        var request = new MemoryNamespaceBindingRequest(id, actor, target, 7, MemoryNamespaceBindingRequest.Mode.BIND_EXISTING);
        var rejected = new MemoryNamespaceBindingOutcome(MemoryNamespaceBindingOutcome.Status.CONFLICT, MemoryNamespaceBinding.initial(actor.scope()), null);
        when(store.apply(request, 1, clock.instant())).thenReturn(CompletableFuture.completedStage(rejected));
        assertSame(rejected, service.bindExisting(id, actor, target, 7).toCompletableFuture().join()); verifyNoInteractions(defaults);
    }

    /** 验证本地配额在下一次创建生效，配置错误不调用存储，修正后恢复。 */
    @Test void invalidQuotaDoesNotSubmitAndCanRecoverOnNextCall() {
        when(defaults.maxCreated()).thenReturn(0).thenReturn(2);
        assertThrows(CompletionException.class, () -> service.createAndBind(UUID.randomUUID(), actor, 0).toCompletableFuture().join()); verifyNoInteractions(store);
        when(store.apply(any(), eq(2), any())).thenAnswer(call -> CompletableFuture.completedStage(committed(call.getArgument(0))));
        assertEquals(BOUND, service.createAndBind(UUID.randomUUID(), actor, 0).toCompletableFuture().join().status());
    }

    /** 验证系统任务不继承聊天用户身份，不能创建空间或自行改变当前 scope 引用。 */
    @Test void systemOriginIsRejectedBeforeQuotaOrStorage() {
        var system = new AgentActorContext(actor.scope(), AgentActorContext.Origin.SYSTEM, Optional.empty(), "", "任务", "目标");
        assertThrows(CompletionException.class, () -> service.createAndBind(UUID.randomUUID(), system, 0).toCompletableFuture().join());
        assertThrows(CompletionException.class, () -> service.bindExisting(UUID.randomUUID(), system, MemoryNamespaceId.SHARED, 0).toCompletableFuture().join());
        verifyNoInteractions(defaults, store);
    }

    /** 验证存储同步拒绝及异步提交失败都通过阶段传播，不伪造绑定成功。 */
    @Test void synchronousAndAsynchronousPersistenceFailuresPropagate() {
        var failure = new IllegalStateException("测试事务失败。"); when(defaults.maxCreated()).thenReturn(5);
        when(store.apply(any(), anyInt(), any())).thenThrow(failure);
        assertSame(failure, assertThrows(CompletionException.class, () -> service.createAndBind(UUID.randomUUID(), actor, 0).toCompletableFuture().join()).getCause());
        doReturn(CompletableFuture.failedStage(failure)).when(store).apply(any(), anyInt(), any());
        assertSame(failure, assertThrows(CompletionException.class, () -> service.bindExisting(UUID.randomUUID(), actor, MemoryNamespaceId.SHARED, 0).toCompletableFuture().join()).getCause());
    }

    /** 验证读取直接等待当前存储引用，不根据历史回执或默认配置推导当前 generation。 */
    @Test void referenceReadsUseCurrentStoreSnapshot() {
        var reference = new MemoryNamespaceReference(actor.scope(), MemoryNamespaceId.SHARED, 0, 9);
        var source = new CompletableFuture<Optional<MemoryNamespaceReference>>(); when(store.readReference(actor.scope())).thenReturn(source);
        var stage = service.readReference(actor.scope()).toCompletableFuture(); assertFalse(stage.isDone());
        source.complete(Optional.of(reference)); assertEquals(reference, stage.join().orElseThrow()); verifyNoInteractions(defaults);
    }

    /** 验证无效版本、创建目标、回执或定位无法伪装成已提交绑定。 */
    @Test void malformedCommandsAndSnapshotsAreRejected() {
        var id = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> new MemoryNamespaceBindingRequest(id, actor, MemoryNamespaceId.SHARED, 0, MemoryNamespaceBindingRequest.Mode.CREATE_AND_BIND));
        assertThrows(IllegalArgumentException.class, () -> new MemoryNamespaceBindingRequest(id, actor, MemoryNamespaceId.SHARED, -1, MemoryNamespaceBindingRequest.Mode.BIND_EXISTING));
        assertThrows(IllegalArgumentException.class, () -> new MemoryNamespaceBinding(actor.scope(), new MemoryNamespaceId("existing"), 0, Instant.EPOCH, null));
        assertThrows(IllegalArgumentException.class, () -> new MemoryNamespaceBindingOutcome(BOUND, MemoryNamespaceBinding.initial(actor.scope()), null));
        assertThrows(IllegalArgumentException.class, () -> new MemoryNamespaceReference(actor.scope(), MemoryNamespaceId.SHARED, 0, -1));
        var request = new MemoryNamespaceBindingRequest(id, actor, new MemoryNamespaceId("existing"), 1, MemoryNamespaceBindingRequest.Mode.BIND_EXISTING);
        assertThrows(IllegalArgumentException.class, () -> MemoryNamespaceBinding.initial(actor.scope()).next(request, clock.instant()));
    }
}
