package org.zexnocs.teanekoagent.plugin;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.zexnocs.teanekoagent.runtime.IAgentToolSource;
import org.zexnocs.teanekoagent.runtime.prompt.IAgentPromptSource;
import java.util.Objects;

/**
 * 将既有工具和资料适配为普通能力包，内置能力与插件使用同一路径。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：更新分层重构后的类型引用，保持原调用与生命周期行为。
 *
 * @param pluginId 所属插件
 * @param id 局部能力 ID
 * @param defaultEnabled 是否默认安装
 * @param tools 原工具源
 * @param prompts 原资料源
 * @param promptNames 来源拥有的资料名称
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.6.0-l
 * @version 4.8.0-l
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
