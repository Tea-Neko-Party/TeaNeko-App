package org.zexnocs.teanekoagent_old.llm.framework.model;

import org.jspecify.annotations.NonNull;

/**
 * 大语言模型适配器的注册 ID。
 * <br>该 ID 来自模型类的 {@code LLMModel.id}，用于在框架中定位具体模型适配器。
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
     * 使用注解声明值构造模型适配器注册 ID。
     *
     * @param id 模型适配器注册 ID
     * @return 模型适配器注册 ID
     */
    public static LLMModelId of(String id) {
        return new LLMModelId(id);
    }

    @Override
    @NonNull
    public String toString() {
        return id;
    }
}
