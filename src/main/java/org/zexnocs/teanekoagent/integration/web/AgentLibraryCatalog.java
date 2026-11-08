package org.zexnocs.teanekoagent.integration.web;

// 2026/11/07：接口统一使用 I 前缀，调用合同与持久数据保持不变。

import org.springframework.stereotype.Component;
import org.zexnocs.teanekoagent.database.easydata.StoredPage;
import org.zexnocs.teanekocore.plugin.PluginRegistry;
import org.zexnocs.teanekocore.plugin.management.IGlobalManagementResource;
import org.zexnocs.teanekocore.plugin.management.ManagementContext;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * 实例持久目录入口；来源只在装配时登记，查询按键读取，不做类或 Bean 扫描。
 * <br>2026/11/06：复用原全局管理网关和本地白名单，重复目录标识立即拒绝。
 * <br>2026/11/07：接入插件归属与独立贡献，保留原权限及执行契约。
 *
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.4-l
 * @version 4.9.0-l
 */
@Component
public final class AgentLibraryCatalog implements IGlobalManagementResource<IAgentLibrarySource.Query, StoredPage<IAgentLibrarySource.Item>> {
    private final Map<String, IAgentLibrarySource> sources;
    private final PluginRegistry plugins;
    /**
     * 2026/11/07：来源归属在初始化登记，查询只读取当前插件资格。
     * @param sources Spring 已登记的持久目录来源
     * @param plugins 统一插件状态及代次
     */
    public AgentLibraryCatalog(List<IAgentLibrarySource> sources, PluginRegistry plugins) {
        this.plugins = plugins;
        var entries = new java.util.HashMap<String, IAgentLibrarySource>();
        for (var source : sources) {
            plugins.requireRegistered(source.pluginId());
            if (entries.putIfAbsent(source.id(), source) != null) {
                throw new IllegalArgumentException("持久目录标识重复：" + source.id());
            }
        }
        this.sources = Map.copyOf(entries);
    }
    /** @return 全局只读资源声明 */
    @Override public Descriptor descriptor() {
        return new Descriptor("teaneko.agent", "library", 1, "agent.library.read", false, List.of());
    }
    /** @return 明确分页请求类型 */
    @Override public Class<IAgentLibrarySource.Query> requestType() {
        return IAgentLibrarySource.Query.class;
    }
    /**
     * @param context 宿主创建的管理能力
     * @param query 有界目录请求
     * @return 原来源的真实页面
     */
    @Override public CompletionStage<StoredPage<IAgentLibrarySource.Item>> execute(ManagementContext context, IAgentLibrarySource.Query query) {
        context.checkActive();
        var source = sources.get(query.kind());
        if (source == null) {
            throw new IllegalArgumentException("未注册的持久目录。");
        }
        // 2026/11/07：读取前及返回后检查原插件代次，停用后不交付插件数据。
        var token = plugins.capture(source.pluginId());
        return source.page(context, query).thenApply(page -> {
            context.checkActive();
            plugins.check(token);
            return page;
        });
    }
}
