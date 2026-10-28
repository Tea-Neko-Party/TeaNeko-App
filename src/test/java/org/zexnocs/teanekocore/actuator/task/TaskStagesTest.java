package org.zexnocs.teanekocore.actuator.task;

import org.junit.jupiter.api.Test;
import org.zexnocs.teanekocore.actuator.task.exception.TaskUnsuccessfulException;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskResult;
import org.zexnocs.teanekocore.logger.ILogger;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 验证最终任务结果展开、失败传播及只读阶段视图。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
class TaskStagesTest {
    private final ILogger logger = mock(ILogger.class);

    /**
     * 验证适配器等待最终任务结果并返回业务值。
     */
    @Test
    void waitsForFinalTaskResultAndReturnsItsValue() {
        var source = new CompletableFuture<ITaskResult<String>>();
        var stage = TaskStages.successful(new TaskFuture<>(logger, "task", source), "operation");
        assertFalse(stage.toCompletableFuture().isDone());

        source.complete(new TaskResult<>(true, "stored"));

        assertEquals("stored", stage.toCompletableFuture().join());
        verifyNoInteractions(logger);
    }

    /**
     * 验证失败任务即使附带值也异常完成。
     */
    @Test
    void unsuccessfulResultIsExceptionalEvenWhenItContainsAValue() {
        var source = CompletableFuture.<ITaskResult<String>>completedFuture(new TaskResult<>(false, "partial"));
        var stage = TaskStages.successful(new TaskFuture<>(logger, "task", source), "operation");

        var error = assertThrows(CompletionException.class, () -> stage.toCompletableFuture().join());

        assertInstanceOf(TaskUnsuccessfulException.class, error.getCause());
        verifyNoInteractions(logger);
    }

    /**
     * 验证原始失败到达调用者且不自动记录终端日志。
     */
    @Test
    void originalFailureReachesCallerWithoutTerminalLogging() {
        var failure = new IllegalStateException("database failed");
        var source = new CompletableFuture<ITaskResult<Void>>();
        var stage = TaskStages.successful(new TaskFuture<>(logger, "task", source), "operation");

        source.completeExceptionally(failure);

        var error = assertThrows(CompletionException.class, () -> stage.toCompletableFuture().join());
        assertSame(failure, error.getCause());
        verifyNoInteractions(logger);
    }

    /**
     * 验证成功 Void 可以为空，但任务结果信封不能缺失。
     */
    @Test
    void successfulVoidResultAllowsNullValueButNotNullResultEnvelope() {
        assertNull(TaskStages.requireSuccess(EmptyTaskResult.INSTANCE, "void task"));
        assertThrows(NullPointerException.class, () -> TaskStages.requireSuccess(null, "broken task"));
    }

    /**
     * 验证完成或取消导出视图不改变源任务。
     */
    @Test
    void completingOrCancellingExportedViewDoesNotCompleteSourceTask() {
        var source = new CompletableFuture<ITaskResult<Integer>>();
        var stage = TaskStages.successful(new TaskFuture<>(logger, "task", source), "operation");

        stage.toCompletableFuture().complete(999);
        stage.toCompletableFuture().cancel(false);

        assertFalse(source.isDone());
        source.complete(new TaskResult<>(true, 42));
        assertEquals(42, stage.toCompletableFuture().join());
    }
}
