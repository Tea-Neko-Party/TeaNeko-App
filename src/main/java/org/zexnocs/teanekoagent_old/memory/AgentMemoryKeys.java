package org.zexnocs.teanekoagent_old.memory;

/**
 * Agent 记忆存储使用的稳定命名空间、target 与 key 构造工具。
 *
 * @author zExNocs
 * @date 2026/06/09
 * @since 4.4.1
 */
public final class AgentMemoryKeys {
    /**
     * 长期记忆命名空间。
     */
    public static final String NAMESPACE_MEMORY = "memory";

    /**
     * 性格学习修正 key。
     */
    public static final String KEY_PERSONALITY_DELTA = "personality.delta";

    /**
     * 性格学习冲突记录 key。
     */
    public static final String KEY_PERSONALITY_CONFLICT = "personality.conflict";

    /**
     * 最近会话摘要 key。
     */
    public static final String KEY_SUMMARY_RECENT = "summary:recent";

    /**
     * 记忆索引 key。
     */
    public static final String KEY_MEMORY_INDEX = "memory.index";

    /**
     * 工具类禁止实例化。
     */
    private AgentMemoryKeys() {
    }

    /**
     * 构造 scope 级 agent 记忆 target。
     *
     * @param scopeId 作用域 ID。
     * @param agentId agent ID。
     * @return scope 级 target。
     */
    public static String scopeTarget(String scopeId, String agentId) {
        return "scope:%s:agent:%s".formatted(normalize(scopeId, "global"), normalize(agentId, "default"));
    }

    /**
     * 构造 agent 全局记忆 target。
     *
     * @param agentId agent ID。
     * @return agent 全局 target。
     */
    public static String agentTarget(String agentId) {
        return "agent:%s".formatted(normalize(agentId, "default"));
    }

    /**
     * 构造用户级 agent 记忆 target。
     *
     * @param userId  用户 ID。
     * @param agentId agent ID。
     * @return 用户级 target。
     */
    public static String userTarget(String userId, String agentId) {
        return "user:%s:agent:%s".formatted(normalize(userId, "unknown"), normalize(agentId, "default"));
    }

    /**
     * 构造用户画像记忆 key。
     *
     * @param userId 用户 ID。
     * @return 用户画像 key。
     */
    public static String profileKey(String userId) {
        return "profile:%s".formatted(normalize(userId, "unknown"));
    }

    /**
     * 构造关系记忆 key。
     *
     * @param userId 用户 ID。
     * @return 关系记忆 key。
     */
    public static String relationshipKey(String userId) {
        return "relationship:%s".formatted(normalize(userId, "unknown"));
    }

    /**
     * 规范化存储键片段。
     *
     * @param value    原始值。
     * @param fallback 空值时使用的默认值。
     * @return 规范化后的值。
     */
    static String normalize(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }
}
