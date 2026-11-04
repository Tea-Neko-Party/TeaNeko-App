package org.zexnocs.teanekoagent.component.llm.framework.message;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;
import org.zexnocs.teanekoagent.component.llm.framework.message.interfaces.ILLMContent;
import org.zexnocs.teanekoagent.component.llm.framework.message.interfaces.ILLMContentPart;

/**
 * LLM Content 的实现类
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @author zExNocs
 * @date 2026/03/21
 * @since 4.4.0
 * @version 4.8.0-l
 */
@Getter
@SuperBuilder
@JsonPropertyOrder({"type"})
public class LLMContent implements ILLMContent {
    /**
     * 消息的类型。
     * <br>一般来说是 "message"
     */
    @NonNull
    private final String type;

    /**
     * 消息内容。
     */
    @NonNull
    @JsonUnwrapped
    private final ILLMContentPart contentPart;
}
