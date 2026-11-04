package org.zexnocs.teanekoagent.runtime.access;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.zexnocs.teanekoagent.component.AgentAssembly;
import org.zexnocs.teanekoagent.runtime.AgentActivity;

import java.util.Objects;

/**
 * 本次读取的作用域开关快照；未登记不使用默认启用值，触发判定仍由独立策略完成。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：将聊天模型路由纳入准入快照及策略变化比较。
 *
 * <br>2026/11/04：装配摘要参与准入比较，支持聊天私有模型路由。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @param registered 是否存在真实配置登记
 * @param enabled 是否启用本作用域 Agent
 * @param mode 主动或被动级别
 * @param modelRoute 当前聊天选择的配置路由，空值继承文件默认
 * @param assembly 参与准入版本比较的无秘密装配快照
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.5.0-l
 * @version 4.8.0-l
 */
public record AgentScopePolicy(boolean registered, boolean enabled, Mode mode, String modelRoute,
                               AgentAssembly assembly) {
    /**
     * 2026/11/04：保留独立旧宿主的显式路由构造，沿用原固定能力。
     * @param registered 是否登记
     * @param enabled 是否启用
     * @param mode 活动模式
     * @param modelRoute 已配置路由
     */
    public AgentScopePolicy(boolean registered, boolean enabled, Mode mode, String modelRoute) {
        this(registered, enabled, mode, modelRoute, AgentAssembly.legacyDefaults());
    }

    /**
     * 2026/11/04：保留旧宿主构造，默认继承文件模型。
     * @param registered 是否登记
     * @param enabled 是否开启
     * @param mode 活动模式
     */
    public AgentScopePolicy(boolean registered, boolean enabled, Mode mode) {
        this(registered, enabled, mode, "");
    }

    /**
     * 固定开关，不允许将未登记伪装为已启用。
     * @param registered 是否已登记
     * @param enabled 是否已启用
     * @param mode 活动级别
     * @param modelRoute 空值或合法配置路由
     * @param assembly 当前配置固定的组件装配
     * @throws IllegalArgumentException 当未登记却设置为启用时
     */
    public AgentScopePolicy {
        // 2026/11/04：装配摘要参与策略复核，组件参数或密钥变更使旧轮次失效。
        Objects.requireNonNull(assembly, "Agent 装配不能为空。");
        Objects.requireNonNull(mode, "Agent 活动级别不能为空。");
        // 2026/11/04：路由纳入策略快照及变更复核，切换后旧轮次不得继续使用原选择。
        Objects.requireNonNull(modelRoute, "模型路由不能为空。");
        if (!modelRoute.isEmpty() && !modelRoute.matches("[a-z][a-z0-9-]*")
                && !modelRoute.matches("agent:[0-9a-f]{2,2048}:[0-9a-f]{64}")) {
            throw new IllegalArgumentException("聊天模型路由格式无效。");
        }
        if (!registered && enabled) {
            throw new IllegalArgumentException("未登记的作用域不能启用 Agent。");
        }
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
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.5.0-l
     * @version 4.8.0-l
     */
    public enum Mode { PASSIVE_BASIC, PASSIVE_CONTEXTUAL, ACTIVE }
}
