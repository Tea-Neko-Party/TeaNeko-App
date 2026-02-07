package org.zexnocs.teanekocore.actuator.exception;

/**
 * 主函数执行异常。
 * 传递给 CompletableFuture 的异常，用于区分主函数执行异常和其他异常。
 */
public class MainTaskExecutionException extends RuntimeException {
    public MainTaskExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
