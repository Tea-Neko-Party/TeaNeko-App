package org.zexnocs.teanekocore.event.core;

import lombok.Getter;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.event.interfaces.IEvent;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekocore.reload.AbstractScanner;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IBeanScanner;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 扫描事件处理器的类。
 *
 * @see EventHandler
 * @see EventListener
 * @author zExNocs
 * @date 2026/02/17
 * @since 4.0.0
 */
@Service("eventHandlerScanner")
public class EventHandlerScanner extends AbstractScanner {
    /// 事件类 → 事件处理器列表
    @SuppressWarnings("rawtypes")
    private final Map<Class<? extends IEvent>,
            List<EventHandlerPatch<? extends IEvent>>> eventHandlerMap = new ConcurrentHashMap<>();
    private final IBeanScanner iBeanScanner;
    private final ILogger iLogger;

    public EventHandlerScanner(IBeanScanner iBeanScanner, ILogger iLogger) {
        this.iBeanScanner = iBeanScanner;
        this.iLogger = iLogger;
    }

    /**
     * 扫描事件处理器。
     */
    @Override
    @SuppressWarnings("rawtypes")
    protected synchronized void _scan() {
        eventHandlerMap.clear();
        // 先用 set 存储事件处理器，避免重复添加
        var eventHandlerSetByType = new HashMap<Class<? extends IEvent>, Set<EventHandlerPatch<? extends IEvent>>>();

        // 扫描所有的 IListener 接口
        var beanPairs = iBeanScanner.getBeansWithAnnotation(EventListener.class);
        for(var pair: beanPairs.values()) {
            var bean = pair.second();
            // 获取接口内所有带有 EventHandler 注解的方法
            var methods = bean.getClass().getMethods();
            for(var method: methods) {
                // 判断方法是否带有 EventHandler 注解
                var methodAnnotation = method.getAnnotation(EventHandler.class);
                if (methodAnnotation == null) {
                    continue;
                }
                // 注册事件处理器
                _registerEventHandler(bean, method, methodAnnotation, eventHandlerSetByType);
            }
        }
        // 处理继承问题，子类获得所有父类的事件处理器
        for (var entry : eventHandlerSetByType.entrySet()) {
            var type = entry.getKey();
            // 获取所有父类的事件处理器
            var superclass = type.getSuperclass();
            while (superclass != null &&
                    IEvent.class.isAssignableFrom(superclass) &&
                    !superclass.equals(IEvent.class)) {
                var parent = superclass.asSubclass(IEvent.class);
                // 获取父类的事件处理器，并添加到子类的事件处理器列表中
                var parentHandlers = eventHandlerSetByType.getOrDefault(parent, Collections.emptySet());
                entry.getValue().addAll(parentHandlers);
                // 处理下一个父类
                superclass = superclass.getSuperclass();
            }
        }

        // 转化成 eventHandlerMap
        eventHandlerMap.clear();
        for (var entry : eventHandlerSetByType.entrySet()) {
            var eventType = entry.getKey();
            var handlerSet = entry.getValue();
            var handlerList = new CopyOnWriteArrayList<>(handlerSet);
            eventHandlerMap.put(eventType, handlerList);
        }

        // 排序事件处理器
        for (var list : eventHandlerMap.values()) {
            list.sort(EventHandlerPatch::compareTo);
        }
    }

    /**
     * 注册事件处理器。
     *
     * @param listener        监听器
     * @param method          监听器的方法
     * @param annotation      事件处理器的注解
     * @param eventHandlerMap 事件处理器映射
     */
    @SuppressWarnings("rawtypes")
    private void _registerEventHandler(Object listener,
                                             Method method,
                                             EventHandler annotation,
                                             Map<Class<? extends IEvent>, Set<EventHandlerPatch<? extends IEvent>>> eventHandlerMap) {
        String TAG = "事件监听器扫描器";
        var clazz = listener.getClass();
        // 判断方法的访问修饰符
        var modifiers = method.getModifiers();
        if (!java.lang.reflect.Modifier.isPublic(modifiers)) {
            iLogger.errorWithReport(TAG,
                    "类 " + clazz.getName() + " " +
                            "的事件处理器 " + method.getName() + " 的访问修饰符不正确，应该为 public");
            return;
        }
        // 判断方法的返回值类型
        var returnType = method.getReturnType();
        if (returnType != void.class) {
            iLogger.errorWithReport(TAG,
                    "类 " + clazz.getName() + " " +
                            "的事件处理器 " + method.getName() + " 的返回值类型不正确，应该为 void");
            return;
        }
        // 判断方法的参数的个数
        var parameterTypes = method.getParameterTypes();
        if (parameterTypes.length != 1) {
            iLogger.errorWithReport(TAG,
                    "类 " + clazz.getName() + " " +
                            "的事件处理器 " + method.getName() + " 的参数数量不正确，应该为 1");
            return;
        }
        // 判断参数的类型
        var parameterType = parameterTypes[0];
        if (!IEvent.class.isAssignableFrom(parameterType)) {
            iLogger.errorWithReport(TAG,
                    "类 " + clazz.getName() + " " +
                            "的事件处理器 " + method.getName() + " 的参数类型不正确，应该为 IEvent 的子类");
            return;
        }
        // 转化成 Class<? extends IEvent>
        var eventClass = parameterType.asSubclass(IEvent.class);
        // 构造EventHandlerPatch
        var eventHandlerPatch = new EventHandlerPatch<>(
                listener,
                method,
                eventClass,
                annotation
        );
        // 储存
        eventHandlerMap.computeIfAbsent(eventClass, k -> new HashSet<>())
                .add(eventHandlerPatch);
    }

    /**
     * 事件 Consumer 的 patch
     */
    @SuppressWarnings("rawtypes")
    public static class EventHandlerPatch<T extends IEvent> implements Comparable<EventHandlerPatch<?>> {
        /// 事件处理器的目标对象
        @Getter
        private final Object target;

        /// 事件处理器的方法
        @Getter
        private final Method method;

        /// 事件处理器的事件类型
        private final Class<T> eventType;

        /// 事件处理器的注解
        @Getter
        private final EventHandler annotation;

        /// 禁止私自实例化
        private EventHandlerPatch(Object target, Method method, Class<T> eventType, EventHandler annotation) {
            this.target = target;
            this.method = method;
            this.eventType = eventType;
            this.annotation = annotation;
        }

        /**
         * 调用事件处理器的方法
         * @param event 事件
         * @throws Exception 异常
         */
        public void invoke(IEvent<?> event) throws Exception {
            if (!eventType.isInstance(event)) {
                throw new IllegalArgumentException("事件" + event.getClass().getName() +
                        "不是事件处理器" + method.getDeclaringClass().getName() + "的事件类型。" +
                        "应该为" + eventType.getName());
            }
            method.invoke(target, eventType.cast(event));
        }

        /**
         * 按照注解的优先级进行降序排序
         * @param other 事件处理器
         * @return 排序结果
         */
        @Override
        public int compareTo(EventHandlerPatch<?> other) {
            return Integer.compare(other.annotation.priority(), this.annotation.priority());
        }
    }

    /**
     * 获取事件处理器列表
     * @param eventClass 事件类
     * @return 事件处理器列表
     */
    @NonNull
    @SuppressWarnings("rawtypes")
    public List<EventHandlerPatch<? extends IEvent>> getEventHandlerList(Class<? extends IEvent> eventClass) {
        if (eventClass == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(eventHandlerMap.getOrDefault(eventClass, List.of()));
    }
}
