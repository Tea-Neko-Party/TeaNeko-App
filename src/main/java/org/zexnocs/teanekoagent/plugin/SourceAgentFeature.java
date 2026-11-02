package org.zexnocs.teanekoagent.plugin;

import org.zexnocs.teanekoagent.agent.IAgentToolSource;
import org.zexnocs.teanekoagent.agent.prompt.IAgentPromptSource;
import java.util.Objects;

/**
 * 将既有工具和资料适配为普通能力包，内置能力与插件使用同一路径。
 * @param pluginId 所属插件
 * @param id 局部能力 ID
 * @param defaultEnabled 是否默认安装
 * @param tools 原工具源
 * @param prompts 原资料源
 * @param promptNames 来源拥有的资料名称
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.0.0
 * @version 4.6.0-l
 */
public record SourceAgentFeature(String pluginId, String id, boolean defaultEnabled,
                                IAgentToolSource tools, IAgentPromptSource prompts, java.util.Set<String> promptNames) implements AgentFeature {
    /**
     * 构造不提供资料的工具能力。
     * @param pluginId 所属插件
     * @param id 能力 ID
     * @param defaultEnabled 是否默认安装
     * @param tools 工具源
     * @param prompts 不产生资料的来源
     */
    public SourceAgentFeature(String pluginId, String id, boolean defaultEnabled, IAgentToolSource tools, IAgentPromptSource prompts) {
        this(pluginId, id, defaultEnabled, tools, prompts, java.util.Set.of());
    }
    /**
     * 固定两个类型化来源，注册检查由宿主统一完成。
     * @param pluginId 所属插件
     * @param id 局部 ID
     * @param defaultEnabled 是否默认安装
     * @param tools 工具源
     * @param prompts 资料源
     * @param promptNames 自有来源名
     */
    public SourceAgentFeature { Objects.requireNonNull(tools); Objects.requireNonNull(prompts); promptNames = java.util.Set.copyOf(promptNames); }
}
