package org.zexnocs.teanekocore.cache;

import org.zexnocs.teanekocore.cache.interfaces.ICacheData;
import org.zexnocs.teanekocore.cache.interfaces.ICacheDataFactory;

/**
 * 创建 CacheData 的工厂类。
 * 所有 CacheData 使用同一个 expireTimeMs
 *
 * @author zExNocs
 * @date 2026/02/13
 */
public class CacheDataFactory<V> implements ICacheDataFactory<V> {
    /// 单个数据的过期时间，单位毫秒
    private final long expireTimeMs;

    /**
     * 构造函数，指定单个数据的过期时间。
     * @param expireTimeMs 单个数据的过期时间，单位毫秒
     */
    public CacheDataFactory(long expireTimeMs) {
        this.expireTimeMs = expireTimeMs;
    }

    /**
     * 创建一个 ICacheData 对象。
     *
     * @param value ICacheData 中存储的值
     * @return 创建好的 ICacheData 对象
     */
    @Override
    public ICacheData<V> createCacheData(V value) {
        return new CacheData<>(value, expireTimeMs);
    }
}
