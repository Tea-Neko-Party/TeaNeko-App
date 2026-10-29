package org.zexnocs.teanekoagent.memory.fact.legacy;

import org.zexnocs.teanekoagent.memory.fact.MemoryFactRecord;

import java.util.List;
import java.util.Objects;

/**
 * 整行原子导入结果，拒绝时不包含部分导入记录。
 * @param status 提交结果
 * @param records 已持久化的记录或映射复用的原记录
 * @param skipped 明确未导入的其他类型
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
public record LegacyMemoryImportResult(Status status, List<MemoryFactRecord> records, List<LegacyMemorySnapshot.Skipped> skipped) {
    /**
     * 冻结结果并拒绝部分成功的伪拒绝回执。
     * @param status 状态
     * @param records 原记录
     * @param skipped 未导入项
     * @throws NullPointerException 当必需字段为空时
     * @throws IllegalArgumentException 当失败仍携带提交记录时
     */
    public LegacyMemoryImportResult {
        Objects.requireNonNull(status, "旧数据导入状态不能为空。"); records = List.copyOf(records); skipped = List.copyOf(skipped);
        if (status != Status.IMPORTED && (!records.isEmpty() || !skipped.isEmpty())) { throw new IllegalArgumentException("拒绝导入不能包含部分成功记录。"); }
    }
    /**
     * 构造整批无写入的拒绝。
     * @param status 拒绝状态
     * @return 空拒绝回执
     */
    public static LegacyMemoryImportResult rejected(Status status) { return new LegacyMemoryImportResult(status, List.of(), List.of()); }
    /**
     * 明确区分源行缺失、预览过期和映射冲突，IMPORTED 包含原结果重放。
     * @author zExNocs
     * @date 2026/10/29
     * @since 4.0.0
     * @version 4.5.0-l
     */
    public enum Status { IMPORTED, CONFLICT, SOURCE_MISSING, SOURCE_CHANGED }
}
