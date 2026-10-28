package org.zexnocs.teanekocore.actuator.execution;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证共享预算控制的取消、截止时间、父子关系与锁外资源通知。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
class ExecutionControlTest {
    /**
     * 验证终止监听可解除且至多通知一次。
     */
    @Test
    void terminationRegistrationsCanBeDetachedAndAreDeliveredOnce() {
        try (var control = ExecutionControl.create()) {
            var removed = new java.util.concurrent.atomic.AtomicInteger();
            var active = new java.util.concurrent.atomic.AtomicInteger();
            var registration = control.onTermination(reason -> removed.incrementAndGet());
            registration.close();
            registration.close();
            control.onTermination(reason -> active.incrementAndGet());
            control.cancel();
            control.close();
            assertEquals(0, removed.get());
            assertEquals(1, active.get());
        }
    }

    /**
     * 验证迟到监听立即收到终止通知，单个回调失败不阻断其他监听。
     */
    @Test
    void lateObserversReceiveStopImmediatelyAndOneFailureDoesNotBlockOthers() {
        try (var control = ExecutionControl.create()) {
            var observed = new java.util.concurrent.atomic.AtomicInteger();
            control.onTermination(reason -> { throw new IllegalStateException("observer failed"); });
            control.onTermination(reason -> observed.incrementAndGet());
            assertDoesNotThrow(control::cancel);
            control.onTermination(reason -> observed.incrementAndGet());
            assertEquals(2, observed.get());
            assertEquals(ExecutionControl.StopReason.CANCELLED, control.termination().toCompletableFuture().join());
        }
    }

    /**
     * 验证取消时重入注册的监听也在生命周期锁外调用。
     *
     * @throws Exception 当底层业务、反射调用或结果转换失败时
     */
    @Test
    void reentrantObserverRegistrationAfterCancellationRunsOutsideLifecycleLock() throws Exception {
        try (var control = ExecutionControl.create(); var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var observed = new CompletableFuture<ExecutionControl.StopReason>();
            control.whileActive(() -> {
                control.cancel();
                control.onTermination(reason -> {
                    var check = executor.submit(() -> assertThrows(ExecutionStoppedException.class, control::checkActive).reason());
                    try {
                        observed.complete(check.get(3, TimeUnit.SECONDS));
                    } catch (Exception failure) {
                        observed.completeExceptionally(failure);
                    }
                });
                assertFalse(observed.isDone());
                return null;
            });
            assertEquals(ExecutionControl.StopReason.CANCELLED, observed.get(3, TimeUnit.SECONDS));
        }
    }

    /**
     * 验证取消能结束包装阶段而无需等待源阶段。
     */
    @Test
    void cancellationCompletesGuardWithoutWaitingForSource() {
        try (var control = ExecutionControl.create()) {
            var source = new CompletableFuture<String>();
            var guarded = control.guard(source).toCompletableFuture();
            assertFalse(guarded.isDone());

            assertTrue(control.cancel());
            assertFalse(control.cancel());

            var failure = assertThrows(CompletionException.class, guarded::join).getCause();
            assertInstanceOf(ExecutionStoppedException.class, failure);
            assertEquals(ExecutionControl.StopReason.CANCELLED, ((ExecutionStoppedException) failure).reason());
            assertFalse(source.isDone());
            source.complete("late result");
            assertTrue(guarded.isCompletedExceptionally());
        }
    }

    /**
     * 验证截止时间结束永不完成的阶段且不关闭借用调度器。
     *
     */
    @Test
    void deadlineStopsANeverCompletingStageWithoutShuttingDownBorrowedScheduler() {
        var scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            try (var control = ExecutionControl.withDeadline(new ResourceBudget(Map.of()),
                    Instant.now().plusMillis(80), Clock.systemUTC(), scheduler)) {
                var guarded = control.guard(new CompletableFuture<>()).toCompletableFuture();
                var failure = assertThrows(ExecutionException.class, () -> guarded.get(3, TimeUnit.SECONDS)).getCause();
                assertInstanceOf(ExecutionStoppedException.class, failure);
                assertEquals(ExecutionControl.StopReason.DEADLINE_EXCEEDED, ((ExecutionStoppedException) failure).reason());
            }
            assertFalse(scheduler.isShutdown());
        } finally {
            scheduler.shutdownNow();
        }
    }

    /**
     * 验证子执行共享预算且取消只向下传播。
     */
    @Test
    void childrenShareBudgetButOnlyInheritCancellationDownward() {
        var key = new BudgetKey("work");
        try (var parent = ExecutionControl.create(new ResourceBudget(Map.of(key, 3L)));
             var child = parent.child()) {
            child.consume(key, 1);
            parent.consume(key, 1);
            assertEquals(2, parent.budget().used(key));
            assertEquals(parent.id(), child.parentId().orElseThrow());

            child.cancel();
            assertThrows(ExecutionStoppedException.class, child::checkActive);
            parent.consume(key, 1);
            assertEquals(3, child.budget().used(key));

            try (var otherChild = parent.child()) {
                parent.cancel();
                assertEquals(ExecutionControl.StopReason.CANCELLED,
                        assertThrows(ExecutionStoppedException.class, otherChild::checkActive).reason());
            }
        }
    }

    /**
     * 验证关闭拥有者会终止在途子执行并拒绝新工作。
     */
    @Test
    void closingOwnerTerminatesPendingChildrenAndRejectsNewWork() {
        var parent = ExecutionControl.create();
        try (var child = parent.child()) {
            var guarded = child.guard(new CompletableFuture<>()).toCompletableFuture();
            parent.close();
            assertTrue(guarded.isCompletedExceptionally());
            assertEquals(ExecutionControl.StopReason.CLOSED,
                    assertThrows(ExecutionStoppedException.class, child::checkActive).reason());
            assertThrows(ExecutionStoppedException.class, parent::child);
        }
    }

    /**
     * 验证即使计时回调未运行，轻量提交前也检查截止时间。
     */
    @Test
    void deadlineIsCheckedBeforeLightweightCommitEvenWithoutTimerCallback() {
        var scheduler = Executors.newSingleThreadScheduledExecutor();
        var now = Instant.parse("2026-10-28T00:00:00Z");
        try (var control = ExecutionControl.withDeadline(new ResourceBudget(Map.of()), now.minusSeconds(1),
                Clock.fixed(now, java.time.ZoneOffset.UTC), scheduler)) {
            assertEquals(Duration.ZERO, control.remainingTime().orElseThrow());
            assertEquals(ExecutionControl.StopReason.DEADLINE_EXCEEDED,
                    assertThrows(ExecutionStoppedException.class, () -> control.whileActive(() -> "must not commit")).reason());
        } finally {
            scheduler.shutdownNow();
        }
    }

    /**
     * 验证重入取消在退出生命周期锁后通知观察者。
     */
    @Test
    void reentrantCancellationNotifiesObserversAfterReleasingLifecycleLock() {
        try (var control = ExecutionControl.create(); var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var observed = control.termination().thenApply(reason -> {
                var check = executor.submit(() -> assertThrows(ExecutionStoppedException.class, control::checkActive).reason());
                try {
                    return check.get(3, TimeUnit.SECONDS);
                } catch (Exception failure) {
                    throw new CompletionException(failure);
                }
            });

            control.whileActive(() -> {
                control.cancel();
                return null;
            });

            assertEquals(ExecutionControl.StopReason.CANCELLED, observed.toCompletableFuture().join());
        }
    }
}
