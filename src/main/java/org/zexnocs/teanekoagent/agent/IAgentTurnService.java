package org.zexnocs.teanekoagent.agent;

import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;

/**
 * 独立轮次执行端口，供会话层组合；准备只登记资源，显式 start 后才开始业务。
 * 默认聊天调用应使用经过邮箱的 IAgentService。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
public interface IAgentTurnService {
    /**
     * 准备独立预算轮次，不读取配置或执行模型/工具。
     * @param handle 目标实例
     * @param request 固定宿主请求
     * @return 必须显式启动、可以在启动前取消的句柄
     * @throws NullPointerException 当实例或请求为空时
     */
    AgentRunExecution prepare(AgentHandle handle, AgentRunRequest request);

    /**
     * 准备共享宿主预算的派生轮次，保持原父控制存活直到完成。
     * @param handle 目标实例
     * @param request 固定请求
     * @param parent 借用的总预算及截止时间控制
     * @return 尚未执行业务的轮次句柄
     * @throws NullPointerException 当实例、请求或父控制为空时
     */
    AgentRunExecution prepare(AgentHandle handle, AgentRunRequest request, ExecutionControl parent);
}
