package org.zexnocs.teanekoagent_old.personality;

import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptions;
import org.zexnocs.teanekoagent_old.file_config.personality.AgentPersonalityRuntimeConfig;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Agent 配置到 LLM 通用模型参数的转换服务。
 * <br>Agent 直接复用 LLM 层已有的模型参数模型，避免在 Agent 层重复定义模型配置结构。
 *
 * @author zExNocs
 * @date 2026/06/09
 * @since 4.4.1
 */
@Service
public class AgentLLMModelOptionsResolver {
    /**
     * 将 Agent 人格运行配置转换为 LLM 模型参数覆盖。
     *
     * @param config Agent 人格运行配置。
     * @return LLM 模型参数覆盖。
     */
    public LLMModelOptions resolve(AgentPersonalityRuntimeConfig config) {
        var metadata = new LinkedHashMap<String, Object>();
        putIfNotBlank(metadata, "api", config.modelApi());
        putIfNotBlank(metadata, "baseUrl", config.baseUrl());

        return LLMModelOptions.builder()
                .provider(blankToNull(config.modelId()))
                .model(blankToNull(config.model()))
                .temperature(config.temperature())
                .topP(config.topP())
                .maxTokens(config.maxTokens())
                .metadata(Map.copyOf(metadata))
                .build();
    }

    /**
     * 如果字符串不为空则写入 metadata。
     *
     * @param metadata metadata 映射。
     * @param key      metadata key。
     * @param value    待写入值。
     */
    private static void putIfNotBlank(Map<String, Object> metadata, String key, String value) {
        if (value != null && !value.isBlank()) {
            metadata.put(key, value.trim());
        }
    }

    /**
     * 将空白字符串转换为 {@code null}。
     *
     * @param value 原始值。
     * @return 非空字符串或 {@code null}。
     */
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
