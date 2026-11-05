package org.zexnocs.teanekoagent.component.llm.framework.structured;

import org.slf4j.LoggerFactory;
import org.zexnocs.teanekoagent.component.llm.framework.model.LLMModelOptions;
import org.zexnocs.teanekoagent.component.llm.framework.model.interfaces.LLMResponseFormat;

/**
 * 适配器声明的原生 JSON 能力，与本地严格解码分离，不根据路由名猜测能力。
 * <br>2026/11/06：不支持时保留提示并明确警告，不发送供应商不支持的格式字段。
 *
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.3-l
 * @version 4.8.3-l
 */
public enum LLMJsonSupport {
    /** 无原生约束，只能依靠结构提示和本地校验。 */
    NONE,
    /** 原生保证 JSON 对象，不保证字段结构。 */
    OBJECT,
    /** 可发送严格 JSON Schema。 */
    SCHEMA;

    /**
     * 为实际协议准备独立选项；预检不告警，真正请求只在降级时告警。
     * @param original 宿主请求的通用参数
     * @param warn 是否进入实际发送阶段
     * @return 适配器可发送的副本
     */
    public LLMModelOptions adapt(LLMModelOptions original, boolean warn) {
        var options = LLMModelOptions.copyOf(original);
        if (options.getResponseFormat() != LLMResponseFormat.JSON) {
            options.setResponseSchema(null);
            return options;
        }
        if (this != SCHEMA) {
            options.setResponseSchema(null);
        }
        if (this == NONE) {
            options.setResponseFormat(LLMResponseFormat.TEXT);
            if (warn) {
                LoggerFactory.getLogger(LLMJsonSupport.class).warn(
                        "当前模型适配器未声明原生 JSON 输出支持；本次仅使用结构提示及本地校验，不保证模型输出符合 JSON。");
            }
        }
        return options;
    }
}
