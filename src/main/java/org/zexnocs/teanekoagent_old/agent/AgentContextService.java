package org.zexnocs.teanekoagent_old.agent;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent_old.agent.prompt.AgentPromptBuildRequest;
import org.zexnocs.teanekoagent_old.agent.prompt.AgentPromptBuilder;
import org.zexnocs.teanekoagent_old.agent.prompt.AgentPromptComponent;
import org.zexnocs.teanekoagent_old.llm.framework.input.LLMPrompt;
import org.zexnocs.teanekoagent_old.llm.framework.message.interfaces.ILLMMessage;
import org.zexnocs.teanekoagent_old.memory.AgentMemoryQueryService;
import org.zexnocs.teanekoagent_old.memory.AgentMemoryRecord;
import org.zexnocs.teanekoagent_old.memory.PersonalityDeltaRecord;
import org.zexnocs.teanekoagent_old.personality.AgentPersonalityResolver;
import org.zexnocs.teanekoagent_old.personality.ResolvedAgentPersonality;
import org.zexnocs.teanekoagent_old.personality.learning.PersonalityLearningService;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * TeaNeko Agent 上下文编排服务。
 * <br>该服务是 Agent 模块的主要入口，负责创建会话、维护 LLM 消息历史、注入性格和记忆、构建 prompt、压缩上下文以及触发性格演化。
 *
 * @author zExNocs
 * @date 2026/06/10
 * @since 4.4.1
 */
@Service
@RequiredArgsConstructor
public class AgentContextService {
    /**
     * Agent 性格解析服务。
     */
    private final AgentPersonalityResolver personalityResolver;

    /**
     * Agent 记忆读写服务。
     */
    private final AgentMemoryQueryService memoryQueryService;

    /**
     * Agent 性格学习服务。
     */
    private final PersonalityLearningService personalityLearningService;

    /**
     * Agent prompt 构建服务。
     */
    private final AgentPromptBuilder promptBuilder;

    /**
     * Agent 上下文压缩策略。
     */
    private final IAgentContextCompressionStrategy compressionStrategy;

    /**
     * 创建一个便于上层持有和调用的 TeaNeko Agent 门面。
     *
     * @param conversationId 会话 ID。
     * @param scopeId        作用域 ID。
     * @param agentId        agent ID。
     * @param userId         用户 ID。
     * @return TeaNeko Agent 门面。
     */
    public TeaNekoAgent createTeaNekoAgent(String conversationId, String scopeId, String agentId, String userId) {
        return new TeaNekoAgent(createContext(conversationId, scopeId, agentId, userId), this);
    }

    /**
     * 创建一个新的 TeaNeko Agent 上下文会话。
     *
     * @param conversationId 会话 ID。
     * @param scopeId        作用域 ID。
     * @param agentId        agent ID。
     * @param userId         用户 ID。
     * @return TeaNeko Agent 上下文会话。
     */
    public AgentConversationContext createContext(String conversationId, String scopeId, String agentId, String userId) {
        var context = new AgentConversationContext(conversationId, agentId, scopeId, userId);
        resolvePersonality(context);
        return context;
    }

    /**
     * 重新解析并写入当前上下文的性格状态。
     *
     * @param context Agent 上下文会话。
     * @return 已解析性格状态。
     */
    public ResolvedAgentPersonality resolvePersonality(AgentConversationContext context) {
        var checkedContext = requireContext(context);
        var personality = personalityResolver.resolve(checkedContext.toRequestContext());
        checkedContext.setResolvedPersonality(personality);
        return personality;
    }

    /**
     * 追加 system 消息。
     *
     * @param context Agent 上下文会话。
     * @param content 消息正文。
     * @return 追加后的 LLM 消息。
     */
    public ILLMMessage appendSystem(AgentConversationContext context, String content) {
        return requireContext(context).addSystemMessage(content);
    }

    /**
     * 追加 user 消息。
     *
     * @param context Agent 上下文会话。
     * @param content 消息正文。
     * @return 追加后的 LLM 消息。
     */
    public ILLMMessage appendUser(AgentConversationContext context, String content) {
        return requireContext(context).addUserMessage(content);
    }

    /**
     * 使用平台消息发生时间追加 user 消息。
     *
     * @param context    Agent 上下文会话。
     * @param content    消息正文。
     * @param occurredAt 平台消息发生时间。
     * @return 追加后的 LLM 消息。
     */
    public ILLMMessage appendUser(AgentConversationContext context, String content, Instant occurredAt) {
        return requireContext(context).addUserMessage(content, occurredAt);
    }

    /**
     * 追加 assistant 消息。
     *
     * @param context Agent 上下文会话。
     * @param content 消息正文。
     * @return 追加后的 LLM 消息。
     */
    public ILLMMessage appendAssistant(AgentConversationContext context, String content) {
        return requireContext(context).addAssistantMessage(content);
    }

    /**
     * 追加 tool 消息。
     *
     * @param context    Agent 上下文会话。
     * @param toolCallId 工具调用 ID。
     * @param content    工具执行结果。
     * @return 追加后的 LLM 消息。
     */
    public ILLMMessage appendTool(AgentConversationContext context, String toolCallId, String content) {
        return requireContext(context).addToolMessage(toolCallId, content);
    }

    /**
     * 按上下文默认数量压缩消息历史。
     *
     * @param context Agent 上下文会话。
     */
    public void compress(AgentConversationContext context) {
        var checkedContext = requireContext(context);
        compress(checkedContext, checkedContext.getMaxMessageCount());
    }

    /**
     * 按指定数量压缩消息历史。
     *
     * @param context              Agent 上下文会话。
     * @param keepLastMessageCount 需要保留的最近消息数量。
     */
    public void compress(AgentConversationContext context, int keepLastMessageCount) {
        var checkedContext = requireContext(context);
        var compressed = compressionStrategy.compress(checkedContext.snapshotMessages(), keepLastMessageCount);
        checkedContext.replaceMessages(compressed);
    }

    /**
     * 暂存一条待写入长期记忆的记录。
     *
     * @param context Agent 上下文会话。
     * @param record  待写入记忆。
     */
    public void stageMemory(AgentConversationContext context, AgentMemoryRecord record) {
        requireContext(context).stageMemory(record);
    }

    /**
     * 将当前上下文暂存的记忆写入记忆模块。
     *
     * @param context Agent 上下文会话。
     * @return 实际写入的记忆数量。
     */
    public int recordStagedMemories(AgentConversationContext context) {
        var checkedContext = requireContext(context);
        if (checkedContext.getUserId().isBlank()) {
            return 0;
        }
        var records = checkedContext.drainStagedMemories();
        for (var record : records) {
            memoryQueryService.appendUserMemory(
                    checkedContext.getScopeId(),
                    checkedContext.getAgentId(),
                    checkedContext.getUserId(),
                    record
            );
        }
        return records.size();
    }

    /**
     * 记录一条性格学习修正并在成功后刷新上下文性格状态。
     *
     * @param context    Agent 上下文会话。
     * @param field      修正字段。
     * @param content    修正内容。
     * @param source     修正来源。
     * @param confidence 置信度。
     * @return 成功写入的性格修正记录；被边界策略拒绝时为空。
     */
    public Optional<PersonalityDeltaRecord> evolvePersonality(AgentConversationContext context,
                                                              String field,
                                                              String content,
                                                              String source,
                                                              double confidence) {
        var checkedContext = requireContext(context);
        var result = personalityLearningService.recordDelta(
                checkedContext.toRequestContext(),
                field,
                content,
                source,
                confidence
        );
        result.ifPresent(ignored -> resolvePersonality(checkedContext));
        return result;
    }

    /**
     * 构建当前上下文对应的 LLM prompt。
     *
     * @param context     Agent 上下文会话。
     * @param userMessage 当前用户消息；如果该消息已经写入上下文，可以传入空字符串避免重复追加。
     * @return LLM prompt。
     */
    public LLMPrompt buildPrompt(AgentConversationContext context, String userMessage) {
        return buildPrompt(context, userMessage, List.of());
    }

    /**
     * 构建带额外 prompt 组件的 LLM prompt。
     *
     * @param context         Agent 上下文会话。
     * @param userMessage     当前用户消息。
     * @param extraComponents 额外 prompt 组件。
     * @return LLM prompt。
     */
    public LLMPrompt buildPrompt(AgentConversationContext context,
                                 String userMessage,
                                 List<AgentPromptComponent> extraComponents) {
        var checkedContext = requireContext(context);
        var personality = checkedContext.getResolvedPersonality();
        if (personality == null) {
            personality = resolvePersonality(checkedContext);
        }
        var components = new ArrayList<AgentPromptComponent>();
        components.add(buildTemporalContextComponent(checkedContext));
        if (extraComponents != null) {
            components.addAll(extraComponents);
        }
        return promptBuilder.build(new AgentPromptBuildRequest(
                checkedContext.toRequestContext(),
                personality,
                userMessage,
                checkedContext.snapshotMessages(),
                components
        ));
    }

    /**
     * 构建当前时间和会话消息时间轴 Prompt 组件。
     *
     * @param context Agent 上下文会话。
     * @return 时间上下文组件。
     */
    private AgentPromptComponent buildTemporalContextComponent(AgentConversationContext context) {
        var now = Instant.now();
        var zoneId = ZoneId.systemDefault();
        var text = new StringBuilder();
        text.append("currentTimeUtc: ").append(now).append('\n');
        text.append("currentTimeLocal: ")
                .append(DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(now.atZone(zoneId)))
                .append('\n');
        text.append("localTimeZone: ").append(zoneId).append('\n');
        text.append("conversationCreatedAt: ").append(context.getCreatedAt()).append('\n');
        text.append("messageTimeline:\n");
        for (var record : context.snapshotMessageTimeline()) {
            text.append("- sequence=")
                    .append(record.sequence())
                    .append(", role=")
                    .append(record.message().getRole())
                    .append(", occurredAt=")
                    .append(record.occurredAt())
                    .append(", recordedAt=")
                    .append(record.recordedAt())
                    .append('\n');
        }
        return new AgentPromptComponent("temporal-context", 12, text.toString());
    }

    /**
     * 校验并返回非空上下文。
     *
     * @param context Agent 上下文会话。
     * @return 非空上下文。
     */
    private AgentConversationContext requireContext(AgentConversationContext context) {
        if (context == null) {
            throw new IllegalArgumentException("Agent conversation context must not be null");
        }
        return context;
    }
}
