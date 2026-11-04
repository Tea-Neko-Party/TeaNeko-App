package org.zexnocs.teanekoagent.runtime;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 宿主固定的发起者与出站目标数据；不携带凭据、平台客户端或可修改权限的对象。
 * <br>2026/11/03：区分可信管理主体与平台用户，管理身份不能作为聊天来源。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @param scope 本次来源作用域
 * @param origin 原生用户、系统任务或独立管理来源
 * @param userId 用户 UUID 或独立管理主体 UUID，系统任务为空
 * @param platformUserId 平台用户来源，未知时为空字符串，不能代替 TeaNeko UUID
 * @param sourceId 消息或任务来源标识
 * @param targetId 宿主固定的目标标识
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.5.0-l
 * @version 4.8.0-l
 */
public record AgentActorContext(AgentScope scope, Origin origin, Optional<UUID> userId,
                                String platformUserId, String sourceId, String targetId) {
    /**
     * 校验身份来源一致性，系统任务不得继承聊天中某个人的身份。
     * @param scope 来源作用域
     * @param origin 请求来源类型
     * @param userId 用户或管理主体 UUID，系统任务为空
     * @param platformUserId 平台用户来源或空字符串
     * @param sourceId 消息或任务标识
     * @param targetId 固定目标
     * @throws IllegalArgumentException 当主体身份缺失、系统继承身份或管理来源携带平台号时
     * @throws NullPointerException 当必需字段为空时
     */
    public AgentActorContext {
        Objects.requireNonNull(scope, "作用域不能为空。");
        Objects.requireNonNull(origin, "请求来源类型不能为空。");
        Objects.requireNonNull(userId, "发起者容器不能为空。");
        Objects.requireNonNull(platformUserId, "平台用户来源不能为空。");
        AgentScope.requireIdentity(sourceId); AgentScope.requireIdentity(targetId);
        // 2026/11/03：管理主体需要明确 UUID，但禁止携带平台用户号。
        if ((origin == Origin.USER || origin == Origin.MANAGEMENT) && userId.isEmpty()
                || origin == Origin.MANAGEMENT && !platformUserId.isEmpty()
                || origin == Origin.SYSTEM && (userId.isPresent() || !platformUserId.isEmpty())) {
            throw new IllegalArgumentException("Agent 请求来源与发起者身份不一致。");
        }
    }

    /**
     * 区分用户请求、系统任务和独立管理主体，不自动授予聊天执行权。
     * <br>2026/11/03：新增可信管理来源。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.5.0-l
     * @version 4.8.0-l
     */
    public enum Origin { USER, SYSTEM, MANAGEMENT }
}
