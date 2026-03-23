package org.zexnocs.teanekoagent.llm_api_framework.message;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;
import org.zexnocs.teanekoagent.llm_api_framework.message.interfaces.ILLMContent;
import org.zexnocs.teanekoagent.llm_api_framework.message.interfaces.ILLMContentPart;

/**
 * LLM Content 的实现类
 *
 * @author zExNocs
 * @date 2026/03/21
 * @since 4.4.0
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
