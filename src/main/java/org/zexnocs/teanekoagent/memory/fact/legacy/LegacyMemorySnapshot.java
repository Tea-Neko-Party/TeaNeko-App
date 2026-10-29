package org.zexnocs.teanekoagent.memory.fact.legacy;

import org.zexnocs.teanekoagent.memory.fact.MemoryFactProvenance;
import org.zexnocs.teanekoagent.memory.fact.MemoryFactValue;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 显式读取的旧行快照，与空列表和行缺失分别表达。
 * @param location 原物理定位
 * @param version 原行 Version
 * @param digest 完整旧 JSON 的摘要
 * @param entries 可转换的事实及偏好
 * @param skipped 保留在旧行中的其他已知类型
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
public record LegacyMemorySnapshot(LegacyMemoryLocation location, long version, String digest,
        List<Entry> entries, List<Skipped> skipped) {
    /**
     * 冻结预览结果，拒绝无版本或无摘要的快照。
     * @param location 旧定位
     * @param version 原行版本
     * @param digest 完整原文摘要
     * @param entries 转换结果
     * @param skipped 未导入类型
     * @throws NullPointerException 当必需字段为空时
     * @throws IllegalArgumentException 当版本或摘要无效时
     */
    public LegacyMemorySnapshot {
        Objects.requireNonNull(location, "旧快照定位不能为空。"); Objects.requireNonNull(digest, "旧快照摘要不能为空。");
        entries = List.copyOf(entries); skipped = List.copyOf(skipped);
        if (version < 0 || !digest.matches("[0-9a-f]{64}")) { throw new IllegalArgumentException("旧快照版本或摘要无效。"); }
    }

    /**
     * 已规范化但尚未授权或持久化的单条旧证据。
     * @param id 原不透明 ID
     * @param subjectKey 待显式映射的主体
     * @param value 新内容
     * @param createdAt 原创建或明确估算时刻
     * @param updatedAt 原更新时刻
     * @param locked 原锁定状态
     * @param provenance 原始证据及调整说明
     * @author zExNocs
     * @date 2026/10/29
     * @since 4.0.0
     * @version 4.5.0-l
     */
    public record Entry(String id, String subjectKey, MemoryFactValue value, Instant createdAt, Instant updatedAt,
            boolean locked, MemoryFactProvenance provenance) {
        /**
         * 校验主语、来源及记录时间，不把旧字符串解释为新身份。
         * @param id 原标识
         * @param subjectKey 待映射主体
         * @param value 规范内容
         * @param createdAt 创建时刻
         * @param updatedAt 更新时刻
         * @param locked 锁定状态
         * @param provenance 原始证据
         * @throws NullPointerException 当必需字段为空时
         * @throws IllegalArgumentException 当标识或时间顺序无效时
         */
        public Entry {
            Objects.requireNonNull(id, "旧记录标识不能为空。"); Objects.requireNonNull(subjectKey, "旧记录主体不能为空。");
            Objects.requireNonNull(value, "旧记录内容不能为空。"); Objects.requireNonNull(createdAt, "旧记录创建时间不能为空。");
            Objects.requireNonNull(updatedAt, "旧记录更新时间不能为空。"); Objects.requireNonNull(provenance, "旧记录原文不能为空。");
            if (id.isBlank() || subjectKey.isBlank() || updatedAt.isBefore(createdAt)) { throw new IllegalArgumentException("旧记录标识或记录时间顺序无效。"); }
        }
    }

    /**
     * 未转成个人事实的已知类型，原文继续由旧行保留。
     * @param id 原记录标识
     * @param type 原类型名
     * @author zExNocs
     * @date 2026/10/29
     * @since 4.0.0
     * @version 4.5.0-l
     */
    public record Skipped(String id, String type) {
        /**
         * 拒绝没有定位或类型的未导入项。
         * @param id 原标识
         * @param type 原类型
         * @throws NullPointerException 当字段为空时
         * @throws IllegalArgumentException 当字段为空白时
         */
        public Skipped {
            Objects.requireNonNull(id, "未导入项标识不能为空。"); Objects.requireNonNull(type, "未导入项类型不能为空。");
            if (id.isBlank() || type.isBlank()) { throw new IllegalArgumentException("未导入项定位无效。"); }
        }
    }
}
