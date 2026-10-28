package org.zexnocs.teanekocore.api_response;

import org.junit.jupiter.api.Test;
import org.zexnocs.teanekocore.actuator.execution.ExecutionPolicy;
import reactor.core.Disposable;
import reactor.core.scheduler.Scheduler;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 验证清理失败在不同完成路径中有明确归属，不丢弃异常或重复完成根任务。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
class APIRequestCleanupTest {
    private final Scheduler timer = mock(Scheduler.class);
    private final CompletableFuture<String> future = new CompletableFuture<>();
    private final RuntimeException cleanup = new IllegalStateException("离线清理失败");
    private final AtomicReference<RuntimeException> diagnostic = new AtomicReference<>();
    private final AtomicInteger reports = new AtomicInteger();

    /**
     * 创建一个在释放计时器时抛错的真实请求仲裁器，不运行 HTTP 或时钟线程。
     * @return 当前用例的生命周期仲裁器
     */
    private APIRequestExecution<String> execution() {
        var disposable = mock(Disposable.class);
        doThrow(cleanup).when(disposable).dispose();
        when(timer.schedule(any(Runnable.class), anyLong(), eq(TimeUnit.NANOSECONDS))).thenReturn(disposable);
        return new APIRequestExecution<>(ExecutionPolicy.defaults(), Duration.ofSeconds(1), timer, future, true,
                failure -> { diagnostic.set(failure); reports.incrementAndGet(); });
    }

    /** 验证成功响应必须先完成清理；清理失败不会执行成功回调。 */
    @Test void successCannotHideCleanupFailure() {
        var execution = execution();
        execution.succeed("正文", ignored -> fail("清理失败不应交付正文。"));
        assertSame(cleanup, assertThrows(CompletionException.class, future::join).getCause());
        assertNull(diagnostic.get());
    }

    /** 验证原始执行失败保持为主异常，清理异常作为附加诊断保留。 */
    @Test void failureRetainsCleanupAsSuppressed() {
        var execution = execution();
        var original = new IllegalArgumentException("离线执行失败");
        execution.fail(original);
        assertSame(original, assertThrows(CompletionException.class, future::join).getCause());
        assertArrayEquals(new Throwable[]{cleanup}, original.getSuppressed());
        assertNull(diagnostic.get());
    }

    /** 验证外部取消后的清理异常交给诊断端口，后续完成不会重复报告或改写取消。 */
    @Test void externalCancellationReportsCleanupExactlyOnce() {
        var execution = execution();
        assertTrue(future.cancel(false));
        assertSame(cleanup, diagnostic.get());
        execution.fail(new IllegalStateException("迟到失败"));
        execution.succeed("迟到正文", ignored -> fail("取消后不应交付正文。"));
        assertTrue(future.isCancelled());
        assertEquals(1, reports.get());
    }

    /** 验证调用方直接完成根任务时仍报告资源错误，保持已经交付的外部结果。 */
    @Test void externallyCompletedResultStillReportsCleanup() {
        execution();
        assertTrue(future.complete("调用方结果"));
        assertEquals("调用方结果", future.join());
        assertSame(cleanup, diagnostic.get());
        assertEquals(1, reports.get());
    }
}
