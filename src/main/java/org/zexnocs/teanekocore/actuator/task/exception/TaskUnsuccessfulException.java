package org.zexnocs.teanekocore.actuator.task.exception;

/**
 * 任务已结束，但最终结果显式报告失败；没有底层异常可供传播。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
public class TaskUnsuccessfulException extends IllegalStateException {
    /**
     * 建立任务结果为失败状态的异常信号，操作名称仅用于非敏感诊断。
     *
     * @param operation 用于定位失败任务的操作名称
     */
    public TaskUnsuccessfulException(String operation) {
        super("Task reported an unsuccessful result: " + operation);
    }
}
