package org.zexnocs.teanekoagent.llm_api_framework.model;

import org.jspecify.annotations.NonNull;

/**
 * 大语言模型的唯一标识。
 * <br>由 provider 和 model 两部分组成，用于在框架中定位具体模型实现。
 *
 * @author zExNocs
 * @date 2026/05/15
 * @since 4.4.0
 */
public record LLMModelId(String provider, String model) {
    /**
     * 构造器
     *
     * @param provider 模型供应商
     * @param model 模型
     */
    public LLMModelId {
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("provider must not be blank");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("model must not be blank");
        }
    }

    /**
     * 快速构造
     *
     * @param provider 模型供应商
     * @param model 模型
     * @return {@link LLMModelId }
     */
    public static LLMModelId of(String provider, String model) {
        return new LLMModelId(provider, model);
    }

    /**
     * 解析 "provider/model" 格式的字符串
     *
     * @param value "provider/model" 格式的字符串
     * @return {@link LLMModelId }
     */
    public static LLMModelId parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("model id must not be blank");
        }
        int splitIndex = value.indexOf('/');
        if (splitIndex < 1 || splitIndex == value.length() - 1) {
            throw new IllegalArgumentException("model id must use provider/model format: " + value);
        }
        return of(value.substring(0, splitIndex), value.substring(splitIndex + 1));
    }

    @Override
    @NonNull
    public String toString() {
        return provider + "/" + model;
    }
}
