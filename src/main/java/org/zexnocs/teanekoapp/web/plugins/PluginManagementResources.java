package org.zexnocs.teanekoapp.web.plugins;

// 2026/11/07：接口统一使用 I 前缀，调用合同与持久数据保持不变。

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.zexnocs.teanekoapp.plugin.PluginManagement;
import org.zexnocs.teanekoapp.plugin.PluginManagementService;
import org.zexnocs.teanekocore.plugin.management.IGlobalManagementResource;
import org.zexnocs.teanekocore.plugin.management.ManagementContext;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;

/**
 * 宿主拥有的插件管理资源，停用业务插件后仍保留重新启用与回执查询入口。
 * <br>2026/11/07：所有动作继续经过原全局管理授权和本地资源白名单。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
@Configuration(proxyBeanMethods = false)
public class PluginManagementResources {
    /**
     * @param service 插件管理用例
     * @return 包含禁用插件的管理目录
     */
    @Bean
    public IGlobalManagementResource<Query, PluginManagement.Catalog> pluginCatalog(PluginManagementService service) {
        return new Resource<>("plugins.catalog", false, Query.class,
                (context, query) -> CompletableFuture.completedStage(service.catalog(context)));
    }

    /**
     * @param service 插件管理用例
     * @return 无副作用的影响预览
     */
    @Bean
    public IGlobalManagementResource<PluginManagement.Request, PluginManagement.Preview> pluginPreview(
            PluginManagementService service) {
        return new Resource<>("plugins.preview", false, PluginManagement.Request.class,
                (context, query) -> CompletableFuture.completedStage(service.preview(query, context)));
    }

    /**
     * @param service 插件管理用例
     * @return 持久化且复核身份的启停动作
     */
    @Bean
    public IGlobalManagementResource<PluginManagement.Change, PluginManagement.Receipt> pluginChange(
            PluginManagementService service) {
        return new Resource<>("plugins.change", true, PluginManagement.Change.class,
                (context, query) -> service.change(query, context));
    }

    /**
     * @param service 插件管理用例
     * @return 当前主体原操作的真实回执
     */
    @Bean
    public IGlobalManagementResource<OperationQuery, PluginManagement.Receipt> pluginOperation(
            PluginManagementService service) {
        return new Resource<>("plugins.operation", false, OperationQuery.class,
                (context, query) -> CompletableFuture.completedStage(service.operation(query.operationId(), context)));
    }

    /**
     * @param directory 已发布的页面目录
     * @return 当前有效的插件页面
     */
    @Bean
    public IGlobalManagementResource<Query, List<PluginConsoleDirectory.View>> pluginPages(
            PluginConsoleDirectory directory) {
        return new Resource<>("plugins.pages", false, Query.class, (context, query) -> {
            context.checkActive();
            return CompletableFuture.completedStage(directory.list());
        });
    }

    /**
     * 固定的空查询，不接受浏览器提供类型名称。
     * @author zExNocs
     * @date 2026/11/07
     * @since 4.9.0-l
     * @version 4.9.0-l
     */
    public record Query() {}

    /**
     * 原启停操作定位请求，主体由认证上下文提供。
     * @param operationId 原操作编号
     * @author zExNocs
     * @date 2026/11/07
     * @since 4.9.0-l
     * @version 4.9.0-l
     */
    public record OperationQuery(UUID operationId) {}

    /**
     * 将固定应用用例适配为原网关资源，不暴露任意方法调用能力。
     * @param id 宿主内资源标识
     * @param mutation 是否修改状态
     * @param type 请求类型
     * @param action 固定业务用例
     * @param <Q> 请求类型
     * @param <R> 结果类型
     * @author zExNocs
     * @date 2026/11/07
     * @since 4.9.0-l
     * @version 4.9.0-l
     */
    private record Resource<Q, R>(String id, boolean mutation, Class<Q> type,
            BiFunction<ManagementContext, Q, CompletionStage<R>> action) implements IGlobalManagementResource<Q, R> {
        /** @return 不可禁用宿主拥有的资源和权限声明 */
        @Override
        public Descriptor descriptor() {
            return new Descriptor("teaneko.core", id, 1, mutation ? "plugins.manage" : "plugins.inspect",
                    mutation, List.of());
        }

        /** @return 已编译的请求 DTO 类型 */
        @Override
        public Class<Q> requestType() {
            return type;
        }

        /**
         * @param context 已认证上下文
         * @param request 明确请求
         * @return 实际业务完成阶段
         */
        @Override
        public CompletionStage<R> execute(ManagementContext context, Q request) {
            context.checkActive();
            return action.apply(context, request);
        }
    }
}
