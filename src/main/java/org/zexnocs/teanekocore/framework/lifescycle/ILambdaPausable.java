package org.zexnocs.teanekocore.framework.lifescycle;

// 2026/11/07：接口统一使用 I 前缀，调用合同与持久数据保持不变。

/**
 * 使用 Lambda 表达式实现的可暂停对象。
 *
 * @author zExNocs
 * @date 2026/02/10
 */
@FunctionalInterface
public interface ILambdaPausable extends IPausable {
    @Override
    default void pause() {
        throw new UnsupportedOperationException("ILambdaPausable does not support pause operation.");
    }

    @Override
    default void resume() {
        throw new UnsupportedOperationException("ILambdaPausable does not support resume operation.");
    }
}
