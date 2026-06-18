package org.zexnocs.teanekoagent_old.sender;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.zexnocs.teanekoagent_old.client.TeaNekoAgentInternalClient;
import org.zexnocs.teanekoagent_old.response.AgentConversationResponse;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekoapp.sender.interfaces.ISenderService;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskResult;
import tools.jackson.databind.ObjectMapper;

/**
 * Agent 手动对话 sender。
 *
 * @author zExNocs
 * @date 2026/06/10
 * @since 4.4.1
 */
@Component
public class AgentConversationSender extends AbstractAgentSender<AgentConversationSendData, AgentConversationResponse> {
    /** Agent 内置客户端。 */
    private final TeaNekoAgentInternalClient client;

    /** JSON 映射器。 */
    private final ObjectMapper mapper;

    /** 创建 Agent 手动对话 sender。 */
    public AgentConversationSender(ISenderService senderService,
                                   TeaNekoAgentInternalClient client,
                                   @Qualifier("customObjectMapper") ObjectMapper mapper) {
        super(senderService);
        this.client = client;
        this.mapper = mapper;
    }

    /**
     * 使用默认会话键与默认 Agent 处理 TeaNeko 消息。
     *
     * @param message TeaNeko 消息
     * @return 异步 Agent 响应
     */
    public TaskFuture<ITaskResult<AgentConversationResponse>> send(ITeaNekoMessageData message) {
        return send(message, defaultConversationId(message), "");
    }

    /**
     * 处理一条 TeaNeko 消息。
     *
     * @param message TeaNeko 消息
     * @param conversationId 会话 ID
     * @param agentId Agent ID；空值使用配置默认值
     * @return 异步 Agent 响应
     */
    public TaskFuture<ITaskResult<AgentConversationResponse>> send(ITeaNekoMessageData message,
                                                                   String conversationId,
                                                                   String agentId) {
        if (message == null) {
            throw new IllegalArgumentException("TeaNeko message must not be null");
        }
        return sendSingle(new AgentConversationSendData(client, mapper, conversationId, agentId, message));
    }

    /** 根据消息来源生成稳定会话键。 */
    private static String defaultConversationId(ITeaNekoMessageData message) {
        return "%s:%s:%s".formatted(
                message.getClient().getClientId(),
                message.getScopeId(),
                message.getUserData().getUserIdInPlatform()
        );
    }
}
