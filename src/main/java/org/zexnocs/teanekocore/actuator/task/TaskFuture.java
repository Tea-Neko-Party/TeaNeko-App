package org.zexnocs.teanekocore.actuator.task;

import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskResult;
import org.zexnocs.teanekocore.logger.ILogger;

import java.util.concurrent.CompletableFuture;

/**
 * 用来管理 Task 执行结果的 Future 对象。
 * 主要用于注册该 Task，并管理其执行链以及最后是否以异常结束。
 *
 * @author zExNocs
 * @date 2026/02/11
 */
public class TaskFuture<T> implements AutoCloseable {
    /// 用于处理异常的 logger
    private final ILogger logger;

    /// Future 对象，用于存储 Task 的执行结果。
    private CompletableFuture<ITaskResult<T>> future;

    /// 是否提交；注意创建与提交必须在同一线程中进行
    private boolean isSubmitted = false;

    /**
     * 构造函数，用于初始化 Future 对象。
     */
    protected TaskFuture(ILogger logger) {
        this.logger = logger;
        this.future = new CompletableFuture<>();
    }

    /**
     * 自动为该 Future 处理未处理的异常。
     */
    @Override
    public void close() throws Exception {
        // todo
    }
}
