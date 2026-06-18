package org.zexnocs.teanekoagent_old.sender;

import org.zexnocs.teanekoapp.sender.AbstractSender;
import org.zexnocs.teanekoapp.sender.api.ISendData;
import org.zexnocs.teanekoapp.sender.interfaces.ISenderService;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.actuator.task.TaskResult;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskResult;

import java.time.Duration;

/**
 * Agent sender 基类。
 * <br>将 App sender 的列表响应转换为 Agent 单结果响应，同时保留 TaskFuture 异步链路。
 *
 * @param <S> Agent 发送数据类型
 * @param <R> Agent 响应类型
 * @author zExNocs
 * @date 2026/06/10
 * @since 4.4.1
 */
public abstract class AbstractAgentSender<S extends ISendData<R>, R> extends AbstractSender<S, R> {
    /**
     * 创建 Agent sender。
     *
     * @param senderService App sender 服务
     */
    protected AbstractAgentSender(ISenderService senderService) {
        super(senderService);
    }

    /**
     * 推送 Agent sender 事件并异步获取单个响应。
     *
     * @param data Agent 发送数据
     * @return 异步任务结果
     */
    protected TaskFuture<ITaskResult<R>> sendSingle(S data) {
        return sendWithFuture(new AgentSentEvent<>(data), Duration.ZERO, 0, Duration.ZERO)
                .thenApply(result -> new TaskResult<>(
                        result.isSuccess(),
                        result.getResult() == null || result.getResult().isEmpty()
                                ? null
                                : result.getResult().getFirst()
                ));
    }
}
