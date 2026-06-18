package org.zexnocs.teanekoagent_old.file_config.interfaces;

import org.zexnocs.teanekoagent_old.file_config.AgentMainFileConfig;
import org.zexnocs.teanekoagent_old.file_config.AgentTokenMonitorFileConfig;
import org.zexnocs.teanekoagent_old.llm.framework.model.LLMModelId;

import java.util.Optional;

/**
 * Agent 文件配置服务接口。
 * <br>用于读取 agent 运行时相关文件配置，并为业务代码提供带默认值的配置对象。
 *
 * @author zExNocs
 * @date 2026/06/11
 * @since 4.4.1
 */
public interface IAgentFileConfigService {
    /**
     * 获取 Agent 主配置。
     *
     * @return Agent 主配置。
     */
    AgentMainFileConfig getMainConfig();

    /**
     * 获取 Agent token 监控器配置。
     *
     * @return Agent token 监控器配置。
     */
    AgentTokenMonitorFileConfig getTokenMonitorConfig();

    /**
     * 查找 Agent 默认模型适配器 ID。
     *
     * @return Agent 默认模型适配器 ID。
     */
    Optional<LLMModelId> findDefaultModelId();
}
