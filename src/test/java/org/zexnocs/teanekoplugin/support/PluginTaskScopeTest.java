package org.zexnocs.teanekoplugin.support;

import org.junit.jupiter.api.Test;
import org.zexnocs.teanekocore.actuator.timer.interfaces.ITimerTaskConfig;
import org.zexnocs.teanekocore.framework.lifescycle.ILivable;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/**
 * 验证热停用等待实际工作、撤销定时器，以及重新启用不复活旧代回调。
 * <br>2026/11/07：覆盖停用与排队工作之间的真实并发边界。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
class PluginTaskScopeTest {
    /**
     * 停用完成信号须等待已经开始的工作，未来排队回调立即拒绝。
     * @throws Exception 当有界等待或测试业务失败时
     */
    @Test
    void stopWaitsForRealCompletionAndRejectsQueuedWork() throws Exception {
        var scope = new PluginTaskScope();
        var entered = new CompletableFuture<Void>();
        var release = new CompletableFuture<Void>();
        try (var executor = Executors.newSingleThreadExecutor()) {
            var running = executor.submit(() -> scope.execute(() -> {
                entered.complete(null);
                release.get(3, TimeUnit.SECONDS);
                return "完成";
            }));
            try {
                entered.get(3, TimeUnit.SECONDS);
                var stopped = scope.stop().toCompletableFuture();
                assertFalse(stopped.isDone());
                assertThrows(CancellationException.class, () -> scope.execute(() -> "不得启动"));
                release.complete(null);
                assertEquals("完成", running.get(3, TimeUnit.SECONDS));
                stopped.get(3, TimeUnit.SECONDS);
            } finally {
                release.complete(null);
                scope.stop().toCompletableFuture().get(3, TimeUnit.SECONDS);
            }
        }
    }

    /**
     * 新代可独立运行，旧代定时器和迟到工作保持不可用。
     * @throws Exception 当独立业务执行失败时
     */
    @Test
    void reenableDoesNotRevivePreviousGeneration() throws Exception {
        var previous = new PluginTaskScope();
        var livable = new AtomicReference<ILivable>();
        ITimerTaskConfig<?> timer = mock(ITimerTaskConfig.class);
        doAnswer(invocation -> {
            livable.set(invocation.getArgument(0));
            return null;
        }).when(timer).setLivable(any());
        previous.own(timer);
        assertTrue(livable.get().isAlive());
        previous.stop().toCompletableFuture().get(3, TimeUnit.SECONDS);
        var current = new PluginTaskScope();
        try {
            assertFalse(livable.get().isAlive());
            assertThrows(CancellationException.class, () -> previous.execute(() -> "旧结果"));
            assertEquals("新结果", current.execute(() -> "新结果"));
        } finally {
            current.stop().toCompletableFuture().get(3, TimeUnit.SECONDS);
        }
    }

    /**
     * 2026/11/07：调用方取消等待副本后，停用仍追踪实际工作且在真实完成时正常释放。
     * @throws Exception 当有界等待失败时
     */
    @Test
    void cancellingPublicWaitCannotCancelPhysicalCompletionObserver() throws Exception {
        var scope = new PluginTaskScope();
        var physical = new CompletableFuture<String>();
        var waiting = scope.executeAsync(() -> physical).toCompletableFuture();
        waiting.cancel(false);
        var stopped = scope.stop().toCompletableFuture();
        try {
            assertFalse(physical.isDone());
            assertFalse(stopped.isDone());
        } finally {
            physical.complete("实际完成");
            stopped.get(3, TimeUnit.SECONDS);
        }
        assertTrue(waiting.isCancelled());
    }
}
