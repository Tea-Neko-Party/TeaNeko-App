package org.zexnocs.teanekoapp.sender.api;

import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskResult;

import java.time.Duration;
import java.util.List;

/**
 * 发送器接口，用于定义发送器的基本功能和行为。
 * <p>发送器负责将数据转化成 {@link ISendData} 推送给 {@link org.zexnocs.teanekoapp.sender.interfaces.ISenderService}。
 *
 * @see ISendData
 * @see org.zexnocs.teanekoapp.sender.interfaces.ISenderService
 * @param <S> 发送数据类型，必须实现 {@link ISendData} 接口
 * @param <R> 响应数据类型，发送器发送数据后可能会收到响应数据
 * @author zExNocs
 * @date 2026/02/24
 * @since 4.0.9
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
     * 发送信息，并返回 future 来允许处理响应信息。
     * 不进行重试，默认重试次数为 0。
     *
     * @param sendData      要发送的数据
     * @param delay         发送延迟的时间
     * @return {@link TaskFuture }<{@link ITaskResult }<{@link List }<{@link R }>>>
     */
    default TaskFuture<ITaskResult<List<R>>> sendWithFuture(S sendData, Duration delay) {
        return sendWithFuture(sendData, delay, 0, Duration.ZERO);
    }

    /**
     * 发送信息，并返回 future 来允许处理响应信息。
     * 默认不 delay、不进行重试，默认重试次数为 0。
     *
     * @param sendData      要发送的数据
     * @return {@link TaskFuture }<{@link ITaskResult }<{@link List }<{@link R }>>>
     */
    default TaskFuture<ITaskResult<List<R>>> sendWithFuture(S sendData) {
        return sendWithFuture(sendData, Duration.ZERO, 0, Duration.ZERO);
    }

    /**
     * 发送信息，并返回 future 来允许处理响应信息。
     * 默认不 delay>
     *
     * @param sendData      要发送的数据
     * @param maxRetryCount 最大重试次数
     * @param retryDelay    重试延迟的时间
     * @return {@link TaskFuture }<{@link ITaskResult }<{@link List }<{@link R }>>>
     */
    default TaskFuture<ITaskResult<List<R>>> sendWithFuture(S sendData, int maxRetryCount, Duration retryDelay) {
        return sendWithFuture(sendData, Duration.ZERO, maxRetryCount, retryDelay);
    }

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
     * 发送信息，并返回 future 来允许处理响应信息。
     * 不进行重试，默认重试次数为 0。
     *
     * @param sendData      要发送的数据
     * @param delay         发送延迟的时间
     */
    default void send(S sendData, Duration delay) {
        send(sendData, delay, 0, Duration.ZERO);
    }

    /**
     * 发送信息，并返回 future 来允许处理响应信息。
     * 默认不 delay、不进行重试，默认重试次数为 0。
     *
     * @param sendData      要发送的数据
     */
    default void send(S sendData) {
        send(sendData, Duration.ZERO, 0, Duration.ZERO);
    }

    /**
     * 发送信息，并返回 future 来允许处理响应信息。
     * 默认不 delay>
     *
     * @param sendData      要发送的数据
     * @param maxRetryCount 最大重试次数
     * @param retryDelay    重试延迟的时间
     */
    default void send(S sendData, int maxRetryCount, Duration retryDelay) {
        send(sendData, Duration.ZERO, maxRetryCount, retryDelay);
    }
}
