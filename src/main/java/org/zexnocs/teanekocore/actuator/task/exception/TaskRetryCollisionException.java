package org.zexnocs.teanekocore.actuator.task.exception;

/**
 * 任务重试冲突异常。
 *
 * @author zExNocs
 * @date 2026/02/10
 */
public class TaskRetryCollisionException extends RuntimeException {
    public TaskRetryCollisionException(String message) {
        super(message);
    }
}
