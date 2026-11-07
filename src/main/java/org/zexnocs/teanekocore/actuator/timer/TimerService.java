package org.zexnocs.teanekocore.actuator.timer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理定时器的服务。
 * <br>2026/11/03：注册真实目录，触发与管理暂停共用门禁。
 * <br>2026/11/04：先验证展示声明再纳入实际调度，调度循环只读展示。
 * <br>2026/11/04：补全定时任务的详细中文介绍，原调度和控制意图保持不变。
 * <br>2026/11/07：固定间隔循环直接使用同一调度器，取消异步重复投递。
 *
 * @author zExNocs
 * @date 2026/02/14
 * @since 4.0.0
 * @version 4.8.7-l
 */
@Service
public class TimerService implements ITimerService {
    private @org.jspecify.annotations.Nullable TimerDirectory directory;

    /**
     * 2026/11/03：安装原计时器的观测及触发门禁，不启动第二套调度器。
     * @param directory 当前进程目录
     */
    @Autowired
    public void installDirectory(TimerDirectory directory) {
        this.directory = directory;
        timers.forEach(directory::register);
    }
    /// 定时器集合
    private final Set<ITimer<?>> timers;
    private final ITaskService iTaskService;

    /** @param iTaskService 原任务服务 */
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
        // 2026/11/04：声明冲突时不能留下已执行但管理注册失败的计时器。
        if (directory != null) {
            directory.register(timer);
        }
        timers.add(timer);
    }

    // --------------- 计时器循环 -----------------
    /** 2026/11/03：原循环使用管理门禁，暂停不会撤销已经提交的任务。 */
    // 2026/11/06：直接在同一调度器按 fixedDelay 驱动，避免异步二次投递形成积压及关闭竞态。
    // 2026/11/04：调度循环不能被网页暂停，否则全部业务计时器都会停止。
    // 2026/11/04：补充任务用途、执行边界与网页控制说明。
    @TimerDisplay(category = "core.maintenance", categoryName = "Core 维护", name = "Core 定时调度循环",
        description = "按配置的短轮询间隔驱动 Core 定时器，检查存活条件、暂停状态和触发时间，再提交符合条件的业务任务。"
            + "本卡片表示调度循环的运行情况，不表示某个异步业务已经处理完成；可在单次运行列表查看已提交任务。"
            + "该入口只读，避免停止循环后使所有依赖它的业务定时器同时失去调度。", controllable = false)
    @Scheduled(fixedDelayString = "${tea-neko.timer.update-delay-ms:10}", scheduler = "timerScheduler")
    public void timerLoop() {
        // 获得当前的时间戳。
        var now = Instant.now();

        // 遍历所有定时器，更新它们的状态。
        List<ITimer<?>> copy = new ArrayList<>(timers);
        List<ITimer<?>> toRemove = new ArrayList<>();

        for (var timer : copy) {
            // 2026/11/03：所有时间和生命周期判定均在门禁内重新执行。
            Runnable update = () -> {
                var livable = timer.getTimerTaskConfig().getLivable();
                if (livable != null && !livable.isAlive()) {
                    toRemove.add(timer);
                    return;
                }
                var pausable = timer.getTimerTaskConfig().getPausable();
                if (pausable != null && pausable.isPaused() || !timer.isTime(now)) {
                    return;
                }
                timer.update(now);
                timer.execute(iTaskService);
            };
            if (directory == null) {
                update.run();
            } else {
                directory.visit(timer, update);
            }
        }

        // 删除已经销毁的定时器。
        for (var key : toRemove) {
            timers.remove(key);
            if (directory != null) {
                directory.remove(key);
            }
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
     * @param <T> 原任务结果类型
     * @param resultType 任务结果类型
     * @return 定时器任务配置，用于设置 Future 执行链与生命周期
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
     * @param <T> 原任务结果类型
     * @param resultType 任务结果类型
     * @return 定时器任务配置，用于设置 Future 执行链与生命周期
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
     * @param <T> 原任务结果类型
     * @param resultType 任务结果类型
     * @return 定时器任务配置，用于设置 Future 执行链与生命周期
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
     * @param <T> 原任务结果类型
     * @param resultType     任务结果类型
     * @return 定时器任务配置，用于设置 Future 执行链与生命周期
     * @see CronTimer
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
        var timer = new CronTimer<>(timerTaskConfig, cronExpression, resultType);
        register(timer);
        return timerTaskConfig;
    }
}
