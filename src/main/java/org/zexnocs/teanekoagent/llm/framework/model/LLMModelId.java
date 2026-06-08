package org.zexnocs.teanekoagent.llm.framework.model;

import org.jspecify.annotations.NonNull;

/**
 * 大语言模型适配器的注册 ID。
 * <br>该 ID 用于在框架中定位具体供应商适配器，通常与供应商 ID 相同，例如 {@code openai}、{@code deepseek}。
 * <br>具体调用的模型名称不再属于 ID，而是由 {@link LLMModelOptions#getModel()} 提供默认值或调用时覆盖。
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
        if (id.contains("/")) {
            throw new IllegalArgumentException("model id must be provider-level id, not provider/model: " + id);
        }
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
     * 使用供应商 ID 快速构造模型适配器注册 ID。
     * <br>该重载保留给旧调用代码迁移使用；{@code model} 不再参与路由，应写入 {@link LLMModelOptions}。
     *
     * @param provider 模型供应商 ID
     * @param model 模型名称
     * @return 模型适配器注册 ID
     * @deprecated 模型名称不再属于 {@link LLMModelId}，请使用 {@link #of(String)}。
     */
    @Deprecated
    public static LLMModelId of(String provider, String model) {
        return of(provider);
    }

    /**
     * 解析供应商级 ID 字符串。
     *
     * @param value 供应商级 ID 字符串，例如 {@code openai}、{@code deepseek}
     * @return 模型适配器注册 ID
     */
    public static LLMModelId parse(String value) {
        return of(value);
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
