package org.zexnocs.teanekocore.database.base.stage;

import jakarta.persistence.OptimisticLockException;
import org.hibernate.StaleObjectStateException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.zexnocs.teanekocore.actuator.task.TaskStageChain;
import org.zexnocs.teanekocore.actuator.task.api.ITaskStage;
import org.zexnocs.teanekocore.actuator.task.api.TaskStage;
import org.zexnocs.teanekocore.actuator.task.exception.TaskNoRetryRuntimeException;
import org.zexnocs.teanekocore.actuator.task.exception.TaskRetryRuntimeException;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskResult;
import org.zexnocs.teanekocore.database.base.exception.DatabaseRetryException;
import org.zexnocs.teanekocore.logger.ILogger;

import java.sql.SQLIntegrityConstraintViolationException;

/**
 *
 *
 * @author zExNocs
 * @date 2026/02/15
 */
@TaskStage(value = {"general_database"}, priority = 50)
public class DatabaseRetryHandlingTaskStage implements ITaskStage {
    /// 日志
    private final ILogger logger;

    @Autowired
    public DatabaseRetryHandlingTaskStage(ILogger logger) {
        this.logger = logger;
    }

    /**
     * 处理任务。
     * 1. 前置处理
     * 2. chain.next() 调用下一个阶段
     * 3. 后置处理
     * 4. 返回 chain.next() 的 Future
     *
     * @param chain 该任务的阶段链
     * @return 返回主运行任务的 CompletableFuture 对，分别表示 future 链的 头和尾
     */
    @Override
    public ITaskResult<?> process(TaskStageChain chain) {
        try {
            return chain.next();
        } catch(DatabaseRetryException |                       // 手动触发的重试异常
                DataIntegrityViolationException |              // 数据完整性违反异常，重试时重新查找数据库
                ConstraintViolationException |                 // 约束违反异常，重试时重新查找数据库
                ObjectOptimisticLockingFailureException |      // 乐观锁失败异常，重试
                OptimisticLockException |                      // 乐观锁异常，重试
                StaleObjectStateException |                    // 过时对象状态异常(乐观锁)，重试
                SQLIntegrityConstraintViolationException e) {
            var task = chain.getTask();
            var config = task.getConfig();
            int tryCount = task.getCurrentRetryCount();
            int maxCount = config.getMaxRetries();
            logger.debug(this.getClass().getSimpleName(), """
                    数据库任务重试：%s
                    数据库异常：%s
                    当前尝试次数：%d/%d""".formatted(
                            config.getName(),
                            e.getClass().getName(),
                            tryCount, maxCount));
            throw new TaskRetryRuntimeException("""
                    数据库任务重试: %s; 重试次数: %d / %d"""
                    .formatted(config.getName(), tryCount, maxCount), e);
        } catch (Exception e) {
            // 其他异常不处理，直接抛出
            throw new TaskNoRetryRuntimeException("数据库任务执行抛出不重试的异常", e);
        }
    }
}
