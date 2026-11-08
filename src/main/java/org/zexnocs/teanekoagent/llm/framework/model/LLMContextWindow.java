package org.zexnocs.teanekoagent.llm.framework.model;

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.zexnocs.teanekocore.actuator.execution.BudgetExceededException;
import org.zexnocs.teanekocore.actuator.execution.BudgetKey;

import java.math.BigDecimal;

/**
 * 本地声明的模型上下文能力与输入占比，预检输入时为完整输出额度保留空间。
 * 此策略随模型参数快照冻结，不作为供应商请求字段发送。
 *
 * 修改记录：<br>
 * 2026/11/04：默认允许输入使用上下文的百分之八十，超限拒绝且不调整输出参数。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @param tokens 部署模型的上下文总 token 容量
 * @param inputRatio 输入可使用的上下文比例
 * <br>2026/11/04：校正当前实现的版本归属为 v4.7.2-l。
 * <br>2026/11/07：根据原提交职责恢复编码损坏的历史注释。
 * <br>2026/11/07：迁入独立 LLM 子模块，保留公开协议与原配置语义。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.7.2-l
 * @version 4.9.0-l
 */
public record LLMContextWindow(long tokens, double inputRatio) {
    public static final double DEFAULT_INPUT_RATIO = 0.8;
    public static final BudgetKey INPUT_TOKENS = new BudgetKey("llm.context-input-tokens");

    /**
     * 验证显式模型容量和比例，不根据路由或模型名称猜测能力。
     *
     * @param tokens 正上下文容量
     * @param inputRatio 大于零且不超过一的有限比例
     * @throws LLMConfigurationException 当容量或比例无效时
     */
    public LLMContextWindow {
        if (tokens < 1 || !Double.isFinite(inputRatio) || inputRatio <= 0 || inputRatio > 1) {
            throw new LLMConfigurationException("模型 context-window-tokens 必须为正数，context-input-ratio 必须大于零且不超过一。");
        }
    }

    /**
     * 在输入比例及完整输出预留两个约束下计算本次输入上限。
     *
     * @param outputTokens 模型配置的完整输出额度
     * @return 本次可容纳的输入 token 数量
     * @throws LLMConfigurationException 当模型输出额度无效或超过上下文容量时
     */
    public long maxInputTokens(int outputTokens) {
        if (outputTokens < 1 || outputTokens > tokens) {
            throw new LLMConfigurationException("模型 max-tokens 必须为正数且不超过 context-window-tokens。");
        }
        long proportional = BigDecimal.valueOf(tokens).multiply(BigDecimal.valueOf(inputRatio)).longValue();
        return Math.min(proportional, tokens - outputTokens);
    }

    /**
     * 在外部请求前检查完整输入估算，保留完整输出参数及实际预算拒绝原因。
     *
     * @param inputTokens 完整输入的保守估算
     * @param outputTokens 模型配置的完整输出额度
     * @throws BudgetExceededException 当输入无法容纳时
     */
    public void requireFits(long inputTokens, int outputTokens) {
        long limit = maxInputTokens(outputTokens);
        if (inputTokens > limit) {
            throw new BudgetExceededException(INPUT_TOKENS, limit, 0, inputTokens);
        }
    }
}
