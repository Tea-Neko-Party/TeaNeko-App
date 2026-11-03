package org.zexnocs.teanekoclient.web;

import org.springframework.stereotype.Component;
import org.zexnocs.teanekoapp.client.api.IClient;
import org.zexnocs.teanekocore.event.interfaces.IEvent;

/**
 * 网页平台使用已认证的类型化入口，不注册机器 WebSocket 或解释任意原始 JSON。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.7.0-l
 */
@Component
public final class WebTransport implements IClient {
    /**
     * @param message 不接受的原始协议
     * @throws UnsupportedOperationException 当绕过原持久发送边界时
     */
    @Override
    public void send(String message) {
        throw new UnsupportedOperationException("网页平台只允许有来源的类型化发送。");
    }

    /**
     * @param message 不接受的原始协议
     * @return 本接口没有原始 JSON 入站能力
     * @throws UnsupportedOperationException 当绕过认证消息入口时
     */
    @Override
    public IEvent<?> handle(String message) {
        throw new UnsupportedOperationException("网页平台只通过认证 HTTP 接收入站消息。");
    }
}
