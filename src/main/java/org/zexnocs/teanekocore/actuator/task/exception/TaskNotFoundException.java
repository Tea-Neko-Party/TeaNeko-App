package org.zexnocs.teanekocore.actuator.task.exception;

import java.util.UUID;

/**
 * 在 TaskService 尝试提交任务发现没有找到对应的 Task 时抛出此异常。
 *
 * @author zExNocs
 * @date 2026/02/13
 */
public class TaskNotFoundException extends RuntimeException {
    public TaskNotFoundException(UUID key) {
        super("未找到对应的任务，key: %s".formatted(key));
    }
}
