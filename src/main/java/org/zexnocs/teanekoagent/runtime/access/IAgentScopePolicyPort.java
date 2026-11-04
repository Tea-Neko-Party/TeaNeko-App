package org.zexnocs.teanekoagent.runtime.access;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.zexnocs.teanekoagent.runtime.AgentScope;

/**
 * 读取真实 scope 登记与开关的边界，不负责默认注册或修改配置。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.5.0-l
 * @version 4.8.0-l
 */
@FunctionalInterface
public interface IAgentScopePolicyPort {
    /**
     * 读取当前策略；缺失必须返回 unregistered，后端故障不得当作启用。
     * @param scope 宿主定位的作用域
     * @return 不可变策略
     */
    AgentScopePolicy read(AgentScope scope);
}
