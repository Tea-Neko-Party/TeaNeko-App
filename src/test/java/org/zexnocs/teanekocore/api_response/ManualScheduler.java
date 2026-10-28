package org.zexnocs.teanekocore.api_response;

import org.jspecify.annotations.NonNull;
import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.scheduler.Scheduler;

import java.time.Duration;
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.concurrent.TimeUnit;

/**
 * 测试时钟/任务队列，不创建线程，不使用真实等待。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
final class ManualScheduler implements Scheduler {
    private final PriorityQueue<Entry> queue = new PriorityQueue<>(Comparator.comparingLong((Entry e) -> e.at).thenComparingLong(e -> e.order));
    private long now;
    private long nextOrder;
    private boolean disposed;

    /**
     * 将任务放入手动时间队列，不启动实际线程；延迟由测试推进时钟后触发。
     *
     * @param action 需要运行的处理回调
     * @return 满足本方法约束的 {@code Disposable} 结果
     */
    @Override
    @NonNull
    public Disposable schedule(@NonNull Runnable action) {
        return schedule(action, 0, TimeUnit.NANOSECONDS);
    }

    /**
     * 将任务放入手动时间队列，不启动实际线程；延迟由测试推进时钟后触发。
     *
     * @param action 需要运行的处理回调
     * @param delay 提交任务前的延迟
     * @param unit 任务延迟或时钟读取所使用的时间单位
     * @return 满足本方法约束的 {@code Disposable} 结果
     * @throws java.util.concurrent.RejectedExecutionException 当该操作的校验或下游调用失败时
     */
    @Override
    @NonNull
    public Disposable schedule(@NonNull Runnable action, long delay, @NonNull TimeUnit unit) {
        if (disposed) {
            throw new java.util.concurrent.RejectedExecutionException("Manual scheduler is disposed.");
        }
        var entry = new Entry(now + unit.toNanos(delay), nextOrder++, action);
        queue.add(entry);
        return entry;
    }

    /**
     * 创建可独立释放的受控 Worker，记录其任务便于资源生命周期断言。
     *
     * @return 满足本方法约束的 {@code Worker} 结果
     */
    @Override
    @NonNull
    public Worker createWorker() {
        return new Worker() {
            private final Disposable.Composite tasks = Disposables.composite();

            /**
             * 把回调交给手动时间队列，并绑定当前 Worker 的释放状态。
             *
             * @param action 需要运行的处理回调
             * @return 满足本方法约束的 {@code Disposable} 结果
             */
            @Override
            @NonNull
            public Disposable schedule(@NonNull Runnable action) {
                return schedule(action, 0, TimeUnit.NANOSECONDS);
            }

            /**
             * 把回调交给手动时间队列，并绑定当前 Worker 的释放状态。
             *
             * @param action 需要运行的处理回调
             * @param delay 提交任务前的延迟
             * @param unit 任务延迟或时钟读取所使用的时间单位
             * @return 满足本方法约束的 {@code Disposable} 结果
             */
            @Override
            @NonNull
            public Disposable schedule(@NonNull Runnable action, long delay, @NonNull TimeUnit unit) {
                var task = ManualScheduler.this.schedule(action, delay, unit);
                tasks.add(task);
                return task;
            }

            /**
             * 取消当前受控 Worker 及其未执行任务，避免影响其他 Worker。
             */
            @Override
            public void dispose() { tasks.dispose(); }

            /**
             * 报告当前受控 Worker 是否已经释放。
             *
             * @return 当前校验或原子操作是否满足本方法约定的条件
             */
            @Override
            public boolean isDisposed() { return tasks.isDisposed(); }
        };
    }

    /**
     * 按请求时间单位读取手动时钟，不受系统时间或测试运行速度影响。
     *
     * @param unit 任务延迟或时钟读取所使用的时间单位
     * @return 按当前输入与状态计算的数值
     */
    @Override
    public long now(TimeUnit unit) {
        return unit.convert(now, TimeUnit.NANOSECONDS);
    }

    /**
     * 推进受控时钟并执行到期任务，保持到期顺序和相同时间的稳定排列。
     *
     * @param amount 需要推进的受控时长
     */
    void advance(Duration amount) {
        long target = now + amount.toNanos();
        while (!queue.isEmpty() && queue.peek().at <= target) {
            var entry = queue.remove();
            now = Math.max(now, entry.at);
            if (!entry.disposed) {
                entry.action.run();
            }
        }
        now = target;
    }

    /**
     * 只移动时钟而暂不执行回调，模拟调度器繁忙导致的迟到计时通知。
     *
     * @param amount 需要推进的受控时长
     */
    void moveClockWithoutRunning(Duration amount) {
        now += amount.toNanos();
    }

    /**
     * 统计当前尚未取消的待执行任务，供资源清理断言使用。
     *
     * @return 按当前输入与状态计算的数值
     */
    long pendingTasks() {
        return queue.stream().filter(entry -> !entry.disposed).count();
    }

    /**
     * 取消并释放手动调度器的在途任务，后续提交按关闭状态处理。
     */
    @Override
    public void dispose() { disposed = true; queue.forEach(Entry::dispose); queue.clear(); }

    /**
     * 提供调度器当前是否已释放的状态，供资源生命周期检查。
     *
     * @return 当前校验或原子操作是否满足本方法约定的条件
     */
    @Override
    public boolean isDisposed() { return disposed; }

    /**
     * 手动调度队列中的可取消任务，保留到期时间和稳定排序序号。
     *
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
     * @version 4.5.0-l
     */
    private static final class Entry implements Disposable {
        final long at;
        final long order;
        final Runnable action;
        boolean disposed;

        /**
         * 通过构造参数固定当前实例的依赖与初始状态，避免执行时再从全局环境查找协作者。
         *
         * @param at 用于本次测试的 {@code at} 数据
         * @param order 用于本次测试的 {@code order} 数据
         * @param action 需要运行的处理回调
         */
        Entry(long at, long order, Runnable action) {
            this.at = at;
            this.order = order;
            this.action = action;
        }

        /**
         * 将当前排队任务标记为取消，后续推进时钟不再运行其回调。
         */
        @Override
        public void dispose() { disposed = true; }

        /**
         * 报告当前任务是否取消，供手动时间队列筛除过期工作。
         *
         * @return 当前校验或原子操作是否满足本方法约定的条件
         */
        @Override
        public boolean isDisposed() { return disposed; }
    }
}
