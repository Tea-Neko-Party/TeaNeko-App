package org.zexnocs.teanekocore.database.itemdata.interfaces;

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekocore.database.itemdata.data.ItemDataDTO;
import org.zexnocs.teanekocore.database.itemdata.metadata.IItemMetadata;

import java.util.Set;
import java.util.UUID;

/**
 * 物品数据缓存服务接口，提供了创建、获取和删除物品数据缓存的方法。
 *
 * @author zExNocs
 * @date 2026/02/16
 */
public interface IItemDataCacheService {
    /**
     * 创建物品数据传输对象 DTO 的缓存
     *
     * @param dto 物品数据对象
     */
    void putCache(ItemDataDTO<? extends IItemMetadata> dto);

    /**
     * 创建 types 集合。一般是由数据库中查询出所有的 type 来创建该集合的缓存。
     *
     * @param namespace 物品命名空间
     * @param ownerId   物品拥有者 ID
     * @param types     物品类型集合
     */
    void putTypes(String namespace, UUID ownerId, Set<String> types);

    /**
     * 根据 namespace, ownerId, type 获取物品数据传输对象 DTO
     *
     * @param namespace 物品命名空间
     * @param ownerId   用户 ID
     * @param type      类型
     * @return 物品数据传输对象
     */
    @Nullable IItemDataDTO<? extends IItemMetadata> getCache(String namespace, UUID ownerId, String type);

    /**
     * 根据 (namespace, ownerId) 获取物品类型集合
     *
     * @param namespace 物品命名空间
     * @param ownerId   物品拥有者 ID
     * @return 物品 UUID 列表
     */
    @Nullable Set<String> getTypes(String namespace, UUID ownerId);
}
