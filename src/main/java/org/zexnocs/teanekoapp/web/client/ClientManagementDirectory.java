package org.zexnocs.teanekoapp.web.client;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoapp.client.TeaNekoClientScanner;
import org.zexnocs.teanekocore.framework.extension.AnnotatedBeanRegistry;
import org.zexnocs.teanekocore.plugin.PluginOwned;
import org.zexnocs.teanekocore.plugin.PluginRegistry;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IBeanScanner;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * 复用 Core 注解注册目录，未知客户端不伪造能力，插件关闭后不继续公开其视图。
 * <br>2026/11/03：账号与聊天查询在异步完成时再次复核插件代次。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/06：缓存客户端插件声明，重扫失败保留原目录并实时复核插件代次。
 * <br>2026/11/07：继承实际客户端插件归属，统一视图与异步访问门禁。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.9.0-l
 */
@Service
public final class ClientManagementDirectory implements org.zexnocs.teanekocore.reload.api.IScanner {
    private final IBeanScanner scanner;
    private volatile Map<String, Entry> adapters = Map.of();
    private volatile boolean initialized;
    private final ObjectProvider<PluginRegistry> plugins;
    private @org.jspecify.annotations.Nullable TeaNekoClientScanner clients;

    /**
     * 2026/11/07：业务查询只读取已发布的客户端归属，不触发发现扫描。
     * @param clients 实际客户端安装目录
     */
    @Autowired
    public void installClients(TeaNekoClientScanner clients) {
        this.clients = clients;
    }

    /**
     * 2026/11/07：管理适配器继承实际客户端所有权，显式冲突不得绕过门禁。
     * @param client 稳定客户端标识
     * @param entry 已发布管理适配器
     * @return 实际插件归属，宿主官方客户端可为空
     * @throws IllegalStateException 当目录未就绪或两份声明归属冲突时
     */
    private @org.jspecify.annotations.Nullable String owner(String client, Entry entry) {
        var directory = clients;
        var declared = entry.owner();
        var actual = directory == null ? null : directory.pluginOwner(client);
        if (actual != null && declared != null && !actual.equals(declared)) {
            throw new IllegalStateException("客户端与管理适配器的插件归属冲突：" + client);
        }
        return actual == null ? declared : actual;
    }

    /**
     * @param scanner 当前 Spring Bean 扫描器
     * @param plugins 可选宿主插件目录
     */
    public ClientManagementDirectory(IBeanScanner scanner, ObjectProvider<PluginRegistry> plugins) {
        this.plugins = plugins;
        this.scanner = scanner;
        init();
    }

    /** 2026/11/06：仅初始化和显式重扫发现客户端声明。 */
    @Override
    public synchronized void init() {
        if (!initialized) reload();
    }

    /** 完整目录构建后原子发布，失败保留原目录和运行状态复核。 */
    @Override
    public synchronized void reload() {
        var discovered = new AnnotatedBeanRegistry<>(scanner, TeaNekoClientManagement.class,
                IClientManagementAdapter.class, annotation -> {
                    if (!annotation.value().matches("[a-z][a-z0-9_-]{0,63}") || annotation.version() != 1) {
                        throw new IllegalArgumentException("客户端管理注解的标识或版本无效。");
                    }
                    return annotation.value();
                });
        var next = new java.util.LinkedHashMap<String, Entry>();
        discovered.entries().forEach((id, entry) -> {
            var owner = AnnotatedElementUtils.findMergedAnnotation(entry.type(), PluginOwned.class);
            next.put(id, new Entry(entry.bean(), owner == null ? null : owner.value()));
        });
        adapters = Map.copyOf(next);
        initialized = true;
    }

    /**
     * @param client 精确客户端标识
     * @return 已发布条目
     */
    private Entry require(String client) {
        if (!initialized) throw new IllegalStateException("客户端目录未就绪，请显式重扫。");
        var entry = adapters.get(client);
        if (entry == null) throw new IllegalArgumentException("未注册的客户端管理入口。");
        return entry;
    }

    /**
     * 缓存静态归属，连接与启停状态仍实时读取。
     * @param bean 原 Spring 实现
     * @param owner 可选插件标识
     * @author zExNocs
     * @date 2026/11/06
     * @since 4.8.4-l
     * @version 4.8.4-l
     */
    private record Entry(IClientManagementAdapter bean, @org.jspecify.annotations.Nullable String owner) {}

    /**
     * 2026/11/07：视图采用实际客户端归属并在返回前复核插件代次。
     * @return 当前可见平台的实际只读状态
     */
    public List<IClientManagementAdapter.View> list() {
        var values = new ArrayList<IClientManagementAdapter.View>();
        var registry = plugins.getIfAvailable();
        for (var item : adapters.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            var owner = owner(item.getKey(), item.getValue());
            if (owner != null && (registry == null || !registry.isEnabled(owner))) {
                continue;
            }
            var token = owner == null ? null : Objects.requireNonNull(registry).capture(owner);
            var view = item.getValue().bean().describe();
            if (!view.clientId().equals(item.getKey()) || view.connections() < 0 || view.name().length() > 80
                    || view.explanation().length() > 256) {
                throw new IllegalStateException("客户端管理视图不符合已声明合同。");
            }
            if (!Objects.equals(owner, owner(item.getKey(), item.getValue()))) {
                throw new IllegalStateException("客户端插件归属已变化，请重新读取。");
            }
            if (token != null) {
                Objects.requireNonNull(registry).check(token);
            }
            // 2026/11/07：归属使用初始化时确认的真实贡献，不采信业务视图自报的插件 ID。
            values.add(new IClientManagementAdapter.View(view.clientId(), view.name(), view.state(),
                    view.connections(), view.explanation(), view.observedAt(), view.settingsAvailable(), owner));
        }
        return List.copyOf(values);
    }

    /**
     * 2026/11/03：使用精确已注册平台，保持原插件的启用和返回边界。
     * 2026/11/07：实际客户端归属与适配器目录同样属于异步授权复核范围。
     * @param client 平台标识
     * @param action 明确应用回调，不接收反射方法名
     * @param <T> 返回类型
     * @return 原异步操作及代次复核
     */
    public <T> CompletionStage<T> invoke(String client,
                                        Function<IClientManagementAdapter, CompletionStage<T>> action) {
        var entry = require(client);
        var checked = guard(client, entry, () -> {});
        return action.apply(entry.bean()).thenApply(value -> {
            checked.run();
            return value;
        });
    }

    /**
     * 2026/11/03：将平台插件代次复核传到实际写事务，而非只在返回时发现撤权。
     * @param client 已注册平台
     * @param authorization 原调用方授权
     * @return 固定本次插件代次的复核回调
     */
    public Runnable guard(String client, Runnable authorization) {
        return guard(client, require(client), authorization);
    }

    /**
     * @param client 已登记客户端
     * @param entry 此次调用捕获的声明
     * @param authorization 会话授权
     * @return 原插件代次和目录代次的复核
     */
    private Runnable guard(String client, Entry entry, Runnable authorization) {
        authorization.run();
        var owner = owner(client, entry);
        var registry = plugins.getIfAvailable();
        if (owner != null && (registry == null || !registry.isEnabled(owner))) {
            throw new IllegalStateException("该平台所属插件当前不可用。");
        }
        var token = owner == null ? null : Objects.requireNonNull(registry).capture(owner);
        return () -> {
            authorization.run();
            if (adapters.get(client) != entry) {
                throw new IllegalStateException("客户端目录已重载，请重新取得操作入口。");
            }
            if (!Objects.equals(owner, owner(client, entry))) {
                throw new IllegalStateException("客户端插件归属已变化，请重新取得操作入口。");
            }
            if (token != null) {
                Objects.requireNonNull(registry).check(token);
            }
        };
    }
}
