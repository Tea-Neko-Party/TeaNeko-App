package org.zexnocs.teanekocore.database.base;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskResult;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskService;
import org.zexnocs.teanekocore.database.base.interfaces.IDatabaseService;
import org.zexnocs.teanekocore.database.base.interfaces.IDatabaseTaskConfig;
import org.zexnocs.teanekocore.framework.function.VoidCallable;
import org.zexnocs.teanekocore.logger.ILogger;

import java.util.Collection;

/**
 * 数据库服务实现类。
 *
 * @author zExNocs
 * @date 2026/02/15
 */
@Service
public class DatabaseService implements IDatabaseService {
    /// 用于数据库任务执行的阶段命名空间。
    public static final String TASK_STAGE_NAMESPACE = "general_database";

    @Value("${tea-neko.database.max-retry-count}")
    private int MAX_RETRY_COUNT;

    /// 任务执行服务
    private final ITaskService iTaskService;
    private final ILogger iLogger;

    @Autowired
    public DatabaseService(ITaskService iTaskService, ILogger iLogger) {
        this.iTaskService = iTaskService;
        this.iLogger = iLogger;
    }

    /**
     * 快速添加一个简单的带事务的数据库任务。
     *
     * @param taskName            任务名称。
     * @param transactionCallback 事务回调，必须提供。
     * @param cacheCallback       缓存回调，可选提供。
     */
    @Override
    public void pushQuickTask(String taskName,
                               @NonNull VoidCallable transactionCallback,
                               @Nullable VoidCallable cacheCallback) {
        var config = new DatabaseTaskConfig(this, taskName);
        config.addTransactionTask(transactionCallback);
        if(cacheCallback != null) {
            config.addCacheTask(cacheCallback);
        }
        config.push();
    }

    /**
     * 直接执行数据库任务。
     * 该方法由 Database task config 的 push() 方法调用。
     * 该方法不会设置 Database task config 的 push 状态；
     * push 状态应该由 Database task config 自行原子性地设置。
     *
     * @param task 任务。
     * @return 数据库任务的 Future 对象，用于给 config 的 push() 方法返回 future。
     */
    @Override
    public TaskFuture<ITaskResult<Void>> __pushTask(IDatabaseTaskConfig task) {
        iLogger.debug(this.getClass().getSimpleName(), """
                提交数据库任务: %s""".formatted(task.getName()));
        return iTaskService.subscribe(task.__getTaskConfig(), Void.class);
    }

    /**
     * 给 Database Config 执行的事务处理，需要启动事务
     *
     * @param tasks 事务任务
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void __executeTaskWithTransaction(Collection<VoidCallable> tasks) throws Exception {
        for (var task : tasks) {
            task.call();
        }
    }

    /**
     * 给 Database Config 执行的缓存处理
     *
     * @param tasks 缓存任务
     */
    @Override
    public void __executeTaskWithCache(Collection<VoidCallable> tasks) throws Exception {
        for (var task : tasks) {
            task.call();
        }
    }

    /**
     * 获取最大重试的尝试次数。
     * 由 spring boot config 配置项提供。
     *
     * @return 最大重试的尝试次数
     */
    @Override
    public int __getMaxRetryCount() {
        return MAX_RETRY_COUNT;
    }
}
