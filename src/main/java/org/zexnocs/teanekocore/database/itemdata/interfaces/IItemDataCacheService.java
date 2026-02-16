package org.zexnocs.teanekocore.database.itemdata.interfaces;

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekocore.database.itemdata.data.ItemDataDTO;

import java.util.Map;
import java.util.UUID;

/**
 * 物品数据缓存服务接口，提供了创建、获取和删除物品数据缓存的方法。
 *
 * @author zExNocs
 * @date 2026/02/16
 */
public interface IItemDataCacheService {
    /**
     * 创建 UUID → IItemDataDTO 的缓存
     * @param dto 物品数据对象
     */
    void createCache(ItemDataDTO<?> dto);

    /**
     * 创建 (ownerId, namespace) → UUID集合 的缓存
     * @param ownerId 物品拥有者 ID
     * @param namespace 物品命名空间
     * @param uuids 物品 UUID 集合
     */
    void createOwnerNamespace2UUIDsCache(UUID ownerId,
                                         String namespace,
                                         Map<String, UUID> uuids);

    /**
     * 在现有的 (ownerId, namespace) → UUID 集合缓存中添加新的 UUID 映射
     * 前提是该缓存已经存在
     * @param ownerId 物品拥有者 ID
     * @param namespace 物品命名空间
     * @param type 物品类型
     * @param uuid 物品 UUID
     */
    void addToOwnerNamespace2UUIDsCache(UUID ownerId, String namespace, String type, UUID uuid);

    /**
     * 根据 UUID 获取物品数据传输对象 DTO
     * @param uuid 物品 UUID
     * @return 物品数据传输对象
     */
    @Nullable
    IItemDataDTO<?> getDTOByUUID(UUID uuid);

    /**
     * 根据 (ownerId, namespace) 获取 UUID 集合
     * @param ownerId 物品拥有者 ID
     * @param namespace 物品命名空间
     * @return 物品 UUID 列表
     */
    @Nullable
    Map<String, UUID> getUUIDsByOwnerNamespace(UUID ownerId, String namespace);

    /**
     * 删除 (ownerId, namespace) 下的 UUID 集合缓存
     * @param ownerId 物品拥有者 ID
     * @param namespace 物品命名空间
     */
    void removeOwnerNamespace2UUIDsCache(UUID ownerId, String namespace);

    /**
     * 删除 UUID 对应的物品数据传输对象缓存
     * @param uuid 物品 UUID
     */
    void removeDTOByUUID(UUID uuid);
}
