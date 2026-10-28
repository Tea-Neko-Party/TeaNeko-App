package org.zexnocs.teanekocore.actuator.execution;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 单次执行的取消、截止时间及共享资源预算，不持有平台或业务身份。
 * 拥有者在执行结束后 close；传入的 scheduler 始终由调用方管理。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
public final class ExecutionControl implements AutoCloseable {
    /**
     * 首次生效的执行停止原因，用于区分取消、截止时间和拥有者关闭。
     *
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
     * @version 4.5.0-l
     */
    public enum StopReason { CANCELLED, DEADLINE_EXCEEDED, CLOSED }

    private final UUID id = UUID.randomUUID();
    private final UUID parentId;
    private final List<UUID> ancestors;
    private final ResourceBudget budget;
    private final Clock clock;
    private final Instant deadline;
    private final ReentrantLock lifecycleLock = new ReentrantLock();
    private final CompletableFuture<StopReason> termination = new CompletableFuture<>();
    private final AtomicReference<ScheduledFuture<?>> deadlineTask = new AtomicReference<>();
    private volatile StopReason stopReason;
    private final AtomicBoolean stopPublished = new AtomicBoolean();
    private final Map<TerminationRegistration, Boolean> terminationListeners = new LinkedHashMap<>();

    /**
     * 可解除的轻量终止监听。close 后释放回调引用；不等待已经开始执行的回调。
     *
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
     * @version 4.5.0-l
     */
    public final class TerminationRegistration implements AutoCloseable {
        private final AtomicReference<Consumer<StopReason>> listener;

        /**
         * 保存可解除的终止回调，供资源持有者在完成后释放引用。
         *
         * @param listener 资源终止回调
         */
        private TerminationRegistration(Consumer<StopReason> listener) {
            this.listener = new AtomicReference<>(listener);
        }

        /**
         * 至多消费一次终止回调，隔离单个监听器的运行时异常以继续释放其他资源。
         *
         * @param reason 首次生效的终止原因
         */
        private void notifyStop(StopReason reason) {
            var action = listener.getAndSet(null);
            if (action != null) {
                try {
                    action.accept(reason);
                } catch (RuntimeException ignored) {
                    // 与 CompletionStage 的监听语义一致：单个监听异常不阻断其他资源释放。
                }
            }
        }

        /**
         * 解除终止监听并移除保存的引用；不等待已经开始的回调。
         */
        @Override
        public void close() {
            listener.set(null);
            lifecycleLock.lock();
            try {
                terminationListeners.remove(this);
            } finally {
                lifecycleLock.unlock();
            }
        }
    }

    /**
     * 建立执行控制的父链路、时钟和共享预算；截止时间的调度由创建入口负责。
     *
     * @param parentId 父执行标识；根执行可为空
     * @param budget 本次执行与子执行共享的资源预算
     * @param clock 计算时间边界使用的时钟
     * @param deadline 执行截止时间；未设置时可为空
     * @throws NullPointerException 当必需的输入或依赖为空时
     */
    private ExecutionControl(UUID parentId, ResourceBudget budget, Clock clock, Instant deadline) {
        this(parentId, budget, clock, deadline, List.of());
    }

    /**
     * 保存不可变祖先标识，不保留父控制对象或通过预算对象猜测调用关系。
     * @param parentId 直接父执行标识，根执行为空
     * @param budget 共享预算
     * @param clock 截止时间来源
     * @param deadline 可选截止时间
     * @param ancestors 从直接父执行向根排列的标识
     * @throws NullPointerException 当预算、时钟或祖先列表为空时
     */
    private ExecutionControl(UUID parentId, ResourceBudget budget, Clock clock, Instant deadline, List<UUID> ancestors) {
        this.parentId = parentId;
        this.ancestors = List.copyOf(ancestors);
        this.budget = Objects.requireNonNull(budget, "共享预算不能为空。");
        this.clock = Objects.requireNonNull(clock, "执行时钟不能为空。");
        this.deadline = deadline;
    }

    /**
     * 创建使用当前时钟、尚未终止的根执行控制；传入预算供后续子执行共享。
     *
     * @return 独立的执行控制，预算与资源归属遵循创建入口说明
     */
    public static ExecutionControl create() {
        return create(new ResourceBudget(Map.of()));
    }

    /**
     * 创建使用当前时钟、尚未终止的根执行控制；传入预算供后续子执行共享。
     *
     * @param budget 本次执行与子执行共享的资源预算
     * @return 独立的执行控制，预算与资源归属遵循创建入口说明
     */
    public static ExecutionControl create(ResourceBudget budget) {
        return new ExecutionControl(null, budget, Clock.systemUTC(), null);
    }

    /**
     * 创建带截止时间的根控制；已到期时立即终止，借用的调度器仍由调用方管理。
     *
     * @param budget 本次执行与子执行共享的资源预算
     * @param deadline 执行截止时间，不能为空
     * @param clock 计算时间边界使用的时钟
     * @param scheduler 由装配方管理的调度器
     * @return 独立的执行控制，预算与资源归属遵循创建入口说明
     * @throws NullPointerException 当必需的输入或依赖为空时
     * @throws RuntimeException 当调度器无法建立截止时间任务时
     */
    public static ExecutionControl withDeadline(
            ResourceBudget budget, Instant deadline, Clock clock, ScheduledExecutorService scheduler) {
        Objects.requireNonNull(deadline, "deadline");
        Objects.requireNonNull(scheduler, "scheduler");
        var control = new ExecutionControl(null, budget, clock, deadline);
        control.armDeadline(scheduler);
        return control;
    }

    /**
     * 在生命周期锁外登记截止时间任务，失败时关闭已创建控制，避免遗留父监听。
     *
     * @param scheduler 借用的截止时间调度器
     * @throws RuntimeException 当调度器拒绝任务或无法建立计时任务时
     */
    private void armDeadline(ScheduledExecutorService scheduler) {
        if (stopReason != null) {
            return;
        }
        var remaining = Duration.between(clock.instant(), deadline);
        if (remaining.isNegative() || remaining.isZero()) {
            stop(StopReason.DEADLINE_EXCEEDED);
            return;
        }
        long delay;
        try {
            delay = remaining.toNanos();
        } catch (ArithmeticException overflow) {
            delay = Long.MAX_VALUE;
        }
        try {
            var task = scheduler.schedule(() -> stop(StopReason.DEADLINE_EXCEEDED), delay, TimeUnit.NANOSECONDS);
            deadlineTask.set(task);
            if (stopReason != null) {
                task.cancel(false);
            }
        } catch (RuntimeException failure) {
            close();
            throw failure;
        }
    }

    /**
     * 提供本次执行的稳定标识，用于关联任务与诊断记录。
     *
     * @return 本执行的唯一标识
     */
    public UUID id() {
        return id;
    }

    /**
     * 提供父执行标识；根执行不具有父标识。
     *
     * @return 父执行标识；根执行返回空
     */
    public Optional<UUID> parentId() {
        return Optional.ofNullable(parentId);
    }

    /**
     * 判断当前控制是否属于指定执行或其后代；只检查显式链路，不授予业务权限。
     * @param ancestor 包含自身在内的执行标识
     * @return 标识为自身或任一祖先时为 true
     * @throws NullPointerException 当执行标识为空时
     */
    public boolean isWithin(UUID ancestor) {
        Objects.requireNonNull(ancestor, "祖先执行标识不能为空。");
        return id.equals(ancestor) || ancestors.contains(ancestor);
    }

    /**
     * 暴露本执行与子执行共用的预算对象，不为子步骤重新分配额度。
     *
     * @return 当前父子执行共享的同一预算对象
     */
    public ResourceBudget budget() {
        return budget;
    }

    /**
     * 按本控制的时钟计算剩余时间；未设置截止时间时返回空，到期后返回零。
     *
     * @return 剩余可用时间；无截止时间返回空，到期返回零
     */
    public Optional<Duration> remainingTime() {
        if (deadline == null) {
            return Optional.empty();
        }
        var remaining = Duration.between(clock.instant(), deadline);
        return Optional.of(remaining.isNegative() ? Duration.ZERO : remaining);
    }

    /**
     * 提供不可由调用方改写的终止信号；信号包含首次生效的停止原因。
     *
     * @return 只读终止阶段，结果为首次生效的停止原因
     */
    public CompletionStage<StopReason> termination() {
        return termination.minimalCompletionStage();
    }

    /**
     * 资源适配器使用此入口，并在自身完成后解除监听；回调在生命周期锁外执行。
     *
     * @param listener 资源终止回调
     * @return 满足本方法约束的 {@code TerminationRegistration} 结果
     * @throws NullPointerException 当必需的输入或依赖为空时
     */
    public TerminationRegistration onTermination(Consumer<StopReason> listener) {
        var registration = new TerminationRegistration(Objects.requireNonNull(listener, "listener"));
        StopReason reason;
        lifecycleLock.lock();
        try {
            reason = stopReason;
            if (reason == null || lifecycleLock.getHoldCount() > 1) {
                terminationListeners.put(registration, Boolean.TRUE);
                reason = null; // 重入的 whileActive 尚未退出，由外层 publishStop 通知。
            }
        } finally {
            lifecycleLock.unlock();
        }
        if (reason != null) {
            registration.notifyStop(reason);
        }
        return registration;
    }

    /**
     * 子执行共享预算，继承父执行终止；取消子执行不反向取消父执行。
     *
     * @return 独立的执行控制，预算与资源归属遵循创建入口说明
     * @throws ExecutionStoppedException 当执行已取消、关闭或超过截止时间时
     */
    public ExecutionControl child() {
        return whileActive(() -> attachChild(deadline));
    }

    /**
     * 创建具有独立阶段截止时间的子控制，沿用父时钟与预算，截止时间不晚于父执行。
     * 子阶段到期只停止自身及其后代；调用方拥有子控制，调度器仍由装配方管理。
     *
     * @param timeout 从创建时刻开始计算的正阶段时限
     * @param scheduler 借用的计时调度器，回调在生命周期锁外登记
     * @return 已建立父终止监听和独立计时任务的子控制
     * @throws IllegalArgumentException 当阶段时限不是正值时
     * @throws NullPointerException 当时限或调度器为空时
     * @throws ExecutionStoppedException 当父执行已经终止时
     * @throws RuntimeException 当调度器拒绝任务时，已创建子控制会先关闭
     */
    public ExecutionControl child(Duration timeout, ScheduledExecutorService scheduler) {
        Objects.requireNonNull(timeout, "阶段时限不能为空。");
        Objects.requireNonNull(scheduler, "计时调度器不能为空。");
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("阶段时限必须大于零。");
        }
        var child = whileActive(() -> {
            Instant stageDeadline;
            try {
                stageDeadline = clock.instant().plus(timeout);
            } catch (java.time.DateTimeException | ArithmeticException overflow) {
                stageDeadline = Instant.MAX;
            }
            if (deadline != null && deadline.isBefore(stageDeadline)) {
                stageDeadline = deadline;
            }
            return attachChild(stageDeadline);
        });
        child.armDeadline(scheduler);
        return child;
    }

    /**
     * 在父生命周期锁内固定子控制与父监听，通知仍由终止发布逻辑在锁外执行。
     *
     * @param childDeadline 已收束于父边界的截止时间，可为空
     * @return 与父执行共享预算的子控制
     */
    private ExecutionControl attachChild(Instant childDeadline) {
        var lineage = new ArrayList<UUID>(ancestors.size() + 1);
        lineage.add(id); lineage.addAll(ancestors);
        var child = new ExecutionControl(id, budget, clock, childDeadline, lineage);
        var subscription = onTermination(child::stop);
        child.onTermination(ignored -> subscription.close());
        return child;
    }

    /**
     * 请求取消当前执行及其子执行；已完成的外部副作用不回滚。
     *
     * @return 本次是否首次把执行转为取消状态；已经停止时为 false
     */
    public boolean cancel() {
        return stop(StopReason.CANCELLED);
    }

    /**
     * 检查取消与截止时间，在停止后阻止调用方继续启动后续步骤。
     *
     * @throws ExecutionStoppedException 当执行已取消、关闭或超过截止时间时
     */
    public void checkActive() {
        whileActive(() -> null);
    }

    /**
     * 仅在执行仍有效时原子预占指定资源；取消检查与预算操作在同一生命周期锁中完成。
     *
     * @param key 定位目标数据的键
     * @param units 本次需要预占的正资源数量
     * @throws ExecutionStoppedException 当执行已取消、关闭或超过截止时间时
     * @throws BudgetExceededException 当共享资源预算不足以预占本次额度时
     * @throws IllegalArgumentException 当参数格式、范围或声明的类型约束不满足时
     */
    public void consume(BudgetKey key, long units) {
        whileActive(() -> {
            budget.consume(key, units);
            return null;
        });
    }

    /**
     * 将一次轻量提交与取消线性化；action 内不得进行 I/O 或等待其他任务。
     * 已经在取消前完成的提交不会被回滚。
     *
     * @param action 需要运行的处理回调
     * @param <T> 本方法约定的泛型数据类型
     * @return 满足本方法约束的 {@code T} 结果
     * @throws ExecutionStoppedException 当执行已取消、关闭或超过截止时间时
     * @throws NullPointerException 当必需的输入或依赖为空时
     */
    public <T> T whileActive(Supplier<T> action) {
        Objects.requireNonNull(action, "action");
        lifecycleLock.lock();
        try {
            if (stopReason == null && deadline != null && !clock.instant().isBefore(deadline)) {
                stopReason = StopReason.DEADLINE_EXCEEDED;
            }
            if (stopReason != null) {
                throw new ExecutionStoppedException(stopReason);
            }
            return action.get();
        } finally {
            lifecycleLock.unlock();
            publishStop();
        }
    }

    /**
     * 即便源 Stage 永不完成，终止也会使包装 Stage 完成。
     * 不假装回滚或强制中断源操作；HTTP 等适配器可订阅 termination 释放自己的资源。
     *
     * @param source 待处理的来源数据
     * @param <T> 本方法约定的泛型数据类型
     * @return 承载本次异步处理实际结果的可组合完成阶段
     * @throws NullPointerException 当必需的输入或依赖为空时
     */
    public <T> CompletionStage<T> guard(CompletionStage<T> source) {
        Objects.requireNonNull(source, "source");
        try {
            checkActive();
        } catch (ExecutionStoppedException stopped) {
            return CompletableFuture.<T>failedFuture(stopped).minimalCompletionStage();
        }
        var guarded = new CompletableFuture<T>();
        var subscription = onTermination(reason -> guarded.completeExceptionally(new ExecutionStoppedException(reason)));
        guarded.whenComplete((ignored, failure) -> subscription.close());
        try {
            source.whenComplete((value, failure) -> {
                try {
                    checkActive();
                    if (failure == null) {
                        guarded.complete(value);
                    } else {
                        guarded.completeExceptionally(failure);
                    }
                } catch (RuntimeException stopped) {
                    guarded.completeExceptionally(stopped);
                }
            });
        } catch (RuntimeException failure) {
            guarded.completeExceptionally(failure);
        }
        return guarded.minimalCompletionStage();
    }

    /**
     * 幂等关闭本控制并通知借用者释放资源；不关闭外部调度器或共享预算。
     */
    @Override
    public void close() {
        stop(StopReason.CLOSED);
    }

    /**
     * 在生命周期锁内保存首个停止原因，并在锁外发布一次终止通知。
     *
     * @param reason 首次生效的终止原因
     * @return 本次是否成功记录首个停止原因
     */
    private boolean stop(StopReason reason) {
        boolean changed;
        lifecycleLock.lock();
        try {
            changed = stopReason == null;
            if (changed) {
                stopReason = reason;
            }
        } finally {
            lifecycleLock.unlock();
        }
        publishStop();
        return changed;
    }

    /**
     * 仅在退出生命周期锁后发布停止状态，解除计时器并逐个通知资源监听者。
     */
    private void publishStop() {
        if (lifecycleLock.isHeldByCurrentThread()) {
            // whileActive 的轻量操作可能重入 cancel；由最外层退出后再通知回调。
            return;
        }
        var reason = stopReason;
        if (reason != null && stopPublished.compareAndSet(false, true)) {
            var task = deadlineTask.getAndSet(null);
            if (task != null) {
                task.cancel(false);
            }
            ArrayList<TerminationRegistration> listeners;
            lifecycleLock.lock();
            try {
                listeners = new ArrayList<>(terminationListeners.keySet());
                terminationListeners.clear();
            } finally {
                lifecycleLock.unlock();
            }
            // 在生命周期锁外释放资源，再完成对外的终止信号。
            listeners.forEach(listener -> listener.notifyStop(reason));
            termination.complete(reason);
        }
    }
}
