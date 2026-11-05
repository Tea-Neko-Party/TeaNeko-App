package org.zexnocs.teanekocore.reload.interfaces;

import org.zexnocs.teanekocore.reload.api.IReloadable;

/**
 * 将所有的 IReloadable 实现类进行管理，并提供一个统一的 reload 方法来调用它们的 reload 方法。
 *
 * 修改记录：<br>
 * 2026/11/05：明确初始化失败后仍保留显式重载入口。
 *
 * @author zExNocs
 * @date 2026/02/11
 * @since 4.0.2
 * @version 4.8.2-l
 */
public interface IReloadService {
    /**
     * 添加一个 IReloadable 实现类到管理列表中。
     * 2026/11/05：登记后同步调用一次 init；失败继续传播，登记保留供后续显式重载。
     * 该方法的调用是同步的，不是异步的。
     * @param reloadable 需要添加的 IReloadable 实现类。
     * @throws RuntimeException 当对象初始化失败时
     */
    void addReloadable(IReloadable reloadable);

    /**
     * 调用所有 IReloadable 实现类的 reload 方法。
     * 该方法是同步的，不是异步的。
     */
    void reloadAll();
}
