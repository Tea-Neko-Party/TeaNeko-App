package org.zexnocs.teanekoagent.runtime;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

/**
 * 从只读定义构造独立 Agent 实例，不自动注册 scope 或启动任何业务活动。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.5.0-l
 * @version 4.8.0-l
 */
public interface IAgentFactory {
    /**
     * 创建新实例并将拥有关系登记到实例注册表。
     * @param definition 已冻结定义
     * @return 新实例，不与相同定义的其他实例共享运行状态
     */
    AgentHandle create(AgentDefinition definition);
}
