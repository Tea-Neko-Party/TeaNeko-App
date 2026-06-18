package org.zexnocs.teanekoagent_old.agent;

import org.springframework.stereotype.Component;
import org.zexnocs.teanekoagent_old.llm.framework.message.interfaces.ILLMMessage;
import org.zexnocs.teanekoagent_old.llm.framework.message.interfaces.LLMMessageRole;

import java.util.ArrayList;
import java.util.List;

/**
 * 默认的 TeaNeko Agent 上下文压缩策略。
 * <br>该策略会保留第一条 system 消息与最近若干条消息，适合作为尚未接入摘要模型前的确定性压缩实现。
 *
 * @author zExNocs
 * @date 2026/06/09
 * @since 4.4.1
 */
@Component
public class TailKeepingAgentContextCompressionStrategy implements IAgentContextCompressionStrategy {
    /**
     * 压缩消息历史。
     *
     * @param messages             原始消息历史。
     * @param keepLastMessageCount 需要保留的最近消息数量。
     * @return 压缩后的消息历史。
     */
    @Override
    public List<ILLMMessage> compress(List<ILLMMessage> messages, int keepLastMessageCount) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        var keepCount = Math.max(1, keepLastMessageCount);
        if (messages.size() <= keepCount) {
            return List.copyOf(messages);
        }

        var preservedSystem = findFirstSystemMessage(messages);
        var tailStart = Math.max(0, messages.size() - keepCount);
        var tail = messages.subList(tailStart, messages.size());
        var result = new ArrayList<ILLMMessage>();
        if (preservedSystem != null) {
            result.add(preservedSystem);
        }
        for (var message : tail) {
            if (message != null && message != preservedSystem) {
                result.add(message);
            }
        }
        return List.copyOf(result);
    }

    /**
     * 查找第一条 system 消息。
     *
     * @param messages 消息历史。
     * @return 第一条 system 消息；不存在时返回 {@code null}。
     */
    private ILLMMessage findFirstSystemMessage(List<ILLMMessage> messages) {
        for (var message : messages) {
            if (message != null && message.getRole() == LLMMessageRole.SYSTEM) {
                return message;
            }
        }
        return null;
    }
}
