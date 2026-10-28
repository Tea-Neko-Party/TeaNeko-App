package org.zexnocs.teanekoagent.agent;

import java.util.Objects;

/**
 * 只负责实例组合与拥有关系，不读取模型、人格或 scope 配置。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
public final class AgentFactory implements IAgentFactory {
    private final IAgentRegistry registry;

    /**
     * 注入实例拥有者，不在构造时创建默认 Agent。
     * @param registry 实例注册表
     */
    public AgentFactory(IAgentRegistry registry) { this.registry = Objects.requireNonNull(registry, "实例注册表不能为空。"); }

    /**
     * 构造全新实体，登记失败时关闭未移交的生命周期。
     * @param definition 可共享只读定义
     * @return 新实例
     * @throws IllegalStateException 当实例注册表拒绝登记时
     */
    @Override public AgentHandle create(AgentDefinition definition) {
        var handle = new AgentHandle(definition);
        try { registry.register(handle); return handle; }
        catch (RuntimeException failure) { handle.close(); throw failure; }
    }
}
