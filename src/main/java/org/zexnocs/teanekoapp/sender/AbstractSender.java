package org.zexnocs.teanekoapp.sender;

import org.zexnocs.teanekoapp.sender.api.ISendData;
import org.zexnocs.teanekoapp.sender.api.ISender;
import org.zexnocs.teanekoapp.sender.interfaces.ISenderService;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskResult;

import java.time.Duration;
import java.util.List;

/**
 * 发送器抽象类，提供了发送器的基本功能和行为的默认实现。
 * <p>发送器负责将数据转化成 {@link ISendData} 推送给 {@link ISenderService}。
 *
 * @author zExNocs
 * @date 2026/02/24
 */
public class AbstractSender<S extends ISendData<R>, R> implements ISender<S, R> {

    /// 发送服务，用于将发送数据推送给客户端，并处理响应数据
    private final ISenderService senderService;

    /// 发送数据的类型，用于提交给 senderService
    private final Class<S> sendDataClass;

    public AbstractSender(ISenderService senderService, Class<S> sendDataClass) {
        this.senderService = senderService;
        this.sendDataClass = sendDataClass;
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
    public TaskFuture<ITaskResult<List<R>>> sendWithFuture(S sendData, Duration delay, int maxRetryCount, Duration retryDelay) {
        return senderService.send(sendData, sendDataClass, delay, maxRetryCount, retryDelay);
    }
}
