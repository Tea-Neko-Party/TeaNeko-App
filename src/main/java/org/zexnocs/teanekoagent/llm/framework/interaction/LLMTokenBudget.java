package org.zexnocs.teanekoagent.llm.framework.interaction;

import org.zexnocs.teanekoagent.llm.framework.model.LLMConfigurationException;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptions;
import org.zexnocs.teanekoagent.llm.framework.response.interfaces.ILLMResult;
import org.zexnocs.teanekocore.actuator.execution.BudgetExceededException;
import org.zexnocs.teanekocore.actuator.execution.ResourceBudget;

import java.util.Optional;

import static org.zexnocs.teanekoagent.llm.framework.interaction.LLMCallLimits.TOTAL_TOKENS;

/**
 * 将 LLM 的输入估算与输出上限适配到 Core 通用预留；局部预算不能扩大宿主总额度。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
final class LLMTokenBudget {
    private final ResourceBudget parent;
    private final ResourceBudget local;

    /**
     * 固定两级预算引用，是否启用由宿主显式声明决定。
     * @param parent 同请求共享总预算
     * @param local 单次交互预算
     */
    LLMTokenBudget(ResourceBudget parent, ResourceBudget local) { this.parent = parent; this.local = local; }

    /**
     * 判断是否存在宿主 token 声明。
     * @return 已声明时为 true
     */
    boolean enabled() { return parent.isDeclared(TOTAL_TOKENS); }

    /**
     * 在副作用前拒绝已经耗尽或封锁的 token 额度。
     * @throws BudgetExceededException 当任一级预算不可用时
     */
    void requireAvailable() {
        if (enabled()) {
            if (local.remaining(TOTAL_TOKENS) == 0) { throw exceeded(local, 1); }
            if (parent.remaining(TOTAL_TOKENS) == 0) { throw exceeded(parent, 1); }
        }
    }

    /**
     * 按最新剩余额度收紧输出上限并预留输入加最大输出，必须在当前阶段受控临界区内调用。
     * @param estimate 已在临界区外完成的输入估算
     * @param options 本次私有且已解析的模型参数
     * @return 两级预留，实际模型调用尚未发起
     * @throws BudgetExceededException 当输入与至少一个输出 token 无法容纳时
     * @throws LLMConfigurationException 当模型缺少正输出上限时
     */
    Held reserve(LLMTokenEstimate estimate, LLMModelOptions options) {
        int configured = options.findMaxTokens().orElseThrow(() -> new LLMConfigurationException("启用 token 预算时必须配置模型输出上限。"));
        if (configured < 1) { throw new LLMConfigurationException("模型输出上限必须为正数。"); }
        long available = Math.min(parent.remaining(TOTAL_TOKENS), local.remaining(TOTAL_TOKENS));
        if (estimate.inputTokens() >= available) {
            var budget = local.remaining(TOTAL_TOKENS) <= parent.remaining(TOTAL_TOKENS) ? local : parent;
            throw exceeded(budget, estimate.inputTokens() == Long.MAX_VALUE ? Long.MAX_VALUE : estimate.inputTokens() + 1);
        }
        int output = (int) Math.min(configured, available - estimate.inputTokens());
        long units = estimate.inputTokens() + output;
        var global = parent.reserve(TOTAL_TOKENS, units);
        try {
            var own = local.reserve(TOTAL_TOKENS, units);
            options.setMaxTokens(output);
            return new Held(global, own, units, estimate.method());
        } catch (RuntimeException failure) {
            global.close();
            throw failure;
        }
    }

    /**
     * 成功时按供应商报告结算；失败、缺失或非法 usage 保守保留全部预留并标为估算。
     * @param held 本次有效预留，未启用或未开始时可为空
     * @param result 本次成功响应，失败时为空
     * @return 记账依据；无预留且无可用报告时为空，表示未知
     */
    Optional<LLMTokenUsage> settle(Held held, ILLMResult result) {
        Long reported = null;
        try {
            if (result != null && result.getUsage() != null) { reported = LLMTokenUsage.reportedTokens(result.getUsage()); }
        } catch (RuntimeException ignored) {
            // 非法报告不会释放已经发起请求的预留，响应校验仍会将其判为非法。
        }
        if (held == null) {
            return reported == null ? Optional.empty() : Optional.of(new LLMTokenUsage(reported,
                    LLMTokenUsage.Source.REPORTED, 0, "", false));
        }
        long charged = reported == null ? held.units() : reported;
        var global = held.parent().settle(charged);
        var own = held.local().settle(charged);
        return Optional.of(new LLMTokenUsage(charged, reported == null ? LLMTokenUsage.Source.ESTIMATED
                : LLMTokenUsage.Source.REPORTED, held.units(), held.estimator(), global.exceeded() || own.exceeded()));
    }

    /**
     * 创建结构化超额信号，使用固定维度而非输入或供应商异常文本。
     * @param budget 超额的预算
     * @param requested 本次请求量
     * @return token 预算异常
     */
    BudgetExceededException exceeded(ResourceBudget budget, long requested) {
        var usage = budget.snapshot().get(TOTAL_TOKENS);
        return new BudgetExceededException(TOTAL_TOKENS, usage.limit(), usage.used(), requested);
    }

    /**
     * 一次模型尝试拥有的两级预留，只有业务尚未发起时才可全部退还。
     * @param parent 宿主预留
     * @param local 局部预留
     * @param units 预留总量
     * @param estimator 输入估算算法
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
     * @version 4.5.0-l
     */
    record Held(ResourceBudget.Reservation parent, ResourceBudget.Reservation local,
                long units, String estimator) implements AutoCloseable {
        /** 仅在模型次数尚未取得或业务未开始时，退还两级预留。 */
        @Override public void close() { local.close(); parent.close(); }
    }
}
