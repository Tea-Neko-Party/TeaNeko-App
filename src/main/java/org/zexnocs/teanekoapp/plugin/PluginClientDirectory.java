package org.zexnocs.teanekoapp.plugin;

import org.springframework.stereotype.Component;
import org.zexnocs.teanekoapp.client.TeaNekoClientScanner;
import org.zexnocs.teanekocore.plugin.PluginRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * 将客户端安装事实转换为插件依赖图；平台连接状态不参与插件激活决策。
 * <br>2026/11/07：缓存静态需求，显式刷新发布完整可用性事实。
 * <br>2026/11/07：优先编译插件注解中的客户端需求，拒绝重复来源。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
@Component
public final class PluginClientDirectory {
    private final TeaNekoClientScanner clients;
    private final PluginRegistry plugins;
    private final Map<String, Set<String>> requirements;

    /**
     * 固定已装配贡献，不在每次目录读取时发现声明。
     * @param clients 原客户端安装目录
     * @param plugins 原插件目录
     * @param declarations 必需客户端声明
     */
    public PluginClientDirectory(TeaNekoClientScanner clients, PluginRegistry plugins,
                                 List<PluginClientRequirement> declarations) {
        this.clients = clients;
        this.plugins = plugins;
        var values = new TreeMap<String, Set<String>>();
        // 2026/11/07：注册表只保存中立 ID，由 App 负责解析实际客户端能力。
        for (var plugin : plugins.list()) {
            var id = plugin.descriptor().id();
            var required = plugins.requiredClients(id);
            if (!required.isEmpty()) {
                values.put(id, required);
            }
        }
        for (var declaration : declarations) {
            plugins.descriptor(declaration.pluginId());
            if (values.putIfAbsent(declaration.pluginId(), declaration.requiredClientIds()) != null) {
                throw new IllegalArgumentException("插件客户端要求重复：" + declaration.pluginId());
            }
        }
        requirements = Map.copyOf(values);
    }

    /**
     * 读取当前插件的完整静态客户端要求。
     * @param pluginId 插件标识
     * @return 不可变客户端集合
     */
    public Set<String> requiredClients(String pluginId) {
        return requirements.getOrDefault(pluginId, Set.of());
    }

    /**
     * 在初始化或明确管理操作时更新中立事实，失败不发布部分依赖图。
     */
    public void refresh() {
        var installed = clients.installed();
        var edges = new TreeMap<String, Set<String>>();
        var reasons = new TreeMap<String, List<String>>();
        requirements.forEach((id, needed) -> {
            var dependencies = new TreeSet<String>();
            var blockers = new ArrayList<String>();
            for (var client : needed) {
                if (!installed.handlers().containsKey(client)) {
                    blockers.add("缺少已注册客户端：" + client);
                } else {
                    String owner = installed.pluginOwners().get(client);
                    if (owner != null && !owner.equals(id)) {
                        dependencies.add(owner);
                    }
                }
            }
            edges.put(id, Set.copyOf(dependencies));
            reasons.put(id, List.copyOf(blockers));
        });
        plugins.publishAvailability(edges, reasons);
    }
}
