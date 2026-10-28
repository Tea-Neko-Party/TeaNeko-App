package org.zexnocs.teanekoagent.agent.access;

import org.zexnocs.teanekoagent.agent.AgentActivity;

import java.util.Objects;

/**
 * 本次读取的作用域开关快照；未登记不使用默认启用值，触发判定仍由独立策略完成。
 * @param registered 是否存在真实配置登记
 * @param enabled 是否启用本作用域 Agent
 * @param mode 主动或被动级别
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
public record AgentScopePolicy(boolean registered, boolean enabled, Mode mode) {
    /**
     * 固定开关，不允许将未登记伪装为已启用。
     * @param registered 是否已登记
     * @param enabled 是否已启用
     * @param mode 活动级别
     * @throws IllegalArgumentException 当未登记却设置为启用时
     */
    public AgentScopePolicy {
        Objects.requireNonNull(mode, "Agent 活动级别不能为空。");
        if (!registered && enabled) { throw new IllegalArgumentException("未登记的作用域不能启用 Agent。"); }
    }

    /**
     * 明确表示配置缺失，不因新建默认对象而悄悄启用。
     * @return 未登记且禁用的策略
     */
    public static AgentScopePolicy unregistered() { return new AgentScopePolicy(false, false, Mode.PASSIVE_BASIC); }

    /**
     * 判断活动开关；普通计划任务不等同自主发言，PROACTIVE 必须显式启用主动模式。
     * @param activity 宿主确定的活动
     * @return 开关与模式允许时为 true
     */
    public boolean allows(AgentActivity activity) {
        Objects.requireNonNull(activity, "Agent 活动不能为空。");
        return registered && enabled && (activity != AgentActivity.PROACTIVE || mode == Mode.ACTIVE);
    }

    /**
     * 触发策略使用的三级模式，门控只决定是否允许，不自动判定消息是否应回复。
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
     * @version 4.5.0-l
     */
    public enum Mode { PASSIVE_BASIC, PASSIVE_CONTEXTUAL, ACTIVE }
}
