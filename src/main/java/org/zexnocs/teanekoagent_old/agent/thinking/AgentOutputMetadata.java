package org.zexnocs.teanekoagent_old.agent.thinking;

import java.time.Instant;

/**
 * Agent 结构化输出的运行时元数据。
 *
 * @param conversationId     会话 ID
 * @param agentId            实际使用的 Agent ID
 * @param personalitySource  人格来源
 * @param provider           模型适配器或供应商 ID
 * @param model              实际响应模型名称
 * @param finishReason       最后一次模型调用的结束原因
 * @param startedAt          本轮开始时间
 * @param completedAt        本轮完成时间
 * @param modelCalls         模型调用次数
 * @param toolCalls          工具调用次数
 * @param promptTokens       本轮累计 prompt token
 * @param completionTokens   本轮累计 completion token
 * @param totalTokens        本轮累计 token
 * @param reasoningTokens    供应商报告的累计 reasoning token
 * @param contextMessages    完成后保留的上下文消息数
 * @param injectedMemories   注入 Prompt 的长期记忆数
 * @param thinkingEnabled    是否启用 Agent 结构化思考流程
 * @param stepLimitReached   是否因思考步骤预算而强制进入最终回答
 * @param confidence         模型对最终回答给出的置信度
 * @author zExNocs
 * @date 2026/06/10
 * @since 4.4.1
 */
public record AgentOutputMetadata(
        String conversationId,
        String agentId,
        String personalitySource,
        String provider,
        String model,
        String finishReason,
        Instant startedAt,
        Instant completedAt,
        int modelCalls,
        int toolCalls,
        int promptTokens,
        int completionTokens,
        int totalTokens,
        int reasoningTokens,
        int contextMessages,
        int injectedMemories,
        boolean thinkingEnabled,
        boolean stepLimitReached,
        double confidence
) {
    /**
     * 创建规范化输出元数据。
     */
    public AgentOutputMetadata {
        conversationId = safe(conversationId);
        agentId = safe(agentId);
        personalitySource = safe(personalitySource);
        provider = safe(provider);
        model = safe(model);
        finishReason = safe(finishReason);
        startedAt = startedAt == null ? Instant.now() : startedAt;
        completedAt = completedAt == null ? Instant.now() : completedAt;
        modelCalls = Math.max(0, modelCalls);
        toolCalls = Math.max(0, toolCalls);
        promptTokens = Math.max(0, promptTokens);
        completionTokens = Math.max(0, completionTokens);
        totalTokens = Math.max(0, totalTokens);
        reasoningTokens = Math.max(0, reasoningTokens);
        contextMessages = Math.max(0, contextMessages);
        injectedMemories = Math.max(0, injectedMemories);
        confidence = Math.clamp(confidence, 0.0, 1.0);
    }

    /**
     * 创建不包含模型调用信息的默认元数据。
     *
     * @return 默认元数据
     */
    public static AgentOutputMetadata empty() {
        var now = Instant.now();
        return new AgentOutputMetadata(
                "", "", "", "", "", "", now, now,
                0, 0, 0, 0, 0, 0, 0, 0, false, false, 0.0
        );
    }

    /**
     * 规范化字符串。
     *
     * @param value 原始字符串
     * @return 非空字符串
     */
    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
