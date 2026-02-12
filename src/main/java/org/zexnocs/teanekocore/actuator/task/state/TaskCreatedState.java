package org.zexnocs.teanekocore.actuator.task.state;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 任务已创建状态，或者重回重试状态。
 * 该状态下任务正处于创建完成等待执行的状态。
 * 下一个状态是 Submitted 状态。
 *
 * @author zExNocs
 * @date 2026/02/12
 */
@AllArgsConstructor
public class TaskCreatedState implements ITaskState {
    /// 执行的时间
    @Getter
    private final long executeTimeInMillis;
}
