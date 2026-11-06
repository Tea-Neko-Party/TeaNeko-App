package org.zexnocs.teanekoagent.integration.web;

import org.springframework.stereotype.Component;
import org.zexnocs.teanekoagent.database.easydata.StoredPage;
import org.zexnocs.teanekocore.plugin.management.ManagementContext;
import java.util.List;
import java.util.concurrent.CompletionStage;

import org.zexnocs.teanekoagent.component.memory.namespace.IMemoryNamespaceStore;

/**
 * 记忆空间的已登记目录。
 * <br>2026/11/06：独立目录只读持久元数据，不初始化空间或改变聊天绑定。
 *
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.4-l
 * @version 4.8.4-l
 */
@Component
public final class MemoryLibrarySource implements AgentLibrarySource {
    private final IMemoryNamespaceStore store;
    /** @param store 原记忆空间端口 */
    public MemoryLibrarySource(IMemoryNamespaceStore store) { this.store = store; }
    /** @return 固定目录标识 */
    @Override public String id() { return "memory"; }
    /**
     * @param context 实时管理能力
     * @param query 原分页
     * @return 只含空间元数据的页面
     */
    @Override public CompletionStage<StoredPage<Item>> page(ManagementContext context, Query query) {
        context.checkActive();
        return store.page(query.after(), 40).thenApply(page -> {
            context.checkActive();
            return new StoredPage<>(page.items().stream().map(item -> new Item(item.id().value(),
                    item.id().equals(org.zexnocs.teanekoagent.component.memory.namespace.MemoryNamespaceId.SHARED) ? "共享记忆" : item.id().value(),
                    "记忆空间", item.generation(), item.revision(), false, List.of())).toList(), page.next());
        });
    }
}
