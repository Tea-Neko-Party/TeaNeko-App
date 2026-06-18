package org.zexnocs.teanekoagent_old.sender;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.zexnocs.teanekoagent_old.client.TeaNekoAgentInternalClient;
import org.zexnocs.teanekoagent_old.memory.AgentMemoryRecord;
import org.zexnocs.teanekoagent_old.response.AgentMemoryWriteResponse;
import org.zexnocs.teanekoapp.sender.interfaces.ISenderService;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskResult;
import tools.jackson.databind.ObjectMapper;

/**
 * Agent 手动记忆写入 sender。
 *
 * @author zExNocs
 * @date 2026/06/10
 * @since 4.4.1
 */
@Component
public class AgentMemoryWriteSender extends AbstractAgentSender<AgentMemoryWriteSendData, AgentMemoryWriteResponse> {
    /** Agent 内置客户端。 */
    private final TeaNekoAgentInternalClient client;
    /** JSON 映射器。 */
    private final ObjectMapper mapper;

    /** 创建 Agent 手动记忆写入 sender。 */
    public AgentMemoryWriteSender(ISenderService senderService,
                                  TeaNekoAgentInternalClient client,
                                  @Qualifier("customObjectMapper") ObjectMapper mapper) {
        super(senderService);
        this.client = client;
        this.mapper = mapper;
    }

    /**
     * 手动写入一条用户记忆。
     *
     * @param scopeId 作用域 ID
     * @param agentId Agent ID
     * @param userId 用户 ID
     * @param record 记忆记录
     * @return 异步写入响应
     */
    public TaskFuture<ITaskResult<AgentMemoryWriteResponse>> send(String scopeId,
                                                                 String agentId,
                                                                 String userId,
                                                                 AgentMemoryRecord record) {
        return sendSingle(new AgentMemoryWriteSendData(client, mapper, scopeId, agentId, userId, record));
    }
}
