package org.zexnocs.teanekocore.cache;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.actuator.task.EmptyTaskResult;
import org.zexnocs.teanekocore.actuator.timer.TimerDisplay;
import org.zexnocs.teanekocore.actuator.timer.interfaces.ITimerService;
import org.zexnocs.teanekocore.cache.interfaces.ICacheContainer;
import org.zexnocs.teanekocore.cache.interfaces.ICacheService;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用于自动管理缓存的服务类。
 * 主要用于清理长时间未被访问的缓存资源。
 * <br>2026/11/04：声明缓存维护分类，关键清理任务只读展示。
 *
 * @author zExNocs
 * @date 2026/02/10
 * @since 4.0.2
 * @version 4.7.1-l
 */
@Service
// 2026/11/04：网页只观测维护任务，不开放暂停全局缓存回收。
@TimerDisplay(category = "core.maintenance", categoryName = "Core 维护", name = "通用缓存清理",
        description = "按原缓存容器的过期规则回收，不改变业务缓存生命周期。", controllable = false)
public class CacheService implements ICacheService {
    /// 清理缓存任务的命名空间
    public static final String CLEAN_CACHE_TASK_NAMESPACE = "one-bot-cache-service-clean-cache-task";

    /// 定时器服务
    private final ITimerService iTimerService;

    /// 清理缓存的时间间隔
    private final Duration cleanCacheInterval;

    /// 缓存列表
    private final Set<ICacheContainer> cacheMap = ConcurrentHashMap.newKeySet();

    @Autowired
    public CacheService(ITimerService iTimerService,
                        @Value("${tea-neko.cache.general-clean-rate-ms:1000}") long cleanCacheIntervalMs) {
        this.cleanCacheInterval = Duration.ofMillis(cleanCacheIntervalMs);
        this.iTimerService = iTimerService;
    }

    @PostConstruct
    public void init() {
        iTimerService.registerBySmartRate(
                "CacheService-清理缓存任务",
                CLEAN_CACHE_TASK_NAMESPACE,
                this::cleanCacheTask,
                cleanCacheInterval,
                EmptyTaskResult.getResultType());
    }

    /**
     * 清理缓存任务。
     * 具体清理逻辑交给各个缓存对象自行处理。
     * @return emptyTaskResult
     */
    public EmptyTaskResult cleanCacheTask() {
        var currentTime = Instant.now();
        for (ICacheContainer cache : cacheMap) {
            cache.autoClean(currentTime);
        }
        return EmptyTaskResult.INSTANCE;
    }

    /**
     * 添加缓存对象。
     * @param cache 缓存对象
     */
    @Override
    public void addCache(ICacheContainer cache) {
        cacheMap.add(cache);
    }

    /**
     * 手动清理所有缓存。
     * 取决于各个缓存对象是否参与手动清理。
     */
    @Override
    public void manualCleanAll() {
        for (ICacheContainer cache : cacheMap) {
            cache.manualClean();
        }
    }
}
