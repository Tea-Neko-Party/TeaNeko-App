package org.zexnocs.teanekocore.plugin.management;

import org.springframework.stereotype.Component;
import org.zexnocs.teanekocore.plugin.PluginRegistry;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Core 的插件目录管理资源，只有明确的全局查看权限才能读取。
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.0.0
 * @version 4.6.0-l
 */
@Component
public final class PluginCatalogResource implements ManagementResource<PluginCatalogResource.Query, List<PluginRegistry.View>> {
    private final PluginRegistry plugins;
    /**
     * 借用统一插件目录。
     * @param plugins 插件目录
     */
    public PluginCatalogResource(PluginRegistry plugins) { this.plugins = plugins; }
    /** @return Core 只读目录用例 */
    @Override public Descriptor descriptor() { return new Descriptor("teaneko.core", "plugins", 1, "plugins.inspect", false, List.of()); }
    /** @return 无额外参数的明确请求类型 */
    @Override public Class<Query> requestType() { return Query.class; }
    /**
     * 返回当前目录及有效状态，不发布资源句柄。
     * @param context 已授权上下文
     * @param query 明确空请求
     * @return 插件管理视图
     */
    @Override public CompletionStage<List<PluginRegistry.View>> execute(ManagementContext context, Query query) {
        context.control().checkActive(); return CompletableFuture.completedStage(plugins.list());
    }
    /**
     * 无额外过滤参数的目录请求。
     * @author zExNocs
     * @date 2026/11/02
     * @since 4.0.0
     * @version 4.6.0-l
     */
    public record Query() {}
}
