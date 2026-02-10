package org.zexnocs.teanekocore.framework.lifescycle;

import org.zexnocs.teanekocore.framework.lifescycle.interfaces.IPausable;

/**
 * 使用 Lambda 表达式实现的可暂停对象。
 *
 * @author zExNocs
 * @date 2026/02/10
 */
public abstract class LambdaPausable implements IPausable {
    @Override
    public void pause() {
        throw new UnsupportedOperationException("LambdaPausable does not support pause operation.");
    }

    @Override
    public void resume() {
        throw new UnsupportedOperationException("LambdaPausable does not support resume operation.");
    }
}
