package org.zexnocs.teanekoapp.sender.interfaces;

/**
 * 用于统一推送发送给客户端事件的服务。
 * 发送流程：
 * 准备好发送的信息
 * → 提交给 {@link ISenderService} 发送信息并注册到 {@link org.zexnocs.teanekoapp.response.interfaces.IResponseService} 中
 * → 推送 SentEvent 事件
 * → 客户端响应信息后触发 {@link org.zexnocs.teanekoapp.response.ResponseEvent} 事件
 * → {@link org.zexnocs.teanekoapp.response.interfaces.IResponseService} 监听到事件后处理响应信息
 *
 * @author zExNocs
 * @date 2026/02/22
 */
public interface ISenderService {
}
