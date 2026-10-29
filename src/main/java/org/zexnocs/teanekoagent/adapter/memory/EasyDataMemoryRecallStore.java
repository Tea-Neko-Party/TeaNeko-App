package org.zexnocs.teanekoagent.adapter.memory;

import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.memory.fact.MemoryFactGenerationException;
import org.zexnocs.teanekoagent.memory.namespace.MemoryNamespaceId;
import org.zexnocs.teanekoagent.memory.recall.IMemoryRecallStore;
import org.zexnocs.teanekoagent.memory.recall.MemoryRecallEvidence;
import org.zexnocs.teanekoagent.memory.recall.MemoryRecallLimits;
import org.zexnocs.teanekoagent.memory.recall.MemoryRecallScan;

import java.util.ArrayList;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * 在一个 namespace 共享锁事务中读取有界稳定前缀，逐页去掉原审计 JSON 后交给业务筛选。
 * 复用事实 schema 与 DatabaseTask，不将相互独立的分页事务冒充完整快照。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
@Service
public final class EasyDataMemoryRecallStore implements IMemoryRecallStore {
    private final EasyDataMemoryNamespaceStore namespaces;
    private final MemoryNamespaceWriteRepository locks;
    private final EasyDataMemoryFactStore facts;
    /**
     * 保存已有适配器依赖，构造不执行读取或注册默认空间。
     * @param namespaces 已有事务适配器
     * @param locks 空间快照锁
     * @param facts 事实 schema 与分页实现
     * @throws NullPointerException 当依赖为空时
     */
    public EasyDataMemoryRecallStore(EasyDataMemoryNamespaceStore namespaces, MemoryNamespaceWriteRepository locks, EasyDataMemoryFactStore facts) {
        this.namespaces = Objects.requireNonNull(namespaces, "记忆检索事务适配器不能为空。");
        this.locks = Objects.requireNonNull(locks, "记忆检索快照锁不能为空。"); this.facts = Objects.requireNonNull(facts, "记忆检索事实适配器不能为空。");
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
            var row = locks.lockSnapshot(namespaceId.value());
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
