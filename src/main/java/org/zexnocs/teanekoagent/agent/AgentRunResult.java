package org.zexnocs.teanekoagent.agent;

import org.zexnocs.teanekoagent.agent.access.AgentAccessDecision;
import org.zexnocs.teanekoagent.llm.framework.interaction.LLMInteractionResult;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMAssistantMessage;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMMessage;
import org.zexnocs.teanekocore.actuator.execution.BudgetKey;
import org.zexnocs.teanekocore.actuator.execution.ResourceBudget;
import org.zexnocs.teanekocore.framework.composition.EntityId;

import java.util.*;

/**
 * 一轮已结束的只读结果。被拒绝或终止时仍保留已观察交互，不自动提交历史或发送平台消息。
 * @param agentId 实际执行的实例标识
 * @param requestId 宿主请求标识
 * @param status 最终业务状态
 * @param access 最近一次允许检查或首次拒绝的非敏感原因
 * @param interaction 已观察完成的完整交互诊断；尚未进入或端口未响应时为空
 * @param budgetUsage 交付时宿主共享总预算读数，派生轮次不把它冒充自身增量
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
public record AgentRunResult(EntityId agentId, UUID requestId, Status status,
                             Optional<AgentAccessDecision> access, Optional<LLMInteractionResult> interaction,
                             Map<BudgetKey, ResourceBudget.Usage> budgetUsage) {
    /**
     * 固定结果容器，成功必须同时具有完整交互及允许的门控结果。
     * @param agentId Agent 实例
     * @param requestId 宿主请求标识
     * @param status 最终状态
     * @param access 门控诊断
     * @param interaction 可选交互快照
     * @param budgetUsage 宿主预算观察值
     * @throws IllegalArgumentException 当成功结果缺少成功交互或准入时
     */
    public AgentRunResult {
        Objects.requireNonNull(agentId, "Agent 实例标识不能为空。");
        Objects.requireNonNull(requestId, "Agent 请求标识不能为空。");
        Objects.requireNonNull(status, "Agent 结果状态不能为空。");
        Objects.requireNonNull(access, "门控结果容器不能为空。");
        Objects.requireNonNull(interaction, "交互结果容器不能为空。");
        budgetUsage = Map.copyOf(budgetUsage);
        if (status == Status.SUCCESS && (access.filter(AgentAccessDecision::allowed).isEmpty()
                || interaction.filter(value -> value.status() == LLMInteractionResult.Status.SUCCESS).isEmpty())) {
            throw new IllegalArgumentException("Agent 成功结果必须包含有效准入和完整成功交互。");
        }
    }

    /**
     * 只对最终成功公开回答候选，门控已失效的模型正文只保留在诊断交互中。
     * @return 当前允许交付的最终消息
     */
    public Optional<ILLMAssistantMessage> answer() {
        return status == Status.SUCCESS ? interaction.flatMap(LLMInteractionResult::finalCandidate) : Optional.empty();
    }

    /**
     * 提供可提交的完整历史；实际提交仍须由会话层校验代次和当前门控。
     * @return 只有本轮成功时存在的完整消息链
     */
    public Optional<List<ILLMMessage>> committableTranscript() {
        return status == Status.SUCCESS ? interaction.flatMap(LLMInteractionResult::committableTranscript) : Optional.empty();
    }

    /**
     * 稳定业务状态，配置拒绝和执行取消与供应商终止分开表达。
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
     * @version 4.5.0-l
     */
    public enum Status { SUCCESS, SKIPPED, CONFIGURATION_ERROR, CANCELLED, TIMED_OUT, BUDGET_EXCEEDED,
        EMPTY_OUTPUT, TRUNCATED, REFUSED, INVALID_INPUT, INVALID_RESPONSE, FAILED,
        BUSY, QUEUE_TIMED_OUT, REENTRANT, DUPLICATE_REQUEST, STALE_CONVERSATION }
}
