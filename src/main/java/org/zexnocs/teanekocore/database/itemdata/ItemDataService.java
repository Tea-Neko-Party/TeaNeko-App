package org.zexnocs.teanekocore.database.itemdata;

import lombok.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.database.itemdata.interfaces.IItemDataCacheService;
import org.zexnocs.teanekocore.database.itemdata.interfaces.IItemDataDTO;
import org.zexnocs.teanekocore.database.itemdata.interfaces.IItemDataDtoService;
import org.zexnocs.teanekocore.database.itemdata.interfaces.IItemDataService;
import org.zexnocs.teanekocore.database.itemdata.metadata.IItemMetadata;

import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 物品数据服务类，提供对物品数据的获取和创建功能。
 *
 * <br>更新日志：
 * <br>4.1.3: 重构数据，不再使用 <@code TaskFuture> 形式来获取数据，而是直接返回数据对象。创建对象会在第一次设置值时真正创建。
 * @see org.zexnocs.teanekocore.database.itemdata.data.ItemDataDTO 一个实现了 IItemDataDTO 接口的物品数据传输对象类
 * @see IItemDataDTO
 * @see IItemMetadata
 * @author zExNocs
 * @date 2026/02/16
 * @since 4.0.0
 * @version 4.1.3
 */
@Service
public class ItemDataService implements IItemDataService {
    private final ItemDataRepository itemDataRepository;
    private final IItemDataCacheService iItemDataCacheService;
    private final IItemDataDtoService iItemDataDtoService;

    @Autowired
    public ItemDataService(ItemDataRepository itemDataRepository,
                           IItemDataCacheService iItemDataCacheService,
                           IItemDataDtoService iItemDataDtoService) {
        this.itemDataRepository = itemDataRepository;
        this.iItemDataCacheService = iItemDataCacheService;
        this.iItemDataDtoService = iItemDataDtoService;
    }

    /**
     * 获取一个新的物品数据传输对象。如果不存在该数据，依然会返回一个 DTO，但是其数量为 0，且其元数据为 null。
     *
     * @param namespace 命名空间
     * @param ownerId   拥有者 ID
     * @param type      物品类型
     * @param clazz     物品元数据类型类
     * @return {@link IItemDataDTO }<{@link T }>
     */
    @SuppressWarnings("unchecked")
    @Override
    @NonNull
    public <T extends IItemMetadata> IItemDataDTO<? extends T> get(String namespace, UUID ownerId, String type, Class<T> clazz) {
        // 先从已有的缓存获取
        var cache = iItemDataCacheService.getCache(namespace, ownerId, type);
        if(cache != null) {
            return (IItemDataDTO<? extends T>) cache;
        }
        // 缓存没有，则尝试从数据库中获取并写入缓存
        var itemDataOptional = itemDataRepository.findByOwnerIdAndNamespaceAndType(ownerId, namespace, type);
        if(itemDataOptional != null) {
            var dto = iItemDataDtoService.createItemDataDto(itemDataOptional, clazz);
            iItemDataCacheService.putCache(dto);
            return dto;
        }
        // 否则创建一个新的 DTO 并写入缓存（但不创建数据库对象，直到设置值时才创建）
        var dto = iItemDataDtoService.createItemDataDto(namespace, ownerId, type, clazz);
        iItemDataCacheService.putCache(dto);
        return dto;
    }

    /**
     * 获取 ownerId 下对应的 namespace 全部物品数据传输对象列表
     *
     * @param namespace 命名空间
     * @param ownerId   拥有者 ID
     * @return 物品数据传输对象列表
     */
    @Override
    public @NonNull Map<String, IItemDataDTO<? extends IItemMetadata>> getByOwner(String namespace, UUID ownerId) {
        // 尝试从缓存中获取该 (ownerId, namespace) 下的 type 列表
        var types = iItemDataCacheService.getTypes(namespace, ownerId);
        if(types != null) {
            Map<String, IItemDataDTO<? extends IItemMetadata>> result = new ConcurrentHashMap<>();
            for (var type : types) {
                var dto = get(namespace, ownerId, type);
                result.put(type, dto);
            }
            return result;
        }
        // ----------------------
        // 否则从数据库中获取
        var itemDataObjects = itemDataRepository.findAllByOwnerIdAndNamespace(ownerId, namespace);
        // 构建 UUID 列表缓存和输出列表
        var caches = new HashSet<String>();
        var result = new ConcurrentHashMap<String, IItemDataDTO<? extends IItemMetadata>>();
        for(var object: itemDataObjects) {
            caches.add(object.getType());
            // 从缓存或者数据库中获取 DTO
            var dto = get(namespace, ownerId, object.getType());
            result.put(object.getType(), dto);
        }
        // 放入缓存
        iItemDataCacheService.putTypes(namespace, ownerId, caches);
        return result;
    }


    /**
     * 获取 namespace 下对应 type 的所有物品数据传输对象映射
     *
     * @param namespace 命名空间
     * @param type 物品类型
     * @return ownerId → 物品数据传输对象 映射。
     */
    @Override
    public @NonNull Map<UUID, IItemDataDTO<? extends IItemMetadata>> getByType(String namespace, String type) {
        var itemDataObjects = itemDataRepository.findAllByNamespaceAndType(namespace, type);
        var result = new ConcurrentHashMap<UUID, IItemDataDTO<? extends IItemMetadata>>();
        for(var object: itemDataObjects) {
            var dto = get(namespace, object.getOwnerId(), type);
            result.put(object.getOwnerId(), dto);
        }
        return result;
    }
}
