package org.zexnocs.teanekocore.database.itemdata.data;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * 一个线程安全的物品数据类
 * 硬性要求物品的个数不能为负数
 * 此外，只能存在一种物品数据对应 (ownerId, namespace, type) 三元组
 *
 * @author zExNocs
 * @date 2026/02/16
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
@Entity
@Table(name = "item_data",
       uniqueConstraints = @UniqueConstraint(columnNames = {"owner_id", "namespace", "type"}))
public class ItemDataObject {
    /// 物品的唯一标识
    @Id
    @GeneratedValue(generator = "UUID")
    @Column(name = "uuid", updatable = false, nullable = false)
    private UUID uuid;

    /// 物品拥有者的 ID
    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    /// 物品隶属的命名空间
    @Column(name = "namespace", nullable = false)
    private String namespace;

    /// 物品的种类 ID
    @Column(name = "type", nullable = false)
    private String type;

    /// 物品的 meta 数据类型，用于将 JSON 反序列化为对应的对象
    @Column(name = "metadata_type")
    private String metadataType;

    /// 物品的 meta 数据，使用 JSON 格式存储
    /// 需要与 metadata_type 一致
    /// 用于在原有的物品数据上附加额外的信息
    @Column(name = "metadata", columnDefinition = "MEDIUMTEXT")
    private String metadata;

    /// 物品的个数
    @Column(name = "count", nullable = false)
    private int count;

    /// 乐观锁版本号。
    /// 用于解决并发问题。
    @Version
    @Column(name = "version")
    private long version;

    /**
     * 重写 equals 方法，用于判断两个 item 是否相同。
     * @param obj 比较对象
     * @return 是否相同
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ItemDataObject other)) return false;
        return uuid != null && uuid.equals(other.uuid);
    }

    /**
     * 重写hashCode方法，用于计算hash值。
     */
    @Override
    public int hashCode() {
        return uuid != null ? uuid.hashCode() : System.identityHashCode(this);
    }
}
