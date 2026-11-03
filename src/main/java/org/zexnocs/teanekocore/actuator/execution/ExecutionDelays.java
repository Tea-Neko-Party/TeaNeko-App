package org.zexnocs.teanekocore.actuator.execution;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
/**
 * 借用调度器的可取消等待，不阻塞执行线，也不关闭原控制。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.6.2-l
 * @version 4.6.2-l
 */
public final class ExecutionDelays {
    /** 禁止实例化无状态等待工具。 */
    private ExecutionDelays() {}
    /**
     * 在控制终止、调度失败或等待结束时完成并解除资源。
     * @param control 借用控制
     * @param scheduler 借用调度器
     * @param duration 有界等待
     * @return 实际等待阶段
     * @throws IllegalArgumentException 当时长不在零至一天时
     */
    public static CompletionStage<Void> waitFor(ExecutionControl control, ScheduledExecutorService scheduler, Duration duration) {
        if (duration.isNegative() || duration.compareTo(Duration.ofDays(1)) > 0) { throw new IllegalArgumentException("等待时长超出允许范围。"); }
        var result = new CompletableFuture<Void>(); var timer = new AtomicReference<ScheduledFuture<?>>();
        var registration = control.onTermination(reason -> result.completeExceptionally(new ExecutionStoppedException(reason)));
        try {
            control.checkActive();
            if (!result.isDone()) { timer.set(scheduler.schedule(() -> {
                try { control.checkActive(); result.complete(null); } catch (RuntimeException stopped) { result.completeExceptionally(stopped); }
            }, duration.toNanos(), TimeUnit.NANOSECONDS)); }
        } catch (RuntimeException failure) { result.completeExceptionally(failure); }
        result.whenComplete((ignored, failure) -> { registration.close(); var task = timer.get(); if (task != null) { task.cancel(false); } });
        return result;
    }
}
