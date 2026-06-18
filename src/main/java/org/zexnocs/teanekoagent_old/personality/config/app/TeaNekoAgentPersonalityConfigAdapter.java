package org.zexnocs.teanekoagent_old.personality.config.app;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent_old.file_config.personality.AgentPersonalityConfigData;
import org.zexnocs.teanekoagent_old.file_config.personality.AgentPersonalityRuntimeConfig;
import org.zexnocs.teanekoagent_old.personality.config.AgentConfigKey;
import org.zexnocs.teanekoagent_old.personality.config.IAgentPersonalityConfigPort;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataService;

/**
 * TeaNeko App 侧 Agent 人格配置适配器。
 * <br>该类实现 Agent Core 的配置读取端口，将 TeaNeko ConfigData 中的配置转换为 Agent 运行配置。
 *
 * @author zExNocs
 * @date 2026/06/09
 * @since 4.4.1
 */
@Service
@RequiredArgsConstructor
public class TeaNekoAgentPersonalityConfigAdapter implements IAgentPersonalityConfigPort {
    /**
     * TeaNeko ConfigData 服务。
     */
    private final IConfigDataService configDataService;

    /**
     * Agent 人格配置管理器。
     */
    private final TeaNekoAgentPersonalityConfigManager configManager;

    /**
     * 获取指定作用域和 agent 的人格运行配置。
     * <br>优先读取 {@code scopeId:agent:agentId}，不存在时回退到 scope 级配置。
     *
     * @param scopeId 作用域 ID。
     * @param agentId agent ID。
     * @return Agent 人格运行配置。
     */
    @Override
    public AgentPersonalityRuntimeConfig getConfig(String scopeId, String agentId) {
        var key = new AgentConfigKey(scopeId, agentId).getKey();
        var config = configDataService.getConfigData(configManager, AgentPersonalityConfigData.class, key);
        if (config.isPresent()) {
            return config.get().toRuntimeConfig();
        }

        if (agentId != null && !agentId.isBlank()) {
            return configDataService
                    .getConfigData(configManager, AgentPersonalityConfigData.class, new AgentConfigKey(scopeId, "").getKey())
                    .map(AgentPersonalityConfigData::toRuntimeConfig)
                    .orElseGet(AgentPersonalityRuntimeConfig::defaults);
        }

        return AgentPersonalityRuntimeConfig.defaults();
    }
}
