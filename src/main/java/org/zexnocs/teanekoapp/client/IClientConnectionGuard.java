package org.zexnocs.teanekoapp.client;

import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * 机器连接授权端口，将握手证明与后续收发复核绑定，具体安全策略由宿主贡献。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/07：增加真实连接登记与关闭通知，使插件停用能够等待自有连接释放。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.9.0-l
 */
public interface IClientConnectionGuard extends HandshakeInterceptor {
    /**
     * @param session 原连接及宿主写入的证明
     * @return 此刻是否仍允许实际收发
     */
    boolean allows(WebSocketSession session);

    /**
     * 已通过全部守卫后登记真实连接，不接管借用客户端实例。
     * @param session 实际受保护连接
     */
    default void connected(WebSocketSession session) {
    }

    /**
     * 实际关闭回调完成后释放宿主登记。
     * @param session 原连接
     */
    default void disconnected(WebSocketSession session) {
    }
}
