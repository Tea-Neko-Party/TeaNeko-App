package org.zexnocs.teanekoagent.agent;

import java.util.Objects;

/**
 * 只负责实例组合与拥有关系，不读取模型、人格或 scope 配置。
 * <br>2026/11/02：校验实例能力并创建插件不可变初始组件。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.6.0-l
 */
public final class AgentFactory implements IAgentFactory {
    private final IAgentRegistry registry;
    // 2026/11/02：独立使用方可明确不安装插件目录。
    private final org.zexnocs.teanekoagent.plugin.AgentPluginRuntime plugins;

    /**
     * 2026/11/02：保留不安装插件目录的独立工厂，不在构造时创建 Agent。
     * @param registry 实例注册表
     */
    public AgentFactory(IAgentRegistry registry) { this(registry, null); }

    /**
     * 组合可选插件装配器，不启动任何 Agent 业务。
     * @param registry 实例拥有者
     * @param plugins 可选能力目录
     */
    public AgentFactory(IAgentRegistry registry, org.zexnocs.teanekoagent.plugin.AgentPluginRuntime plugins) {
        this.registry = Objects.requireNonNull(registry, "实例注册表不能为空。"); this.plugins = plugins;
    }

    /**
     * 构造全新实体，登记失败时关闭未移交的生命周期。
     * @param definition 可共享只读定义
     * @return 新实例
     * @throws IllegalStateException 当实例注册表拒绝登记时
     */
    @Override public AgentHandle create(AgentDefinition definition) {
        // 2026/11/02：未知能力在登记实例前失败，不残留半初始化实例。
        if (plugins == null && !definition.features().isEmpty()) { throw new IllegalStateException("当前工厂未安装插件目录。"); }
        var handle = new AgentHandle(definition, plugins == null ? org.zexnocs.teanekocore.framework.composition.ComponentPatch.empty() : plugins.initial(definition));
        try { registry.register(handle); return handle; }
        catch (RuntimeException failure) { handle.close(); throw failure; }
    }
}
