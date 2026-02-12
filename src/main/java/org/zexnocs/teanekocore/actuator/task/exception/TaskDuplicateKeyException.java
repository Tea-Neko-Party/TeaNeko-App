package org.zexnocs.teanekocore.actuator.task.exception;

import java.util.UUID;

/**
 * 任务重复键异常。当尝试注册一个已经存在的任务键时抛出。
 *
 * @author zExNocs
 * @date 2026/02/13
 */
public class TaskDuplicateKeyException extends RuntimeException {
    public TaskDuplicateKeyException(UUID key) {
        super("任务注册键已存在：" + key);
    }
}
