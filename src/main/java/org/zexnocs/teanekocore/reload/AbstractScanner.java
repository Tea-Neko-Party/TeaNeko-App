package org.zexnocs.teanekocore.reload;

import org.zexnocs.teanekocore.reload.api.IScanner;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 用于快速实现 canner 的抽象类。
 *
 * @author zExNocs
 * @date 2026/02/26
 * @since 4.0.9
 */
public abstract class AbstractScanner implements IScanner {

    /// 是否已经初始化过了。用于防止第一次重复加载。
    private final AtomicBoolean isInit = new AtomicBoolean(false);

    /**
     * 扫描方法。
     *
     */
    protected abstract void _scan();

    /**
     * 热重载方法。
     */
    @Override
    public void reload() {
        _scan();
    }

    /**
     * 初始化方法。
     * 用于防止第一次重复加载。
     */
    @Override
    public void init() {
        if(isInit.compareAndSet(false, true)) {
            _scan();
        }
    }
}
