package org.zexnocs.teanekoapp.web.client;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Service;
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
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.7.0-l
 */
@Service
public final class ClientManagementDirectory {
    private final AnnotatedBeanRegistry<String, IClientManagementAdapter> adapters;
    private final ObjectProvider<PluginRegistry> plugins;

    /**
     * @param scanner 当前 Spring Bean 扫描器
     * @param plugins 可选宿主插件目录
     */
    public ClientManagementDirectory(IBeanScanner scanner, ObjectProvider<PluginRegistry> plugins) {
        this.plugins = plugins;
        adapters = new AnnotatedBeanRegistry<>(scanner, TeaNekoClientManagement.class,
                IClientManagementAdapter.class, annotation -> {
                    if (!annotation.value().matches("[a-z][a-z0-9_-]{0,63}") || annotation.version() != 1) {
                        throw new IllegalArgumentException("客户端管理注解的标识或版本无效。");
                    }
                    return annotation.value();
                });
    }

    /** @return 当前可见平台的实际只读状态 */
    public List<IClientManagementAdapter.View> list() {
        var values = new ArrayList<IClientManagementAdapter.View>();
        var registry = plugins.getIfAvailable();
        for (var item : adapters.entries().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            var owner = AnnotatedElementUtils.findMergedAnnotation(item.getValue().type(), PluginOwned.class);
            if (owner != null && (registry == null || !registry.isEnabled(owner.value()))) {
                continue;
            }
            var token = owner == null ? null : Objects.requireNonNull(registry).capture(owner.value());
            var view = item.getValue().bean().describe();
            if (!view.clientId().equals(item.getKey()) || view.connections() < 0 || view.name().length() > 80
                    || view.explanation().length() > 256) {
                throw new IllegalStateException("客户端管理视图不符合已声明合同。");
            }
            if (token != null) {
                Objects.requireNonNull(registry).check(token);
            }
            values.add(view);
        }
        return List.copyOf(values);
    }

    /**
     * 2026/11/03：使用精确已注册平台，保持原插件的启用和返回边界。
     * @param client 平台标识
     * @param action 明确应用回调，不接收反射方法名
     * @param <T> 返回类型
     * @return 原异步操作及代次复核
     */
    public <T> CompletionStage<T> invoke(String client,
                                        Function<IClientManagementAdapter, CompletionStage<T>> action) {
        var checked = guard(client, () -> {});
        return action.apply(adapters.require(client).bean()).thenApply(value -> {
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
        authorization.run();
        var entry = adapters.require(client);
        var owner = AnnotatedElementUtils.findMergedAnnotation(entry.type(), PluginOwned.class);
        var registry = plugins.getIfAvailable();
        if (owner != null && (registry == null || !registry.isEnabled(owner.value()))) {
            throw new IllegalStateException("该平台所属插件当前不可用。");
        }
        var token = owner == null ? null : Objects.requireNonNull(registry).capture(owner.value());
        return () -> {
            authorization.run();
            if (token != null) {
                Objects.requireNonNull(registry).check(token);
            }
        };
    }
}
