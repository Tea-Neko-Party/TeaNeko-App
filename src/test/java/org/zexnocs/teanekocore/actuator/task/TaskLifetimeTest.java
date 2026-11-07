package org.zexnocs.teanekocore.actuator.task;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.zexnocs.teanekocore.actuator.execution.ExecutionBusyException;
import org.zexnocs.teanekocore.actuator.execution.ExecutionStoppedException;
import org.zexnocs.teanekocore.actuator.task.exception.TaskExpirationException;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskExecuteService;
import org.zexnocs.teanekocore.cache.interfaces.ICacheContainer;
import org.zexnocs.teanekocore.cache.interfaces.ICacheService;
import org.zexnocs.teanekocore.database.base.DatabaseTaskConfig;
import org.zexnocs.teanekocore.database.base.interfaces.IDatabaseService;
import org.zexnocs.teanekocore.framework.function.VoidCallable;
import org.zexnocs.teanekocore.logger.ILogger;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 真实任务宿主的准入、排队取消、运行超时、外部迟到及关闭回归。
 *
 * <br>2026/11/07：验证准入、停止、重试及完整事件的收尾合同。
 *
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.7-l
 * @version 4.8.7-l
 */
@Timeout(10)
class TaskLifetimeTest {
    /**
     * 2026/11/07：执行控制先使事务退出，线程中断必须等代理回滚及资源释放完成后再投递。
     *
     * @throws Exception 有界任务或资源收尾失败时
     */
    @Test
    void cancellationLetsTransactionRollbackBeforeInterruptingWorker() throws Exception {
        try (var fixture = new Fixture()) {
            var database = mock(IDatabaseService.class);
            var started = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            var rollback = new CompletableFuture<Boolean>();
            var config = new DatabaseTaskConfig(database, "事务回滚保护");
            config.addTransactionTask(() -> {
                started.countDown();
                if (!release.await(3, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("测试事务等待超时。");
                }
                TaskExecution.checkCurrent();
            });
            doAnswer(call -> {
                Collection<VoidCallable> actions = call.getArgument(0);
                try {
                    for (var action : actions) {
                        action.call();
                    }
                } catch (ExecutionStoppedException expected) {
                    // 在此模拟事务代理的回滚，不允许中断位破坏连接资源的清理。
                    rollback.complete(!Thread.currentThread().isInterrupted());
                    throw expected;
                }
                return null;
            }).when(database).__executeTaskWithTransaction(any());
            var key = UUID.randomUUID();
            var future = fixture.tasks.subscribeWithFuture(key, config.__getTaskConfig(), Void.class);
            try {
                assertTrue(started.await(3, TimeUnit.SECONDS));
                fixture.tasks.forceCompleteExceptionally(key, new TaskExpirationException("测试取消事务"));
                assertThrows(ExecutionException.class, () -> future.asStage().toCompletableFuture().get(3, TimeUnit.SECONDS));
                assertFalse(future.physicalCompletion().toCompletableFuture().isDone());
            } finally {
                release.countDown();
            }
            assertTrue(rollback.get(3, TimeUnit.SECONDS), "回滚阶段仍可使用原连接。");
            future.physicalCompletion().toCompletableFuture().get(3, TimeUnit.SECONDS);
            verify(database, never()).__executeTaskWithCache(any());
            assertEquals(0, fixture.tasks.inFlightCount());
        }
    }

    /**
     * 2026/11/07：明确失败仍可重试；调度拒绝必须同时收束等待和实际占用。
     *
     * @throws Exception 有界任务未能完成时
     */
    @Test
    void retriesKnownFailuresAndReclaimsRejectedScheduling() throws Exception {
        try (var fixture = new Fixture()) {
            var attempts = new AtomicInteger();
            var future = fixture.tasks.subscribeWithFuture(TaskConfig.<Void>builder().name("明确失败重试")
                .taskStages(List.of()).maxRetries(1).retryInterval(Duration.ZERO).callable(() -> {
                    if (attempts.incrementAndGet() == 1) {
                        throw new org.zexnocs.teanekocore.actuator.task.exception.TaskRetryRuntimeException("测试冲突");
                    }
                    return EmptyTaskResult.INSTANCE;
                }).build(), Void.class);
            assertTrue(future.asStage().toCompletableFuture().get(3, TimeUnit.SECONDS).isSuccess());
            future.physicalCompletion().toCompletableFuture().get(3, TimeUnit.SECONDS);
            assertEquals(2, attempts.get());
            assertEquals(0, fixture.tasks.inFlightCount());
            fixture.scheduler.shutdownNow();
            var rejected = fixture.tasks.subscribeWithFuture(TaskConfig.<Void>builder().name("调度拒绝")
                .taskStages(List.of()).callable(() -> EmptyTaskResult.INSTANCE).build(), Void.class);
            assertThrows(ExecutionException.class, () -> rejected.asStage().toCompletableFuture().get(3, TimeUnit.SECONDS));
            rejected.physicalCompletion().toCompletableFuture().get(3, TimeUnit.SECONDS);
            assertEquals(0, fixture.tasks.inFlightCount());
        }
    }

    /**
     * 2026/11/07：事件通知结束不能代替事件登记的异步业务收尾。
     *
     * @throws Exception 有界事件等待失败时
     */
    @Test
    void eventCompletionIncludesRegisteredBusinessWork() throws Exception {
        try (var fixture = new Fixture()) {
            var events = new org.zexnocs.teanekocore.event.EventService(mock(ILogger.class),
                mock(org.zexnocs.teanekocore.event.core.EventHandlerScanner.class), fixture.tasks);
            org.zexnocs.teanekocore.event.interfaces.IEvent<?> event = mock();
            var business = new CompletableFuture<Void>();
            when(event.processingCompletion()).thenReturn(business);
            var complete = events.pushEventWithCompletion(event).toCompletableFuture();
            fixture.submitted.get().getFuture().physicalCompletion().toCompletableFuture().get(3, TimeUnit.SECONDS);
            assertFalse(complete.isDone());
            business.completeExceptionally(new IllegalStateException("真实业务失败"));
            assertThrows(ExecutionException.class, () -> complete.get(3, TimeUnit.SECONDS));
        }
    }

    /**
     * 超时不能为忽略中断的 Callable 提前归还容量，也不能重试未知写入。
     *
     * @throws Exception 当有界测试未完成时
     */
    @Test
    void runningTimeoutKeepsCapacityUntilPhysicalExit() throws Exception {
        try (var fixture = new Fixture()) {
            var started = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            var attempts = new AtomicInteger();
            var config = TaskConfig.<Void>builder().name("不可撤回工作").taskStages(List.of())
                .expirationDuration(Duration.ofMillis(10)).maxRetries(5).callable(() -> {
                    attempts.incrementAndGet();
                    started.countDown();
                    boolean interrupted = false;
                    while (release.getCount() > 0) {
                        try {
                            if (!release.await(3, TimeUnit.SECONDS)) {
                                throw new IllegalStateException("测试工作等待超时。");
                            }
                        } catch (InterruptedException ignored) {
                            interrupted = true;
                        }
                    }
                    if (interrupted) {
                        Thread.currentThread().interrupt();
                    }
                    return EmptyTaskResult.INSTANCE;
                }).build();
            var waiting = fixture.tasks.subscribeWithFuture(config, Void.class);
            try {
                assertTrue(started.await(3, TimeUnit.SECONDS));
                fixture.cache.get().autoClean(Instant.now().plusSeconds(1));
                assertThrows(ExecutionException.class, () -> waiting.asStage().toCompletableFuture().get(3, TimeUnit.SECONDS));
                assertEquals(1, fixture.tasks.inFlightCount());
                assertThrows(ExecutionBusyException.class, () -> fixture.tasks.subscribeWithFuture(config, Void.class));
                assertFalse(waiting.physicalCompletion().toCompletableFuture().isDone());
            } finally {
                release.countDown();
            }
            waiting.physicalCompletion().toCompletableFuture().get(3, TimeUnit.SECONDS);
            assertEquals(0, fixture.tasks.inFlightCount());
            assertEquals(1, attempts.get());
            assertThrows(CompletionException.class, () -> waiting.asStage().toCompletableFuture().join());
        }
    }

    /**
     * 排队取消不执行原方法；返回空值后的外部未知结果则保留容量直至原回执到达。
     *
     * @throws Exception 当有界等待失败时
     */
    @Test
    void queuedAndExternalWorkHaveDifferentCompletionRules() throws Exception {
        try (var fixture = new Fixture()) {
            var invoked = new AtomicInteger();
            var key = UUID.randomUUID();
            var queued = fixture.tasks.subscribeWithFuture(key, TaskConfig.<Void>builder().name("排队测试")
                .taskStages(List.of()).delayDuration(Duration.ofMinutes(1)).callable(() -> {
                    invoked.incrementAndGet();
                    return EmptyTaskResult.INSTANCE;
                }).build(), Void.class);
            fixture.tasks.forceCompleteExceptionally(key, new TaskExpirationException("测试取消排队"));
            queued.physicalCompletion().toCompletableFuture().get(3, TimeUnit.SECONDS);
            assertEquals(0, fixture.tasks.inFlightCount());
            assertEquals(0, invoked.get());

            var externalKey = UUID.randomUUID();
            var external = fixture.tasks.subscribeWithFuture(externalKey, TaskConfig.<Void>builder().name("外部回执")
                .taskStages(List.of()).callable(() -> null).build(), Void.class);
            fixture.submitted.get().getExecutingFuture().get(3, TimeUnit.SECONDS);
            fixture.tasks.forceCompleteExceptionally(externalKey, new TaskExpirationException("测试结果未知"));
            assertEquals(1, fixture.tasks.inFlightCount());
            assertFalse(external.physicalCompletion().toCompletableFuture().isDone());
            assertFalse(fixture.tasks.complete(externalKey, EmptyTaskResult.INSTANCE));
            external.physicalCompletion().toCompletableFuture().get(3, TimeUnit.SECONDS);
            assertEquals(0, fixture.tasks.inFlightCount());
            fixture.tasks.close();
            assertThrows(ExecutionBusyException.class, () -> fixture.tasks.subscribeWithFuture(
                TaskConfig.<Void>builder().name("关闭后").callable(() -> EmptyTaskResult.INSTANCE).build(), Void.class));
        }
    }

    /**
     * 复用正式执行与重试服务，只替换缓存定时触发以避免等待墙钟。
     *
     * @author zExNocs
     * @date 2026/11/06
     * @since 4.8.7-l
     * @version 4.8.7-l
     */
    private static final class Fixture implements AutoCloseable {
        final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
        final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        final AtomicReference<ICacheContainer> cache = new AtomicReference<>();
        final AtomicReference<org.zexnocs.teanekocore.actuator.task.interfaces.ITask<?>> submitted = new AtomicReference<>();
        final TaskService tasks;

        /**
         * 构造隔离宿主，容量为一以观察真实占用。
         */
        Fixture() {
            var caches = mock(ICacheService.class);
            doAnswer(call -> {
                cache.set(call.getArgument(0));
                return null;
            }).when(caches).addCache(any());
            var executor = mock(ITaskExecuteService.class);
            var logger = mock(ILogger.class);
            tasks = new TaskService(caches, logger, executor, new TaskRetryService(executor));
            tasks.setMaxInFlight(1);
            var actual = new TaskExecuteService(workers, logger, mock(TaskStageScanner.class), tasks, scheduler);
            doAnswer(call -> {
                submitted.set(call.getArgument(0));
                actual.__executeTask(call.getArgument(0));
                return null;
            }).when(executor).__executeTask(any());
            doAnswer(call -> {
                actual.__executeTaskRetry(call.getArgument(0));
                return null;
            })
                .when(executor).__executeTaskRetry(any());
        }

        /**
         * 停止宿主后释放测试拥有的线程。
         */
        @Override
        public void close() {
            tasks.close();
            scheduler.shutdownNow();
            workers.close();
        }
    }
}
