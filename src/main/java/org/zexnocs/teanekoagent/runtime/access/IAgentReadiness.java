package org.zexnocs.teanekoagent.runtime.access;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.zexnocs.teanekoagent.runtime.AgentDefinition;

/**
 * Agent 就绪检查边界，可供管理入口使用，不发起模型请求或注册 scope。
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
public interface IAgentReadiness {
    /**
     * 读取当前配置并检查模型与凭据；调用方应在异步业务执行器中调用。
     * @param definition 需要检查的只读定义
     * @return 可公开的就绪结果，不导出凭据或原始异常
     */
    AgentReadiness inspect(AgentDefinition definition);
}
