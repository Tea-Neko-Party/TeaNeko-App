package org.zexnocs.teanekocore.framework.pair;

import lombok.AllArgsConstructor;

import java.util.Objects;

/**
 * first 和 second 的位置没有区别的 Pair。
 * 例如 ("a", "b") 和 ("b", "a") 的 HashCode 和 equals 都相同。
 *
 * @param <T> first 和 second 的类型。
 * @author zExNocs
 * @date 2026/02/11
 */
@AllArgsConstructor
public class UnorderedPair<T> implements Pair<T, T> {
    private final T first;
    private final T second;

    @Override
    public T first() {
        return first;
    }

    @Override
    public T second() {
        return second;
    }

    /**
     * 重写 equals 方法，使得 first 和 second 的位置没有区别。
     * @param o   the reference object with which to compare.
     * @return true if this object is the same as the obj argument; false otherwise.
     */
    @Override
    public boolean equals(Object o) {
        // 地址相同则返回 true
        if (this == o) return true;
        // 如果 o 不是 UnorderedPair 类型，则返回 false
        if (o == null || getClass() != o.getClass()) return false;
        UnorderedPair<?> that = (UnorderedPair<?>) o;
        // 比较 first 和 second 的值，位置没有区别
        return (first.equals(that.first) && second.equals(that.second)) ||
               (first.equals(that.second) && second.equals(that.first));
    }

    /**
     * 重写 hashCode 方法，使得 first 和 second 的位置没有区别。
     * @return a hash code value for this object.
     */
    @Override
    public int hashCode() {
        // 计算 first 和 second 的 hashCode，并将它们相加，使得位置 没有区别
        return Objects.hashCode(first) + Objects.hashCode(second);
    }
}
