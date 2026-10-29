package org.zexnocs.teanekoagent.memory.recall;

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.memory.fact.MemoryEventTime;
import org.zexnocs.teanekoagent.memory.fact.MemoryFactRecord;
import org.zexnocs.teanekoagent.memory.fact.MemoryFactSource;
import org.zexnocs.teanekoagent.memory.fact.MemoryFactValue;
import org.zexnocs.teanekoagent.memory.namespace.MemoryNamespaceId;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 可供上层检索使用的独立证据，保留事实主语、可信来源与时间，不包含旧审计原文。
 * @param id 稳定记录 ID
 * @param namespaceId 证据所属空间
 * @param generation 数据代次
 * @param revision 内容版本
 * @param subjectId 事实主体
 * @param value 事实或偏好
 * @param source 首次接受来源；导入记录表示真实导入操作者
 * @param createdAt 记录时刻
 * @param updatedAt 更新时刻
 * @param locked 管理锁定状态
 * @param timeBasis 事件时间依据
 * @param externalOrigin 可选外部证据定位，不包含原 JSON
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
public record MemoryRecallEvidence(UUID id, MemoryNamespaceId namespaceId, long generation, long revision, UUID subjectId,
        MemoryFactValue value, MemoryFactSource source, Instant createdAt, Instant updatedAt, boolean locked, TimeBasis timeBasis,
        @Nullable ExternalOrigin externalOrigin) {
    /**
     * 校验事实定位与时间不变量，拒绝将记录时间估算伪装成已知事件时间。
     * @param id 记录 ID
     * @param namespaceId 空间
     * @param generation 代次
     * @param revision 版本
     * @param subjectId 主体
     * @param value 内容
     * @param source 来源
     * @param createdAt 记录时间
     * @param updatedAt 更新时间
     * @param locked 锁定状态
     * @param timeBasis 时间依据
     * @param externalOrigin 外部定位
     * @throws NullPointerException 当必需数据为空时
     * @throws IllegalArgumentException 当记录或时间依据不一致时
     */
    public MemoryRecallEvidence {
        Objects.requireNonNull(id, "检索证据 ID 不能为空。"); Objects.requireNonNull(namespaceId, "检索证据空间不能为空。");
        Objects.requireNonNull(subjectId, "检索证据主体不能为空。"); Objects.requireNonNull(value, "检索证据内容不能为空。");
        Objects.requireNonNull(source, "检索证据来源不能为空。"); Objects.requireNonNull(createdAt, "检索证据记录时间不能为空。");
        Objects.requireNonNull(updatedAt, "检索证据更新时间不能为空。");
        if (generation < 0 || revision < 0 || updatedAt.isBefore(createdAt)) { throw new IllegalArgumentException("检索证据版本或记录时间顺序无效。"); }
        Objects.requireNonNull(timeBasis, "检索证据时间依据不能为空。");
        if ((value.eventTime().precision() == MemoryEventTime.Precision.UNKNOWN) != (timeBasis == TimeBasis.RECORDED_TIME_ESTIMATE)) {
            throw new IllegalArgumentException("检索证据时间依据与事件精度不一致。");
        }
    }
    /**
     * 从已校验原记录构造证据，外部原文及调整明细只留在可信审计存储。
     * @param record 原记录
     * @return 不包含原始审计 JSON 的证据
     */
    public static MemoryRecallEvidence from(MemoryFactRecord record) {
        var provenance = record.provenance();
        var origin = provenance == null ? null : new ExternalOrigin(provenance.system(), provenance.target(), provenance.key(), provenance.externalId(), provenance.digest());
        return new MemoryRecallEvidence(record.id(), record.namespaceId(), record.generation(), record.revision(), record.subjectId(), record.value(),
                record.source(), record.createdAt(), record.updatedAt(), record.locked(), record.value().eventTime().precision() == MemoryEventTime.Precision.UNKNOWN
                ? TimeBasis.RECORDED_TIME_ESTIMATE : TimeBasis.EVENT_TIME, origin);
    }
    /**
     * 区分来源提供的事件时间与使用记录时间的兼容估算。
     * @author zExNocs
     * @date 2026/10/29
     * @since 4.0.0
     * @version 4.5.0-l
     */
    public enum TimeBasis { EVENT_TIME, RECORDED_TIME_ESTIMATE }
    /**
     * 可审计的外部定位；原 source 等字段仍通过独立可信管理操作读取。
     * @param system 来源格式
     * @param target 原目标
     * @param key 原键
     * @param externalId 原标识
     * @param digest 原文摘要
     * @author zExNocs
     * @date 2026/10/29
     * @since 4.0.0
     * @version 4.5.0-l
     */
    public record ExternalOrigin(String system, String target, String key, String externalId, String digest) {
        /**
         * 验证引用结构，不读取原文或推断身份。
         * @param system 格式
         * @param target 目标
         * @param key 原键
         * @param externalId 原标识
         * @param digest 摘要
         * @throws NullPointerException 当必需数据为空时
         * @throws IllegalArgumentException 当定位或摘要无效时
         */
        public ExternalOrigin {
            for (var part : List.of(system, target, key, externalId)) { if (part.isBlank()) { throw new IllegalArgumentException("检索外部证据定位无效。"); } }
            Objects.requireNonNull(digest, "检索外部证据摘要不能为空。");
            if (!digest.matches("[0-9a-f]{64}")) { throw new IllegalArgumentException("检索外部证据摘要无效。"); }
        }
    }
}
