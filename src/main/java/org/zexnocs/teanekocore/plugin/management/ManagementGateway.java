package org.zexnocs.teanekocore.plugin.management;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.plugin.PluginRegistry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * 传输无关的插件管理路由；没有显式授权适配器时拒绝所有管理访问。
 * 不发布 HTTP，不根据客户端类型名反射调用，也不授予 Web 身份聊天来源资格。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/07：为宿主提供实际业务完成阶段，等待截止独立包装。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.6.0-l
 * @version 4.8.7-l
 */
@Service
public final class ManagementGateway {
    private final PluginRegistry plugins;
    private final ObjectProvider<ManagementAuthority> authorities;
    private final Map<String, ManagementResource<?, ?>> resources;
    /**
     * 统一校验归属及重名。
     * @param plugins 插件目录
     * @param contributions 管理资源
     * @param authorities 唯一宿主授权适配器，缺失时拒绝
     */
    public ManagementGateway(PluginRegistry plugins, List<ManagementResource<?, ?>> contributions, ObjectProvider<ManagementAuthority> authorities) {
        this.plugins = plugins; this.authorities = authorities;
        var values = new TreeMap<String, ManagementResource<?, ?>>();
        for (var resource : contributions) {
            var descriptor = resource.descriptor(); plugins.requireRegistered(descriptor.pluginId());
            if (values.putIfAbsent(descriptor.key(), resource) != null) { throw new IllegalArgumentException("管理资源 ID 重复：" + descriptor.key()); }
        }
        resources = Collections.unmodifiableMap(values);
    }
    /**
     * 按当前主体过滤可见资源，不泄露未授权插件的配置 schema。
     * @param context 可信管理上下文
     * @return 当前可发现能力
     */
    public CompletionStage<List<ManagementResource.Descriptor>> describe(ManagementContext context) {
        try {
            var authority = authority();
            CompletionStage<List<ManagementResource.Descriptor>> result = CompletableFuture.completedStage(List.of());
            for (var resource : resources.values()) {
                var descriptor = resource.descriptor();
                if (!plugins.isEnabled(descriptor.pluginId())) { continue; }
                result = result.thenCompose(previous -> context.control().guard(authority.authorize(context, descriptor))
                        .handle((ignored, failure) -> {
                            if (failure != null) { return previous; }
                            var copy = new ArrayList<>(previous); copy.add(descriptor); return List.copyOf(copy);
                        }));
            }
            return context.control().guard(result).thenApply(values -> values.stream().filter(value -> plugins.isEnabled(value.pluginId())).toList());
        } catch (RuntimeException failure) { return CompletableFuture.failedStage(failure); }
    }
    /**
     * 路由明确请求 DTO，授权与插件版本在实际执行及返回时复核。
     * @param key 资源全局 ID
     * @param context 已认证上下文
     * @param request 已按声明类型解析的 DTO
     * @return 真实业务结果
     */
    public CompletionStage<?> execute(String key, ManagementContext context, Object request) {
        return context.control().guard(executePhysical(key, context, request));
    }

    /**
     * 2026/11/06：物理阶段完整结束才完成，宿主另以控制器限制调用者等待。
     * @param key 资源全局 ID
     * @param context 已认证上下文
     * @param request 明确请求 DTO
     * @return 授权、业务和返回复核实际完成的阶段，不以逻辑超时提前完成
     */
    public CompletionStage<?> executePhysical(String key, ManagementContext context, Object request) {
        try {
            context.control().checkActive();
            var resource = resources.get(key);
            if (resource == null) {
                throw new IllegalArgumentException("管理资源不存在或不可见。");
            }
            var authority = authority();
            var descriptor = resource.descriptor();
            return authority.authorize(context, descriptor).thenCompose(ignored -> {
                context.checkActive();
                var token = plugins.capture(descriptor.pluginId());
                return call(resource, context, request).thenCompose(value -> authority.authorize(context, descriptor)
                    .thenApply(authorized -> {
                        context.checkActive();
                        plugins.check(token);
                        return value;
                    }));
            });
        } catch (RuntimeException failure) {
            return CompletableFuture.failedStage(failure);
        }
    }
    /**
     * 保留资源泛型并进行运行时请求类型检查。
     * @param resource 已注册用例
     * @param context 可信上下文
     * @param request 请求 DTO
     * @param <Q> 请求类型
     * @param <R> 返回类型
     * @return 完整业务阶段
     */
    private static <Q, R> CompletionStage<R> call(ManagementResource<Q, R> resource, ManagementContext context, Object request) {
        context.checkActive();
        return resource.execute(context, resource.requestType().cast(request));
    }
    /**
     * 要求唯一显式授权适配器，避免默认开放。
     * @return 宿主授权入口
     * @throws IllegalStateException 当未安装授权时
     */
    private ManagementAuthority authority() {
        return Objects.requireNonNull(authorities.getIfAvailable(), "尚未安装可信管理授权适配器。");
    }
}
