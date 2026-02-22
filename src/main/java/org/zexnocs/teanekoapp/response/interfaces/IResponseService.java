package org.zexnocs.teanekoapp.response.interfaces;

import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskResult;

import java.util.List;

/**
 * 用于接收从 client 的响应信息。
 * 用于发送信息时同时注册一个 future 来处理客户端的响应信息。
 * 发送流程：
 * 准备好发送的信息
 * → 提交给 {@link org.zexnocs.teanekoapp.sender.interfaces.ISenderService} 发送信息并注册到 {@link IResponseService} 中
 * → 推送 SentEvent 事件
 * → 客户端响应信息后触发 {@link org.zexnocs.teanekoapp.response.ResponseEvent} 事件
 * → {@link IResponseService} 监听到事件后处理响应信息
 *
 * @author zExNocs
 * @date 2026/02/22
 */
public interface IResponseService {
    /**
     * 注册一个 future 来处理客户端的响应信息。
     */
    <T> TaskFuture<ITaskResult<List<T>>> register();
}
