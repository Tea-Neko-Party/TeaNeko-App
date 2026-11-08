package org.zexnocs.teanekoagent.integration.web;

// 2026/11/07：接口统一使用 I 前缀，调用合同与持久数据保持不变。

import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.component.AgentComponentRegistry;
import org.zexnocs.teanekoagent.component.tool.TeaNekoAgentToolRegistry;
import org.zexnocs.teanekoagent.plugin.AgentPluginRuntime;
import org.zexnocs.teanekoapp.plugin.IPluginContributionSource;
import org.zexnocs.teanekoapp.plugin.PluginContribution;
import org.zexnocs.teanekocore.plugin.PluginRegistry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * 向 App 中立目录贡献 Agent 已安装组件、系统、工具和能力的静态说明。
 * <br>2026/11/07：普通 API 仍按插件状态过滤，总管理说明不开放装配或执行。
 * <br>2026/11/07：仅汇总业务插件贡献，原生组件、工具与宿主能力留在系统目录。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
@Service
public final class AgentPluginContributionSource implements IPluginContributionSource {
    private final AgentComponentRegistry components;
    private final TeaNekoAgentToolRegistry tools;
    private final AgentPluginRuntime runtime;
    private final PluginRegistry plugins;

    /**
     * 借用已经建立的安装目录，构造时不读取业务数据。
     * @param components 组件声明缓存
     * @param tools 工具声明缓存
     * @param runtime 能力与系统声明缓存
     * @param plugins 区分业务插件和稳定宿主能力
     */
    public AgentPluginContributionSource(AgentComponentRegistry components, TeaNekoAgentToolRegistry tools,
            AgentPluginRuntime runtime, PluginRegistry plugins) {
        this.components = components;
        this.tools = tools;
        this.runtime = runtime;
        this.plugins = plugins;
    }

    /** @return 包含停用贡献的只读列表，不读取当前配置或数据库 */
    @Override
    public List<PluginContribution> contributions() {
        var result = new ArrayList<PluginContribution>();
        var systems = new LinkedHashMap<String, PluginContribution>();
        for (var component : components.installedDescription()) {
            if (component.pluginId() == null || plugins.isHostCapability(component.pluginId())) {
                continue;
            }
            result.add(new PluginContribution(component.pluginId(), "component", component.id(),
                    component.name(), component.description()));
            for (var system : component.systems()) {
                var item = new PluginContribution(component.pluginId(), "system", system.id(), system.id(),
                        "执行阶段：" + system.phase());
                systems.put(item.pluginId() + ":" + item.id(), item);
            }
        }
        for (var system : runtime.installedSystems()) {
            if (plugins.isHostCapability(system.pluginId())) {
                continue;
            }
            var item = new PluginContribution(system.pluginId(), "system", system.id(), system.id(),
                    "执行阶段：" + system.phase());
            var previous = systems.putIfAbsent(item.pluginId() + ":" + item.id(), item);
            if (previous != null && !previous.equals(item)) {
                throw new IllegalArgumentException("Agent 系统贡献说明冲突：" + item.id());
            }
        }
        result.addAll(systems.values());
        for (var tool : tools.installedDescription()) {
            if (tool.pluginId() == null || plugins.isHostCapability(tool.pluginId())) {
                continue;
            }
            result.add(new PluginContribution(tool.pluginId(), "tool", tool.name(),
                    tool.name(), tool.description()));
        }
        for (var feature : runtime.featureIds()) {
            var owner = feature.substring(0, feature.indexOf(':'));
            if (plugins.isHostCapability(owner)) {
                continue;
            }
            result.add(new PluginContribution(owner, "feature", feature,
                    feature, "可按实例选择的 Agent 能力。"));
        }
        return List.copyOf(result);
    }
}
