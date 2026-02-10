package org.zexnocs.teanekocore.reload.interfaces;

import org.zexnocs.teanekocore.reload.api.IReloadable;

/**
 * 将所有的 IReloadable 实现类进行管理，并提供一个统一的 reload 方法来调用它们的 reload 方法。
 *
 * @author zExNocs
 * @date 2026/02/11
 */
public interface IReloadService {
    /**
     * 添加一个 IReloadable 实现类到管理列表中。
     * 并调用一次该实现类的 init 方法，以确保它在添加时已经被正确加载。
     * 该方法的调用是同步的，不是异步的。
     * @param reloadable 需要添加的 IReloadable 实现类。
     */
    void addReloadable(IReloadable reloadable);

    /**
     * 调用所有 IReloadable 实现类的 reload 方法。
     * 该方法是同步的，不是异步的。
     */
    void reloadAll();
}
