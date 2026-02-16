package org.zexnocs.teanekocore.database.itemdata;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.cache.ConcurrentMapCacheContainer;
import org.zexnocs.teanekocore.cache.interfaces.ICacheService;
import org.zexnocs.teanekocore.database.itemdata.data.ItemDataDTO;
import org.zexnocs.teanekocore.database.itemdata.interfaces.IItemDataCacheService;
import org.zexnocs.teanekocore.database.itemdata.interfaces.IItemDataDTO;
import org.zexnocs.teanekocore.framework.pair.HashPair;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 物品数据缓存服务实现类。
 *
 * @author zExNocs
 * @date 2026/02/16
 */
@Service
public class ItemDataCacheService implements IItemDataCacheService {

    /// 缓存 UUID 到 IItemDataDTO 的映射
    private final ConcurrentMapCacheContainer<UUID, IItemDataDTO<?>> itemDataDtoCache;

    /// 缓存 (ownerId, namespace) 到 (type, UUID) 映射的缓存
    private final ConcurrentMapCacheContainer<HashPair<UUID, String>, ConcurrentHashMap<String, UUID>>
            ownerNamespace2UuidTypeCache;

    @Autowired
    public ItemDataCacheService(ICacheService iCacheService) {
        this.itemDataDtoCache = ConcurrentMapCacheContainer.of(iCacheService);
        this.ownerNamespace2UuidTypeCache = ConcurrentMapCacheContainer.of(iCacheService);
    }

    /**
     * 创建物品数据传输对象 DTO 的缓存
     * @param dto 物品数据对象
     */
    @Override
    public void createCache(ItemDataDTO<?> dto) {
        itemDataDtoCache.put(dto.getUuid(), dto);
    }

    /**
     * 创建 (ownerId, namespace) → UUID 集合的缓存
     * @param ownerId 物品拥有者 ID
     * @param namespace 物品命名空间
     * @param uuids 物品 UUID 集合
     */
    @Override
    public void createOwnerNamespace2UUIDsCache(UUID ownerId, String namespace, Map<String, UUID> uuids) {
        var key = new HashPair<>(ownerId, namespace);
        var copy = new ConcurrentHashMap<String, UUID>(uuids.size());
        copy.putAll(uuids);
        ownerNamespace2UuidTypeCache.put(key, copy);
    }

    /**
     * 在现有的 (ownerId, namespace) → UUID 集合缓存中添加新的 UUID 映射
     * 前提是该缓存已经存在
     * @param ownerId 物品拥有者 ID
     * @param namespace 物品命名空间
     * @param type 物品类型
     * @param uuid 物品 UUID
     */
    @Override
    public void addToOwnerNamespace2UUIDsCache(UUID ownerId, String namespace, String type, UUID uuid) {
        var key = new HashPair<>(ownerId, namespace);
        ownerNamespace2UuidTypeCache.computeIfPresent(key, (k, v) -> {
            v.put(type, uuid);
            return v;
        });
    }

    /**
     * 根据 UUID 获取物品数据传输对象 DTO
     * @param uuid 物品 UUID
     * @return 物品数据传输对象
     */
    @Override
    public IItemDataDTO<?> getDTOByUUID(UUID uuid) {
        return itemDataDtoCache.get(uuid);
    }

    /**
     * 根据 (ownerId, namespace) 获取 UUID 集合
     *
     * @param ownerId   物品拥有者 ID
     * @param namespace 物品命名空间
     * @return 物品 UUID 列表
     */
    @Override
    public Map<String, UUID> getUUIDsByOwnerNamespace(UUID ownerId, String namespace) {
        var key = new HashPair<>(ownerId, namespace);
        var value = ownerNamespace2UuidTypeCache.get(key);
        if(value == null) {
            return null;
        }
        return Collections.unmodifiableMap(value);
    }

    /**
     * 删除 (ownerId, namespace) 下的 UUID 集合缓存
     * @param ownerId 物品拥有者 ID
     * @param namespace 物品命名空间
     */
    @Override
    public void removeOwnerNamespace2UUIDsCache(UUID ownerId, String namespace) {
        var key = new HashPair<>(ownerId, namespace);
        ownerNamespace2UuidTypeCache.remove(key);
    }

    /**
     * 删除 UUID 对应的物品数据传输对象缓存
     * @param uuid 物品 UUID
     */
    @Override
    public void removeDTOByUUID(UUID uuid) {
        itemDataDtoCache.remove(uuid);
    }
}
