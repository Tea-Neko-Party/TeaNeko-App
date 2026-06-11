package org.zexnocs.teanekocore.cache.interfaces;

import java.time.Instant;

/**
 * 缓存类接口：如果长时间没有被访问，则会被自动清理
 *
 * @author zExNocs
 * @date 2026/02/06
 */
public interface ICacheContainer {
    /**
     * 用于在特定情况下手动清理缓存
     */
    void manualClean();

    /**
     * 自动清理缓存
     *
     * @param currentTime 当前时间点
     */
    void autoClean(Instant currentTime);
}
