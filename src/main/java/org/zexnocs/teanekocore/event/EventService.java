package org.zexnocs.teanekocore.event;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.actuator.task.EmptyTaskResult;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskService;
import org.zexnocs.teanekocore.actuator.timer.interfaces.ITimerService;
import org.zexnocs.teanekocore.event.core.Event;
import org.zexnocs.teanekocore.event.core.EventHandlerScanner;
import org.zexnocs.teanekocore.event.interfaces.IEvent;
import org.zexnocs.teanekocore.event.interfaces.IEventService;
import org.zexnocs.teanekocore.logger.ILogger;

import java.time.Duration;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;


/**
 * 事件服务，负责处理事件的推送和分发。
 * 当前事件服务是一旦有任何监听器出现异常就会停止处理事件，并将异常抛出到命名空间或者 TaskFuture 中处理。
 *
 * @author zExNocs
 * @date 2026/02/17
 */
@Service
public class EventService implements IEventService {
    /// 处理的最大迭代次数
    private static final int MAX_ITERATION = 10;
    /// 事件打包服务的任务阶段命名空间
    public static final String EVENT_SERVICE_PATCH_NAMESPACE = "event-service-patch";
    /// 事件处理服务的基本命名空间
    public static final String EVENT_PROCESS_DEFAULT_NAMESPACE = "event-process-default";
    /// 监听器处理服务的基本命名空间
    public static final String EVENT_LISTENER_DEFAULT_NAMESPACE = "event-listener-default";
    private final ITaskService iTaskService;

    @Value("${tea-neko.event.patch-rate-ms}")
    private long patchInterval;

    /// 日志记录器
    private final ILogger logger;

    /// 定时器服务
    private final ITimerService timerService;

    /// 事件处理器扫描器
    private final EventHandlerScanner eventHandlerScanner;

    /// 待打包的事件队列
    private final Queue<IEvent<?>> eventQueue = new ConcurrentLinkedQueue<>();

    @Lazy
    @Autowired
    public EventService(ILogger logger,
                        ITimerService timerService,
                        EventHandlerScanner eventHandlerScanner, ITaskService iTaskService) {
        this.logger = logger;
        this.timerService = timerService;
        this.eventHandlerScanner = eventHandlerScanner;
        this.iTaskService = iTaskService;
    }

    /**
     * 初始化事件服务，注册定时任务来处理事件队列。
     *
     */
    @PostConstruct
    public void init() {
        timerService.registerByRate(
                "EventService-分发事件",
                EVENT_SERVICE_PATCH_NAMESPACE,
                this::_patchEventQueue,
                Duration.ofMillis(patchInterval),
                EmptyTaskResult.getResultType());
    }

    /**
     * 推送事件到事件队列。
     * @param event 事件
     */
    @Override
    public void pushEvent(IEvent<?> event) {
        if (event == null) {
            return;
        }
        eventQueue.add(event);
    }

    /**
     * 处理事件队列。
     */
    private EmptyTaskResult _patchEventQueue() {
        // 处理事件队列
        while(true) {
            var event = eventQueue.poll();
            if (event == null) {
                break;
            }
            String eventProcessNamespace = Optional.ofNullable(event.getClass().getAnnotation(Event.class))
                    .map(Event::namespace)
                    .filter(s -> !s.isEmpty())
                    .orElse(EVENT_PROCESS_DEFAULT_NAMESPACE);
            iTaskService.subscribe(
                    "事件{" + event.getClass().getName() + "}的处理",
                    eventProcessNamespace,
                    () -> {
                        _processEvent(event);
                        return EmptyTaskResult.INSTANCE;
                    },
                    EmptyTaskResult.getResultType()).finish();
        }
        return EmptyTaskResult.INSTANCE;
    }

    /**
     * 处理事件，包括通知监听器、在同一线程中推送下一个事件等。
     * 一旦有任何监听器出现异常就会停止处理事件，并将异常抛出到阶段链或者 TaskFuture 中处理。
     *
     * @param event 事件
     */
    private void _processEvent(IEvent<?> event) throws RuntimeException {
        int iteration = 0;
        while (event != null) {
            String TAG = "事件{%s}".formatted(event.getClass().getName());

            iteration += 1;
            // 判断迭代次数是否超过最大值
            if (iteration > MAX_ITERATION) {
                logger.warn(this.getClass().getSimpleName(),  "迭代次数超过最大值，停止处理事件。");
                return;
            }

            // 判断事件的完整性
            if (!event.isComplete()) {
                logger.warn(this.getClass().getSimpleName(), "%s 不完整，取消事件。".formatted(TAG));
                event.setCancelled(true);
                return;
            }

            // 通知之前的操作
            try {
                event._beforeNotify();
            } catch (Exception e) {
                throw new RuntimeException("%s 的通知前预处理异常".formatted(TAG), e);
            }

            // 判断事件是否已经被取消
            if (event.isCancelled()) {
                logger.debug(this.getClass().getSimpleName(), "%s 在通知前预处理后被取消。".formatted(TAG));
                // 不返回，因为可能存在忽略取消事件的监听器
            }

            // 通知事件的监听器
            try {
                // 获取事件的监听器
                var eventListenerPatchList = eventHandlerScanner.getEventHandlerList(event.getClass());
                // 遍历事件的监听器
                for (var patch: eventListenerPatchList) {
                    // 获取注解来决定其运行的配置
                    var annotation = patch.getAnnotation();
                    // 如果事件被取消，且监听器不忽略取消事件，则跳过该监听器
                    if(event.isCancelled() && !annotation.ignoreCancelled()) {
                        continue;
                    }
                    // 判断监听器是否异步
                    if (annotation.async()) {
                        _asyncProcessListener(TAG, event, patch);
                    } else {
                        // 如果不异步执行，则在当前线程中运行
                        patch.invoke(event);
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException("%s 通知异常".formatted(TAG), e);
            }

            // 判断事件是否已经被取消
            if (event.isCancelled()) {
                logger.debug(this.getClass().getSimpleName(), "%s 在通知时被取消。".formatted(TAG));
                // 返回，因为如果事件被取消，则不需要处理后续的操作
                return;
            }

            // 通知完后处理事件
            try {
                event._afterNotify();
            } catch (Exception e) {
                throw new RuntimeException("%s 通知后处理异常".formatted(TAG), e);
            }

            // 判断事件是否已经被取消
            if (event.isCancelled()) {
                logger.debug(this.getClass().getSimpleName(), "%s 在通知后处理后被取消。".formatted(TAG));
                // 如果事件被取消则不推送下一个事件
                return;
            }

            // 同一线程处理下一个事件
            event = event._getNextEvent();
        }
    }

    /**
     * 异步处理监听器。
     *
     * @param TAG 日志标签
     * @param event 事件
     * @param patch 监听器补丁
     */
    private void _asyncProcessListener(String TAG,
                IEvent<?> event,
                EventHandlerScanner.EventHandlerPatch<?> patch) {
        var annotation = patch.getAnnotation();
        var listener = patch.getTarget();
        // 获取命名空间，如果没有则使用默认命名空间
        String taskChainNamespace = Optional.ofNullable(annotation.taskChainNamespace())
            .filter(s -> !s.isEmpty())
            .orElse(EVENT_LISTENER_DEFAULT_NAMESPACE);
        // 注册
        iTaskService.subscribe("""
                        %s_监听器{%s}_{%s}的异步处理"""
                        .formatted(TAG, listener.getClass().getName(), patch.getMethod().getName()),
                taskChainNamespace,
                () -> {
                    patch.invoke(event);
                    return EmptyTaskResult.INSTANCE;
                }, EmptyTaskResult.getResultType()).finish();
    }
}
