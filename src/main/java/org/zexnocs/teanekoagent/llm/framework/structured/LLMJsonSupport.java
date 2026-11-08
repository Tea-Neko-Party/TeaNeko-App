package org.zexnocs.teanekoagent.llm.framework.structured;

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

import org.slf4j.LoggerFactory;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMMessage;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptions;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.LLMResponseFormat;

import java.util.List;

/**
 * 适配器声明的原生 JSON 能力，与本地严格解码分离，不根据路由名猜测能力。
 * <br>2026/11/06：不支持时保留提示并明确警告，不发送供应商不支持的格式字段。
 * <br>2026/11/06：按原生能力去除重复结构提示，保持输入预检、观察与实际发送一致。
 * <br>2026/11/07：迁入独立 LLM 子模块，保留公开协议与原配置语义。
 *
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.3-l
 * @version 4.9.0-l
 */
public enum LLMJsonSupport {
    /** 无原生约束，只能依靠结构提示和本地校验。 */
    NONE,
    /** 原生保证 JSON 对象，不保证字段结构。 */
    OBJECT,
    /** 可发送严格 JSON Schema。 */
    SCHEMA;

    /**
     * 2026/11/06：仅在协议确实携带结构时压缩宿主指令，业务 system 消息原样保留。
     * @param source 原消息
     * @param options 尚未丢失 JSON 意图的选项
     * @return 适配后的消息，不修改来源
     */
    public List<ILLMMessage> messages(List<ILLMMessage> source, LLMModelOptions options) {
        boolean nativeSchema = this == SCHEMA && options.getResponseFormat() == LLMResponseFormat.JSON
                && options.findResponseSchema().isPresent();
        return source.stream().map(message -> message instanceof LLMJsonInstruction instruction
                ? new LLMJsonInstruction(instruction.guidance(), instruction.schema(), nativeSchema)
                : message).toList();
    }

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
