package org.zexnocs.teanekocore.actuator.task.exception;

import org.zexnocs.teanekocore.actuator.task.state.ITaskState;

/**
 * 任务非法状态异常。
 * 表示任务在执行某一个需要检测状态的操作时，处于一个错误的状态，无法继续执行。
 *
 * @author zExNocs
 * @date 2026/02/12
 * @since 4.0.0
 */
public class TaskIllegalStateException extends RuntimeException {
    /**
     * 构造一个新的 TaskIllegalStateException。
     *
     * @param message 可能的原因
     * @param currentState 当前状态
     * @param expectedState 期望状态
     */
    public TaskIllegalStateException(String message,
                                     Class<? extends ITaskState> currentState,
                                     Class<? extends ITaskState> expectedState) {
        super("""
                任务状态异常：
                当前状态: %s
                需求状态: %s
                可能的原因: %s"""
                .formatted(currentState.getSimpleName(),
                expectedState.getSimpleName(),
                message));
    }
}
