package org.zexnocs.teanekocore.actuator.task;

import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskResult;

/**
 * 一般任务结果。
 * 直接存储结果和结果是否成功的记录类。
 *
 * @param isSuccess 结果是否成功
 * @param result    结果
 * @author zExNocs
 * @date 2026/02/06
 */
public record TaskResult<T>(boolean isSuccess, T result) implements ITaskResult<T> {
}
