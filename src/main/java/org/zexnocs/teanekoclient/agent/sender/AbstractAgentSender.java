package org.zexnocs.teanekoclient.agent.sender;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。
import org.zexnocs.teanekoapp.sender.AbstractSender;
import org.zexnocs.teanekoapp.sender.SentEvent;
import org.zexnocs.teanekoapp.sender.interfaces.ISenderService;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.actuator.task.TaskResult;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskResult;
import java.time.Duration;
/**
 * 复用原 Sender/echo/Response 链，不自动重试 Agent 副作用。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @param <R> 业务响应类型
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.4.1
 * @version 4.8.0-l
 */
public abstract class AbstractAgentSender<R> extends AbstractSender<AgentSendData<R>, R> {
    /**
     * 借用 App 原发送服务。
     * @param service 原发送服务
     */
    protected AbstractAgentSender(ISenderService service) { super(service); }
    /**
     * 等待原业务终态后返回单个 DTO，保留失败响应中的明确状态。
     * @param data 已固定结构化操作
     * @return 实际业务响应任务
     */
    protected TaskFuture<ITaskResult<R>> sendSingle(AgentSendData<R> data) {
        return sendWithFuture(new SentEvent<>(data), Duration.ZERO, 0, Duration.ZERO).thenApply(result ->
                new TaskResult<>(result.isSuccess(), result.getResult() == null || result.getResult().isEmpty() ? null : result.getResult().getFirst()));
    }
}
