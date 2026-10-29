package org.zexnocs.teanekoagent.memory.fact.legacy;

import java.util.Objects;

/**
 * 调用方明确指定的旧 memory/profile 行；旧标识保持不透明，不猜测 UUID。
 * @param scopeId 旧 scope
 * @param agentId 旧 Agent
 * @param subjectKey 旧 profile 键中的主体
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
public record LegacyMemoryLocation(String scopeId, String agentId, String subjectKey) {
    /**
     * 要求实际存储键，拒绝隐式修剪、默认替换和超出旧表列长度的定位。
     * @param scopeId 旧 scope
     * @param agentId 旧 Agent
     * @param subjectKey 旧主体键
     * @throws NullPointerException 当定位为空时
     * @throws IllegalArgumentException 当定位不规范或过长时
     */
    public LegacyMemoryLocation {
        Objects.requireNonNull(scopeId, "旧 scope 不能为空。"); Objects.requireNonNull(agentId, "旧 Agent 不能为空。");
        Objects.requireNonNull(subjectKey, "旧主体键不能为空。");
        for (var field : new String[]{scopeId, agentId, subjectKey}) {
            if (field.isBlank() || !field.equals(field.trim())) { throw new IllegalArgumentException("旧定位必须是明确且无首尾空白的存储键。"); }
        }
        if (("scope:" + scopeId + ":agent:" + agentId).length() > 255 || ("profile:" + subjectKey).length() > 255) {
            throw new IllegalArgumentException("旧定位超出存储列长度。");
        }
    }
    /**
     * 复现旧物理目标，不将它作为新共享空间的过滤条件。
     * @return 旧目标
     */
    public String target() { return "scope:" + scopeId + ":agent:" + agentId; }
    /**
     * 复现旧画像列表键。
     * @return 旧列表键
     */
    public String key() { return "profile:" + subjectKey; }
}
