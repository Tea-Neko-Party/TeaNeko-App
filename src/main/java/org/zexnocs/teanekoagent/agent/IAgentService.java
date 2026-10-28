package org.zexnocs.teanekoagent.agent;

import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;

import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * 默认 Agent 会话入口，同 client/scope/实例进入同一顺序邮箱，群内不按用户拆分。
 * 不发送平台消息、不自动写历史，不将请求身份解释为永久授权。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
public interface IAgentService {
    /**
     * 排队执行独立请求，出队后根据实际就绪设置建立父总预算和截止时间。
     * COLLECT 和 TOOL 属于独立能力入口，不能在本入口触发模型轮次。
     * @param handle 由工厂创建且仍登记的实例
     * @param request 已规范化的宿主请求
     * @return 包含完整交互完成或明确拒绝/终止的只读阶段
     * @throws NullPointerException 当实例或请求为空时
     */
    CompletionStage<AgentRunResult> run(AgentHandle handle, AgentRunRequest request);

    /**
     * 执行同一宿主请求的派生轮次，共享已有总预算、取消和截止时间。
     * 关闭本轮不会取消宿主或其他派生轮次；宿主须预先声明所需预算维度。
     * @param handle 目标实例
     * @param request 已固定请求
     * @param parent 借用的宿主执行控制
     * @return 真正完成的只读阶段，不重置宿主额度
     * @throws NullPointerException 当实例、请求或父控制为空时
     */
    CompletionStage<AgentRunResult> run(AgentHandle handle, AgentRunRequest request, ExecutionControl parent);

    /**
     * 请求取消本人发起的运行或排队请求；管理者取消整个 scope 需独立的业务授权入口。
     * @param handle 目标实例
     * @param scope 原请求的客户端及作用域
     * @param requestId 原始请求标识
     * @param requester 宿主确认的 TeaNeko UUID，不能来自模型参数
     * @return 请求取消、无此在途请求或非本人
     * @throws NullPointerException 当实例、作用域、请求标识或发起者为空时
     */
    CancelResult cancel(AgentHandle handle, AgentScope scope, UUID requestId, UUID requester);

    /**
     * 取消请求的接收结果，不表示已经回滚外部动作。
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
     * @version 4.5.0-l
     */
    enum CancelResult { REQUESTED, NOT_FOUND, NOT_OWNER }
}
