package org.zexnocs.teanekoagent.agent;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 宿主固定的发起者与出站目标数据；不携带凭据、平台客户端或可修改权限的对象。
 * @param scope 本次来源作用域
 * @param origin 人类请求或系统任务
 * @param userId TeaNeko 用户 UUID，系统任务为空
 * @param platformUserId 平台用户来源，未知时为空字符串，不能代替 TeaNeko UUID
 * @param sourceId 消息或任务来源标识
 * @param targetId 宿主固定的目标标识
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
public record AgentActorContext(AgentScope scope, Origin origin, Optional<UUID> userId,
                                String platformUserId, String sourceId, String targetId) {
    /**
     * 校验身份来源一致性，系统任务不得继承聊天中某个人的身份。
     * @param scope 来源作用域
     * @param origin 请求来源类型
     * @param userId 人类发起者 UUID 或空值容器
     * @param platformUserId 平台用户来源或空字符串
     * @param sourceId 消息或任务标识
     * @param targetId 固定目标
     * @throws IllegalArgumentException 当用户身份缺失或系统任务携带用户身份时
     * @throws NullPointerException 当必需字段为空时
     */
    public AgentActorContext {
        Objects.requireNonNull(scope, "作用域不能为空。");
        Objects.requireNonNull(origin, "请求来源类型不能为空。");
        Objects.requireNonNull(userId, "发起者容器不能为空。");
        Objects.requireNonNull(platformUserId, "平台用户来源不能为空。");
        AgentScope.requireIdentity(sourceId); AgentScope.requireIdentity(targetId);
        if (origin == Origin.USER && userId.isEmpty()
                || origin == Origin.SYSTEM && (userId.isPresent() || !platformUserId.isEmpty())) {
            throw new IllegalArgumentException("Agent 请求来源与发起者身份不一致。");
        }
    }

    /**
     * 区分用户请求与系统任务，不为系统任务自动授予用户权限。
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
     * @version 4.5.0-l
     */
    public enum Origin { USER, SYSTEM }
}
