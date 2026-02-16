package org.zexnocs.teanekocore.database.itemdata.interfaces;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekocore.database.itemdata.exception.InvalidMetadataTypeException;
import org.zexnocs.teanekocore.database.itemdata.exception.ItemDataNotFoundException;
import org.zexnocs.teanekocore.database.itemdata.metadata.IItemMetadata;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 物品数据服务接口，提供获取和创建物品数据传输对象的方法。
 *
 * @author zExNocs
 * @date 2026/02/16
 */
public interface IItemDataService {
    /**
     * 根据 UUID 获取物品数据传输对象
     * @param uuid 物品唯一标识符
     * @param clazz 物品元数据类型类
     * @return 物品数据传输对象
     * @throws InvalidMetadataTypeException 无效的元数据类型异常
     * @throws ItemDataNotFoundException 物品数据未找到异常
     * @throws ClassCastException 类转换异常
     */
    <T extends IItemMetadata> IItemDataDTO<T> getByUuid(UUID uuid, Class<T> clazz)
        throws InvalidMetadataTypeException, ItemDataNotFoundException, ClassCastException;

    /**
     * 创建一个新的物品数据传输对象
     * @param ownerId 拥有者 ID
     * @param namespace 命名空间
     * @param type 物品类型
     * @param count 初始数量
     * @param metadata 物品元数据
     * @return 新创建的物品数据传输对象
     * @param <T> 物品元数据类型
     */
    <T extends IItemMetadata> CompletableFuture<IItemDataDTO<T>> getOrCreate(
            UUID ownerId,
            String namespace,
            String type,
            int count,
            T metadata
    );

    /**
     * 获取 ownerId 下对应的 namespace 全部物品数据传输对象映射
     *
     * @param ownerId   拥有者 ID
     * @param namespace 命名空间
     * @return type → 物品数据传输对象 映射。
     */
    @NonNull
    Map<String, IItemDataDTO<?>> getMapByOwnerNamespace(UUID ownerId, String namespace);

    /**
     * 获取 namespace 下对应 type 的所有物品数据传输对象映射
     * @param namespace 命名空间
     * @param type 物品类型
     * @return ownerId → 物品数据传输对象 映射。
     */
    @NonNull
    Map<UUID, IItemDataDTO<?>> getMapByNamespaceType(String namespace, String type);

    /**
     * 获取 ownerId 下对应的 namespace 下 type 类型的物品数据传输对象
     * @param ownerId 拥有者 ID
     * @param namespace 命名空间
     * @param type 物品类型
     * @return 物品数据传输对象列表。如果不存在则返回 null。
     */
    @Nullable
    IItemDataDTO<?> getByOwnerNamespaceType(UUID ownerId, String namespace, String type);
}
