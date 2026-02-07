package org.zexnocs.teanekocore.cache.interfaces;

/**
 * 用于自动管理缓存的服务类接口。
 * 主要用于清理长时间未被访问的缓存资源。
 *
 * @author zExNocs
 * @date 2026/02/06
 */
public interface ICacheService {
    /**
     * 新增缓存到缓存服务中进行管理
     *
     * @param cache 缓存对象
     */
    void addCache(Cache cache);

    /**
     * 尝试手动清理所有缓存
     * 取决于各个缓存对象是否参与强制清理
     */
    void manualCleanAll();
}
