package org.zexnocs.teanekoplugin.agent.cognition.memory.layered;

// 2026/11/07：接口统一使用 I 前缀，调用合同与持久数据保持不变。

// 2026/11/07：认知业务契约归入插件，纯会话数据沿用运行底座。

// 2026/11/07：按插件归属迁移业务实现，保留底层契约、持久标识和事务边界。


// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.zexnocs.teanekocore.actuator.task.EmptyTaskResult;
import org.zexnocs.teanekocore.actuator.timer.interfaces.ITimerService;
import org.zexnocs.teanekocore.plugin.IPluginLifecycle;
import org.zexnocs.teanekoplugin.support.PluginTaskScope;
import org.zexnocs.teanekocore.actuator.timer.TimerDisplay;
import org.zexnocs.teanekocore.logger.ILogger;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/**
 * 在原业务域串行维护各记忆端口，不调用模型，不关闭借用执行器，不重叠启动新批次。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：声明定时任务分类及网页控制边界。
 * <br>2026/11/04：补全定时任务的详细中文介绍，原调度和控制意图保持不变。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/06：保留失败阶段及安全根因，避免配置或异步边界丢失诊断。
 *
 * <br>2026/11/07：后台任务支持重复热启停，停止等待本代实际工作收尾。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.6.1-l
 * @version 4.9.0-l
 */
// 2026/11/04：展示声明不改变原任务调度和生命周期。
// 2026/11/04：补充任务用途、执行边界与网页控制说明。
@TimerDisplay(category = "agent.memory", categoryName = "Agent 记忆", name = "记忆数据维护",
        description = "按配置周期依次维护已装配的记忆数据域，回收到期正文、失效租约和允许清理的辅助状态。"
        + "不调用模型；关闭学习或停止采集后，已有数据仍按原保留规则维护，有效长期结论不因停止学习被删除。"
        + "每次执行有界事务，单个数据域失败不阻止其他域继续，后续从已提交进度恢复；维护任务只读。", controllable = false)
@org.zexnocs.teanekocore.plugin.PluginOwned("agent.cognition")
public final class MemoryMaintenanceService implements AutoCloseable, IPluginLifecycle {
    private final List<IMemoryMaintenance> stores;
    private final BooleanSupplier enabled;
    private final Executor executor;
    private final Clock clock;
    private final ILogger logger;
    private final AtomicBoolean running = new AtomicBoolean();
    private volatile boolean closed = true;
    // 2026/11/07：每次启用独立登记异步占用，停用等真实工作结束。
    private PluginTaskScope lifecycle = new PluginTaskScope();
    private @org.jspecify.annotations.Nullable ITimerService timers;
    private long intervalMillis = 10000;

    /**
     * 2026/11/07：借用原定时器服务并保留原轮询配置，插件未启动时不注册任务。
     * @param timers 原 Core 调度入口
     * @param intervalMillis 原轮询间隔，必须为正
     */
    @Autowired
    public void installTimer(ITimerService timers, @Value("${tea-neko.agent.memory.maintenance.interval-ms:10000}") long intervalMillis) {
        if (intervalMillis <= 0) {
            throw new IllegalArgumentException("插件后台轮询间隔必须为正数。");
        }
        this.timers = timers;
        this.intervalMillis = intervalMillis;
    }

    /**
     * 捕获可替换的维护端口，构造期间不访问数据库。
     * @param stores 已装配维护端口
     * @param enabled 独立维护开关
     * @param executor 原业务域
     * @param clock 当前时间
     * @param logger 固定诊断
     */
    public MemoryMaintenanceService(List<IMemoryMaintenance> stores, BooleanSupplier enabled, Executor executor, Clock clock, ILogger logger) {
        this.stores = List.copyOf(stores); this.enabled = enabled; this.executor = executor; this.clock = clock; this.logger = logger;
    }
    /** 通过原调度设施维护，不因前批等待数据库而创建重复工作。 */
    public void tick() {
        drain().whenComplete((ignored, failure) -> {
            if (failure != null) { logger.warn("Agent 记忆维护", "记忆维护调度未完成，将从已提交的进度继续。", failure); }
        });
    }
    /**
     * 维护一次有限集合，等待实际提交；某个端口失败不阻止其他域继续回收。
     * @return 实际删除和精简的总数
     */
    public synchronized CompletionStage<Integer> drain() {
        if (closed) {
            return CompletableFuture.completedStage(0);
        }
        return lifecycle.executeAsync(this::drainCurrent);
    }

    /**
     * 2026/11/07：执行本代已经登记的维护链，原提交和租约逻辑保持完整等待。
     * @return 本轮真实完成阶段
     */
    private CompletionStage<Integer> drainCurrent() {
        if (closed || !running.compareAndSet(false, true)) { return CompletableFuture.completedStage(0); }
        try {
            return CompletableFuture.supplyAsync(() -> visit(0, 0), executor).thenCompose(stage -> stage)
                    .whenComplete((ignored, failure) -> running.set(false));
        } catch (RuntimeException failure) { running.set(false); return CompletableFuture.failedStage(failure); }
    }
    /**
     * 依次等待端口事务，不在数据库完成线程中直接读取下一域配置。
     * @param index 当前端口位置
     * @param changed 已提交数量
     * @return 剩余端口完成阶段
     */
    private CompletionStage<Integer> visit(int index, int changed) {
        if (closed || !enabled.getAsBoolean() || index == stores.size()) { return CompletableFuture.completedStage(changed); }
        CompletionStage<Integer> work;
        try { work = Objects.requireNonNull(stores.get(index).maintain(clock.instant()), "记忆维护端口不能返回空阶段。"); }
        catch (RuntimeException failure) { work = CompletableFuture.failedStage(failure); }
        return work.handleAsync((count, failure) -> {
            // 2026/11/06：保留具体端口与安全原因，不用零计数掩盖无效返回。
            if (failure != null || count == null || count < 0) {
                logger.warn("Agent 记忆维护", "数据域维护失败，游标不推进。端口="
                        + stores.get(index).getClass().getName(),
                        failure != null ? failure : new IllegalStateException("维护计数必须为非负整数。"));
                return 0;
            }
            return count;
        }, executor).thenComposeAsync(count -> visit(index + 1, changed + count), executor);
    }
    /** 停止新增维护，已开始的数据库事务继续以真实结果完成。 */
    @Override
    public void close() {
        stop();
    }

    /** @return 2026/11/07：本后台服务所属插件。 */
    @Override
    public String pluginId() {
        return "agent.cognition";
    }

    /** @return 同一插件内稳定的后台生命周期标识。 */
    @Override
    public String id() {
        return "memory-maintenance";
    }

    /**
     * 2026/11/07：旧代实际结束后重新开放定时入口，不关闭借用执行器。
     * @return 启动完成阶段
     */
    @Override
    public synchronized CompletionStage<Void> start() {
        if (!closed) {
            return CompletableFuture.completedStage(null);
        }
        if (!lifecycle.stop().toCompletableFuture().isDone()) {
            return CompletableFuture.failedStage(new IllegalStateException("上一代插件后台工作尚未结束。"));
        }
        var scope = new PluginTaskScope();
        lifecycle = scope;
        closed = false;
        if (timers != null) {
            var timer = timers.registerByDelay("记忆数据维护", "agent-memory-maintenance", () -> scope.execute(() -> {
                drain().toCompletableFuture().join();
                return EmptyTaskResult.INSTANCE;
            }), Duration.ofMillis(intervalMillis), Void.class);
            scope.own(timer);
        }
        return CompletableFuture.completedStage(null);
    }

    /**
     * 2026/11/07：先关闭新工作并撤销自有控制，再等待已开始的实际链结束。
     * @return 本代全部工作结束阶段
     */
    @Override
    public synchronized CompletionStage<Void> stop() {
        closed = true;

        return lifecycle.stop();
    }
}
