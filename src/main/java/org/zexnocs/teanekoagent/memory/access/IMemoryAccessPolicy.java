package org.zexnocs.teanekoagent.memory.access;

import org.zexnocs.teanekoagent.agent.AgentScope;

/**
 * 读取当前记忆开关，不持有模型、Repository 或缓存的授权状态。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
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
