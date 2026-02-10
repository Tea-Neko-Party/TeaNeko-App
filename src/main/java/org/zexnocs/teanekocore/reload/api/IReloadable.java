package org.zexnocs.teanekocore.reload.api;

/**
 * 支持热重载的接口。
 *
 * @author zExNocs
 * @date 2026/02/11
 */
public interface IReloadable {
    /**
     * 热重载方法。
     */
    void reload();

    /**
     * 初始化方法。
     * 用于防止第一次重复加载。
     */
    void init();
}
