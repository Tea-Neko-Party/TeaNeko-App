package org.zexnocs.teanekocore.cache.interfaces;

/**
 * 缓存数据接口：提供缓存需要用到的方法
 *
 * @param <V> 缓存的值类型
 * @author zExNocs
 * @date 2026/02/11
 */
public interface ICacheData<V> {
    /**
     * 获取缓存的值
     */
    V getValue();

    /**
     * 更新缓存的访问时间
     * @param currentTimeMs 当前时间，单位毫秒
     */
    void updateAccessTime(long currentTimeMs);

    /**
     * 是否过期
     * @param currentTimeMs 当前时间，单位毫秒
     * @return 是否过期
     */
    boolean isExpired(long currentTimeMs);

    /**
     * 过期后的处理方法，默认不做任何处理
     * 注意只有自动过期时才会调用这个方法，手动清理不会调用这个方法。
     * 该方法将在与自动清理在同一个线程中调用，注意：
     * 1. 请使用轻量化的操作，避免堵塞清理操作；例如日志记录、简单的资源释放等。
     * 2. 如果需要较重的操作，请在函数里另外在 taskService 中请求一个新的线程来执行。
     * 3. 请不要在该方法里尝试访问、修改缓存，否则会抛出 ConcurrentModificationException；如果要删除请让其返回 true 清理
     * @param currentTimeMs 用于传递当前的时间，单位毫秒
     * @param value 当前缓存的值，过期后可能需要进行一些清理或其他操作
     * @return true 表示会正常删除该缓存；false 表示暂时不删除该缓存。如果没有更新 access time 则会在下次清理时再次调用 onExpire 方法。
     */
    boolean onExpire(long currentTimeMs, V value);
}
