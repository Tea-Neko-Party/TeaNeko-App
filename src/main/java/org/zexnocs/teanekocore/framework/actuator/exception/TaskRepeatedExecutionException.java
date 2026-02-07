package org.zexnocs.teanekocore.framework.actuator.exception;

/**
 * 任务重复执行异常。
 * 当 Task 类的 run 方法被重复调用时抛出此异常。
 */
public class TaskRepeatedExecutionException extends RuntimeException {
    public TaskRepeatedExecutionException(String message) {
        super(message);
    }
}
