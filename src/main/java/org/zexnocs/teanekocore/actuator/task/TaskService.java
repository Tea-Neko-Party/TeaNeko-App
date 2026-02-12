package org.zexnocs.teanekocore.actuator.task;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.actuator.task.exception.TaskDuplicateKeyException;
import org.zexnocs.teanekocore.actuator.task.exception.TaskExpirationException;
import org.zexnocs.teanekocore.actuator.task.exception.TaskIllegalStateException;
import org.zexnocs.teanekocore.actuator.task.exception.TaskNotFoundException;
import org.zexnocs.teanekocore.actuator.task.interfaces.*;
import org.zexnocs.teanekocore.actuator.task.state.TaskFinishedState;
import org.zexnocs.teanekocore.cache.ConcurrentMapCacheContainer;
import org.zexnocs.teanekocore.cache.interfaces.ICacheData;
import org.zexnocs.teanekocore.cache.interfaces.ICacheDataFactory;
import org.zexnocs.teanekocore.cache.interfaces.ICacheService;
import org.zexnocs.teanekocore.logger.ILogger;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 任务服务。
 * 用于注册任务、提交任务结果。
 *
 * @author zExNocs
 * @date 2026/02/13
 */
@Service
public class TaskService implements ITaskService {
    private final ConcurrentMapCacheContainer<UUID, ITask<?>> taskMap;
    private final ILogger logger;
    private final ITaskExecuteService iTaskExecuteService;
    private final ITaskRetryService iTaskRetryService;

    @Lazy
    @Autowired
    public TaskService(ICacheService iCacheService, ILogger logger, ITaskExecuteService iTaskExecuteService, ITaskRetryService iTaskRetryService) {
        this.taskMap = ConcurrentMapCacheContainer.of(iCacheService,
                1000L,                      // 清理间隔，1s
                new TaskCacheFactory(),         // CacheData 工厂
                false);                     // 不参与自动清理
        this.logger = logger;
        this.iTaskExecuteService = iTaskExecuteService;
        this.iTaskRetryService = iTaskRetryService;
    }

    /**
     * 使用一个完整的 ITaskConfig 来注册一个任务。
     *
     * @param key    任务的唯一标识符
     * @param config 任务的配置
     * @param clazz  任务结果的类型
     * @return TaskFuture，用于获取任务结果；TaskFuture 务必调用 .finish() 函数来保证处理异常。
     * @throws TaskDuplicateKeyException 如果指定的 key 已经被使用
     */
    @Override
    public <T> TaskFuture<ITaskResult<T>> subscribe(UUID key, @NonNull ITaskConfig<T> config, Class<T> clazz)
            throws TaskDuplicateKeyException {
        // 如果 key 已经存在，则抛出异常
        if(taskMap.containsKey(key)) {
            throw new TaskDuplicateKeyException(key);
        }
        // 创建一个新的 Task 对象
        var future = new TaskFuture<>(logger, config.getName(), new CompletableFuture<ITaskResult<T>>());
        var task = new Task<>(key, config, future, clazz);
        taskMap.put(key, task);
        // 执行任务
        iTaskExecuteService.executeTask(task);
        return future;
    }

    /**
     * 使用一个 result 来完成一个订阅任务。
     * 如果 result 的 isSuccess() 返回 false，则会重试该任务直到达到最大重试次数。
     *
     * @param key    订阅任务的唯一标识符
     * @param result 订阅任务的结果
     * @return 此次是否成功完成任务。如果返回 false，则表示任务已被重新加入队列等待重试
     * @throws TaskNotFoundException     如果没有找到对应的订阅任务
     * @throws ClassCastException        如果 result 的类型与订阅任务的类型不匹配
     * @throws TaskIllegalStateException 如果该任务没有 executed 则抛出此异常
     */
    @Override
    @SuppressWarnings("unchecked")
    public boolean complete(UUID key, @NonNull ITaskResult<?> result)
            throws TaskNotFoundException, ClassCastException, TaskIllegalStateException {
        var task = taskMap.get(key);
        if(task == null) {
            // 没有找到对应的任务，抛出异常
            throw new TaskNotFoundException(key);
        }
        // 检测结果类型是否匹配
        var clazz = task.getResultType();
        var resultValue = result.getResult();
        if(!clazz.isInstance(resultValue) && resultValue != null) {
            // 如果 resultValue 不为 null 且类型不匹配，则抛出异常
            throw new ClassCastException("""
                    结果类型与任务配置的类型不匹配：
                    目标任务名称：%s
                    目标类型：%s
                    传入类型：%s"""
                    .formatted(task.getConfig().getName(),
                            clazz.getName(),
                            resultValue.getClass().getName()));
        }
        // 尝试 retry 任务，是否符合由 retry service 判断
        if(iTaskRetryService.retryTaskWithResult(task, result)) {
            // 如果 retry 成功，则返回 false
            return false;
        }
        // 否则，完成任务并返回 true
        taskMap.remove(key);
        _forceComplete((ITask<Object>) task, (ITaskResult<Object>) result);
        return true;
    }

    /// 强制进行类型转化使得 task 与 result 的类型一致
    private <T> void _forceComplete(ITask<T> task, ITaskResult<T> result) {
        task.getFuture().complete(result);
        task.switchState(new TaskFinishedState());
    }

    /**
     * 使用一个异常来完成一个订阅任务。
     * 会重试该任务直到达到最大重试次数。
     *
     * @param key       订阅任务的唯一标识符
     * @param exception 订阅任务的异常
     * @return 此次是否成功完成任务。如果返回 false，则表示任务已被重新加入队列等待重试
     * @throws TaskNotFoundException     如果没有找到对应的订阅任务
     * @throws TaskIllegalStateException 如果该任务没有 executed 则抛出此异常
     */
    @Override
    public boolean completeExceptionally(UUID key, Throwable exception) throws TaskNotFoundException, TaskIllegalStateException {
        var task = taskMap.get(key);
        if(task == null) {
            // 没有找到对应的任务，抛出异常
            throw new TaskNotFoundException(key);
        }
        // 尝试 retry 任务，是否符合由 retry service 判断
        if(iTaskRetryService.retryTaskWithException(task, exception)) {
            // 如果 retry 成功，则返回 false
            return false;
        }
        // 否则，完成任务并返回 true
        taskMap.remove(key);
        task.getFuture().completeExceptionally(exception);
        task.switchState(new TaskFinishedState());
        return true;
    }

    // ---------- Task 专用的 TaskCache -------------
    /// TaskCache 用于包装 Task 对象，使其能够存储在 taskMap 中。
    @AllArgsConstructor
    private class TaskCache implements ICacheData<ITask<?>> {
        /// task 对象
        private final ITask<?> task;

        /**
         * 获取缓存的值
         */
        @Override
        public ITask<?> getValue() {
            return task;
        }

        /**
         * 更新缓存的访问时间
         * @param currentTimeMs 当前时间，单位毫秒
         */
        @Override
        public void updateAccessTime(long currentTimeMs) {
            // 什么也不做，因为访问不会改变过期时间。
        }

        /**
         * 是否过期
         * @param currentTimeMs 当前时间，单位毫秒
         * @return 是否过期
         */
        @Override
        public boolean isExpired(long currentTimeMs) {
            return task.isExpired(currentTimeMs);
        }

        /**
         * 过期后任务将会以异常形式完成。
         * @param currentTimeMs 用于传递当前的时间，单位毫秒
         * @param value         当前缓存的值，过期后可能需要进行一些清理或其他操作
         * @return 是否需要删除该缓存。返回 true 则会删除，返回 false 则不会删除。
         */
        @Override
        public boolean onExpire(long currentTimeMs, ITask<?> value) {
            // 以异常的形式完成任务
            try {
                var e = new TaskExpirationException("任务过期：" + task.getConfig().getName());
                // 尝试使用过期异常 retry 任务，是否符合由 retry service 判断
                if(iTaskRetryService.retryTaskWithException(task, e)) {
                    // 如果 retry 成功，则返回 false，暂时不进行删除
                    return false;
                } else {
                    // 否则，完成任务并返回 true，进行删除
                    task.getFuture().completeExceptionally(e);
                    task.switchState(new TaskFinishedState());
                    return true;
                }
            } catch (TaskIllegalStateException e) {
                // 说明该任务没有执行完却过期了，任务执行时间过长，可能发生了死锁或者死循环
                task.getExecutingFuture().cancel(true);
                task.getFuture().completeExceptionally(e);
                task.switchState(new TaskFinishedState());
                logger.errorWithReport(TaskService.class.getName(),
                        "任务执行时间过长，可能出现了死锁或死循环，已取消任务并完成异常：%s"
                                .formatted(task.getConfig().getName()), e);
                return true;
            }
        }
    }

    /// TaskCache 的工厂类
    private class TaskCacheFactory implements ICacheDataFactory<ITask<?>> {
        /**
         * 创建一个 ICacheData 对象。
         *
         * @param value ICacheData 中存储的值
         * @return 创建好的 ICacheData 对象
         */
        @Override
        public ICacheData<ITask<?>> createCacheData(ITask<?> value) {
            return new TaskCache(value);
        }
    }
}
