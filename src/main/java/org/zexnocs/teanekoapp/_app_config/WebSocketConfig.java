package org.zexnocs.teanekoapp._app_config;

import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.zexnocs.teanekoapp.client.TeaNekoClientScanner;

/**
 * websocket 配置类。
 *
 * @author zExNocs
 * @date 2026/02/26
 * @since 4.0.9
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    private final TeaNekoClientScanner teaNekoClientScanner;

    public WebSocketConfig(TeaNekoClientScanner teaNekoClientScanner) {
        this.teaNekoClientScanner = teaNekoClientScanner;
    }

    /**
     * 注册 WebSocket 处理器。
     * <p> 将所有 websocket 客户端注册到 {@code "/ws/{value}"} 路径下。
     */
    @Override
    public void registerWebSocketHandlers(@NonNull WebSocketHandlerRegistry registry) {
        teaNekoClientScanner.getHandlerMap().forEach((k, v) -> {
            if(v.second() instanceof WebSocketHandler handler) {
                registry.addHandler(handler, "/ws/" + k)
                        .setAllowedOrigins("*");
            }
        });
    }
}
