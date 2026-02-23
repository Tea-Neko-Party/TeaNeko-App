package org.zexnocs.teanekoapp.sender.interfaces;

import org.zexnocs.teanekoapp.response.exception.ResponseEchoDuplicateException;
import org.zexnocs.teanekoapp.sender.api.ISendData;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskResult;

import java.time.Duration;
import java.util.List;

/**
 * 用于统一推送发送给客户端事件的服务。
 * 发送流程：
 * <p>准备好发送的信息
 * <p>→ 提交给 {@link ISenderService} 发送信息
 * <p>→ {@link ISenderService} 将信息注册给 {@link org.zexnocs.teanekoapp.response.interfaces.IResponseService}
 * <p>→ 推送 {@link org.zexnocs.teanekoapp.sender.SentEvent} 事件
 * <p>→ 客户端响应信息后触发 {@link org.zexnocs.teanekoapp.response.ResponseEvent} 事件
 * <p>→ {@link org.zexnocs.teanekoapp.response.interfaces.IResponseService} 监听到事件后处理响应信息
 *
 * @see org.zexnocs.teanekoapp.response.interfaces.IResponseService
 * @see org.zexnocs.teanekoapp.sender.SentEvent
 * @see org.zexnocs.teanekoapp.response.ResponseEvent
 * @author zExNocs
 * @date 2026/02/22
 * @since 4.0.8
 */
public interface ISenderService {
    /**
     * 发送信息，并返回 future 来允许处理响应信息。
     *
     * @see ISendData
     * @see TaskFuture
     * @see ITaskResult
     * @param <R> 响应数据类型
     * @param <S> 发送数据类型，必须实现 {@link ISendData} 接口
     * @param sendData      要发送的数据
     * @param sendDataClass 发送数据的类对象
     * @param delay         发送延迟的时间，单位毫秒
     * @param maxRetryCount 最大重试次数
     * @param retryDelay    重试延迟的时间，单位毫秒
     * @return {@link TaskFuture }<{@link ITaskResult }<{@link List }<{@link R }>>>}
     * @throws ResponseEchoDuplicateException 如果 echo 已经存在于注册表中，则抛出该异常
     */
    <R, S extends ISendData<R>> TaskFuture<ITaskResult<List<R>>> send(
            S sendData,
            Class<S> sendDataClass,
            Duration delay,
            int maxRetryCount,
            Duration retryDelay) throws ResponseEchoDuplicateException;

    /**
     * 发送信息，默认不进行重试。
     *
     * @see ISendData
     * @see TaskFuture
     * @see ITaskResult
     * @param <R> 响应数据类型
     * @param <S> 发送数据类型，必须实现 {@link ISendData} 接口
     * @param sendData      要发送的数据
     * @param sendDataClass 发送数据的类对象
     * @param delay         发送延迟的时间，单位毫秒
     * @return {@link TaskFuture }<{@link ITaskResult }<{@link List }<{@link R }>>>}
     * @throws ResponseEchoDuplicateException 如果 echo 已经存在于注册表中，则抛出该异常
     */
    default <R, S extends ISendData<R>> TaskFuture<ITaskResult<List<R>>> send(
            S sendData,
            Class<S> sendDataClass,
            Duration delay) throws ResponseEchoDuplicateException {
        return send(sendData, sendDataClass, delay, 0, Duration.ZERO);
    }

    /**
     * 发送信息，不进行重试并没有发送延迟。
     *
     * @see ISendData
     * @see TaskFuture
     * @see ITaskResult
     * @param <R> 响应数据类型
     * @param <S> 发送数据类型，必须实现 {@link ISendData} 接口
     * @param sendData      要发送的数据
     * @param sendDataClass 发送数据的类对象
     * @return {@link TaskFuture }<{@link ITaskResult }<{@link List }<{@link R }>>>}
     * @throws ResponseEchoDuplicateException 如果 echo 已经存在于注册表中，则抛出该异常
     */
    default <R, S extends ISendData<R>> TaskFuture<ITaskResult<List<R>>> send(S sendData, Class<S> sendDataClass)
            throws ResponseEchoDuplicateException{
        return send(sendData, sendDataClass, Duration.ZERO, 0, Duration.ZERO);
    }
}
