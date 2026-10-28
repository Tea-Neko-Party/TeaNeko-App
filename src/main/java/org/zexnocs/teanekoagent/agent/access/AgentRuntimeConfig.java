package org.zexnocs.teanekoagent.agent.access;

import org.zexnocs.teanekoagent.llm.framework.interaction.LLMInteractionSettings;

import java.util.Objects;

/**
 * 本地 Agent 总开关及一次交互设置，不包含平台引用或凭据。
 * @param enabled 本地全局开关
 * @param interaction 模型/工具次数、时限和可选 token 限额
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
public record AgentRuntimeConfig(boolean enabled, LLMInteractionSettings interaction) {
    /**
     * 固定本轮设置，不由此构造父预算或启动执行。
     * @param enabled 本地总开关
     * @param interaction 已验证的交互设置
     */
    public AgentRuntimeConfig { Objects.requireNonNull(interaction, "交互设置不能为空。"); }
}
