package org.zexnocs.teanekoapp.web;

import org.apache.catalina.connector.Connector;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.URI;

/**
 * 管理机器 WebSocket 的代理监听端口与公开连接地址，认证仍由原握手守卫执行。
 * 独立监听仅绑定本机，供 HTTPS 代理转发，不直接暴露明文管理接口。
 *
 * 修改记录：<br>
 * 2026/11/05：支持独立代理端口并向客户端管理页提供实际连接信息。
 *
 * @author zExNocs
 * @date 2026/11/05
 * @since 4.8.2-l
 * @version 4.8.2-l
 */
@Component
public final class WebSocketEndpoints implements WebServerFactoryCustomizer<TomcatServletWebServerFactory> {
    private final Environment environment;
    private final int port;
    private final String publicOrigin;

    /**
     * @param environment 当前生效环境，用于取得主服务的实际端口
     * @param port 独立本机代理端口，零表示共用主端口
     * @param publicOrigin 可选的公开 WSS 来源，留空继承控制台 HTTPS 来源
     * @throws IllegalArgumentException 当端口或公开来源格式无效时
     */
    public WebSocketEndpoints(Environment environment, @Value("${tea-neko.websocket.port:0}") int port,
                              @Value("${tea-neko.websocket.public-origin:}") String publicOrigin) {
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("WebSocket 代理端口应为 0 至 65535 的整数。");
        }
        if (!publicOrigin.isBlank()) {
            var uri = URI.create(publicOrigin);
            if (!"wss".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null
                    || !(uri.getPath().isEmpty() || uri.getPath().equals("/"))
                    || uri.getPort() == 0 || uri.getPort() > 65535) {
                throw new IllegalArgumentException("WebSocket 公开来源须为 wss://主机[:端口]，不能包含路径或凭据。");
            }
        }
        this.environment = environment;
        this.port = port;
        this.publicOrigin = publicOrigin.replaceFirst("/$", "");
    }

    /**
     * 2026/11/05：额外连接器复用原路由和认证，仅用于可信本机代理，不改变主服务监听。
     * @param factory 原 Tomcat 服务工厂
     * @throws IllegalArgumentException 当独立端口与主端口冲突时
     */
    @Override
    public void customize(@NonNull TomcatServletWebServerFactory factory) {
        if (port == 0) {
            return;
        }
        if (port == factory.getPort()) {
            throw new IllegalArgumentException("独立 WebSocket 代理端口不能与 server.port 相同；共用时请填写 0。");
        }
        var connector = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
        connector.setPort(port);
        connector.setProperty("address", "127.0.0.1");
        factory.addAdditionalConnectors(connector);
    }

    /**
     * @return 独立代理端口或主服务实际端口
     */
    public int backendPort() {
        return port == 0 ? environment.getRequiredProperty("local.server.port", Integer.class) : port;
    }

    /**
     * @param client 服务端目录内的客户端标识
     * @param consoleOrigin 已校验的控制台 HTTPS 来源
     * @return 不带令牌的公开连接地址
     */
    public URI address(String client, String consoleOrigin) {
        String origin = publicOrigin.isBlank() ? "wss" + consoleOrigin.substring("https".length()) : publicOrigin;
        return URI.create(origin + "/ws/" + client);
    }
}
