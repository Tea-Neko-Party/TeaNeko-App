package org.zexnocs.teanekoagent.runtime.access;

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.runtime.AgentActivity;
import org.zexnocs.teanekoagent.runtime.AgentActorContext;
import org.zexnocs.teanekoagent.runtime.AgentHandle;
import org.zexnocs.teanekoagent.runtime.IAgentRegistry;
import org.zexnocs.teanekocore.logger.ILogger;

import java.util.Objects;
import java.util.Optional;

import static org.zexnocs.teanekoagent.runtime.access.AgentAccessDecision.Reason.ACTIVITY_DISABLED;
import static org.zexnocs.teanekoagent.runtime.access.AgentAccessDecision.Reason.ACTIVITY_POLICY_REJECTED;
import static org.zexnocs.teanekoagent.runtime.access.AgentAccessDecision.Reason.ALLOWED;
import static org.zexnocs.teanekoagent.runtime.access.AgentAccessDecision.Reason.INSTANCE_CLOSED;
import static org.zexnocs.teanekoagent.runtime.access.AgentAccessDecision.Reason.INSTANCE_NOT_REGISTERED;
import static org.zexnocs.teanekoagent.runtime.access.AgentAccessDecision.Reason.INVALID_ORIGIN;
import static org.zexnocs.teanekoagent.runtime.access.AgentAccessDecision.Reason.LOCAL_DISABLED;
import static org.zexnocs.teanekoagent.runtime.access.AgentAccessDecision.Reason.MODE_RESTRICTED;
import static org.zexnocs.teanekoagent.runtime.access.AgentAccessDecision.Reason.READINESS_UNAVAILABLE;
import static org.zexnocs.teanekoagent.runtime.access.AgentAccessDecision.Reason.SCOPE_CONFIG_UNAVAILABLE;
import static org.zexnocs.teanekoagent.runtime.access.AgentAccessDecision.Reason.SCOPE_DISABLED;
import static org.zexnocs.teanekoagent.runtime.access.AgentAccessDecision.Reason.SCOPE_POLICY_CHANGED;
import static org.zexnocs.teanekoagent.runtime.access.AgentAccessDecision.Reason.SCOPE_UNREGISTERED;

/**
 * 新 Agent 所有活动的统一准入检查，先验证实例和 scope，再读取运行配置或凭据。
 * 管理注册、注销与诊断入口不通过本门控执行，从而允许未注册作用域开通功能。
 * <br>2026/11/03：明确拒绝管理主体直接进入聊天或模型活动。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：读取聊天模型覆盖并保持共享定义不变，准入期间切换仍拒绝旧快照。
 *
 * <br>2026/11/04：模型就绪检查使用当前聊天的固定装配定义。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * <br>2026/11/06：聊天路由覆盖仅替换身份，不重置定义中的 JSON 输出合同。
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.5.0-l
 * @version 4.8.3-l
 */
public final class AgentAccessGate {
    private final IAgentRegistry registry;
    private final IAgentScopePolicyPort scopes;
    private final IAgentReadiness readiness;
    private final ILogger logger;
    private final @Nullable IAgentActivityPolicy activities;

    /**
     * 注入只读检查端口，不在构造时检查 scope 或加载配置。
     * @param registry 实例拥有者
     * @param scopes 作用域登记端口
     * @param readiness 模型与配置检查端口
     * @param logger 固定原因日志
     */
    public AgentAccessGate(IAgentRegistry registry, IAgentScopePolicyPort scopes, IAgentReadiness readiness, ILogger logger) {
        this(registry, scopes, readiness, logger, null);
    }

    /**
     * 组合可选宿主活动策略，旧独立宿主保留原构造语义。
     * @param registry 实例登记
     * @param scopes 作用域开关
     * @param readiness 就绪检查
     * @param logger 诊断日志
     * @param activities 可选动态活动准入
     */
    public AgentAccessGate(IAgentRegistry registry, IAgentScopePolicyPort scopes, IAgentReadiness readiness, ILogger logger,
            @Nullable IAgentActivityPolicy activities) {
        this.registry = Objects.requireNonNull(registry, "实例注册表不能为空。");
        this.scopes = Objects.requireNonNull(scopes, "作用域端口不能为空。");
        this.readiness = Objects.requireNonNull(readiness, "就绪检查端口不能为空。");
        this.logger = Objects.requireNonNull(logger, "日志端口不能为空。");
        this.activities = activities;
    }

    /**
     * 对全部活动使用相同登记边界；结果只对当前检查成立，不能代替工具原业务权限链。
     * 作用域读取可能访问缓存或数据库，调用方须在业务执行器中调用，不能放入 Core 生命周期锁。
     * @param handle 宿主指定实例
     * @param actor 宿主固定的来源与目标
     * @param activity 宿主确定的活动类别
     * @return 明确准入结果，不执行模型、工具或平台操作
     */
    public AgentAccessDecision evaluate(AgentHandle handle, AgentActorContext actor, AgentActivity activity) {
        Objects.requireNonNull(handle, "Agent 实例不能为空。");
        Objects.requireNonNull(actor, "请求身份不能为空。");
        Objects.requireNonNull(activity, "Agent 活动不能为空。");
        // 2026/11/03：管理授权不等于聊天或系统活动授权。
        if (actor.origin() == AgentActorContext.Origin.MANAGEMENT) {
            return denied(INVALID_ORIGIN);
        }
        if (handle.isClosed()) { return denied(INSTANCE_CLOSED); }
        if (registry.find(handle.id()).filter(registered -> registered == handle).isEmpty()) { return denied(INSTANCE_NOT_REGISTERED); }
        if (!handle.definition().activities().contains(activity)) { return denied(ACTIVITY_DISABLED); }
        try { if (activities != null && !activities.allows(actor, activity)) { return denied(ACTIVITY_POLICY_REJECTED); } }
        catch (RuntimeException failure) { return denied(ACTIVITY_POLICY_REJECTED); }
        if ((activity == AgentActivity.PROACTIVE || activity == AgentActivity.SCHEDULED)
                && actor.origin() != AgentActorContext.Origin.SYSTEM
                || activity == AgentActivity.COMMAND && actor.origin() != AgentActorContext.Origin.USER) {
            return denied(INVALID_ORIGIN);
        }
        final AgentScopePolicy policy;
        try { policy = Objects.requireNonNull(scopes.read(actor.scope())); }
        catch (RuntimeException failure) { report(SCOPE_CONFIG_UNAVAILABLE); return denied(SCOPE_CONFIG_UNAVAILABLE); }
        if (!policy.registered()) { return scoped(SCOPE_UNREGISTERED, policy); }
        if (!policy.enabled()) { return scoped(SCOPE_DISABLED, policy); }
        if (!policy.allows(activity)) { return scoped(MODE_RESTRICTED, policy); }
        final AgentReadiness current;
        try {
            // 2026/11/04：聊天路由优先于文件默认，定义中的其他模型选项和能力保持不变。
            var definition = handle.definition();
            if (!policy.modelRoute().isEmpty()) {
                // 2026/11/06：只覆盖路由，避免默认 TEXT 清除原 JSON 合同。
                var options = definition.modelOptions().toOptions();
                options.setProvider(policy.modelRoute());
                definition = new org.zexnocs.teanekoagent.runtime.AgentDefinition(definition.id(),
                        new org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptionsSnapshot(options),
                        definition.activities(), definition.features());
            }
            current = Objects.requireNonNull(readiness.inspect(definition));
        }
        catch (RuntimeException failure) { report(READINESS_UNAVAILABLE); return scoped(READINESS_UNAVAILABLE, policy); }
        // 检查过程中实例可能被关闭，不能把已失效的结果交给下一步骤。
        if (handle.isClosed() || registry.find(handle.id()).filter(registered -> registered == handle).isEmpty()) {
            return scoped(INSTANCE_CLOSED, policy);
        }
        if (current.status() == AgentReadiness.Status.READY) {
            final AgentScopePolicy latest;
            try { latest = Objects.requireNonNull(scopes.read(actor.scope())); }
            catch (RuntimeException failure) { report(SCOPE_CONFIG_UNAVAILABLE); return scoped(SCOPE_CONFIG_UNAVAILABLE, policy); }
            if (!latest.equals(policy)) {
                var reason = !latest.registered() ? SCOPE_UNREGISTERED : !latest.enabled() ? SCOPE_DISABLED
                        : !latest.allows(activity) ? MODE_RESTRICTED : SCOPE_POLICY_CHANGED;
                return scoped(reason, latest);
            }
        }
        var reason = current.status() == AgentReadiness.Status.READY ? ALLOWED
                : current.status() == AgentReadiness.Status.LOCAL_DISABLED ? LOCAL_DISABLED : READINESS_UNAVAILABLE;
        return new AgentAccessDecision(reason, Optional.of(policy), Optional.of(current));
    }

    /**
     * 返回前置拒绝，不伪造作用域或运行快照。
     * @param reason 拒绝原因
     * @return 无运行数据的结果
     */
    private static AgentAccessDecision denied(AgentAccessDecision.Reason reason) {
        return new AgentAccessDecision(reason, Optional.empty(), Optional.empty());
    }

    /**
     * 保留已读取的作用域数据，但不提供可继续执行的运行快照。
     * @param reason 拒绝原因
     * @param policy 已读取策略
     * @return 作用域阶段的拒绝结果
     */
    private static AgentAccessDecision scoped(AgentAccessDecision.Reason reason, AgentScopePolicy policy) {
        return new AgentAccessDecision(reason, Optional.of(policy), Optional.empty());
    }

    /**
     * 只记录固定原因，诊断系统异常也不能使准入失败被跳过。
     * @param reason 固定失败原因
     */
    private void report(AgentAccessDecision.Reason reason) {
        try { logger.error(AgentAccessGate.class.getName(), reason.description()); }
        catch (RuntimeException ignored) { /* 门控失败不能依赖日志成功。 */ }
    }
}
