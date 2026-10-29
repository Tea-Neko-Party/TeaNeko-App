package org.zexnocs.teanekoagent.memory.fact;

import org.zexnocs.teanekoagent.agent.AgentActorContext;

import java.util.Objects;
import java.util.UUID;

/**
 * 宿主固定的记忆来源，保留请求、动作、真实发起者及 client/scope/消息信息。
 * @param requestId 原始 Agent 或管理请求
 * @param actionId 同请求内稳定的工具调用或操作标识
 * @param actor 可信发起上下文，不由模型反序列化生成
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
public record MemoryFactSource(UUID requestId, String actionId, AgentActorContext actor) {
    /**
     * 固定来源定位，不把来源身份自动变为共享空间的查询过滤条件。
     * @param requestId 原始请求
     * @param actionId 稳定动作
     * @param actor 真实发起者
     * @throws NullPointerException 当必需来源为空时
     * @throws IllegalArgumentException 当动作标识为空白或过长时
     */
    public MemoryFactSource {
        Objects.requireNonNull(requestId, "记忆来源请求不能为空。"); Objects.requireNonNull(actor, "记忆来源身份不能为空。");
        Objects.requireNonNull(actionId, "记忆来源动作不能为空。");
        if (actionId.isBlank() || actionId.length() > 128) { throw new IllegalArgumentException("记忆来源动作标识无效。"); }
    }
}
