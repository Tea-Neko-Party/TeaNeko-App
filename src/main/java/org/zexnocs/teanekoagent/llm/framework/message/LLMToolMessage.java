package org.zexnocs.teanekoagent.llm.framework.message;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMToolMessage;

/**
 * 大语言模型 tool 消息实现。
 * <br>用于将本地工具执行结果返回给模型，并关联对应的 tool call id。
 *
 * @author zExNocs
 * @date 2026/03/21
 * @since 4.4.0
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class LLMToolMessage extends AbstractLLMMessage implements ILLMToolMessage {
    /**
     * tool call id
     */
    @JsonProperty("tool_call_id")
    private String toolCallId;

}
