package org.zexnocs.teanekoagent.llm.framework.interaction;

import org.zexnocs.teanekoagent.llm.framework.response.interfaces.ILLMUsage;

import java.util.Objects;

/**
 * 一次模型尝试的 token 记账依据；供应商报告与未知时的保守预留估算明确区分。
 * @param tokens 供应商报告或保守估算的总量，不等同精确费用
 * @param source 记账依据
 * @param reservedTokens 请求前预留总量，未启用 token 预算时为零
 * @param estimator 输入估算算法，未使用估算器时为空字符串
 * @param exceeded 结算后局部或父预算是否处于超额封锁状态
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
public record LLMTokenUsage(long tokens, Source source, long reservedTokens, String estimator, boolean exceeded) {
    /**
     * 固定非负记账数据，未知用量通过 Optional 缺失表达，不能在此伪造已知零值。
     * @param tokens 当前尝试的总量
     * @param source 供应商报告或保守估算
     * @param reservedTokens 本次预留量
     * @param estimator 稳定估算算法标识
     * @param exceeded 是否已经超额
     * @throws IllegalArgumentException 当数量为负或标识非法时
     * @throws NullPointerException 当记账来源为空时
     */
    public LLMTokenUsage {
        Objects.requireNonNull(source, "token 记账来源不能为空。");
        if (tokens < 0 || reservedTokens < 0 || estimator == null || !estimator.matches("[a-z0-9.-]{0,64}")) {
            throw new IllegalArgumentException("token 记账数据无效。");
        }
    }

    /**
     * 读取供应商报告，至少采用 total 与输入加输出之和的较大值，避免整数相加溢出。
     * 原始字段仍由交互响应保留，本方法不把推理 token 再次累加到输出 token。
     * @param usage 供应商原始用量
     * @return 非负总量
     * @throws IllegalArgumentException 当供应商报告负数用量时
     */
    static long reportedTokens(ILLMUsage usage) {
        if (usage.getPromptTokens() < 0 || usage.getCompletionTokens() < 0 || usage.getTotalTokens() < 0
                || usage.getPromptCacheHitTokens() < 0 || usage.getPromptCacheMissTokens() < 0 || usage.getReasoningTokens() < 0) {
            throw new IllegalArgumentException("供应商 token 用量不能为负数。");
        }
        return Math.max(usage.getTotalTokens(), (long) usage.getPromptTokens() + usage.getCompletionTokens());
    }

    /**
     * 记账来源，估算只用于保守准入，不声称供应商已产生相同计费。
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
     * @version 4.5.0-l
     */
    public enum Source { REPORTED, ESTIMATED }
}
