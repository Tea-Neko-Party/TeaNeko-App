package org.zexnocs.teanekoapp.web;

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.zexnocs.teanekoapp.client.IClientConnectionGuard;
import org.zexnocs.teanekocore.utils.StableDigest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/**
 * 握手只保存已验证机器凭据的校验值，启用控制面或更换凭据后旧连接不能继续收发。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/05：握手兼容直接 WS 与代理 WSS，持续校验仍绑定同一机器令牌。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.8.2-l
 */
@Component
public final class WebSocketAccessGuard implements IClientConnectionGuard {
    private static final String PROOF = WebSocketAccessGuard.class.getName() + ".proof";
    private final WebCredentials credentials;

    /** @param credentials 当前成功安全配置 */
    public WebSocketAccessGuard(WebCredentials credentials) {
        this.credentials = credentials;
    }

    /**
     * 2026/11/05：传输可为 WS 或 WSS，不能将 Docker 来源当作本机身份或跳过机器令牌。
     * @param request 原握手请求
     * @param response 原响应
     * @param handler 实际客户端
     * @param attributes 仅由服务器写入的连接上下文
     * @return 是否可继续握手
     */
    @Override
    public boolean beforeHandshake(@NonNull ServerHttpRequest request, @NonNull ServerHttpResponse response,
                                   @NonNull WebSocketHandler handler, @NonNull Map<String, Object> attributes) {
        try {
            String path = request.getURI().getPath();
            String client = path.substring(path.lastIndexOf('/') + 1);
            var settings = credentials.settings();
            // 2026/11/05：内网 WS 与公开 WSS 共用机器认证，不按是否经过 TLS 代理决定令牌校验。
            if (!credentials.machine(client, request.getHeaders().getFirst("Authorization"))) {
                return false;
            }
            String secret = settings.machineTokens().get(client);
            attributes.put(PROOF, new Proof(client, settings.enabled() && secret != null
                    ? StableDigest.sha256(secret) : ""));
            return true;
        } catch (RuntimeException failure) {
            return false;
        }
    }

    /**
     * @param request 原请求
     * @param response 原响应
     * @param handler 实际客户端
     * @param exception 可选握手失败，此处没有新增资源需要释放
     */
    @Override
    public void afterHandshake(@NonNull ServerHttpRequest request, @NonNull ServerHttpResponse response,
                               @NonNull WebSocketHandler handler, @Nullable Exception exception) {}

    /**
     * @param session 当前原连接
     * @return 原证明是否仍与当前机器密钥匹配
     */
    @Override
    public boolean allows(WebSocketSession session) {
        var settings = credentials.settings();
        if (!settings.enabled()) {
            return true;
        }
        if (!(session.getAttributes().get(PROOF) instanceof Proof(String client, String hash))) {
            return false;
        }
        String secret = settings.machineTokens().get(client);
        return secret != null && MessageDigest.isEqual(hash.getBytes(StandardCharsets.US_ASCII),
                StableDigest.sha256(secret).getBytes(StandardCharsets.US_ASCII));
    }

    /**
     * 不包含原始凭据的服务器证明，浏览器或平台帧不能写入。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param client 已验证客户端
     * @param hash 本次握手的校验值，旧无认证接入为空
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    @JsonIgnoreType
    private record Proof(String client, String hash) {}
}
