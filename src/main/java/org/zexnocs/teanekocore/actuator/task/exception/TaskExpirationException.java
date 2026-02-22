package org.zexnocs.teanekocore.actuator.task.exception;

/**
 * Task 过期异常。当一个 Task 被认为过期时，将会以异常的形式完成。
 *
 * @since 4.0.0
 * @author zExNocs
 * @date 2026/02/13
 */
public class TaskExpirationException extends RuntimeException {
    public TaskExpirationException(String message) {
        super(message);
    }
}
