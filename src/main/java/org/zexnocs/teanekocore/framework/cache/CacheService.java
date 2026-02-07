package org.zexnocs.teanekocore.framework.cache;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.framework.cache.interfaces.Cache;
import org.zexnocs.teanekocore.framework.cache.interfaces.ICacheService;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用于自动管理缓存的服务类。
 * 主要用于清理长时间未被访问的缓存资源。
 */
@Service
public class CacheService implements ICacheService {
    public static final String CLEAN_CACHE_TASK_NAMESPACE = "one-bot-cache-service-clean-cache-task";
    private final ITimerService iTimerService;
    private final long cleanCacheIntervalMs;

    /// 缓存列表
    private final Set<Cache> cacheMap = ConcurrentHashMap.newKeySet();

    @Autowired
    public CacheService(ITimerService iTimerService,
                        @Value("${oneBot.cache.general-clean-interval-ms}") long cleanCacheIntervalMs) {
        this.cleanCacheIntervalMs = cleanCacheIntervalMs;
        this.iTimerService = iTimerService;
    }

    @PostConstruct
    public void init() {
        // 注册定期清理缓存任务
        iTimerService.registerNonOnceAsync(
                "CacheService-清理缓存任务",
                CLEAN_CACHE_TASK_NAMESPACE,
                this::cleanCacheTask,
                cleanCacheIntervalMs
        );
    }

    /**
     * 清理缓存任务。
     * 具体清理逻辑交给各个缓存对象自行处理。
     * @return null
     */
    public Void cleanCacheTask() {
        long currentTimeMs = System.currentTimeMillis();
        for (Cache cache : cacheMap) {
            cache.autoClean(currentTimeMs);
        }
        return null;
    }

    /**
     * 添加缓存对象。
     * @param cache 缓存对象
     */
    @Override
    public void addCache(Cache cache) {
        cacheMap.add(cache);
    }

    /**
     * 手动清理所有缓存。
     * 取决于各个缓存对象是否参与手动清理。
     */
    @Override
    public void manualCleanAll() {
        for (Cache cache : cacheMap) {
            cache.manualClean();
        }
    }
}
