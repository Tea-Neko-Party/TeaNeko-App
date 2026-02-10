package org.zexnocs.teanekocore.actuator.task.exception;

/**
 * 主函数执行异常。
 * 传递给 CompletableFuture 的异常，用于区分主函数执行异常和其他异常。
 *
 * @author zExNocs
 * @date 2026/02/10
 */
public class MainTaskExecutionException extends RuntimeException {
    public MainTaskExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
