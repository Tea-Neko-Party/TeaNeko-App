package org.zexnocs.teanekocore.cache.interfaces;

/**
 * 一个创建 ICacheData 的工厂接口。
 * @param <V> 创建的 ICacheData 中存储的值的类型
 */
public interface ICacheDataFactory<V> {
    /**
     * 创建一个 ICacheData 对象。
     * @param value ICacheData 中存储的值
     * @return 创建好的 ICacheData 对象
     */
    ICacheData<V> createCacheData(V value);
}
