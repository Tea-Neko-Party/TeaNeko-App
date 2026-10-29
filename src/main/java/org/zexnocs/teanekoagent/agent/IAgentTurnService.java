package org.zexnocs.teanekoagent.agent;

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.agent.access.AgentAccessDecision;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;

import java.util.concurrent.CompletionStage;

/**
 * 独立轮次执行端口，供会话层组合；准备只登记资源，显式 start 后才开始业务。
 * 默认聊天调用应使用经过邮箱的 IAgentService。
 *
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
public interface IAgentTurnService {
    /**
     * 准备邮箱已固定引用的轮次；实现必须复核原引用并保留逐模型、工具和交付守卫。
     * 旧端口默认拒绝，不能通过普通 prepare 静默忽略空间保护。
     * @param handle 目标实例
     * @param request 已绑定会话及空间的请求
     * @param parent 可选借用父控制
     * @return 显式启动的受控句柄
     * @throws UnsupportedOperationException 当实现没有持久化引用保护时
     */
    default AgentRunExecution prepareReferenced(AgentHandle handle, AgentRunRequest request, @Nullable ExecutionControl parent) {
        throw new UnsupportedOperationException("独立轮次端口尚未支持固定记忆引用。");
    }
    /**
     * 为完成结果重放复核当前准入，不创建轮次或消耗模型预算；旧端口未适配时明确拒绝。
     * @param handle 目标实例
     * @param request 原始可信请求
     * @return 当前准入决定
     * @throws UnsupportedOperationException 当端口未支持只读准入复核时
     */
    default CompletionStage<AgentAccessDecision> checkAccess(AgentHandle handle, AgentRunRequest request) {
        throw new UnsupportedOperationException("独立轮次端口尚未支持结果重放准入复核。");
    }

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
