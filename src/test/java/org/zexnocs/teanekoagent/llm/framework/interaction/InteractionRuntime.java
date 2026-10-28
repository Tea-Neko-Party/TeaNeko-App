package org.zexnocs.teanekoagent.llm.framework.interaction;

import org.jspecify.annotations.NonNull;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.actuator.execution.ResourceBudget;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.Mockito.*;

/**
 * 手动推进业务队列、时钟及截止时间回调的离线夹具，供 LLM 和上层服务复用，不创建真实线程池。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
public final class InteractionRuntime implements Executor {
    public final ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
    final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-28T00:00:00Z"));
    private final Queue<Runnable> work = new ArrayDeque<>();
    private final List<Entry> timers = new ArrayList<>();
    public final Clock clock = new Clock() {
        /**
         * 固定测试时钟的时区，避免依赖机器默认设置。
         *
         * @return UTC 时区
         */
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }

        /**
         * 本夹具只使用 Instant，时区转换不改变受控时间来源。
         *
         * @param zone Clock 接口要求的目标时区
         * @return 当前手动时钟
         */
        @Override public Clock withZone(ZoneId zone) { return this; }

        /**
         * 提供由用例主动推进的时间。
         *
         * @return 当前测试时刻
         */
        @Override public Instant instant() { return now.get(); }
    };

    /**
     * 捕获实际 Core 使用的 Callable 计时重载，每个取消句柄独立可观察。
     */
    public InteractionRuntime() {
        doAnswer(invocation -> {
            Callable<?> callback = invocation.getArgument(0);
            long delay = invocation.getArgument(1);
            TimeUnit unit = invocation.getArgument(2);
            var done = new AtomicBoolean();
            var future = mock(ScheduledFuture.class);
            when(future.cancel(false)).thenAnswer(ignored -> !done.getAndSet(true));
            when(future.isCancelled()).thenAnswer(ignored -> done.get());
            timers.add(new Entry(now.get().plusNanos(unit.toNanos(delay)), callback, done));
            return future;
        }).when(scheduler).schedule(org.mockito.ArgumentMatchers.<Callable<Object>>any(), anyLong(), any(TimeUnit.class));
    }

    /**
     * 只登记业务工作，是否开始由用例主动决定。
     *
     * @param command 需要排队的阶段准备任务
     */
    @Override public void execute(@NonNull Runnable command) { work.add(command); }

    /**
     * 按排队顺序执行所有当前可运行工作，期间产生的新工作也继续推进。
     */
    public void runAll() {
        while (!work.isEmpty()) {
            work.remove().run();
        }
    }

    /**
     * 推进时间并按到期顺序运行未取消的计时回调，不等待真实时间。
     *
     * @param amount 本次需要推进的时长
     */
    public void advance(Duration amount) {
        now.updateAndGet(value -> value.plus(amount));
        for (;;) {
            var due = timers.stream().filter(entry -> !entry.done().get() && !entry.due().isAfter(now.get()))
                    .min(Comparator.comparing(Entry::due));
            if (due.isEmpty()) {
                return;
            }
            var entry = due.orElseThrow();
            entry.done().set(true);
            try {
                entry.callback().call();
            } catch (Exception failure) {
                throw new AssertionError("测试截止时间回调失败。", failure);
            }
        }
    }

    /**
     * 统计尚未结束的计时任务，供阶段及拥有者关闭后的资源断言使用。
     *
     * @return 尚未取消或执行的计时任务数
     */
    public long pendingTimers() { return timers.stream().filter(entry -> !entry.done().get()).count(); }

    /**
     * 为独立请求创建使用手动时钟的宿主控制，默认一天上限不会干扰交互自己的时限。
     *
     * @param limits 宿主总限额，与子交互局部设置分别验证
     * @return 由用例负责关闭的父控制
     */
    public ExecutionControl parent(LLMCallLimits limits) {
        ResourceBudget budget = limits.newBudget();
        return ExecutionControl.withDeadline(budget, now.get().plus(Duration.ofDays(1)), clock, scheduler);
    }

    /**
     * 保存一个不可变到期定位与其独立取消标记。
     *
     * @param due 计时回调的触发时刻
     * @param callback 实际 Core 登记的终止回调
     * @param done 回调是否已执行或取消
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
     * @version 4.5.0-l
     */
    private record Entry(Instant due, Callable<?> callback, AtomicBoolean done) {}
}
