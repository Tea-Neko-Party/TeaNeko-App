package org.zexnocs.teanekocore.database.base.exception;

import org.zexnocs.teanekocore.actuator.task.exception.TaskRetryRuntimeException;

/**
 * 需要去重试的数据库异常。
 * 抛出该异常会自动重试；或者作为包装在 DatabaseRetryHandlingTaskStage 中被捕获并重试。
 *
 * @author zExNocs
 * @date 2026/02/15
 */
public class DatabaseRetryException extends TaskRetryRuntimeException {
    /**
     * 构造函数
     *
     * @param message 信息
     * @param cause 原因
     */
    public DatabaseRetryException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 构造函数
     *
     * @param message 信息
     */
    public DatabaseRetryException(String message) {
        super(message);
    }
}
