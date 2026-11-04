package org.zexnocs.teanekoagent.runtime.workflow;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import lombok.NonNull;
import org.zexnocs.teanekoagent.component.llm.framework.message.interfaces.ILLMAssistantMessage;
import org.zexnocs.teanekoagent.runtime.AgentRunResult;
import org.zexnocs.teanekocore.actuator.execution.BudgetKey;
import org.zexnocs.teanekocore.actuator.execution.ResourceBudget;

import java.util.*;

/**
 * 一次显式协作的只读观察，步骤诊断可能含旧答案，只有整体成功才能公开最终答案。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @param workflowId 宿主协作标识
 * @param status 整体结果
 * @param steps 已观察的顺序步骤结果
 * @param budgetUsage 原父预算的累计观察，包含在途预留，不累加各子步骤读数
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.5.0-l
 * @version 4.8.0-l
 */
public record AgentWorkflowResult(UUID workflowId, AgentRunResult.Status status, List<StepResult> steps,
                                  Map<BudgetKey, ResourceBudget.Usage> budgetUsage) {
    /**
     * 冻结已观察诊断，拒绝没有完整成功步骤的成功声明。
     * @param workflowId 原协作标识
     * @param status 整体状态
     * @param steps 顺序诊断
     * @param budgetUsage 父预算观察
     * @throws IllegalArgumentException 当成功声明与步骤不一致时
     */
    public AgentWorkflowResult {
        Objects.requireNonNull(workflowId, "协作标识不能为空。"); Objects.requireNonNull(status, "协作状态不能为空。");
        steps = List.copyOf(steps); budgetUsage = Map.copyOf(budgetUsage);
        if (status == AgentRunResult.Status.SUCCESS && (steps.isEmpty() || steps.stream().anyMatch(step -> step.result().status() != AgentRunResult.Status.SUCCESS))) {
            throw new IllegalArgumentException("协作成功必须具有完整成功步骤。");
        }
    }
    /** @return 仅整体成功时可交付的最后答案 */
    public Optional<ILLMAssistantMessage> answer() {
        return status == AgentRunResult.Status.SUCCESS ? steps.getLast().result().answer() : Optional.empty();
    }
    /** @return 不展开答案和诊断正文的结果摘要 */
    @Override @NonNull
    public String toString() { return "Agent 协作结果[标识=" + workflowId + ", 状态=" + status + ", 步骤=" + steps.size() + "]"; }
    /**
     * 一个已经观察结束的真实步骤，不用模型文字冒充业务完成。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @param name 原步骤名
     * @param result 实际轮次结果
     * @author zExNocs
     * @date 2026/11/01
     * @since 4.5.0-l
     * @version 4.8.0-l
     */
    public record StepResult(String name, AgentRunResult result) {}
}
