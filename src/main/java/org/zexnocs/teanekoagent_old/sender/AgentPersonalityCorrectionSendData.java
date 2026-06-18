package org.zexnocs.teanekoagent_old.sender;

import lombok.Getter;
import org.zexnocs.teanekoagent_old.client.TeaNekoAgentInternalClient;
import org.zexnocs.teanekoagent_old.response.AgentPersonalityCorrectionResponse;
import org.zexnocs.teanekoapp.sender.AbstractJsonSendData;
import tools.jackson.databind.ObjectMapper;

/**
 * Agent 手动人格修正发送数据。
 *
 * @author zExNocs
 * @date 2026/06/10
 * @since 4.4.1
 */
@Getter
public class AgentPersonalityCorrectionSendData extends AbstractJsonSendData<AgentPersonalityCorrectionResponse> {
    /** 作用域 ID。 */
    private final String scopeId;
    /** Agent ID。 */
    private final String agentId;
    /** 用户 ID。 */
    private final String userId;
    /** 会话 ID。 */
    private final String conversationId;
    /** 修正字段。 */
    private final String field;
    /** 修正内容。 */
    private final String content;
    /** 修正来源。 */
    private final String source;
    /** 修正置信度。 */
    private final double confidence;

    /** 创建手动人格修正发送数据。 */
    public AgentPersonalityCorrectionSendData(TeaNekoAgentInternalClient client,
                                              ObjectMapper mapper,
                                              String scopeId,
                                              String agentId,
                                              String userId,
                                              String conversationId,
                                              String field,
                                              String content,
                                              String source,
                                              double confidence) {
        super(client, mapper, AgentPersonalityCorrectionResponse.class);
        this.scopeId = safe(scopeId);
        this.agentId = safe(agentId);
        this.userId = safe(userId);
        this.conversationId = safe(conversationId);
        this.field = safe(field);
        this.content = safe(content);
        this.source = safe(source);
        this.confidence = Math.clamp(confidence, 0, 1);
    }

    /** 规范化可空文本。 */
    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
