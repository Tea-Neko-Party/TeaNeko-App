package org.zexnocs.teanekoagent.agent;

/**
 * 从只读定义构造独立 Agent 实例，不自动注册 scope 或启动任何业务活动。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
public interface IAgentFactory {
    /**
     * 创建新实例并将拥有关系登记到实例注册表。
     * @param definition 已冻结定义
     * @return 新实例，不与相同定义的其他实例共享运行状态
     */
    AgentHandle create(AgentDefinition definition);
}
