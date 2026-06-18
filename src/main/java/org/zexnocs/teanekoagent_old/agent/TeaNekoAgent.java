package org.zexnocs.teanekoagent_old.agent;

import lombok.Getter;
import org.zexnocs.teanekoagent_old.llm.framework.input.LLMPrompt;
import org.zexnocs.teanekoagent_old.llm.framework.message.interfaces.ILLMMessage;
import org.zexnocs.teanekoagent_old.memory.AgentMemoryRecord;
import org.zexnocs.teanekoagent_old.memory.PersonalityDeltaRecord;
import org.zexnocs.teanekoagent_old.personality.ResolvedAgentPersonality;

import java.time.Instant;
import java.util.Optional;

/**
 * TeaNeko Agent 会话门面。
 * <br>该对象用于上层服务方便地管理单个对话中的 LLM 消息历史、上下文压缩、记忆写入和性格演化。
 *
 * @author zExNocs
 * @date 2026/06/10
 * @since 4.4.1
 */
public class TeaNekoAgent {
    /**
     * 当前 TeaNeko Agent 门面持有的上下文会话。
     */
    @Getter
    private final AgentConversationContext context;

    /**
     * TeaNeko Agent 上下文编排服务。
     */
    private final AgentContextService contextService;

    /**
     * 创建 TeaNeko Agent 会话门面。
     *
     * @param context        TeaNeko Agent 上下文会话。
     * @param contextService TeaNeko Agent 上下文编排服务。
     */
    public TeaNekoAgent(AgentConversationContext context, AgentContextService contextService) {
        if (context == null) {
            throw new IllegalArgumentException("Agent conversation context must not be null");
        }
        if (contextService == null) {
            throw new IllegalArgumentException("Agent context service must not be null");
        }
        this.context = context;
        this.contextService = contextService;
    }

    /**
     * 追加 system 消息。
     *
     * @param content 消息正文。
     * @return 追加后的 LLM 消息。
     */
    public ILLMMessage addSystemMessage(String content) {
        return contextService.appendSystem(context, content);
    }

    /**
     * 追加 user 消息。
     *
     * @param content 消息正文。
     * @return 追加后的 LLM 消息。
     */
    public ILLMMessage addUserMessage(String content) {
        return contextService.appendUser(context, content);
    }

    /**
     * 使用指定发生时间追加 user 消息。
     *
     * @param content    消息正文。
     * @param occurredAt 平台消息发生时间。
     * @return 追加后的 LLM 消息。
     */
    public ILLMMessage addUserMessage(String content, Instant occurredAt) {
        return contextService.appendUser(context, content, occurredAt);
    }

    /**
     * 追加 assistant 消息。
     *
     * @param content 消息正文。
     * @return 追加后的 LLM 消息。
     */
    public ILLMMessage addAssistantMessage(String content) {
        return contextService.appendAssistant(context, content);
    }

    /**
     * 追加 tool 消息。
     *
     * @param toolCallId 工具调用 ID。
     * @param content    工具执行结果。
     * @return 追加后的 LLM 消息。
     */
    public ILLMMessage addToolMessage(String toolCallId, String content) {
        return contextService.appendTool(context, toolCallId, content);
    }

    /**
     * 按上下文默认数量压缩消息历史。
     */
    public void compress() {
        contextService.compress(context);
    }

    /**
     * 按指定数量压缩消息历史。
     *
     * @param keepLastMessageCount 需要保留的最近消息数量。
     */
    public void compress(int keepLastMessageCount) {
        contextService.compress(context, keepLastMessageCount);
    }

    /**
     * 暂存一条待写入长期记忆的记录。
     *
     * @param record 待写入记忆。
     */
    public void stageMemory(AgentMemoryRecord record) {
        contextService.stageMemory(context, record);
    }

    /**
     * 将当前上下文暂存的记忆写入记忆模块。
     *
     * @return 实际写入的记忆数量。
     */
    public int recordStagedMemories() {
        return contextService.recordStagedMemories(context);
    }

    /**
     * 记录一条性格学习修正。
     *
     * @param field      修正字段。
     * @param content    修正内容。
     * @param source     修正来源。
     * @param confidence 置信度。
     * @return 成功写入的性格修正记录；被边界策略拒绝时为空。
     */
    public Optional<PersonalityDeltaRecord> evolvePersonality(String field,
                                                              String content,
                                                              String source,
                                                              double confidence) {
        return contextService.evolvePersonality(context, field, content, source, confidence);
    }

    /**
     * 刷新当前上下文的性格状态。
     *
     * @return 已解析性格状态。
     */
    public ResolvedAgentPersonality refreshPersonality() {
        return contextService.resolvePersonality(context);
    }

    /**
     * 构建当前上下文对应的 LLM prompt。
     *
     * @param userMessage 当前用户消息；如果该消息已经写入上下文，可以传入空字符串避免重复追加。
     * @return LLM prompt。
     */
    public LLMPrompt buildPrompt(String userMessage) {
        return contextService.buildPrompt(context, userMessage);
    }
}
