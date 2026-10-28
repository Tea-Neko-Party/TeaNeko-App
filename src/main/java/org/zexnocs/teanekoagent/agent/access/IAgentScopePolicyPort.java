package org.zexnocs.teanekoagent.agent.access;

import org.zexnocs.teanekoagent.agent.AgentScope;

/**
 * 读取真实 scope 登记与开关的边界，不负责默认注册或修改配置。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
@FunctionalInterface
public interface IAgentScopePolicyPort {
    /**
     * 读取当前策略；缺失必须返回 unregistered，后端故障不得当作启用。
     * @param scope 宿主定位的作用域
     * @return 不可变策略
     */
    AgentScopePolicy read(AgentScope scope);
}
