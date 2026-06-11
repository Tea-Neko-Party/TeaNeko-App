package org.zexnocs.teanekocore.cache;

import lombok.Getter;
import org.zexnocs.teanekocore.cache.interfaces.ICacheData;

import java.time.Duration;
import java.time.Instant;
import java.util.function.BiFunction;

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
    private volatile Instant lastUpdate;

    /// 过期时间，单位毫秒
    private final Duration expireTime;

    /**
     * 过期后的处理方法，默认不做任何处理
     * 注意只有自动过期时才会调用这个方法，手动清理不会调用这个方法。
     * 该方法将在与自动清理在同一个线程中调用，注意：
     * 1. 请使用轻量化的操作，避免堵塞清理操作；例如日志记录、简单的资源释放等。
     * 2. 如果需要较重的操作，请在函数里另外在 taskService 中请求一个新的线程来执行。
     * 3. 请不要在该方法里尝试访问、修改缓存，否则会抛出 ConcurrentModificationException
     * 返回 true: 表示会正常删除该缓存
     * 返回 false: 表示暂时不删除该缓存。
     * 如果没有更新 access time 则会在下次清理时再次调用 onExpire 方法。
     */
    private final BiFunction<Instant, V, Boolean> onExpireFunction;

    /**
     * 构造函数
     * @param value 存储的值
     * @param expireTimeMs 过期时间，单位毫秒
     */
    public CacheData(V value, long expireTimeMs) {
        this(value, Duration.ofMillis(expireTimeMs));
    }

    /**
     * 构造缓存数据。
     *
     * @param value 存储的值
     * @param expireTime 过期时长
     */
    public CacheData(V value, Duration expireTime) {
        this.value = value;
        this.lastUpdate = Instant.now();
        this.expireTime = expireTime;
        this.onExpireFunction = null;
    }

    /**
     * 构造函数，带过期后处理函数
     * @param value 存储的值
     * @param expireTimeMs 过期时间，单位毫秒
     * @param onExpireFunction 过期后的处理函数，接受当前时间和当前值作为参数
     */
    public CacheData(V value, long expireTimeMs, BiFunction<Instant, V, Boolean> onExpireFunction) {
        this(value, Duration.ofMillis(expireTimeMs), onExpireFunction);
    }

    /**
     * 构造带过期回调的缓存数据。
     *
     * @param value 存储的值
     * @param expireTime 过期时长
     * @param onExpireFunction 过期回调
     */
    public CacheData(V value, Duration expireTime, BiFunction<Instant, V, Boolean> onExpireFunction) {
        this.value = value;
        this.lastUpdate = Instant.now();
        this.expireTime = expireTime;
        this.onExpireFunction = onExpireFunction;
    }

    /**
     * 更新缓存的访问时间
     *
     * @param currentTime 当前时间点
     */
    @Override
    public void updateAccessTime(Instant currentTime) {
        this.lastUpdate = currentTime;
    }

    /**
     * 是否过期
     * @param currentTime 当前时间点
     * @return true 如果过期，否则 false
     */
    public boolean isExpired(Instant currentTime) {
        return currentTime.isAfter(lastUpdate.plus(expireTime));
    }

    /**
     * 过期后的处理方法，默认不做任何处理
     * 注意只有自动过期时才会调用这个方法，手动清理不会调用这个方法。
     * 该方法将在与自动清理在同一个线程中调用，注意：
     * 1. 请使用轻量化的操作，避免堵塞清理操作；例如日志记录、简单的资源释放等。
     * 2. 如果需要较重的操作，请在函数里另外在 taskService 中请求一个新的线程来执行。
     * 3. 请不要在该方法里尝试访问、修改缓存，否则会抛出 ConcurrentModificationException；如果要删除请让其返回 true 清理
     * @param currentTime 当前时间点
     * @param value 当前缓存的值，过期后可能需要进行一些清理或其他操作
     * @return true 表示会正常删除该缓存；false 表示暂时不删除该缓存。如果没有更新 access time 则会在下次清理时再次调用 onExpire 方法。
     */
    @Override
    public boolean onExpire(Instant currentTime, V value) {
        if (onExpireFunction != null) {
            return onExpireFunction.apply(currentTime, value);
        }
        // 正常删除该缓存
        return true;
    }
}
