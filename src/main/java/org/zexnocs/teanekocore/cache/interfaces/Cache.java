package org.zexnocs.teanekocore.cache.interfaces;

/**
 * 缓存类接口：如果长时间没有被访问，则会被自动清理
 *
 * @author zExNocs
 * @date 2026/02/06
 */
public interface Cache {
    /**
     * 用于在特定情况下手动清理缓存
     */
    void manualClean();

    /**
     * 自动清理缓存
     *
     * @param currentTimeMs 当前时间，单位毫秒
     */
    void autoClean(long currentTimeMs);
}
