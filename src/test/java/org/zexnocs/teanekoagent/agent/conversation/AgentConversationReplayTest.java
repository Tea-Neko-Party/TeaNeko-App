package org.zexnocs.teanekoagent.agent.conversation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.zexnocs.teanekoagent.agent.*;
import org.zexnocs.teanekoagent.agent.access.AgentAccessDecision;
import org.zexnocs.teanekoagent.llm.framework.interaction.InteractionRuntime;
import org.zexnocs.teanekoagent.llm.framework.interaction.LLMCallLimits;
import org.zexnocs.teanekoagent.llm.framework.interaction.LLMInteractionResult;
import org.zexnocs.teanekoagent.llm.framework.message.LLMUserMessage;
import org.zexnocs.teanekoagent.llm.framework.message.content.LLMContentListBuilder;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptionsSnapshot;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.logger.ILogger;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 验证显式重试仅观察或重放，覆盖缓存边界、身份冲突、门控及取消竞争。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
class AgentConversationReplayTest {
    private final InteractionRuntime runtime = new InteractionRuntime();
    private final AgentRegistry registry = new AgentRegistry();
    private final AgentHandle handle = new AgentFactory(registry).create(new AgentDefinition("重放测试", new LLMModelOptionsSnapshot(null), Set.of(AgentActivity.values())));
    private final AgentConversationTestBackend backend = new AgentConversationTestBackend();
    private final IAgentTurnService port = mock(IAgentTurnService.class);
    private final AtomicReference<AgentConversationOptions> options = new AtomicReference<>(AgentConversationOptions.defaults());
    private final AtomicReference<Supplier<CompletionStage<AgentAccessDecision>>> gate = new AtomicReference<>(
            () -> CompletableFuture.completedStage(AgentConversationTestCall.allowedAccess()));
    private final ILogger logger = mock(ILogger.class);
    private final AgentConversationService service = new AgentConversationService(port, AgentQueueSettings::defaults,
            runtime, runtime.scheduler, runtime.clock, logger, AgentConversationSettings::defaults, (agent, request) -> options.get(), new ObjectMapper());

    /** 为测试端口接入真实冷句柄，只有重放门控可由用例延迟或拒绝。 */
    AgentConversationReplayTest() {
        when(port.prepare(any(), any())).thenAnswer(call -> backend.prepare(call.getArgument(0), call.getArgument(1)));
        when(port.prepare(any(), any(), any())).thenAnswer(call -> backend.prepare(call.getArgument(0), call.getArgument(1), call.getArgument(2)));
        when(port.checkAccess(any(), any())).thenAnswer(call -> gate.get().get());
    }

    /** 关闭拥有的请求和实例，观察者不得遗留计时器。 */
    @AfterEach void cleanup() {
        backend.autoAck.set(true); service.close(); backend.close(); registry.close(); runtime.runAll();
        assertEquals(0, runtime.pendingTimers());
    }

    /**
     * 创建当前群的一次新增输入。
     * @param text 消息正文
     * @return 带真实 UUID 的原始请求
     */
    private AgentRunRequest request(String text) {
        var actor = new AgentActorContext(new AgentScope("客户端", "群"), AgentActorContext.Origin.USER,
                Optional.of(UUID.randomUUID()), "平台用户", UUID.randomUUID().toString(), "目标");
        return new AgentRunRequest(UUID.randomUUID(), actor, AgentActivity.SERVICE,
                List.of(LLMUserMessage.builder().contents(new LLMContentListBuilder().addText(text).build()).build()));
    }

    /**
     * 完成已开始轮次并推进后继。
     * @param request 原始请求
     * @param status 实际完成状态
     */
    private void complete(AgentRunRequest request, AgentRunResult.Status status) {
        backend.calls.get(request.requestId()).complete(status); runtime.runAll();
    }

    /**
     * 推进观察的业务回调后有界等待，不再假定已缓存结果必定同步完成。
     * @param stage 只读重放阶段
     * @return 已完成结果
     * @throws AssertionError 当回调未在测试时限内完成时
     */
    private AgentRunResult await(CompletionStage<AgentRunResult> stage) {
        var future = stage.toCompletableFuture(); long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!future.isDone()) {
            runtime.runAll();
            if (System.nanoTime() >= deadline) { throw new AssertionError("重放观察未在测试时限内完成。"); }
            Thread.onSpinWait();
        }
        return future.join();
    }

    /**
     * 读取当前完成记录。
     * @param request 同作用域请求
     * @return 当前代次记录
     */
    private AgentRequestJournal journal(AgentRunRequest request) {
        return service.snapshot(handle, request.actor().scope()).orElseThrow().require(AgentRequestJournal.COMPONENT);
    }

    /** 验证在途重试等待原执行，完成后复用同一个结果，不再次建立轮次。 */
    @Test void inFlightAndCompletedRetryReuseOriginalExecution() {
        var a = request("原始输入"); var original = service.run(handle, a).toCompletableFuture(); runtime.runAll();
        var observer = service.retry(handle, a).toCompletableFuture(); assertFalse(observer.isDone());
        complete(a, AgentRunResult.Status.SUCCESS);
        assertEquals(service.snapshot(handle, a.actor().scope()).orElseThrow().require(AgentConversationState.COMPONENT).stamp(), await(original).conversation());
        assertSame(await(original), await(observer)); assertSame(await(original), await(service.retry(handle, a)));
        assertEquals(List.of(a.requestId()), backend.started); verify(port, times(1)).prepare(any(), any());
    }

    /** 验证等待项的重试只观察原队列位置，不额外入队或提前执行。 */
    @Test void queuedRetryObservesOriginalFifoPosition() {
        var a = request("第一条"); var b = request("第二条");
        service.run(handle, a); var original = service.run(handle, b).toCompletableFuture(); runtime.runAll();
        var observer = service.retry(handle, b).toCompletableFuture(); assertFalse(observer.isDone()); assertEquals(1, backend.calls.size());
        complete(a, AgentRunResult.Status.SUCCESS); assertFalse(observer.isDone());
        complete(b, AgentRunResult.Status.SUCCESS); assertSame(await(original), await(observer));
        assertEquals(List.of(a.requestId(), b.requestId()), backend.started);
    }

    /** 验证缺失、过期和失效后的原 ID 都不会因重试再次执行。 */
    @Test void missingExpiredAndInvalidatedRecordsNeverStartNewWork() {
        var a = request("输入");
        assertEquals(AgentRunResult.Status.REPLAY_UNAVAILABLE, await(service.retry(handle, a)).status());
        var original = service.run(handle, a).toCompletableFuture(); runtime.runAll(); complete(a, AgentRunResult.Status.SUCCESS);
        var oldStamp = await(original).conversation(); assertNotNull(oldStamp);
        service.invalidate(handle, a.actor().scope());
        assertEquals(oldStamp, await(original).conversation()); assertEquals(AgentRunResult.Status.SUCCESS, await(original).status());
        assertNotEquals(oldStamp, service.snapshot(handle, a.actor().scope()).orElseThrow().require(AgentConversationState.COMPONENT).stamp());
        assertEquals(AgentRunResult.Status.REPLAY_UNAVAILABLE, await(service.retry(handle, a)).status());
        var b = request("新代次"); service.run(handle, b); runtime.runAll(); complete(b, AgentRunResult.Status.SUCCESS);
        runtime.advance(Duration.ofMinutes(30));
        assertEquals(AgentRunResult.Status.REPLAY_UNAVAILABLE, await(service.retry(handle, b)).status());
        assertEquals(2, backend.started.size());
    }

    /** 验证相同 ID 不跨实例、客户端或 scope 复用结果。 */
    @Test void replayDoesNotCrossInstanceClientOrScope() {
        var a = request("输入"); service.run(handle, a); runtime.runAll(); complete(a, AgentRunResult.Status.SUCCESS);
        var another = new AgentFactory(registry).create(handle.definition());
        assertEquals(AgentRunResult.Status.REPLAY_UNAVAILABLE, await(service.retry(another, a)).status());
        for (var scope : List.of(new AgentScope("另一客户端", "群"), new AgentScope("客户端", "另一群"))) {
            var actor = new AgentActorContext(scope, a.actor().origin(), a.actor().userId(), a.actor().platformUserId(), a.actor().sourceId(), a.actor().targetId());
            var foreign = new AgentRunRequest(a.requestId(), actor, a.activity(), a.messages());
            assertEquals(AgentRunResult.Status.REPLAY_UNAVAILABLE, await(service.retry(handle, foreign)).status());
        }
        assertEquals(1, backend.started.size());
    }

    /** 验证同 ID 的操作者、输入或活动变化均拒绝，不交付原回答。 */
    @Test void identityAndInputConflictNeverRevealCachedAnswer() {
        var a = request("原文"); service.run(handle, a); runtime.runAll(); complete(a, AgentRunResult.Status.SUCCESS);
        var other = request("原文");
        var changedActor = new AgentRunRequest(a.requestId(), other.actor(), a.activity(), a.messages());
        var changedInput = new AgentRunRequest(a.requestId(), a.actor(), a.activity(), request("不同正文").messages());
        var changedActivity = new AgentRunRequest(a.requestId(), a.actor(), AgentActivity.COMMAND, a.messages());
        for (var conflict : List.of(changedActor, changedInput, changedActivity)) {
            var result = await(service.retry(handle, conflict));
            assertEquals(AgentRunResult.Status.REQUEST_CONFLICT, result.status()); assertTrue(result.answer().isEmpty());
            assertEquals(AgentRunResult.Status.REQUEST_CONFLICT, service.run(handle, conflict).toCompletableFuture().join().status());
        }
        assertEquals(1, backend.started.size());
    }

    /** 验证关闭正文缓存仍保留完成状态，run 和 retry 都不会把已知 ID 当新动作执行。 */
    @Test void zeroReplayBudgetKeepsStatusWithoutReexecutingKnownId() {
        options.set(new AgentConversationOptions(40, 262144, 32, 0));
        var a = request("输入"); var original = service.run(handle, a).toCompletableFuture(); runtime.runAll(); complete(a, AgentRunResult.Status.SUCCESS);
        assertEquals(AgentRunResult.Status.SUCCESS, await(original).status());
        var record = journal(a).find(a.requestId()).orElseThrow(); assertNull(record.result()); assertEquals(AgentRunResult.Status.SUCCESS, record.status());
        assertEquals(AgentRunResult.Status.REPLAY_UNAVAILABLE, await(service.retry(handle, a)).status());
        assertEquals(AgentRunResult.Status.REPLAY_UNAVAILABLE, service.run(handle, a).toCompletableFuture().join().status());
        assertEquals(1, backend.started.size());
    }

    /** 验证完成记录数量淘汰后，显式重试返回缺失而不会重新调用端口。 */
    @Test void recordCapacityEvictionDoesNotTurnRetryIntoRun() {
        options.set(new AgentConversationOptions(40, 262144, 1, 1048576));
        var a = request("第一条"); var b = request("第二条");
        service.run(handle, a); runtime.runAll(); complete(a, AgentRunResult.Status.SUCCESS);
        var latest = service.run(handle, b).toCompletableFuture(); runtime.runAll(); complete(b, AgentRunResult.Status.SUCCESS);
        assertEquals(1, journal(b).entries().size());
        assertEquals(AgentRunResult.Status.REPLAY_UNAVAILABLE, await(service.retry(handle, a)).status());
        assertSame(await(latest), await(service.retry(handle, b))); assertEquals(2, backend.started.size());
    }

    /** 验证非零字节预算优先保留最新结果正文，旧状态继续保留。 */
    @Test void resultByteBudgetEvictsBodiesBeforeStatusRecords() {
        var a = request("第一条"); var b = request("第二条"); var c = request("第三条");
        service.run(handle, a); runtime.runAll(); complete(a, AgentRunResult.Status.SUCCESS);
        service.run(handle, b); runtime.runAll(); complete(b, AgentRunResult.Status.SUCCESS);
        long middleBytes = journal(b).find(b.requestId()).orElseThrow().bytes();
        options.set(new AgentConversationOptions(40, 262144, 32, 2 * middleBytes - 1));
        service.run(handle, c); runtime.runAll(); complete(c, AgentRunResult.Status.SUCCESS);
        assertEquals(3, journal(c).entries().size());
        assertNotNull(journal(c).find(c.requestId()).orElseThrow().result());
        assertNull(journal(c).find(b.requestId()).orElseThrow().result()); assertNull(journal(c).find(a.requestId()).orElseThrow().result());
        assertEquals(AgentRunResult.Status.REPLAY_UNAVAILABLE, await(service.retry(handle, a)).status());
    }

    /** 验证失败或取消可作为诊断重放，仍不会进入正常历史。 */
    @Test void cancelledResultIsReplayedAsDiagnosticWithoutHistory() {
        var a = request("输入"); var original = service.run(handle, a).toCompletableFuture(); runtime.runAll(); complete(a, AgentRunResult.Status.CANCELLED);
        assertSame(await(original), await(service.retry(handle, a)));
        assertEquals(AgentRunResult.Status.CANCELLED, journal(a).find(a.requestId()).orElseThrow().status());
        assertTrue(service.snapshot(handle, a.actor().scope()).orElseThrow().require(AgentConversationWindow.COMPONENT).turns().isEmpty());
    }

    /** 验证观察父取消只结束观察，不取消原执行或清空其未来结果。 */
    @Test void cancellingObserverParentDoesNotCancelOriginal() {
        var a = request("输入"); var original = service.run(handle, a).toCompletableFuture(); runtime.runAll();
        try (var parent = runtime.parent(LLMCallLimits.defaults())) {
            var observer = service.retry(handle, a, parent).toCompletableFuture(); parent.cancel();
            assertEquals(AgentRunResult.Status.CANCELLED, await(observer).status()); assertFalse(original.isDone());
            assertDoesNotThrow(backend.calls.get(a.requestId()).control::checkActive);
            complete(a, AgentRunResult.Status.SUCCESS); assertEquals(AgentRunResult.Status.SUCCESS, await(original).status());
        }
    }

    /** 验证观察父到期保留超时原因，原独立请求仍可正常完成。 */
    @Test void observerDeadlineDoesNotReplaceOriginalDeadlineOrResult() {
        var a = request("输入"); var original = service.run(handle, a).toCompletableFuture(); runtime.runAll();
        try (var parent = ExecutionControl.withDeadline(LLMCallLimits.defaults().newBudget(), runtime.clock.instant().plusSeconds(2), runtime.clock, runtime.scheduler)) {
            var observer = service.retry(handle, a, parent).toCompletableFuture(); runtime.advance(Duration.ofSeconds(3));
            assertEquals(AgentRunResult.Status.TIMED_OUT, await(observer).status()); assertFalse(original.isDone());
            complete(a, AgentRunResult.Status.SUCCESS); assertEquals(AgentRunResult.Status.SUCCESS, await(original).status());
        }
    }

    /** 验证工具后代不能观察自己占有的在途邮箱，从而形成递归等待。 */
    @Test void recursiveObservationIsRejected() {
        var a = request("输入"); service.run(handle, a); runtime.runAll();
        try (var child = backend.calls.get(a.requestId()).control.child()) {
            assertEquals(AgentRunResult.Status.REENTRANT, await(service.retry(handle, a, child)).status());
        }
        complete(a, AgentRunResult.Status.SUCCESS);
    }

    /** 验证自定义执行链查询抛错时仍返回已完成失败阶段，不影响原请求。 */
    @Test void failingOwnershipQueryCompletesReplayWithoutCancellingOriginal() {
        doAnswer(call -> {
            var original = backend.prepare(call.getArgument(0), call.getArgument(1));
            return new AgentRunExecution(original.result(), original::start, original::cancel,
                    candidate -> { throw new IllegalStateException("替身链路查询失败"); });
        }).when(port).prepare(any(), any());
        var a = request("输入"); var original = service.run(handle, a).toCompletableFuture(); runtime.runAll();
        try (var parent = ExecutionControl.create()) {
            assertEquals(AgentRunResult.Status.FAILED, await(service.retry(handle, a, parent)).status());
            assertFalse(original.isDone()); complete(a, AgentRunResult.Status.SUCCESS); assertEquals(AgentRunResult.Status.SUCCESS, await(original).status());
        }
    }

    /** 验证错误冷端口的成功声明不能绕过完整输入前缀校验，失败诊断不进入窗口。 */
    @Test void malformedSuccessfulTranscriptIsReportedAsInvalidResponse() {
        doAnswer(call -> {
            var original = backend.prepare(call.getArgument(0), call.getArgument(1));
            var changed = original.result().thenApply(raw -> {
                var candidate = raw.interaction().orElseThrow().finalCandidate().orElseThrow();
                var invalid = new LLMInteractionResult(LLMInteractionResult.Status.SUCCESS, List.of(candidate), candidate, List.of(), List.of(), Map.of());
                return new AgentRunResult(raw.agentId(), raw.requestId(), raw.status(), raw.access().orElse(null), invalid, raw.budgetUsage());
            });
            return new AgentRunExecution(changed, original::start, original::cancel, original::contains);
        }).when(port).prepare(any(), any());
        var a = request("输入"); var result = service.run(handle, a).toCompletableFuture(); runtime.runAll(); complete(a, AgentRunResult.Status.SUCCESS);
        assertEquals(AgentRunResult.Status.INVALID_RESPONSE, await(result).status()); assertTrue(await(result).answer().isEmpty());
        assertTrue(service.snapshot(handle, a.actor().scope()).orElseThrow().require(AgentConversationWindow.COMPONENT).turns().isEmpty());
        assertEquals(AgentRunResult.Status.INVALID_RESPONSE, journal(a).find(a.requestId()).orElseThrow().status());
    }

    /** 验证自定义端口返回其他代次时拒绝其成功声明，结果绑定仍由邮箱固定。 */
    @Test void foreignEpochResultCannotEnterWindowOrReplayAsSuccess() {
        doAnswer(call -> {
            var prepared = (AgentRunRequest) call.getArgument(1);
            var original = backend.prepare(call.getArgument(0), prepared);
            var foreign = new AgentConversationStamp(new AgentConversationKey(handle.id(), prepared.actor().scope()), UUID.randomUUID());
            var changed = original.result().thenApply(raw -> new AgentRunResult(raw.agentId(), raw.requestId(), raw.status(),
                    raw.access().orElse(null), raw.interaction().orElse(null), raw.budgetUsage(), foreign));
            return new AgentRunExecution(changed, original::start, original::cancel, original::contains);
        }).when(port).prepare(any(), any());
        var a = request("输入"); var result = service.run(handle, a).toCompletableFuture(); runtime.runAll(); complete(a, AgentRunResult.Status.SUCCESS);
        assertEquals(AgentRunResult.Status.FAILED, await(result).status()); assertTrue(await(result).answer().isEmpty());
        assertEquals(service.snapshot(handle, a.actor().scope()).orElseThrow().require(AgentConversationState.COMPONENT).stamp(), await(result).conversation());
        assertTrue(service.snapshot(handle, a.actor().scope()).orElseThrow().require(AgentConversationWindow.COMPONENT).turns().isEmpty());
        assertSame(await(result), await(service.retry(handle, a)));
    }

    /** 验证缓存回答仍复核当前准入，拒绝与端口故障都不公开原回答。 */
    @Test void replayRechecksCurrentGateAndFailsClosed() {
        var a = request("输入"); var original = service.run(handle, a).toCompletableFuture(); runtime.runAll(); complete(a, AgentRunResult.Status.SUCCESS);
        gate.set(() -> CompletableFuture.completedStage(new AgentAccessDecision(AgentAccessDecision.Reason.SCOPE_DISABLED, Optional.empty(), Optional.empty())));
        var denied = await(service.retry(handle, a));
        assertEquals(AgentRunResult.Status.SKIPPED, denied.status()); assertTrue(denied.answer().isEmpty()); assertTrue(denied.interaction().isPresent());
        gate.set(() -> CompletableFuture.failedStage(new IllegalStateException("敏感内部原因")));
        assertEquals(AgentRunResult.Status.CONFIGURATION_ERROR, await(service.retry(handle, a)).status());
        gate.set(() -> CompletableFuture.completedStage(AgentConversationTestCall.allowedAccess()));
        assertSame(await(original), await(service.retry(handle, a))); assertEquals(1, backend.started.size());
    }

    /** 验证旧轮次端口不支持重放准入时明确拒绝，不回退为重新执行。 */
    @Test void unsupportedReplayGateNeverFallsBackToAnotherExecution() {
        var a = request("输入"); service.run(handle, a); runtime.runAll(); complete(a, AgentRunResult.Status.SUCCESS);
        when(port.checkAccess(any(), any())).thenThrow(new UnsupportedOperationException("旧端口未适配"));
        var rejected = await(service.retry(handle, a));
        assertEquals(AgentRunResult.Status.CONFIGURATION_ERROR, rejected.status()); assertTrue(rejected.answer().isEmpty()); assertEquals(1, backend.started.size());
    }

    /** 验证准入异步复核期间失效，迟到允许结果不能公开旧代次回答。 */
    @Test void epochIsCheckedAgainAfterDelayedReplayGate() {
        var a = request("输入"); service.run(handle, a); runtime.runAll(); complete(a, AgentRunResult.Status.SUCCESS);
        var pending = new CompletableFuture<AgentAccessDecision>(); gate.set(() -> pending);
        var replay = service.retry(handle, a).toCompletableFuture(); assertFalse(replay.isDone());
        service.invalidate(handle, a.actor().scope()); pending.complete(AgentConversationTestCall.allowedAccess());
        assertEquals(AgentRunResult.Status.STALE_CONVERSATION, await(replay).status()); assertTrue(await(replay).answer().isEmpty());
        assertEquals(1, backend.started.size());
    }

    /** 验证结果重放不重复扣除已有宿主总预算。 */
    @Test void completedReplayDoesNotChargeParentAgain() {
        backend.charge.set(true);
        try (var parent = runtime.parent(new LLMCallLimits(1, 0, 0))) {
            var a = request("输入"); var original = service.run(handle, a, parent).toCompletableFuture(); runtime.runAll(); complete(a, AgentRunResult.Status.SUCCESS);
            assertSame(await(original), await(service.retry(handle, a, parent)));
            assertEquals(1, parent.budget().used(LLMCallLimits.MODEL_REQUESTS)); assertEquals(1, backend.started.size());
        }
    }

    /**
     * 验证重放门控在邮箱锁外，可从另一线程读取当前快照。
     */
    @Test void replayGateRunsOutsideMailboxLock() {
        var a = request("输入"); service.run(handle, a); runtime.runAll(); complete(a, AgentRunResult.Status.SUCCESS);
        gate.set(() -> CompletableFuture.supplyAsync(() -> {
            assertTrue(service.snapshot(handle, a.actor().scope()).isPresent()); return AgentConversationTestCall.allowedAccess();
        }));
        assertEquals(AgentRunResult.Status.SUCCESS, await(service.retry(handle, a)).status());
    }

    /**
     * 验证真实线程并发重放仍复用单一完成结果，不产生额外业务调用。
     * @throws Exception 当并发观察超过测试时限时
     */
    @Test void concurrentReplaysReuseOneResult() throws Exception {
        backend.immediate.set(true); backend.charge.set(true);
        try (var workers = Executors.newVirtualThreadPerTaskExecutor(); var timers = Executors.newSingleThreadScheduledExecutor();
             var queued = new AgentConversationService(port, AgentQueueSettings::defaults, workers, timers, Clock.systemUTC(), logger)) {
            var a = request("输入"); var original = queued.run(handle, a).toCompletableFuture().get(5, TimeUnit.SECONDS);
            var observers = new ArrayList<CompletableFuture<AgentRunResult>>();
            for (int i = 0; i < 40; i++) { observers.add(CompletableFuture.supplyAsync(() -> queued.retry(handle, a), workers).thenCompose(stage -> stage)); }
            for (var observer : observers) { assertSame(original, observer.get(5, TimeUnit.SECONDS)); }
            assertEquals(1, backend.started.size());
        }
    }
}
