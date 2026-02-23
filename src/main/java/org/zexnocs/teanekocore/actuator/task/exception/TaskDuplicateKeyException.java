package org.zexnocs.teanekocore.actuator.task.exception;

/**
 * 任务重复键异常。当尝试注册一个已经存在的任务键时抛出。
 *
 * @since 4.0.0
 * @author zExNocs
 * @date 2026/02/13
 */
public class TaskDuplicateKeyException extends RuntimeException {
    /**
     * 构造一个新的 TaskDuplicateKeyException，包含指定的任务键信息。
     *
     * @param key 任务键，不是 message
     */
    public TaskDuplicateKeyException(String key) {
        super("任务注册键已存在：" + key);
    }
}
