package org.zexnocs.teanekoagent.component.llm.framework.interaction;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.component.llm.framework.message.LLMProtocolSnapshots;
import org.zexnocs.teanekoagent.component.llm.framework.message.interfaces.ILLMAssistantMessage;
import org.zexnocs.teanekoagent.component.llm.framework.message.interfaces.ILLMMessage;
import org.zexnocs.teanekoagent.component.llm.framework.response.interfaces.ILLMResult;
import org.zexnocs.teanekoagent.component.llm.framework.response.interfaces.ILLMUsage;
import org.zexnocs.teanekoagent.component.llm.framework.tool.ToolInvocationResult;
import org.zexnocs.teanekocore.actuator.execution.BudgetKey;
import org.zexnocs.teanekocore.actuator.execution.ResourceBudget;
import org.zexnocs.teanekocore.logger.FailureDiagnostic;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 可安全交付的交互快照，不持有执行控制、执行器或凭据。
 * transcript 包含诊断所需的中间链；只有成功结果允许通过专用入口提交为正常历史。
 *
 * <br>2026/11/02：保留结构化工具图像，供实际视觉消息与历史投影交叉验证。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/05：交互结果保留可公开的失败诊断，兼容已有构造。
 * <br>2026/11/06：模型尝试附带不进入 JSON 的临时输入观察，保留已有构造入口。
 *
 * @param status 明确终止原因
 * @param transcript 初始消息及已观察的模型/工具/视觉参考消息，失败时可能是不完整诊断链
 * @param finalCandidate 终止时的模型消息，工具请求不会伪装成最终答案
 * @param attempts 每次实际模型尝试的响应和 usage，失败尝试可无响应
 * @param tools 已观察完成的工具结果，不推断未完成动作是否产生副作用
 * @param budgetUsage 本次交互实际消耗的局部额度，不是供应商计费统计
 * @param rejectedBudget 导致交互终止的预算维度，非预算终止时为空
 *
 * @param failure 可选的安全失败诊断
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.5.0-l
 * @version 4.8.5-l
 */
public record LLMInteractionResult(Status status, List<ILLMMessage> transcript,
                                    Optional<ILLMAssistantMessage> finalCandidate, List<ModelAttempt> attempts,
                                    List<ToolInvocationResult> tools, Map<BudgetKey, ResourceBudget.Usage> budgetUsage,
                                    Optional<BudgetKey> rejectedBudget, @Nullable FailureDiagnostic failure) {
    /**
     * 2026/11/05：保留原构造签名，未提供诊断时不虚构失败原因。
     * @param status 终止状态
     * @param transcript 诊断消息链
     * @param finalCandidate 最终候选
     * @param attempts 模型尝试
     * @param tools 工具结果
     * @param budgetUsage 预算快照
     * @param rejectedBudget 被拒预算维度
     */
    public LLMInteractionResult(Status status, List<ILLMMessage> transcript,
            Optional<ILLMAssistantMessage> finalCandidate, List<ModelAttempt> attempts,
            List<ToolInvocationResult> tools, Map<BudgetKey, ResourceBudget.Usage> budgetUsage,
            Optional<BudgetKey> rejectedBudget) {
        this(status, transcript, finalCandidate, attempts, tools, budgetUsage, rejectedBudget, null);
    }

    /**
     * 2026/11/05：复制结果并携带脱敏原因，异常对象不进入快照。
     * @param diagnostic 可选安全诊断
     * @return 包含同一结果与新诊断的快照
     */
    public LLMInteractionResult withFailure(@Nullable FailureDiagnostic diagnostic) {
        return new LLMInteractionResult(status, transcript, finalCandidate, attempts, tools, budgetUsage,
                rejectedBudget, diagnostic);
    }
    /**
     * 冻结所有对外数据，防止调用方或供应商在交付后修改回放内容。
     *
     * @param status 终止原因
     * @param transcript 诊断消息链
     * @param finalCandidate 可选的终止候选
     * @param attempts 实际模型尝试记录
     * @param tools 工具完成记录
     * @param budgetUsage 局部预算用量
     * @param rejectedBudget 可选的被拒预算维度
     * @param failure 可选安全诊断
     * @throws NullPointerException 当必需的状态、列表或可选容器为空时
     * @throws IllegalArgumentException 当消息或透明状态无法安全冻结时
     */
    public LLMInteractionResult {
        Objects.requireNonNull(status, "交互状态不能为空。");
        transcript = LLMProtocolSnapshots.messages(transcript);
        finalCandidate = finalCandidate.map(message -> (ILLMAssistantMessage) LLMProtocolSnapshots.message(message));
        attempts = List.copyOf(attempts);
        tools = tools.stream().map(result -> new ToolInvocationResult(LLMProtocolSnapshots.call(result.call()),
                // 2026/11/02：冻结结果时保留显式图片，不使诊断与实际视觉消息失去对应关系。
                result.status(), result.output(), result.truncated(), result.images())).toList();
        budgetUsage = Map.copyOf(budgetUsage);
        Objects.requireNonNull(rejectedBudget, "预算终止维度容器不能为空。");
    }

    /**
     * 保留未提供具体预算终止维度的构造入口。
     * @param status 终止状态
     * @param transcript 诊断链
     * @param finalCandidate 终止候选，null 表示缺失
     * @param attempts 模型尝试
     * @param tools 已观察工具结果
     * @param budgetUsage 局部预算快照
     */
    public LLMInteractionResult(Status status, List<ILLMMessage> transcript, @Nullable ILLMAssistantMessage finalCandidate,
                                List<ModelAttempt> attempts, List<ToolInvocationResult> tools,
                                Map<BudgetKey, ResourceBudget.Usage> budgetUsage) {
        this(status, transcript, Optional.ofNullable(finalCandidate), attempts, tools, budgetUsage, Optional.empty());
    }

    /**
     * 只允许完整成功的链进入正常历史；失败链可供诊断但不得作为正常对话继续回放。
     *
     * @return 成功时为完整不可变消息列表，其余状态为空
     */
    public Optional<List<ILLMMessage>> committableTranscript() {
        return status == Status.SUCCESS ? Optional.of(transcript) : Optional.empty();
    }

    /**
     * 读取已预占的模型尝试次数，传输失败仍计入；不按成功响应个数估算。
     *
     * @return 本次交互实际模型尝试次数
     */
    public long modelAttempts() {
        var used = budgetUsage.get(LLMCallLimits.MODEL_REQUESTS);
        return used == null ? 0 : used.used();
    }

    /**
     * 可区分的终止状态；工具阶段可恢复的错误另见 tools，不强行使最终答案失败。
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
    public enum Status { SUCCESS, EMPTY_OUTPUT, TRUNCATED, REFUSED, BUDGET_EXCEEDED, CANCELLED,
        TIMED_OUT, INVALID_INPUT, INVALID_RESPONSE, CONFIGURATION_ERROR, FAILED }

    /**
     * 单次实际尝试的不可变响应；缺失响应和缺失 usage 不伪造 token 数。
     *
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * <br>2026/11/06：临时保留实际输入供请求汇总，禁止通过结果 JSON 导出。
     * @param number 本次交互中从一开始的尝试序号
     * @param response 供应商响应，传输失败或无法安全冻结时为空
     * @param tokenUsage 记账依据，既无报告又未启用预留时为空，表示未知
     * @param input 实际调用边界的临时输入；旧构造和未提交尝试为空
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.5.0-l
     * @version 4.8.5-l
     */
    public record ModelAttempt(int number, Optional<ILLMResult> response, Optional<LLMTokenUsage> tokenUsage,
                               @com.fasterxml.jackson.annotation.JsonIgnore Optional<LLMRequestSnapshot> input) {
        /**
         * 固定一次响应，拒绝不合法的尝试序号。
         * @param number 从一开始的实际尝试序号
         * @param response 可选的供应商结果
         * @param tokenUsage 可选的 token 记账依据
         * @param input 可选实际输入，不进入持久记录
         * @throws IllegalArgumentException 当序号非正时
         * @throws NullPointerException 当可选响应容器为空时
         */
        public ModelAttempt {
            if (number < 1) {
                throw new IllegalArgumentException("模型尝试序号必须为正数。");
            }
            response = response.map(LLMProtocolSnapshots::result);
            Objects.requireNonNull(tokenUsage, "token 记账容器不能为空。");
            Objects.requireNonNull(input, "模型输入观察容器不能为空。");
        }

        /**
         * 2026/11/06：兼容不采集输入的调用方，不从响应推测实际提示。
         * @param number 尝试编号
         * @param response 实际响应
         * @param tokenUsage 用量来源
         */
        public ModelAttempt(int number, Optional<ILLMResult> response, Optional<LLMTokenUsage> tokenUsage) {
            this(number, response, tokenUsage, Optional.empty());
        }

        /**
         * 保留不提供预算记账的响应构造方式，缺失记账不等于已知零用量。
         * @param number 实际尝试序号
         * @param response 原始响应，null 表示缺失
         */
        public ModelAttempt(int number, @Nullable ILLMResult response) { this(number, Optional.ofNullable(response), Optional.empty()); }

        /**
         * 逐次暴露供应商报告的 usage，不用实际请求次数推导 token 或费用。
         * @return 缺失响应或缺失 usage 时为空
         */
        public Optional<ILLMUsage> usage() { return response.map(ILLMResult::getUsage); }
    }
}
