package org.zexnocs.teanekoplugin.agent.cognition.integration.web;

// 2026/11/07：接口统一使用 I 前缀，调用合同与持久数据保持不变。

// 2026/11/07：认知业务契约归入插件，纯会话数据沿用运行底座。

// 2026/11/07：按插件归属迁移业务实现，保留底层契约、持久标识和事务边界。

import org.zexnocs.teanekoagent.integration.web.IAgentLibrarySource;

import org.springframework.stereotype.Component;
import org.zexnocs.teanekoagent.database.easydata.StoredPage;
import org.zexnocs.teanekocore.plugin.management.ManagementContext;
import java.util.concurrent.CompletionStage;

import org.zexnocs.teanekoplugin.agent.cognition.memory.admin.IMemoryManagementStore;
import org.zexnocs.teanekoplugin.agent.cognition.memory.fact.MemoryFactRecord;
import org.zexnocs.teanekoplugin.agent.cognition.memory.namespace.IMemoryNamespaceStore;
import org.zexnocs.teanekoplugin.agent.cognition.memory.namespace.MemoryNamespaceId;
import org.zexnocs.teanekoplugin.agent.cognition.memory.namespace.MemoryNamespaceReference;
import org.zexnocs.teanekoplugin.agent.cognition.memory.layered.ILongTermMemoryStore;
import org.zexnocs.teanekoapp.teauser.TeaUserDirectory;
import java.time.Instant;
import java.util.UUID;

/**
 * 按当前代次发现明确事实与自动整理的画像主体，分页不会只截取最近一批用户。
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
public final class ProfileLibrarySource implements IAgentLibrarySource {
    /** @return 2026/11/07：此持久目录由认知插件提供。 */
    @Override public String pluginId() {
        return "agent.cognition";
    }

    private final IMemoryNamespaceStore namespaces;
    private final IMemoryManagementStore facts;
    private final ILongTermMemoryStore learned;
    private final TeaUserDirectory users;
    /**
     * @param namespaces 原空间目录
     * @param facts 明确事实
     * @param learned 长期整理
     * @param users 已存用户映射
     */
    public ProfileLibrarySource(IMemoryNamespaceStore namespaces, IMemoryManagementStore facts,
            ILongTermMemoryStore learned, TeaUserDirectory users) {
        this.namespaces = namespaces;
        this.facts = facts;
        this.learned = learned;
        this.users = users;
    }
    /** @return 固定目录标识 */
    @Override public String id() { return "profiles"; }
    /**
     * @param context 原管理能力
     * @param query 固定空间、代次和记录游标
     * @return 本页已知主体，可跨页去重；空页仍可能带后继游标
     */
    @Override public CompletionStage<StoredPage<Item>> page(ManagementContext context, Query query) {
        context.checkActive();
        var id = new MemoryNamespaceId(query.namespace());
        return namespaces.find(id).thenCompose(found -> {
            context.checkActive();
            var namespace = found.orElseThrow(() -> new java.util.NoSuchElementException("记忆空间未登记。"));
            if (query.generation() < 0 || query.generation() != namespace.generation()) {
                throw new IllegalStateException("请固定当前记忆空间代次后查询画像目录。");
            }
            CompletionStage<StoredPage<UUID>> subjects;
            if (query.after().startsWith("learned:")) {
                var reference = new MemoryNamespaceReference(AgentConsoleService.scope(context), id, 0, namespace.generation());
                subjects = learned.subjects(reference, query.after().substring(8), 40)
                        .thenApply(page -> new StoredPage<>(page.items(), page.next().isEmpty() ? "" : "learned:" + page.next()));
            } else {
                if (!query.after().isEmpty() && !query.after().startsWith("facts:")) {
                    throw new IllegalArgumentException("画像目录游标无效。");
                }
                UUID after = query.after().isEmpty() ? null : UUID.fromString(query.after().substring(6));
                subjects = facts.page(id, namespace.generation(), after, 40).thenApply(page -> new StoredPage<>(
                        page.records().stream().filter(item -> item.value().expiresAt() == null
                                || item.value().expiresAt().isAfter(Instant.now())).map(MemoryFactRecord::subjectId).distinct().toList(),
                        page.nextCursor() == null ? "learned:" : "facts:" + page.nextCursor()));
            }
            return subjects.thenApply(page -> {
                context.checkActive();
                var items = page.items().stream().map(subject -> {
                    var mappings = users.mappings(subject);
                    String name = mappings.stream().map(TeaUserDirectory.Mapping::nickname).filter(value -> !value.isBlank())
                            .findFirst().orElse(subject.toString());
                    return new Item(subject.toString(), name, "用户画像", namespace.generation(), -1, false, mappings);
                }).toList();
                context.checkActive();
                return new StoredPage<>(items, page.next());
            });
        });
    }
}
