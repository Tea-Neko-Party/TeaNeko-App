package org.zexnocs.teanekocore.api_response;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.actuator.execution.ExecutionPolicy;
import org.zexnocs.teanekocore.actuator.execution.ExecutionStoppedException;
import org.zexnocs.teanekocore.actuator.execution.ResourceBudget;
import org.zexnocs.teanekocore.api_response.api.APIRequestData;
import org.zexnocs.teanekocore.api_response.api.APIRequestParam;
import org.zexnocs.teanekocore.api_response.api.IAPIRequestData;
import org.zexnocs.teanekocore.api_response.api.IAPIResponseData;
import org.zexnocs.teanekocore.api_response.exception.APIRequestTimeoutException;
import org.zexnocs.teanekocore.cache.interfaces.ICacheService;
import org.zexnocs.teanekocore.logger.ILogger;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 用真实 WebClient 链和受控传输验证 HTTP 生命周期、取消、时限与资源释放。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
class APIResponseExecutionTest {
    /**
     * 含固定查询参数和请求头的 GET 测试请求。
     *
     * @param value 待处理的值
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
     * @version 4.5.0-l
     */
    @APIRequestData(baseUrl = "https://offline.test", path = "/lookup", timeoutInMillis = 10000, retryCount = 0)
    record Query(@APIRequestParam("q") String value) implements IAPIRequestData {
        /**
         * 返回本用例固定请求头，验证 HTTP 请求准备不丢失原协议参数。
         *
         * @return 按当前定位规则生成的映射；是否可修改遵循本方法说明
         */
        @Override
        public Map<String, String> headers() { return Map.of("X-Test", "request"); }
    }

    /**
     * 用于验证 JSON 正文传递的 POST 测试请求。
     *
     * @param value 待处理的值
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
     * @version 4.5.0-l
     */
    @APIRequestData(baseUrl = "https://offline.test", path = "/post", method = "POST", timeoutInMillis = 10000, retryCount = 0)
    record Post(String value) implements IAPIRequestData {}

    /**
     * 用于验证既有重试次数及阶段总时限的请求夹具。
     *
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
     * @version 4.5.0-l
     */
    @APIRequestData(baseUrl = "https://offline.test", path = "/retry", timeoutInMillis = 10000, retryCount = 2, retryDelayInMillis = 1000)
    record RetryQuery() implements IAPIRequestData {}

    /**
     * 用于实际响应反序列化的最小测试数据。
     *
     * @param value 待处理的值
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
     * @version 4.5.0-l
     */
    public record Reply(String value) implements IAPIResponseData {}

    /**
     * 验证成功请求在下游执行前解除计时器与终止监听。
     */
    @Test
    void successfulRequestReleasesTimerAndListenerBeforeDownstreamRuns() {
        var fixture = new Fixture(Schedulers.immediate());
        try (var control = ExecutionControl.create()) {
            var result = fixture.service.addTask(new Query("one"), Reply.class, true, true,
                    ExecutionPolicy.controlled(Duration.ofSeconds(3), control)).asStage().toCompletableFuture();
            assertFalse(result.isDone());
            result.thenAccept(reply -> { assertEquals(0, fixture.timer.pendingTasks()); control.cancel(); });
            fixture.respond("answer");
            assertEquals("answer", result.join().value());
            assertEquals(1, fixture.requests.size());
            assertEquals("/lookup", fixture.requests.getFirst().url().getPath());
            assertEquals("q=one", fixture.requests.getFirst().url().getQuery());
            assertEquals("request", fixture.requests.getFirst().headers().getFirst("X-Test"));
            assertFalse(fixture.timer.isDisposed());
        }
    }

    /**
     * 验证取消释放真实 HTTP 订阅并阻止迟到缓存写入。
     */
    @Test
    void cancellationDisposesActualExchangeAndPreventsLateCacheWrite() {
        var fixture = new Fixture(Schedulers.immediate());
        try (var control = ExecutionControl.create()) {
            var task = fixture.service.addTask(new Query("one"), Reply.class, false, false,
                    ExecutionPolicy.controlled(Duration.ofSeconds(3), control));
            control.cancel();
            assertStopped(task.asStage(), ExecutionControl.StopReason.CANCELLED);
            assertEquals(1, fixture.cancelled.get());
            assertEquals(0, fixture.timer.pendingTasks());
            fixture.respond("late");
            var next = fixture.service.addTask(new Query("one"), Reply.class, false, false).asStage().toCompletableFuture();
            assertEquals(2, fixture.requests.size());
            fixture.respond("fresh");
            assertEquals("fresh", next.join().value());
        }
    }

    /**
     * 验证阶段超时终止传输且不取消父控制。
     */
    @Test
    void stageTimeoutStopsTransportWithoutCancellingParentControl() {
        var fixture = new Fixture(Schedulers.immediate());
        try (var control = ExecutionControl.create()) {
            var result = fixture.service.addTask(new Post("payload"), Reply.class, true, true,
                    ExecutionPolicy.controlled(Duration.ofSeconds(2), control)).asStage();
            fixture.timer.advance(Duration.ofSeconds(2));
            assertInstanceOf(APIRequestTimeoutException.class, failure(result));
            assertEquals(1, fixture.cancelled.get());
            assertDoesNotThrow(control::checkActive);
            assertEquals("POST", fixture.requests.getFirst().method().name());
        }
    }

    /**
     * 验证父截止时间收束阶段时限且保留独立终止原因。
     */
    @Test
    void parentDeadlineShortensTheStageAndHasADistinctTerminationReason() {
        var fixture = new Fixture(Schedulers.immediate());
        try (var control = deadline(fixture.timer, Duration.ofSeconds(2))) {
            var result = fixture.service.addTask(new Query("one"), Reply.class, true, true,
                    ExecutionPolicy.controlled(Duration.ofSeconds(9), control)).asStage();
            fixture.timer.advance(Duration.ofMillis(1999));
            assertFalse(result.toCompletableFuture().isDone());
            fixture.timer.advance(Duration.ofMillis(1));
            assertStopped(result, ExecutionControl.StopReason.DEADLINE_EXCEEDED);
            assertEquals(1, fixture.cancelled.get());
            assertEquals(0, fixture.timer.pendingTasks());
        }
    }

    /**
     * 验证预先取消或到期的请求不会创建实际传输。
     */
    @Test
    void alreadyCancelledAndExpiredRequestsDoNotCreateTheExchange() {
        var fixture = new Fixture(Schedulers.immediate());
        try (var cancelled = ExecutionControl.create(); var expired = deadline(fixture.timer, Duration.ofSeconds(1))) {
            cancelled.cancel();
            assertStopped(fixture.service.addTask(new Query("one"), Reply.class, true, true,
                    ExecutionPolicy.controlled(null, cancelled)).asStage(), ExecutionControl.StopReason.CANCELLED);
            fixture.timer.moveClockWithoutRunning(Duration.ofSeconds(2));
            assertStopped(fixture.service.addTask(new Query("one"), Reply.class, true, true,
                    ExecutionPolicy.controlled(null, expired)).asStage(), ExecutionControl.StopReason.DEADLINE_EXCEEDED);
            assertEquals(0, fixture.requests.size());
        }
    }

    /**
     * 验证排队请求在任何 HTTP 工作开始前即可取消。
     */
    @Test
    void queuedRequestCanBeCancelledBeforeAnyHttpWork() {
        var queue = new ManualScheduler();
        var fixture = new Fixture(queue);
        try (var control = ExecutionControl.create()) {
            var result = fixture.service.addTask(new Query("one"), Reply.class, true, true,
                    ExecutionPolicy.controlled(Duration.ofSeconds(3), control)).asStage();
            assertTrue(fixture.requests.isEmpty());
            control.cancel();
            queue.advance(Duration.ZERO);
            assertStopped(result, ExecutionControl.StopReason.CANCELLED);
            assertTrue(fixture.requests.isEmpty());
        }
    }

    /**
     * 验证阶段时限包括 API 调度器上的排队等待。
     */
    @Test
    void timeoutIncludesTimeSpentWaitingOnTheApiScheduler() {
        var queue = new ManualScheduler();
        var fixture = new Fixture(queue);
        var result = fixture.service.addTask(new Query("one"), Reply.class, true, true,
                ExecutionPolicy.withTimeout(Duration.ofSeconds(1))).asStage();
        fixture.timer.advance(Duration.ofSeconds(1));
        queue.advance(Duration.ZERO);
        assertInstanceOf(APIRequestTimeoutException.class, failure(result));
        assertTrue(fixture.requests.isEmpty());
    }

    /**
     * 验证计时回调延迟时，迟到响应也不能覆盖已到期阶段。
     */
    @Test
    void lateResponseCannotBeatAnExpiredStageEvenIfTimerCallbackIsDelayed() {
        var fixture = new Fixture(Schedulers.immediate());
        var result = fixture.service.addTask(new Query("one"), Reply.class, true, true,
                ExecutionPolicy.withTimeout(Duration.ofSeconds(1))).asStage();
        fixture.timer.moveClockWithoutRunning(Duration.ofSeconds(2));
        fixture.respond("late");
        assertInstanceOf(APIRequestTimeoutException.class, failure(result));
        assertEquals(0, fixture.timer.pendingTasks());
    }

    /**
     * 验证创建传输时取消仍能释放稍后到达的订阅。
     */
    @Test
    void cancellationDuringExchangeCreationStillCancelsTheLateSubscription() {
        var fixture = new Fixture(Schedulers.immediate());
        try (var control = ExecutionControl.create()) {
            fixture.beforeSubscribe = control::cancel;
            var result = fixture.service.addTask(new Query("one"), Reply.class, true, true,
                    ExecutionPolicy.controlled(Duration.ofSeconds(3), control)).asStage();
            assertStopped(result, ExecutionControl.StopReason.CANCELLED);
            assertEquals(1, fixture.cancelled.get());
        }
    }

    /**
     * 验证取消根 Future 释放 HTTP 订阅但不停止共享控制。
     */
    @Test
    void rootFutureCancellationDisposesExchangeWithoutStoppingSharedControl() {
        var fixture = new Fixture(Schedulers.immediate());
        try (var control = ExecutionControl.create()) {
            var task = fixture.service.addTask(new Query("one"), Reply.class, true, true,
                    ExecutionPolicy.controlled(Duration.ofSeconds(3), control));
            task.finish(ignored -> {}).cancel(false);
            assertEquals(1, fixture.cancelled.get());
            assertEquals(0, fixture.timer.pendingTasks());
            assertDoesNotThrow(control::checkActive);
        }
    }

    /**
     * 验证空正文与传输异常均结束任务并释放计时器。
     */
    @Test
    void emptyBodyAndTransportFailureBothEndTheFutureAndReleaseTimer() {
        var fixture = new Fixture(Schedulers.immediate());
        var empty = fixture.service.addTask(new Query("empty"), Reply.class, true, true,
                ExecutionPolicy.withTimeout(Duration.ofSeconds(3))).asStage();
        fixture.sink.get().success(ClientResponse.create(HttpStatus.NO_CONTENT).build());
        assertEquals("API response body is empty.", failure(empty).getMessage());
        var failed = fixture.service.addTask(new Query("fail"), Reply.class, true, true,
                ExecutionPolicy.withTimeout(Duration.ofSeconds(3))).asStage();
        fixture.sink.get().error(new IllegalStateException("fake-transport-failure"));
        assertTrue(failure(failed).getMessage().contains("fake-transport-failure"));
        assertEquals(0, fixture.timer.pendingTasks());
        assertEquals(2, fixture.requests.size()); // retryCount=0 不重放。
    }

    /**
     * 验证缓存命中仍检查控制状态且不遗留计时器。
     */
    @Test
    void cacheHitStillChecksControlAndDoesNotLeaveTimerBehind() {
        var fixture = new Fixture(Schedulers.immediate());
        var first = fixture.service.addTask(new Query("one"), Reply.class).asStage();
        fixture.respond("cached");
        assertEquals("cached", first.toCompletableFuture().join().value());
        var cached = fixture.service.addTask(new Query("one"), Reply.class, false, false,
                ExecutionPolicy.withTimeout(Duration.ofSeconds(3))).asStage();
        assertEquals("cached", cached.toCompletableFuture().join().value());
        assertEquals(0, fixture.timer.pendingTasks());
        try (var control = ExecutionControl.create()) {
            control.cancel();
            assertStopped(fixture.service.addTask(new Query("one"), Reply.class, false, false,
                    ExecutionPolicy.controlled(null, control)).asStage(), ExecutionControl.StopReason.CANCELLED);
        }
        assertEquals(1, fixture.requests.size());
    }

    /**
     * 验证显式阶段时限包含重试等待。
     */
    @Test
    void explicitStageTimeoutCoversRetryDelays() {
        var fixture = new Fixture(Schedulers.immediate());
        fixture.failImmediately = true;
        var result = fixture.service.addTask(new RetryQuery(), Reply.class, true, true,
                ExecutionPolicy.withTimeout(Duration.ofMillis(1500))).asStage();
        fixture.timer.advance(Duration.ofMillis(1000));
        assertEquals(2, fixture.requests.size());
        fixture.timer.advance(Duration.ofMillis(500));
        assertInstanceOf(APIRequestTimeoutException.class, failure(result));
        fixture.timer.advance(Duration.ofSeconds(5));
        assertEquals(2, fixture.requests.size());
    }

    /**
     * 验证显式时限覆盖注解，默认入口继续遵守注解配置。
     */
    @Test
    void explicitTimeoutCanOverrideTheAnnotationAndDefaultsStillUseIt() {
        var fixture = new Fixture(Schedulers.immediate());
        var longer = fixture.service.addTask(new Query("longer"), Reply.class, true, true,
                ExecutionPolicy.withTimeout(Duration.ofSeconds(15))).asStage();
        fixture.timer.advance(Duration.ofSeconds(10));
        assertFalse(longer.toCompletableFuture().isDone());
        fixture.timer.advance(Duration.ofSeconds(5));
        assertInstanceOf(APIRequestTimeoutException.class, failure(longer));
        var defaults = fixture.service.addTask(new Query("default"), Reply.class, true, true,
                ExecutionPolicy.defaults()).asStage();
        fixture.timer.advance(Duration.ofSeconds(10));
        assertInstanceOf(APIRequestTimeoutException.class, failure(defaults));
    }

    /**
     * 验证取消单个请求不影响其他在途请求。
     */
    @Test
    void cancellingOneExecutionDoesNotCancelAnotherInFlightRequest() {
        var fixture = new Fixture(Schedulers.immediate());
        try (var first = ExecutionControl.create(); var second = ExecutionControl.create()) {
            var a = fixture.service.addTask(new Query("a"), Reply.class, true, true,
                    ExecutionPolicy.controlled(Duration.ofSeconds(3), first)).asStage();
            var b = fixture.service.addTask(new Query("b"), Reply.class, true, true,
                    ExecutionPolicy.controlled(Duration.ofSeconds(3), second)).asStage();
            first.cancel();
            assertStopped(a, ExecutionControl.StopReason.CANCELLED);
            assertFalse(b.toCompletableFuture().isDone());
            fixture.respond("b-answer");
            assertEquals("b-answer", b.toCompletableFuture().join().value());
            assertEquals(1, fixture.cancelled.get());
            assertEquals(0, fixture.timer.pendingTasks());
        }
    }

    /**
     * 验证响应与取消并发竞争时只接受一个结果并释放资源。
     *
     * @throws Exception 当底层业务、反射调用或结果转换失败时
     */
    @Test
    void simultaneousResponseAndCancellationChooseOneResultAndReleaseResources() throws Exception {
        try (var threads = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int iteration = 0; iteration < 30; iteration++) {
                var fixture = new Fixture(Schedulers.immediate());
                try (var control = ExecutionControl.create()) {
                    var result = fixture.service.addTask(new Query("race"), Reply.class, true, true,
                            ExecutionPolicy.controlled(Duration.ofSeconds(3), control)).asStage().toCompletableFuture();
                    var start = new CountDownLatch(1);
                    var response = threads.submit(() -> { start.await(); fixture.respond("answer"); return null; });
                    var cancellation = threads.submit(() -> { start.await(); control.cancel(); return null; });
                    start.countDown();
                    response.get(3, TimeUnit.SECONDS);
                    cancellation.get(3, TimeUnit.SECONDS);
                    assertTrue(result.isDone());
                    if (result.isCompletedExceptionally()) {
                        assertStopped(result, ExecutionControl.StopReason.CANCELLED);
                    } else {
                        assertEquals("answer", result.join().value());
                    }
                    assertEquals(0, fixture.timer.pendingTasks());
                }
            }
        }
    }

    /**
     * 验证旧 HTTP 入口保留既有重试次数和单次尝试超时。
     */
    @Test
    void legacyEntryRetainsConfiguredRetryCountAndPerAttemptTimeout() {
        var fixture = new Fixture(Schedulers.immediate());
        fixture.failImmediately = true;
        var result = fixture.service.addTask(new RetryQuery(), Reply.class, true, true).asStage();
        fixture.timer.advance(Duration.ofSeconds(2));
        assertEquals(3, fixture.requests.size());
        assertTrue(result.toCompletableFuture().isCompletedExceptionally());
        assertEquals(0, fixture.timer.pendingTasks());
    }

    /**
     * 提取已经异常结束的受控阶段根因，便于断言原异常是否保留。
     *
     * @param stage 需要观察或组合的完成阶段
     * @return 满足本方法约束的 {@code Throwable} 结果
     */
    private static Throwable failure(CompletionStage<?> stage) {
        return assertThrows(CompletionException.class, () -> stage.toCompletableFuture().join()).getCause();
    }

    /**
     * 断言阶段以指定控制停止原因结束，避免把取消或 deadline 混同普通失败。
     *
     * @param stage 需要观察或组合的完成阶段
     * @param expected 约定的预期状态或版本
     */
    private static void assertStopped(CompletionStage<?> stage, ExecutionControl.StopReason expected) {
        assertEquals(expected, assertInstanceOf(ExecutionStoppedException.class, failure(stage)).reason());
    }

    /**
     * 用手动时钟与受控调度构造截止时间控制，无需实际等待。
     *
     * @param timer 阶段计时使用的借用调度器
     * @param duration 需要换算或推进的时长
     * @return 独立的执行控制，预算与资源归属遵循创建入口说明
     */
    private static ExecutionControl deadline(ManualScheduler timer, Duration duration) {
        var clock = new Clock() {
            /**
             * 提供当前受控测试时钟的时区。
             *
             * @return 满足本方法约束的 {@code ZoneId} 结果
             */
            @Override public ZoneId getZone() { return ZoneOffset.UTC; }
            /**
             * 按 Clock 契约处理时区转换，保持本用例受控时间来源。
             *
             * @param zone 时钟转换目标时区
             * @return 满足本方法约束的 {@code Clock} 结果
             */
            @Override public Clock withZone(ZoneId zone) { return this; }
            /**
             * 提供由用例手动推进的当前时刻，不依赖真实等待。
             *
             * @return 满足本方法约束的 {@code Instant} 结果
             */
            @Override public Instant instant() { return Instant.EPOCH.plusNanos(timer.now(TimeUnit.NANOSECONDS)); }
        };
        var scheduler = mock(ScheduledExecutorService.class);
        when(scheduler.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class))).thenAnswer(invocation -> {
            var task = timer.schedule(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2));
            var future = mock(ScheduledFuture.class);
            when(future.cancel(false)).thenAnswer(ignored -> { task.dispose(); return true; });
            return future;
        });
        return ExecutionControl.withDeadline(new ResourceBudget(Map.of()), clock.instant().plus(duration), clock, scheduler);
    }

    /**
     * 组合真实 HTTP 服务、手动调度器与受控交换函数的离线夹具。
     *
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
     * @version 4.5.0-l
     */
    static final class Fixture {
        final ManualScheduler timer = new ManualScheduler();
        final List<ClientRequest> requests = new ArrayList<>();
        final AtomicReference<MonoSink<ClientResponse>> sink = new AtomicReference<>();
        final AtomicInteger cancelled = new AtomicInteger();
        final APIResponseService service;
        Runnable beforeSubscribe = () -> {};
        boolean failImmediately;

        /**
         * 通过构造参数固定当前实例的依赖与初始状态，避免执行时再从全局环境查找协作者。
         *
         * @param apiScheduler HTTP 工作使用的借用调度器
         */
        Fixture(Scheduler apiScheduler) {
            service = new APIResponseService(apiScheduler, timer, mock(ICacheService.class), mock(ILogger.class), url -> WebClient.builder()
                    .baseUrl(url).exchangeFunction(request -> {
                        requests.add(request);
                        beforeSubscribe.run();
                        return Mono.create(current -> {
                            sink.set(current);
                            current.onCancel(cancelled::incrementAndGet);
                            if (failImmediately) { current.error(new IllegalStateException("fake-transport-failure")); }
                        });
                    }).build());
        }

        /**
         * 由测试主动提交响应正文，推进真实 WebClient 操作符链而不访问网络。
         *
         * @param value 待处理的值
         */
        void respond(String value) {
            respondJson("{\"value\":\"" + value + "\"}");
        }

        /**
         * 提交固定 JSON 响应，供实际反序列化与资源清理用例使用。
         *
         * @param json 用于受控响应的固定 JSON 正文
         */
        void respondJson(String json) {
            sink.get().success(ClientResponse.create(HttpStatus.OK).header("Content-Type", "application/json")
                    .body(json).build());
        }
    }
}
