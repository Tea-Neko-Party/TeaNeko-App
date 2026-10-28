package org.zexnocs.teanekoagent.agent.access;

import org.zexnocs.teanekoagent.agent.AgentDefinition;

/**
 * Agent 就绪检查边界，可供管理入口使用，不发起模型请求或注册 scope。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
@FunctionalInterface
public interface IAgentReadiness {
    /**
     * 读取当前配置并检查模型与凭据；调用方应在异步业务执行器中调用。
     * @param definition 需要检查的只读定义
     * @return 可公开的就绪结果，不导出凭据或原始异常
     */
    AgentReadiness inspect(AgentDefinition definition);
}
