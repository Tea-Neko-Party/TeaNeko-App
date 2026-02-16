package org.zexnocs.teanekocore.database.itemdata;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.zexnocs.teanekocore.database.itemdata.data.ItemDataObject;

import java.util.List;
import java.util.UUID;

/**
 * 物品数据仓库接口，提供对物品数据的 CRUD 操作和一些特定的查询方法。
 *
 * @author zExNocs
 * @date 2026/02/16
 */
@Repository
public interface ItemDataRepository extends JpaRepository<ItemDataObject, UUID> {
    /**
     * 通过拥有者 ID 和命名空间获取所有物品数据
     * @param ownerId 拥有者 ID
     * @param namespace 命名空间
     * @return 物品数据列表
     */
    List<ItemDataObject> findAllByOwnerIdAndNamespace(UUID ownerId, String namespace);

    /**
     * 通过 namespace 和 type 获取所有物品数据
     * @param namespace 命名空间
     * @param type 物品类型
     * @return 物品数据列表
     */
    List<ItemDataObject> findAllByNamespaceAndType(String namespace, String type);

    /**
     * 通过拥有者 ID、命名空间和类型获取物品数据
     * @param ownerId 拥有者 ID
     * @param namespace 命名空间
     * @param type 物品类型
     * @return 物品数据对象，如果不存在则返回 null
     */
    ItemDataObject findByOwnerIdAndNamespaceAndType(UUID ownerId, String namespace, String type);

    /**
     * 增加物品数量
     * @param uuid 物品 ID
     * @param delta 增加的数量
     * @return 受影响的行数
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE ItemDataObject i
        SET i.count = i.count + :delta
        WHERE i.uuid = :uuid
    """)
    int incrementItemCount(@Param("uuid") UUID uuid, @Param("delta") int delta);

    /**
     * 减少物品数量
     * 如果物品数量不足，则会返回 0，表示没有任何行受到影响
     * @param uuid 物品 ID
     * @param delta 减少的数量
     * @return 受影响的行数
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE ItemDataObject i
        SET i.count = i.count - :delta
        WHERE i.uuid = :uuid AND i.count >= :delta
    """)
    int decrementItemCount(@Param("uuid") UUID uuid, @Param("delta") int delta);

    /**
     * 更新物品元数据
     * @param uuid 物品 ID
     * @param newMetaData 新的元数据
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE ItemDataObject i
        SET i.metadata = :newMetaData
        WHERE i.uuid = :uuid
    """)
    void updateMetadata(@Param("uuid") UUID uuid, @Param("newMetaData") String newMetaData);

    /**
     * 安全地设置物品数量
     * @param uuid 物品 ID
     * @param newCount 新的物品数量
     * @throws IllegalArgumentException 如果用户不存在
     * @throws ObjectOptimisticLockingFailureException 如果乐观锁更新失败
     */
    @Transactional
    default void safeUpdateCount(UUID uuid, int newCount)
            throws IllegalArgumentException, ObjectOptimisticLockingFailureException {
        ItemDataObject itemDataObject = findById(uuid).orElseThrow(() -> new IllegalArgumentException(
                "Item with UUID " + uuid + " does not exist."));
        itemDataObject.setCount(newCount);
        save(itemDataObject);
    }
}
