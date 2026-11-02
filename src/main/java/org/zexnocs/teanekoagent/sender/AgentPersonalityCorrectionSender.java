package org.zexnocs.teanekoagent.sender;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.zexnocs.teanekoagent.client.TeaNekoAgentInternalClient;
import org.zexnocs.teanekoagent.response.AgentPersonalityCorrectionResponse;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekoapp.sender.interfaces.ISenderService;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskResult;
import tools.jackson.databind.ObjectMapper;

/**
 * 管理员提交原文表达候选，沿用当前人格字段与学习边界。
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.4.1
 * @version 4.5.0-l
 */
@Component
public final class AgentPersonalityCorrectionSender extends AbstractAgentSender<AgentPersonalityCorrectionResponse> {
    private final TeaNekoAgentInternalClient client;
    private final ObjectMapper mapper;
    /**
     * 保存借用边界，不执行业务。
     * @param service 原 Sender 服务
     * @param client 进程内客户端
     * @param mapper 响应映射器
     */
    public AgentPersonalityCorrectionSender(ISenderService service, TeaNekoAgentInternalClient client, ObjectMapper mapper) { super(service); this.client = client; this.mapper = mapper; }
    /**
     * 提交独立宿主操作，不将模型内容当作原文来源。
     * @param source 真实平台消息
     * @param field 允许的表达字段
     * @return 原异步业务回执
     */
    public TaskFuture<ITaskResult<AgentPersonalityCorrectionResponse>> send(ITeaNekoMessageData source, String field) { return send(source, field, null); }
    /**
     * 派生操作继承父取消；对话继续共用父模型预算，不关闭借用父控制。
     * @param source 真实平台消息
     * @param field 允许的表达字段
     * @param parent 可选父控制
     * @return 包含实际业务终态的任务
     */
    public TaskFuture<ITaskResult<AgentPersonalityCorrectionResponse>> send(ITeaNekoMessageData source, String field, @Nullable ExecutionControl parent) {
        return sendSingle(new AgentSendData<>(client, mapper, AgentPersonalityCorrectionResponse.class, AgentSendData.Kind.PERSONALITY_CORRECTION, source, field, parent));
    }
}
