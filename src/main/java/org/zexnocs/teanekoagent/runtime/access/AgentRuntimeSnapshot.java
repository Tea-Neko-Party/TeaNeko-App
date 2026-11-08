package org.zexnocs.teanekoagent.runtime.access;

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.zexnocs.teanekoagent.llm.framework.interaction.LLMInteractionSettings;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptionsSnapshot;
import org.zexnocs.teanekoagent.llm.framework.model.ModelCapabilities;

import java.util.Objects;

/**
 * 可供后续轮次组件使用的就绪数据，凭据解析结果始终留在检查边界之外。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @param modelOptions 已解析的模型参数
 * @param capabilities 具体模型的能力快照
 * @param interaction 本次交互设置
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.5.0-l
 * @version 4.8.0-l
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
