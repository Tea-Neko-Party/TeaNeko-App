package org.zexnocs.teanekoagent_old.agent;

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent_old.agent.prompt.AgentRequestContext;
import org.zexnocs.teanekoagent_old.llm.framework.message.LLMMessageListBuilder;
import org.zexnocs.teanekoagent_old.llm.framework.message.interfaces.ILLMMessage;
import org.zexnocs.teanekoagent_old.memory.AgentMemoryRecord;
import org.zexnocs.teanekoagent_old.personality.ResolvedAgentPersonality;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * TeaNeko Agent 单个上下文会话。
 * <br>该对象负责保存当前会话的 LLM 消息历史、已解析性格状态以及待写入记忆，避免 App 层直接管理 Agent 内部状态。
 *
 * @author zExNocs
 * @date 2026/06/10
 * @since 4.4.1
 */
@Getter
public class AgentConversationContext {
    /**
     * 会话 ID。
     */
    private final String conversationId;

    /**
     * 当前会话实际使用的 agent ID。
     */
    private String agentId;

    /**
     * 当前会话所属作用域 ID。
     */
    private final String scopeId;

    /**
     * 当前会话相关用户 ID。
     */
    private final String userId;

    /**
     * 当前上下文保存的 LLM 角色消息历史。
     */
    private final List<ILLMMessage> messages = new ArrayList<>();

    /**
     * 当前会话消息时间轴。
     * <br>时间轴与 {@link #messages} 保持相同顺序，但不会作为供应商 Message 字段发送。
     */
    private final List<AgentConversationMessage> messageTimeline = new ArrayList<>();

    /**
     * 下一条消息的顺序编号。
     */
    private long nextMessageSequence = 1;

    /**
     * 当前会话中已经抽取但尚未持久化的记忆。
     */
    private final List<AgentMemoryRecord> stagedMemories = new ArrayList<>();

    /**
     * 当前会话最近一次解析得到的性格状态。
     */
    @Nullable
    private ResolvedAgentPersonality resolvedPersonality;

    /**
     * 会话创建时间。
     */
    private final Instant createdAt = Instant.now();

    /**
     * 会话最后更新时间。
     */
    private Instant updatedAt = Instant.now();

    /**
     * 默认保留的最大消息数量。
     */
    private int maxMessageCount = 40;

    /**
     * 创建 TeaNeko Agent 上下文会话。
     *
     * @param conversationId 会话 ID。
     * @param agentId        agent ID。
     * @param scopeId        作用域 ID。
     * @param userId         用户 ID。
     */
    public AgentConversationContext(String conversationId, String agentId, String scopeId, String userId) {
        this.conversationId = safeOrRandom(conversationId);
        this.agentId = safe(agentId);
        this.scopeId = safe(scopeId);
        this.userId = safe(userId);
    }

    /**
     * 追加 system 消息。
     *
     * @param content 消息正文。
     * @return 追加后的消息。
     */
    public synchronized ILLMMessage addSystemMessage(String content) {
        return addMessage(firstMessage(LLMMessageListBuilder.builder().addSystem(content)));
    }

    /**
     * 追加 user 消息。
     *
     * @param content 消息正文。
     * @return 追加后的消息。
     */
    public synchronized ILLMMessage addUserMessage(String content) {
        return addMessage(firstMessage(LLMMessageListBuilder.builder().addUser(content)));
    }

    /**
     * 使用指定发生时间追加 user 消息。
     *
     * @param content    消息正文。
     * @param occurredAt 平台消息发生时间。
     * @return 追加后的消息。
     */
    public synchronized ILLMMessage addUserMessage(String content, Instant occurredAt) {
        return addMessage(firstMessage(LLMMessageListBuilder.builder().addUser(content)), occurredAt);
    }

    /**
     * 追加 assistant 消息。
     *
     * @param content 消息正文。
     * @return 追加后的消息。
     */
    public synchronized ILLMMessage addAssistantMessage(String content) {
        return addMessage(firstMessage(LLMMessageListBuilder.builder().addAssistant(content)));
    }

    /**
     * 追加 tool 消息。
     *
     * @param toolCallId 工具调用 ID。
     * @param content    工具执行结果。
     * @return 追加后的消息。
     */
    public synchronized ILLMMessage addToolMessage(String toolCallId, String content) {
        return addMessage(firstMessage(LLMMessageListBuilder.builder().addTool(toolCallId, content)));
    }

    /**
     * 追加任意 LLM 消息。
     *
     * @param message LLM 消息。
     * @return 追加后的消息。
     */
    public synchronized ILLMMessage addMessage(ILLMMessage message) {
        return addMessage(message, Instant.now());
    }

    /**
     * 使用指定发生时间追加任意 LLM 消息。
     *
     * @param message    LLM 消息。
     * @param occurredAt 消息实际发生时间。
     * @return 追加后的消息。
     */
    public synchronized ILLMMessage addMessage(ILLMMessage message, Instant occurredAt) {
        if (message == null) {
            throw new IllegalArgumentException("LLM message must not be null");
        }
        var recordedAt = Instant.now();
        messages.add(message);
        messageTimeline.add(new AgentConversationMessage(
                nextMessageSequence++,
                message,
                occurredAt,
                recordedAt
        ));
        touch();
        return message;
    }

    /**
     * 获取当前消息历史快照。
     *
     * @return 不可变消息历史快照。
     */
    public synchronized List<ILLMMessage> snapshotMessages() {
        return List.copyOf(messages);
    }

    /**
     * 获取当前会话消息时间轴快照。
     *
     * @return 不可变消息时间轴快照。
     */
    public synchronized List<AgentConversationMessage> snapshotMessageTimeline() {
        return List.copyOf(messageTimeline);
    }

    /**
     * 替换当前消息历史。
     *
     * @param newMessages 新消息历史。
     */
    public synchronized void replaceMessages(List<ILLMMessage> newMessages) {
        var previousTimeline = List.copyOf(messageTimeline);
        messages.clear();
        messageTimeline.clear();
        if (newMessages != null) {
            for (var message : newMessages) {
                if (message != null) {
                    messages.add(message);
                    messageTimeline.add(findTimelineRecord(previousTimeline, message));
                }
            }
        }
        touch();
    }

    /**
     * 从旧时间轴中查找消息记录。
     *
     * @param timeline 旧时间轴。
     * @param message  保留的消息。
     * @return 原时间记录；找不到时创建当前时间记录。
     */
    private AgentConversationMessage findTimelineRecord(List<AgentConversationMessage> timeline,
                                                        ILLMMessage message) {
        for (var record : timeline) {
            if (record.message() == message) {
                return record;
            }
        }
        var now = Instant.now();
        return new AgentConversationMessage(nextMessageSequence++, message, now, now);
    }

    /**
     * 设置最近一次解析得到的性格状态。
     *
     * @param resolvedPersonality 已解析性格状态。
     */
    public synchronized void setResolvedPersonality(ResolvedAgentPersonality resolvedPersonality) {
        this.resolvedPersonality = resolvedPersonality;
        if (resolvedPersonality != null && !resolvedPersonality.agentId().isBlank()) {
            this.agentId = resolvedPersonality.agentId();
        }
        touch();
    }

    /**
     * 设置默认保留的最大消息数量。
     *
     * @param maxMessageCount 最大消息数量。
     */
    public synchronized void setMaxMessageCount(int maxMessageCount) {
        this.maxMessageCount = Math.max(1, maxMessageCount);
    }

    /**
     * 将当前上下文转换为性格和记忆模块使用的请求上下文。
     *
     * @return Agent 请求上下文。
     */
    public synchronized AgentRequestContext toRequestContext() {
        return new AgentRequestContext(scopeId, agentId, userId, conversationId);
    }

    /**
     * 暂存一条待写入的长期记忆。
     *
     * @param record 待写入记忆。
     */
    public synchronized void stageMemory(AgentMemoryRecord record) {
        if (record == null || record.getContent() == null || record.getContent().isBlank()) {
            return;
        }
        enrichMemory(record);
        stagedMemories.add(record);
        touch();
    }

    /**
     * 取出并清空当前暂存的记忆。
     *
     * @return 待写入记忆快照。
     */
    public synchronized List<AgentMemoryRecord> drainStagedMemories() {
        var records = List.copyOf(stagedMemories);
        stagedMemories.clear();
        touch();
        return records;
    }

    /**
     * 为记忆补全当前上下文元信息。
     *
     * @param record 记忆记录。
     */
    private void enrichMemory(AgentMemoryRecord record) {
        if (record.getScopeId() == null || record.getScopeId().isBlank()) {
            record.setScopeId(scopeId);
        }
        if (record.getAgentId() == null || record.getAgentId().isBlank()) {
            record.setAgentId(agentId);
        }
        if (record.getSubjectId() == null || record.getSubjectId().isBlank()) {
            record.setSubjectId(userId);
        }
        if (record.getSource() == null || record.getSource().isBlank()) {
            record.setSource(conversationId);
        }
        record.setUpdatedAt(Instant.now());
    }

    /**
     * 从 LLM message builder 中取出第一条消息。
     *
     * @param builder LLM message builder。
     * @return 第一条消息。
     */
    private static ILLMMessage firstMessage(LLMMessageListBuilder builder) {
        var builtMessages = builder.build();
        if (builtMessages.isEmpty()) {
            throw new IllegalStateException("LLM message builder did not create message");
        }
        return builtMessages.getFirst();
    }

    /**
     * 更新会话最后更新时间。
     */
    private void touch() {
        updatedAt = Instant.now();
    }

    /**
     * 规范化字符串值。
     *
     * @param value 原始值。
     * @return 非空字符串。
     */
    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * 规范化字符串值，空值时生成随机 ID。
     *
     * @param value 原始值。
     * @return 非空 ID。
     */
    private static String safeOrRandom(String value) {
        var safeValue = safe(value);
        return safeValue.isBlank() ? UUID.randomUUID().toString() : safeValue;
    }
}
