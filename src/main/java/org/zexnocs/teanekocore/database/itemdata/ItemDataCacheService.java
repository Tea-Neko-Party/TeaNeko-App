package org.zexnocs.teanekocore.database.itemdata;

import lombok.Setter;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.cache.ConcurrentMapCacheContainer;
import org.zexnocs.teanekocore.cache.interfaces.ICacheService;
import org.zexnocs.teanekocore.database.itemdata.data.ItemDataDTO;
import org.zexnocs.teanekocore.database.itemdata.interfaces.IItemDataCacheService;
import org.zexnocs.teanekocore.database.itemdata.interfaces.IItemDataDTO;
import org.zexnocs.teanekocore.database.itemdata.metadata.IItemMetadata;
import org.zexnocs.teanekocore.framework.pair.HashPair;
import org.zexnocs.teanekocore.framework.pair.Pair;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 物品数据缓存服务实现类。
 * <br>更新日志：
 * <br>4.1.3: 不再手动将 dto 放入到 group 缓存中，而是在创建该缓存时自动将 dto 放入到 group 缓存中。
 *
 * @author zExNocs
 * @date 2026/02/16
 * @since 4.0.0
 * @version 4.1.3
 */
@Service
public class ItemDataCacheService implements IItemDataCacheService {

    /// 缓存 (namespace, ownerId) → cache 的映射。
    private final ConcurrentMapCacheContainer<Pair<String, UUID>, CacheHolder> itemDataDtoCache;

    @Autowired
    public ItemDataCacheService(ICacheService iCacheService) {
        this.itemDataDtoCache = ConcurrentMapCacheContainer.of(iCacheService);
    }

    /**
     * 创建物品数据传输对象 DTO 的缓存
     *
     * @param dto 物品数据对象
     */
    @Override
    public void putCache(ItemDataDTO<? extends IItemMetadata> dto) {
        var key = new HashPair<>(dto.getNamespace(), dto.getOwnerId());
        itemDataDtoCache.compute(key, (k, v) -> {
            if (v == null) {
                v = new CacheHolder();
            }
            v.addType2DtoCache(dto.getType(), dto);
            return v;
        });
    }

    /**
     * 创建 types 集合。一般是由数据库中查询出所有的 type 来创建该集合的缓存。
     *
     * @param namespace 物品命名空间
     * @param ownerId   物品拥有者 ID
     * @param types     物品类型集合
     */
    @Override
    public void putTypes(String namespace, UUID ownerId, Set<String> types) {
        var key = new HashPair<>(namespace, ownerId);
        itemDataDtoCache.compute(key, (k, v) -> {
            if (v == null) {
                v = new CacheHolder();
            }
            Set<String> newSet = ConcurrentHashMap.newKeySet(types.size());
            newSet.addAll(types);
            v.setTypesCache(newSet);
            return v;
        });
    }

    /**
     * 根据 namespace, ownerId, type 获取物品数据传输对象 DTO
     *
     * @param namespace 物品命名空间
     * @param ownerId   用户 ID
     * @param type      类型
     * @return 物品数据传输对象
     */
    @Override
    public @Nullable IItemDataDTO<? extends IItemMetadata> getCache(String namespace, UUID ownerId, String type) {
        var key = new HashPair<>(namespace, ownerId);
        var cacheHolder = itemDataDtoCache.get(key);
        if (cacheHolder == null) {
            return null;
        }
        return cacheHolder.type2UuidCache.get(type);
    }

    /**
     * 根据 (namespace, ownerId) 获取物品类型集合
     *
     * @param namespace 物品命名空间
     * @param ownerId   物品拥有者 ID
     * @return 物品 UUID 列表
     */
    @Override
    public @Nullable Set<String> getTypes(String namespace, UUID ownerId) {
        var key = new HashPair<>(namespace, ownerId);
        var cacheHolder = itemDataDtoCache.get(key);
        if (cacheHolder == null) {
            return null;
        }
        return cacheHolder.getTypesCache();
    }


    /**
     * Item Data 缓存器。
     * 属于一个 (namespace, ownerID) 下的所有 type 物品缓存器。
     *
     * @author zExNocs
     * @date 2026/03/10
     * @since 4.1.3
     */
    private static class CacheHolder {
        /**
         * 根据 type 获取 dto 的缓存
         */
        private final Map<String, IItemDataDTO<?>> type2UuidCache = new ConcurrentHashMap<>();

        /**
         * 获取所有 type 集合。
         * 只有当查询到 type 集合时才会创建该集合的缓存。
         * 需要从数据库中查询出所有的 type 来创建该集合的缓存。
         */
        @Setter
        private volatile Set<String> typesCache = null;

        /**
         * 添加一个 type → dto 的映射
         *
         * @param type 物品类型
         * @param dto 物品数据对象
         */
        public void addType2DtoCache(String type, IItemDataDTO<?> dto) {
            type2UuidCache.put(type, dto);
            // 如果 types 集合不为空，则将该 type 添加到 types 集合中
            if (typesCache != null) {
                typesCache.add(type);
            }
        }

        /**
         * 获取所有的 type 集合
         *
         * @return type 集合
         */
        @Nullable
        public Set<String> getTypesCache() {
            if(typesCache == null) {
                return null;
            }
            // 合并 type2UuidCache 中的 type 到 typesCache 中
            typesCache.addAll(type2UuidCache.keySet());
            return new HashSet<>(typesCache);
        }
    }
}
