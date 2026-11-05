package org.zexnocs.teanekocore.actuator.timer;

import org.jspecify.annotations.Nullable;
import org.springframework.scheduling.config.*;
import org.springframework.scheduling.support.ScheduledMethodRunnable;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;
import org.zexnocs.teanekocore.actuator.timer.interfaces.ITimerViewSource;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.*;

/**
 * 读取 Spring 实际注册任务，按原方法声明展示；不创建调度器，不接管暂停或取消。
 * 弱键仅维持同一活任务的观测标识，不延长已移除任务及其业务对象的生命周期。
 * <br>2026/11/05：复用任务持有者发现缓存，观测时不遍历 Spring Bean 目录。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.7.1-l
 * @version 4.8.2-l
 */
@Component
public final class SpringTimerViewSource implements ITimerViewSource {
    private final TimerSourceScanner scanner;
    private final Map<ScheduledTask, UUID> ids = new WeakHashMap<>();

    /**
     * 2026/11/05：查询只读取已发现的任务持有者，构造不扫描容器。
     * @param scanner 专用调度来源目录
     */
    public SpringTimerViewSource(TimerSourceScanner scanner) {
        this.scanner = scanner;
    }

    /** @return 原 Spring 调度来源 */
    @Override
    public String source() {
        return "spring";
    }

    /**
     * 2026/11/05：持有者缓存不替代实时任务集合，已移除任务不会继续展示。
     * @param limit 最大观测数量
     * @return 原任务的只读窗口，同一任务由多个持有者公开时只展示一次
     */
    @Override
    public synchronized List<TimerDirectory.View> snapshot(int limit) {
        var seen = Collections.newSetFromMap(new IdentityHashMap<ScheduledTask, Boolean>());
        var result = new ArrayList<TimerDirectory.View>();
        for (var holder : scanner.holders()) {
            for (var scheduled : holder.getScheduledTasks()) {
                if (!seen.add(scheduled)) {
                    continue;
                }
                var task = scheduled.getTask();
                var metadata = TimerPresentation.from(method(task));
                if (!metadata.visible()) {
                    continue;
                }
                var next = scheduled.nextExecution();
                String state = task.getLastExecutionOutcome().status() == TaskExecutionOutcome.Status.STARTED
                        ? "RUNNING" : next == null ? "UNKNOWN" : "ACTIVE";
                result.add(new TimerDirectory.View(ids.computeIfAbsent(scheduled, ignored -> UUID.randomUUID()),
                        metadata.name().isEmpty() ? "Spring 定时任务" : metadata.name(), state, 0,
                        observation(task, next), List.of(), metadata.category(), metadata.categoryName(),
                        metadata.description(), source()));
                if (result.size() == limit) {
                    return List.copyOf(result);
                }
            }
        }
        return List.copyOf(result);
    }

    /**
     * 只在此处适配当前 Spring Task 的结果观测包装，不按名称或 toString 猜测方法。
     * Spring 未公开包装器解包接口；框架升级后结构变化必须明确失败并由真实任务测试定位。
     * 自定义 Runnable 没有方法声明时保留未分类。
     * @param task 实际注册的 Spring 任务
     * @return 原 ScheduledMethodRunnable 的方法，其他任务为空
     * @throws IllegalStateException 当框架包装合同已变化时
     */
    private static @Nullable Method method(Task task) {
        Runnable runnable = task.getRunnable();
        if (runnable.getClass().getEnclosingClass() == Task.class) {
            var field = ReflectionUtils.findField(runnable.getClass(), "runnable", Runnable.class);
            if (field == null) {
                throw new IllegalStateException("Spring 定时任务观测包装已变化，无法关联实际方法。");
            }
            ReflectionUtils.makeAccessible(field);
            runnable = (Runnable) java.util.Objects.requireNonNull(ReflectionUtils.getField(field, runnable),
                    "Spring 定时任务包装缺少原执行方法。");
        }
        return runnable instanceof ScheduledMethodRunnable method ? method.getMethod() : null;
    }

    /**
     * @param task 原 Spring 规则
     * @param next 框架公开的下次预计执行时间，无法确认时为空
     * @return 原格式规则，不推测异步业务完成或未公开的时区
     */
    private static TimerObservation observation(Task task, @Nullable Instant next) {
        if (task instanceof CronTask cron) {
            return new TimerObservation("CRON", cron.getExpression(), next, false, "");
        }
        if (task instanceof FixedDelayTask delay) {
            return new TimerObservation("FIXED_DELAY", "Spring 方法结束后等待：" + delay.getIntervalDuration(), next, false, "");
        }
        if (task instanceof FixedRateTask rate) {
            return new TimerObservation("FIXED_RATE", "Spring 固定触发间隔：" + rate.getIntervalDuration(), next, false, "");
        }
        return new TimerObservation("CUSTOM", "Spring 自定义规则，未提供可公开的周期", next, false, "");
    }
}
