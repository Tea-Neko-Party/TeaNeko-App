package org.zexnocs.teanekoagent.llm.framework.interaction;

import org.zexnocs.teanekocore.actuator.execution.BudgetKey;
import org.zexnocs.teanekocore.actuator.execution.ResourceBudget;

import java.util.HashMap;

/**
 * 单次交互的请求与工具限额；同请求的多个交互执行还必须继承宿主总预算。
 *
 * @param maxModelCalls 最多预占的模型请求次数，至少为一
 * @param maxToolRounds 最多工具批次数，零表示禁用工具
 * @param maxToolCalls 最多工具执行次数，零表示禁用工具
 * @param maxTotalTokens 可选总 token 额度；null 表示不设局部 token 限额，零表示不允许模型请求
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
public record LLMCallLimits(int maxModelCalls, int maxToolRounds, int maxToolCalls, Long maxTotalTokens) {
    public static final BudgetKey MODEL_REQUESTS = new BudgetKey("llm.model-requests");
    public static final BudgetKey TOOL_ROUNDS = new BudgetKey("llm.tool-rounds");
    public static final BudgetKey TOOL_EXECUTIONS = new BudgetKey("llm.tool-executions");
    public static final BudgetKey TOTAL_TOKENS = new BudgetKey("llm.total-tokens");

    /**
     * 校验次数并限制工具批次数，至少给工具回填后的模型收束保留一次本地请求。
     *
     * @param maxModelCalls 模型请求上限
     * @param maxToolRounds 工具批次上限
     * @param maxToolCalls 工具执行上限
     * @param maxTotalTokens 可选非负 token 上限
     * @throws IllegalArgumentException 当模型次数非正或工具/token 上限为负时
     */
    public LLMCallLimits {
        if (maxModelCalls < 1 || maxToolRounds < 0 || maxToolCalls < 0 || maxTotalTokens != null && maxTotalTokens < 0) {
            throw new IllegalArgumentException("模型请求次数必须为正，工具次数和 token 上限不能为负。");
        }
        maxToolRounds = Math.min(maxToolRounds, maxModelCalls - 1);
    }

    /**
     * 保留仅设置次数的入口；若宿主已声明 token 预算，执行时仍继承宿主限制。
     * @param maxModelCalls 模型请求上限
     * @param maxToolRounds 工具批次上限
     * @param maxToolCalls 工具执行上限
     */
    public LLMCallLimits(int maxModelCalls, int maxToolRounds, int maxToolCalls) {
        this(maxModelCalls, maxToolRounds, maxToolCalls, null);
    }

    /**
     * 提供计划约定的初始限额，部署或单次服务调用可以显式替换。
     *
     * @return 三次模型请求、两批工具和六次工具执行的限额
     */
    public static LLMCallLimits defaults() {
        return new LLMCallLimits(3, 2, 6);
    }

    /**
     * 为独立请求建立预算；同请求的子步骤必须复用已有父控制，不能调用此方法重置总额度。
     *
     * @return 声明三个次数维度及可选 token 维度的独立预算
     */
    public ResourceBudget newBudget() {
        var limits = new HashMap<BudgetKey, Long>();
        limits.put(MODEL_REQUESTS, (long) maxModelCalls);
        limits.put(TOOL_ROUNDS, (long) maxToolRounds);
        limits.put(TOOL_EXECUTIONS, (long) maxToolCalls);
        if (maxTotalTokens != null) { limits.put(TOTAL_TOKENS, maxTotalTokens); }
        return new ResourceBudget(limits);
    }
}
