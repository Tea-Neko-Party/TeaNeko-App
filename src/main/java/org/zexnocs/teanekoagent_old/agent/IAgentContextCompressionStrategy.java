package org.zexnocs.teanekoagent_old.agent;

import org.zexnocs.teanekoagent_old.llm.framework.message.interfaces.ILLMMessage;

import java.util.List;

/**
 * TeaNeko Agent 上下文压缩策略接口。
 * <br>策略直接处理 LLM 消息历史，避免 Agent 层重复定义消息结构。
 *
 * @author zExNocs
 * @date 2026/06/09
 * @since 4.4.1
 */
public interface IAgentContextCompressionStrategy {
    /**
     * 压缩消息历史。
     *
     * @param messages             原始消息历史。
     * @param keepLastMessageCount 需要保留的最近消息数量。
     * @return 压缩后的消息历史。
     */
    List<ILLMMessage> compress(List<ILLMMessage> messages, int keepLastMessageCount);
}
