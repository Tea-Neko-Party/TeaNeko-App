package org.zexnocs.teanekoagent_old.agent.prompt;

import org.zexnocs.teanekoagent_old.llm.framework.message.interfaces.ILLMMessage;
import org.zexnocs.teanekoagent_old.personality.ResolvedAgentPersonality;

import java.util.List;

/**
 * TeaNeko Agent prompt 构建请求。
 *
 * @param context             Agent 请求上下文。
 * @param personality         已解析的人格状态。
 * @param userMessage         当前用户消息。
 * @param conversationContext 当前会话 LLM 消息上下文。
 * @param extraComponents     额外 prompt 组件。
 * @author zExNocs
 * @date 2026/06/09
 * @since 4.4.1
 */
public record AgentPromptBuildRequest(
        AgentRequestContext context,
        ResolvedAgentPersonality personality,
        String userMessage,
        List<ILLMMessage> conversationContext,
        List<AgentPromptComponent> extraComponents
) {
    /**
     * 创建 TeaNeko Agent prompt 构建请求。
     *
     * @param context             Agent 请求上下文。
     * @param personality         已解析人格。
     * @param userMessage         当前用户消息。
     * @param conversationContext 会话上下文。
     * @param extraComponents     额外组件。
     */
    public AgentPromptBuildRequest {
        userMessage = userMessage == null ? "" : userMessage;
        conversationContext = conversationContext == null ? List.of() : List.copyOf(conversationContext);
        extraComponents = extraComponents == null ? List.of() : List.copyOf(extraComponents);
    }
}
