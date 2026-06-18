package org.zexnocs.teanekoagent_old.file_config.personality;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Agent Core 使用的不可变人格运行配置。
 *
 * @param enabled                    当前作用域是否启用 Agent。
 * @param agentId                    agent ID。
 * @param customPersonalityEnabled   是否启用自定义性格。
 * @param customPersonality          自定义基础性格。
 * @param customSpeakingStyle        自定义说话风格。
 * @param customBoundaries           自定义硬边界。
 * @param personalityLearningEnabled 是否启用性格学习。
 * @param memoryEnabled              是否启用长期记忆读取。
 * @param modelId                    模型适配器 ID。
 * @param model                      供应商侧具体模型名。
 * @param modelApi                   供应商侧 API 名称或 endpoint。
 * @param baseUrl                    供应商 base URL。
 * @param temperature                采样温度。
 * @param topP                       top-p 参数。
 * @param maxTokens                  输出 token 上限。
 * @author zExNocs
 * @date 2026/06/09
 * @since 4.4.1
 */
public record AgentPersonalityRuntimeConfig(
        boolean enabled,
        String agentId,
        boolean customPersonalityEnabled,
        String customPersonality,
        String customSpeakingStyle,
        List<String> customBoundaries,
        boolean personalityLearningEnabled,
        boolean memoryEnabled,
        String modelId,
        String model,
        String modelApi,
        String baseUrl,
        @Nullable Double temperature,
        @Nullable Double topP,
        @Nullable Integer maxTokens
) {
    /**
     * 创建 Agent 人格运行配置。
     *
     * @param enabled                    当前作用域是否启用 Agent。
     * @param agentId                    agent ID。
     * @param customPersonalityEnabled   是否启用自定义性格。
     * @param customPersonality          自定义基础性格。
     * @param customSpeakingStyle        自定义说话风格。
     * @param customBoundaries           自定义硬边界。
     * @param personalityLearningEnabled 是否启用性格学习。
     * @param memoryEnabled              是否启用长期记忆读取。
     * @param modelId                    模型适配器 ID。
     * @param model                      供应商侧具体模型名。
     * @param modelApi                   供应商侧 API 名称或 endpoint。
     * @param baseUrl                    供应商 base URL。
     * @param temperature                采样温度。
     * @param topP                       top-p 参数。
     * @param maxTokens                  输出 token 上限。
     */
    public AgentPersonalityRuntimeConfig {
        agentId = safe(agentId);
        customPersonality = safe(customPersonality);
        customSpeakingStyle = safe(customSpeakingStyle);
        customBoundaries = customBoundaries == null ? List.of() : List.copyOf(customBoundaries);
        modelId = safe(modelId);
        model = safe(model);
        modelApi = safe(modelApi);
        baseUrl = safe(baseUrl);
    }

    /**
     * 创建默认人格运行配置。
     *
     * @return 默认人格运行配置。
     */
    public static AgentPersonalityRuntimeConfig defaults() {
        return new AgentPersonalityConfigData().toRuntimeConfig();
    }

    /**
     * 判断当前配置是否拥有可用的自定义基础性格。
     *
     * @return 如果启用并配置了自定义性格则返回 true。
     */
    public boolean hasCustomPersonality() {
        return customPersonalityEnabled && !customPersonality.isBlank();
    }

    /**
     * 规范化字符串值。
     *
     * @param value 原始值。
     * @return 非空字符串。
     */
    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
