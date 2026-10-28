package org.zexnocs.teanekocore.actuator.task;

import org.junit.jupiter.api.Test;
import org.zexnocs.teanekocore.logger.ILogger;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 验证 TaskFuture 与任意 CompletionStage 的非阻塞桥接和根任务保护。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
class TaskFutureStageTest {
    /**
     * 验证导出的阶段不能完成或取消根任务。
     */
    @Test
    void exportedStageCannotCompleteOrCancelRootTask() {
        var logger = mock(ILogger.class);
        var source = new CompletableFuture<String>();
        var task = new TaskFuture<>(logger, "read-only stage", source);

        task.asStage().toCompletableFuture().complete("forged");
        task.asStage().toCompletableFuture().cancel(false);

        assertFalse(task.isDone());
        source.complete("actual result");
        assertEquals("actual result", task.asStage().toCompletableFuture().join());
        verifyNoInteractions(logger);
    }

    /**
     * 验证导入阶段保持异步且可接入既有任务链。
     */
    @Test
    void importedStageRemainsAsynchronousAndSupportsExistingTaskChains() {
        var logger = mock(ILogger.class);
        var source = new CompletableFuture<Integer>();
        var task = TaskFuture.fromStage(logger, "imported stage", source).thenApply(value -> value + 1);
        assertFalse(task.isDone());

        source.complete(41);

        assertEquals(42, task.asStage().toCompletableFuture().join());
        verifyNoInteractions(logger);
    }

    /**
     * 验证阶段桥接传播异步异常且不额外记录链尾日志。
     */
    @Test
    void asynchronousFailurePropagatesWithoutAddingTerminalLogging() {
        var logger = mock(ILogger.class);
        var source = new CompletableFuture<String>();
        var task = TaskFuture.fromStage(logger, "failed stage", source);
        var expected = new IllegalStateException("business failure");

        source.completeExceptionally(expected);

        var failure = assertThrows(CompletionException.class, () -> task.asStage().toCompletableFuture().join());
        assertSame(expected, failure.getCause());
        verifyNoInteractions(logger);
    }

    /**
     * 验证组合操作等待任意 CompletionStage 的实际结果。
     */
    @Test
    void compositionWaitsForAnArbitraryCompletionStage() {
        var logger = mock(ILogger.class);
        var continuation = new CompletableFuture<String>();
        var task = new TaskFuture<>(logger, "stage composition", CompletableFuture.completedFuture(1))
                .thenCompose(ignored -> continuation.minimalCompletionStage());

        assertFalse(task.isDone());
        continuation.complete("completed asynchronously");
        assertEquals("completed asynchronously", task.asStage().toCompletableFuture().join());
        verifyNoInteractions(logger);
    }
}
