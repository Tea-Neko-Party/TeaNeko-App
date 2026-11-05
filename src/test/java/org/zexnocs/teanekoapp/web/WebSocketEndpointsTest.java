package org.zexnocs.teanekoapp.web;

import org.junit.jupiter.api.Test;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 核对独立代理端口、公开 WSS 地址和本机监听限制，不启动真实应用。
 *
 * 修改记录：<br>
 * 2026/11/05：覆盖任意开发 HTTPS 端口及独立公开 WSS 端口，后端 WS 端口保持分离。
 *
 * @author zExNocs
 * @date 2026/11/05
 * @since 4.8.2-l
 * @version 4.8.2-l
 */
class WebSocketEndpointsTest {
    /**
     * 验证独立端口绑定、主端口冲突和公开来源校验，防止界面展示不能使用的连接地址。
     * 2026/11/05：公开 WSS 可使用独立端口，也可跟随任意开发 HTTPS 端口，不固定为 5173。
     */
    @Test
    void separatesPublicTlsPortFromLoopbackProxyAndRejectsInvalidSettings() {
        var environment = new MockEnvironment().withProperty("local.server.port", "6691");
        var factory = new TomcatServletWebServerFactory(6691);
        var endpoints = new WebSocketEndpoints(environment, 6692, "wss://bot.example:7443/");
        endpoints.customize(factory);
        var connector = factory.getAdditionalConnectors().getFirst();
        assertEquals(6692, connector.getPort());
        assertEquals("127.0.0.1", assertInstanceOf(java.net.InetAddress.class,
                connector.getProperty("address")).getHostAddress());
        assertEquals(6692, endpoints.backendPort());
        assertEquals("wss://bot.example:7443/ws/onebot", endpoints.address("onebot", "https://console.example").toString());
        var inherited = new WebSocketEndpoints(environment, 0, "");
        assertEquals(6691, inherited.backendPort());
        assertEquals("wss://localhost:5443/ws/onebot", inherited.address("onebot", "https://localhost:5443").toString());
        assertThrows(IllegalArgumentException.class,
                () -> new WebSocketEndpoints(environment, 6691, "").customize(factory));
        assertThrows(IllegalArgumentException.class,
                () -> new WebSocketEndpoints(environment, -1, ""));
        assertThrows(IllegalArgumentException.class,
                () -> new WebSocketEndpoints(environment, 0, "wss://bot.example/ws/onebot"));
        assertThrows(IllegalArgumentException.class,
                () -> new WebSocketEndpoints(environment, 0, "ws://bot.example"));
    }
}
