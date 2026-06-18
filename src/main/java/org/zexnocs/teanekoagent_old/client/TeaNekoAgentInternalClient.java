package org.zexnocs.teanekoagent_old.client;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.zexnocs.teanekoagent_old.agent.AgentContextService;
import org.zexnocs.teanekoagent_old.agent.AgentConversationRegistryService;
import org.zexnocs.teanekoagent_old.agent.AgentRuntimeService;
import org.zexnocs.teanekoagent_old.agent.prompt.AgentRequestContext;
import org.zexnocs.teanekoagent_old.memory.AgentMemoryQueryService;
import org.zexnocs.teanekoagent_old.personality.learning.PersonalityLearningService;
import org.zexnocs.teanekoagent_old.response.*;
import org.zexnocs.teanekoagent_old.sender.AgentConversationSendData;
import org.zexnocs.teanekoagent_old.sender.AgentMemoryWriteSendData;
import org.zexnocs.teanekoagent_old.sender.AgentPersonalityCorrectionSendData;
import org.zexnocs.teanekoapp.client.api.IClient;
import org.zexnocs.teanekoapp.sender.api.ISendData;
import org.zexnocs.teanekocore.event.interfaces.IEvent;
import org.zexnocs.teanekocore.event.interfaces.IEventService;
import org.zexnocs.teanekocore.logger.ILogger;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * TeaNeko Agent 进程内客户端实现。
 * <br>该实现直接执行 Agent 操作，并仅推送对应的 {@link AgentResponseEvent}。
 *
 * @author zExNocs
 * @date 2026/06/10
 * @since 4.4.1
 */
@Component
public class TeaNekoAgentInternalClient implements IClient {
    /** Agent 会话注册服务。 */
    private final AgentConversationRegistryService conversationRegistryService;
    /** Agent Runtime。 */
    private final AgentRuntimeService runtimeService;
    /** Agent 上下文服务。 */
    private final AgentContextService contextService;
    /** Agent 记忆服务。 */
    private final AgentMemoryQueryService memoryQueryService;
    /** Agent 人格学习服务。 */
    private final PersonalityLearningService personalityLearningService;
    /** Core 事件服务。 */
    private final IEventService eventService;
    /** JSON 映射器。 */
    private final ObjectMapper mapper;
    /** 日志服务。 */
    private final ILogger logger;

    /** 创建 TeaNeko Agent 进程内客户端。 */
    public TeaNekoAgentInternalClient(AgentConversationRegistryService conversationRegistryService,
                                     AgentRuntimeService runtimeService,
                                     AgentContextService contextService,
                                     AgentMemoryQueryService memoryQueryService,
                                     PersonalityLearningService personalityLearningService,
                                     IEventService eventService,
                                     @Qualifier("customObjectMapper") ObjectMapper mapper,
                                     ILogger logger) {
        this.conversationRegistryService = conversationRegistryService;
        this.runtimeService = runtimeService;
        this.contextService = contextService;
        this.memoryQueryService = memoryQueryService;
        this.personalityLearningService = personalityLearningService;
        this.eventService = eventService;
        this.mapper = mapper;
        this.logger = logger;
    }

    /**
     * 内置客户端不接受缺少类型信息的原始发送字符串。
     *
     * @param message 原始发送字符串
     */
    @Override
    public void send(String message) {
        throw new UnsupportedOperationException("TeaNeko Agent client only accepts structured ISendData");
    }

    /**
     * 执行 Agent 发送数据并推送响应事件。
     *
     * @param data Agent 发送数据
     */
    @Override
    public void send(ISendData<?> data) {
        try {
            if (data instanceof AgentConversationSendData conversation) {
                executeConversation(conversation);
            } else if (data instanceof AgentMemoryWriteSendData memory) {
                executeMemoryWrite(memory);
            } else if (data instanceof AgentPersonalityCorrectionSendData personality) {
                executePersonalityCorrection(personality);
            } else {
                throw new UnsupportedOperationException("Unsupported Agent send data: " + data.getClass().getName());
            }
        } catch (Exception exception) {
            logger.warn(TeaNekoAgentInternalClient.class.getSimpleName(), "Agent sender execution failed", exception);
            publish(data.getEcho(), false, null);
        }
    }

    /**
     * 将 Agent 响应 JSON 解析为响应事件。
     *
     * @param message Agent 响应 JSON
     * @return Agent 响应事件
     */
    @Override
    public IEvent<?> handle(String message) {
        return new AgentResponseEvent(mapper.readValue(message, AgentResponseData.class));
    }

    /** 执行手动对话。 */
    private void executeConversation(AgentConversationSendData data) {
        var message = data.getMessage();
        if (message == null) {
            throw new IllegalArgumentException("TeaNeko message must not be null");
        }
        var userId = message.getUserData().getUserIdInPlatform();
        var context = conversationRegistryService.getOrCreate(
                data.getConversationId(), message.getScopeId(), data.getAgentId(), userId
        );
        var output = runtimeService.handle(context, message).orElse(null);
        publish(data.getEcho(), true, new AgentConversationResponse(
                context.getConversationId(), message.getMessageId(), output
        ));
    }

    /** 执行手动记忆写入。 */
    private void executeMemoryWrite(AgentMemoryWriteSendData data) {
        var record = data.getRecord();
        if (record == null || record.getContent() == null || record.getContent().isBlank()) {
            throw new IllegalArgumentException("Agent memory content must not be blank");
        }
        if (record.getScopeId() == null || record.getScopeId().isBlank()) {
            record.setScopeId(data.getScopeId());
        }
        if (record.getAgentId() == null || record.getAgentId().isBlank()) {
            record.setAgentId(data.getAgentId());
        }
        if (record.getSubjectId() == null || record.getSubjectId().isBlank()) {
            record.setSubjectId(data.getUserId());
        }
        if (record.getSource() == null || record.getSource().isBlank()) {
            record.setSource("manual-sender");
        }
        record.setUpdatedAt(Instant.now());
        memoryQueryService.appendUserMemory(data.getScopeId(), data.getAgentId(), data.getUserId(), record);
        publish(data.getEcho(), true, new AgentMemoryWriteResponse(record));
    }

    /** 执行手动人格修正。 */
    private void executePersonalityCorrection(AgentPersonalityCorrectionSendData data) {
        var context = new AgentRequestContext(
                data.getScopeId(), data.getAgentId(), data.getUserId(), data.getConversationId()
        );
        var record = personalityLearningService.recordDelta(
                context, data.getField(), data.getContent(), data.getSource(), data.getConfidence()
        ).orElse(null);
        if (record != null && !data.getConversationId().isBlank()) {
            conversationRegistryService.find(data.getConversationId())
                    .ifPresent(contextService::resolvePersonality);
        }
        publish(data.getEcho(), record != null, new AgentPersonalityCorrectionResponse(record != null, record));
    }

    /** 推送供 ResponseService 完成 TaskFuture 的 Agent 响应事件。 */
    private void publish(String echo, boolean success, Object response) {
        var rawData = response == null ? List.<Map<String, Object>>of() : List.of(toMap(response));
        eventService.pushEvent(new AgentResponseEvent(AgentResponseData.builder()
                .success(success)
                .echo(echo)
                .rawData(rawData)
                .build()));
    }

    /** 将具体响应转换为 ResponseService 使用的原始映射。 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Object response) {
        return (Map<String, Object>) mapper.convertValue(response, Map.class);
    }
}
