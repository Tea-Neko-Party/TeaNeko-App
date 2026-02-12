package org.zexnocs.teanekocore.cache;

import org.zexnocs.teanekocore.cache.interfaces.ICacheContainer;
import org.zexnocs.teanekocore.cache.interfaces.ICacheData;
import org.zexnocs.teanekocore.cache.interfaces.ICacheDataFactory;
import org.zexnocs.teanekocore.cache.interfaces.ICacheService;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * 基于 ConcurrentHashMap 实现的缓存类，所有缓存资源的清理时间是共享的。
 * <param K> 键类型
 * <param V> 值类型
 *
 * @author zExNocs
 * @date 2026/02/10
 */
public class ConcurrentMapCacheContainer<K, V> implements ICacheContainer {
    /// 默认创建实例方法
    public static <K, V> ConcurrentMapCacheContainer<K, V> of(ICacheService cacheService) {
        // 默认过期时间为 1 小时，清理间隔为 1 分钟，参与手动清理
        return of(cacheService, 3600_000L, 60_000L, true);
    }

    /// 指定过期时间的创建实例方法
    /// 使用 1 / 60 的过期时间作为清理间隔时间，参与手动清理
    public static <K, V> ConcurrentMapCacheContainer<K, V> of(ICacheService cacheService,
                                                              long expireTimeMs) {
        return of(cacheService, expireTimeMs, expireTimeMs / 60, true);
    }

    /// 指定过期时间和清理间隔时间的创建实例方法
    /// 参与手动清理
    public static <K, V> ConcurrentMapCacheContainer<K, V> of(ICacheService cacheService,
                                                              long expireTimeMs,
                                                              long cleanIntervalMs) {
        return of(cacheService, expireTimeMs, cleanIntervalMs, true);
    }

    /// 指定过期时间
    /// 使用 1 / 60 的过期时间作为清理间隔时间
    /// 和是否参与手动清理的创建实例方法
    public static <K, V> ConcurrentMapCacheContainer<K, V> of(ICacheService cacheService,
                                                              long expireTimeMs,
                                                              boolean participateInManualClean) {
        return of(cacheService, expireTimeMs, expireTimeMs / 60, participateInManualClean);
    }

    /**
     * 完整的创建实例方法
     * @param cacheService 缓存服务实例
     * @param expireTimeMs 单个数据的过期时间，单位毫秒
     * @param cleanIntervalMs 清理间隔时间，单位毫秒
     * @param participateInManualClean 是否参与手动清理
     * @return 创建好的 ConcurrentMapCacheContainer 实例
     * @param <K> 键类型
     * @param <V> 值类型
     */
    public static <K, V> ConcurrentMapCacheContainer<K, V> of(ICacheService cacheService,
                                                              long expireTimeMs,
                                                              long cleanIntervalMs,
                                                              boolean participateInManualClean) {
        var instance = new ConcurrentMapCacheContainer<K, V>(
                participateInManualClean,
                cleanIntervalMs,
                new CacheDataFactory<>(expireTimeMs));
        cacheService.addCache(instance);
        return instance;
    }

    /**
     * 使用一个 ICacheDataFactory 来创建 ConcurrentMapCacheContainer 实例
     * @param cacheService 缓存服务实例
     * @param cleanIntervalMs 清理间隔时间，单位毫秒
     * @param iCacheDataFactory 创建 CacheData 的工厂类
     * @param participateInManualClean 是否参与手动清理
     * @return 创建好的 ConcurrentMapCacheContainer 实例
     * @param <K> 键类型
     * @param <V> 值类型
     */
    public static <K, V> ConcurrentMapCacheContainer<K, V> of(ICacheService cacheService,
                                                              long cleanIntervalMs,
                                                              ICacheDataFactory<V> iCacheDataFactory,
                                                              boolean participateInManualClean) {
        var instance = new ConcurrentMapCacheContainer<K, V>(
                participateInManualClean,
                cleanIntervalMs,
                iCacheDataFactory);
        cacheService.addCache(instance);
        return instance;
    }

    // -------------------------------

    /// 清理间隔时间，单位毫秒
    private final long cleanIntervalMs;

    /// 上次清理的时间，单位毫秒
    private volatile long lastCleanTimeMs;

    /// 是否参与手动清理
    private final boolean participateInManualClean;

    /// 缓存存储
    private final Map<K, ICacheData<V>> cache = new ConcurrentHashMap<>();

    /// 创建 CacheData 的工厂类
    private final ICacheDataFactory<V> iCacheDataFactory;

    private ConcurrentMapCacheContainer(boolean participateInManualClean,
                                        long cleanIntervalMs,
                                        ICacheDataFactory<V> iCacheDataFactory) {
        this.cleanIntervalMs = cleanIntervalMs;
        this.participateInManualClean = participateInManualClean;
        this.lastCleanTimeMs = System.currentTimeMillis();
        this.iCacheDataFactory = iCacheDataFactory;
    }

    /**
     * 添加一个手动定义的缓存项
     *
     * @param key   键
     * @param cache 缓存数据，包含值和过期时间
     */
    public void put(K key, ICacheData<V> cache) {
        cache.updateAccessTime(System.currentTimeMillis());
        this.cache.put(key, cache);
    }

    /**
     * 添加一个使用默认过期时间的缓存项
     *
     * @param key   键
     * @param value 值
     */
    public void put(K key, V value) {
        cache.put(key, iCacheDataFactory.createCacheData(value));
    }

    /**
     * 访问一个缓存项，并更新其最后访问时间
     *
     * @param key 键
     * @return 值，如果不存在则返回 null
     */
    public V get(K key) {
        var data = cache.get(key);
        if (data != null) {
            data.updateAccessTime(System.currentTimeMillis());
            return data.getValue();
        }
        return null;
    }

    /**
     * computeIfAbsent 方法
     *
     * @param key             键
     * @param mappingFunction 映射函数
     * @return 值
     */
    public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
        var data = cache.computeIfAbsent(key, k -> iCacheDataFactory.createCacheData(mappingFunction.apply(k)));
        data.updateAccessTime(System.currentTimeMillis());
        return data.getValue();
    }

    /**
     * computeIfPresent 方法
     *
     * @param key               键
     * @param remappingFunction 重映射函数
     * @return 值，如果不存在则返回 null
     */
    public V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        var data = cache.computeIfPresent(key, (k, v) -> {
            var newValue = remappingFunction.apply(k, v.getValue());
            if (newValue != null) {
                return iCacheDataFactory.createCacheData(newValue);
            } else {
                return null;
            }
        });
        if (data != null) {
            data.updateAccessTime(System.currentTimeMillis());
            return data.getValue();
        }
        return null;
    }

    /**
     * compute 方法
     *
     * @param key               键
     * @param remappingFunction 重映射函数.
     * @return 值，如果不存在则返回 null
     */
    public V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        var data = cache.compute(key, (k, v) -> {
            V oldValue = (v != null) ? v.getValue() : null;
            var newValue = remappingFunction.apply(k, oldValue);
            if (newValue != null) {
                return iCacheDataFactory.createCacheData(newValue);
            } else {
                return null;
            }
        });
        if (data != null) {
            data.updateAccessTime(System.currentTimeMillis());
            return data.getValue();
        }
        return null;
    }

    /**
     * 删除某个键
     *
     * @param key 键
     */
    public void remove(K key) {
        cache.remove(key);
    }

    /**
     * 是否包含某个键
     *
     * @param key 键
     * @return true 如果包含，否则 false
     */
    public boolean containsKey(K key) {
        return cache.containsKey(key);
    }

    /**
     * 如果开启了参与手动清理，则清理所有缓存
     */
    @Override
    public void manualClean() {
        if (participateInManualClean) {
            cache.clear();
        }
    }

    /**
     * 自动清理缓存
     *
     * @param currentTimeMs 当前时间，单位毫秒
     */
    @Override
    public synchronized void autoClean(long currentTimeMs) {
        // 如果距离上次清理时间未到达清理间隔时间，则跳过清理
        if (currentTimeMs - lastCleanTimeMs < cleanIntervalMs) {
            return;
        }
        lastCleanTimeMs = currentTimeMs;
        // 清理过期缓存
        cache.entrySet().removeIf(entry -> {
            var value = entry.getValue();
            if(value.isExpired(currentTimeMs)) {
                // 执行过期后的处理方法
                return value.onExpire(currentTimeMs, value.getValue());
            }
            return false;
        });
    }
}
