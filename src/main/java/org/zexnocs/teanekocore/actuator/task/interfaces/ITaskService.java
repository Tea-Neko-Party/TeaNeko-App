package org.zexnocs.teanekocore.actuator.task.interfaces;

import lombok.NonNull;
import org.zexnocs.teanekocore.actuator.task.TaskConfig;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.actuator.task.exception.TaskDuplicateKeyException;
import org.zexnocs.teanekocore.actuator.task.exception.TaskIllegalStateException;
import org.zexnocs.teanekocore.actuator.task.exception.TaskNotFoundException;

import java.util.UUID;
import java.util.concurrent.Callable;

/**
 * 任务服务接口。
 * 用于注册任务、提交任务结果。
 * <p>
 * 间接完成一个任务：
 * 1. 手动指定 key 并自行记录
 * 2. 使用 complete 或 completeExceptionally 方法完成任务
 * <p>
 * 主动完成一个任务：
 * 1. 在 supplier 中直接返回结果
 * 2. 可以使用随机 key
 * @author zExNocs
 * @date 2026/02/13
 */
public interface ITaskService {
    // -------------- 注册任务 --------------
    /**
     * 使用一个完整的 ITaskConfig 来注册一个任务。
     * @param key 任务的唯一标识符
     * @param config 任务的配置
     * @param clazz 任务结果的类型
     * @param <T> 任务结果的类型
     * @return TaskFuture，用于获取任务结果；TaskFuture 务必调用 .finish() 函数来保证处理异常。
     * @throws TaskDuplicateKeyException 如果指定的 key 已经被使用
     */
    <T> TaskFuture<ITaskResult<T>> subscribe(UUID key, @NonNull ITaskConfig<T> config, Class<T> clazz)
            throws TaskDuplicateKeyException;

    /**
     * 随机一个 key 来注册一个任务。
     * @param config 任务的配置
     * @param clazz 任务结果的类型
     * @param <T> 任务结果的类型
     * @return TaskFuture，用于获取任务结果；TaskFuture 务必调用 .finish() 函数来保证处理异常。
     */
    default <T> TaskFuture<ITaskResult<T>> subscribe(@NonNull ITaskConfig<T> config, Class<T> clazz) {
        return subscribe(UUID.randomUUID(), config, clazz);
    }

    /**
     * 手动指定一个 key 和默认配置来注册一个任务。
     * 默认：
     * 1. 不重试
     * 2. 过期时间 10 分钟
     *
     * @param key       任务的唯一标识符
     * @param name      任务名称
     * @param taskStage 任务阶段命名空间
     * @param callable  任务
     * @param clazz     任务结果的类型
     * @param <T> 任务结果的类型
     * @return TaskFuture，用于获取任务结果；TaskFuture 务必调用 .finish() 函数来保证处理异常。
     * @throws TaskDuplicateKeyException 任务重复键异常。当尝试注册一个已经存在的任务键时抛出。
     */
    default <T> TaskFuture<ITaskResult<T>> subscribe(
            UUID key,
            String name,
            String taskStage,
            Callable<ITaskResult<T>> callable,
            Class<T> clazz) throws TaskDuplicateKeyException {
        var config = TaskConfig.<T>builder()
                .name(name)
                .taskStageNamespace(taskStage)
                .callable(callable)
                .build();
        return subscribe(key, config, clazz);
    }

    /**
     * 使用随机 key 和默认配置来注册一个任务。
     * 默认：
     * 1. 不重试
     * 2. 过期时间 10 分钟
     * @param name 任务名称
     * @param callable 任务
     * @param clazz 任务结果的类型
     * @param <T> 任务结果的类型
     * @return TaskFuture，用于获取任务结果；TaskFuture 务必调用 .finish() 函数来保证处理异常。
     * @throws TaskDuplicateKeyException 如果指定的 key 已经被使用
     */
    default <T> TaskFuture<ITaskResult<T>> subscribe(
            String name,
            String taskStage,
            Callable<ITaskResult<T>> callable,
            Class<T> clazz) throws TaskDuplicateKeyException {
        return subscribe(UUID.randomUUID(), name, taskStage, callable, clazz);
    }

    // -------------- 提交任务结果 --------------
    /**
     * 使用一个 result 来完成一个订阅任务。
     * 如果 result 的 isSuccess() 返回 false，则会重试该任务直到达到最大重试次数。
     * @param key 订阅任务的唯一标识符
     * @param result 订阅任务的结果
     * @throws TaskNotFoundException 如果没有找到对应的订阅任务
     * @throws ClassCastException 如果 result 的类型与订阅任务的类型不匹配
     * @throws TaskIllegalStateException 如果该任务没有 executed 则抛出此异常
     * @return 此次是否成功完成任务。如果返回 false，则表示任务已被重新加入队列等待重试
     */
    boolean complete(UUID key, @NonNull ITaskResult<?> result)
            throws TaskNotFoundException, ClassCastException, TaskIllegalStateException;

    /**
     * 使用一个异常来完成一个订阅任务。
     * 会重试该任务直到达到最大重试次数。
     * @param key 订阅任务的唯一标识符
     * @param exception 订阅任务的异常
     * @throws TaskNotFoundException 如果没有找到对应的订阅任务
     * @throws TaskIllegalStateException 如果该任务没有 executed 则抛出此异常
     * @return 此次是否成功完成任务。如果返回 false，则表示任务已被重新加入队列等待重试
     */
    boolean completeExceptionally(UUID key, Throwable exception)
            throws TaskNotFoundException, TaskIllegalStateException;
}
