package org.zexnocs.teanekocore.event.core;

import lombok.Getter;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.event.interfaces.IEvent;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekocore.reload.AbstractScanner;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IBeanScanner;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IClassScanner;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 扫描事件处理器的类。
 * <br>2026/11/02：相同优先级按 Bean 名及方法签名确定稳定监听顺序。
 * <br>2026/11/04：根据实际事件类型合并父类监听器，未被类扫描覆盖的客户端事件也能进入通用消息链。
 *
 * @author zExNocs
 * @date 2026/02/17
 * @since 4.0.0
 * @version 4.7.5-l
 * @see EventHandler
 * @see EventListener
 */
@Service("eventHandlerScanner")
public class EventHandlerScanner extends AbstractScanner {
    /// 事件类 → 事件处理器列表
    @SuppressWarnings("rawtypes")
    private final Map<Class<? extends IEvent>,
            List<EventHandlerPatch<? extends IEvent>>> eventHandlerMap = new ConcurrentHashMap<>();
    private final IBeanScanner iBeanScanner;
    private final ILogger iLogger;
    private final IClassScanner iClassScanner;

    /**
     * 2026/11/02：补充扫描依赖合同，初始化时生成监听目录。
     * @param iBeanScanner Bean 扫描
     * @param iLogger 项目日志
     * @param iClassScanner 事件类型扫描
     */
    public EventHandlerScanner(IBeanScanner iBeanScanner, ILogger iLogger, IClassScanner iClassScanner) {
        this.iBeanScanner = iBeanScanner;
        this.iLogger = iLogger;
        this.iClassScanner = iClassScanner;
    }

    /**
     * 扫描事件处理器。
     */
    @Override
    @SuppressWarnings("rawtypes")
    protected synchronized void _scan() {
        // 先用 set 存储事件处理器，避免重复添加
        var eventHandlerSetByType = new HashMap<Class<? extends IEvent>, Set<EventHandlerPatch<? extends IEvent>>>();

        // 扫描所有的 IListener 接口
        var beanPairs = iBeanScanner.getBeansWithAnnotation(EventListener.class);
        // 2026/11/02：Bean 名进入稳定排序，避免 HashSet 顺序影响可修改事件。
        for(var entry: beanPairs.entrySet()) {
            var pair = entry.getValue();
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
                _registerEventHandler(entry.getKey(), bean, method, methodAnnotation, eventHandlerSetByType);
            }
        }
        // 处理继承问题，子类获得所有父类的事件处理器
        // 获取所有继承了 IEvent 的事件类
        var eventTypes = iClassScanner.getClassesWithInterface(IEvent.class);
        for (var type : eventTypes) {
            // 获取所有父类的事件处理器
            var superclass = type.getSuperclass();
            while (superclass != null &&
                    IEvent.class.isAssignableFrom(superclass) &&
                    !superclass.equals(IEvent.class)) {
                var parent = superclass.asSubclass(IEvent.class);
                // 获取父类的事件处理器，并添加到子类的事件处理器列表中
                var parentHandlers = eventHandlerSetByType.getOrDefault(parent, Collections.emptySet());
                var set = eventHandlerSetByType.computeIfAbsent(
                        type.asSubclass(IEvent.class),
                        k -> new HashSet<>());
                set.addAll(parentHandlers);
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
     * 清理原始数据的方法。
     *
     */
    @Override
    protected void _clear() {
        eventHandlerMap.clear();
    }

    /**
     * 2026/11/02：注册事件处理器并保存稳定 Bean 身份。
     *
     * @param listener        监听器
     * @param beanName        稳定 Bean 名
     * @param method          监听器的方法
     * @param annotation      事件处理器的注解
     * @param eventHandlerMap 事件处理器映射
     */
    @SuppressWarnings("rawtypes")
    private void _registerEventHandler(String beanName, Object listener,
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
                // 2026/11/02：绑定注册名，代理或同类型多 Bean 不再造成相同优先级歧义。
                beanName,
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
     * 事件 Consumer 的固定调用描述。
     * <br>2026/11/02：保存 Bean 名作为同优先级稳定排序依据。
     * @param <T> 事件类型
     * @author zExNocs
     * @date 2026/02/17
     * @since 4.0.8
     * @version 4.6.0-l
     */
    @SuppressWarnings("rawtypes")
    public static class EventHandlerPatch<T extends IEvent> implements Comparable<EventHandlerPatch<?>> {
        private final String beanName;
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

        /**
         * 2026/11/02：固定注册身份与调用描述，只由扫描器创建。
         * @param beanName 注册名
         * @param target 监听器
         * @param method 方法
         * @param eventType 事件类型
         * @param annotation 处理配置
         */
        private EventHandlerPatch(String beanName, Object target, Method method, Class<T> eventType, EventHandler annotation) {
            this.beanName = beanName;
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
            // 2026/11/02：优先级相同也保持可重现的业务修改顺序。
            int priority = Integer.compare(other.annotation.priority(), this.annotation.priority());
            if (priority != 0) { return priority; }
            int bean = beanName.compareTo(other.beanName);
            return bean != 0 ? bean : method.toGenericString().compareTo(other.method.toGenericString());
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
        // 2026/11/04：运行时沿真实继承关系取监听器，去重并保持原优先级和稳定顺序。
        var handlers = new TreeSet<>(eventHandlerMap.getOrDefault(eventClass, List.of()));
        Class<?> parent = eventClass.getSuperclass();
        while (parent != null && IEvent.class.isAssignableFrom(parent)) {
            handlers.addAll(eventHandlerMap.getOrDefault(parent.asSubclass(IEvent.class), List.of()));
            parent = parent.getSuperclass();
        }
        return List.copyOf(handlers);
    }
}
