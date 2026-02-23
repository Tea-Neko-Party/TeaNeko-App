package org.zexnocs.teanekoapp.response.interfaces;

import org.zexnocs.teanekoapp.response.exception.ResponseEchoDuplicateException;
import org.zexnocs.teanekoapp.sender.api.ISendData;
import org.zexnocs.teanekoapp.sender.interfaces.ISenderService;

import java.util.UUID;

/**
 * 用于接收从 client 的响应信息。
 * 该类不应该由外部直接调用，而应该通过 {@link ISenderService} 来注册 future。
 *
 * @see org.zexnocs.teanekoapp.sender.interfaces.ISenderService
 * @see org.zexnocs.teanekoapp.sender.SentEvent
 * @author zExNocs
 * @date 2026/02/22
 * @since 4.0.8
 */
public interface IResponseService {
    /**
     * 注册一个 future 来处理客户端的响应信息。
     *
     * @param echo     用于标识发送信息的唯一键，通常是一个 UUID。
     * @param key      在 taskService 中注册的 key，通常与 echo 中的 key 保持一致。
     * @param sendData 要发送的数据
     * @throws ResponseEchoDuplicateException 如果echo已经存在于
     */
    void register(String echo, UUID key, ISendData<?> sendData) throws ResponseEchoDuplicateException;

    /**
     * 删除一个 echo 的注册信息，通常在 future 完成后调用。
     * @param echo 要删除的 echo
     */
    void unregister(String echo);
}
