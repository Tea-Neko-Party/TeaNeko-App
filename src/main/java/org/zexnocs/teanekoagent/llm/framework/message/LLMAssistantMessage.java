package org.zexnocs.teanekoagent.llm.framework.message;

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMAssistantMessage;
import org.zexnocs.teanekoagent.llm.framework.tool.interfaces.ILLMToolCall;

import java.util.List;

/**
 * 大语言模型 assistant 消息实现。
 * <br>除了普通消息内容外，还可以携带模型请求执行的工具调用列表。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/07：迁入独立 LLM 子模块，保留公开协议与原配置语义。
 *
 * @author zExNocs
 * @date 2026/03/21
 * @since 4.4.0
 * @version 4.9.0-l
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class LLMAssistantMessage extends AbstractLLMMessage implements ILLMAssistantMessage {
    /// tool call list
    @Builder.Default
    @JsonProperty("tool_calls")
    private List<ILLMToolCall> toolCalls = List.of();
}
