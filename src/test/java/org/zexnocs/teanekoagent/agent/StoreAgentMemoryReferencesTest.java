package org.zexnocs.teanekoagent.agent;

import org.junit.jupiter.api.Test;
import org.zexnocs.teanekoagent.adapter.memory.StoreAgentMemoryReferences;
import org.zexnocs.teanekoagent.agent.access.AgentScopePolicy;
import org.zexnocs.teanekoagent.agent.access.IAgentScopePolicyPort;
import org.zexnocs.teanekoagent.agent.memory.AgentMemoryReferenceException;
import org.zexnocs.teanekoagent.memory.namespace.*;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 验证当前空间引用适配器的按需初始化、只读复核、scope 开关、线程切换与错误隐藏。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
class StoreAgentMemoryReferencesTest {
    private final AgentScope scope = new AgentScope("客户端", "群");
    private final MemoryNamespaceReference fixed = new MemoryNamespaceReference(scope, MemoryNamespaceId.SHARED, 0, 0);
    private final IAgentScopePolicyPort scopes = mock(IAgentScopePolicyPort.class);
    private final IMemoryNamespaceBindingStore bindings = mock(IMemoryNamespaceBindingStore.class);
    private final MemoryNamespaceRegistry registry = mock(MemoryNamespaceRegistry.class);
    private final StoreAgentMemoryReferences references = new StoreAgentMemoryReferences(scopes, bindings, registry, Runnable::run);

    /**
     * 提供已注册且启用的策略，不建立模型或平台环境。
     * @return 不可变启用策略
     */
    private static AgentScopePolicy enabled() { return new AgentScopePolicy(true, true, AgentScopePolicy.Mode.ACTIVE); }

    /**
     * 提取异步失败中的固定引用异常，验证没有携带原配置或数据库 Cause。
     * @param stage 待验证阶段
     * @return 固定类型异常
     */
    private static AgentMemoryReferenceException rejected(CompletionStage<?> stage) {
        var failure = assertThrows(CompletionException.class, () -> stage.toCompletableFuture().join());
        var result = assertInstanceOf(AgentMemoryReferenceException.class, failure.getCause()); assertNull(result.getCause()); return result;
    }

    /** 验证构造没有配置读取、绑定查询或默认空间登记。 */
    @Test void constructionIsLazy() { verifyNoInteractions(scopes, bindings, registry); }

    /** 验证未注册和禁用时，在访问空间存储或登记默认空间之前拒绝。 */
    @Test void unregisteredAndDisabledScopesNeverTouchStorage() {
        for (var policy : List.of(AgentScopePolicy.unregistered(), new AgentScopePolicy(true, false, AgentScopePolicy.Mode.PASSIVE_BASIC))) {
            when(scopes.read(scope)).thenReturn(policy);
            assertEquals(AgentMemoryReferenceException.Reason.DISABLED, rejected(references.resolve(scope)).reason());
            assertEquals(AgentMemoryReferenceException.Reason.DISABLED, rejected(references.verify(fixed)).reason());
        }
        verifyNoInteractions(bindings, registry);
    }

    /** 验证配置读取失败只有固定中文原因，不向引用结果传递敏感文本或异常链。 */
    @Test void scopeFailureIsRedacted() {
        when(scopes.read(scope)).thenThrow(new IllegalStateException("敏感配置内容"));
        var failure = rejected(references.resolve(scope)); assertEquals(AgentMemoryReferenceException.Reason.UNAVAILABLE, failure.reason());
        assertEquals("记忆空间引用读取或校验不可用。", failure.getMessage()); verifyNoInteractions(bindings, registry);
    }

    /** 验证有效已有引用直接返回，解析和复核都不创建默认空间。 */
    @Test void existingReferenceDoesNotCreateSharedState() {
        when(scopes.read(scope)).thenReturn(enabled()); when(bindings.readReference(scope)).thenReturn(CompletableFuture.completedStage(Optional.of(fixed)));
        assertEquals(fixed, references.resolve(scope).toCompletableFuture().join());
        assertNull(references.verify(fixed).toCompletableFuture().join()); verifyNoInteractions(registry);
    }

    /** 验证缺省共享空间只在显式且已启用的解析中登记，完成后重新读取权威引用。 */
    @Test void initialResolutionWaitsForSharedRegistrationAndRereadsBinding() {
        when(scopes.read(scope)).thenReturn(enabled());
        when(bindings.readReference(scope)).thenReturn(CompletableFuture.completedStage(Optional.empty()))
                .thenReturn(CompletableFuture.completedStage(Optional.of(fixed)));
        var pending = new CompletableFuture<MemoryNamespaceMutation>(); when(registry.ensureShared()).thenReturn(pending);
        var result = references.resolve(scope).toCompletableFuture(); assertFalse(result.isDone());
        pending.complete(new MemoryNamespaceMutation(MemoryNamespaceMutation.Status.CREATED,
                new MemoryNamespace(MemoryNamespaceId.SHARED, 0, 0, Instant.EPOCH, null, null)));
        assertEquals(fixed, result.join()); verify(bindings, times(2)).readReference(scope); verify(registry).ensureShared();
    }

    /** 验证异步读取后 scope 已停用时不开始默认空间写入。 */
    @Test void disabledBeforeDefaultRegistrationPreventsWrite() {
        when(scopes.read(scope)).thenReturn(enabled(), AgentScopePolicy.unregistered());
        when(bindings.readReference(scope)).thenReturn(CompletableFuture.completedStage(Optional.empty()));
        assertEquals(AgentMemoryReferenceException.Reason.DISABLED, rejected(references.resolve(scope)).reason()); verifyNoInteractions(registry);
    }

    /** 验证读取期间 scope 注销后，迟到引用不会被交付为有效结果。 */
    @Test void disabledAfterReadRejectsLateReference() {
        when(scopes.read(scope)).thenReturn(enabled()); var pending = new CompletableFuture<Optional<MemoryNamespaceReference>>();
        when(bindings.readReference(scope)).thenReturn(pending); var result = references.resolve(scope);
        when(scopes.read(scope)).thenReturn(AgentScopePolicy.unregistered()); pending.complete(Optional.of(fixed));
        assertEquals(AgentMemoryReferenceException.Reason.DISABLED, rejected(result).reason());
    }

    /** 验证只读复核分辨 namespace、绑定版本和 generation，失效不刷新原引用。 */
    @Test void verificationDetectsEveryReferenceBoundary() {
        when(scopes.read(scope)).thenReturn(enabled());
        for (var changed : List.of(new MemoryNamespaceReference(scope, new MemoryNamespaceId("other"), 1, 0),
                new MemoryNamespaceReference(scope, MemoryNamespaceId.SHARED, 2, 0), new MemoryNamespaceReference(scope, MemoryNamespaceId.SHARED, 0, 1))) {
            when(bindings.readReference(scope)).thenReturn(CompletableFuture.completedStage(Optional.of(changed)));
            assertEquals(AgentMemoryReferenceException.Reason.CHANGED, rejected(references.verify(fixed)).reason());
        }
        verifyNoInteractions(registry);
    }

    /** 验证复核时记录缺失只拒绝，不尝试重建默认空间或回退新 generation。 */
    @Test void verificationNeverInitializesMissingStorage() {
        when(scopes.read(scope)).thenReturn(enabled()); when(bindings.readReference(scope)).thenReturn(CompletableFuture.completedStage(Optional.empty()));
        assertEquals(AgentMemoryReferenceException.Reason.UNAVAILABLE, rejected(references.verify(fixed)).reason()); verifyNoInteractions(registry);
    }

    /** 验证错误端口返回其他 scope 时拒绝，摘要或相似名称不替代完整定位。 */
    @Test void foreignScopeFromStoreIsRejected() {
        when(scopes.read(scope)).thenReturn(enabled());
        var foreign = new MemoryNamespaceReference(new AgentScope("另一客户端", "群"), MemoryNamespaceId.SHARED, 0, 0);
        when(bindings.readReference(scope)).thenReturn(CompletableFuture.completedStage(Optional.of(foreign)));
        assertEquals(AgentMemoryReferenceException.Reason.UNAVAILABLE, rejected(references.resolve(scope)).reason());
        assertEquals(AgentMemoryReferenceException.Reason.UNAVAILABLE, rejected(references.verify(fixed)).reason());
    }

    /**
     * 验证数据库完成线程只排队后置检查，真实配置读取仍切回借用业务执行域。
     * @throws Exception 当异步数据库测试线程未能结束时
     */
    @Test void postReadScopeChecksReturnToBusinessExecutor() throws Exception {
        var tasks = new ConcurrentLinkedQueue<Runnable>(); var businessThread = Thread.currentThread();
        when(scopes.read(scope)).thenAnswer(ignored -> { assertSame(businessThread, Thread.currentThread()); return enabled(); });
        var pending = new CompletableFuture<Optional<MemoryNamespaceReference>>(); when(bindings.readReference(scope)).thenReturn(pending);
        var adapter = new StoreAgentMemoryReferences(scopes, bindings, registry, tasks::add);
        var result = adapter.resolve(scope).toCompletableFuture(); verifyNoInteractions(scopes, bindings, registry);
        Objects.requireNonNull(tasks.poll()).run(); assertFalse(result.isDone());
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) { workers.submit(() -> pending.complete(Optional.of(fixed))).get(3, TimeUnit.SECONDS); }
        assertFalse(result.isDone()); Objects.requireNonNull(tasks.poll()).run(); assertEquals(fixed, result.join());
    }

    /** 验证执行器拒绝及数据库失败都明确结束阶段且隐藏原始失败内容。 */
    @Test void executorAndStorageFailuresAreRedacted() {
        var rejectedExecutor = new StoreAgentMemoryReferences(scopes, bindings, registry,
                ignored -> { throw new RejectedExecutionException("测试执行器不可用"); });
        assertEquals(AgentMemoryReferenceException.Reason.UNAVAILABLE, rejected(rejectedExecutor.resolve(scope)).reason());
        verifyNoInteractions(scopes, bindings, registry);
        when(scopes.read(scope)).thenReturn(enabled()); when(bindings.readReference(scope)).thenReturn(CompletableFuture.failedStage(new IllegalStateException("敏感存储正文")));
        assertEquals(AgentMemoryReferenceException.Reason.UNAVAILABLE, rejected(references.resolve(scope)).reason());
        verifyNoInteractions(registry);
    }
}
