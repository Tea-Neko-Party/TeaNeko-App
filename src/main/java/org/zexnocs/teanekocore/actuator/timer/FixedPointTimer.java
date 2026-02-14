package org.zexnocs.teanekocore.actuator.timer;

import lombok.Getter;
import org.jspecify.annotations.NonNull;
import org.springframework.scheduling.support.CronExpression;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskService;
import org.zexnocs.teanekocore.actuator.timer.interfaces.ITimer;
import org.zexnocs.teanekocore.actuator.timer.interfaces.ITimerTaskConfig;
import org.zexnocs.teanekocore.utils.ChinaDateUtil;

import java.time.LocalDateTime;

/**
 * 固定时间点计时器。
 * 使用 Spring Boot 中的 CronExpression 来解析 cron 表达式，来实现固定时间点的计时器。
 * <p>
 * CronExpression 是用来计算下一次符合条件时间的表达式，其构造说明：
 * CronExpression 有六个字段，分别是：
 * >>> 秒[0, 59]、分[0, 59]、时[0, 23]、日[1, 31]、月[1, 12]、周[0, 7] (0 和 7 都是周天)。
 * 每个字段可以使用以下特殊字符：
 * - 星号 *: 所有值
 * - 范围 -: 例如 1-5 表示 1 到 5
 * - 步长 /: 表示每几个单位 例如 1-10/2 表示 1, 3, 5, 7, 9
 * - 列表 ,: 例如 1,3,5 表示 1、3、5
 * - 问号 ?: 只能在日和周字段使用，表示不指定值；也就是说周和日只能指定一个，另一个使用问号。
 * - L: 只能在日和周字段使用，表示最后一个。
 *      - 日: 使用 L 表示最后一天; 使用 L-n 表示倒数第 n 天，例如 L-3 表示倒数第三天。
 *      - 周: 使用 L 表示最后一个周几，例如 5L 表示最后一个星期五。
 * - W: 只能在日字段使用，表示离指定日期最近的工作日。例如 15W 表示离每月 15 号最近的工作日。LW 表示离每月最后一天最近的工作日。
 * - #: 只能在周字段使用，表示第几个。例如 5#2 表示第二个星期五。
 * 宏：
 * - @yearly: 0 0 0 1 1 * 表示每年 1 月 1 日 0 时 0 分 0 秒触发
 * - @monthly: 0 0 0 1 * * 表示每月 1 日 0 时 0 分 0 秒触发
 * - @weekly: 0 0 0 * * 0 表示每周 0（周天） 0 时 0 分 0 秒触发
 * - @daily: 0 0 0 * * * 表示每天 0 时 0 分 0 秒触发
 * - @hourly: 0 0 * * * * 表示每小时 0 分 0 秒触发
 * <p>
 * @see CronExpression
 * @author zExNocs
 * @date 2026/02/14
 */
public class FixedPointTimer<T> implements ITimer<T>  {
    /// 任务配置
    private final ITimerTaskConfig<T> timerTaskConfig;

    /// Cron 表达式。
    private final CronExpression cron;

    /// 下次触发时间戳（毫秒）。
    private long nextTriggerTime;

    /// result type
    @Getter
    private final Class<T> resultType;

    /**
     * 构造函数。
     *
     * @param timerTaskConfig 任务配置
     * @param cronExpression  Cron 表达式
     */
    public FixedPointTimer(@NonNull ITimerTaskConfig<T> timerTaskConfig,
                           @NonNull String cronExpression,
                           @NonNull Class<T> resultType) {
        this.timerTaskConfig = timerTaskConfig;
        this.cron = CronExpression.parse(cronExpression);
        this.nextTriggerTime = ChinaDateUtil.Instance.getNextTriggerTime(cron, LocalDateTime.now());
        this.resultType = resultType;
    }

    /**
     * 获取 TimerTaskConfig
     *
     * @return 任务配置。
     */
    @Override
    public @NonNull ITimerTaskConfig<T> getTimerTaskConfig() {
        return timerTaskConfig;
    }

    /**
     * 判断是否到了执行时间。
     *
     * @param currentTime 当前时间戳（毫秒）。
     * @return 是否到了执行时间。
     */
    @Override
    public boolean isTime(long currentTime) {
        return currentTime >= nextTriggerTime;
    }

    /**
     * 每次执行成功时更新定时器的状态
     *
     * @param currentTime 当前时间戳（毫秒）。
     */
    @Override
    public void update(long currentTime) {
        this.nextTriggerTime = ChinaDateUtil.Instance.getNextTriggerTime(cron, currentTime);
    }

    /**
     * 使用 iTaskService 来执行任务。
     * 同时使用 timerTaskConfig 中的 TaskFutureChain 来配置任务的 future 链
     *
     * @param iTaskService 任务服务。
     */
    @Override
    public void execute(ITaskService iTaskService) {
        var taskFuture = iTaskService.subscribe(timerTaskConfig.getTaskConfig(), resultType);
        // 配置任务的 future 链。
        var chain = timerTaskConfig.getTaskFutureChain();
        if(chain != null) {
            var finalFuture = chain.apply(taskFuture);
            finalFuture.finish();
        } else {
            taskFuture.finish();
        }
    }
}
