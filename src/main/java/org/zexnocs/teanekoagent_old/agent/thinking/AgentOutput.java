package org.zexnocs.teanekoagent_old.agent.thinking;

import java.util.List;

/**
 * Agent 单轮结构化输出。
 * <br>该对象将可公开思考摘要、最终用户答案和运行时元数据分离，平台适配器只需发送 {@link #answer()}。
 *
 * @param thoughts 可公开的有限思考摘要
 * @param answer   最终能够呈现给用户的答案
 * @param metadata 本轮运行时元数据
 * @author zExNocs
 * @date 2026/06/10
 * @since 4.4.1
 */
public record AgentOutput(
        List<AgentThoughtStep> thoughts,
        String answer,
        AgentOutputMetadata metadata
) {
    /**
     * 创建规范化 Agent 输出。
     */
    public AgentOutput {
        thoughts = thoughts == null ? List.of() : List.copyOf(thoughts);
        answer = answer == null ? "" : answer.trim();
        metadata = metadata == null ? AgentOutputMetadata.empty() : metadata;
    }

    /**
     * 从普通文本创建不包含思考过程的兼容输出。
     *
     * @param answer 最终答案
     * @return 兼容 Agent 输出
     */
    public static AgentOutput simple(String answer) {
        return new AgentOutput(List.of(), answer, AgentOutputMetadata.empty());
    }
}
