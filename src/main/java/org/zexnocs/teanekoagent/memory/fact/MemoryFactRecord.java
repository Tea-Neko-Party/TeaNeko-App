package org.zexnocs.teanekoagent.memory.fact;

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.memory.namespace.MemoryNamespaceId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 不可变的长期事实记录，主语和来源独立保留，画像由这些证据另行构建。
 * @param id 稳定记录 ID
 * @param namespaceId 共享业务空间
 * @param generation 数据代次
 * @param revision 内容版本
 * @param subjectId 事实主体 UUID
 * @param value 事实/偏好内容
 * @param source 首次接受来源；重复来源另存操作回执
 * @param createdAt 可信记录时间
 * @param updatedAt 最近更新时刻
 * @param locked 管理锁定状态，追加不能覆盖
 * @param provenance 可选外部原始证据，不作为可信操作者
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
public record MemoryFactRecord(UUID id, MemoryNamespaceId namespaceId, long generation, long revision, UUID subjectId,
        MemoryFactValue value, MemoryFactSource source, Instant createdAt, Instant updatedAt, boolean locked,
        @Nullable MemoryFactProvenance provenance) {
    /**
     * 校验定位、版本及记录时间顺序，不把事件时间强制改成创建时间。
     * @param id 记录 ID
     * @param namespaceId 业务空间
     * @param generation 数据代次
     * @param revision 内容版本
     * @param subjectId 事实主体
     * @param value 内容
     * @param source 首次来源
     * @param createdAt 记录时刻
     * @param updatedAt 更新时刻
     * @param locked 管理锁定状态
     * @param provenance 外部证据
     * @throws NullPointerException 当必需字段为空时
     * @throws IllegalArgumentException 当版本或记录时间顺序无效时
     */
    public MemoryFactRecord {
        Objects.requireNonNull(id, "事实记录 ID 不能为空。"); Objects.requireNonNull(namespaceId, "事实空间不能为空。");
        Objects.requireNonNull(subjectId, "事实主语不能为空。"); Objects.requireNonNull(value, "事实内容不能为空。");
        Objects.requireNonNull(source, "事实来源不能为空。"); Objects.requireNonNull(createdAt, "事实记录时刻不能为空。");
        Objects.requireNonNull(updatedAt, "事实更新时间不能为空。");
        if (generation < 0 || revision < 0 || updatedAt.isBefore(createdAt)) { throw new IllegalArgumentException("事实版本或记录时间顺序无效。"); }
    }

    /**
     * 保留普通追加入口，不为新事实虚构外部来源。
     * @param id 记录 ID
     * @param namespaceId 空间
     * @param generation 代次
     * @param revision 版本
     * @param subjectId 主体
     * @param value 内容
     * @param source 可信来源
     * @param createdAt 创建时刻
     * @param updatedAt 更新时刻
     * @param locked 锁定状态
     */
    public MemoryFactRecord(UUID id, MemoryNamespaceId namespaceId, long generation, long revision, UUID subjectId,
            MemoryFactValue value, MemoryFactSource source, Instant createdAt, Instant updatedAt, boolean locked) {
        this(id, namespaceId, generation, revision, subjectId, value, source, createdAt, updatedAt, locked, null);
    }

    /**
     * 观察有效期，不删除原证据或根据过期记录继续生成画像。
     * @param now 可信当前时刻
     * @return 到期或已过期时为 true
     * @throws NullPointerException 当当前时刻为空时
     */
    public boolean isExpired(Instant now) {
        Objects.requireNonNull(now, "记忆有效期检查时刻不能为空。");
        var expiry = value.expiresAt(); return expiry != null && !expiry.isAfter(now);
    }
}
