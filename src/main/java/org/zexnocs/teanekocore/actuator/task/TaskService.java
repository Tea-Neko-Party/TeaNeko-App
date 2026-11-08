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
import org.zexnocs.teanekocore.actuator.task.interfaces.ITask;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskConfig;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskExecuteService;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskResult;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskRetryService;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskService;
import org.zexnocs.teanekocore.actuator.task.state.TaskFinishedState;
import org.zexnocs.teanekocore.cache.ConcurrentMapCacheContainer;
import org.zexnocs.teanekocore.cache.interfaces.ICacheData;
import org.zexnocs.teanekocore.cache.interfaces.ICacheDataFactory;
import org.zexnocs.teanekocore.cache.interfaces.ICacheService;
import org.zexnocs.teanekocore.logger.ILogger;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 任务服务。
 * 用于注册任务、提交任务结果。
 * <br>2026/11/03：注册真实任务回执的有界观测，不将提交当作业务完成。
 * <br>2026/11/07：限制任务准入并将逻辑等待与实际工作收尾分开。
 * <br>2026/11/07：增加插件归属与有效状态约束。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.9.0-l
 */
@Service
public class TaskService implements ITaskService, AutoCloseable {
    private @org.jspecify.annotations.Nullable TaskPluginBindings pluginBindings;

    /**
     * 2026/11/07：外部异步回执同样复核排队时固定的插件代次。
     * @param bindings 任务归属与调用代次缓存
     */
    @Autowired
    public void installPluginBindings(TaskPluginBindings bindings) {
        this.pluginBindings = bindings;
    }
    private final java.util.Map<UUID, ITask<?>> inFlight = new java.util.HashMap<>();
    private int maxInFlight = 1024;
    private boolean closed;

    /**
     * @param limit 本机任务准入总数，包含排队、实际执行及外部结果等待
     * @throws IllegalArgumentException 当限制不在支持范围时
     */
    @org.springframework.beans.factory.annotation.Value("${tea-neko.tasks.max-in-flight:1024}")
    public void setMaxInFlight(int limit) {
        if (limit < 1 || limit > 100000) {
            throw new IllegalArgumentException("任务在途上限须在 1～100000 之间。 ");
        }
        synchronized (inFlight) {
            maxInFlight = limit;
        }
    }

    /** @return 包含逻辑超时后尚未收尾工作的实际占用数量 */
    public int inFlightCount() {
        synchronized (inFlight) {
            return inFlight.size();
        }
    }

    /** 关闭停止新准入并请求协作停止；不丢弃尚未收尾的物理工作。 */
    @Override
    public void close() {
        java.util.List<ITask<?>> remaining;
        synchronized (inFlight) {
            closed = true;
            remaining = java.util.List.copyOf(inFlight.values());
        }
        remaining.forEach(task -> stopWaiting(task, new TaskExpirationException("任务宿主正在关闭，已开始的工作仍需收尾。")));
    }
    private org.zexnocs.teanekocore.actuator.runtime.@org.jspecify.annotations.Nullable RuntimeMonitor monitor;

    /**
     * 2026/11/03：复用原任务服务，独立宿主不安装观测时行为保持不变。
     * @param monitor 当前进程的有界观测
     */
    @Autowired
    public void installMonitor(org.zexnocs.teanekocore.actuator.runtime.RuntimeMonitor monitor) {
        this.monitor = monitor;
    }
    private final ConcurrentMapCacheContainer<UUID, ITask<?>> taskMap;
    private final ILogger logger;
    private final ITaskExecuteService iTaskExecuteService;
    private final ITaskRetryService iTaskRetryService;

    /**
     * 装配任务执行与重试服务，并将任务缓存从普通手动清理中隔离，避免清缓存使在途回执丢失。
     *
     * @param iCacheService 缓存注册与生命周期服务
     * @param logger 项目日志接口
     * @param iTaskExecuteService 本次装配使用的 {@code ITaskExecuteService} 服务
     * @param iTaskRetryService 本次装配使用的 {@code ITaskRetryService} 服务
     */
    @Lazy
    @Autowired
    public TaskService(ICacheService iCacheService,
                       ILogger logger,
                       ITaskExecuteService iTaskExecuteService,
                       ITaskRetryService iTaskRetryService) {
        this.taskMap = ConcurrentMapCacheContainer.of(iCacheService,
                Duration.ofSeconds(1),             // 清理间隔，1s
                new TaskCacheFactory(),         // CacheData 工厂
                false);                         // 不参与自动清理
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
     * @param <T> 本方法约定的泛型数据类型
     * @return TaskFuture，用于获取任务结果；TaskFuture 务必调用 .finish() 函数来保证处理异常。
     * @throws TaskDuplicateKeyException 如果指定的 key 已经被使用
     */
    @Override
    public <T> TaskFuture<ITaskResult<T>> subscribeWithFuture(UUID key, @NonNull ITaskConfig<T> config, Class<T> clazz)
        throws TaskDuplicateKeyException {
        // 2026/11/06：准入覆盖真实工作，等待取消和截止不提前释放容量。
        Task<T> task;
        TaskFuture<ITaskResult<T>> future;
        synchronized (inFlight) {
            if (inFlight.containsKey(key)) {
                throw new TaskDuplicateKeyException(key.toString());
            }
            if (closed || inFlight.size() >= maxInFlight) {
                throw new org.zexnocs.teanekocore.actuator.execution.ExecutionBusyException("任务在途容量已满或正在关闭。");
            }
            future = new TaskFuture<>(logger, config.getName(), new CompletableFuture<>());
            task = new Task<>(key, config, future, clazz);
            inFlight.put(key, task);
            taskMap.putIfAbsent(key, task);
        }
        var released = task.getExecution().completion().whenComplete((ignored, failure) -> {
            taskMap.remove(key);
            synchronized (inFlight) {
                inFlight.remove(key, task);
            }
        });
        future.bindPhysical(released);
        future.getFuture().whenComplete((ignored, failure) -> {
            if (future.getFuture().isCancelled()) {
                task.getExecution().stop();
            }
        });
        if (monitor != null) {
            monitor.observe(task);
        }
        try {
            iTaskExecuteService.__executeTask(task);
        } catch (RuntimeException failure) {
            stopWaiting(task, failure);
            task.getExecution().settled();
        }
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
        if (task == null) {
            // 没有找到对应的任务，抛出异常
            throw new TaskNotFoundException(key);
        }
        // 检测结果类型是否匹配
        var clazz = task.getResultType();
        var resultValue = result.getResult();
        if (!clazz.isInstance(resultValue) && resultValue != null) {
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
        if (task.getExecution().stopped()) {
            task.getExecution().settled();
            return false;
        }
        // 2026/11/07：迟到结果不恢复旧资格，已经发生的操作不重新提交。
        if (pluginBindings != null) {
            try {
                pluginBindings.check(task);
            } catch (org.zexnocs.teanekocore.actuator.task.exception.TaskNoRetryRuntimeException failure) {
                task.switchState(new TaskFinishedState());
                boolean completed = task.getFuture().completeExceptionally(failure);
                task.getExecution().settled();
                return completed;
            }
        }
        // 尝试 retry 任务，是否符合由 retry service 判断
        if (iTaskRetryService.__retryTaskWithResult(task, result)) {
            // 如果 retry 成功，则返回 false
            return false;
        }
        // 否则，完成任务并返回 true
        task.switchState(new TaskFinishedState());
        boolean completed = _forceComplete((ITask<Object>) task, (ITaskResult<Object>) result);
        task.getExecution().settled();
        return completed;
    }

    /**
     * 强制进行类型转化使得 task 与 result 的类型一致
     *
     * @param task 用于本次处理的 {@code task} 数据
     * @param result 已经形成的处理结果
     * @param <T> 本方法约定的泛型数据类型
     * @return 当前校验或原子操作是否满足本方法约定的条件
     */
    private <T> boolean _forceComplete(ITask<T> task, ITaskResult<T> result) {
        return task.getFuture().complete(result);
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
        if (task == null) {
            // 没有找到对应的任务，抛出异常
            throw new TaskNotFoundException(key);
        }
        if (task.getExecution().stopped()) {
            task.getExecution().settled();
            return false;
        }
        // 2026/11/07：插件换代时禁止失败路径重新取得资格并重做副作用。
        if (pluginBindings != null) {
            try {
                pluginBindings.check(task);
            } catch (org.zexnocs.teanekocore.actuator.task.exception.TaskNoRetryRuntimeException failure) {
                exception = failure;
            }
        }
        // 尝试 retry 任务，是否符合由 retry service 判断
        if (iTaskRetryService.__retryTaskWithException(task, exception)) {
            // 如果 retry 成功，则返回 false
            return false;
        }
        // 否则，完成任务并返回 true
        task.switchState(new TaskFinishedState());
        boolean completed = task.getFuture().completeExceptionally(exception);
        task.getExecution().settled();
        return completed;
    }

    /**
     * 强制使用一个异常来完成一个订阅任务。
     * 不会再重试该任务。
     *
     * @param key       订阅任务的唯一标识符
     * @param exception 订阅任务的异常
     * @throws TaskNotFoundException     如果没有找到对应的订阅任务
     * @throws TaskIllegalStateException 如果该任务没有 executed 则抛出此
     * @since 4.0.8
     */
    @Override
    public void forceCompleteExceptionally(UUID key, Throwable exception) throws TaskNotFoundException, TaskIllegalStateException {
        var task = taskMap.get(key);
        if (task == null) {
            // 没有找到对应的任务，抛出异常
            throw new TaskNotFoundException(key);
        }
        stopWaiting(task, exception);
    }

    // ---------- Task 专用的 TaskCache -------------
    /**
     * TaskCache 用于包装 Task 对象，使其能够存储在 taskMap 中。
     *
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
     * @version 4.5.0-l
     */
    @AllArgsConstructor
    private class TaskCache implements ICacheData<ITask<?>> {
        /// task 对象
        private final ITask<?> task;

        /**
         * 获取缓存的值
         *
         * @return 满足本方法约束的 {@code ITask<?>} 结果
         */
        @Override
        public ITask<?> getValue() {
            return task;
        }

        /**
         * 更新缓存的访问时间
         *
         * @param currentTime 当前时间点
         */
        @Override
        public void updateAccessTime(Instant currentTime) {
            // 什么也不做，因为访问不会改变过期时间。
        }

        /**
         * 是否过期
         *
         * @param currentTime 当前时间点
         * @return 是否过期
         */
        @Override
        public boolean isExpired(Instant currentTime) {
            return task.isExpired(currentTime);
        }

        /**
         * 过期后任务将会以异常形式完成。
         *
         * @param currentTime 当前时间点
         * @param value         当前缓存的值，过期后可能需要进行一些清理或其他操作
         * @return 是否需要删除该缓存。返回 true 则会删除，返回 false 则不会删除。
         */
        @Override
        public boolean onExpire(Instant currentTime, ITask<?> value) {
            // 2026/11/06：未知副作用不自动重试，停止等待但保留物理工作及原编号。
            stopWaiting(task, new TaskExpirationException("任务等待超过期限，已开始的工作可能仍在执行："
                + task.getConfig().getName()));
            return false;
        }
    }

    /**
     * @param task 原在途任务
     * @param failure 固定等待失败，不能宣称实际写入已回滚
     */
    private void stopWaiting(ITask<?> task, Throwable failure) {
        task.getExecution().stop();
        task.getFuture().completeExceptionally(failure);
        task.switchState(new TaskFinishedState());
    }

    /**
     * TaskCache 的工厂类
     *
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
     * @version 4.5.0-l
     */
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
