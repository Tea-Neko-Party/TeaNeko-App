package org.zexnocs.teanekocore.actuator.task;

import lombok.AccessLevel;
import lombok.Getter;
import org.zexnocs.teanekocore.actuator.task.exception.TaskNoRetryRuntimeException;
import org.zexnocs.teanekocore.actuator.task.exception.TaskRetryRuntimeException;
import org.zexnocs.teanekocore.logger.ILogger;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 用来管理 Task 执行结果的 Future 对象。
 * 主要用于注册该 Task，并管理其执行链以及最后是否以异常结束。
 * 请结束后手动调用 finish() 方法来处理异常
 *
 * @author zExNocs
 * @date 2026/02/11
 */
public class TaskFuture<T> {
    /// 共享资源
    private final SharedState state;

    /// Future 对象
    @Getter(AccessLevel.PACKAGE)
    private final CompletableFuture<T> future;

    /// 标记 Future 是否已经完成，防止重复调用 finish() 方法。
    private final AtomicBoolean finished = new AtomicBoolean(false);

    /**
     * 构造函数，用于初始化 Future 对象。
     * 是 root Future
     */
    public TaskFuture(ILogger logger, String taskName, CompletableFuture<T> future) {
        this.future = future;
        this.state = new SharedState(logger, taskName);
    }

    /**
     * 构造函数，用于初始化 Future 对象。
     * @param future Future 对象
     */
    private TaskFuture(CompletableFuture<T> future, SharedState state) {
        this.future = future;
        this.state = state;
    }

    // --------- 共享资源管理部分 ---------

    /// 共享的状态对象，用于在 Future 链中共享日志记录和任务名称等信息。
    ///
    /// @param logger   日志
    /// @param taskName 任务名称
    private record SharedState(ILogger logger, String taskName) {}

    /**
     * 手动在 Future 结束时调用，用于处理异常日志等操作。
     * 如果 Future 以异常结束，则会记录错误日志。
     */
    public CompletableFuture<T> finish() {
        // 判断是否已经完成，防止重复调用 finish() 方法。
        if (!finished.compareAndSet(false, true)) {
            return future;
        }

        future.whenComplete((r, t) -> {
            if(t != null) {
                // 解包异常，获取实际的异常信息
                t = unwrapException(t);
                state.logger.errorWithReport(
                        TaskFuture.class.getSimpleName(),
                        "执行任务：%s 时发生未处理的异常。".formatted(state.taskName),
                        t
                );
            }
        });
        return future;
    }

    /**
     * 手动指定异常处理的 finish 方法，用于自定义异常处理逻辑。
     * 如果 Future 以异常结束，则会调用 exceptionHandler 来处理异常。
     * @param exceptionHandler 异常处理函数，接受一个 Throwable 参数。
     * @return Future 对象
     */
    public CompletableFuture<T> finish(Consumer<Throwable> exceptionHandler) {
        // 判断是否已经完成，防止重复调用 finish() 方法。
        if (!finished.compareAndSet(false, true)) {
            return future;
        }

        future.whenComplete((r, t) -> {
            if (t != null) {
                // 解包异常，获取实际的异常信息
                t = unwrapException(t);
                exceptionHandler.accept(t);
            }
        });
        return future;
    }

    // --------- Future 管理部分 ---------
    /**
     * 是否已经完成 Future 的执行。
     * @return 是否已经完成 Future 的执行
     */
    public boolean isDone() {
        return future.isDone();
    }

    /**
     * 提交 Task 的执行结果。
     * @param result Task 的执行结果
     * @return 是否成功提交结果，若 Task 已经完成或以异常结束，则返回 false
     */
    public boolean complete(T result) {
        return future.complete(result);
    }

    /**
     * 以异常结束 Future 的执行。
     * @param ex 结束 Task 的异常
     * @return 是否成功以异常结束 Task 的执行，若 Task 已经完成或以异常结束，则返回 false
     */
    public boolean completeExceptionally(Throwable ex) {
        return future.completeExceptionally(ex);
    }

    /// 包装的 thenApply 方法，用于在 Future 上注册回调函数，并返回新的 TaskFuture 对象。
    public <U> TaskFuture<U> thenApply(Function<? super T, ? extends U> fn) {
        return new TaskFuture<>(future.thenApply(fn), state);
    }

    /// 包装的 thenCompose 方法，用于在 Future 上注册回调函数，并返回新的 TaskFuture 对象。
    public <U> TaskFuture<U> thenCompose(Function<? super T, ? extends CompletableFuture<U>> fn) {
        return new TaskFuture<>(future.thenCompose(fn), state);
    }

    /// 包装的 thenCompose 方法，用于在 Future 上注册回调函数，并返回新的 TaskFuture 对象。
    public <U> TaskFuture<U> thenComposeTask(Function<? super T, ? extends TaskFuture<U>> fn) {
        return new TaskFuture<>(future.thenCompose(t -> fn.apply(t).getFuture()), state);
    }

    /// 包装的 thenAccept 方法，用于在 Future 上注册回调函数，并返回新的 TaskFuture 对象。
    public TaskFuture<Void> thenAccept(Consumer<? super T> action) {
        return new TaskFuture<>(future.thenAccept(action), state);
    }

    /// 包装的异常处理方法，用于在 Future 上注册异常处理函数，并返回新的 TaskFuture 对象。
    public TaskFuture<T> exceptionally(Function<Throwable, ? extends T> fn) {
        return new TaskFuture<>(future.exceptionally(fn), state);
    }

    /// 包装的 handle 方法，用于在 Future 上注册回调函数，并返回新的 TaskFuture 对象。
    public <U> TaskFuture<U> handle(BiFunction<? super T, Throwable, ? extends U> fn) {
        return new TaskFuture<>(future.handle(fn), state);
    }

    /// 包装的 whenComplete 方法，用于在 Future 上注册回调函数，并返回新的 TaskFuture 对象。
    public TaskFuture<T> whenComplete(BiConsumer<? super T, ? super Throwable> action) {
        return new TaskFuture<>(future.whenComplete(action), state);
    }

    // ----------- 其他方法 ---------------
    /**
     * 将异常从 CompletionException 或 ExecutionException 中解包。
     * @param t 需要解包的异常。
     * @return 解包后的异常
     */
    public static Throwable unwrapException(Throwable t) {
        while(     t instanceof CompletionException
                || t instanceof ExecutionException
                || t instanceof TaskNoRetryRuntimeException
                || t instanceof TaskRetryRuntimeException) {
            if(t.getCause() == null) {
                // 如果没有可用的异常，则直接返回当前异常
                return t;
            }
            t = t.getCause();
        }
        return t;
    }
}
