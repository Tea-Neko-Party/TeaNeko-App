package org.zexnocs.teanekoagent_old.sender;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import org.zexnocs.teanekoagent_old.client.TeaNekoAgentInternalClient;
import org.zexnocs.teanekoagent_old.response.AgentConversationResponse;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekoapp.sender.AbstractJsonSendData;
import tools.jackson.databind.ObjectMapper;

/**
 * Agent 手动对话发送数据。
 *
 * @author zExNocs
 * @date 2026/06/10
 * @since 4.4.1
 */
@Getter
public class AgentConversationSendData extends AbstractJsonSendData<AgentConversationResponse> {
    /** 会话 ID。 */
    private final String conversationId;

    /** 目标 Agent ID。 */
    private final String agentId;

    /** 原生 TeaNeko 入站消息。 */
    @JsonIgnore
    private final ITeaNekoMessageData message;

    /**
     * 创建 Agent 手动对话发送数据。
     *
     * @param client 内置 Agent 客户端
     * @param mapper JSON 映射器
     * @param conversationId 会话 ID
     * @param agentId Agent ID
     * @param message 原生 TeaNeko 消息
     */
    public AgentConversationSendData(TeaNekoAgentInternalClient client,
                                     ObjectMapper mapper,
                                     String conversationId,
                                     String agentId,
                                     ITeaNekoMessageData message) {
        super(client, mapper, AgentConversationResponse.class);
        this.conversationId = conversationId == null ? "" : conversationId.trim();
        this.agentId = agentId == null ? "" : agentId.trim();
        this.message = message;
    }
}
