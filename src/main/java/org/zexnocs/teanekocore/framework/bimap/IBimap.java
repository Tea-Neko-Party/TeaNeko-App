package org.zexnocs.teanekocore.framework.bimap;

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekocore.framework.pair.Pair;

import java.util.Map;
import java.util.Set;

/**
 * 双向映射接口。
 *
 * @param <K> 键类型。
 * @param <V> 值类型。
 * @author zExNocs
 * @date 2026/02/11
 */
public interface IBimap<K, V> {
    /**
     * 根据 key 获取 value。
     * @param key 键。
     * @return 值。
     */
    @Nullable
    V getValue(K key);

    /**
     * 根据 value 获取 key。
     * @param value 值。
     * @return 键。
     */
    @Nullable
    K getKey(V value);

    /**
     * 添加一个键值对。
     * @param key 键。
     * @param value 值。
     * @return 被替换的键值对，如果没有被替换则返回 null。
     */
    Pair<K, V> put(K key, V value);

    /**
     * 是否包含指定的 key。
     * @param key 键。
     * @return 是否包含。
     */
    boolean containsKey(K key);

    /**
     * 是否包含指定的 value。
     * @param value 值。
     * @return 是否包含。
     */
    boolean containsValue(V value);

    /**
     * 根据 key 移除一个键值对，并返回被移除的值。
     * @param key 键。
     * @return 被移除的值。
     */
    V removeByKey(K key);

    /**
     * 根据 value 移除一个键值对，并返回被移除的键。
     * @param value 值。
     * @return 被移除的键。
     */
    K removeByValue(V value);

    /**
     * 清空双向映射。
     */
    void clear();

    /**
     * 获取双向映射的大小。
     * @return 大小。
     */
    int size();

    /**
     * 判断双向映射是否为空。
     * @return 是否为空。
     */
    boolean isEmpty();

    /**
     * 获取 EntrySet。
     * 只提供读取，不提供修改。
     * @return EntrySet。
     */
    Set<Map.Entry<K, V>> entrySet();
}
