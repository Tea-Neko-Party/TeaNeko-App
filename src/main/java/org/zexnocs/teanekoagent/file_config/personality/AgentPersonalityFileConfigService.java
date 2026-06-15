package org.zexnocs.teanekoagent.file_config.personality;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.file_config.exception.FileConfigDataNotFoundException;
import org.zexnocs.teanekocore.file_config.interfaces.IFileConfigService;

/**
 * 文件主性格配置读取服务。
 *
 * @author zExNocs
 * @date 2026/06/10
 * @since 4.4.1
 */
@Service
@RequiredArgsConstructor
public class AgentPersonalityFileConfigService {
    /**
     * 文件配置服务。
     */
    private final IFileConfigService fileConfigService;

    /**
     * 获取 Agent 人格文件配置。
     * <br>当文件配置不存在时返回默认空配置。
     *
     * @return Agent 人格文件配置。
     */
    public AgentPersonalityFileConfig getConfig() {
        try {
            return fileConfigService.get(AgentPersonalityFileConfig.class);
        } catch (FileConfigDataNotFoundException ignored) {
            return new AgentPersonalityFileConfig();
        }
    }

    /**
     * 获取默认 agent ID。
     *
     * @return 默认 agent ID。
     */
    public String getDefaultAgentId() {
        var defaultAgentId = getConfig().getDefaultAgentId();
        return defaultAgentId == null || defaultAgentId.isBlank() ? "teaneko" : defaultAgentId.trim();
    }

    /**
     * 获取指定 agent 的基础人格。
     * <br>如果文件中不存在对应人格，则返回内置兜底人格。
     *
     * @param agentId agent ID。
     * @return 基础人格定义。
     */
    public AgentPersonalityDefinition getPersonality(String agentId) {
        var resolvedAgentId = agentId == null || agentId.isBlank() ? getDefaultAgentId() : agentId.trim();
        return getConfig()
                .findPersonality(resolvedAgentId)
                .orElseGet(() -> AgentPersonalityDefinition.fallback(resolvedAgentId));
    }
}
