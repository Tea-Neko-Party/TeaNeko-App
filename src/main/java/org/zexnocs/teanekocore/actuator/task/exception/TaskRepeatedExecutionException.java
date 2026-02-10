package org.zexnocs.teanekocore.actuator.task.exception;

/**
 * 任务重复执行异常。
 * 当 Task 类的 run 方法被重复调用时抛出此异常。
 *
 * @author zExNocs
 * @date 2026/02/10
 */
public class TaskRepeatedExecutionException extends RuntimeException {
    public TaskRepeatedExecutionException(String message) {
        super(message);
    }
}
