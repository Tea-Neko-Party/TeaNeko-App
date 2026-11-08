package org.zexnocs.teanekoplugin.agent.cognition.memory.adapter;

// 2026/11/07：认知业务契约归入插件，纯会话数据沿用运行底座。

// 2026/11/07：按插件归属迁移业务实现，保留底层契约、持久标识和事务边界。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.database.easydata.core.EasyDataLock;
import org.zexnocs.teanekoplugin.agent.cognition.memory.fact.MemoryFactGenerationException;
import org.zexnocs.teanekoplugin.agent.cognition.memory.namespace.MemoryNamespaceId;
import org.zexnocs.teanekoplugin.agent.cognition.memory.recall.IMemoryRecallStore;
import org.zexnocs.teanekoplugin.agent.cognition.memory.recall.MemoryRecallEvidence;
import org.zexnocs.teanekoplugin.agent.cognition.memory.recall.MemoryRecallLimits;
import org.zexnocs.teanekoplugin.agent.cognition.memory.recall.MemoryRecallScan;

import java.util.ArrayList;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * 在一个 namespace 共享锁事务中读取有界稳定前缀，逐页去掉原审计 JSON 后交给业务筛选。
 * 复用事实 schema 与 DatabaseTask，不将相互独立的分页事务冒充完整快照。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/07：按插件归属迁移业务实现，保留原配置与持久化契约。
 *
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.5.0-l
 * @version 4.9.0-l
 */
@Service
@org.zexnocs.teanekocore.plugin.PluginOwned("agent.cognition")
public final class EasyDataMemoryRecallStore implements IMemoryRecallStore {
    private final EasyDataMemoryNamespaceStore namespaces;
    private final EasyDataMemoryFactStore facts;
    /**
     * 保存已有适配器依赖，构造不执行读取或注册默认空间。
     * @param namespaces 已有事务适配器
     * @param facts 事实 schema 与分页实现
     * @throws NullPointerException 当依赖为空时
     */
    public EasyDataMemoryRecallStore(EasyDataMemoryNamespaceStore namespaces, EasyDataMemoryFactStore facts) {
        this.namespaces = Objects.requireNonNull(namespaces, "记忆检索事务适配器不能为空。");
        this.facts = Objects.requireNonNull(facts, "记忆检索事实适配器不能为空。");
    }
    /**
     * 锁定当前元数据后按每页至多一百条读取；所有写入须遵守同一 namespace 写锁协议。
     * @param namespaceId 已授权空间
     * @param generation 固定代次
     * @param maxScanned 最多检查记录数
     * @return 稳定候选及末尾标记，提交完成后交付；不更改任何业务数据
     * @throws NullPointerException 当空间为空时
     * @throws IllegalArgumentException 当代次或预算无效时
     */
    @Override public CompletionStage<MemoryRecallScan> scan(MemoryNamespaceId namespaceId, long generation, int maxScanned) {
        Objects.requireNonNull(namespaceId, "记忆检索空间不能为空。");
        if (generation < 0 || maxScanned < 1 || maxScanned > MemoryRecallLimits.MAX_SCANNED) { throw new IllegalArgumentException("记忆候选读取代次或预算无效。"); }
        return namespaces.submit("读取记忆检索候选", () -> {
            var row = namespaces.lockNamespace(namespaceId, EasyDataLock.READ);
            if (row == null) { throw new MemoryFactGenerationException(true); }
            if (namespaces.namespace(row, namespaceId).generation() != generation) { throw new MemoryFactGenerationException(false); }
            var evidence = new ArrayList<MemoryRecallEvidence>(); UUID cursor = null;
            while (evidence.size() < maxScanned) {
                var page = facts.listInTransaction(namespaceId, generation, cursor, Math.min(100, maxScanned - evidence.size()));
                page.records().forEach(record -> evidence.add(MemoryRecallEvidence.from(record)));
                cursor = page.nextCursor();
                if (cursor == null) { return new MemoryRecallScan(namespaceId, generation, evidence, true); }
            }
            return new MemoryRecallScan(namespaceId, generation, evidence, false);
        });
    }
}
