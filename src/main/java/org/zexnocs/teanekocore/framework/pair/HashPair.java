package org.zexnocs.teanekocore.framework.pair;

/**
 * 哈希对数据的容器。
 * @param <F> 第一个元素的类型
 * @param <S> 第二个元素的类型
 * 相同的 first 和 second 会有相同的 hashCode 和 equals 实现。
 *
 * @author zExNocs
 * @date 2026/02/11
 */
public record HashPair<F, S>(F first, S second) implements Pair<F, S> {
    public static <F, S> HashPair<F, S> of(F first, S second) {
        return new HashPair<>(first, second);
    }
}
