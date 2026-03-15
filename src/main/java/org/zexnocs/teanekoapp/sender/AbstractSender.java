package org.zexnocs.teanekoapp.sender;

import org.zexnocs.teanekoapp.sender.api.ISendData;
import org.zexnocs.teanekoapp.sender.api.ISender;
import org.zexnocs.teanekoapp.sender.interfaces.ISenderService;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskResult;
import org.zexnocs.teanekocore.event.interfaces.IEvent;

import java.time.Duration;
import java.util.List;

/**
 * 发送器抽象类，提供了发送器的基本功能和行为的默认实现。
 * <br>发送器负责将数据转化成 {@link ISendData} 推送给 {@link ISenderService}。
 * <br>4.3.2: 新增允许自定义事件发送
 *
 * @author zExNocs
 * @date 2026/02/24
 * @since 4.0.9
 * @version 4.3.2
 */
public class AbstractSender<S extends ISendData<R>, R> implements ISender<S, R> {

    /// 发送服务，用于将发送数据推送给客户端，并处理响应数据
    private final ISenderService senderService;

    /**
     * 构造函数，初始化发送器。
     *
     * @param senderService 发送服务实例，用于将发送数据推送给客户端，并处理响应数据
     */
    public AbstractSender(ISenderService senderService) {
        this.senderService = senderService;
    }

    /**
     * 发送信息，并返回 future 来允许处理响应信息。
     *
     * @param sendData      要发送的数据
     * @param delay         发送延迟的时间
     * @param maxRetryCount 最大重试次数
     * @param retryDelay    重试延迟的时间
     * @return {@link TaskFuture }<{@link ITaskResult }<{@link List }<{@link R }>>>
     */
    @Override
    public TaskFuture<ITaskResult<List<R>>> sendWithFuture(S sendData,
                                                           Duration delay,
                                                           int maxRetryCount,
                                                           Duration retryDelay) {
        return senderService.send(sendData, delay, maxRetryCount, retryDelay);
    }

    /**
     * 使用自定义事件发送信息，并返回 future 来允许处理响应信息。
     *
     * @param event         要发送的数据事件
     * @param delay         发送延迟的时间
     * @param maxRetryCount 最大重试次数
     * @param retryDelay    重试延迟的时间
     * @return {@link TaskFuture }<{@link ITaskResult }<{@link List }<{@link R }>>>
     */
    @Override
    public TaskFuture<ITaskResult<List<R>>> sendWithFuture(IEvent<S> event, Duration delay, int maxRetryCount, Duration retryDelay) {
        return senderService.send(event, delay, maxRetryCount, retryDelay);
    }
}
