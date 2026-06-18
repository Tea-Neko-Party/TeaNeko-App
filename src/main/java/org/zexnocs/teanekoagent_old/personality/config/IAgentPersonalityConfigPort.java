package org.zexnocs.teanekoagent_old.personality.config;

import org.zexnocs.teanekoagent_old.file_config.personality.AgentPersonalityRuntimeConfig;

/**
 * Agent Core 读取作用域级人格配置的端口接口。
 * <br>具体应用应在适配层实现该接口，避免 Agent Core 直接依赖应用配置系统。
 *
 * @author zExNocs
 * @date 2026/06/09
 * @since 4.4.1
 */
public interface IAgentPersonalityConfigPort {
    /**
     * 获取指定作用域和 agent 的人格运行配置。
     *
     * @param scopeId 作用域 ID。
     * @param agentId agent ID。
     * @return 人格运行配置。
     */
    AgentPersonalityRuntimeConfig getConfig(String scopeId, String agentId);
}
