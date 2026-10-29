package org.zexnocs.teanekoagent.memory.recall;

import org.zexnocs.teanekoagent.memory.namespace.MemoryNamespaceId;

import java.util.List;
import java.util.Objects;

/**
 * 存储在同一稳定读取边界生成的候选前缀，complete 仅表示该时点已扫描完本代次。
 * @param namespaceId 原空间
 * @param generation 原代次
 * @param evidence 稳定 ID 升序的有界候选，尚未排除过期或无关记录
 * @param complete 是否已到达该读取边界的末尾
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
public record MemoryRecallScan(MemoryNamespaceId namespaceId, long generation, List<MemoryRecallEvidence> evidence, boolean complete) {
    /**
     * 拒绝跨空间、重复或倒退的候选，避免损坏端口产出貌似完整的空结果。
     * @param namespaceId 空间
     * @param generation 代次
     * @param evidence 原候选
     * @param complete 是否完整
     * @throws NullPointerException 当必需数据为空时
     * @throws IllegalArgumentException 当定位、排序或容量无效时
     */
    public MemoryRecallScan {
        Objects.requireNonNull(namespaceId, "检索候选空间不能为空。"); evidence = List.copyOf(evidence);
        if (generation < 0 || evidence.size() > MemoryRecallLimits.MAX_SCANNED || !complete && evidence.isEmpty()) { throw new IllegalArgumentException("检索候选代次或容量无效。"); }
        String previous = "";
        for (var item : evidence) {
            if (!item.namespaceId().equals(namespaceId) || item.generation() != generation || item.id().toString().compareTo(previous) <= 0) {
                throw new IllegalArgumentException("检索候选定位或稳定排序无效。");
            }
            previous = item.id().toString();
        }
    }
}
