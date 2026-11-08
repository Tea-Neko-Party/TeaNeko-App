package org.zexnocs.teanekoplugin.agent.social.proactive.adapter;

// 2026/11/07：接口统一使用 I 前缀，调用合同与持久数据保持不变。

// 2026/11/07：按插件归属迁移业务实现，保留底层契约、持久标识和事务边界。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import jakarta.annotation.PreDestroy;
import org.zexnocs.teanekocore.plugin.IPluginLifecycle;
import org.zexnocs.teanekoplugin.support.PluginTaskScope;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoplugin.agent.social.proactive.ProactiveConfiguration;
import org.zexnocs.teanekoagent.integration.config.AgentScopeConfigManager;
import org.zexnocs.teanekoagent.integration.message.NativeMessageInput;
import org.zexnocs.teanekoagent.runtime.AgentScope;
import org.zexnocs.teanekoagent.runtime.monitor.AgentRuntimeMonitor.Delivery;
import org.zexnocs.teanekoagent.runtime.trigger.AgentTriggerGate;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekocore.actuator.task.EmptyTaskResult;
import org.zexnocs.teanekocore.actuator.timer.TimerDisplay;
import org.zexnocs.teanekocore.actuator.timer.interfaces.ITimerService;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataService;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 将真实旁观消息及原 Core Timer 转为自主候选，定时状态只保存未来发生，不补跑旧任务。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：声明定时任务分类及网页控制边界。
 *
 * <br>2026/11/04：补全定时任务的详细中文介绍，原调度和控制意图保持不变。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * <br>2026/11/05：轮换批次起点，忙槽位保留原到期机会。
 * <br>2026/11/07：主动候选与定时器按插件代次启停，关闭等待完整候选结束。
 * <br>2026/11/07：调度参数读取本插件配置适配器，不向宿主配置追加字段。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.5.0-l
 * @version 4.9.0-l
 */
@Service
// 2026/11/04：展示声明不改变原任务调度和生命周期。
// 2026/11/04：补充任务用途、执行边界与网页控制说明。
@TimerDisplay(category = "agent.proactive", categoryName = "Agent 主动活动", name = "自主候选扫描",
        description = "定期检查已知聊天的主动活动条件，满足启用、时间窗口和冷却规则时才尝试生成候选。"
        + "候选使用系统身份，继续检查当前聊天权限、人格、模型上下文和发送目标；可能选择不发言，也可能向真实聊天发送消息。"
        + "本项为只读扫描任务；需要停止某个聊天的主动活动时修改该聊天的 Agent 主动模式配置。", controllable = false)
@org.zexnocs.teanekocore.plugin.PluginOwned("agent.social")
public final class ProactiveCoordinator implements AutoCloseable, IPluginLifecycle, org.zexnocs.teanekoagent.integration.message.IAgentProactiveCoordinator {
    private final IConfigDataService configs;
    private final AgentScopeConfigManager manager;
    private final ProactiveConfiguration configuration;
    private final ProactiveTargets targets;
    private final ProactiveService service;
    private final ITimerService timers;
    private final Clock clock;
    private final Executor executor;
    private final Map<AgentScope, Scheduled> scheduled = new LinkedHashMap<>();
    private final AtomicBoolean scanning = new AtomicBoolean();
    private volatile boolean closed = true;
    private PluginTaskScope lifecycle = new PluginTaskScope();
    private int cursor;

    /**
     * 装配已有调度及执行域，构造不读取配置或启动候选。
     * @param configs 原配置服务
     * @param manager 注册声明
     * @param configuration 本插件有效活动参数
     * @param targets 可信目标
     * @param service 独立受限候选服务
     * @param timers 原 Core Timer
     * @param executor 借用业务域
     */
    @Autowired public ProactiveCoordinator(IConfigDataService configs, AgentScopeConfigManager manager, ProactiveConfiguration configuration, ProactiveTargets targets,
            ProactiveService service, ITimerService timers, @Qualifier("virtualExecutor") Executor executor) {
        this(configs, manager, configuration, targets, service, timers, executor, Clock.systemUTC());
    }

    /**
     * 显式时钟用于宿主组合及离线恢复验证，不替换共享计时器。
     * @param configs 原配置
     * @param manager 注册声明
     * @param configuration 本插件有效活动参数
     * @param targets 原生目标
     * @param service 候选服务
     * @param timers 原计时器
     * @param executor 借用执行域
     * @param clock 显式时钟
     */
    public ProactiveCoordinator(IConfigDataService configs, AgentScopeConfigManager manager, ProactiveConfiguration configuration, ProactiveTargets targets,
            ProactiveService service, ITimerService timers, Executor executor, Clock clock) {
        this.configuration = configuration;
        this.configs = configs; this.manager = manager; this.targets = targets; this.service = service; this.timers = timers;
        this.executor = executor; this.clock = clock;
    }

    /** @return 2026/11/07：主动协调器所属插件。 */
    @Override
    public String pluginId() {
        return "agent.social";
    }

    /** @return 稳定的候选调度生命周期标识。 */
    @Override
    public String id() {
        return "proactive-coordinator";
    }

    /**
     * 2026/11/07：每次启用重新注册未来定时器，旧回调固定在已经失效的作用域。
     * @return 注册完成阶段
     */
    @Override
    public synchronized CompletionStage<Void> start() {
        if (!closed) {
            return CompletableFuture.completedStage(null);
        }
        if (!lifecycle.stop().toCompletableFuture().isDone()) {
            return CompletableFuture.failedStage(new IllegalStateException("上一代主动候选仍在执行。"));
        }
        var scope = new PluginTaskScope();
        lifecycle = scope;
        closed = false;
        var config = timers.registerByDelay("Agent 自主候选", "Agent 自主候选扫描", () -> scope.execute(() -> {
            tick().toCompletableFuture().join();
            return EmptyTaskResult.INSTANCE;
        }), Duration.ofSeconds(30), Void.class);
        scope.own(config);
        return CompletableFuture.completedStage(null);
    }

    /**
     * 2026/11/07：先停止候选入口，再等待本代已经提交的候选实际完成。
     * @return 候选全部结束阶段
     */
    @Override
    public synchronized CompletionStage<Void> stop() {
        closed = true;
        return lifecycle.stop().thenRun(() -> {
            synchronized (scheduled) {
                scheduled.clear();
            }
        });
    }

    /**
     * 只为未触发被动回复的当前原生文本提出接话，不保留原始消息对象或借用发起者权限。
     * @param data 真实来源
     * @param input 冻结的当前原文
     * @return 确认自主发言成功时为真，弃言和跳过均为假
     */
    public synchronized CompletionStage<Boolean> offer(ITeaNekoMessageData data, NativeMessageInput input) {
        if (closed) {
            return CompletableFuture.completedStage(false);
        }
        return lifecycle.executeAsync(() -> offerCurrent(data, input));
    }

    /**
     * 2026/11/07：本代已登记的接话链，启动候选前再复核启用状态。
     * @param data 真实来源
     * @param input 原始输入
     * @return 候选实际结果
     */
    private CompletionStage<Boolean> offerCurrent(ITeaNekoMessageData data, NativeMessageInput input) {
        if (closed || input.text().isBlank()) { return CompletableFuture.completedFuture(false); }
        return CompletableFuture.supplyAsync(() -> {
            var config = config(data.getScopeId());
            if (config == null) { return null; }
            var target = targets.from(data);
            remember(target, config);
            return config.interjection() ? target : null;
        }, executor).thenCompose(target -> target == null || closed ? CompletableFuture.completedFuture(false)
                : service.candidate(target, AgentTriggerGate.Kind.INTERJECTION, data.getMessageId(), input.text()).thenApply(result -> result == Delivery.SENT));
    }

    /**
     * 在原生业务执行域登记已确认的目标；定时配置故障不能阻断普通被动回复。
     * @param data 已通过入站检查的真实消息
     */
    public void observe(ITeaNekoMessageData data) {
        if (closed) { return; }
        try {
            var config = config(data.getScopeId());
            if (config != null) { remember(targets.from(data), config); }
        } catch (RuntimeException unavailable) { /* 主动目标不可用时保持被动入口可达。 */ }
    }

    /**
     * 分批扫描并轮转已登记群和当前进程已确认的私聊，每次至多四个定时发生，不排队补跑。
     * @return 本批候选全部结束的阶段
     */
    public synchronized CompletionStage<Void> tick() {
        if (closed) {
            return CompletableFuture.completedStage(null);
        }
        return lifecycle.executeAsync(this::scan);
    }

    /**
     * 2026/11/07：本代已登记的候选扫描，停止后不启动下一候选。
     * @return 本批候选真实结束阶段
     */
    private CompletionStage<Void> scan() {
        if (closed || !scanning.compareAndSet(false, true)) { return CompletableFuture.completedFuture(null); }
        return CompletableFuture.supplyAsync(() -> {
            for (var key : configs.getAllConfigKeys(manager)) {
                var config = config(key);
                if (config == null || !config.timed()) {
                    continue;
                }
                try { remember(targets.group(key), config); } catch (IllegalArgumentException | NoSuchElementException unknownTarget) { /* 私聊只从真实入站确认客户端，不按配置猜测。 */ }
            }
            var due = new ArrayList<Map.Entry<ProactiveTargets.Target, Instant>>(); var now = clock.instant();
            List<AgentScope> keys;
            synchronized (scheduled) { keys = List.copyOf(scheduled.keySet()); }
            for (int n = 0; n < Math.min(4, keys.size()); n++) {
                var scope = keys.get(Math.floorMod(cursor + n, keys.size())); var config = config(scope.scopeId());
                synchronized (scheduled) {
                    var item = scheduled.get(scope);
                    if (item == null) { continue; }
                    if (config == null || !config.timed()) {
                        scheduled.remove(scope);
                        continue;
                    }
                    if (!now.isBefore(item.next())) {
                        due.add(Map.entry(item.target(), now));
                    }
                }
            }
            // 2026/11/05：每批轮换起点，不能在目标数等于批宽时固定偏向前两项。
            cursor = keys.isEmpty() ? 0 : Math.floorMod(cursor + 1, keys.size());
            return due;
        }, executor).thenCompose(due -> CompletableFuture.allOf(due.stream().map(item ->
                (closed ? CompletableFuture.completedStage(Delivery.SKIPPED)
                        : service.candidate(item.getKey(), AgentTriggerGate.Kind.TIMER, item.getValue().toString(), ""))
                        .exceptionally(failure -> Delivery.FAILED).thenAccept(result -> {
                            if (result != Delivery.BUSY) {
                                synchronized (scheduled) {
                                    var current = scheduled.get(item.getKey().scope());
                                    if (current != null && current.target().equals(item.getKey())
                                            && !current.next().isAfter(item.getValue())) {
                                        scheduled.put(item.getKey().scope(), new Scheduled(current.target(),
                                                clock.instant().plusSeconds(current.interval()), current.interval()));
                                    }
                                }
                            }
                        }).toCompletableFuture()).toArray(CompletableFuture[]::new)))
                .whenComplete((ignored, failure) -> scanning.set(false));
    }

    /**
     * 新目标、客户端变化或间隔改变都重新安排未来发生，重启不执行过去的定时写操作。
     * @param target 原生目标
     * @param config 当前 ACTIVE 配置
     */
    private void remember(ProactiveTargets.Target target, ProactiveConfiguration.Settings config) {
        int interval = config.intervalSeconds();
        if (interval < 60 || interval > 86400) { throw new IllegalArgumentException("自主定时间隔无效。"); }
        synchronized (scheduled) {
            var old = scheduled.get(target.scope());
            if (old == null && scheduled.size() >= 1000) { return; }
            if (old == null || !old.target().equals(target) || old.interval() != interval) {
                scheduled.put(target.scope(), new Scheduled(target, clock.instant().plusSeconds(interval), interval));
            }
        }
    }

    /**
     * 每次读取当前登记和级别，不使用旧配置继续主动发言。
     * @param scope 当前作用域
     * @return 当前 ACTIVE 配置，关闭时为空
     */
    private ProactiveConfiguration.@Nullable Settings config(String scope) {
        return configuration.read(scope).orElse(null);
    }

    /** 只停止本协调器及原 Timer 生命周期，不关闭借用服务和执行器。 */
    @Override
    @PreDestroy
    public void close() {
        stop();
    }

    /**
     * 有界进程内调度状态，私聊目标不由配置键推断。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @param target 固定原生目标
     * @param next 下次未来时刻
     * @param interval 固定间隔
     * @author zExNocs
     * @date 2026/11/02
     * @since 4.5.0-l
     * @version 4.9.0-l
     */
    private record Scheduled(ProactiveTargets.Target target, Instant next, int interval) { }
}
