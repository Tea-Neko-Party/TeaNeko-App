package org.zexnocs.teanekoapp.sender;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoapp.response.exception.ResponseEchoDuplicateException;
import org.zexnocs.teanekoapp.response.interfaces.IResponseService;
import org.zexnocs.teanekoapp.sender.api.ISendData;
import org.zexnocs.teanekoapp.sender.interfaces.ISenderService;
import org.zexnocs.teanekocore.actuator.task.TaskConfig;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskResult;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskService;
import org.zexnocs.teanekocore.event.interfaces.IEvent;
import org.zexnocs.teanekocore.event.interfaces.IEventService;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * 用于统一推送发送给客户端事件的服务。
 *
 * @author zExNocs
 * @date 2026/02/23
 * @since 4.0.8
 */
@Service
public class SenderService implements ISenderService {
    /// 反应信息服务
    private final IResponseService iResponseService;

    /// 事件服务
    private final IEventService iEventService;
    private final ITaskService iTaskService;

    @Autowired
    public SenderService(IResponseService iResponseService,
                         IEventService iEventService,
                         ITaskService iTaskService) {
        this.iResponseService = iResponseService;
        this.iEventService = iEventService;
        this.iTaskService = iTaskService;
    }

    /**
     * 使用指定的
     * {@link SentEvent}
     * 来发送信息
     *
     * @param event         包含该数据的事件
     * @param delay         发送延迟的时间，单位毫秒
     * @param maxRetryCount 最大重试次数
     * @param retryDelay    重试延迟的时间，单位毫秒
     * @return {@link TaskFuture }<{@link ITaskResult }<{@link List }<{@link R }>>>}
     * @throws ResponseEchoDuplicateException 如果 echo 已经存在于注册表中，则抛出该异常
     */
    @Override
    public <R, S extends ISendData<R>> TaskFuture<ITaskResult<List<R>>> send(IEvent<S> event, Duration delay, int maxRetryCount, Duration retryDelay) throws ResponseEchoDuplicateException {
        var sendData = event.getData();
        var echo = sendData.getEcho();
        // 尝试将 echo 转化为 UUID
        UUID key;
        try {
            key = UUID.fromString(echo);
        } catch (IllegalArgumentException e) {
            // 如果 echo 不是一个合法的 UUID，则生成一个新的 UUID 作为 key
            key = UUID.randomUUID();
        }
        // 如果是 sent event，则设置 key
        if(event instanceof SentEvent<?> sentEvent) {
            sentEvent.setTaskKey(key);
        }
        // 注册到 ResponseService 中
        iResponseService.register(echo, key, sendData);

        // 注册 task
        var config = TaskConfig.<List<R>>builder()
                .name("注册 sendData 的响应 future: %s".formatted(sendData.toSendString()))
                .callable(() -> {
                    // 推送事件
                    iEventService.pushEvent(event);
                    return null;
                })
                .delayDuration(delay)
                .expirationDuration(Duration.ofMinutes(5))
                .maxRetries(maxRetryCount)
                .retryInterval(retryDelay)
                .build();
        @SuppressWarnings("unchecked")
        var future = iTaskService.subscribeWithFuture(key, config, (Class<List<R>>) (Class<?>) List.class);
        return future.whenComplete((taskResult, throwable) -> {
            // 无论成功还是失败，都要取消 ResponseService 中的注册信息，避免内存泄漏
            iResponseService.unregister(echo);
        });
    }
}
