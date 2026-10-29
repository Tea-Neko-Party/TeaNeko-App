package org.zexnocs.teanekoagent.agent.conversation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.zexnocs.teanekoagent.agent.*;
import org.zexnocs.teanekoagent.agent.access.AgentAccessDecision;
import org.zexnocs.teanekoagent.llm.framework.interaction.InteractionRuntime;
import org.zexnocs.teanekoagent.llm.framework.message.LLMUserMessage;
import org.zexnocs.teanekoagent.llm.framework.message.content.LLMContentListBuilder;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptionsSnapshot;
import org.zexnocs.teanekoagent.memory.namespace.MemoryNamespaceId;
import org.zexnocs.teanekoagent.memory.namespace.MemoryNamespaceReference;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.logger.ILogger;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 验证持久化引用在默认邮箱队首、同步通知、结果提交与重放中的隔离和资源拥有关系。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
@Timeout(10)
class AgentConversationMemoryTest {
    private final InteractionRuntime runtime = new InteractionRuntime();
    private final AgentRegistry registry = new AgentRegistry();
    private final AgentFactory factory = new AgentFactory(registry);
    private final AgentHandle handle = factory.create(new AgentDefinition("会话记忆", new LLMModelOptionsSnapshot(null), Set.of(AgentActivity.values())));
    private final AgentScope scope = new AgentScope("客户端", "群");
    private final MemoryNamespaceReference initial = new MemoryNamespaceReference(scope, MemoryNamespaceId.SHARED, 0, 0);
    private final AtomicReference<MemoryNamespaceReference> current = new AtomicReference<>(initial);
    private final AtomicReference<Supplier<CompletionStage<MemoryNamespaceReference>>> resolve = new AtomicReference<>(() -> CompletableFuture.completedStage(current.get()));
    private final AtomicReference<Function<MemoryNamespaceReference, CompletionStage<Void>>> verify = new AtomicReference<>(this::initialVerification);
    private final AtomicReference<Supplier<CompletionStage<AgentAccessDecision>>> gate = new AtomicReference<>(() -> CompletableFuture.completedStage(AgentConversationTestCall.allowedAccess()));
    private final IAgentMemoryReferences references = mock(IAgentMemoryReferences.class);
    private final AgentConversationTestBackend backend = new AgentConversationTestBackend();
    private final IAgentTurnService port = mock(IAgentTurnService.class);
    private final AgentConversationService service = new AgentConversationService(port, AgentQueueSettings::defaults, runtime,
            runtime.scheduler, runtime.clock, mock(ILogger.class), AgentConversationSettings::defaults,
            (agent, request) -> AgentConversationOptions.defaults(), new ObjectMapper(), references);

    /** 连接可控异步引用及冷轮次，不启动数据库或平台。 */
    AgentConversationMemoryTest() {
        when(port.checkAccess(any(), any())).thenAnswer(call -> gate.get().get());
        when(port.prepareReferenced(any(), any(), any())).thenAnswer(call -> backend.prepareReferenced(call.getArgument(0), call.getArgument(1), call.getArgument(2)));
        when(references.resolve(any())).thenAnswer(call -> resolve.get().get());
        when(references.verify(any())).thenAnswer(call -> verify.get().apply(call.getArgument(0)));
    }

    /**
     * 根据显式测试参数检查当前引用，不通过线程状态传播身份。
     * @param expected 本轮固定引用
     * @return 一致时完成，否则明确失效
     */
    private CompletionStage<Void> initialVerification(MemoryNamespaceReference expected) {
        return current.get().equals(expected) ? CompletableFuture.completedStage(null)
                : CompletableFuture.failedStage(new AgentMemoryReferenceException(AgentMemoryReferenceException.Reason.CHANGED));
    }

    /** 释放测试拥有的请求和实例，异步观察不能遗留计时器。 */
    @AfterEach void cleanup() { backend.autoAck.set(true); service.close(); backend.close(); registry.close(); runtime.runAll(); assertEquals(0, runtime.pendingTimers()); }

    /**
     * 构造同群的真实来源输入。
     * @return 尚未绑定空间和代次的原始请求
     */
    private AgentRunRequest request() {
        var actor = new AgentActorContext(scope, AgentActorContext.Origin.USER, Optional.of(UUID.randomUUID()), "用户", UUID.randomUUID().toString(), "目标");
        return new AgentRunRequest(UUID.randomUUID(), actor, AgentActivity.SERVICE,
                List.of(LLMUserMessage.builder().contents(new LLMContentListBuilder().addText("输入").build()).build()));
    }

    /**
     * 完成真实邮箱状态下的一次成功轮次。
     * @param owner 目标实例
     * @param request 原始请求
     * @return 最终成功结果
     */
    private AgentRunResult success(AgentHandle owner, AgentRunRequest request) {
        var result = service.run(owner, request).toCompletableFuture(); runtime.runAll();
        backend.calls.get(request.requestId()).complete(AgentRunResult.Status.SUCCESS); runtime.runAll();
        assertEquals(AgentRunResult.Status.SUCCESS, result.join().status()); return result.join();
    }

    /**
     * 同步当前 scope 的真实引用，借用父控制在完成后仍保持有效。
     * @return 实际推进的会话数
     */
    private int synchronize() {
        try (var parent = ExecutionControl.create()) {
            var result = service.synchronizeMemory(scope, parent).toCompletableFuture(); runtime.runAll();
            var changed = result.join(); assertDoesNotThrow(parent::checkActive); return changed;
        }
    }

    /** 验证构造及入队不读引用，队首等待解析完成后才创建冷轮次。 */
    @Test void headWaitsForReferenceWithoutEarlyPreparation() {
        verifyNoInteractions(references, port);
        var pending = new CompletableFuture<MemoryNamespaceReference>(); resolve.set(() -> pending);
        var input = request(); var result = service.run(handle, input).toCompletableFuture(); runtime.runAll();
        assertFalse(result.isDone()); assertTrue(backend.calls.isEmpty());
        pending.complete(initial); runtime.runAll(); assertEquals(initial, backend.calls.get(input.requestId()).request.memoryReference());
        backend.calls.get(input.requestId()).complete(AgentRunResult.Status.SUCCESS); runtime.runAll(); assertEquals(AgentRunResult.Status.SUCCESS, result.join().status());
    }

    /** 验证后继使用前一完整成功窗口，组件及交付结果保留同一权威引用。 */
    @Test void unchangedReferenceKeepsHistoryAndProvenance() {
        var first = success(handle, request()); var next = request(); success(handle, next);
        var box = service.snapshot(handle, scope).orElseThrow();
        assertEquals(initial, box.require(AgentComponents.MEMORY_REFERENCE)); assertEquals(initial, first.memoryReference());
        assertEquals(3, backend.calls.get(next.requestId()).request.messages().size());
        assertEquals(first.conversation(), box.require(AgentConversationState.COMPONENT).stamp());
        assertEquals(0, synchronize()); assertEquals(2, box.require(AgentConversationWindow.COMPONENT).turns().size());
    }

    /** 验证遗漏同步通知后，下一队首先换代清窗口和完成记录再装配输入。 */
    @Test void newHeadRecoversMissedNotificationBeforeReadingHistory() {
        var a = request(); var old = success(handle, a);
        current.set(new MemoryNamespaceReference(scope, new MemoryNamespaceId("other"), 1, 0));
        var b = request(); var latest = success(handle, b);
        assertNotEquals(old.conversation(), latest.conversation()); assertEquals(1, backend.calls.get(b.requestId()).request.messages().size());
        assertTrue(service.snapshot(handle, scope).orElseThrow().require(AgentRequestJournal.COMPONENT).find(a.requestId()).isEmpty());
        assertEquals(AgentRunResult.Status.REPLAY_UNAVAILABLE, service.retry(handle, a).toCompletableFuture().join().status());
    }

    /** 验证换代拒绝旧等待项，旧运行在实际结束回执前仍占槽，新输入不能并行启动。 */
    @Test void synchronizationKeepsOldRunningSlotUntilAcknowledgement() {
        backend.autoAck.set(false);
        var a = request(); var b = request(); var active = service.run(handle, a).toCompletableFuture();
        var waiting = service.run(handle, b).toCompletableFuture(); runtime.runAll();
        current.set(new MemoryNamespaceReference(scope, MemoryNamespaceId.SHARED, 0, 1)); assertEquals(1, synchronize());
        assertEquals(AgentRunResult.Status.STALE_CONVERSATION, waiting.join().status()); assertFalse(active.isDone());
        var c = request(); var next = service.run(handle, c).toCompletableFuture(); runtime.runAll(); assertEquals(1, backend.started.size());
        backend.calls.get(a.requestId()).complete(AgentRunResult.Status.SUCCESS); runtime.runAll();
        assertEquals(AgentRunResult.Status.STALE_CONVERSATION, active.join().status()); assertTrue(active.join().answer().isEmpty());
        assertEquals(2, backend.started.size()); assertEquals(1, backend.calls.get(c.requestId()).request.messages().size());
        backend.calls.get(c.requestId()).complete(AgentRunResult.Status.SUCCESS); runtime.runAll(); assertEquals(AgentRunResult.Status.SUCCESS, next.join().status());
    }

    /** 验证同 scope 多实例一起换代；重复同引用同步不再次清空已建立的新上下文。 */
    @Test void synchronizationCoversEveryRelatedInstanceExactlyOnce() {
        var other = factory.create(handle.definition()); success(handle, request()); success(other, request());
        current.set(new MemoryNamespaceReference(scope, new MemoryNamespaceId("shared-new"), 1, 0)); assertEquals(2, synchronize());
        success(handle, request()); var stamp = service.snapshot(handle, scope).orElseThrow().require(AgentConversationState.COMPONENT).stamp();
        assertEquals(0, synchronize()); assertEquals(stamp, service.snapshot(handle, scope).orElseThrow().require(AgentConversationState.COMPONENT).stamp());
        assertEquals(1, service.snapshot(handle, scope).orElseThrow().require(AgentConversationWindow.COMPONENT).turns().size());
    }

    /** 验证旧同步请求后到时不能回退引用或再次失效新上下文。 */
    @Test void lateOldObservationCannotRewindReference() {
        success(handle, request()); var pending = new CompletableFuture<MemoryNamespaceReference>(); resolve.set(() -> pending);
        try (var parent = ExecutionControl.create()) {
            var old = service.synchronizeMemory(scope, parent).toCompletableFuture(); runtime.runAll();
            current.set(new MemoryNamespaceReference(scope, new MemoryNamespaceId("newer"), 1, 0)); resolve.set(() -> CompletableFuture.completedStage(current.get()));
            assertEquals(1, synchronize()); var stamp = service.snapshot(handle, scope).orElseThrow().require(AgentConversationState.COMPONENT).stamp();
            pending.complete(initial); runtime.runAll(); assertThrows(CompletionException.class, old::join);
            assertEquals(current.get(), service.snapshot(handle, scope).orElseThrow().require(AgentComponents.MEMORY_REFERENCE));
            assertEquals(stamp, service.snapshot(handle, scope).orElseThrow().require(AgentConversationState.COMPONENT).stamp());
        }
    }

    /** 验证通知超时后迟到读取不再换代；下一次明确同步仍可读取并应用当前引用。 */
    @Test void timedOutNotificationCannotApplyLateObservation() {
        var old = success(handle, request()); var pending = new CompletableFuture<MemoryNamespaceReference>(); resolve.set(() -> pending);
        try (var parent = ExecutionControl.create()) {
            var result = service.synchronizeMemory(scope, parent).toCompletableFuture(); runtime.runAll();
            runtime.advance(Duration.ofSeconds(31)); runtime.runAll(); assertThrows(CompletionException.class, result::join);
            current.set(new MemoryNamespaceReference(scope, new MemoryNamespaceId("late"), 1, 0));
            pending.complete(current.get()); runtime.runAll(); assertDoesNotThrow(parent::checkActive);
            assertEquals(old.conversation(), service.snapshot(handle, scope).orElseThrow().require(AgentConversationState.COMPONENT).stamp());
            resolve.set(() -> CompletableFuture.completedStage(current.get())); assertEquals(1, synchronize());
        }
    }

    /** 验证切回 default 仍依绑定版本建立新代次，不复用原空间的旧完成结果。 */
    @Test void returningToOriginalNamespaceStillStartsNewEpoch() {
        var old = success(handle, request()); current.set(new MemoryNamespaceReference(scope, new MemoryNamespaceId("other"), 1, 0)); synchronize();
        current.set(new MemoryNamespaceReference(scope, MemoryNamespaceId.SHARED, 2, 0)); assertEquals(1, synchronize());
        assertNotEquals(old.conversation(), service.snapshot(handle, scope).orElseThrow().require(AgentConversationState.COMPONENT).stamp());
    }

    /** 验证缓存重放及同 ID run 都只读复核当前引用，失败不补执行或返回旧答案。 */
    @Test void cachedReplayChecksCurrentReferenceWithoutExecutingAgain() {
        var input = request(); var old = success(handle, input);
        current.set(new MemoryNamespaceReference(scope, MemoryNamespaceId.SHARED, 0, 1));
        var retry = service.retry(handle, input).toCompletableFuture(); var run = service.run(handle, input).toCompletableFuture(); runtime.runAll();
        assertEquals(AgentRunResult.Status.STALE_CONVERSATION, retry.join().status()); assertTrue(retry.join().answer().isEmpty());
        assertEquals(AgentRunResult.Status.STALE_CONVERSATION, run.join().status()); assertEquals(1, backend.started.size());
        assertEquals(old.interaction(), retry.join().interaction());
    }

    /** 验证最终复核期间变化不提交窗口，诊断保留且后继在新引用下运行。 */
    @Test void changedReferenceAtFinalCommitDoesNotAppendOldTurn() {
        var input = request(); var result = service.run(handle, input).toCompletableFuture(); runtime.runAll();
        current.set(new MemoryNamespaceReference(scope, MemoryNamespaceId.SHARED, 0, 1));
        backend.calls.get(input.requestId()).complete(AgentRunResult.Status.SUCCESS); runtime.runAll();
        assertEquals(AgentRunResult.Status.STALE_CONVERSATION, result.join().status()); assertTrue(result.join().interaction().isPresent());
        assertTrue(service.snapshot(handle, scope).orElseThrow().require(AgentConversationWindow.COMPONENT).turns().isEmpty());
        success(handle, request());
    }

    /** 验证最终复核受原轮次总时限约束，迟到允许不能追加历史或公开答案。 */
    @Test void finalVerificationDeadlineIsBoundedAndLateCompletionIgnored() {
        var pending = new CompletableFuture<Void>(); verify.set(ignored -> pending);
        var input = request(); var result = service.run(handle, input).toCompletableFuture(); runtime.runAll();
        backend.calls.get(input.requestId()).complete(AgentRunResult.Status.SUCCESS); runtime.runAll(); assertFalse(result.isDone());
        runtime.advance(Duration.ofSeconds(121)); runtime.runAll(); assertEquals(AgentRunResult.Status.TIMED_OUT, result.join().status());
        pending.complete(null); runtime.runAll(); assertTrue(result.join().answer().isEmpty());
        assertTrue(service.snapshot(handle, scope).orElseThrow().require(AgentConversationWindow.COMPONENT).turns().isEmpty());
    }

    /** 验证队首解析超时不创建冷轮次，迟到引用也不能写入会话组件。 */
    @Test void pendingHeadResolutionExpiresWithQueueDeadline() {
        var pending = new CompletableFuture<MemoryNamespaceReference>(); resolve.set(() -> pending);
        var result = service.run(handle, request()).toCompletableFuture(); runtime.runAll();
        runtime.advance(Duration.ofSeconds(31)); runtime.runAll(); assertEquals(AgentRunResult.Status.QUEUE_TIMED_OUT, result.join().status());
        pending.complete(initial); runtime.runAll(); assertTrue(backend.calls.isEmpty());
        assertTrue(service.snapshot(handle, scope).orElseThrow().find(AgentComponents.MEMORY_REFERENCE).isEmpty());
    }

    /** 验证观察父取消只结束重试等待，不取消原运行或关闭借用父控制。 */
    @Test void retryCancellationDoesNotCancelOriginalExecution() {
        var input = request(); var original = service.run(handle, input).toCompletableFuture(); runtime.runAll();
        try (var parent = ExecutionControl.create()) {
            var observer = service.retry(handle, input, parent).toCompletableFuture(); runtime.runAll(); parent.cancel(); runtime.runAll();
            assertEquals(AgentRunResult.Status.CANCELLED, observer.join().status()); assertFalse(original.isDone());
        }
        backend.calls.get(input.requestId()).complete(AgentRunResult.Status.SUCCESS); runtime.runAll(); assertEquals(AgentRunResult.Status.SUCCESS, original.join().status());
    }

    /** 验证注销在读引用之前拒绝，不调用存储解析或冷轮次。 */
    @Test void scopeDenialPrecedesPersistentReferenceAccess() {
        gate.set(() -> CompletableFuture.completedStage(new AgentAccessDecision(AgentAccessDecision.Reason.SCOPE_UNREGISTERED, Optional.empty(), Optional.empty())));
        var result = service.run(handle, request()).toCompletableFuture(); runtime.runAll();
        assertEquals(AgentRunResult.Status.SKIPPED, result.join().status()); verifyNoInteractions(references); assertTrue(backend.calls.isEmpty());
    }

    /** 验证自定义旧端口不支持固定引用时明确拒绝，不回退普通 prepare 执行。 */
    @Test void unsupportedPortCannotFallBackToUnprotectedExecution() {
        doThrow(new UnsupportedOperationException("测试端口尚未升级")).when(port).prepareReferenced(any(), any(), any());
        var result = service.run(handle, request()).toCompletableFuture(); runtime.runAll();
        assertEquals(AgentRunResult.Status.CONFIGURATION_ERROR, result.join().status()); verify(port, never()).prepare(any(), any()); assertTrue(backend.calls.isEmpty());
    }

    /** 验证默认入口覆盖外部的空间和 stamp 提示，使用当前存储解析值。 */
    @Test void externalHintsCannotReplaceAuthoritativeReference() {
        var input = request(); var hint = new MemoryNamespaceReference(scope, new MemoryNamespaceId("hint"), 5, 9);
        var stamped = new AgentRunRequest(input.requestId(), input.actor(), input.activity(), input.messages(),
                new AgentConversationStamp(new AgentConversationKey(handle.id(), scope), UUID.randomUUID()), hint);
        assertEquals(initial, success(handle, stamped).memoryReference());
    }

    /** 验证服务关闭结束正在等待的解析与重试，迟到完成不能再调用业务。 */
    @Test void serviceCloseCancelsPendingReferenceWork() {
        var pending = new CompletableFuture<MemoryNamespaceReference>(); resolve.set(() -> pending);
        var input = request(); var result = service.run(handle, input).toCompletableFuture(); runtime.runAll();
        var observer = service.retry(handle, input).toCompletableFuture(); runtime.runAll(); service.close(); runtime.runAll();
        assertEquals(AgentRunResult.Status.CANCELLED, result.join().status()); assertEquals(AgentRunResult.Status.CANCELLED, observer.join().status());
        pending.complete(initial); runtime.runAll(); assertTrue(backend.calls.isEmpty());
    }

    /** 验证同步通知失败不清旧状态，修复后由下一队首读取当前引用恢复。 */
    @Test void failedNotificationRecoversLazilyAtNextHead() {
        var old = success(handle, request()); current.set(new MemoryNamespaceReference(scope, new MemoryNamespaceId("after-failure"), 1, 0));
        resolve.set(() -> CompletableFuture.failedStage(new IllegalStateException("测试存储暂不可用")));
        try (var parent = ExecutionControl.create()) {
            var failed = service.synchronizeMemory(scope, parent).toCompletableFuture(); runtime.runAll(); assertThrows(CompletionException.class, failed::join);
        }
        assertEquals(old.conversation(), service.snapshot(handle, scope).orElseThrow().require(AgentConversationState.COMPONENT).stamp());
        resolve.set(() -> CompletableFuture.completedStage(current.get())); var next = request(); success(handle, next);
        assertEquals(1, backend.calls.get(next.requestId()).request.messages().size());
    }

    /** 验证等待只读重放校验期间内存代次改变，即使迟到允许也不返回原答案。 */
    @Test void invalidationDuringReplayVerificationRejectsLatePermission() {
        var input = request(); success(handle, input); var pending = new CompletableFuture<Void>(); verify.set(ignored -> pending);
        var result = service.retry(handle, input).toCompletableFuture(); runtime.runAll(); assertFalse(result.isDone());
        service.invalidate(handle, scope); pending.complete(null); runtime.runAll();
        assertEquals(AgentRunResult.Status.STALE_CONVERSATION, result.join().status()); assertTrue(result.join().answer().isEmpty());
        assertEquals(1, backend.started.size());
    }

    /** 验证同步期间取消正在进行的最终校验，保留已完成交互并让新代次取得运行槽。 */
    @Test void synchronizationCancelsPendingFinalVerificationWithoutLosingDiagnostics() {
        var pending = new CompletableFuture<Void>(); verify.set(ignored -> pending);
        var input = request(); var result = service.run(handle, input).toCompletableFuture(); runtime.runAll();
        backend.calls.get(input.requestId()).complete(AgentRunResult.Status.SUCCESS); runtime.runAll(); assertFalse(result.isDone());
        current.set(new MemoryNamespaceReference(scope, MemoryNamespaceId.SHARED, 0, 1)); assertEquals(1, synchronize()); runtime.runAll();
        assertEquals(AgentRunResult.Status.STALE_CONVERSATION, result.join().status()); assertTrue(result.join().interaction().isPresent());
        verify.set(this::initialVerification); success(handle, request()); pending.complete(null); runtime.runAll();
        assertEquals(1, service.snapshot(handle, scope).orElseThrow().require(AgentConversationWindow.COMPONENT).turns().size());
    }

    /** 验证同名 scope 在不同客户端下仍保持独立，同步不改变另一客户端的窗口和代次。 */
    @Test void synchronizationDoesNotTouchOtherClientScope() {
        success(handle, request()); var otherScope = new AgentScope("另一客户端", scope.scopeId());
        var otherReference = new MemoryNamespaceReference(otherScope, MemoryNamespaceId.SHARED, 0, 0);
        doReturn(CompletableFuture.completedStage(otherReference)).when(references).resolve(otherScope);
        verify.set(value -> value.scope().equals(scope) ? initialVerification(value) : CompletableFuture.completedStage(null));
        var original = request(); var actor = new AgentActorContext(otherScope, AgentActorContext.Origin.USER, original.actor().userId(), "用户", "消息", "目标");
        var other = new AgentRunRequest(UUID.randomUUID(), actor, AgentActivity.SERVICE, original.messages()); var prior = success(handle, other);
        current.set(new MemoryNamespaceReference(scope, MemoryNamespaceId.SHARED, 0, 1)); assertEquals(1, synchronize());
        var snapshot = service.snapshot(handle, otherScope).orElseThrow(); assertEquals(prior.conversation(), snapshot.require(AgentConversationState.COMPONENT).stamp());
        assertEquals(1, snapshot.require(AgentConversationWindow.COMPONENT).turns().size());
    }

    /** 验证错误适配器返回跨作用域引用时，在建立冷轮次或替换组件之前拒绝。 */
    @Test void foreignScopeObservationNeverBecomesConversationState() {
        resolve.set(() -> CompletableFuture.completedStage(new MemoryNamespaceReference(new AgentScope("另一客户端", "群"), MemoryNamespaceId.SHARED, 0, 0)));
        var result = service.run(handle, request()).toCompletableFuture(); runtime.runAll();
        assertEquals(AgentRunResult.Status.CONFIGURATION_ERROR, result.join().status()); assertTrue(backend.calls.isEmpty());
        assertTrue(service.snapshot(handle, scope).orElseThrow().find(AgentComponents.MEMORY_REFERENCE).isEmpty());
    }

    /** 验证自定义端口成功结果丢失固定引用时拒绝提交，不能以正常答案绕过引用合同。 */
    @Test void unreferencedSuccessFromCustomPortIsRejected() {
        var input = request(); var result = service.run(handle, input).toCompletableFuture(); runtime.runAll();
        var call = backend.calls.get(input.requestId());
        call.complete(AgentRunResult.Status.SUCCESS);
        // 为下一轮单独提供旧六参数成功结果，保留真实协议快照以检测引用合同。
        var raw = call.result.join(); var second = request(); var secondResult = service.run(handle, second).toCompletableFuture(); runtime.runAll();
        var secondCall = backend.calls.get(second.requestId());
        secondCall.result.complete(new AgentRunResult(handle.id(), second.requestId(), raw.status(), raw.access(), raw.interaction(), raw.budgetUsage()));
        runtime.runAll(); assertEquals(AgentRunResult.Status.SUCCESS, result.join().status());
        assertEquals(AgentRunResult.Status.CONFIGURATION_ERROR, secondResult.join().status()); assertTrue(secondResult.join().answer().isEmpty());
        assertEquals(1, service.snapshot(handle, scope).orElseThrow().require(AgentConversationWindow.COMPONENT).turns().size());
    }

    /**
     * 验证真实线程同时通知同一个 generation 时只有一次会话换代，其余观察保持幂等。
     * @throws Exception 当并发测试任务未能结束时
     */
    @Test void concurrentNotificationsAdvanceEpochOnlyOnce() throws Exception {
        try (var timer = Executors.newSingleThreadScheduledExecutor(); var workers = Executors.newVirtualThreadPerTaskExecutor();
             var concurrent = new AgentConversationService(backend, AgentQueueSettings::defaults, Runnable::run, timer, Clock.systemUTC(),
                     mock(ILogger.class), AgentConversationSettings::defaults, (agent, input) -> AgentConversationOptions.defaults(), new ObjectMapper(), references)) {
            backend.immediate.set(true); var input = request(); assertEquals(AgentRunResult.Status.SUCCESS, concurrent.run(handle, input).toCompletableFuture().join().status());
            var old = concurrent.snapshot(handle, scope).orElseThrow().require(AgentConversationState.COMPONENT).stamp();
            current.set(new MemoryNamespaceReference(scope, MemoryNamespaceId.SHARED, 0, 1));
            var start = new CountDownLatch(1); var jobs = new ArrayList<Future<Integer>>();
            for (int i = 0; i < 20; i++) {
                jobs.add(workers.submit(() -> {
                    start.await();
                    try (var parent = ExecutionControl.create()) { return concurrent.synchronizeMemory(scope, parent).toCompletableFuture().get(3, TimeUnit.SECONDS); }
                }));
            }
            start.countDown(); int changes = 0; for (var job : jobs) { changes += job.get(4, TimeUnit.SECONDS); }
            assertEquals(1, changes); assertNotEquals(old, concurrent.snapshot(handle, scope).orElseThrow().require(AgentConversationState.COMPONENT).stamp());
        }
    }
}
