package org.zexnocs.teanekoagent.integration.web;

import org.springframework.stereotype.Component;
import org.zexnocs.teanekoagent.database.easydata.StoredPage;
import org.zexnocs.teanekocore.plugin.management.GlobalManagementResource;
import org.zexnocs.teanekocore.plugin.management.ManagementContext;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * 实例持久目录入口；来源只在装配时登记，查询按键读取，不做类或 Bean 扫描。
 * <br>2026/11/06：复用原全局管理网关和本地白名单，重复目录标识立即拒绝。
 *
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.4-l
 * @version 4.8.4-l
 */
@Component
public final class AgentLibraryCatalog implements GlobalManagementResource<AgentLibrarySource.Query, StoredPage<AgentLibrarySource.Item>> {
    private final Map<String, AgentLibrarySource> sources;
    /** @param sources Spring 已登记的持久目录来源 */
    public AgentLibraryCatalog(List<AgentLibrarySource> sources) {
        var entries = new java.util.HashMap<String, AgentLibrarySource>();
        for (var source : sources) {
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
    @Override public Class<AgentLibrarySource.Query> requestType() { return AgentLibrarySource.Query.class; }
    /**
     * @param context 宿主创建的管理能力
     * @param query 有界目录请求
     * @return 原来源的真实页面
     */
    @Override public CompletionStage<StoredPage<AgentLibrarySource.Item>> execute(ManagementContext context, AgentLibrarySource.Query query) {
        context.checkActive();
        var source = sources.get(query.kind());
        if (source == null) {
            throw new IllegalArgumentException("未注册的持久目录。");
        }
        return source.page(context, query).thenApply(page -> { context.checkActive(); return page; });
    }
}
