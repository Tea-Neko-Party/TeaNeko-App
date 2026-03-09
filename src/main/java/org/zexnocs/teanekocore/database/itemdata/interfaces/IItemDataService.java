package org.zexnocs.teanekocore.database.itemdata.interfaces;

import org.jspecify.annotations.NonNull;
import org.zexnocs.teanekocore.database.itemdata.metadata.IItemMetadata;

import java.util.Map;
import java.util.UUID;

/**
 * 物品数据服务接口，提供获取和创建物品数据传输对象的方法。
 *
 * @see IItemDataDTO
 * @see IItemMetadata
 * @author zExNocs
 * @date 2026/02/16
 * @since 4.0.0
 */
public interface IItemDataService {
    /**
     * 获取一个物品数据传输对象。
     * <br>如果不存在该数据，依然会返回一个 DTO，但是其数量为 0，且其元数据为 null。
     *
     * @param namespace 命名空间
     * @param ownerId   拥有者 ID
     * @param type      物品类型
     * @param clazz     物品元数据类型类
     * @return {@link IItemDataDTO }<{@link T }>
     */
    @NonNull
    <T extends IItemMetadata> IItemDataDTO<? extends T> get(
            String namespace, UUID ownerId,
            String type,
            Class<T> clazz
    );

    /**
     * 获取一个物品数据传输对象。
     * <br>如果不存在该数据，依然会返回一个 DTO，但是其数量为 0，且其元数据为 null。
     *
     * @param namespace 命名空间
     * @param ownerId   拥有者 ID
     * @param type      物品类型
     * @return {@link IItemDataDTO }<{@link ? }>
     */
    @NonNull
    default IItemDataDTO<?> get(String namespace, UUID ownerId, String type) {
        return get(namespace, ownerId, type, IItemMetadata.class);
    }

    /**
     * 获取 ownerId 下对应的 namespace 全部物品数据传输对象映射
     *
     * @param namespace 命名空间
     * @param ownerId   拥有者 ID
     * @return type → 物品数据传输对象 映射。
     */
    @NonNull
    Map<String, IItemDataDTO<? extends IItemMetadata>> getByOwner(String namespace, UUID ownerId);

    /**
     * 获取 namespace 下对应 type 的所有物品数据传输对象映射
     *
     * @param namespace 命名空间
     * @param type 物品类型
     * @return ownerId → 物品数据传输对象 映射。
     */
    @NonNull
    Map<UUID, IItemDataDTO<? extends IItemMetadata>> getByType(String namespace, String type);
}
