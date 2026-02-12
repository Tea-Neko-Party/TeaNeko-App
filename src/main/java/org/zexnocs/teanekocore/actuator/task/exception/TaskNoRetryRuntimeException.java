package org.zexnocs.teanekocore.actuator.task.exception;

/**
 * 固定不会进行重试的异常，抛出这个异常会直接结束任务，不会进行任何重试。
 *
 * @author zExNocs
 * @date 2026/02/12
 */
public class TaskNoRetryRuntimeException extends RuntimeException {
    /**
     * 构造一个新的 TaskNoRetryException。
     *
     * @param message 异常消息
     */
    public TaskNoRetryRuntimeException(String message) {
        super(message);
    }

    /**
     * 构造一个新的 TaskNoRetryException，带有异常消息和原因。
     *
     * @param message 异常消息
     * @param cause   导致此异常的原因
     */
    public TaskNoRetryRuntimeException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 解包 TaskNoRetryException 获取原始异常。
     */
    public Throwable unwrap() {
        Throwable cause = this.getCause();
        if(cause instanceof TaskNoRetryRuntimeException) {
            return ((TaskNoRetryRuntimeException) cause).unwrap();
        }
        return cause != null ? cause : this;
    }
}
