package org.zexnocs.teanekoclient.agent.sender;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekoapp.sender.interfaces.ISenderService;
import org.zexnocs.teanekoclient.agent.TeaNekoAgentInternalClient;
import org.zexnocs.teanekoclient.agent.response.AgentMemoryWriteResponse;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskResult;
import tools.jackson.databind.ObjectMapper;

/**
 * 明确记忆新增只解析真实原文，不能替换主体或空间。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.4.1
 * @version 4.8.0-l
 */
@Component
public final class AgentMemoryWriteSender extends AbstractAgentSender<AgentMemoryWriteResponse> {
    private final TeaNekoAgentInternalClient client;
    private final ObjectMapper mapper;
    /**
     * 保存借用边界，不执行业务。
     * @param service 原 Sender 服务
     * @param client 进程内客户端
     * @param mapper 响应映射器
     */
    public AgentMemoryWriteSender(ISenderService service, TeaNekoAgentInternalClient client, ObjectMapper mapper) { super(service); this.client = client; this.mapper = mapper; }
    /**
     * 提交独立宿主操作，不将模型内容当作原文来源。
     * @param source 真实平台消息
     * @return 原异步业务回执
     */
    public TaskFuture<ITaskResult<AgentMemoryWriteResponse>> send(ITeaNekoMessageData source) { return send(source, null); }
    /**
     * 派生操作继承父取消；对话继续共用父模型预算，不关闭借用父控制。
     * @param source 真实平台消息
     * @param parent 可选父控制
     * @return 包含实际业务终态的任务
     */
    public TaskFuture<ITaskResult<AgentMemoryWriteResponse>> send(ITeaNekoMessageData source, @Nullable ExecutionControl parent) {
        return sendSingle(new AgentSendData<>(client, mapper, AgentMemoryWriteResponse.class, AgentSendData.Kind.MEMORY_WRITE, source, "", parent));
    }
}
