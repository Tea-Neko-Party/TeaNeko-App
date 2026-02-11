package org.zexnocs.teanekocore.cache;

import lombok.Getter;
import org.zexnocs.teanekocore.cache.interfaces.ICacheData;

import java.util.function.BiConsumer;

/**
 * 一个基础的缓存数据类，包含一个值和一个过期时间。
 *
 * @author zExNocs
 * @date 2026/02/11
 */
public class CacheData<V> implements ICacheData<V> {
    /// 存储的值
    @Getter
    private final V value;

    /// 上次访问时间，单位毫秒
    private volatile long lastUpdate;

    /// 过期时间，单位毫秒
    private final long expireTimeMs;

    /// 执行过期后的 lambda 函数
    private final BiConsumer<Long, V> onExpireFunction;

    /**
     * 构造函数
     * @param value 存储的值
     * @param expireTimeMs 过期时间，单位毫秒
     */
    public CacheData(V value, long expireTimeMs) {
        this.value = value;
        this.lastUpdate = System.currentTimeMillis();
        this.expireTimeMs = expireTimeMs;
        this.onExpireFunction = null;
    }

    /**
     * 构造函数，带过期后处理函数
     * @param value 存储的值
     * @param expireTimeMs 过期时间，单位毫秒
     * @param onExpireFunction 过期后的处理函数，接受当前时间和当前值作为参数
     */
    public CacheData(V value, long expireTimeMs, BiConsumer<Long, V> onExpireFunction) {
        this.value = value;
        this.lastUpdate = System.currentTimeMillis();
        this.expireTimeMs = expireTimeMs;
        this.onExpireFunction = onExpireFunction;
    }

    /**
     * 更新缓存的访问时间
     *
     * @param currentTimeMs 当前时间，单位毫秒
     */
    @Override
    public void updateAccessTime(long currentTimeMs) {
        this.lastUpdate = currentTimeMs;
    }

    /**
     * 是否过期
     * @param currentTimeMs 当前时间，单位毫秒
     * @return true 如果过期，否则 false
     */
    public boolean isExpired(long currentTimeMs) {
        return currentTimeMs - lastUpdate > expireTimeMs;
    }

    /**
     * 过期后的处理方法，默认不做任何处理
     * 注意只有自动过期时才会调用这个方法，手动清理不会调用这个方法。
     * 该方法将在与自动清理在同一个线程中调用，注意：
     * 1. 请使用轻量化的操作，避免堵塞清理操作；例如日志记录、简单的资源释放等。
     * 2. 如果需要较重的操作，请在函数里另外在 taskService 中请求一个新的线程来执行。
     *
     * @param currentTimeMs 用于传递当前的时间，单位毫秒
     * @param value         当前缓存的值，过期后可能需要进行一些清理或其他操作
     */
    @Override
    public void onExpire(long currentTimeMs, V value) {
        if (onExpireFunction != null) {
            onExpireFunction.accept(currentTimeMs, value);
        }
    }
}
