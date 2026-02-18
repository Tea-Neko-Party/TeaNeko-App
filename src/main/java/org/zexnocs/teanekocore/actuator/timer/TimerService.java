package org.zexnocs.teanekocore.actuator.timer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.actuator.task.TaskConfig;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskResult;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskService;
import org.zexnocs.teanekocore.actuator.timer.interfaces.ITimer;
import org.zexnocs.teanekocore.actuator.timer.interfaces.ITimerService;
import org.zexnocs.teanekocore.actuator.timer.interfaces.ITimerTaskConfig;
import org.zexnocs.teanekocore.framework.function.MethodCallable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理定时器的服务。
 *
 * @author zExNocs
 * @date 2026/02/14
 */
@Service
public class TimerService implements ITimerService {
    /// 定时器集合
    private final Set<ITimer<?>> timers;
    private final ITaskService iTaskService;

    @Lazy
    @Autowired
    public TimerService(ITaskService iTaskService) {
        this.timers = ConcurrentHashMap.newKeySet();
        this.iTaskService = iTaskService;
    }

    /**
     * 注册一个定时器。
     * 自行选择定时器和配置任务 config。
     *
     * @param timer 定时器。
     */
    @Override
    public void register(ITimer<?> timer) {
        timers.add(timer);
    }

    // --------------- 计时器循环 -----------------
    @Async("timerScheduler")
    @Scheduled(fixedDelayString = "${tea-neko.timer.update-delay-ms}")
    public void timerLoop() {
        // 获得当前的时间戳。
        long now = System.currentTimeMillis();

        // 遍历所有定时器，更新它们的状态。
        List<ITimer<?>> copy = new ArrayList<>(timers);
        List<ITimer<?>> toRemove = new ArrayList<>();

        for (var timer: copy) {
            // 如果定时器已经销毁，则删除。
            var livable = timer.getTimerTaskConfig().getLivable();
            if (livable != null && !livable.isAlive()) {
                toRemove.add(timer);
                continue;
            }
            // 如果定时器已经暂停，则跳过。
            var pausable = timer.getTimerTaskConfig().getPausable();
            if (pausable != null && pausable.isPaused()) {
                continue;
            }
            // 如果定时器没有到达执行时间，则跳过。
            if (!timer.isTime(now)) {
                continue;
            }
            // ----- 已通过定时器属性判断执行 ------
            timer.update(now);
            timer.execute(iTaskService);
        }

        // 删除已经销毁的定时器。
        for (var key: toRemove) {
            timers.remove(key);
        }
    }

    // --------------- 以下为快速注册定时器的方法 ---------------
    /**
     * 使用一个 rate 和一个 callable 注册一个默认规则的定时器。
     * 每次 rate 都会生成一个 task 来执行，无论上一个任务是否完成。
     * 不保证任务只有一个线程执行。
     * 默认规则：
     * 1. 不进行重试和错误处理
     * 2. 10min 没有返回结果会过期
     *
     * @param taskName   任务名称
     * @param taskStage  任务阶段
     * @param callable   任务执行的 Callable
     * @param rate       定时器的周期
     * @param resultType 任务结果类型
     * @return {@link ITimerTaskConfig }<{@link T }> 定时器任务配置对象，用于设置任务的 Future 执行链与生命周期
     */
    @Override
    public <T> ITimerTaskConfig<T> registerByRate(String taskName,
                                                  String taskStage,
                                                  MethodCallable<ITaskResult<T>> callable,
                                                  Duration rate,
                                                  Class<T> resultType) {
        var taskConfig = TaskConfig.<T>builder()
                .name(taskName)
                .taskStageNamespace(taskStage)
                .callable(callable)
                .build();
        var timerTaskConfig = TimerTaskConfig.<T>builder()
                .taskConfig(taskConfig)
                .build();
        var timer = new FixedRateTimer<>(timerTaskConfig, rate, resultType);
        register(timer);
        return timerTaskConfig;
    }

    /**
     * 使用一个 delay 和一个 callable 注册一个默认规则的定时器。
     * 每次 task 完成后都会经过该 delay 来生成一个新的 task 来执行。
     * 因此执行时间为 上一次 task 执行时间 + delay。
     * 保证任务永远只有一个线程执行。
     * 默认规则：
     * 1. 不进行重试和错误处理
     * 2. 10min 没有返回结果会过期
     *
     * @param taskName   任务名称
     * @param taskStage  任务阶段
     * @param callable   任务执行的 Callable
     * @param delay      定时器的周期
     * @param resultType 任务结果类型
     * @return {@link ITimerTaskConfig }<{@link T }> 定时器任务配置对象，用于设置任务的 Future 执行链与生命周期
     */
    @Override
    public <T> ITimerTaskConfig<T> registerByDelay(String taskName,
                                                   String taskStage,
                                                   MethodCallable<ITaskResult<T>> callable,
                                                   Duration delay,
                                                   Class<T> resultType) {
        var taskConfig = TaskConfig.<T>builder()
                .name(taskName)
                .taskStageNamespace(taskStage)
                .callable(callable)
                .build();
        var timerTaskConfig = TimerTaskConfig.<T>builder()
                .taskConfig(taskConfig)
                .build();
        var timer = new FixedDelayTimer<>(timerTaskConfig, delay, resultType);
        register(timer);
        return timerTaskConfig;
    }

    /**
     * 使用一个 rate 和一个 callable 注册一个默认规则的智能定时器。
     * 每个 rate 都会尝试生成一个 task 来执行；如果上次的 task 没有完成则会一直等待上次 task 完成。
     * 执行时间为 max(rate, 上一次 task 执行时间)。
     * 保证任务永远只有一个线程执行。
     * 默认规则：
     * 1. 不进行重试和错误处理
     * 2. 10min 没有返回结果会过期
     *
     * @param taskName   任务名称
     * @param taskStage  任务阶段
     * @param callable   任务执行的 Callable
     * @param rate       定时器的周期
     * @param resultType 任务结果类型
     * @return {@link ITimerTaskConfig }<{@link T }> 定时器任务配置对象，用于设置任务的 Future 执行链与生命周期
     */
    @Override
    public <T> ITimerTaskConfig<T> registerBySmartRate(String taskName,
                                                       String taskStage,
                                                       MethodCallable<ITaskResult<T>> callable,
                                                       Duration rate,
                                                       Class<T> resultType) {
        var taskConfig = TaskConfig.<T>builder()
                .name(taskName)
                .taskStageNamespace(taskStage)
                .callable(callable)
                .build();
        var timerTaskConfig = TimerTaskConfig.<T>builder()
                .taskConfig(taskConfig)
                .build();
        var timer = new SmartRateTimer<>(timerTaskConfig, rate, resultType);
        register(timer);
        return timerTaskConfig;
    }

    /**
     * 使用一个 cron 表达式和一个 callable 注册一个默认规则的定时器。
     *
     * @param taskName       任务名称
     * @param taskStage      任务阶段
     * @param callable       任务执行的 Callable
     * @param cronExpression 定时器的 cron 表达式
     * @param resultType     任务结果类型
     * @return {@link ITimerTaskConfig }<{@link T }> 定时器任务配置对象，用于设置任务的 Future 执行链与生命周期
     * @see FixedPointTimer
     */
    @Override
    public <T> ITimerTaskConfig<T> registerByCron(String taskName,
                                                  String taskStage,
                                                  MethodCallable<ITaskResult<T>> callable,
                                                  String cronExpression,
                                                  Class<T> resultType) {
        var taskConfig = TaskConfig.<T>builder()
                .name(taskName)
                .taskStageNamespace(taskStage)
                .callable(callable)
                .build();
        var timerTaskConfig = TimerTaskConfig.<T>builder()
                .taskConfig(taskConfig)
                .build();
        var timer = new FixedPointTimer<>(timerTaskConfig, cronExpression, resultType);
        register(timer);
        return timerTaskConfig;
    }
}
