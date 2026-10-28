package org.zexnocs.teanekoagent._app_config;

import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.agent.access.AgentRuntimeConfig;
import org.zexnocs.teanekoagent.agent.access.IAgentRuntimeConfigPort;
import org.zexnocs.teanekoagent.file_config.AgentMainFileConfig;
import org.zexnocs.teanekoagent.llm.file_config.LLMModelFileConfig;
import org.zexnocs.teanekoagent.llm.framework.interaction.ILLMInteractionDefaults;
import org.zexnocs.teanekoagent.llm.framework.interaction.LLMInteractionSettings;
import org.zexnocs.teanekoagent.llm.framework.model.LLMConfigurationException;
import org.zexnocs.teanekocore.file_config.interfaces.IFileConfigService;

import java.util.Objects;

/**
 * 适配本地主配置和 LLM 交互默认设置，读取故障只影响 Agent 就绪状态。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
@Service
public final class FileAgentRuntimeConfig implements IAgentRuntimeConfigPort {
    private final IFileConfigService files;
    private final ILLMInteractionDefaults defaults;

    /**
     * 仅保存端口，不在 App Bean 创建时解析文件或环境变量。
     * @param files 文件配置端口
     * @param defaults LLM 所属的默认交互设置
     */
    public FileAgentRuntimeConfig(IFileConfigService files, ILLMInteractionDefaults defaults) {
        this.files = Objects.requireNonNull(files, "文件配置端口不能为空。");
        this.defaults = Objects.requireNonNull(defaults, "交互默认设置端口不能为空。");
    }

    /**
     * 从当前已加载文件取得开关与固定设置，全局关闭时不继续访问模型设置。
     * 文件修复后由既有文件重载更新数据，下一次检查取得新快照。
     * @return 本地运行设置
     * @throws LLMConfigurationException 当主配置或交互默认值不可用时
     */
    @Override public AgentRuntimeConfig read() {
        try {
            var config = Objects.requireNonNull(files.requireCurrent(AgentMainFileConfig.class));
            boolean enabled = config.isEnabled();
            if (enabled) { Objects.requireNonNull(files.requireCurrent(LLMModelFileConfig.class)); }
            return new AgentRuntimeConfig(enabled, enabled
                    ? Objects.requireNonNull(defaults.resolve()) : LLMInteractionSettings.defaults());
        } catch (RuntimeException ignored) {
            throw new LLMConfigurationException("Agent 本地配置不可用，请修复并重载主配置或 LLM 交互设置。");
        }
    }
}
