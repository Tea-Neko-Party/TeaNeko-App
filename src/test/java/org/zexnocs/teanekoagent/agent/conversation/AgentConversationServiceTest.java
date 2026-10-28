package org.zexnocs.teanekoagent.agent.conversation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.zexnocs.teanekoagent.agent.*;
import org.zexnocs.teanekoagent.agent.access.AgentAccessDecision;
import org.zexnocs.teanekoagent.agent.access.AgentReadiness;
import org.zexnocs.teanekoagent.agent.access.AgentRuntimeSnapshot;
import org.zexnocs.teanekoagent.agent.access.AgentScopePolicy;
import org.zexnocs.teanekoagent.llm.framework.interaction.InteractionRuntime;
import org.zexnocs.teanekoagent.llm.framework.interaction.LLMCallLimits;
import org.zexnocs.teanekoagent.llm.framework.interaction.LLMInteractionResult;
import org.zexnocs.teanekoagent.llm.framework.interaction.LLMInteractionSettings;
import org.zexnocs.teanekoagent.llm.framework.message.LLMAssistantMessage;
import org.zexnocs.teanekoagent.llm.framework.message.LLMUserMessage;
import org.zexnocs.teanekoagent.llm.framework.message.content.LLMContentListBuilder;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptions;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptionsSnapshot;
import org.zexnocs.teanekoagent.llm.framework.model.ModelCapabilities;
import org.zexnocs.teanekocore.actuator.execution.BudgetExceededException;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.actuator.execution.ExecutionStoppedException;
import org.zexnocs.teanekocore.logger.ILogger;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 使用可控冷轮次验证邮箱状态仲裁、共享身份、排队时限与锁外资源操作。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
class AgentConversationServiceTest {
    private final InteractionRuntime runtime = new InteractionRuntime();
    private final AgentRegistry registry = new AgentRegistry();
    private final AgentHandle handle = new AgentFactory(registry).create(new AgentDefinition("共享定义",
            new LLMModelOptionsSnapshot(null), Set.of(AgentActivity.values())));
    private final AtomicReference<AgentQueueSettings> settings = new AtomicReference<>(AgentQueueSettings.defaults());
    private final AtomicReference<AgentConversationSettings> retention = new AtomicReference<>(AgentConversationSettings.defaults());
    private final ILogger logger = mock(ILogger.class);
    private final Backend backend = new Backend();
    private final AgentConversationService service = new AgentConversationService(backend, settings::get,
            runtime, runtime.scheduler, runtime.clock, logger, retention::get);

    /** 关闭拥有的服务和实例，运行排队回调后不得遗留计时器或关闭借用资源。 */
    @AfterEach void cleanup() {
        backend.autoAck.set(true); service.close(); backend.close(); registry.close(); runtime.runAll();
        assertEquals(0, runtime.pendingTimers());
        verify(runtime.scheduler, never()).shutdown(); verify(runtime.scheduler, never()).shutdownNow();
    }

    /**
     * 构造具有明确 UUID 的原始请求，同 scope 的不同用户仍应进入同一邮箱。
     * @param client 来源客户端
     * @param scope 来源作用域
     * @return 固定宿主请求
     */
    private AgentRunRequest request(String client, String scope) {
        var actor = new AgentActorContext(new AgentScope(client, scope), AgentActorContext.Origin.USER,
                Optional.of(UUID.randomUUID()), "平台用户", UUID.randomUUID().toString(), "目标");
        return new AgentRunRequest(UUID.randomUUID(), actor, AgentActivity.SERVICE,
                List.of(LLMUserMessage.builder().contents(new LLMContentListBuilder().addText("请求正文").build()).build()));
    }

    /**
     * 提供当前群的不同用户请求。
     * @return 测试请求
     */
    private AgentRunRequest request() { return request("客户端", "群"); }

    /**
     * 以宿主身份请求取消，不构造任何额外管理权限。
     * @param request 原请求
     * @return 取消接收结果
     */
    private IAgentService.CancelResult cancel(AgentRunRequest request) {
        return service.cancel(handle, request.actor().scope(), request.requestId(), request.actor().userId().orElseThrow());
    }

    /**
     * 完成指定请求的实际业务，再推进邮箱调度。
     * @param request 原请求
     */
    private void complete(AgentRunRequest request) { backend.calls.get(request.requestId()).complete(AgentRunResult.Status.SUCCESS); runtime.runAll(); }

    /**
     * 读取有效会话的不可变生命周期组件。
     * @param request 对应作用域的请求
     * @return 当前会话状态
     */
    private AgentConversationState state(AgentRunRequest request) {
        return service.snapshot(handle, request.actor().scope()).orElseThrow().require(AgentConversationState.COMPONENT);
    }

    /** 验证默认有效期从实际空闲开始，观察快照不续期，旧快照保持不可变。 */
    @Test void idleTtlStartsAtCompletionAndSnapshotDoesNotRefreshIt() {
        var a = request(); service.run(handle, a); runtime.runAll();
        var original = service.snapshot(handle, a.actor().scope()).orElseThrow();
        runtime.advance(Duration.ofMinutes(40));
        assertTrue(service.snapshot(handle, a.actor().scope()).isPresent());
        complete(a); var idle = state(a);
        assertEquals(runtime.clock.instant(), idle.lastActivity());
        assertTrue(idle.lastActivity().isAfter(original.require(AgentConversationState.COMPONENT).lastActivity()));
        runtime.advance(Duration.ofMinutes(29)); assertTrue(service.snapshot(handle, a.actor().scope()).isPresent());
        runtime.advance(Duration.ofMinutes(1)); assertTrue(service.snapshot(handle, a.actor().scope()).isEmpty());
        assertEquals(0, runtime.pendingTimers());
    }

    /** 验证 TTL 达到边界后重新入队建立全新代次和实体身份。 */
    @Test void expiredConversationIsRecreatedWithDifferentEpochAndEntity() {
        var a = request(); service.run(handle, a); runtime.runAll(); complete(a);
        var old = service.snapshot(handle, a.actor().scope()).orElseThrow();
        runtime.advance(Duration.ofMinutes(30));
        var b = request(); service.run(handle, b); runtime.runAll();
        var current = service.snapshot(handle, b.actor().scope()).orElseThrow();
        assertNotEquals(old.id(), current.id());
        assertNotEquals(old.require(AgentConversationState.COMPONENT).stamp(), state(b).stamp()); complete(b);
    }

    /** 验证容量包含活跃项，满且无空闲候选时明确拒绝，新请求不建立冷轮次。 */
    @Test void fullActiveCapacityRejectsAnotherConversationAndKeepsOriginalQueue() {
        retention.set(new AgentConversationSettings(1, Duration.ofSeconds(1)));
        var a = request(); var b = request(); var other = request("客户端", "另一群");
        service.run(handle, a); runtime.runAll(); runtime.advance(Duration.ofSeconds(2));
        var waiting = service.run(handle, b).toCompletableFuture();
        var rejected = service.run(handle, other).toCompletableFuture();
        assertEquals(AgentRunResult.Status.BUSY, rejected.join().status());
        assertFalse(backend.calls.containsKey(other.requestId())); assertEquals(0, service.purgeIdle());
        complete(a); assertFalse(waiting.isDone()); complete(b); assertEquals(AgentRunResult.Status.SUCCESS, waiting.join().status());
    }

    /** 验证容量只淘汰最近最少活动的空闲项，不把活跃项按旧活动时刻移除。 */
    @Test void capacityEvictsLeastRecentlyActiveIdleConversation() {
        retention.set(new AgentConversationSettings(2, Duration.ofHours(1)));
        var a = request(); service.run(handle, a); runtime.runAll(); complete(a);
        runtime.advance(Duration.ofSeconds(1));
        var b = request("客户端", "第二群"); service.run(handle, b); runtime.runAll(); complete(b);
        runtime.advance(Duration.ofSeconds(1));
        var c = request("客户端", "第三群"); service.run(handle, c); runtime.runAll();
        assertTrue(service.snapshot(handle, a.actor().scope()).isEmpty());
        assertTrue(service.snapshot(handle, b.actor().scope()).isPresent());
        assertTrue(service.snapshot(handle, c.actor().scope()).isPresent()); complete(c);
    }

    /** 验证热更新缩小容量时保留活跃项，释放后显式清理可收缩缓存。 */
    @Test void reducedCapacityNeverEvictsActiveMailboxes() {
        var a = request(); var b = request("客户端", "第二群");
        service.run(handle, a); service.run(handle, b); runtime.runAll();
        retention.set(new AgentConversationSettings(1, Duration.ofMinutes(30)));
        assertEquals(0, service.purgeIdle());
        assertEquals(AgentRunResult.Status.BUSY, service.run(handle, request("客户端", "第三群")).toCompletableFuture().join().status());
        complete(a); assertEquals(1, service.purgeIdle());
        assertTrue(service.snapshot(handle, a.actor().scope()).isEmpty()); assertTrue(service.snapshot(handle, b.actor().scope()).isPresent()); complete(b);
    }

    /** 验证新策略在后续操作生效，已有不可变快照不会被原地修改。 */
    @Test void latestRetentionCanExpirePreviouslyIdleConversation() {
        var a = request(); service.run(handle, a); runtime.runAll(); complete(a);
        var old = state(a); runtime.advance(Duration.ofSeconds(3));
        retention.set(new AgentConversationSettings(1000, Duration.ofSeconds(2)));
        assertEquals(1, service.purgeIdle()); assertTrue(service.snapshot(handle, a.actor().scope()).isEmpty());
        assertEquals(old.stamp().key(), new AgentConversationKey(handle.id(), a.actor().scope()));
    }

    /** 验证时钟回拨不会立即过期，活动时刻也不会向过去移动。 */
    @Test void clockRollbackDoesNotExpireOrRewindConversation() {
        var a = request(); service.run(handle, a); runtime.runAll(); complete(a); var previous = state(a);
        runtime.advance(Duration.ofMinutes(-10)); assertEquals(0, service.purgeIdle());
        var b = request(); service.run(handle, b); runtime.runAll(); complete(b);
        assertEquals(previous.lastActivity(), state(b).lastActivity()); assertEquals(previous.stamp(), state(b).stamp());
    }

    /** 验证空闲实例关闭会立即解除所有会话拥有关系，不影响另一个实例。 */
    @Test void closingIdleInstanceRemovesItsCachedConversationsOnly() {
        var another = new AgentFactory(registry).create(handle.definition());
        var a = request(); var b = request("客户端", "第二群");
        service.run(handle, a); service.run(handle, b); service.run(another, request()); runtime.runAll();
        complete(a); complete(b);
        handle.close(); assertTrue(service.snapshot(handle, a.actor().scope()).isEmpty()); assertTrue(service.snapshot(handle, b.actor().scope()).isEmpty());
        assertTrue(service.snapshot(another, a.actor().scope()).isPresent()); assertEquals(0, service.purgeIdle());
    }

    /** 验证空闲失效只推进目标代次，实体 revision 有序增长且不重建运行拥有者。 */
    @Test void invalidatingIdleConversationChangesOnlyItsEpoch() {
        var a = request(); service.run(handle, a); runtime.runAll(); complete(a);
        var before = service.snapshot(handle, a.actor().scope()).orElseThrow();
        var next = service.invalidate(handle, a.actor().scope()).orElseThrow();
        var after = service.snapshot(handle, a.actor().scope()).orElseThrow();
        assertEquals(before.id(), after.id()); assertTrue(after.revision() > before.revision());
        assertNotEquals(before.require(AgentConversationState.COMPONENT).stamp(), next);
        assertEquals(next, after.require(AgentConversationState.COMPONENT).stamp());
    }

    /** 验证不存在、过期和已关闭会话的管理操作不隐式创建状态。 */
    @Test void inspectionAndInvalidationDoNotCreateMissingConversation() {
        var a = request(); assertTrue(service.snapshot(handle, a.actor().scope()).isEmpty());
        assertTrue(service.invalidate(handle, a.actor().scope()).isEmpty());
        service.run(handle, a); runtime.runAll(); complete(a); runtime.advance(Duration.ofMinutes(30));
        assertTrue(service.invalidate(handle, a.actor().scope()).isEmpty());
        service.close(); assertEquals(0, service.purgeIdle()); assertTrue(service.snapshot(handle, a.actor().scope()).isEmpty());
        assertTrue(service.invalidate(handle, a.actor().scope()).isEmpty());
    }

    /** 验证失效直接结束所有旧等待项，旧运行确认前不启动新代次，迟到成功只保留诊断。 */
    @Test void invalidationWaitsForRunningAcknowledgementBeforeNewEpochStarts() {
        backend.autoAck.set(false);
        var a = request(); var b = request(); var c = request();
        var first = service.run(handle, a).toCompletableFuture(); service.run(handle, b); var third = service.run(handle, c).toCompletableFuture(); runtime.runAll();
        var old = state(a).stamp(); var next = service.invalidate(handle, a.actor().scope()).orElseThrow();
        assertEquals(AgentRunResult.Status.STALE_CONVERSATION, third.join().status()); assertFalse(first.isDone());
        var d = request(); var newTurn = service.run(handle, d).toCompletableFuture(); runtime.runAll();
        assertEquals(List.of(a.requestId()), backend.started); assertNotEquals(old, next);
        assertThrows(ExecutionStoppedException.class, backend.calls.get(a.requestId()).control::checkActive);
        complete(a); var stale = first.join(); assertEquals(AgentRunResult.Status.STALE_CONVERSATION, stale.status());
        assertTrue(stale.answer().isEmpty()); assertTrue(stale.committableTranscript().isEmpty()); assertTrue(stale.interaction().isPresent());
        assertEquals(next, backend.calls.get(d.requestId()).request.conversation());
        assertEquals(List.of(a.requestId(), d.requestId()), backend.started); complete(d); assertEquals(AgentRunResult.Status.SUCCESS, newTurn.join().status());
        assertEquals(next, state(d).stamp());
    }

    /** 验证冷准备期间失效不会启动旧业务，迟到返回的冷句柄同样被取消。 */
    @Test void invalidationDuringColdPreparationCancelsLateHandle() {
        var a = request(); var b = request(); var first = service.run(handle, a).toCompletableFuture();
        var second = service.run(handle, b).toCompletableFuture();
        backend.beforePrepare.set(() -> service.invalidate(handle, a.actor().scope())); runtime.runAll();
        assertEquals(AgentRunResult.Status.STALE_CONVERSATION, first.join().status());
        assertEquals(AgentRunResult.Status.STALE_CONVERSATION, second.join().status()); assertTrue(backend.started.isEmpty());
        assertThrows(ExecutionStoppedException.class, backend.calls.get(a.requestId()).control::checkActive);
        backend.beforePrepare.set(() -> {}); var c = request(); var current = service.run(handle, c).toCompletableFuture(); runtime.runAll();
        complete(c); assertEquals(AgentRunResult.Status.SUCCESS, current.join().status());
    }

    /** 验证多次失效同时覆盖新排队项，且不会取消其他 scope 的请求。 */
    @Test void repeatedInvalidationRemovesNewWaitersAndPreservesOtherScope() {
        backend.autoAck.set(false);
        var a = request(); var other = request("客户端", "另一群"); service.run(handle, a); var independent = service.run(handle, other).toCompletableFuture(); runtime.runAll();
        service.invalidate(handle, a.actor().scope());
        var b = request(); var waiting = service.run(handle, b).toCompletableFuture();
        var latest = service.invalidate(handle, a.actor().scope()).orElseThrow();
        assertEquals(AgentRunResult.Status.STALE_CONVERSATION, waiting.join().status()); assertFalse(independent.isDone());
        complete(a); complete(other); assertEquals(AgentRunResult.Status.SUCCESS, independent.join().status()); assertEquals(latest, state(a).stamp());
    }

    /** 验证失效交付回调在锁外执行，可从另一线程读取新代次而不死锁。 */
    @Test void invalidationCallbacksCanReadNewEpochFromAnotherThread() {
        var a = request(); var stage = service.run(handle, a);
        var observed = stage.thenApply(ignored -> CompletableFuture.supplyAsync(() -> state(a).stamp()).orTimeout(3, TimeUnit.SECONDS).join());
        var next = service.invalidate(handle, a.actor().scope()).orElseThrow();
        assertEquals(next, observed.toCompletableFuture().join()); assertTrue(backend.started.isEmpty());
    }

    /** 验证默认邮箱覆盖传入的代次提示，不让调用者借其他实例的绑定决定执行上下文。 */
    @Test void mailboxAlwaysBindsItsOwnTrustedStamp() {
        var raw = request(); var foreign = new AgentConversationStamp(
                new AgentConversationKey(org.zexnocs.teanekocore.framework.composition.EntityId.random(), raw.actor().scope()), UUID.randomUUID());
        var a = new AgentRunRequest(raw.requestId(), raw.actor(), raw.activity(), raw.messages(), foreign);
        service.run(handle, a); runtime.runAll();
        assertNotEquals(foreign, backend.calls.get(a.requestId()).request.conversation());
        assertEquals(state(a).stamp(), backend.calls.get(a.requestId()).request.conversation()); complete(a);
    }

    /**
     * 验证多个作用域同时竞争容量时，登记数量受同一邮箱锁约束。
     * @throws Exception 当并发提交或请求完成超过测试时限时
     */
    @Test void concurrentNewConversationsRespectGlobalCapacity() throws Exception {
        try (var workers = Executors.newVirtualThreadPerTaskExecutor(); var timers = Executors.newSingleThreadScheduledExecutor();
             var queued = new AgentConversationService(backend, AgentQueueSettings::defaults, workers, timers,
                     java.time.Clock.systemUTC(), logger, () -> new AgentConversationSettings(4, Duration.ofMinutes(30)))) {
            var start = new CountDownLatch(1); var submissions = new ArrayList<Future<CompletionStage<AgentRunResult>>>();
            var prepared = new CountDownLatch(4); backend.beforePrepare.set(prepared::countDown);
            for (int i = 0; i < 50; i++) {
                var request = request("客户端", "并发群" + i);
                submissions.add(workers.submit(() -> { start.await(); return queued.run(handle, request); }));
            }
            start.countDown(); var results = new ArrayList<CompletableFuture<AgentRunResult>>();
            for (var submission : submissions) { results.add(submission.get(5, TimeUnit.SECONDS).toCompletableFuture()); }
            assertTrue(prepared.await(5, TimeUnit.SECONDS));
            assertEquals(46, results.stream().filter(CompletableFuture::isDone).count());
            queued.close();
            for (var result : results) { result.get(5, TimeUnit.SECONDS); }
            assertEquals(46, results.stream().filter(result -> result.join().status() == AgentRunResult.Status.BUSY).count());
            assertEquals(4, results.stream().filter(result -> result.join().status() == AgentRunResult.Status.CANCELLED).count());
            assertTrue(backend.calls.size() <= 4);
        }
    }

    /**
     * 验证真实线程中的结束与代次切换竞争只交付一个结果，下一轮固定当前代次。
     * @throws Exception 当竞争操作或结果交付超过测试时限时
     */
    @Test void concurrentInvalidationAndCompletionNeverReuseOldEpoch() throws Exception {
        try (var workers = Executors.newVirtualThreadPerTaskExecutor(); var timers = Executors.newSingleThreadScheduledExecutor();
             var queued = new AgentConversationService(backend, AgentQueueSettings::defaults, Runnable::run, timers,
                     java.time.Clock.systemUTC(), logger)) {
            for (int i = 0; i < 40; i++) {
                var a = request(); var first = queued.run(handle, a).toCompletableFuture();
                var old = backend.calls.get(a.requestId()).request.conversation(); var start = new CountDownLatch(1);
                var invalidation = workers.submit(() -> { start.await(); return queued.invalidate(handle, a.actor().scope()).orElseThrow(); });
                var completion = workers.submit(() -> { start.await(); backend.calls.get(a.requestId()).complete(AgentRunResult.Status.SUCCESS); return null; });
                start.countDown(); var next = invalidation.get(5, TimeUnit.SECONDS); completion.get(5, TimeUnit.SECONDS);
                var outcome = first.get(5, TimeUnit.SECONDS);
                assertTrue(outcome.status() == AgentRunResult.Status.SUCCESS || outcome.status() == AgentRunResult.Status.STALE_CONVERSATION);
                assertNotEquals(old, next);
                var b = request(); var current = queued.run(handle, b).toCompletableFuture();
                assertEquals(next, backend.calls.get(b.requestId()).request.conversation());
                backend.calls.get(b.requestId()).complete(AgentRunResult.Status.SUCCESS);
                assertEquals(AgentRunResult.Status.SUCCESS, current.get(5, TimeUnit.SECONDS).status()); assertSame(outcome, first.join());
            }
        }
    }

    /** 验证自定义端口取消抛错时保留旧运行槽，不能通过异常提前并行执行新代次。 */
    @Test void failedCancellationCannotReleaseInvalidatedRunningSlot() {
        var port = mock(IAgentTurnService.class);
        when(port.prepare(any(), any())).thenAnswer(invocation -> {
            var original = backend.prepare(invocation.getArgument(0), invocation.getArgument(1));
            return new AgentRunExecution(original.result(), original::start,
                    () -> { throw new IllegalStateException("替身取消失败"); }, original::contains);
        });
        try (var queued = new AgentConversationService(port, settings::get, runtime, runtime.scheduler, runtime.clock, logger)) {
            var a = request(); var first = queued.run(handle, a).toCompletableFuture(); runtime.runAll();
            queued.invalidate(handle, a.actor().scope());
            var b = request(); var second = queued.run(handle, b).toCompletableFuture(); runtime.runAll();
            assertFalse(first.isDone()); assertFalse(second.isDone()); assertEquals(List.of(a.requestId()), backend.started);
            complete(a); assertEquals(AgentRunResult.Status.STALE_CONVERSATION, first.join().status());
            assertEquals(List.of(a.requestId(), b.requestId()), backend.started); complete(b); assertEquals(AgentRunResult.Status.SUCCESS, second.join().status());
        }
    }

    /** 验证启动端口已产生实际动作后抛错，仍先取消并等待回执再推进后继。 */
    @Test void failingStarterStillWaitsForCancellationAcknowledgement() {
        backend.autoAck.set(false);
        var port = mock(IAgentTurnService.class); var firstStart = new AtomicBoolean(true);
        when(port.prepare(any(), any())).thenAnswer(invocation -> {
            var original = backend.prepare(invocation.getArgument(0), invocation.getArgument(1));
            return new AgentRunExecution(original.result(), () -> {
                original.start(); if (firstStart.getAndSet(false)) { throw new IllegalStateException("替身启动后失败"); }
            }, original::cancel, original::contains);
        });
        try (var queued = new AgentConversationService(port, settings::get, runtime, runtime.scheduler, runtime.clock, logger)) {
            var a = request(); var first = queued.run(handle, a).toCompletableFuture();
            var b = request(); var second = queued.run(handle, b).toCompletableFuture(); runtime.runAll();
            assertFalse(first.isDone()); assertFalse(second.isDone()); assertEquals(List.of(a.requestId()), backend.started);
            assertThrows(ExecutionStoppedException.class, backend.calls.get(a.requestId()).control::checkActive);
            complete(a); assertEquals(AgentRunResult.Status.FAILED, first.join().status());
            assertEquals(List.of(a.requestId(), b.requestId()), backend.started); complete(b); assertEquals(AgentRunResult.Status.SUCCESS, second.join().status());
        }
    }

    /** 验证同群不同用户按入队顺序执行，并且排队时连冷轮次都不创建。 */
    @Test void differentUsersInSameGroupRunInFifoOrder() {
        var a = request(); var b = request(); var c = request();
        var first = service.run(handle, a).toCompletableFuture();
        var second = service.run(handle, b).toCompletableFuture();
        var third = service.run(handle, c).toCompletableFuture(); runtime.runAll();
        assertEquals(List.of(a.requestId()), backend.started); assertEquals(1, backend.calls.size());
        assertFalse(first.isDone()); assertFalse(second.isDone());
        complete(a); assertEquals(AgentRunResult.Status.SUCCESS, first.join().status());
        assertEquals(List.of(a.requestId(), b.requestId()), backend.started); assertFalse(third.isDone());
        complete(b); complete(c); assertEquals(AgentRunResult.Status.SUCCESS, third.join().status());
        assertEquals(List.of(a.requestId(), b.requestId(), c.requestId()), backend.started);
    }

    /** 验证 client、scope 和实例分别隔离运行槽，不按共享定义串行化所有请求。 */
    @Test void differentClientScopeAndInstanceExecuteIndependently() {
        var secondHandle = new AgentFactory(registry).create(handle.definition());
        var a = request(); var b = request("另一个客户端", "群"); var c = request("客户端", "另一群"); var d = request();
        var stages = List.of(service.run(handle, a), service.run(handle, b), service.run(handle, c), service.run(secondHandle, d));
        runtime.runAll(); assertEquals(4, backend.started.size());
        for (var call : backend.calls.values()) { call.complete(AgentRunResult.Status.SUCCESS); }
        runtime.runAll(); stages.forEach(stage -> assertEquals(AgentRunResult.Status.SUCCESS, stage.toCompletableFuture().join().status()));
    }

    /** 验证默认五个等待槽不包含已占用的运行槽，溢出不创建底层轮次。 */
    @Test void defaultCapacityAllowsFiveWaitingRequests() {
        var first = request(); service.run(handle, first); runtime.runAll();
        var waiting = new ArrayList<AgentRunRequest>();
        for (int i = 0; i < 5; i++) { var request = request(); waiting.add(request); service.run(handle, request); }
        var overflow = service.run(handle, request()).toCompletableFuture().join();
        assertEquals(AgentRunResult.Status.BUSY, overflow.status()); assertEquals(1, backend.calls.size());
        complete(first); for (var request : waiting) { complete(request); }
    }

    /** 验证零等待容量保留一个运行槽，忙碌时立即拒绝。 */
    @Test void zeroWaitingCapacityStillAllowsOneActiveRequest() {
        settings.set(new AgentQueueSettings(0, Duration.ofSeconds(30)));
        var a = request(); service.run(handle, a); runtime.runAll();
        assertEquals(AgentRunResult.Status.BUSY, service.run(handle, request()).toCompletableFuture().join().status());
        complete(a);
    }

    /** 验证本地重载只影响新入队策略，已等待请求保持原截止时间。 */
    @Test void queuedPolicyIsFrozenAcrossReload() {
        var a = request(); var b = request(); service.run(handle, a); runtime.runAll();
        var waiting = service.run(handle, b).toCompletableFuture();
        settings.set(new AgentQueueSettings(0, Duration.ofSeconds(1)));
        assertEquals(AgentRunResult.Status.BUSY, service.run(handle, request()).toCompletableFuture().join().status());
        runtime.advance(Duration.ofSeconds(2)); assertFalse(waiting.isDone());
        complete(a); complete(b); assertEquals(AgentRunResult.Status.SUCCESS, waiting.join().status());
    }

    /** 验证普通用户不能取消同群另一人的请求，取消本人等待项不影响队首。 */
    @Test void cancelOwnWaitingRequestDoesNotCancelAnotherUser() {
        var a = request(); var b = request(); var c = request();
        service.run(handle, a); var second = service.run(handle, b).toCompletableFuture(); service.run(handle, c); runtime.runAll();
        assertEquals(IAgentService.CancelResult.NOT_OWNER, service.cancel(handle, b.actor().scope(), b.requestId(), a.actor().userId().orElseThrow()));
        assertEquals(IAgentService.CancelResult.REQUESTED, cancel(b)); assertEquals(AgentRunResult.Status.CANCELLED, second.join().status());
        assertEquals(List.of(a.requestId()), backend.started); complete(a); assertEquals(List.of(a.requestId(), c.requestId()), backend.started); complete(c);
        assertEquals(IAgentService.CancelResult.NOT_FOUND, cancel(b));
    }

    /** 验证运行取消必须等待底层回执才释放槽位，避免旧动作尚未结束时与下一轮交错。 */
    @Test void runningCancellationWaitsForBackendAcknowledgement() {
        backend.autoAck.set(false);
        var a = request(); var b = request();
        var first = service.run(handle, a).toCompletableFuture(); service.run(handle, b); runtime.runAll();
        assertEquals(IAgentService.CancelResult.REQUESTED, cancel(a)); runtime.runAll();
        assertFalse(first.isDone()); assertEquals(List.of(a.requestId()), backend.started);
        backend.calls.get(a.requestId()).complete(AgentRunResult.Status.CANCELLED); runtime.runAll();
        assertEquals(AgentRunResult.Status.CANCELLED, first.join().status()); assertEquals(List.of(a.requestId(), b.requestId()), backend.started);
        backend.autoAck.set(true); complete(b);
    }

    /** 验证首项在执行器启动前取消，迟到调度不得创建任何底层资源。 */
    @Test void cancellationBeforeDispatchDoesNotPrepareTurn() {
        var a = request(); var first = service.run(handle, a).toCompletableFuture();
        assertEquals(IAgentService.CancelResult.REQUESTED, cancel(a)); assertEquals(AgentRunResult.Status.CANCELLED, first.join().status());
        runtime.runAll(); assertTrue(backend.calls.isEmpty());
    }

    /** 验证等待超时不影响当前运行请求，不创建被丢弃项的轮次。 */
    @Test void waitingTimeoutRemovesOnlyExpiredRequest() {
        var a = request(); var b = request(); var first = service.run(handle, a).toCompletableFuture(); runtime.runAll();
        var waiting = service.run(handle, b).toCompletableFuture(); runtime.advance(Duration.ofSeconds(30));
        assertEquals(AgentRunResult.Status.QUEUE_TIMED_OUT, waiting.join().status()); assertFalse(first.isDone());
        assertEquals(1, backend.calls.size()); complete(a); assertEquals(1, backend.started.size());
    }

    /** 验证已选中但尚未获得执行线程的首项也受排队上限约束。 */
    @Test void selectedRequestCannotWaitForeverForExecutor() {
        var request = request(); var result = service.run(handle, request).toCompletableFuture();
        runtime.advance(Duration.ofSeconds(30)); assertEquals(AgentRunResult.Status.QUEUE_TIMED_OUT, result.join().status());
        runtime.runAll(); assertTrue(backend.calls.isEmpty());
    }

    /**
     * 验证排队计时任务即使取消失败且迟到执行，也不能终止已经开始的轮次。
     * @throws Exception 当受控计时回调失败时
     */
    @Test void lateQueueTimerCannotCancelRunningTurn() throws Exception {
        var callback = new AtomicReference<Callable<?>>();
        doAnswer(invocation -> { callback.set(invocation.getArgument(0)); return mock(ScheduledFuture.class); })
                .when(runtime.scheduler).schedule(org.mockito.ArgumentMatchers.<Callable<Object>>any(), anyLong(), any(TimeUnit.class));
        var request = request(); var result = service.run(handle, request).toCompletableFuture(); runtime.runAll();
        callback.get().call(); assertFalse(result.isDone()); complete(request);
        assertEquals(AgentRunResult.Status.SUCCESS, result.join().status());
    }

    /** 验证出队时重新检查等待截止时间，不依赖计时线程已经执行。 */
    @Test void delayedTimerDoesNotAllowExpiredRequestToStart() {
        var instant = new AtomicReference<>(runtime.clock.instant());
        var current = mock(java.time.Clock.class); when(current.instant()).thenAnswer(ignored -> instant.get());
        try (var queued = new AgentConversationService(backend, settings::get, runtime, runtime.scheduler, current, logger)) {
            var a = request(); var b = request(); queued.run(handle, a); runtime.runAll();
            var waiting = queued.run(handle, b).toCompletableFuture();
            instant.set(instant.get().plusSeconds(31)); backend.calls.get(a.requestId()).complete(AgentRunResult.Status.SUCCESS); runtime.runAll();
            assertEquals(AgentRunResult.Status.QUEUE_TIMED_OUT, waiting.join().status()); assertEquals(1, backend.started.size());
        }
    }

    /** 验证取消一个父控制只移除其排队项，不能取消其他父请求。 */
    @Test void parentCancellationWhileWaitingKeepsOthersActive() {
        try (var parent = runtime.parent(LLMCallLimits.defaults())) {
            var a = request(); var b = request(); service.run(handle, a); runtime.runAll();
            var waiting = service.run(handle, b, parent).toCompletableFuture(); parent.cancel();
            assertEquals(AgentRunResult.Status.CANCELLED, waiting.join().status()); assertEquals(1, backend.calls.size()); complete(a);
        }
    }

    /** 验证父截止时间在排队阶段保持超时语义，不能出队后重新获得时限。 */
    @Test void parentDeadlineWhileWaitingIsNotReset() {
        try (var parent = ExecutionControl.withDeadline(LLMCallLimits.defaults().newBudget(), runtime.clock.instant().plusSeconds(5),
                runtime.clock, runtime.scheduler)) {
            var a = request(); var b = request(); service.run(handle, a); runtime.runAll();
            var waiting = service.run(handle, b, parent).toCompletableFuture(); runtime.advance(Duration.ofSeconds(5));
            assertEquals(AgentRunResult.Status.TIMED_OUT, waiting.join().status()); complete(a);
        }
    }

    /** 验证出队仍共享父预算，等待不占额度，后继没有重建总额度。 */
    @Test void derivedQueueSharesBudgetWithoutChargingWaiting() {
        backend.charge.set(true);
        try (var parent = runtime.parent(new LLMCallLimits(1, 0, 0))) {
            var a = request(); var b = request();
            var first = service.run(handle, a, parent).toCompletableFuture(); var second = service.run(handle, b, parent).toCompletableFuture(); runtime.runAll();
            assertEquals(1, parent.budget().used(LLMCallLimits.MODEL_REQUESTS)); assertEquals(1, backend.calls.size());
            complete(a); assertEquals(AgentRunResult.Status.SUCCESS, first.join().status());
            assertEquals(AgentRunResult.Status.BUDGET_EXCEEDED, second.join().status());
            assertEquals(1, parent.budget().used(LLMCallLimits.MODEL_REQUESTS)); assertDoesNotThrow(parent::checkActive);
        }
    }

    /** 验证显式后代控制递归进入同一邮箱快速拒绝，不等待自己占有的槽位。 */
    @Test void sameConversationDescendantIsRejectedButSiblingParentCanQueue() {
        var a = request(); service.run(handle, a); runtime.runAll();
        try (var child = backend.calls.get(a.requestId()).control.child(); var sibling = runtime.parent(LLMCallLimits.defaults())) {
            var rejected = service.run(handle, request(), child).toCompletableFuture().join(); assertEquals(AgentRunResult.Status.REENTRANT, rejected.status());
            var b = request(); var waiting = service.run(handle, b, sibling).toCompletableFuture(); assertFalse(waiting.isDone());
            complete(a); complete(b); assertEquals(AgentRunResult.Status.SUCCESS, waiting.join().status());
        }
    }

    /** 验证同一派生链进入另一会话可并行，不把共享预算对象误判为递归。 */
    @Test void descendantCanRunInAnotherScope() {
        var a = request(); service.run(handle, a); runtime.runAll();
        try (var child = backend.calls.get(a.requestId()).control.child()) {
            var b = request("客户端", "另一个群"); service.run(handle, b, child); runtime.runAll();
            assertEquals(2, backend.started.size()); complete(b); complete(a);
        }
    }

    /** 验证相同在途 requestId 明确拒绝，不让另一个调用者获得或替换已有结果。 */
    @Test void duplicateRequestIsNotSilentlyMergedAndIdentifierCanBeReusedAfterFinish() {
        var a = request(); service.run(handle, a); runtime.runAll();
        assertEquals(AgentRunResult.Status.DUPLICATE_REQUEST, service.run(handle, a).toCompletableFuture().join().status()); complete(a);
        var retry = service.run(handle, a).toCompletableFuture(); runtime.runAll(); complete(a);
        assertEquals(AgentRunResult.Status.SUCCESS, retry.join().status()); assertEquals(2, backend.started.size());
    }

    /** 验证实例关闭结束其运行和排队项，不影响其他实例。 */
    @Test void instanceCloseCancelsOnlyItsOwnMailbox() {
        var secondHandle = new AgentFactory(registry).create(handle.definition()); var a = request(); var b = request(); var c = request();
        var first = service.run(handle, a).toCompletableFuture(); var waiting = service.run(handle, b).toCompletableFuture();
        var other = service.run(secondHandle, c).toCompletableFuture(); runtime.runAll(); handle.close(); runtime.runAll();
        assertEquals(AgentRunResult.Status.CANCELLED, first.join().status()); assertEquals(AgentRunResult.Status.CANCELLED, waiting.join().status());
        assertFalse(other.isDone()); complete(c); assertEquals(AgentRunResult.Status.SUCCESS, other.join().status());
    }

    /** 验证服务关闭阻止后继启动，不关闭父请求或实例。 */
    @Test void serviceCloseEndsEveryOwnedRequest() {
        try (var parent = runtime.parent(LLMCallLimits.defaults())) {
            var a = request(); var b = request(); var first = service.run(handle, a, parent).toCompletableFuture();
            var waiting = service.run(handle, b).toCompletableFuture(); runtime.runAll(); service.close(); service.close(); runtime.runAll();
            assertEquals(AgentRunResult.Status.CANCELLED, first.join().status()); assertEquals(AgentRunResult.Status.CANCELLED, waiting.join().status());
            assertEquals(1, backend.started.size()); assertDoesNotThrow(parent::checkActive); assertFalse(handle.isClosed());
            assertEquals(AgentRunResult.Status.CANCELLED, service.run(handle, request()).toCompletableFuture().join().status());
        }
    }

    /** 验证取消结果视图不会取消排队或运行请求。 */
    @Test void cancellingViewDoesNotCancelRequest() {
        var a = request(); var stage = service.run(handle, a); assertTrue(stage.toCompletableFuture().cancel(false)); runtime.runAll(); complete(a);
        assertEquals(AgentRunResult.Status.SUCCESS, stage.toCompletableFuture().join().status());
    }

    /** 验证大量同步完成的后继通过邮箱工作计数推进，不递归增长调用栈。 */
    @Test void directExecutorDrainsLongQueueWithoutRecursiveStack() {
        settings.set(new AgentQueueSettings(1000, Duration.ofSeconds(30)));
        try (var direct = new AgentConversationService(backend, settings::get, Runnable::run, runtime.scheduler, runtime.clock, logger)) {
            var a = request(); direct.run(handle, a);
            var stages = new ArrayList<CompletionStage<AgentRunResult>>();
            for (int i = 0; i < 700; i++) { stages.add(direct.run(handle, request())); }
            backend.immediate.set(true); backend.calls.get(a.requestId()).complete(AgentRunResult.Status.SUCCESS);
            for (var stage : stages) { assertEquals(AgentRunResult.Status.SUCCESS, stage.toCompletableFuture().join().status()); }
            assertEquals(701, backend.started.size());
        }
    }

    /** 验证调度拒绝结束当前未启动请求，后续修复后可使用新邮箱。 */
    @Test void rejectedExecutorCompletesWithoutPreparingBackend() {
        var reject = new AtomicBoolean(true);
        Executor executor = action -> { if (reject.get()) { throw new RejectedExecutionException("调度失败"); } runtime.execute(action); };
        try (var queued = new AgentConversationService(backend, settings::get, executor, runtime.scheduler, runtime.clock, logger)) {
            assertEquals(AgentRunResult.Status.FAILED, queued.run(handle, request()).toCompletableFuture().join().status()); assertTrue(backend.calls.isEmpty());
            reject.set(false); var request = request(); var result = queued.run(handle, request).toCompletableFuture(); runtime.runAll(); complete(request);
            assertEquals(AgentRunResult.Status.SUCCESS, result.join().status());
        }
    }

    /** 验证计时器拒绝不会丢失结果，也不会启动尚未受时限保护的请求。 */
    @Test void rejectedTimerFailsBeforeStartingBackend() {
        doThrow(new RejectedExecutionException("不可公开的计时故障")).when(runtime.scheduler)
                .schedule(org.mockito.ArgumentMatchers.<Callable<Object>>any(), anyLong(), any(TimeUnit.class));
        var result = service.run(handle, request()).toCompletableFuture().join();
        assertEquals(AgentRunResult.Status.FAILED, result.status()); assertTrue(backend.calls.isEmpty());
        assertFalse(result.toString().contains("不可公开的计时故障"));
    }

    /** 验证计时器返回空句柄时明确失败，不能把无超时保护的请求留在队列中。 */
    @Test void nullTimerHandleFailsBeforeStartingBackend() {
        doReturn(null).when(runtime.scheduler).schedule(org.mockito.ArgumentMatchers.<Callable<Object>>any(), anyLong(), any(TimeUnit.class));
        assertEquals(AgentRunResult.Status.FAILED, service.run(handle, request()).toCompletableFuture().join().status());
        runtime.runAll(); assertTrue(backend.calls.isEmpty());
    }

    /**
     * 验证启动端口和用户完成回调均不在邮箱锁内运行。
     */
    @Test void backendAndCompletionCallbacksRunOutsideMailboxLock() {
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            backend.beforePrepare.set(() -> {
                try { workers.submit(() -> service.run(handle, request("客户端", "锁外群"))).get(2, TimeUnit.SECONDS); }
                catch (Exception failure) { throw new AssertionError("启动端口运行在邮箱锁内。", failure); }
                backend.beforePrepare.set(() -> {});
            });
            var a = request(); var stage = service.run(handle, a);
            stage.thenAccept(ignored -> {
                try { workers.submit(() -> service.run(handle, request("客户端", "完成回调群"))).get(2, TimeUnit.SECONDS); }
                catch (Exception failure) { throw new AssertionError("完成回调运行在邮箱锁内。", failure); }
            });
            runtime.runAll(); complete(a);
        }
    }

    /** 验证关闭发生在冷轮次准备期间时，返回的迟到句柄必须取消而不是启动。 */
    @Test void closeDuringColdPreparationDoesNotStartDetachedTurn() {
        backend.beforePrepare.set(service::close); var request = request();
        var result = service.run(handle, request).toCompletableFuture(); runtime.runAll();
        assertEquals(AgentRunResult.Status.CANCELLED, result.join().status()); assertTrue(backend.started.isEmpty());
        assertThrows(ExecutionStoppedException.class, backend.calls.get(request.requestId()).control::checkActive);
    }

    /** 验证系统任务没有可继承的用户 UUID，普通用户不能借同群身份取消它。 */
    @Test void userCannotCancelSystemOriginTask() {
        var actor = new AgentActorContext(new AgentScope("客户端", "群"), AgentActorContext.Origin.SYSTEM,
                Optional.empty(), "", "系统任务", "目标");
        var request = new AgentRunRequest(UUID.randomUUID(), actor, AgentActivity.SCHEDULED, List.of());
        var result = service.run(handle, request).toCompletableFuture(); runtime.runAll();
        assertEquals(IAgentService.CancelResult.NOT_OWNER, service.cancel(handle, actor.scope(), request.requestId(), UUID.randomUUID()));
        assertFalse(result.isDone()); complete(request);
    }

    /** 验证头部准备失败会推进下一项，不把失败当作永久占用的运行槽。 */
    @Test void failedHeadPreparationAdvancesNextRequest() {
        var port = mock(IAgentTurnService.class); var count = new AtomicInteger();
        when(port.prepare(any(), any())).thenAnswer(invocation -> {
            if (count.getAndIncrement() == 0) { throw new IllegalStateException("端口准备失败"); }
            return backend.prepare(invocation.getArgument(0), invocation.getArgument(1));
        });
        try (var queued = new AgentConversationService(port, settings::get, runtime, runtime.scheduler, runtime.clock, logger)) {
            var a = request(); var b = request(); var first = queued.run(handle, a).toCompletableFuture();
            var second = queued.run(handle, b).toCompletableFuture(); runtime.runAll();
            assertEquals(AgentRunResult.Status.FAILED, first.join().status()); assertEquals(List.of(b.requestId()), backend.started);
            complete(b); assertEquals(AgentRunResult.Status.SUCCESS, second.join().status());
        }
    }

    /** 验证错误端口不能向当前请求交付其他 requestId 的结果。 */
    @Test void foreignResultIdentityIsRejected() {
        var port = mock(IAgentTurnService.class);
        when(port.prepare(any(), any())).thenReturn(new AgentRunExecution(CompletableFuture.completedStage(
                new AgentRunResult(handle.id(), UUID.randomUUID(), AgentRunResult.Status.CANCELLED,
                        Optional.empty(), Optional.empty(), Map.of())), () -> fail("已完成句柄不能再启动业务。"), () -> false, ignored -> false));
        try (var queued = new AgentConversationService(port, settings::get, runtime, runtime.scheduler, runtime.clock, logger)) {
            var request = request(); var result = queued.run(handle, request).toCompletableFuture(); runtime.runAll();
            assertEquals(request.requestId(), result.join().requestId()); assertEquals(AgentRunResult.Status.FAILED, result.join().status());
        }
    }

    /**
     * 验证同会话真实并发提交不会并行启动两个业务，结果均能结束。
     * @throws Exception 当并发请求未在限定时间内结束时
     */
    @Test void concurrentSubmissionsStillStartOnlyOneTurnAtATime() throws Exception {
        backend.immediate.set(true); backend.racing.set(true);
        try (var workers = Executors.newVirtualThreadPerTaskExecutor(); var timers = Executors.newSingleThreadScheduledExecutor();
             var queued = new AgentConversationService(backend, () -> new AgentQueueSettings(300, Duration.ofSeconds(30)),
                     workers, timers, java.time.Clock.systemUTC(), logger)) {
            var start = new CountDownLatch(1); var outcomes = new ArrayList<CompletableFuture<AgentRunResult>>();
            for (int i = 0; i < 200; i++) {
                var request = request();
                outcomes.add(CompletableFuture.supplyAsync(() -> {
                    try { start.await(); } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt(); throw new CompletionException(interrupted);
                    }
                    return queued.run(handle, request);
                }, workers).thenCompose(stage -> stage));
            }
            start.countDown();
            for (var outcome : outcomes) { assertEquals(AgentRunResult.Status.SUCCESS, outcome.get(10, TimeUnit.SECONDS).status()); }
            assertEquals(1, backend.maxConcurrentStarts.get()); assertEquals(200, backend.started.size());
            assertFalse(workers.isShutdown()); assertFalse(timers.isShutdown());
        }
    }

    /**
     * 不执行模型的可控冷轮次端口，业务完成与取消回执分别由用例推进。
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
     * @version 4.5.0-l
     */
    private static final class Backend implements IAgentTurnService, AutoCloseable {
        final Map<UUID, Call> calls = new ConcurrentHashMap<>();
        final List<UUID> started = Collections.synchronizedList(new ArrayList<>());
        final AtomicBoolean autoAck = new AtomicBoolean(true);
        final AtomicBoolean immediate = new AtomicBoolean();
        final AtomicBoolean charge = new AtomicBoolean();
        final AtomicBoolean racing = new AtomicBoolean();
        final AtomicInteger concurrentStarts = new AtomicInteger();
        final AtomicInteger maxConcurrentStarts = new AtomicInteger();
        final AtomicReference<Runnable> beforePrepare = new AtomicReference<>(() -> {});

        /**
         * 仅创建独立控制及冷句柄。
         * @param handle 目标实例
         * @param request 固定请求
         * @return 尚未开始业务的句柄
         */
        @Override public AgentRunExecution prepare(AgentHandle handle, AgentRunRequest request) {
            return create(handle, request, ExecutionControl.create(LLMCallLimits.defaults().newBudget()));
        }

        /**
         * 保留同一父预算及链路。
         * @param handle 目标实例
         * @param request 固定请求
         * @param parent 借用的总控制
         * @return 冷执行句柄
         */
        @Override public AgentRunExecution prepare(AgentHandle handle, AgentRunRequest request, ExecutionControl parent) {
            return create(handle, request, parent.child());
        }

        /**
         * 先创建资源和观察关系，只有显式 start 才登记实际业务调用。
         * @param handle 实例
         * @param request 请求
         * @param control 本用例拥有的控制
         * @return 受控冷句柄
         */
        private AgentRunExecution create(AgentHandle handle, AgentRunRequest request, ExecutionControl control) {
            beforePrepare.get().run();
            var call = new Call(handle, request, control);
            calls.put(request.requestId(), call);
            var link = control.onTermination(reason -> {
                if (autoAck.get()) { call.complete(reason == ExecutionControl.StopReason.DEADLINE_EXCEEDED
                        ? AgentRunResult.Status.TIMED_OUT : AgentRunResult.Status.CANCELLED); }
            });
            call.result.whenComplete((value, failure) -> { link.close(); control.close(); });
            return new AgentRunExecution(call.result, () -> {
                try {
                    control.checkActive();
                    if (charge.get()) { control.consume(LLMCallLimits.MODEL_REQUESTS, 1); }
                    started.add(request.requestId());
                    if (racing.get()) {
                        int current = concurrentStarts.incrementAndGet(); maxConcurrentStarts.accumulateAndGet(current, Math::max);
                        try { Thread.yield(); } finally { concurrentStarts.decrementAndGet(); }
                    }
                    if (immediate.get()) { call.complete(AgentRunResult.Status.SUCCESS); }
                } catch (BudgetExceededException failure) { call.complete(AgentRunResult.Status.BUDGET_EXCEEDED); }
            }, control::cancel, candidate -> candidate.isWithin(control.id()));
        }

        /** 释放本用例仍持有的控制和未完成回执。 */
        @Override public void close() { calls.values().forEach(call -> call.control.cancel()); }
    }

    /**
     * 单次替身业务，成功结果使用真实不可变协议容器。
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
     * @version 4.5.0-l
     */
    private static final class Call {
        final AgentHandle handle;
        final AgentRunRequest request;
        final ExecutionControl control;
        final CompletableFuture<AgentRunResult> result = new CompletableFuture<>();

        /**
         * 保存当前替身拥有的数据与控制。
         * @param handle 目标实例
         * @param request 当前请求
         * @param control 当前控制
         */
        Call(AgentHandle handle, AgentRunRequest request, ExecutionControl control) {
            this.handle = handle; this.request = request; this.control = control;
        }

        /**
         * 完成真实回执；取消后的重复成功不会改写已交付结果。
         * @param status 业务状态
         */
        void complete(AgentRunResult.Status status) {
            var runtime = new AgentRuntimeSnapshot(new LLMModelOptionsSnapshot(LLMModelOptions.builder()
                    .provider("test").model("离线模型").build()), ModelCapabilities.textOnly(), LLMInteractionSettings.defaults());
            var access = new AgentAccessDecision(AgentAccessDecision.Reason.ALLOWED,
                    Optional.of(new AgentScopePolicy(true, true, AgentScopePolicy.Mode.ACTIVE)),
                    Optional.of(new AgentReadiness(AgentReadiness.Status.READY, Optional.of(runtime))));
            var message = LLMAssistantMessage.builder().contents(new LLMContentListBuilder().addText("替身回答").build()).build();
            var interaction = new LLMInteractionResult(LLMInteractionResult.Status.SUCCESS, List.of(message), message,
                    List.of(), List.of(), Map.of());
            result.complete(new AgentRunResult(handle.id(), request.requestId(), status, Optional.of(access),
                    status == AgentRunResult.Status.SUCCESS ? Optional.of(interaction) : Optional.empty(), control.budget().snapshot()));
        }
    }
}
