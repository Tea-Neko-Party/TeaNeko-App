package org.zexnocs.teanekoapp.plugin;

// 2026/11/07：接口统一使用 I 前缀，调用合同与持久数据保持不变。

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.zexnocs.teanekocore.plugin.IPluginLifecycle;
import org.zexnocs.teanekocore.plugin.PluginRegistry;
import org.zexnocs.teanekocore.plugin.PluginSwitchMode;
import org.zexnocs.teanekocore.logger.ILogger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * 按依赖拓扑协调可重复启停，调用门禁先撤销、资源终态后确认。
 * <br>2026/11/07：意愿与实际启动分开，失败不会发布有效资格或隐式重试。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
@Component
public final class PluginRuntime {
    private final PluginRegistry plugins;
    private final PluginClientConnections connections;
    private final ILogger logger;
    private final Map<String, Boolean> applied = new HashMap<>();
    private final Set<String> started = new HashSet<>();
    private final Map<String, Status> states = new HashMap<>();
    private Map<String, List<IPluginLifecycle>> hooks = Map.of();
    private long revision = 1;
    private boolean configured;
    private boolean busy;

    /**
     * 关闭普通模块的启动前门禁，防止文件意愿尚未加载就产生业务。
     * @param plugins 原插件目录
     * @param connections 插件自有客户端连接
     * @param logger 原安全日志入口
     */
    public PluginRuntime(PluginRegistry plugins, PluginClientConnections connections, ILogger logger) {
        this.plugins = plugins;
        this.connections = connections;
        this.logger = logger;
        for (var view : plugins.list()) {
            String id = view.descriptor().id();
            if (view.descriptor().mandatory()) {
                applied.put(id, true);
                states.put(id, new Status("ENABLED", ""));
            } else {
                plugins.setEnabled(id, false);
                states.put(id, new Status("INITIALIZING", "等待插件运行配置初始化。"));
            }
        }
    }

    /**
     * 单例全部创建后一次安装生命周期目录，拒绝插件内重名。
     * @param contributions 已由容器创建的生命周期
     */
    public synchronized void configure(List<IPluginLifecycle> contributions) {
        if (configured) {
            return;
        }
        var grouped = new TreeMap<String, Map<String, IPluginLifecycle>>();
        for (var hook : contributions) {
            // 2026/11/07：宿主能力不参与业务插件生命周期。
            plugins.descriptor(hook.pluginId());
            if (hook.id() == null || hook.id().isBlank()) {
                throw new IllegalArgumentException("插件生命周期标识不能为空。");
            }
            if (grouped.computeIfAbsent(hook.pluginId(), ignored -> new TreeMap<>())
                    .putIfAbsent(hook.id(), hook) != null) {
                throw new IllegalArgumentException("插件生命周期标识重复：" + hook.pluginId() + ":" + hook.id());
            }
        }
        var next = new TreeMap<String, List<IPluginLifecycle>>();
        grouped.forEach((id, values) -> next.put(id, List.copyOf(values.values())));
        hooks = Map.copyOf(next);
        configured = true;
    }

    /**
     * 查询实际资源状态，不以保存意愿伪装启动完成。
     * @param id 插件标识
     * @return 当前生命周期状态
     */
    public synchronized Status status(String id) {
        plugins.requireRegistered(id);
        return states.get(id);
    }

    /**
     * 查询本进程已接受的意愿，重启型变更不会立即替换它。
     * @param id 插件标识
     * @return 已应用意愿
     */
    public synchronized boolean appliedIntent(String id) {
        return applied.getOrDefault(id, false);
    }

    /**
     * 返回生命周期快照代次。
     * @return 状态修订号
     */
    public synchronized long revision() {
        return revision;
    }

    /**
     * 计算一个操作是否需要推迟到重启，避免热停依赖后遗留无法热停的消费者。
     * @param id 目标插件
     * @param enabled 目标意愿
     * @return 是否需要重启
     */
    public synchronized boolean needsRestart(String id, boolean enabled) {
        if (appliedIntent(id) == enabled) {
            return false;
        }
        if (plugins.descriptor(id).switchMode() == PluginSwitchMode.RESTART_REQUIRED) {
            return true;
        }
        var proposed = new HashMap<>(applied);
        proposed.put(id, enabled);
        var target = targets(proposed);
        return plugins.list().stream().anyMatch(view -> view.enabled() && !target.get(view.descriptor().id())
                && view.descriptor().switchMode() == PluginSwitchMode.RESTART_REQUIRED);
    }

    /**
     * 按本次已授权意愿协调实际资源，不持锁等待异步回调。
     * @param desired 完整持久意愿
     * @param changed 本次目标，启动时为空
     * @param boot 是否为进程启动
     * @return 所有本次资源转换真实结束的阶段
     */
    public CompletionStage<Void> reconcile(Map<String, Boolean> desired, @Nullable String changed, boolean boot) {
        final Map<String, Boolean> target;
        final List<String> order;
        synchronized (this) {
            if (!configured || busy) {
                return CompletableFuture.failedStage(new IllegalStateException("插件生命周期尚未就绪或仍在切换。"));
            }
            busy = true;
            if (boot) {
                for (var view : plugins.list()) {
                    applied.put(view.descriptor().id(), view.descriptor().mandatory()
                            || desired.getOrDefault(view.descriptor().id(), true));
                }
            } else if (changed != null && !needsRestart(changed, desired.getOrDefault(changed, true))) {
                applied.put(changed, desired.getOrDefault(changed, true));
            }
            target = targets(applied);
            order = plugins.activationOrder();
            for (var id : order) {
                if (!target.get(id) && !plugins.descriptor(id).mandatory()) {
                    plugins.setEnabled(id, false);
                }
            }
        }
        CompletionStage<Void> stage = CompletableFuture.completedStage(null);
        var reversed = new ArrayList<>(order);
        Collections.reverse(reversed);
        for (var id : reversed) {
            if (!target.get(id)) {
                stage = stage.thenCompose(ignored -> stopPlugin(id));
            }
        }
        for (var id : order) {
            if (target.get(id)) {
                stage = stage.thenCompose(ignored -> startPlugin(id, boot || id.equals(changed)));
            }
        }
        return stage.whenComplete((ignored, failure) -> {
            synchronized (this) {
                busy = false;
                revision++;
            }
        }).toCompletableFuture().minimalCompletionStage();
    }

    /**
     * 从完整安装目录推导有效目标，缺失依赖明确得到不可用结果。
     * @param intentions 本进程允许应用的意愿
     * @return 每个已安装插件的目标资格
     */
    public Map<String, Boolean> targets(Map<String, Boolean> intentions) {
        var result = new HashMap<String, Boolean>();
        for (var id : plugins.activationOrder()) {
            boolean enabled = intentions.getOrDefault(id, true) && plugins.blockers(id).isEmpty()
                    // 2026/11/07：稳定宿主能力不属于业务插件拓扑，也不参与开关意愿。
                    && plugins.dependencies(id).stream().allMatch(dependency -> plugins.isHostCapability(dependency)
                    || result.getOrDefault(dependency, false));
            result.put(id, enabled);
        }
        return Map.copyOf(result);
    }

    /**
     * 应用最终停止时释放全部普通插件，包括平时仅允许重启切换的插件。
     * @return 全部真实停止尝试结束的阶段，持久启用意愿保持原值
     */
    public CompletionStage<Void> shutdown() {
        var stopped = new HashMap<String, Boolean>();
        plugins.list().forEach(view -> stopped.put(view.descriptor().id(), false));
        return reconcile(stopped, null, true).thenRun(() -> {
            synchronized (this) {
                if (!started.isEmpty()) {
                    throw new IllegalStateException("插件资源尚未完成最终停止：" + String.join("、", started));
                }
            }
        }).whenComplete((ignored, failure) -> {
            if (failure != null) {
                logger.error("插件生命周期", "应用关闭时插件资源协调未完成。", failure);
            }
        });
    }

    /**
     * 按逆序停止资源；某一钩子失败仍尝试清理其他已启动资源。
     * @param id 插件标识
     * @return 该插件停用尝试完成的阶段
     */
    private CompletionStage<Void> stopPlugin(String id) {
        synchronized (this) {
            if (plugins.descriptor(id).mandatory()) {
                return CompletableFuture.completedStage(null);
            }
            if (!started.contains(id)) {
                update(id, appliedIntent(id) ? "BLOCKED" : "DISABLED", blockedReason(id));
                return CompletableFuture.completedStage(null);
            }
            // 2026/11/07：消费者未完成物理排空时保留提供者资源，所有调用门禁已提前关闭。
            if (started.stream().anyMatch(consumer -> !consumer.equals(id)
                    && plugins.dependencies(consumer).contains(id))) {
                update(id, "FAILED", "依赖此插件的资源尚未停止，请重试关闭以完成依赖链清理。");
                return CompletableFuture.completedStage(null);
            }
            update(id, "STOPPING", "正在停止任务并等待自有资源结束。");
        }
        // 2026/11/07：同时撤销连接、停止业务并等待调用排空，避免协议回调与停止钩子互相等待。
        CompletionStage<Void> connectionStop;
        try {
            connectionStop = connections.stop(id);
        } catch (RuntimeException failure) {
            connectionStop = CompletableFuture.failedStage(failure);
        }
        var hookStop = stopHooks(id);
        var callStop = plugins.drain(id);
        return CompletableFuture.allOf(connectionStop.toCompletableFuture(), hookStop.toCompletableFuture(),
                callStop.toCompletableFuture()).handle((ignored, failure) -> {
            synchronized (this) {
                if (failure == null) {
                    started.remove(id);
                    update(id, appliedIntent(id) ? "BLOCKED" : "DISABLED", blockedReason(id));
                } else {
                    logger.error("插件生命周期", "插件停用未完成：" + id, failure);
                    update(id, "FAILED", "插件资源尚未完成停用；请检查运行日志并重试停用。");
                }
            }
            return null;
        });
    }

    /**
     * 所有钩子真实启动成功后才开放新业务；失败清理部分资源。
     * @param id 插件标识
     * @param retry 是否为明确重试或首次启动
     * @return 启动或失败清理结束的阶段
     */
    private CompletionStage<Void> startPlugin(String id, boolean retry) {
        synchronized (this) {
            if (plugins.descriptor(id).mandatory()) {
                return CompletableFuture.completedStage(null);
            }
            if (started.contains(id)) {
                return CompletableFuture.completedStage(null);
            }
            if (states.get(id).state().equals("FAILED") && !retry) {
                return CompletableFuture.completedStage(null);
            }
            if (plugins.dependencies(id).stream().anyMatch(dependency -> !plugins.isEnabled(dependency))) {
                update(id, "BLOCKED", blockedReason(id));
                return CompletableFuture.completedStage(null);
            }
            update(id, "STARTING", "正在启动插件资源。");
        }
        CompletionStage<Void> stage = CompletableFuture.completedStage(null);
        for (var hook : hooks.getOrDefault(id, List.of())) {
            stage = stage.thenCompose(ignored -> invoke(hook, true));
        }
        return stage.handle((ignored, failure) -> {
            if (failure == null) {
                synchronized (this) {
                    started.add(id);
                    plugins.setEnabled(id, true);
                    update(id, "ENABLED", "");
                }
                return CompletableFuture.<Void>completedStage(null);
            }
            logger.error("插件生命周期", "插件启动失败：" + id, failure);
            return stopHooks(id).handle((cleaned, cleanupFailure) -> {
                synchronized (this) {
                    if (cleanupFailure != null) {
                        started.add(id);
                        logger.error("插件生命周期", "插件启动失败后的资源清理未完成：" + id, cleanupFailure);
                    }
                    update(id, "FAILED", cleanupFailure == null
                            ? "插件资源启动失败；请检查运行日志并显式重试。"
                            : "插件启动及清理未完成；请先重试停用。");
                }
                return (Void) null;
            });
        }).thenCompose(value -> value);
    }

    /**
     * 停止同一插件的全部钩子，保留首个失败且不跳过后续清理。
     * @param id 插件标识
     * @return 完整清理阶段
     */
    private CompletionStage<Void> stopHooks(String id) {
        var values = new ArrayList<>(hooks.getOrDefault(id, List.of()));
        Collections.reverse(values);
        CompletionStage<@Nullable Throwable> stage = CompletableFuture.completedStage(null);
        for (var hook : values) {
            stage = stage.thenCompose(previous -> invoke(hook, false)
                    .handle((ignored, failure) -> previous == null ? failure : previous));
        }
        return stage.thenCompose(failure -> failure == null ? CompletableFuture.completedStage(null)
                : CompletableFuture.failedStage(failure));
    }

    /**
     * 将同步启动异常转换为真实失败阶段，不自动重试插件业务。
     * @param hook 当前钩子
     * @param start 是否启动
     * @return 插件返回的实际阶段
     */
    private CompletionStage<Void> invoke(IPluginLifecycle hook, boolean start) {
        try {
            return Objects.requireNonNull(start ? hook.start() : hook.stop(), "插件生命周期不能返回空阶段。");
        } catch (RuntimeException failure) {
            return CompletableFuture.failedStage(failure);
        }
    }

    /**
     * 生成不包含扩展异常正文的阻塞原因。
     * @param id 插件标识
     * @return 当前依赖说明
     */
    private String blockedReason(String id) {
        var reasons = new ArrayList<>(plugins.blockers(id));
        for (var dependency : plugins.dependencies(id)) {
            if (!plugins.isEnabled(dependency)) {
                reasons.add("依赖插件尚未启用：" + dependency);
            }
        }
        return String.join("；", reasons);
    }

    /**
     * 在协调器锁内发布状态和版本。
     * @param id 插件标识
     * @param state 状态
     * @param reason 安全原因
     */
    private void update(String id, String state, String reason) {
        states.put(id, new Status(state, reason));
        revision++;
    }

    /**
     * 不持有资源句柄的状态快照。
     * @param state 生命周期状态
     * @param reason 安全诊断
     * @author zExNocs
     * @date 2026/11/07
     * @since 4.9.0-l
     * @version 4.9.0-l
     */
    public record Status(String state, String reason) {
    }
}
