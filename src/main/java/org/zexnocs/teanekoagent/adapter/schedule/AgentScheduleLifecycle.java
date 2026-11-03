package org.zexnocs.teanekoagent.adapter.schedule;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.schedule.AgentScheduleService;
import org.zexnocs.teanekocore.actuator.timer.interfaces.ITimerService;
import org.zexnocs.teanekocore.logger.ILogger;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 在原扫描器及数据库就绪后恢复一次任务；启动失败关闭调度并保留明确不可用状态。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.5.0-l
 * @version 4.5.0-l
 */
@Service
public final class AgentScheduleLifecycle {
    private final AgentScheduleService schedules;
    private final ITimerService timers;
    private final ILogger logger;
    private final AtomicBoolean started = new AtomicBoolean();
    private final CompletableFuture<Void> ready = new CompletableFuture<>();

    /**
     * 保存原服务，构造不访问数据库。
     * @param schedules 调度服务
     * @param timers 原 Core Timer
     * @param logger 项目日志
     */
    public AgentScheduleLifecycle(AgentScheduleService schedules, ITimerService timers, ILogger logger) {
        this.schedules = schedules; this.timers = timers; this.logger = logger;
    }

    /** 启动时等待实际恢复完成；事件重放不重复注册定时器，异常不泄露任务正文。 */
    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (!started.compareAndSet(false, true)) { return; }
        try {
            schedules.start(timers).toCompletableFuture().join(); ready.complete(null);
        } catch (RuntimeException failure) {
            schedules.close(); ready.completeExceptionally(new IllegalStateException("提醒任务恢复失败，调度已关闭。"));
            logger.error("Agent 计划任务", "提醒任务恢复失败，调度已关闭；任务记录保留，请检查数据库与定时器后重启。");
        }
    }

    /**
     * 原生命令必须等待恢复，防止恢复过程把新创建或执行的任务当作旧任务处理。
     * @return 只读就绪阶段
     */
    public CompletionStage<Void> ready() { return ready.minimalCompletionStage(); }
}
