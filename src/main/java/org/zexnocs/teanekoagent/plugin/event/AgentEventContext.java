package org.zexnocs.teanekoagent.plugin.event;

import org.zexnocs.teanekoagent.agent.AgentScope;
import org.zexnocs.teanekocore.framework.composition.EntityId;

import java.util.Set;
import java.util.UUID;

/**
 * 决策事件的只读定位信息，不携带凭据、实体写权限或根控制。
 * @param requestId 本轮请求 ID
 * @param agentId 实际实例 ID
 * @param definitionId 定义 ID
 * @param scope 真实客户端与作用域
 * @param features 本轮安装的能力 ID
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.0.0
 * @version 4.6.0-l
 */
public record AgentEventContext(UUID requestId, EntityId agentId, String definitionId, AgentScope scope, Set<String> features) {
    /**
     * 固定事件可见范围。
     * @param requestId 请求
     * @param agentId 实例
     * @param definitionId 定义
     * @param scope 作用域
     * @param features 安装能力
     */
    public AgentEventContext { features = Set.copyOf(features); }
}
