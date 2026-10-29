package org.zexnocs.teanekoagent.memory.recall;

import org.zexnocs.teanekoagent.memory.namespace.MemoryNamespaceReference;
import java.time.Instant;
import java.util.*;

/**
 * 保留覆盖程度的检索证据，不将预算内无命中宣称为确定不存在。
 * @param reference 查询固定引用，交付/注入前仍须复核
 * @param observedAt 本次过滤有效期的固定时刻
 * @param evidence 已筛选排序的证据
 * @param scanned 原候选检查数
 * @param matched 检查范围内符合条件的数量
 * @param limits 导致覆盖或输出不完整的预算
 * @param characters 实际证据数组 JSON 字符数，不含本结果诊断信封
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
public record MemoryRecallResult(MemoryNamespaceReference reference, Instant observedAt, List<MemoryRecallEvidence> evidence,
        int scanned, int matched, Set<Limit> limits, int characters) {
    /**
     * 冻结结果并验证证据范围、统计和完整性，没有失败伪装的正常空值。
     * @param reference 原引用
     * @param observedAt 固定时刻
     * @param evidence 证据
     * @param scanned 检查数
     * @param matched 命中数
     * @param limits 触及的预算
     * @param characters 负载字符数
     * @throws NullPointerException 当必需数据为空时
     * @throws IllegalArgumentException 当统计或定位无效时
     */
    public MemoryRecallResult {
        Objects.requireNonNull(reference, "检索结果引用不能为空。"); Objects.requireNonNull(observedAt, "检索结果时刻不能为空。");
        evidence = List.copyOf(evidence); limits = Set.copyOf(limits);
        if (scanned < matched || scanned > MemoryRecallLimits.MAX_SCANNED || matched < evidence.size() || characters < 2 || characters > MemoryRecallLimits.MAX_CHARACTERS
                || limits.isEmpty() && matched != evidence.size() || evidence.size() > MemoryRecallLimits.MAX_RESULTS) { throw new IllegalArgumentException("检索结果统计无效。"); }
        var ids = new HashSet<UUID>();
        for (var item : evidence) {
            if (!ids.add(item.id()) || !item.namespaceId().equals(reference.namespaceId()) || item.generation() != reference.generation()) {
                throw new IllegalArgumentException("检索结果证据与固定引用不一致。");
            }
        }
    }
    /**
     * 只有检查完全部候选且不存在输出截断时，空结果才证明该读取边界没有符合条件的事实。
     * @return 本次查询已完整确认无结果时为真
     */
    public boolean definitivelyEmpty() { return limits.isEmpty() && evidence.isEmpty(); }
    /**
     * 分开表达未检查全部、结果条数截断及完整证据无法装入字符预算。
     * @author zExNocs
     * @date 2026/10/29
     * @since 4.0.0
     * @version 4.5.0-l
     */
    public enum Limit { SCAN, RESULTS, CHARACTERS }
}
