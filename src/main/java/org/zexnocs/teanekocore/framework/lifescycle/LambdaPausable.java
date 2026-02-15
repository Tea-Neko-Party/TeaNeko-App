package org.zexnocs.teanekocore.framework.lifescycle;

/**
 * 使用 Lambda 表达式实现的可暂停对象。
 *
 * @author zExNocs
 * @date 2026/02/10
 */
@FunctionalInterface
public interface LambdaPausable extends IPausable {
    @Override
    default void pause() {
        throw new UnsupportedOperationException("LambdaPausable does not support pause operation.");
    }

    @Override
    default void resume() {
        throw new UnsupportedOperationException("LambdaPausable does not support resume operation.");
    }
}
