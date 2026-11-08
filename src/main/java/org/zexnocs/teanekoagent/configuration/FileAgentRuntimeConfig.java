package org.zexnocs.teanekoagent.configuration;

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.llm.file_config.LLMModelFileConfig;
import org.zexnocs.teanekoagent.llm.framework.interaction.ILLMInteractionDefaults;
import org.zexnocs.teanekoagent.llm.framework.interaction.LLMInteractionSettings;
import org.zexnocs.teanekoagent.llm.framework.model.LLMConfigurationException;
import org.zexnocs.teanekoagent.configuration.file_config.AgentMainFileConfig;
import org.zexnocs.teanekoagent.runtime.access.AgentRuntimeConfig;
import org.zexnocs.teanekoagent.runtime.access.IAgentRuntimeConfigPort;
import org.zexnocs.teanekocore.file_config.interfaces.IFileConfigService;

import java.util.Objects;

/**
 * 适配本地主配置和 LLM 交互默认设置，读取故障只影响 Agent 就绪状态。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * <br>2026/11/06：保留失败阶段及安全根因，避免配置或异步边界丢失诊断。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.5.0-l
 * @version 4.8.4-l
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
        } catch (RuntimeException failure) {
            throw new LLMConfigurationException("Agent 本地配置不可用，请修复并重载主配置或 LLM 交互设置。", failure);
        }
    }
}
