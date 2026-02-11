package org.zexnocs.teanekocore.framework.bimap;

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekocore.framework.pair.IndependentPair;
import org.zexnocs.teanekocore.framework.pair.Pair;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 带锁的线性安全的双向映射。
 *
 * @author zExNocs
 * @date 2026/02/11
 */
public class LockBiMap<K, V> implements IBimap<K, V> {
    private final Map<K, V> kv;
    private final Map<V, K> vk;

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * 默认构造函数，初始化双向映射。
     */
    public LockBiMap() {
        this.kv = new HashMap<>();
        this.vk = new HashMap<>();
    }

    /**
     * 提供初始容量的构造函数，初始化双向映射。
     */
    public LockBiMap(int initialCapacity) {
        this.kv = new HashMap<>(initialCapacity);
        this.vk = new HashMap<>(initialCapacity);
    }

    /**
     * 根据 key 获取 value。
     *
     * @param key 键。
     * @return 值。
     */
    @Override
    public @Nullable V getValue(K key) {
        lock.readLock().lock();
        try {
            return kv.get(key);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 根据 value 获取 key。
     *
     * @param value 值。
     * @return 键。
     */
    @Override
    public @Nullable K getKey(V value) {
        lock.readLock().lock();
        try {
            return vk.get(value);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 添加一个键值对。
     *
     * @param key   键。
     * @param value 值。
     * @return 被替换的键值对，如果没有被替换则返回 null。
     */
    @Override
    public Pair<K, V> put(K key, V value) {
        lock.writeLock().lock();
        try {
            V oldValue = kv.get(key);
            K oldKey = vk.get(value);

            if (oldValue != null) {
                vk.remove(oldValue);
            }
            if (oldKey != null) {
                kv.remove(oldKey);
            }

            kv.put(key, value);
            vk.put(value, key);

            return IndependentPair.of(oldKey, oldValue);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 是否包含指定的 key。
     *
     * @param key 键。
     * @return 是否包含。
     */
    @Override
    public boolean containsKey(K key) {
        lock.readLock().lock();
        try {
            return kv.containsKey(key);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 是否包含指定的 value。
     *
     * @param value 值。
     * @return 是否包含。
     */
    @Override
    public boolean containsValue(V value) {
        lock.readLock().lock();
        try {
            return vk.containsKey(value);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 根据 key 移除一个键值对，并返回被移除的值。
     *
     * @param key 键。
     * @return 被移除的值。
     */
    @Override
    public V removeByKey(K key) {
        lock.writeLock().lock();
        try {
            V value = kv.remove(key);
            if (value != null) {
                vk.remove(value);
            }
            return value;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 根据 value 移除一个键值对，并返回被移除的键。
     *
     * @param value 值。
     * @return 被移除的键。
     */
    @Override
    public K removeByValue(V value) {
        lock.writeLock().lock();
        try {
            K key = vk.remove(value);
            if (key != null) {
                kv.remove(key);
            }
            return key;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 清空双向映射。
     */
    @Override
    public void clear() {
        lock.writeLock().lock();
        try {
            kv.clear();
            vk.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 获取双向映射的大小。
     *
     * @return 大小。
     */
    @Override
    public int size() {
        lock.readLock().lock();
        try {
            return kv.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 判断双向映射是否为空。
     *
     * @return 是否为空。
     */
    @Override
    public boolean isEmpty() {
        lock.readLock().lock();
        try {
            return kv.isEmpty();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 获取 EntrySet。
     * 只提供读取，不提供修改。
     *
     * @return EntrySet。
     */
    @Override
    public Set<Map.Entry<K, V>> entrySet() {
        lock.readLock().lock();
        try {
            // 返回不可变快照，防止破坏双射
            return Set.copyOf(kv.entrySet());
        } finally {
            lock.readLock().unlock();
        }
    }
}
