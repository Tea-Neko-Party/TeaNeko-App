package org.zexnocs.teanekoagent.agent.access;

import org.zexnocs.teanekoagent.llm.framework.interaction.LLMInteractionSettings;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptionsSnapshot;
import org.zexnocs.teanekoagent.llm.framework.model.ModelCapabilities;

import java.util.Objects;

/**
 * 可供后续轮次组件使用的就绪数据，凭据解析结果始终留在检查边界之外。
 * @param modelOptions 已解析的模型参数
 * @param capabilities 具体模型的能力快照
 * @param interaction 本次交互设置
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
public record AgentRuntimeSnapshot(LLMModelOptionsSnapshot modelOptions, ModelCapabilities capabilities,
                                   LLMInteractionSettings interaction) {
    /**
     * 拒绝不完整就绪数据，不能把可选缺失能力伪装为已经验证。
     * @param modelOptions 模型参数快照
     * @param capabilities 已验证模型能力
     * @param interaction 已验证交互设置
     */
    public AgentRuntimeSnapshot {
        Objects.requireNonNull(modelOptions, "模型参数快照不能为空。");
        Objects.requireNonNull(capabilities, "模型能力不能为空。");
        Objects.requireNonNull(interaction, "交互设置不能为空。");
    }
}
