package org.zexnocs.teanekoagent_old.sender;

import lombok.Getter;
import org.zexnocs.teanekoagent_old.client.TeaNekoAgentInternalClient;
import org.zexnocs.teanekoagent_old.memory.AgentMemoryRecord;
import org.zexnocs.teanekoagent_old.response.AgentMemoryWriteResponse;
import org.zexnocs.teanekoapp.sender.AbstractJsonSendData;
import tools.jackson.databind.ObjectMapper;

/**
 * Agent 手动记忆写入发送数据。
 *
 * @author zExNocs
 * @date 2026/06/10
 * @since 4.4.1
 */
@Getter
public class AgentMemoryWriteSendData extends AbstractJsonSendData<AgentMemoryWriteResponse> {
    /** 作用域 ID。 */
    private final String scopeId;

    /** Agent ID。 */
    private final String agentId;

    /** 用户 ID。 */
    private final String userId;

    /** 待写入记忆。 */
    private final AgentMemoryRecord record;

    /** 创建手动记忆写入发送数据。 */
    public AgentMemoryWriteSendData(TeaNekoAgentInternalClient client,
                                    ObjectMapper mapper,
                                    String scopeId,
                                    String agentId,
                                    String userId,
                                    AgentMemoryRecord record) {
        super(client, mapper, AgentMemoryWriteResponse.class);
        this.scopeId = safe(scopeId);
        this.agentId = safe(agentId);
        this.userId = safe(userId);
        this.record = record;
    }

    /** 规范化可空文本。 */
    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
