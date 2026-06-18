package org.zexnocs.teanekoagent_old.personality.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent_old.file_config.personality.AgentPersonalityRuntimeConfig;

/**
 * 默认人格配置端口实现。
 * <br>当宿主应用没有注册自己的配置适配器时使用，始终返回默认配置。
 *
 * @author zExNocs
 * @date 2026/06/09
 * @since 4.4.1
 */
@Service
@ConditionalOnMissingBean(IAgentPersonalityConfigPort.class)
public class DefaultAgentPersonalityConfigPort implements IAgentPersonalityConfigPort {
    /**
     * 获取默认人格运行配置。
     *
     * @param scopeId 作用域 ID。
     * @param agentId agent ID。
     * @return 默认人格运行配置。
     */
    @Override
    public AgentPersonalityRuntimeConfig getConfig(String scopeId, String agentId) {
        return AgentPersonalityRuntimeConfig.defaults();
    }
}
