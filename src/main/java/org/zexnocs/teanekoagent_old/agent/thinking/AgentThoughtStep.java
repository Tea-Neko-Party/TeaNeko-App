package org.zexnocs.teanekoagent_old.agent.thinking;

/**
 * Agent 单个可公开思考摘要步骤。
 * <br>仅保存简短、高层、可审计的决策说明，不保存私有链式思考或供应商原始 reasoning content。
 *
 * @param index   本轮 Agent 输出中的步骤序号，从 1 开始
 * @param phase   思考阶段
 * @param summary 面向调试和审计的简短摘要
 * @author zExNocs
 * @date 2026/06/10
 * @since 4.4.1
 */
public record AgentThoughtStep(int index, AgentThoughtPhase phase, String summary) {
    /**
     * 创建规范化思考摘要步骤。
     *
     * @param index   步骤序号
     * @param phase   思考阶段
     * @param summary 思考摘要
     */
    public AgentThoughtStep {
        index = Math.max(1, index);
        phase = phase == null ? AgentThoughtPhase.ANALYSIS : phase;
        summary = summary == null ? "" : summary.trim();
    }
}
