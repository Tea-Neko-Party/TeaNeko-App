package org.zexnocs.teanekocore.actuator.task.exception;

/**
 * 任务重试异常。
 * 抛出该异常必定会导致任务重试。
 *
 * @author zExNocs
 * @date 2026/02/12
 */
public class TaskRetryRuntimeException extends RuntimeException {
    /**
     * 构造一个新的 TaskRetryException。
     *
     * @param message 错误消息
     */
    public TaskRetryRuntimeException(String message) {
        super(message);
    }

    /**
     * 构造一个新的 TaskRetryException。
     *
     * @param message 错误消息
     * @param cause   导致此异常的原因
     */
    public TaskRetryRuntimeException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 解包 TaskRetryException，获取原始异常。
     */
    public Throwable unwrap() {
        Throwable cause = this.getCause();
        if (cause instanceof TaskRetryRuntimeException) {
            return ((TaskRetryRuntimeException) cause).unwrap();
        }
        return cause != null ? cause : this;
    }
}
