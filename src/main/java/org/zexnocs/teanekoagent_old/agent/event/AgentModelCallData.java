package org.zexnocs.teanekoagent_old.agent.event;

import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.llm.framework.input.LLMPrompt;
import org.zexnocs.teanekoagent.llm.framework.response.interfaces.ILLMResult;
import org.zexnocs.teanekoagent_old.agent.AgentConversationContext;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;

import java.util.Optional;

/**
 * Agent 单次模型调用事件数据。
 * <br>该对象用于在 {@link AgentModelCallEvent} 中传递本次 Prompt、模型响应和对应的运行时上下文。
 *
 * @author zExNocs
 * @date 2026/06/10
 * @since 4.4.1
 */
@Getter
@Setter
public class AgentModelCallData {
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
     * 即将发送给 LLM framework 的 Prompt。
     */
    private LLMPrompt prompt;

    /**
     * 模型调用结果。
     */
    @Nullable
    private ILLMResult result;

    /**
     * 创建 Agent 单次模型调用事件数据。
     *
     * @param context        当前 Agent 会话上下文。
     * @param inboundMessage 当前轮次对应的入站消息。
     * @param round          当前 tool call loop 中的模型调用轮次。
     * @param prompt         即将发送给 LLM framework 的 Prompt。
     */
    public AgentModelCallData(AgentConversationContext context,
                              ITeaNekoMessageData inboundMessage,
                              int round,
                              LLMPrompt prompt) {
        this.context = context;
        this.inboundMessage = inboundMessage;
        this.round = round;
        this.prompt = prompt;
    }

    /**
     * 查找模型调用结果。
     *
     * @return 模型调用结果；模型调用被取消且未提供替代结果时为空。
     */
    public Optional<ILLMResult> findResult() {
        return Optional.ofNullable(result);
    }
}
