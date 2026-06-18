package org.zexnocs.teanekoagent_old.memory;

/**
 * EasyData 中一个记忆桶的定位信息。
 *
 * @param namespace EasyData 命名空间。
 * @param target    EasyData target。
 * @param key       EasyData key。
 * @author zExNocs
 * @date 2026/06/09
 * @since 4.4.1
 */
public record MemoryTargetKey(String namespace, String target, String key) {
    /**
     * 构造性格修正记忆定位信息。
     *
     * @param scopeId 作用域 ID。
     * @param agentId agent ID。
     * @return 性格修正记忆定位信息。
     */
    public static MemoryTargetKey personalityDelta(String scopeId, String agentId) {
        return new MemoryTargetKey(
                AgentMemoryKeys.NAMESPACE_MEMORY,
                AgentMemoryKeys.scopeTarget(scopeId, agentId),
                AgentMemoryKeys.KEY_PERSONALITY_DELTA);
    }

    /**
     * 构造用户画像记忆定位信息。
     *
     * @param scopeId 作用域 ID。
     * @param agentId agent ID。
     * @param userId  用户 ID。
     * @return 用户画像记忆定位信息。
     */
    public static MemoryTargetKey userProfile(String scopeId, String agentId, String userId) {
        return new MemoryTargetKey(
                AgentMemoryKeys.NAMESPACE_MEMORY,
                AgentMemoryKeys.scopeTarget(scopeId, agentId),
                AgentMemoryKeys.profileKey(userId));
    }

    /**
     * 构造关系记忆定位信息。
     *
     * @param scopeId 作用域 ID。
     * @param agentId agent ID。
     * @param userId  用户 ID。
     * @return 关系记忆定位信息。
     */
    public static MemoryTargetKey relationship(String scopeId, String agentId, String userId) {
        return new MemoryTargetKey(
                AgentMemoryKeys.NAMESPACE_MEMORY,
                AgentMemoryKeys.scopeTarget(scopeId, agentId),
                AgentMemoryKeys.relationshipKey(userId));
    }
}
