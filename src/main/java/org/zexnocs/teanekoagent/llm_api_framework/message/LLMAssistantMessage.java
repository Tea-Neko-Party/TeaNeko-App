package org.zexnocs.teanekoagent.llm_api_framework.message;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.zexnocs.teanekoagent.llm_api_framework.message.interfaces.ILLMAssistantMessage;
import org.zexnocs.teanekoagent.llm_api_framework.tool.interfaces.ILLMToolCall;

import java.util.List;

/**
 * 大语言模型 assistant 消息实现。
 * <br>除了普通消息内容外，还可以携带模型请求执行的工具调用列表。
 *
 * @author zExNocs
 * @date 2026/03/21
 * @since 4.4.0
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class LLMAssistantMessage extends AbstractLLMMessage implements ILLMAssistantMessage {
    /// tool call list
    @JsonProperty("tool_calls")
    private List<ILLMToolCall> toolCalls = List.of();
}
