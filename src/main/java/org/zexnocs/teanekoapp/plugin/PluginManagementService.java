package org.zexnocs.teanekoapp.plugin;

// 2026/11/07：接口统一使用 I 前缀，调用合同与持久数据保持不变。

import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.context.SmartLifecycle;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.plugin.IPluginLifecycle;
import org.zexnocs.teanekocore.plugin.PluginRegistry;
import org.zexnocs.teanekocore.plugin.management.ManagementContext;

import java.time.Instant;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

/**
 * 宿主持有的插件管理用例，先确认并持久意愿，再协调真实生命周期。
 * <br>2026/11/07：版本、影响确认、原操作防重和主体隔离共用同一管理边界。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
@Service
@Order(1000)
public final class PluginManagementService implements ApplicationRunner, SmartLifecycle {
    private final PluginRegistry plugins;
    private final PluginRuntime runtime;
    private final PluginStateStore store;
    private final PluginClientDirectory clients;
    private final PluginOperationJournal journal;
    private final ObjectProvider<IPluginLifecycle> lifecycles;
    private @org.jspecify.annotations.Nullable PluginContributionDirectory contributions;
    private boolean ready;
    private boolean changing;

    /**
     * 2026/11/07：安装已缓存的跨层贡献说明，原独立生命周期测试可不装配页面来源。
     * @param contributions 完整安装目录
     */
    @org.springframework.beans.factory.annotation.Autowired
    public void installContributions(PluginContributionDirectory contributions) {
        this.contributions = contributions;
    }
    private boolean closing;
    private boolean closed;
    private CompletableFuture<Void> settled = CompletableFuture.completedFuture(null);
    private @Nullable CompletableFuture<Void> shutdown;

    /**
     * 借用原目录、文件原子写入与容器生命周期，不在构造时扫描扩展。
     * @param plugins 插件目录
     * @param runtime 真实资源协调器
     * @param store 持久意愿
     * @param clients 客户端需求
     * @param journal 原操作账本
     * @param lifecycles 容器拥有的生命周期提供者
     */
    public PluginManagementService(PluginRegistry plugins, PluginRuntime runtime, PluginStateStore store,
                                    PluginClientDirectory clients, PluginOperationJournal journal,
                                    ObjectProvider<IPluginLifecycle> lifecycles) {
        this.plugins = plugins;
        this.runtime = runtime;
        this.store = store;
        this.clients = clients;
        this.journal = journal;
        this.lifecycles = lifecycles;
    }

    /**
     * 文件和业务扫描完成后启动插件；有界等待失败不发布伪造的就绪状态。
     * @param arguments 宿主启动参数，不用于扩大插件权限
     * @throws Exception 当本地配置或生命周期协调无法在启动期限内结束时
     */
    @Override
    public void run(@NonNull ApplicationArguments arguments) throws Exception {
        initialize().toCompletableFuture().get(60, TimeUnit.SECONDS);
    }

    /**
     * 初始化真实生命周期，独立测试可等待返回阶段而不启动 Web 容器。
     * @return 启动协调与中断恢复完成的阶段
     */
    public CompletionStage<Void> initialize() {
        synchronized (this) {
            if (ready || changing) {
                return CompletableFuture.failedStage(new IllegalStateException("插件管理已初始化或正在切换。"));
            }
            changing = true;
            settled = new CompletableFuture<>();
        }
        try {
            store.initialize();
            clients.refresh();
            runtime.configure(lifecycles.orderedStream().toList());
            return runtime.reconcile(store.snapshot().enabled(), null, true).thenRun(() -> {
                synchronized (this) {
                    ready = true;
                    recover();
                }
            }).whenComplete((ignored, failure) -> {
                synchronized (this) {
                    changing = false;
                    settled.complete(null);
                }
            }).toCompletableFuture().minimalCompletionStage();
        } catch (RuntimeException failure) {
            synchronized (this) {
                changing = false;
                settled.complete(null);
            }
            return CompletableFuture.failedStage(failure);
        }
    }

    /**
     * 读取全部已安装插件，停用或失败项仍可在宿主页面重新管理。
     * @param context 可信管理上下文
     * @return 公开目录快照
     */
    public synchronized PluginManagement.Catalog catalog(ManagementContext context) {
        context.checkActive();
        requireReady();
        var desired = store.snapshot().enabled();
        var items = plugins.list().stream().map(view -> {
            var descriptor = view.descriptor();
            String id = descriptor.id();
            boolean wanted = desired.getOrDefault(id, true);
            boolean pending = wanted != runtime.appliedIntent(id);
            var status = runtime.status(id);
            return new PluginManagement.Item(id, descriptor.name(), descriptor.description(), descriptor.category(),
                    descriptor.version(), descriptor.mandatory(), wanted, view.enabled(),
                    pending ? "PENDING_RESTART" : status.state(),
                    pending ? "启用意愿已保存，需要重启应用后生效。" : status.reason(), pending,
                    plugins.dependencies(id), clients.requiredClients(id),
                    contributions == null ? List.of() : contributions.list(id));
        }).toList();
        return new PluginManagement.Catalog(revision(), items);
    }

    /**
     * 预览操作的依赖影响，不自动改变其他插件的原启用意愿。
     * @param request 明确目标与已查看的目录版本
     * @param context 可信管理上下文
     * @return 可供用户确认的精确影响
     */
    public synchronized PluginManagement.Preview preview(PluginManagement.Request request, ManagementContext context) {
        context.checkActive();
        requireReady();
        checkRevision(request.expectedRevision());
        return preview(request.pluginId(), request.enabled());
    }

    /**
     * 持久化前复核身份、版本与影响确认；提交后生命周期拥有其完整工作阶段。
     * @param request 已确认的固定操作
     * @param context 可信主体及实时授权
     * @return 真实应用结果，超时后仍可用原编号查询
     */
    public CompletionStage<PluginManagement.Receipt> change(PluginManagement.Change request, ManagementContext context) {
        final PluginOperationJournal.Entry committed;
        synchronized (this) {
            context.checkActive();
            requireReady();
            var existing = journal.find(request.operationId());
            if (existing != null) {
                verifyOwner(existing, context);
                if (!existing.request().equals(request)) {
                    throw new IllegalArgumentException("同一插件操作编号不能替换原请求。");
                }
                return CompletableFuture.completedStage(existing.receipt());
            }
            if (closing || changing) {
                throw new IllegalStateException("插件正在完成上一次资源切换，请查询原操作。");
            }
            clients.refresh();
            checkRevision(request.expectedRevision());
            var preview = preview(request.pluginId(), request.enabled());
            if (!request.includeDependencies() && preview.affected().stream()
                    .anyMatch(id -> !id.equals(request.pluginId()))) {
                throw new IllegalArgumentException("本次操作影响其他插件，请先确认完整依赖影响。");
            }
            changing = true;
            settled = new CompletableFuture<>();
            String original = store.snapshot().revision();
            try {
                store.save(request.pluginId(), request.enabled(), original, context::checkActive, planned -> {
                    var receipt = new PluginManagement.Receipt(request.operationId(), "APPLYING", revision(),
                            "启用意愿正在保存或应用，请使用原编号查询。", preview.restartRequired());
                    journal.put(new PluginOperationJournal.Entry(context.subject(), request, original, planned,
                            Instant.now(), receipt));
                });
                committed = java.util.Objects.requireNonNull(journal.find(request.operationId()));
            } catch (RuntimeException failure) {
                try {
                    recoverFailedSave(request.operationId());
                } catch (RuntimeException recoveryFailure) {
                    failure.addSuppressed(recoveryFailure);
                }
                changing = false;
                settled.complete(null);
                throw failure;
            }
        }
        return runtime.reconcile(store.snapshot().enabled(), request.pluginId(), false).handle((ignored, failure) -> {
            synchronized (this) {
                try {
                    var receipt = failure == null ? receipt(request)
                            : new PluginManagement.Receipt(request.operationId(), "FAILED", revision(),
                            "启用意愿已保存，资源协调失败；请保留原编号并检查插件状态。", false);
                    journal.put(committed.with(receipt));
                    return receipt;
                } finally {
                    changing = false;
                    settled.complete(null);
                }
            }
        }).toCompletableFuture().minimalCompletionStage();
    }

    /**
     * 读取当前可信主体原操作的结果，不触发业务重试。
     * @param id 操作编号
     * @param context 可信主体
     * @return 真实保留回执
     */
    public synchronized PluginManagement.Receipt operation(UUID id, ManagementContext context) {
        context.checkActive();
        requireReady();
        var entry = journal.find(id);
        if (entry == null) {
            throw new IllegalArgumentException("插件操作不存在或已超过回执保留期。");
        }
        verifyOwner(entry, context);
        return entry.receipt();
    }

    /**
     * 创建使用当前依赖快照的预览。
     * @param id 插件标识
     * @param enabled 目标意愿
     * @return 当前影响及阻塞
     */
    private PluginManagement.Preview preview(String id, boolean enabled) {
        plugins.requireRegistered(id);
        if (!enabled && plugins.descriptor(id).mandatory()) {
            throw new IllegalArgumentException("宿主基础模块不能通过插件管理关闭。");
        }
        var intentions = new HashMap<String, Boolean>();
        plugins.list().forEach(view -> intentions.put(view.descriptor().id(), runtime.appliedIntent(view.descriptor().id())));
        intentions.put(id, enabled);
        var targets = runtime.targets(intentions);
        var affected = new ArrayList<String>();
        for (var view : plugins.list()) {
            if (view.enabled() != targets.get(view.descriptor().id())) {
                affected.add(view.descriptor().id());
            }
        }
        if (!affected.contains(id)) {
            affected.add(id);
        }
        // 2026/11/07：缺失依赖只能阻止启用，不能阻止用户保存关闭意愿。
        var blockers = new ArrayList<String>();
        if (enabled) {
            blockers.addAll(plugins.blockers(id));
            plugins.dependencies(id).stream().filter(dependency -> !plugins.isEnabled(dependency))
                    .forEach(dependency -> blockers.add("依赖插件尚未启用：" + dependency));
        }
        return new PluginManagement.Preview(id, enabled, revision(), affected, blockers, runtime.needsRestart(id, enabled));
    }

    /**
     * 按实际资源状态生成结果，不将依赖阻塞当作启用成功。
     * @param request 已持久化的请求
     * @return 当前真实结果
     */
    private PluginManagement.Receipt receipt(PluginManagement.Change request) {
        String id = request.pluginId();
        boolean pending = request.enabled() != runtime.appliedIntent(id);
        if (pending) {
            return new PluginManagement.Receipt(request.operationId(), "PENDING_RESTART", revision(),
                    "启用意愿已保存，需要重启应用后生效。", true);
        }
        var status = runtime.status(id);
        boolean applied = plugins.isEnabled(id) == request.enabled() && !status.state().equals("FAILED");
        return new PluginManagement.Receipt(request.operationId(), applied ? "APPLIED" : status.state(), revision(),
                applied ? "插件启用意愿已保存并生效。" : status.reason(), false);
    }

    /**
     * 对中断操作比较真实原文件版本，不用当前状态猜测此前是否提交。
     */
    private void recover() {
        String disk = store.snapshot().revision();
        for (var entry : journal.pending()) {
            PluginManagement.Receipt receipt;
            if (disk.equals(entry.planned())) {
                receipt = receipt(entry.request());
            } else if (disk.equals(entry.expected())) {
                receipt = new PluginManagement.Receipt(entry.request().operationId(), "NOT_APPLIED", revision(),
                        "中断前未替换启用配置，本次意愿未保存。", false);
            } else {
                receipt = new PluginManagement.Receipt(entry.request().operationId(), "INDETERMINATE", revision(),
                        "配置在操作中断后发生其他变化，无法确认原操作；请检查当前插件状态。", false);
            }
            journal.put(entry.with(receipt));
        }
    }

    /**
     * 预写后保存失败时立即核对真实文件，不让已经确定未提交的操作永远处于执行中。
     * @param operationId 本次固定操作编号
     */
    private void recoverFailedSave(UUID operationId) {
        // 2026/11/07：预写前的文件冲突也刷新快照，下一次预览须基于真实持久状态。
        String actual = store.refresh().revision();
        var entry = journal.find(operationId);
        if (entry == null) {
            return;
        }
        String state = "INDETERMINATE";
        String note = "保存期间发生错误，请核对当前插件状态后重新预览；本操作没有确认完成生命周期。";
        if (!entry.expected().equals(entry.planned()) && actual.equals(entry.expected())) {
            state = "NOT_APPLIED";
            note = "插件启用意愿未写入，原操作没有应用；可刷新后使用新编号重试。";
        } else if (!entry.expected().equals(entry.planned()) && actual.equals(entry.planned())) {
            state = "SAVED_NOT_APPLIED";
            note = "启用意愿已经写入，但资源应用未启动；请使用新编号显式重试或重启应用。";
        }
        journal.put(entry.with(new PluginManagement.Receipt(operationId, state, revision(), note, false)));
    }

    /**
     * 组合持久、依赖和生命周期版本。
     * @return 不含配置内容的稳定版本
     */
    private String revision() {
        return store.snapshot().revision() + ":" + plugins.revision() + ":" + runtime.revision();
    }

    /**
     * 拒绝过期预览，不能在依赖图变化后应用旧确认。
     * @param expected 预期版本
     */
    private void checkRevision(String expected) {
        if (!revision().equals(expected)) {
            throw new ConcurrentModificationException("插件目录或启用状态已改变，请刷新后重新预览。");
        }
    }

    /**
     * 校验回执归属，不向其他主体泄露原操作。
     * @param entry 原回执
     * @param context 当前可信上下文
     */
    private void verifyOwner(PluginOperationJournal.Entry entry, ManagementContext context) {
        if (!entry.subject().equals(context.subject())) {
            throw new SecurityException("无权查看或重用其他主体的插件操作。");
        }
    }

    /** 确保初始化失败不会被解释为空目录或固定成功。 */
    private void requireReady() {
        if (!ready) {
            throw new IllegalStateException("插件管理尚未完成初始化。");
        }
    }

    /** 启动由有序 ApplicationRunner 完成，禁止容器在配置迁移前自动激活插件。 */
    @Override
    public void start() {
    }

    /** @return 由配置迁移后的运行器显式初始化，不参与默认自动启动 */
    @Override
    public boolean isAutoStartup() {
        return false;
    }

    /** @return 实际资源仍由本服务拥有时为真，停用期间仍等待真实结束 */
    @Override
    public synchronized boolean isRunning() {
        return (ready || changing) && !closed;
    }

    /** @return 在数据库、客户端等共享资源销毁前停止插件业务 */
    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    /**
     * 最终销毁等待正在进行的启停操作，再按依赖逆序释放全部业务资源。
     * @param callback 真实排空后通知容器继续销毁共享资源
     */
    @Override
    public void stop(@NonNull Runnable callback) {
        final CompletionStage<Void> pending;
        final CompletableFuture<Void> finished;
        final boolean initiate;
        synchronized (this) {
            closing = true;
            pending = settled;
            initiate = shutdown == null || shutdown.isCompletedExceptionally();
            if (initiate) {
                shutdown = new CompletableFuture<>();
            }
            finished = java.util.Objects.requireNonNull(shutdown);
        }
        finished.thenRun(callback);
        if (initiate) {
            pending.thenCompose(ignored -> runtime.shutdown()).whenComplete((ignored, failure) -> {
                if (failure == null) {
                    synchronized (this) {
                        closed = true;
                    }
                    finished.complete(null);
                } else {
                    finished.completeExceptionally(failure);
                }
            });
        }
    }

    /**
     * 为显式容器停止提供有界等待，不改变磁盘启用意愿。
     * @throws java.util.concurrent.CompletionException 当实际停止超过期限时
     */
    @Override
    public void stop() {
        stop(() -> {});
        final CompletableFuture<Void> observed;
        synchronized (this) {
            observed = java.util.Objects.requireNonNull(shutdown).copy();
        }
        observed.orTimeout(60, TimeUnit.SECONDS).join();
    }
}
