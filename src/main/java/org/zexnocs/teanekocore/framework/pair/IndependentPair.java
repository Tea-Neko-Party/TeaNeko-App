package org.zexnocs.teanekocore.framework.pair;

// 2026/11/07：接口统一使用 I 前缀，调用合同与持久数据保持不变。

import lombok.AllArgsConstructor;

/**
 * 非哈希对数据的容器。
 *
 * @author zExNocs
 * @date 2026/02/11
 */
@AllArgsConstructor
public class IndependentPair<F, S> implements IPair<F, S> {
    private final F first;
    private final S second;

    @Override
    public F first() {
        return first;
    }

    @Override
    public S second() {
        return second;
    }

    public static <F, S> IndependentPair<F, S> of(F first, S second) {
        return new IndependentPair<>(first, second);
    }
}
