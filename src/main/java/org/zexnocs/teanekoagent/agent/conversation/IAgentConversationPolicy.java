package org.zexnocs.teanekoagent.agent.conversation;

import org.zexnocs.teanekoagent.agent.AgentHandle;
import org.zexnocs.teanekoagent.agent.AgentRunRequest;

/**
 * 为实际出队轮次解析历史和重放策略；配置访问在业务执行域、邮箱锁外进行。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
@FunctionalInterface
public interface IAgentConversationPolicy {
    /**
     * 固定本轮策略，不创建实例或注册作用域。
     * @param handle 目标实例
     * @param request 可信原始输入
     * @return 已校验的非空策略
     * @throws IllegalStateException 当本地或作用域配置不可用时
     */
    AgentConversationOptions resolve(AgentHandle handle, AgentRunRequest request);
}
