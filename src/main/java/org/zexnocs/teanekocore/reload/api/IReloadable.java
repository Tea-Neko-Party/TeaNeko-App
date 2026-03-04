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

    /**
     * 获取优先级
     *
     * @return 优先级，数值越大优先级越高
     */
    default int getPriority() {
        return 0;
    }
}
