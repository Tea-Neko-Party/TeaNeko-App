package org.zexnocs.teanekoagent.schedule;

import org.zexnocs.teanekoagent.agent.AgentActorContext;
import org.zexnocs.teanekocore.actuator.task.EmptyTaskResult;
import org.zexnocs.teanekocore.actuator.timer.interfaces.ITimerService;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static org.zexnocs.teanekoagent.schedule.AgentScheduledTask.Status.*;

/**
 * 显式装配的单宿主任务调度服务；原 Timer 驱动分页扫描，数据库仲裁每次发生的执行权。
 * 不默认安装动作或权限实现，不以保存的创建者信息代替当前权限。
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.0.0
 * @version 4.5.0-l
 */
public final class AgentScheduleService implements AutoCloseable {
    private final EasyDataScheduleStore store;
    private final Clock clock;
    private final Policy policy;
    private final Map<String, Action> actions;
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean scanning = new AtomicBoolean();
    private volatile boolean closed;
    private String cursor = "";

    /**
     * 只接收可信宿主安装的动作，构造不启动后台任务。
     * @param store 专用存储
     * @param clock 显式时钟
     * @param policy 当前配置及真实权限查询
     * @param actions 动作注册表
     */
    public AgentScheduleService(EasyDataScheduleStore store, Clock clock, Policy policy, Map<String, Action> actions) {
        this.store = Objects.requireNonNull(store); this.clock = Objects.requireNonNull(clock);
        this.policy = Objects.requireNonNull(policy); this.actions = Map.copyOf(actions);
    }

    /**
     * 校验真实创建来源和动作，先持久化再返回；已存在的原请求允许查询终态。
     * @param id 稳定请求标识
     * @param spec 已消除时间歧义的任务候选
     * @return 已提交的任务卡片数据
     */
    public CompletionStage<AgentScheduledTask> create(UUID id, AgentScheduledTask.Spec spec) {
        var task = new AgentScheduledTask(id, spec, spec.firstFire(), ACTIVE, 1, "等待执行");
        if (closed || !actions.containsKey(spec.action())) { return CompletableFuture.failedFuture(new IllegalStateException("计划任务动作不可用。")); }
        return check(task, Operation.CREATE, spec.creator()).thenCompose(ignored -> store.find(id))
                .thenCompose(existing -> {
                    if (existing.isEmpty() && (!spec.firstFire().isAfter(clock.instant())
                            || spec.firstFire().isAfter(clock.instant().plusSeconds(315_360_000)))) {
                        return CompletableFuture.failedFuture(new IllegalArgumentException("首次执行时间必须在未来，请明确时间及其时区。"));
                    }
                    return store.create(task);
                });
    }

    /**
     * 详情先按来源隔离，再执行当前读取授权。
     * @param id 任务标识
     * @param actor 原生查询者
     * @return 授权后的快照
     */
    public CompletionStage<AgentScheduledTask> detail(UUID id, AgentActorContext actor) {
        return store.find(id).thenCompose(found -> {
            var task = found.orElseThrow(() -> new IllegalArgumentException("计划任务不存在。"));
            requireScope(task, actor);
            return check(task, Operation.READ, actor).thenApply(ignored -> task);
        });
    }

    /**
     * 取消通过当前权限检查，重复取消不增加版本；已经完成的动作不会声称回滚。
     * @param id 任务标识
     * @param actor 原生创建者或已验证的 scope 管理员
     * @return 已提交状态
     */
    public CompletionStage<AgentScheduledTask> cancel(UUID id, AgentActorContext actor) {
        return detail(id, actor).thenCompose(task -> check(task, Operation.CANCEL, actor))
                .thenCompose(ignored -> store.update(id, task -> task.status() == ACTIVE || task.status() == RUNNING
                        || task.status() == PAUSED ? transition(task, CANCELLED, task.nextFire(), "已取消；已开始的外部动作不能保证撤销") : task));
    }

    /**
     * 分页只公开当前 scope 中得到读取授权的任务；游标基于原始页，避免过滤后遗漏后续记录。
     * @param actor 原生查询者
     * @param after 上一页游标
     * @return 当前页及继续游标，空游标表示已经结束
     */
    public CompletionStage<Page> list(AgentActorContext actor, String after) {
        return list(actor, after, () -> { });
    }

    /**
     * 逐项授权时跳过无权查看的记录，取消检查仍传播，不把其他用户记录造成的拒绝误报为整页失败。
     * @param actor 原生查询者
     * @param after 上页游标
     * @param guard 查询生命周期检查
     * @return 可见任务及原始页游标
     */
    public CompletionStage<Page> list(AgentActorContext actor, String after, Runnable guard) {
        guard.run();
        return store.page(after).thenCompose(tasks -> {
            CompletionStage<List<AgentScheduledTask>> chain = CompletableFuture.completedFuture(new ArrayList<>());
            for (var task : tasks) {
                if (!task.spec().creator().scope().equals(actor.scope())) { continue; }
                chain = chain.thenCompose(result -> {
                    guard.run();
                    return check(task, Operation.READ, actor).handle((ignored, failure) -> {
                        guard.run(); if (failure == null) { result.add(task); } return result;
                    });
                });
            }
            var next = tasks.size() < 100 ? "" : tasks.getLast().id().toString();
            return chain.thenApply(result -> new Page(result, next));
        });
    }

    /**
     * 单宿主启动时先恢复状态，再安装原 Core Timer；关闭后生命周期使定时器自动退出。
     * 不支持多个宿主同时执行启动恢复，部署必须保证调度器单实例。
     * @param timers 原定时器服务
     * @return 恢复及注册完成阶段
     */
    public CompletionStage<Void> start(ITimerService timers) {
        if (closed || !started.compareAndSet(false, true)) {
            return CompletableFuture.failedFuture(new IllegalStateException("任务调度器已经启动或关闭。"));
        }
        return recover("").thenRun(() -> {
            if (closed) { return; }
            var config = timers.registerByDelay("Agent 持久化任务", "Agent 计划任务扫描", () -> {
                tick().toCompletableFuture().join(); return EmptyTaskResult.INSTANCE;
            }, Duration.ofSeconds(1), Void.class);
            config.setLivable(() -> !closed);
        });
    }

    /**
     * 每次只扫描一百条，扫描互斥；迟到超过三十秒不补跑动作。
     * @return 本页实际执行及状态提交完成阶段
     */
    public CompletionStage<Void> tick() {
        if (closed || !scanning.compareAndSet(false, true)) { return CompletableFuture.completedFuture(null); }
        return store.page(cursor).thenCompose(tasks -> {
            CompletionStage<Void> chain = CompletableFuture.completedFuture(null);
            for (var task : tasks) { chain = chain.thenCompose(ignored -> dispatch(task)); }
            return chain.thenRun(() -> cursor = tasks.size() < 100 ? "" : tasks.getLast().id().toString());
        }).whenComplete((ignored, failure) -> scanning.set(false));
    }

    /**
     * 启动恢复不重放已经认领的动作；过去的单次过期，重复任务跳到未来。
     * @param after 当前恢复游标
     * @return 全部恢复提交阶段
     */
    private CompletionStage<Void> recover(String after) {
        return store.page(after).thenCompose(tasks -> {
            CompletionStage<Void> chain = CompletableFuture.completedFuture(null);
            for (var task : tasks) {
                chain = chain.thenCompose(ignored -> store.update(task.id(), current -> {
                    if (current.status() == RUNNING) { return transition(current, INDETERMINATE, current.nextFire(), "重启前动作结果不明，禁止自动重试"); }
                    return current.status() == ACTIVE && !current.nextFire().isAfter(clock.instant()) ? missed(current) : current;
                }).thenApply(updated -> null));
            }
            return tasks.size() < 100 ? chain : chain.thenCompose(ignored -> recover(tasks.getLast().id().toString()));
        });
    }

    /**
     * 先通过当前策略，再按 revision 和发生时刻取得执行权；取消后的旧快照不能认领。
     * @param observed 扫描快照
     * @return 动作与结果落库阶段
     */
    private CompletionStage<Void> dispatch(AgentScheduledTask observed) {
        var now = clock.instant();
        if (closed || observed.status() != ACTIVE || observed.nextFire().isAfter(now)) { return CompletableFuture.completedFuture(null); }
        if (observed.nextFire().plusSeconds(30).isBefore(now)) {
            return store.update(observed.id(), current -> current.equals(observed) ? missed(current) : current).thenApply(ignored -> null);
        }
        return check(observed, Operation.EXECUTE, observed.spec().creator()).handle((ignored, failure) -> failure == null)
                .thenCompose(allowed -> {
                    var claim = transition(observed, allowed && actions.containsKey(observed.spec().action()) ? RUNNING : PAUSED,
                            observed.nextFire(), allowed ? "已认领执行权" : "当前配置或创建者权限不可确认，已暂停");
                    return store.compareAndSet(observed, claim).thenCompose(won -> {
                    if (!won || claim.status() != RUNNING) { return CompletableFuture.completedFuture(null); }
                    return guard(claim).thenCompose(ignored -> actions.get(claim.spec().action()).execute(claim, () -> guard(claim)))
                            .handle((result, failure) -> failure == null && result != null ? result : Outcome.INDETERMINATE)
                            .thenCompose(result -> store.update(claim.id(), current -> {
                                if (!current.equals(claim)) { return current; }
                                if (result == Outcome.INDETERMINATE) { return transition(current, INDETERMINATE, current.nextFire(), "动作结果不明，禁止自动重试"); }
                                if (result == Outcome.REJECTED) { return transition(current, PAUSED, current.nextFire(), "执行器拒绝动作，已暂停"); }
                                return current.spec().intervalSeconds() == 0 ? transition(current, COMPLETED, current.nextFire(), "执行完成")
                                        : transition(current, ACTIVE, future(current, clock.instant()), "执行完成");
                            })).thenApply(ignored -> null);
                    });
                });
    }

    /**
     * 执行器必须在每个真实副作用前等待本检查；检查不赋予系统任务用户身份。
     * @param claim 已认领快照
     * @return 当前权限和数据库修订号检查阶段
     */
    private CompletionStage<Void> guard(AgentScheduledTask claim) {
        if (closed) { return CompletableFuture.failedFuture(new IllegalStateException("任务调度器已关闭。")); }
        return check(claim, Operation.EXECUTE, claim.spec().creator()).thenCompose(ignored -> store.find(claim.id()))
                .thenAccept(current -> {
                    if (closed || current.isEmpty() || !current.get().equals(claim)) { throw new IllegalStateException("任务已取消或执行权已失效。"); }
                });
    }

    /**
     * 跳过迟到发生，不补跑过期写操作。
     * @param task 当前活动任务
     * @return 过期或下一次未来快照
     */
    private AgentScheduledTask missed(AgentScheduledTask task) {
        return task.spec().intervalSeconds() == 0 ? transition(task, EXPIRED, task.nextFire(), "执行时间已过，未补跑")
                : transition(task, ACTIVE, future(task, clock.instant()), "已跳过错过的执行时间");
    }

    /**
     * 以原发生时间为锚点计算未来固定间隔，不因执行耗时漂移或使用服务器时区。
     * @param task 重复任务
     * @param now 当前时刻
     * @return 严格晚于当前时刻的下次发生
     */
    private static Instant future(AgentScheduledTask task, Instant now) {
        long interval = task.spec().intervalSeconds();
        long elapsed = Math.max(0, Duration.between(task.nextFire(), now).getSeconds());
        return task.nextFire().plusSeconds(Math.multiplyExact(Math.addExact(elapsed / interval, 1), interval));
    }

    /**
     * 每次状态变化都增加版本，使旧回调不可重新发布结果。
     * @param task 原快照
     * @param status 新状态
     * @param nextFire 新计划时间
     * @param outcome 安全结果
     * @return 新快照
     */
    private static AgentScheduledTask transition(AgentScheduledTask task, AgentScheduledTask.Status status, Instant nextFire, String outcome) {
        return new AgentScheduledTask(task.id(), task.spec(), nextFire, status, Math.addExact(task.revision(), 1), outcome);
    }

    /**
     * 管理操作不得跨原生来源访问任务。
     * @param task 任务
     * @param actor 原生操作人
     */
    private static void requireScope(AgentScheduledTask task, AgentActorContext actor) {
        if (actor.origin() != AgentActorContext.Origin.USER || !task.spec().creator().scope().equals(actor.scope())) {
            throw new IllegalArgumentException("计划任务不存在或当前来源不可访问。");
        }
    }

    /**
     * 同步的权限查询故障也归入拒绝阶段，避免已认领动作遗留为无回执的运行状态。
     * @param task 固定任务
     * @param operation 操作
     * @param actor 可信来源
     * @return 当前检查阶段
     */
    private CompletionStage<Void> check(AgentScheduledTask task, Operation operation, AgentActorContext actor) {
        try { return Objects.requireNonNull(policy.check(task, operation, actor), "任务权限检查未返回阶段。"); }
        catch (RuntimeException failure) { return CompletableFuture.failedFuture(failure); }
    }

    /** 使后续扫描及副作用检查停止；不声称撤销已经开始的外部动作。 */
    @Override public void close() { closed = true; }

    /**
     * 当前真实权限检查；CREATE/EXECUTE 必须检查 scope 注册、启用、动作能力和创建者当前权限，
     * READ/CANCEL 必须检查归属或 scope 管理权限；查询失败应拒绝，不能从保存记录推导 DEBUG。
     * @author zExNocs
     * @date 2026/11/01
     * @since 4.0.0
     * @version 4.5.0-l
     */
    @FunctionalInterface public interface Policy {
        /**
         * 检查当前宿主配置和权威身份，不进行副作用。
         * @param task 固定任务
         * @param operation 操作类型
         * @param actor 原生管理人或仅供重新查询权限的原创建者
         * @return 准入阶段，拒绝时失败
         */
        CompletionStage<Void> check(AgentScheduledTask task, Operation operation, AgentActorContext actor);
    }

    /**
     * 宿主注册的动作执行器，必须在每个副作用前等待 guard，并使用任务 ID 加 nextFire 作为幂等键。
     * 不盲目重试，也不能把保存的创建者直接转换为高权限命令来源。
     * @author zExNocs
     * @date 2026/11/01
     * @since 4.0.0
     * @version 4.5.0-l
     */
    @FunctionalInterface public interface Action {
        /**
         * 执行当前发生并等待实际完成。
         * @param task 已取得执行权的任务
         * @param guard 当前授权及取消检查
         * @return 明确完成、拒绝或结果不明
         */
        CompletionStage<Outcome> execute(AgentScheduledTask task, Supplier<CompletionStage<Void>> guard);
    }

    /**
     * 区分管理和执行权限。
     * @author zExNocs
     * @date 2026/11/01
     * @since 4.0.0
     * @version 4.5.0-l
     */
    public enum Operation { CREATE, READ, CANCEL, EXECUTE }

    /**
     * 执行器返回的业务结果，不透传敏感异常。
     * @author zExNocs
     * @date 2026/11/01
     * @since 4.0.0
     * @version 4.5.0-l
     */
    public enum Outcome { COMPLETED, REJECTED, INDETERMINATE }

    /**
     * 可过滤的有界列表；后续页不根据展示数量推断。
     * @param tasks 当前可见任务
     * @param nextCursor 继续游标
     * @author zExNocs
     * @date 2026/11/01
     * @since 4.0.0
     * @version 4.5.0-l
     */
    public record Page(List<AgentScheduledTask> tasks, String nextCursor) {
        /**
         * 固定列表，避免展示层改变查询结果。
         * @param tasks 可见任务
         * @param nextCursor 继续游标
         */
        public Page { tasks = List.copyOf(tasks); }
    }
}
