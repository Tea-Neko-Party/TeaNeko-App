package org.zexnocs.teanekoagent.memory.layered;

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.agent.access.AgentAccessDecision;
import org.zexnocs.teanekoagent.llm.framework.interaction.LLMInteractionResult;

import java.io.Serial;

/**
 * 记忆整理的安全失败分类，只保留宿主原因及模型状态，不保留模型输出或原始解析消息。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.7.1-l
 * @version 4.7.1-l
 */
public final class MemoryProcessingException extends IllegalStateException {
    @Serial
    private static final long serialVersionUID = 1L;
    private final Reason reason;
    private final LLMInteractionResult.@Nullable Status modelStatus;
    private final String accessStatus;

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
        super(reason.description());
        this.reason = reason;
        this.modelStatus = modelStatus;
        this.accessStatus = "未检查";
    }

    /**
     * 只提取门控与就绪状态的枚举，不保留运行快照或配置。
     * @param decision 当前实际拒绝的门控结果
     */
    public MemoryProcessingException(AgentAccessDecision decision) {
        super(Reason.ACCESS_REVOKED.description());
        this.reason = Reason.ACCESS_REVOKED;
        this.modelStatus = null;
        this.accessStatus = decision.reason().name()
                + decision.readiness().map(value -> "/" + value.status().name()).orElse("");
    }

    /** @return 实际观察到的门控及就绪枚举，未经过门控时为未检查 */
    public String accessStatus() {
        return accessStatus;
    }

    /** @return 可供日志及网页筛选的稳定原因 */
    public Reason reason() {
        return reason;
    }

    /** @return 原模型终止状态，未观察到模型结果时为空 */
    public LLMInteractionResult.@Nullable Status modelStatus() {
        return modelStatus;
    }

    /**
     * 明确区分配置准入、模型返回、格式、预算及事务租约问题。
     *
     * @author zExNocs
     * @date 2026/11/04
     * @since 4.7.1-l
     * @version 4.7.1-l
     */
    public enum Reason {
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
