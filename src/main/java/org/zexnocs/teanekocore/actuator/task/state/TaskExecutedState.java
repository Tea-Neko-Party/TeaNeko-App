package org.zexnocs.teanekocore.actuator.task.state;

/**
 * 任务执行完毕状态。
 * 该状态下有会切换到两个其他状态：
 * 1. Created 状态，说明任务符合重试条件
 * 2. Finished 状态，说明任务被提交完成
 *
 * @author zExNocs
 * @date 2026/02/12
 */
public class TaskExecutedState implements ITaskState {
}
