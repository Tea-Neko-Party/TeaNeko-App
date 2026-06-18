package org.zexnocs.teanekoagent_old.sender;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.zexnocs.teanekoagent_old.client.TeaNekoAgentInternalClient;
import org.zexnocs.teanekoagent_old.response.AgentPersonalityCorrectionResponse;
import org.zexnocs.teanekoapp.sender.interfaces.ISenderService;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskResult;
import tools.jackson.databind.ObjectMapper;

/**
 * Agent 手动人格修正 sender。
 *
 * @author zExNocs
 * @date 2026/06/10
 * @since 4.4.1
 */
@Component
public class AgentPersonalityCorrectionSender extends AbstractAgentSender<AgentPersonalityCorrectionSendData, AgentPersonalityCorrectionResponse> {
    /** Agent 内置客户端。 */
    private final TeaNekoAgentInternalClient client;
    /** JSON 映射器。 */
    private final ObjectMapper mapper;

    /** 创建 Agent 手动人格修正 sender。 */
    public AgentPersonalityCorrectionSender(ISenderService senderService,
                                            TeaNekoAgentInternalClient client,
                                            @Qualifier("customObjectMapper") ObjectMapper mapper) {
        super(senderService);
        this.client = client;
        this.mapper = mapper;
    }

    /**
     * 手动提交人格修正。
     *
     * @return 异步人格修正响应
     */
    public TaskFuture<ITaskResult<AgentPersonalityCorrectionResponse>> send(String scopeId,
                                                                            String agentId,
                                                                            String userId,
                                                                            String conversationId,
                                                                            String field,
                                                                            String content,
                                                                            String source,
                                                                            double confidence) {
        return sendSingle(new AgentPersonalityCorrectionSendData(
                client, mapper, scopeId, agentId, userId, conversationId,
                field, content, source, confidence
        ));
    }
}
