package org.zexnocs.teanekoclient.agent;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.springframework.stereotype.Component;
import org.zexnocs.teanekoapp.client.api.IClient;
import org.zexnocs.teanekoapp.sender.api.ISendData;
import org.zexnocs.teanekoclient.agent.response.*;
import org.zexnocs.teanekoclient.agent.sender.AgentSendData;
import org.zexnocs.teanekoclient.agent.service.AgentOperations;
import org.zexnocs.teanekocore.actuator.execution.ExecutionStoppedException;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskService;
import org.zexnocs.teanekocore.event.interfaces.IEvent;
import org.zexnocs.teanekocore.event.interfaces.IEventService;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

/**
 * 进程内异步客户端，只发布一个标准响应，不构造普通消息或提升用户权限。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.4.1
 * @version 4.8.0-l
 */
@Component
public final class TeaNekoAgentInternalClient implements IClient {
    private final AgentOperations operations;
    private final IEventService events;
    private final ITaskService tasks;
    private final ObjectMapper mapper;
    /**
     * 借用原业务服务与事件/任务回执设施，不启动任何模型或外部连接。
     * @param operations 三类明确业务
     * @param events 原事件总线
     * @param tasks 原任务回执
     * @param mapper DTO 映射器
     */
    public TeaNekoAgentInternalClient(AgentOperations operations, IEventService events, ITaskService tasks, ObjectMapper mapper) {
        this.operations = operations; this.events = events; this.tasks = tasks; this.mapper = mapper;
    }
    /**
     * 拒绝没有原生来源的字符串操作。
     * @param message 原始字符串
     * @throws UnsupportedOperationException 总是拒绝
     */
    @Override public void send(String message) { throw new UnsupportedOperationException("内部 Agent 只接受具有真实原生来源的结构化操作。"); }
    /**
     * 原 SentEvent 只负责启动，业务阶段结束后通过唯一响应完成原 TaskFuture。
     * @param data 已登记 echo 的结构化操作
     * @throws UnsupportedOperationException 当操作不属于三类 Agent 数据时
     */
    @Override public void send(ISendData<?> data) {
        if (!(data instanceof AgentSendData<?> operation)) { throw new UnsupportedOperationException("内部 Agent 不支持此发送数据类型。"); }
        CompletionStage<?> stage;
        try {
            stage = switch (operation.getKind()) {
                case CONVERSATION -> operations.conversation(operation.getSource(), operation.getParent());
                case MEMORY_WRITE -> operations.memory(operation.getSource(), operation.getParent());
                case PERSONALITY_CORRECTION -> operations.correction(operation.getSource(), operation.getField(), operation.getParent());
            };
        } catch (RuntimeException failure) { stage = CompletableFuture.failedStage(failure); }
        stage.whenComplete((result, failure) -> publish(operation, result, failure));
    }
    /**
     * 仅解析内部响应，不接收或执行 JSON 操作请求。
     * @param message 响应 JSON
     * @return 唯一 Agent 响应事件类型
     */
    @Override public IEvent<?> handle(String message) { return new AgentResponseEvent(mapper.readValue(message, AgentResponseData.class)); }
    /**
     * 显式返回业务终态，响应发布故障直接终止原关联任务，避免等待五分钟或补发副作用。
     * @param data 原结构化操作
     * @param result 实际业务 DTO
     * @param failure 可选内部异常，不能进入响应或日志
     */
    private void publish(AgentSendData<?> data, Object result, Throwable failure) {
        try {
            boolean success = failure == null;
            if (failure != null) {
                var cause = failure;
                while (cause instanceof CompletionException && cause.getCause() != null) { cause = cause.getCause(); }
                var status = cause instanceof ExecutionStoppedException ? "STOPPED" : "FAILED";
                result = switch (data.getKind()) {
                    case CONVERSATION -> new AgentConversationResponse(null, status, List.of());
                    case MEMORY_WRITE -> new AgentMemoryWriteResponse(status, null);
                    case PERSONALITY_CORRECTION -> new AgentPersonalityCorrectionResponse(status, false);
                };
            } else {
                success = switch (result) {
                    case AgentConversationResponse value -> value.status().equals("SUCCESS");
                    case AgentMemoryWriteResponse value -> Set.of("STORED", "DUPLICATE").contains(value.status());
                    case AgentPersonalityCorrectionResponse value -> Set.of("STORED", "DUPLICATE").contains(value.status());
                    default -> false;
                };
            }
            Map<String, Object> body = mapper.convertValue(result, new TypeReference<>() {
            });
            var event = new AgentResponseEvent(AgentResponseData.builder().success(success).echo(data.getEcho()).rawData(List.of(body)).build());
            events.pushEventWithFuture(event)
                    .asStage().whenComplete((ack, error) -> {
                        if (error != null || ack == null || !ack.isSuccess() || event.isCancelled()) { failResponse(data); }
                    });
        } catch (RuntimeException unavailable) { failResponse(data); }
    }
    /**
     * 不重发操作，明确结束无法返回响应的原任务。
     * @param data 原 echo
     */
    private void failResponse(AgentSendData<?> data) {
        tasks.forceCompleteExceptionally(UUID.fromString(data.getEcho()), new IllegalStateException("内部 Agent 响应事件未完成，操作结果不能由此推断或重试。"));
    }
}
