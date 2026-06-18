package org.zexnocs.teanekoagent_old.personality.config;

import org.zexnocs.teanekocore.database.configdata.api.IConfigKey;

/**
 * Agent 配置使用的组合配置键。
 *
 * @param scopeId 作用域 ID。
 * @param agentId agent ID；空值时只使用 scope ID。
 * @author zExNocs
 * @date 2026/06/09
 * @since 4.4.1
 */
public record AgentConfigKey(String scopeId, String agentId) implements IConfigKey {
    /**
     * 获取配置键。
     * <br>当 agent ID 不为空时，格式为 {@code {scopeId}:agent:{agentId}}；否则直接使用 scope ID。
     *
     * @return 配置键字符串。
     */
    @Override
    public String getKey() {
        var scope = normalize(scopeId, "global");
        if (agentId == null || agentId.isBlank()) {
            return scope;
        }
        return "%s:agent:%s".formatted(scope, agentId.trim());
    }

    /**
     * 规范化配置键片段。
     *
     * @param value    原始值。
     * @param fallback 空值时的默认值。
     * @return 规范化结果。
     */
    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
