package org.zexnocs.teanekocore.event;

import org.springframework.core.ResolvableType;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.cache.ConcurrentMapCacheContainer;
import org.zexnocs.teanekocore.cache.interfaces.ICacheService;
import org.zexnocs.teanekocore.event.interfaces.IEvent;
import org.zexnocs.teanekocore.event.interfaces.IEventGeneralizedTypeService;

/**
 * 获取 {@link org.zexnocs.teanekocore.event.interfaces.IEvent} 的泛化类型的服务类，并提供缓存。
 *
 * @author zExNocs
 * @date 2026/03/04
 * @since 4.0.12
 */
@Service
public class EventGeneralizedTypeService implements IEventGeneralizedTypeService {

    /// 缓存事件类与其泛化实际类型的映射关系
    private final ConcurrentMapCacheContainer<Class<? extends IEvent<?>>, Class<?>> cache;

    public EventGeneralizedTypeService(ICacheService cacheService) {
        this.cache = ConcurrentMapCacheContainer.of(cacheService);
    }


    /**
     * 获取泛化实际类型。
     *
     * @param eventClass 事件类
     * @return 泛化实际类型
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> Class<T> getGeneralizedActualType(Class<? extends IEvent<T>> eventClass) {
        return (Class<T>) cache.computeIfAbsent(eventClass, clazz ->
                ResolvableType
                .forClass(clazz)
                .as(IEvent.class)
                .getGeneric(0)
                .resolve());
    }
}
