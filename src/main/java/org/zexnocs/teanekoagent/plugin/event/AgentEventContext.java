package org.zexnocs.teanekoagent.plugin.event;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.zexnocs.teanekoagent.runtime.AgentScope;
import org.zexnocs.teanekocore.framework.composition.EntityId;

import java.util.Set;
import java.util.UUID;

/**
 * 决策事件的只读定位信息，不携带凭据、实体写权限或根控制。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：更新分层重构后的类型引用，保持原调用与生命周期行为。
 *
 * @param requestId 本轮请求 ID
 * @param agentId 实际实例 ID
 * @param definitionId 定义 ID
 * @param scope 真实客户端与作用域
 * @param features 本轮安装的能力 ID
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.6.0-l
 * @version 4.8.0-l
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
