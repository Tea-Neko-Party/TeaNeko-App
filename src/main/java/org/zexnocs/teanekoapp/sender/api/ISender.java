package org.zexnocs.teanekoapp.sender.api;

import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskResult;
import org.zexnocs.teanekocore.event.interfaces.IEvent;

import java.time.Duration;
import java.util.List;

/**
 * 发送器接口，用于定义发送器的基本功能和行为。
 * <p>发送器负责将数据转化成 {@link ISendData} 推送给 {@link org.zexnocs.teanekoapp.sender.interfaces.ISenderService}。
 * <br>4.3.2: 新增允许自定义事件发送
 *
 * @see ISendData
 * @see org.zexnocs.teanekoapp.sender.interfaces.ISenderService
 * @param <S> 发送数据类型，必须实现 {@link ISendData} 接口
 * @param <R> 响应数据类型，发送器发送数据后可能会收到响应数据
 * @author zExNocs
 * @date 2026/02/24
 * @since 4.0.9
 * @version 4.3.2
 */
public interface ISender<S extends ISendData<R>, R> {
    /**
     * 发送信息，并返回 future 来允许处理响应信息。
     *
     * @param sendData      要发送的数据
     * @param delay         发送延迟的时间
     * @param maxRetryCount 最大重试次数
     * @param retryDelay    重试延迟的时间
     * @return {@link TaskFuture }<{@link ITaskResult }<{@link List }<{@link R }>>>
     */
    TaskFuture<ITaskResult<List<R>>> sendWithFuture(S sendData, Duration delay, int maxRetryCount, Duration retryDelay);

    /**
     * 发送信息，不返回 future，适用于不需要处理响应信息的场景。
     *
     * @param sendData 要发送的数据
     * @param delay 发送延迟的时间
     * @param maxRetryCount 最大重试次数
     * @param retryDelay    重试延迟的时间
     */
    default void send(S sendData, Duration delay, int maxRetryCount, Duration retryDelay) {
        sendWithFuture(sendData, delay, maxRetryCount, retryDelay)
                .finish();
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
    TaskFuture<ITaskResult<List<R>>> sendWithFuture(IEvent<S> event, Duration delay, int maxRetryCount, Duration retryDelay);

    /**
     * 使用自定义事件发送信息，不返回 future，适用于不需要处理响应信息的场景。
     *
     * @param event         要发送的数据事件
     * @param delay         发送延迟的时间
     * @param maxRetryCount 最大重试次数
     * @param retryDelay    重试延迟的时间
     */
    default void send(IEvent<S> event, Duration delay, int maxRetryCount, Duration retryDelay) {
        sendWithFuture(event, delay, maxRetryCount, retryDelay)
                .finish();
    }
}
