package org.zexnocs.teanekoapp._app_config;

import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.zexnocs.teanekoapp.client.GuardedClientWebSocketHandler;
import org.zexnocs.teanekoapp.client.IClientConnectionGuard;
import org.zexnocs.teanekoapp.client.TeaNekoClientScanner;

import java.util.List;

/**
 * 注册客户端 WebSocket 与宿主连接授权策略。
 * <br>2026/11/03：增加握手证明和真实收发守卫，控制面启用后旧未认证连接不能沿用。
 *
 * @author zExNocs
 * @date 2026/02/26
 * @since 4.0.9
 * @version 4.7.0-l
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    private final TeaNekoClientScanner teaNekoClientScanner;
    private final List<IClientConnectionGuard> guards;

    /**
     * 2026/11/03：策略由 Spring 贡献，保持平台实现与 Web 认证解耦。
     * @param teaNekoClientScanner 已启用平台
     * @param guards 当前宿主连接策略
     */
    public WebSocketConfig(TeaNekoClientScanner teaNekoClientScanner, List<IClientConnectionGuard> guards) {
        this.teaNekoClientScanner = teaNekoClientScanner;
        this.guards = List.copyOf(guards);
    }

    /**
     * 注册 WebSocket 处理器。
     * <p> 将所有 websocket 客户端注册到 {@code "/ws/{value}"} 路径下。
     * 2026/11/03：机器来源可无浏览器 Origin，但必须经过独立机器凭据及持续收发守卫。
     * @param registry 原 WebSocket 路由目录
     */
    @Override
    public void registerWebSocketHandlers(@NonNull WebSocketHandlerRegistry registry) {
        teaNekoClientScanner.getHandlerMap().forEach((k, v) -> {
            if (v.second().getClient() instanceof WebSocketHandler handler) {
                registry.addHandler(new GuardedClientWebSocketHandler(handler, guards), "/ws/" + k)
                        .addInterceptors(guards.toArray(IClientConnectionGuard[]::new))
                        .setAllowedOrigins("*");
            }
        });
    }
}
