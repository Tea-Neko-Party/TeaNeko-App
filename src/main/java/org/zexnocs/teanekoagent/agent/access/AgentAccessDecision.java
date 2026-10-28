package org.zexnocs.teanekoagent.agent.access;

import java.util.Objects;
import java.util.Optional;

/**
 * 一次门控检查的数据结果，不是可以永久复用或提升权限的令牌。
 * @param reason 允许或拒绝原因
 * @param scope 已读取的作用域策略，尚未读取时为空
 * @param readiness 已完成的就绪检查，前置拒绝时为空
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
public record AgentAccessDecision(Reason reason, Optional<AgentScopePolicy> scope, Optional<AgentReadiness> readiness) {
    /**
     * 允许结果必须具有已启用 scope 及完整就绪快照，不能用默认空对象伪装成功。
     * @param reason 检查原因
     * @param scope 可选 scope 数据
     * @param readiness 可选就绪结果
     * @throws IllegalArgumentException 当允许结果缺少完整开关或就绪数据时
     */
    public AgentAccessDecision {
        Objects.requireNonNull(reason, "门控原因不能为空。");
        Objects.requireNonNull(scope, "作用域策略容器不能为空。");
        Objects.requireNonNull(readiness, "就绪结果容器不能为空。");
        if (reason == Reason.ALLOWED && (scope.isEmpty() || !scope.get().registered() || !scope.get().enabled()
                || readiness.isEmpty() || readiness.get().status() != AgentReadiness.Status.READY)) {
            throw new IllegalArgumentException("允许 Agent 执行必须具备完整作用域和就绪数据。");
        }
    }

    /**
     * 仅表示当前检查通过，执行、提交或副作用前仍须复核。
     * @return 当前检查允许时为 true
     */
    public boolean allowed() { return reason == Reason.ALLOWED; }

    /**
     * 取得已验证的运行数据，拒绝结果不提供可继续执行的快照。
     * @return 仅允许时存在的运行快照
     */
    public Optional<AgentRuntimeSnapshot> runtime() {
        return allowed() ? readiness.flatMap(AgentReadiness::snapshot) : Optional.empty();
    }

    /**
     * 统一门控的稳定原因，不含模型输出、配置值或异常链。
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
     * @version 4.5.0-l
     */
    public enum Reason {
        ALLOWED("Agent 活动已获准。"), INSTANCE_CLOSED("Agent 实例已关闭。"),
        INSTANCE_NOT_REGISTERED("Agent 实例未登记。"), ACTIVITY_DISABLED("当前定义未启用此活动。"),
        INVALID_ORIGIN("请求来源不适用于当前活动。"), SCOPE_UNREGISTERED("当前作用域未注册 Agent。"),
        SCOPE_DISABLED("当前作用域已关闭 Agent。"), MODE_RESTRICTED("当前作用域未允许主动活动。"),
        SCOPE_CONFIG_UNAVAILABLE("Agent 作用域配置不可用。"), LOCAL_DISABLED("本地配置已关闭 Agent。"),
        SCOPE_POLICY_CHANGED("检查期间作用域策略已变化，请在新活动中重新检查。"),
        READINESS_UNAVAILABLE("Agent 运行配置尚未就绪。");

        private final String description;

        /**
         * 固定可向管理入口显示的说明。
         * @param description 固定中文说明
         */
        Reason(String description) { this.description = description; }

        /**
         * 取得不包含敏感值的检查原因。
         * @return 固定中文说明
         */
        public String description() { return description; }
    }
}
