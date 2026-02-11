package org.zexnocs.teanekocore.actuator.task.interfaces;

/**
 * 任务接口，用于定义一个任务的基本行为。
 *
 * @author zExNocs
 * @date 2026/02/10
 */
public interface ITask<T> {
    /**
     * 获取任务配置
     * @return 任务配置对象
     */
    ITaskConfig<T> getConfig();

    /**
     * 是否已经超过了最大重试次数
     * @return 是否超过了最大重试次数
     */
    boolean isMaxRetryCountExceeded();

    /**
     * 线程安全的更新重试状态。
     * 如果没有执行过，则不允许重试
     * @return 是否允许重试。true表示允许重试，false 表示不允许重试
     */
    boolean safeUpdateRetry();

    /**
     * 原子性地设置并标记为已提交执行
     * @return 在这次提交执行之前是否已经提交过
     */
    boolean getAndSetSubmitted();

    /**
     * 是否彻底完成任务
     * @return 是否彻底完成任务
     */
    boolean isDone();
}
