package org.zexnocs.teanekocore.event.core;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.event.interfaces.IEvent;
import org.zexnocs.teanekocore.reload.api.IScanner;
import org.zexnocs.teanekocore.utils.bean_scanner.IBeanScanner;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 扫描带有 Event 类型注解的 IEvent，且 key 不为空的类
 *
 * @author zExNocs
 * @date 2026/02/17
 */
@Service("eventScanner")
public class EventScanner implements IScanner {
    /// 防止第一次重复加载
    private final AtomicBoolean isInitialized = new AtomicBoolean(false);

    /// key → 事件数据类型
    @SuppressWarnings("rawtypes")
    private final Map<String, Class<? extends IEvent>> eventMap = new ConcurrentHashMap<>();

    /// 用于扫描带有 Event 注解的 IEvent 类
    private final IBeanScanner iBeanScanner;

    @Autowired
    public EventScanner(IBeanScanner iBeanScanner) {
        this.iBeanScanner = iBeanScanner;
    }

    /**
     * 热重载方法。
     */
    @Override
    public void reload() {
        __scan();
    }

    /**
     * 初始化方法。
     * 用于防止第一次重复加载。
     */
    @Override
    public void init() {
        if (isInitialized.compareAndSet(false, true)) {
            __scan();
        }
    }

    /**
     * 扫描带有 Event 注解的 IEvent，且 key 不为空的类
     */
    private synchronized void __scan() {
        eventMap.clear();
        var beanPairs = iBeanScanner.getBeansWithAnnotationAndInterface(Event.class, IEvent.class);
        for(var beanPair : beanPairs.values()) {
            var eventAnnotation = beanPair.first();
            var clazz = iBeanScanner.getBeanClass(beanPair.second(), IEvent.class);
            var key = eventAnnotation.value();
            if(key == null || key.isBlank()) {
                // 如果 key 为空，则跳过
                continue;
            }
            // 将事件类型和数据类型添加到 eventMap 中
            eventMap.put(key, clazz);
        }
    }

    /**
     * 根据 key 获取事件类型和数据类型
     * @param key 事件类型的 key
     * @return IEvent<?> 事件类型
     */
    @SuppressWarnings("rawtypes")
    public Class<? extends IEvent> getEventType(String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        return eventMap.get(key);
    }
}
