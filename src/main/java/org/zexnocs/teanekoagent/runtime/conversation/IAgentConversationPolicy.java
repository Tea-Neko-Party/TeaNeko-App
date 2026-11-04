package org.zexnocs.teanekoagent.runtime.conversation;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.zexnocs.teanekoagent.runtime.AgentHandle;
import org.zexnocs.teanekoagent.runtime.AgentRunRequest;

/**
 * 为实际出队轮次解析历史和重放策略；配置访问在业务执行域、邮箱锁外进行。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.5.0-l
 * @version 4.8.0-l
 */
@FunctionalInterface
public interface IAgentConversationPolicy {
    /**
     * 固定本轮策略，不创建实例或注册作用域。
     * @param handle 目标实例
     * @param request 可信原始输入
     * @return 已校验的非空策略
     * @throws IllegalStateException 当本地或作用域配置不可用时
     */
    AgentConversationOptions resolve(AgentHandle handle, AgentRunRequest request);
}
