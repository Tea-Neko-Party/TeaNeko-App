package org.zexnocs.teanekocore.actuator.task;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskResult;

/**
 * 一般任务结果。
 * 直接存储结果和结果是否成功的记录类。
 *
 * @author zExNocs
 * @date 2026/02/06
 */
@Getter
@Setter
@AllArgsConstructor
public class TaskResult<T> implements ITaskResult<T> {
    /**
     * 结果是否成功
     */
    private final boolean isSuccess;

    /**
     * 结果值
     */
    private final T result;
}
