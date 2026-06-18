package org.zexnocs.teanekoagent_old.agent.thinking;

/**
 * Agent 可公开思考摘要的阶段。
 * <br>该枚举描述 Agent 运行时生成的高层决策摘要，不承载模型供应商返回的原始推理内容。
 *
 * @author zExNocs
 * @date 2026/06/10
 * @since 4.4.1
 */
public enum AgentThoughtPhase {
    /**
     * 分析当前请求并决定下一步行动。
     */
    ANALYSIS,

    /**
     * 已执行工具并获得新的外部观察。
     */
    OBSERVATION,

    /**
     * 在生成最终答案前进行简短校验。
     */
    FINAL_CHECK
}
