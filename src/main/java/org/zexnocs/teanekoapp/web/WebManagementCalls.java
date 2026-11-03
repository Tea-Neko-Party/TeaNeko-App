package org.zexnocs.teanekoapp.web;

import org.springframework.stereotype.Service;
import org.zexnocs.teanekoapp.web.client.ClientManagementDirectory;
import org.zexnocs.teanekoapp.web.client.ManagementTargets;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.file_config.interfaces.IFileConfigService;
import org.zexnocs.teanekocore.plugin.PluginRegistry;
import org.zexnocs.teanekocore.plugin.management.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

/**
 * 将真实 Web 会话映射到原管理网关，只授权本次服务器持有的上下文与本地白名单。
 * 在途能力随物理阶段释放，浏览器断开不被当作事务已回滚。
 * <br>2026/11/03：实例级资源使用独立入口，继续复用原管理网关与在途授权。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.7.0-l
 */
@Service
public final class WebManagementCalls implements ManagementAuthority {
    private final ManagementGateway gateway;
    private final ClientManagementDirectory clients;
    private final PluginRegistry plugins;
    private final IFileConfigService files;
    private final ObjectMapper mapper;
    private final Map<String, ManagementResource<?, ?>> resources;
    private final Map<UUID, ManagementContext> active = new ConcurrentHashMap<>();
    private final Semaphore slots = new Semaphore(32);

    /**
     * @param gateway 原显式授权网关
     * @param clients 原目标解析目录
     * @param plugins 原插件代次
     * @param files 本地白名单
     * @param mapper 原数据转换器
     * @param contributions 已声明的类型化资源
     */
    public WebManagementCalls(ManagementGateway gateway, ClientManagementDirectory clients, PluginRegistry plugins,
                              IFileConfigService files, ObjectMapper mapper,
                              List<ManagementResource<?, ?>> contributions) {
        this.gateway = gateway;
        this.clients = clients;
        this.plugins = plugins;
        this.files = files;
        this.mapper = mapper;
        var registered = new HashMap<String, ManagementResource<?, ?>>();
        for (var resource : contributions) {
            if (registered.putIfAbsent(resource.descriptor().key(), resource) != null) {
                throw new IllegalArgumentException("Web 管理资源标识重复。");
            }
        }
        resources = Map.copyOf(registered);
    }

    /**
     * @param client 已注册平台
     * @param account 已知账号引用
     * @param target 已知聊天引用
     * @param key 已声明资源 ID
     * @param body 只能绑定该资源明确请求类型的正文
     * @param session 当前真实会话能力
     * @return 原网关的实际业务结果
     */
    public CompletionStage<?> invoke(String client, String account, String target, String key,
                                      JsonNode body, Runnable session) {
        var resource = resources.get(key);
        if (resource == null || resource instanceof GlobalManagementResource<?, ?> || !allowed(key)) {
            throw new SecurityException("该管理资源未在本地明确开放。");
        }
        if (!body.isObject() || mapper.writeValueAsString(body).length() > 65536) {
            throw new IllegalArgumentException("管理请求必须是有界对象。");
        }
        Object request = mapper.treeToValue(body, resource.requestType());
        var platform = clients.guard(client, session);
        // 2026/11/03：目标解析之后复用同一在途授权，不复制全局管理网关。
        return clients.invoke(client, adapter -> adapter.resolve(account, target).thenCompose(resolved ->
                execute(resource, request, ManagementTargets.encode(client, resolved.scope()), platform)));
    }

    /**
     * 2026/11/03：只接受明确声明全局作用域的资源。
     * @param key 已声明实例级资源
     * @param body 该资源的明确请求
     * @param session 当前会话
     * @return 原管理网关实际阶段
     */
    public CompletionStage<?> invokeGlobal(String key, JsonNode body, Runnable session) {
        session.run();
        var resource = resources.get(key);
        if (!(resource instanceof GlobalManagementResource<?, ?>) || !allowed(key)) {
            throw new SecurityException("该实例级资源未在本地明确开放。");
        }
        if (!body.isObject() || mapper.writeValueAsString(body).length() > 65536) {
            throw new IllegalArgumentException("管理请求必须是有界对象。");
        }
        return execute(resource, mapper.treeToValue(body, resource.requestType()), "global", session);
    }

    /**
     * @param resource 原注册资源
     * @param request 原类型化请求
     * @param scope 宿主固定作用域
     * @param platform 当前会话及可选平台守卫
     * @return 实际阶段结束后才释放控制与并发槽
     */
    private CompletionStage<?> execute(ManagementResource<?, ?> resource, Object request, String scope, Runnable platform) {
        String key = resource.descriptor().key();
        if (!slots.tryAcquire()) {
            return CompletableFuture.failedStage(new IllegalStateException("管理操作正在繁忙，请稍后重试。"));
        }
        var control = ExecutionControl.create();
        ManagementContext context;
        try {
            var token = plugins.capture(resource.descriptor().pluginId());
            Runnable checked = () -> {
                platform.run();
                plugins.check(token);
                if (!allowed(key)) {
                    throw new SecurityException("管理资源授权已经撤销。");
                }
            };
            checked.run();
            context = new ManagementContext("web.local-admin", scope,
                    UUID.randomUUID(), control, checked);
            active.put(context.requestId(), context);
        } catch (RuntimeException failure) {
            control.close();
            slots.release();
            return CompletableFuture.failedStage(failure);
        }
        CompletionStage<?> physical;
        try {
            physical = Objects.requireNonNull(gateway.execute(key, context, request), "管理网关没有返回执行阶段。");
        } catch (RuntimeException failure) {
            physical = CompletableFuture.failedStage(failure);
        }
        return physical.whenComplete((ignored, failure) -> {
            active.remove(context.requestId(), context);
            control.close();
            slots.release();
        }).toCompletableFuture().minimalCompletionStage();
    }

    /**
     * @param context 必须是本入口本次持有的同一对象
     * @param resource 原网关的固定声明
     * @return 明确通过或拒绝，不根据字符串主体自动授予权限
     */
    @Override
    public CompletionStage<Void> authorize(ManagementContext context, ManagementResource.Descriptor resource) {
        try {
            if (active.get(context.requestId()) != context || !allowed(resource.key())) {
                throw new SecurityException("未持有当前管理调用授权。");
            }
            context.checkActive();
            return CompletableFuture.completedStage(null);
        } catch (RuntimeException failure) {
            return CompletableFuture.failedStage(failure);
        }
    }

    /**
     * @param key 原资源键
     * @return 当前本地明确允许
     */
    private boolean allowed(String key) {
        return files.requireCurrent(WebSecurityFileConfig.class).snapshot().managementResources().contains(key);
    }
}
