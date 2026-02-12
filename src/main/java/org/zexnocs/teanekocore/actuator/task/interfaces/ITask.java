package org.zexnocs.teanekocore.actuator.task.interfaces;

import org.zexnocs.teanekocore.actuator.task.exception.TaskIllegalStateException;
import org.zexnocs.teanekocore.actuator.task.state.ITaskState;
import org.zexnocs.teanekocore.framework.state.IStateMachine;

import java.util.UUID;

/**
 * 任务接口，用于定义一个任务的基本行为。
 * 是一个简单的状态机，支持的状态包含：
 * 创建 → 提交/执行中 → 执行完毕 → 提交成功
 * 若重试会重回创建状态
 *
 * @author zExNocs
 * @date 2026/02/10
 */
public interface ITask<T> extends IStateMachine<ITaskState> {
    /**
     * 获取任务的唯一标识符
     * @return 任务的唯一标识符
     */
    UUID getKey();

    /**
     * 获取任务配置
     * @return 任务配置对象
     */
    ITaskConfig<T> getConfig();

    /**
     * 原子性地修改成 Retry 的状态。
     * 前提是当前任务处于 Created 状态。
     * @return true 表示修改成功；false 表示重试次数达到上限
     * @throws TaskIllegalStateException 如果当前任务不处于 Created 状态
     */
    boolean switchToRetryState() throws TaskIllegalStateException;

    /**
     * 获取当前的执行时间，前提是任务处于 Created 状态。
     * @return 当前的执行时间
     */
    long getExecuteTimeInMillis() throws TaskIllegalStateException;
}
