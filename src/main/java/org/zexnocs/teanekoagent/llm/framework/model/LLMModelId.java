package org.zexnocs.teanekoagent.llm.framework.model;

import org.jspecify.annotations.NonNull;

/**
 * 大语言模型适配器的注册 ID。
 * <br>该 ID 用于在框架中定位具体供应商适配器，通常与供应商 ID 相同，例如 {@code openai}、{@code deepseek}。
 * <br>具体调用的模型名称由 {@link LLMModelOptions#getModel()} 提供默认值或调用时覆盖。
 *
 * @author zExNocs
 * @date 2026/05/15
 * @since 4.4.0
 */
public record LLMModelId(String id) {
    /**
     * 创建模型适配器注册 ID。
     *
     * @param id 模型适配器注册 ID
     */
    public LLMModelId {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("model id must not be blank");
        }
        id = id.trim();
    }

    /**
     * 使用供应商级 ID 快速构造模型适配器注册 ID。
     *
     * @param id 模型适配器注册 ID
     * @return 模型适配器注册 ID
     */
    public static LLMModelId of(String id) {
        return new LLMModelId(id);
    }

    /**
     * 获取供应商级 ID。
     * <br>该方法用于兼容早期以 {@code provider} 命名的调用点。
     *
     * @return 供应商级 ID
     */
    public String provider() {
        return id;
    }

    @Override
    @NonNull
    public String toString() {
        return id;
    }
}
