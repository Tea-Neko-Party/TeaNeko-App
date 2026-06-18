package org.zexnocs.teanekoagent_old.agent.event;

import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent_old.agent.AgentConversationContext;
import org.zexnocs.teanekoagent_old.llm.framework.tool.interfaces.ILLMToolCall;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;

import java.util.Optional;

/**
 * Agent 单次工具调用事件数据。
 * <br>该对象用于在 {@link AgentToolCallEvent} 中传递模型请求的工具调用、执行结果和可能的执行异常。
 *
 * @author zExNocs
 * @date 2026/06/10
 * @since 4.4.1
 */
@Getter
@Setter
public class AgentToolCallData {
    /**
     * 当前 Agent 会话上下文。
     */
    private final AgentConversationContext context;

    /**
     * 当前轮次对应的入站消息。
     */
    private final ITeaNekoMessageData inboundMessage;

    /**
     * 当前 tool call loop 中的模型调用轮次。
     */
    private final int round;

    /**
     * 模型请求执行的工具调用。
     */
    private ILLMToolCall toolCall;

    /**
     * 工具执行结果，通常会作为 tool message 回填给模型。
     */
    @Nullable
    private String result;

    /**
     * 工具执行过程中捕获的异常。
     */
    @Nullable
    private Exception exception;

    /**
     * 创建 Agent 单次工具调用事件数据。
     *
     * @param context        当前 Agent 会话上下文。
     * @param inboundMessage 当前轮次对应的入站消息。
     * @param round          当前 tool call loop 中的模型调用轮次。
     * @param toolCall       模型请求执行的工具调用。
     */
    public AgentToolCallData(AgentConversationContext context,
                             ITeaNekoMessageData inboundMessage,
                             int round,
                             ILLMToolCall toolCall) {
        this.context = context;
        this.inboundMessage = inboundMessage;
        this.round = round;
        this.toolCall = toolCall;
    }

    /**
     * 查找工具执行结果。
     *
     * @return 工具执行结果；工具调用被取消且未提供替代结果时为空。
     */
    public Optional<String> findResult() {
        return Optional.ofNullable(result);
    }
}
