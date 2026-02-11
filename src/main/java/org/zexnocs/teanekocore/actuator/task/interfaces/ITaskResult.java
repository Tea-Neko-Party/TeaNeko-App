package org.zexnocs.teanekocore.actuator.task.interfaces;

import org.jspecify.annotations.Nullable;

/**
 * 任务结果接口。
 * 任务结果接口定义了任务执行后的结果类型，可以包含成功与否的状态以及具体的结果数据。
 * 任务执行后会返回一个实现了 ITaskResult 接口的对象，供调用者获取执行结果。
 * 如果 success 为 false，会进行重试，直到达到最大重试次数或者任务过期。
 *
 * @author zExNocs
 * @date 2026/02/10
 */
public interface ITaskResult<T> {
    /**
     * 该任务是否成功完成。
     * 如果返回 false 表示任务执行失败，可能会进行重试，直到达到最大重试次数或者任务过期。
     * @return true 如果任务成功完成，false 如果任务失败。
     */
    boolean isSuccess();

    /**
     * 返回任务的结果数据。
     * 结果数据的类型由具体实现类定义，可以是任何类型。
     * @return 任务的结果数据。
     */
    @Nullable
    T getResult();
}
