package org.zexnocs.teanekocore.framework.pair;

/**
 * 一对数据的容器。
 * 用于存储一对相关的数据。
 * @param <F> 第一个数据的类型
 * @param <S> 第二个数据的类型
 * @author zExNocs
 * @date 2026/02/11
 */
public interface Pair<F, S> {
    /**
     * 获取第一个数据。
     * @return 第一个数据
     */
    F first();

    /**
     * 获取第二个数据。
     * @return 第二个数据
     */
    S second();
}
