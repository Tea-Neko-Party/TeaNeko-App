package org.zexnocs.teanekoagent_old.agent.event;

import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMAssistantMessage;
import org.zexnocs.teanekoagent_old.agent.AgentConversationContext;
import org.zexnocs.teanekoagent_old.agent.thinking.AgentOutput;
import org.zexnocs.teanekoagent_old.agent.token.AgentTokenUsageRecord;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Agent 单轮运行事件数据。
 * <br>直接保存 TeaNeko App 消息，不再维护独立的 Agent 入站消息副本。
 *
 * @author zExNocs
 * @date 2026/06/10
 * @since 4.4.1
 */
@Getter
@Setter
public class AgentTurnData {
    /** 当前 Agent 会话上下文。 */
    private final AgentConversationContext context;

    /** 当前轮次对应的 TeaNeko 入站消息。 */
    private final ITeaNekoMessageData inboundMessage;

    /** 本轮最多允许执行的工具调用轮数。 */
    private int maxToolRounds;

    /** 本轮暴露给模型的工具包名称。 */
    private String toolPackage;

    /** 本轮模型最终生成的 assistant 消息。 */
    @Nullable
    private ILLMAssistantMessage assistantMessage;

    /** 本轮 Agent 生成的结构化输出。 */
    @Nullable
    private AgentOutput agentOutput;

    /** 本轮对话产生的 token 使用记录。 */
    private final List<AgentTokenUsageRecord> tokenUsageRecords = new ArrayList<>();

    /**
     * 创建 Agent 单轮运行事件数据。
     *
     * @param context        当前 Agent 会话上下文
     * @param inboundMessage 当前轮次对应的 TeaNeko 入站消息
     * @param maxToolRounds  最大工具调用轮数
     * @param toolPackage    暴露给模型的工具包名称
     */
    public AgentTurnData(AgentConversationContext context,
                         ITeaNekoMessageData inboundMessage,
                         int maxToolRounds,
                         String toolPackage) {
        this.context = context;
        this.inboundMessage = inboundMessage;
        this.maxToolRounds = Math.max(0, maxToolRounds);
        this.toolPackage = toolPackage == null ? "" : toolPackage;
    }

    /**
     * 查找本轮 Agent 结构化输出。
     *
     * @return 尚未生成时为空
     */
    public Optional<AgentOutput> findAgentOutput() {
        return Optional.ofNullable(agentOutput);
    }

    /**
     * 追加一条 token 使用记录。
     *
     * @param record token 使用记录
     */
    public void addTokenUsageRecord(AgentTokenUsageRecord record) {
        if (record != null) {
            tokenUsageRecords.add(record);
        }
    }

    /**
     * 获取本轮 token 使用记录快照。
     *
     * @return 不可变记录快照
     */
    public List<AgentTokenUsageRecord> snapshotTokenUsageRecords() {
        return List.copyOf(tokenUsageRecords);
    }
}
