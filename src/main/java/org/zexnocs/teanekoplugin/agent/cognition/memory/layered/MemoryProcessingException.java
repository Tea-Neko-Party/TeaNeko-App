package org.zexnocs.teanekoplugin.agent.cognition.memory.layered;

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.llm.framework.interaction.LLMInteractionResult;
import org.zexnocs.teanekoagent.llm.framework.structured.LLMJsonException;
import org.zexnocs.teanekoagent.runtime.access.AgentAccessDecision;
import org.zexnocs.teanekocore.logger.FailureDiagnostic;
import org.zexnocs.teanekocore.logger.IFailureReason;

import java.io.Serial;

/**
 * 记忆整理的安全失败分类，只保留宿主原因及模型状态，不保留模型输出或原始解析消息。
 *
 * 修改记录：<br>
 * 2026/11/04：将模型或本地配置未就绪与运行中资格撤销分别报告。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * <br>2026/11/05：保留固定协议说明及原错误的安全快照，不保存模型原文。
 * <br>2026/11/06：共享 JSON 诊断映射为原领域失败，保留暂停与退避分类。
 * <br>2026/11/07：认知业务与宿主生命周期解耦，保留原身份、事务及异步完成边界。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.7.1-l
 * @version 4.9.0-l
 */
public final class MemoryProcessingException extends IllegalStateException implements IFailureReason {
    @Serial
    private static final long serialVersionUID = 1L;
    private final Reason reason;
    private final LLMInteractionResult.@Nullable Status modelStatus;
    private final String accessStatus;
    private final String detail;
    private final transient @Nullable FailureDiagnostic diagnostic;

    /**
     * @param reason 明确宿主失败原因
     */
    public MemoryProcessingException(Reason reason) {
        this(reason, null);
    }

    /**
     * @param reason 安全原因
     * @param modelStatus 模型真实终止状态，未进入模型时为空
     */
    public MemoryProcessingException(Reason reason, LLMInteractionResult.@Nullable Status modelStatus) {
        // 2026/11/05：原调用保持固定分类，新调用可补充安全原因和原解析位置。
        this(reason, modelStatus, reason.description(), null);
    }

    /**
     * 2026/11/05：补充固定协议说明及脱敏快照，不接收原始解析异常或响应文本。
     * @param reason 安全分类
     * @param modelStatus 实际模型状态
     * @param detail 固定说明，不允许传入模型输出、字段值或供应商错误正文
     * @param diagnostic 原失败的安全快照
     */
    public MemoryProcessingException(Reason reason, LLMInteractionResult.@Nullable Status modelStatus,
            String detail, @Nullable FailureDiagnostic diagnostic) {
        super(reason.description());
        this.reason = reason;
        this.modelStatus = modelStatus;
        this.accessStatus = "未检查";
        this.detail = detail;
        this.diagnostic = diagnostic;
    }

    /**
     * 只提取门控与就绪状态的枚举，不保留运行快照或配置。
     * @param decision 当前实际拒绝的门控结果
     */
    public MemoryProcessingException(AgentAccessDecision decision) {
        // 2026/11/04：尚未取得模型配置时不能误称为已经撤销调用权限。
        super(accessReason(decision).description());
        this.reason = accessReason(decision);
        this.modelStatus = null;
        // 2026/11/05：门控拒绝仍使用原固定分类，不伪造模型或解析诊断。
        this.detail = reason.description();
        this.diagnostic = null;
        this.accessStatus = decision.reason().name()
                + decision.readiness().map(value -> "/" + value.status().name()).orElse("");
    }

    /** @return 实际观察到的门控及就绪枚举，未经过门控时为未检查 */
    public String accessStatus() {
        return accessStatus;
    }

    /**
     * 2026/11/04：保留配置未就绪的实际门控结果，不改变原准入和重试策略。
     *
     * @param decision 当前拒绝结果
     * @return 配置失败或运行资格失效分类
     */
    private static Reason accessReason(AgentAccessDecision decision) {
        return decision.reason() == AgentAccessDecision.Reason.READINESS_UNAVAILABLE
                ? Reason.READINESS_UNAVAILABLE : Reason.ACCESS_REVOKED;
    }

    /** @return 可供日志及网页筛选的稳定原因 */
    public Reason reason() {
        return reason;
    }

    /**
     * 2026/11/06：保留统一 JSON 诊断，并维持无效输出的持久暂停计数。
     * @param failure 异步失败
     * @return JSON 的领域分类；其他异常保持原失败链
     */
    public static RuntimeException structured(Throwable failure) {
        var cause = failure;
        while (cause instanceof java.util.concurrent.CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        if (cause instanceof LLMJsonException json) {
            var reason = switch (json.code()) {
                case MODEL_RESULT -> Reason.MODEL_RESULT;
                case OUTPUT_LIMIT -> Reason.OUTPUT_LIMIT;
                default -> Reason.INVALID_OUTPUT;
            };
            return new MemoryProcessingException(reason, json.status(), json.failureReason(), json.failureDiagnostic());
        }
        return cause instanceof RuntimeException runtime ? runtime : new java.util.concurrent.CompletionException(cause);
    }

    /** @return 2026/11/05：可公开的协议或资格失败说明 */
    @Override
    public String failureReason() {
        return detail;
    }

    /** @return 2026/11/05：原模型或 JSON 校验失败的安全快照，没有时为空 */
    @Override
    public @Nullable FailureDiagnostic failureDiagnostic() {
        return diagnostic;
    }

    /** @return 原模型终止状态，未观察到模型结果时为空 */
    public LLMInteractionResult.@Nullable Status modelStatus() {
        return modelStatus;
    }

    /**
     * 明确区分配置准入、模型返回、格式、预算及事务租约问题。
     *
     * 修改记录：<br>
     * 2026/11/04：增加尚未取得有效模型配置的独立原因。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     * <br>2026/11/07：认知业务与宿主生命周期解耦，保留原身份、事务及异步完成边界。
     *
     *
     * @author zExNocs
     * @date 2026/11/04
     * @since 4.7.1-l
     * @version 4.9.0-l
     */
    public enum Reason {
        // 2026/11/04：模型前配置错误单独提示，不与聊天开关撤销混淆。
        READINESS_UNAVAILABLE("模型或本地运行配置未就绪，请检查同一时间的 Agent 就绪日志及 model.yml"),
        ACCESS_REVOKED("当前生成或提交资格不满足，请检查聊天开关及模型就绪配置"),
        INPUT_LIMIT("模型输入超过预算"),
        MODEL_RESULT("模型未正常完成，请结合相同请求的 LLM 日志检查"),
        INVALID_OUTPUT("模型输出格式不符合记忆协议"),
        OUTPUT_LIMIT("模型输出超过容量上限"),
        LEASE_CHANGED("原输入或整理租约已变化");

        private final String description;

        /** @param description 固定中文诊断 */
        Reason(String description) {
            this.description = description;
        }

        /** @return 不包含对话或秘密的简短说明 */
        public String description() {
            return description;
        }
    }
}
