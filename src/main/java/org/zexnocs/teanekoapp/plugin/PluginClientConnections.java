package org.zexnocs.teanekoapp.plugin;

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.zexnocs.teanekoapp.client.IClientConnectionGuard;
import org.zexnocs.teanekoapp.client.TeaNekoClientScanner;
import org.zexnocs.teanekocore.plugin.PluginRegistry;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * 将插件提供的客户端连接绑定到原插件代次，停用时关闭并等待真实断连回调。
 * <br>2026/11/07：握手、实际收发和后台连接释放共用同一归属证明。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
@Component
public final class PluginClientConnections implements IClientConnectionGuard {
    private static final String PROOF = PluginClientConnections.class.getName() + ".proof";
    private final TeaNekoClientScanner clients;
    private final PluginRegistry plugins;
    private final Map<String, Connection> connections = new HashMap<>();

    /**
     * 借用已发布客户端目录与插件代次，不扫描握手处理器。
     * @param clients 客户端目录
     * @param plugins 插件目录
     */
    public PluginClientConnections(TeaNekoClientScanner clients, PluginRegistry plugins) {
        this.clients = clients;
        this.plugins = plugins;
    }

    /**
     * 为实际路由生成服务器持有的代次证明。
     * @param request 原握手请求
     * @param response 原响应
     * @param handler 已登记处理器
     * @param attributes 服务器连接属性
     * @return 当前客户端与所属插件是否可用
     */
    @Override
    public boolean beforeHandshake(@NonNull ServerHttpRequest request, @NonNull ServerHttpResponse response,
                                    @NonNull WebSocketHandler handler, @NonNull Map<String, Object> attributes) {
        String path = request.getURI().getPath();
        String id = path.substring(path.lastIndexOf('/') + 1);
        if (clients.getPair(id) == null) {
            return false;
        }
        String owner = clients.pluginOwner(id);
        try {
            attributes.put(PROOF, new Proof(id, owner, owner == null ? null : plugins.capture(owner)));
            return true;
        } catch (IllegalStateException failure) {
            return false;
        }
    }

    /**
     * 握手结束时尚未接管网络连接，不提前登记资源。
     * @param request 原请求
     * @param response 原响应
     * @param handler 原处理器
     * @param exception 可选握手失败
     */
    @Override
    public void afterHandshake(@NonNull ServerHttpRequest request, @NonNull ServerHttpResponse response,
                               @NonNull WebSocketHandler handler, @Nullable Exception exception) {
    }

    /**
     * 真实入站和发送都重新核验，不让停用再启用恢复旧连接资格。
     * @param session 实际连接
     * @return 原插件代次仍然有效时为真
     */
    @Override
    public boolean allows(WebSocketSession session) {
        if (!(session.getAttributes().get(PROOF) instanceof Proof(
            String client, String owner, PluginRegistry.Token token
        ))) {
            return false;
        }
        if (clients.getPair(client) == null
                || !java.util.Objects.equals(owner, clients.pluginOwner(client))) {
            return false;
        }
        try {
            if (token != null) {
                plugins.check(token);
            }
            return true;
        } catch (IllegalStateException failure) {
            return false;
        }
    }

    /**
     * 与停止快照使用同一锁，覆盖停用和建立连接之间的竞争。
     * @param session 已通过其他守卫的真实连接
     */
    @Override
    public synchronized void connected(WebSocketSession session) {
        if (!allows(session)) {
            throw new SecurityException("插件客户端资格已经失效。");
        }
        if (session.getAttributes().get(PROOF) instanceof Proof proof && proof.owner() != null) {
            if (connections.putIfAbsent(session.getId(), new Connection(proof.owner(), session,
                    new CompletableFuture<>())) != null) {
                throw new IllegalStateException("插件客户端连接标识重复。");
            }
        }
    }

    /**
     * 在原客户端关闭回调结束后确认资源释放。
     * @param session 实际已关闭连接
     */
    @Override
    public void disconnected(WebSocketSession session) {
        Connection connection;
        synchronized (this) {
            connection = connections.remove(session.getId());
        }
        if (connection != null) {
            connection.closed().complete(null);
        }
    }

    /**
     * 宿主撤销插件门禁后关闭其真实连接，不关闭其他客户端或共享处理器。
     * @param pluginId 已撤销资格的插件
     * @return 真实断连完成的阶段
     */
    public CompletionStage<Void> stop(String pluginId) {
        final java.util.List<Connection> owned;
        synchronized (this) {
            owned = connections.values().stream().filter(value -> value.owner().equals(pluginId)).toList();
        }
        var completion = new java.util.ArrayList<CompletableFuture<Void>>();
        for (var connection : owned) {
            try {
                connection.session().close(CloseStatus.POLICY_VIOLATION);
                completion.add(connection.closed());
            } catch (IOException failure) {
                // 2026/11/07：一条连接关闭失败仍收尾其余连接，失败连接保留以供显式重试。
                completion.add(CompletableFuture.failedFuture(failure));
            }
        }
        return CompletableFuture.allOf(completion.toArray(CompletableFuture[]::new));
    }

    /**
     * 服务器写入的归属证明，不接受客户端帧中的身份字段。
     * @param client 已注册客户端
     * @param owner 可选所属插件
     * @param token 可选插件代次
     * @author zExNocs
     * @date 2026/11/07
     * @since 4.9.0-l
     * @version 4.9.0-l
     */
    @JsonIgnoreType
    private record Proof(String client, @Nullable String owner, PluginRegistry.@Nullable Token token) {
    }

    /**
     * 当前插件实际拥有的连接及其物理终态。
     * @param owner 插件标识
     * @param session 实际连接
     * @param closed 原客户端关闭回调的完成信号
     * @author zExNocs
     * @date 2026/11/07
     * @since 4.9.0-l
     * @version 4.9.0-l
     */
    private record Connection(String owner, WebSocketSession session, CompletableFuture<Void> closed) {
    }
}
