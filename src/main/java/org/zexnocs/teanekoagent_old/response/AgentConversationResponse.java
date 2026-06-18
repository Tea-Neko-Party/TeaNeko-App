package org.zexnocs.teanekoagent_old.response;

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent_old.agent.thinking.AgentOutput;

/**
 * Agent 手动对话响应。
 *
 * @param conversationId 会话 ID
 * @param requestMessageId 请求消息 ID
 * @param output Agent 结构化输出；本轮无需回复时为空
 * @author zExNocs
 * @date 2026/06/10
 * @since 4.4.1
 */
public record AgentConversationResponse(
        String conversationId,
        String requestMessageId,
        @Nullable AgentOutput output
) {
}
