package org.zexnocs.teanekoagent_old.agent;

import org.zexnocs.teanekoagent_old.llm.framework.message.interfaces.ILLMMessage;

import java.time.Instant;

/**
 * Agent 会话中的带时间消息记录。
 *
 * @param sequence   消息在会话中的顺序编号。
 * @param message    LLM 消息。
 * @param occurredAt 消息在平台或模型侧实际发生的时间。
 * @param recordedAt 消息写入 Agent 上下文的时间。
 * @author zExNocs
 * @date 2026/06/10
 * @since 4.4.1
 */
public record AgentConversationMessage(
        long sequence,
        ILLMMessage message,
        Instant occurredAt,
        Instant recordedAt
) {
    /**
     * 创建带时间消息记录。
     *
     * @param sequence   消息顺序编号。
     * @param message    LLM 消息。
     * @param occurredAt 消息发生时间；为空时使用记录时间。
     * @param recordedAt 消息记录时间；为空时使用当前时间。
     */
    public AgentConversationMessage {
        recordedAt = recordedAt == null ? Instant.now() : recordedAt;
        occurredAt = occurredAt == null ? recordedAt : occurredAt;
    }
}
