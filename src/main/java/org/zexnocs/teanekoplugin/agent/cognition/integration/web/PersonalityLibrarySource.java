package org.zexnocs.teanekoplugin.agent.cognition.integration.web;

// 2026/11/07：接口统一使用 I 前缀，调用合同与持久数据保持不变。

// 2026/11/07：认知业务契约归入插件，纯会话数据沿用运行底座。

// 2026/11/07：按插件归属迁移业务实现，保留底层契约、持久标识和事务边界。

import org.zexnocs.teanekoagent.integration.web.IAgentLibrarySource;

import org.springframework.stereotype.Component;
import org.zexnocs.teanekoagent.database.easydata.StoredPage;
import org.zexnocs.teanekocore.plugin.management.ManagementContext;
import java.util.List;
import java.util.concurrent.CompletionStage;

import org.zexnocs.teanekoplugin.agent.cognition.personality.IPersonalityStore;

/**
 * 人格补正空间和不可变备份目录。
 * <br>2026/11/06：独立目录只读持久元数据，不初始化空间或改变聊天绑定。
 * <br>2026/11/07：按插件归属迁移业务实现，保留原配置与持久化契约。
 *
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.4-l
 * @version 4.9.0-l
 */
@Component
@org.zexnocs.teanekocore.plugin.PluginOwned("agent.cognition")
public final class PersonalityLibrarySource implements IAgentLibrarySource {
    /** @return 2026/11/07：此持久目录由认知插件提供。 */
    @Override public String pluginId() {
        return "agent.cognition";
    }

    private final IPersonalityStore store;
    /** @param store 原人格存储端口 */
    public PersonalityLibrarySource(IPersonalityStore store) { this.store = store; }
    /** @return 固定目录标识 */
    @Override public String id() { return "personality"; }
    /**
     * @param context 实时管理能力
     * @param query 原分页
     * @return 不含补正正文的页面
     */
    @Override public CompletionStage<StoredPage<Item>> page(ManagementContext context, Query query) {
        context.checkActive();
        return store.page(query.after(), 40).thenApply(page -> {
            context.checkActive();
            return new StoredPage<>(page.items().stream().map(item -> new Item(item.key(), item.key(),
                    (item.backup() ? "只读备份" : "人格空间") + " · " + item.corrections().size() + " 条补正",
                    -1, item.revision(), item.backup(), List.of())).toList(), page.next());
        });
    }
}
