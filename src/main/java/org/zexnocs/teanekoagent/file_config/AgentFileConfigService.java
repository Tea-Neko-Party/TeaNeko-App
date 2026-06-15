package org.zexnocs.teanekoagent.file_config;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.file_config.interfaces.IAgentFileConfigService;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelId;
import org.zexnocs.teanekocore.file_config.exception.FileConfigDataNotFoundException;
import org.zexnocs.teanekocore.file_config.interfaces.IFileConfigService;

import java.util.Optional;

/**
 * Agent 文件配置服务。
 * <br>负责从 TeaNeko Core 文件配置系统中读取 agent 运行时配置，并在配置缺失时提供默认值。
 *
 * @author zExNocs
 * @date 2026/06/11
 * @since 4.4.1
 */
@Service
@RequiredArgsConstructor
public class AgentFileConfigService implements IAgentFileConfigService {
    /**
     * TeaNeko Core 文件配置服务。
     */
    private final IFileConfigService fileConfigService;

    /**
     * 获取 Agent 主配置。
     *
     * @return Agent 主配置。
     */
    @Override
    public AgentMainFileConfig getMainConfig() {
        try {
            return fileConfigService.get(AgentMainFileConfig.class);
        } catch (FileConfigDataNotFoundException ignored) {
            return new AgentMainFileConfig();
        }
    }

    /**
     * 获取 Agent token 监控器配置。
     *
     * @return Agent token 监控器配置。
     */
    @Override
    public AgentTokenMonitorFileConfig getTokenMonitorConfig() {
        try {
            return fileConfigService.get(AgentTokenMonitorFileConfig.class);
        } catch (FileConfigDataNotFoundException ignored) {
            return new AgentTokenMonitorFileConfig();
        }
    }

    /**
     * 查找 Agent 默认模型适配器 ID。
     *
     * @return Agent 默认模型适配器 ID。
     */
    @Override
    public Optional<LLMModelId> findDefaultModelId() {
        return getMainConfig().findDefaultModelId();
    }
}
