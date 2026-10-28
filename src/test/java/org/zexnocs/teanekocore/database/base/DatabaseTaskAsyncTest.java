package org.zexnocs.teanekocore.database.base;

import org.junit.jupiter.api.Test;
import org.zexnocs.teanekocore.actuator.task.EmptyTaskResult;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.actuator.task.TaskResult;
import org.zexnocs.teanekocore.actuator.task.exception.TaskUnsuccessfulException;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskResult;
import org.zexnocs.teanekocore.database.base.exception.DatabaseTaskRepeatedSubmissionException;
import org.zexnocs.teanekocore.database.base.interfaces.IDatabaseService;
import org.zexnocs.teanekocore.logger.ILogger;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 验证数据库异步提交等待最终事务及缓存处理结果。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
class DatabaseTaskAsyncTest {
    private final IDatabaseService database = mock(IDatabaseService.class);
    private final ILogger logger = mock(ILogger.class);
    private final CompletableFuture<ITaskResult<Void>> source = new CompletableFuture<>();
    private final DatabaseTaskConfig task = new DatabaseTaskConfig(database, "async database write");

    /**
     * 在每个用例中重新建立受控数据库任务与根 Future，避免提交状态相互污染。
     */
    private void prepare() {
        when(database.__pushTask(task)).thenReturn(new TaskFuture<>(logger, task.getName(), source));
    }

    /**
     * 验证异步提交等待数据库任务且只提交一次。
     */
    @Test
    void waitsForDatabaseFutureAndSubmitsExactlyOnce() {
        prepare();
        var stage = task.pushAsync();
        assertFalse(stage.toCompletableFuture().isDone());
        verify(database).__pushTask(task);

        source.complete(EmptyTaskResult.INSTANCE);

        assertNull(stage.toCompletableFuture().join());
        verifyNoMoreInteractions(database);
        verifyNoInteractions(logger);
    }

    /**
     * 验证后续链只在事务与缓存处理完成后运行。
     *
     * @throws Exception 当底层业务、反射调用或结果转换失败时
     */
    @Test
    void transactionAndCacheAreCompleteBeforeContinuationRuns() throws Exception {
        var observed = new StringBuilder();
        task.addTransactionTask(() -> observed.append("transaction;"));
        task.addCacheTask(() -> observed.append("cache;"));
        doAnswer(invocation -> {
            for (var operation : task.__getTasksWithTransaction()) operation.call();
            return null;
        }).when(database).__executeTaskWithTransaction(any());
        doAnswer(invocation -> {
            for (var operation : task.__getTasksWithCache()) operation.call();
            return null;
        }).when(database).__executeTaskWithCache(any());
        when(database.__pushTask(task)).thenReturn(new TaskFuture<>(logger, task.getName(), source));
        var stage = task.pushAsync().thenRun(() -> observed.append("continuation"));

        source.complete(task.__getTaskConfig().getCallable().call());

        stage.toCompletableFuture().join();
        assertEquals("transaction;cache;continuation", observed.toString());
    }

    /**
     * 验证最终失败状态和异常不能变成成功的 Void。
     */
    @Test
    void finalFailureAndExceptionDoNotBecomeSuccessfulVoidStages() {
        prepare();
        var stage = task.pushAsync();
        source.complete(new TaskResult<>(false, null));

        var error = assertThrows(CompletionException.class, () -> stage.toCompletableFuture().join());
        assertInstanceOf(TaskUnsuccessfulException.class, error.getCause());

        var otherTask = new DatabaseTaskConfig(database, "failed transaction");
        var failure = new IllegalArgumentException("transaction failed");
        when(database.__pushTask(otherTask)).thenReturn(new TaskFuture<>(logger, "failed",
                CompletableFuture.failedFuture(failure)));
        var failed = otherTask.pushAsync();
        assertSame(failure, assertThrows(CompletionException.class,
                () -> failed.toCompletableFuture().join()).getCause());
    }

    /**
     * 验证异步 push 保留单次提交与提交后修改保护。
     */
    @Test
    void asyncPushRetainsSingleSubmissionAndMutationProtection() {
        prepare();
        task.pushAsync();

        assertThrows(DatabaseTaskRepeatedSubmissionException.class, task::pushAsync);
        assertThrows(DatabaseTaskRepeatedSubmissionException.class, task::pushWithFuture);
        assertThrows(DatabaseTaskRepeatedSubmissionException.class, task::push);
        assertThrows(DatabaseTaskRepeatedSubmissionException.class, () -> task.addCacheTask(() -> {}));
        verify(database, times(1)).__pushTask(task);
    }
}
