package org.zexnocs.teanekoapp.client;

import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * 机器连接授权端口，将握手证明与后续收发复核绑定，具体安全策略由宿主贡献。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.7.0-l
 */
public interface IClientConnectionGuard extends HandshakeInterceptor {
    /**
     * @param session 原连接及宿主写入的证明
     * @return 此刻是否仍允许实际收发
     */
    boolean allows(WebSocketSession session);
}
