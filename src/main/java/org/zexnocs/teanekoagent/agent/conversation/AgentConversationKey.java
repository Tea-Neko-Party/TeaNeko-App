package org.zexnocs.teanekoagent.agent.conversation;

import org.zexnocs.teanekoagent.agent.AgentScope;
import org.zexnocs.teanekocore.framework.composition.EntityId;
import java.util.Objects;

/**
 * 默认共享会话键，不加入发起用户，避免把同群拆成多个并行邮箱。
 * @param agentId Agent 实例标识，不以共享定义 ID 代替
 * @param scope 原始 client/scope 边界
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
public record AgentConversationKey(EntityId agentId, AgentScope scope) {
    /**
     * 固定会话身份，不解析或改写宿主作用域。
     * @param agentId 实例标识
     * @param scope 客户端及作用域
     * @throws NullPointerException 当实例标识或作用域为空时
     */
    public AgentConversationKey {
        Objects.requireNonNull(agentId, "会话实例标识不能为空。");
        Objects.requireNonNull(scope, "会话作用域不能为空。");
    }
}
