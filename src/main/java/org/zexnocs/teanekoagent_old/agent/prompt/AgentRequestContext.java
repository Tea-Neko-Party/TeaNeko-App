package org.zexnocs.teanekoagent_old.agent.prompt;

/**
 * 单次 Agent 请求的运行上下文。
 *
 * @param scopeId        当前请求所属作用域 ID。
 * @param agentId        当前请求指定的 agent ID，空值表示使用默认 agent。
 * @param userId         当前请求相关用户 ID。
 * @param conversationId 当前会话 ID。
 * @author zExNocs
 * @date 2026/06/09
 * @since 4.4.1
 */
public record AgentRequestContext(
        String scopeId,
        String agentId,
        String userId,
        String conversationId
) {
    /**
     * 创建 Agent 请求上下文。
     *
     * @param scopeId        作用域 ID。
     * @param agentId        agent ID。
     * @param userId         用户 ID。
     * @param conversationId 会话 ID。
     */
    public AgentRequestContext {
        scopeId = safe(scopeId);
        agentId = safe(agentId);
        userId = safe(userId);
        conversationId = safe(conversationId);
    }

    /**
     * 创建不指定会话 ID 的请求上下文。
     *
     * @param scopeId 作用域 ID。
     * @param agentId agent ID。
     * @param userId  用户 ID。
     * @return Agent 请求上下文。
     */
    public static AgentRequestContext of(String scopeId, String agentId, String userId) {
        return new AgentRequestContext(scopeId, agentId, userId, "");
    }

    /**
     * 复制当前上下文并替换 agent ID。
     *
     * @param agentId 新 agent ID。
     * @return 替换 agent ID 后的新上下文。
     */
    public AgentRequestContext withAgentId(String agentId) {
        return new AgentRequestContext(scopeId, agentId, userId, conversationId);
    }

    /**
     * 规范化字符串值。
     *
     * @param value 原始值。
     * @return 非空字符串。
     */
    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
