package org.zexnocs.teanekoplugin.agent.cognition.memory.access;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.zexnocs.teanekoagent.runtime.AgentScope;

/**
 * 读取当前记忆开关，不持有模型、Repository 或缓存的授权状态。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/07：认知业务与宿主生命周期解耦，保留原身份、事务及异步完成边界。
 *
 *
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.5.0-l
 * @version 4.9.0-l
 */
@FunctionalInterface
public interface IMemoryAccessPolicy {
    /**
     * 在借用业务执行域读取最新策略，缺失登记必须返回关闭。
     * @param scope 宿主作用域
     * @return 本次检查的不可变开关
     */
    MemoryAccessPolicy read(AgentScope scope);
}
