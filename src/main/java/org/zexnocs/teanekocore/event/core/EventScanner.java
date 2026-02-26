package org.zexnocs.teanekocore.event.core;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.event.interfaces.IEvent;
import org.zexnocs.teanekocore.reload.AbstractScanner;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IClassScanner;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 扫描带有 Event 类型注解的 IEvent，且 key 不为空的类
 *
 * @see Event
 * @see IEvent
 * @author zExNocs
 * @date 2026/02/17
 * @since 4.0.0
 */
@Service("eventScanner")
public class EventScanner extends AbstractScanner {
    /// key → 事件数据类型
    @SuppressWarnings("rawtypes")
    private final Map<String, Class<? extends IEvent>> eventMap = new ConcurrentHashMap<>();
    private final IClassScanner iClassScanner;

    @Autowired
    public EventScanner(IClassScanner iClassScanner) {
        this.iClassScanner = iClassScanner;
    }

    /**
     * 扫描带有 Event 注解的 IEvent，且 key 不为空的类
     */
    @Override
    protected synchronized void _scan() {
        eventMap.clear();
        var map = iClassScanner.getClassesWithAnnotationAndInterface(Event.class, IEvent.class);
        for(var entry : map.entrySet()) {
            var clazz = entry.getKey();
            var eventAnnotation = entry.getValue();
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
