package org.zexnocs.teanekocore.database.easydata.core;

import lombok.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.cache.ConcurrentMapCacheContainer;
import org.zexnocs.teanekocore.cache.interfaces.ICacheService;
import org.zexnocs.teanekocore.database.base.DatabaseService;
import org.zexnocs.teanekocore.database.base.interfaces.IDatabaseService;
import org.zexnocs.teanekocore.database.easydata.BaseEasyDataObject;
import org.zexnocs.teanekocore.database.easydata.BaseEasyDataRepository;
import org.zexnocs.teanekocore.database.easydata.api.IEasyData;
import org.zexnocs.teanekocore.database.easydata.core.interfaces.IEasyDataDto;
import org.zexnocs.teanekocore.database.easydata.core.interfaces.IEasyDataService;
import org.zexnocs.teanekocore.framework.pair.HashPair;
import org.zexnocs.teanekocore.framework.pair.Pair;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * EasyData 服务类。
 * 用于通过 table 和 命名空间储存、获取相应的 EasyData 对象。
 *
 * @author zExNocs
 * @date 2026/02/15
 */
@Service("easyDataService")
public class EasyDataService implements IEasyDataService {
    /// EasyData 数据库扫描器
    private final EasyDataRepositoryScanner easyDataRepositoryScanner;

    /// 数据库服务
    private final IDatabaseService databaseService;

    /// EasyData 缓存; (entity name, namespace) → EasyDataCache
    private final ConcurrentMapCacheContainer<Pair<String, String>, EasyDataCache> easyDataCache;

    /// EasyData 的 task stage chain 执行缓存
    private final Map<Class<? extends BaseEasyDataObject>, String> taskStageNamespaceCache = new ConcurrentHashMap<>();

    /// object mapper
    private final ObjectMapper objectMapper;

    /**
     * 构造函数。
     */
    @Autowired
    public EasyDataService(ICacheService cacheService,
                           IDatabaseService databaseService,
                           EasyDataRepositoryScanner easyDataRepositoryScanner,
                           ObjectMapper objectMapper) {
        this.databaseService = databaseService;
        this.easyDataRepositoryScanner = easyDataRepositoryScanner;
        this.objectMapper = objectMapper;
        this.easyDataCache = ConcurrentMapCacheContainer.of(cacheService);
    }

    /**
     * 根据 EasyData 获取数据对象。
     * 优先从缓存中获取。
     * @return EasyData 数据对象
     */
    @Override
    @NonNull
    public IEasyDataDto getEasyData(IEasyData easyData, String target) {
        // 构造 key
        var key = HashPair.of(easyData.getEntityClass().getName(), easyData.getNamespace());
        // 尝试获取任务阶段链命名空间
        String taskStageNamespace = taskStageNamespaceCache.computeIfAbsent(easyData.getEntityClass(),
                k -> {
                    // 如果没有，则尝试从注解里获取
                    var clazz = easyData.getClass();
                    if (clazz.isAnnotationPresent(EasyData.class)) {
                        var __stageNamespace = clazz.getAnnotation(EasyData.class).value();
                        // 如果不为空，则返回
                        if (__stageNamespace != null && !__stageNamespace.isEmpty()) {
                            return __stageNamespace;
                        }
                    }
                    return DatabaseService.TASK_STAGE_NAMESPACE;
                });
        var cache = easyDataCache.computeIfAbsent(key, k ->
                new EasyDataCache(easyDataRepositoryScanner.getRepository(easyData.getEntityClass()),
                        taskStageNamespace,
                        easyData.getNamespace()));
        return cache.get(target);
    }

    /**
     * EasyData 数据对象的缓存类。
     */
    public class EasyDataCache {
        /// target → 数据对象
        private final Map<String, IEasyDataDto> data = new ConcurrentHashMap<>();

        /// 数据库引用
        private final BaseEasyDataRepository<?> repository;

        /// 任务阶段链命名空间
        private final String taskStageNamespace;

        /// 命名空间
        private final String namespace;

        /**
         * 构造函数。
         */
        private EasyDataCache(BaseEasyDataRepository<?> repository,
                              String taskStageNamespace,
                              String namespace) {
            this.repository = repository;
            this.taskStageNamespace = taskStageNamespace;
            this.namespace = namespace;
        }

        /**
         * 根据 target 获取数据对象。
         */
        @NonNull
        public IEasyDataDto get(String target) {
            return data.computeIfAbsent(target,
                    key -> new EasyDataDto(
                            databaseService,
                            repository,
                            taskStageNamespace,
                            namespace,
                            target,
                            objectMapper));
        }
    }
}
