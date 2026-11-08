package org.zexnocs.teanekocore.event;

// 2026/11/07：接口统一使用 I 前缀，调用合同与持久数据保持不变。

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.actuator.task.EmptyTaskResult;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskResult;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskService;
import org.zexnocs.teanekocore.event.core.EventHandlerScanner;
import org.zexnocs.teanekocore.event.interfaces.IEvent;
import org.zexnocs.teanekocore.event.interfaces.IEventService;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekocore.plugin.PluginRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 事件服务，负责处理事件的推送和分发。
 * 当前事件服务是一旦有任何监听器出现异常就会停止处理事件，并将异常抛出到命名空间或者 TaskFuture 中处理。
 * <br>2026/11/02：按插件归属过滤监听器，决策事件拒绝异步监听并复核调用版本。
 * <br>2026/11/07：完整事件入口跟踪通知、异步监听和各级业务的实际完成。
 * <br>2026/11/07：所有通知结束后统一封闭业务登记，插件调用占用保持到真实业务终态。
 *
 * @author zExNocs
 * @date 2026/02/17
 * @since 4.0.8
 * @version 4.9.0-l
 */
@Service
public class EventService implements IEventService {
    // 2026/11/02：插件归属由宿主统一解析，保留直接构造的旧使用方式。
    private org.zexnocs.teanekocore.plugin.PluginAccess pluginAccess;

    /**
     * 安装插件调用守卫。
     * @param access 统一插件归属入口
     */
    @Autowired
    public void installPluginAccess(org.zexnocs.teanekocore.plugin.PluginAccess access) { this.pluginAccess = access; }

    /**
     * 检查插件有效范围，宿主未声明归属的监听器保留原语义。
     * @param event 当前事件
     * @param patch 监听器
     * @return 当前允许通知时为真
     */
    private boolean accepts(IEvent<?> event, EventHandlerScanner.EventHandlerPatch<?> patch) {
        if (pluginAccess == null) { return true; }
        if (!pluginAccess.enabled(patch.getTarget(), patch.getMethod())) { return false; }
        var owner = pluginAccess.owner(patch.getTarget(), patch.getMethod());
        return owner == null || !(event instanceof org.zexnocs.teanekocore.plugin.IPluginScopedEvent scoped) || scoped.acceptsPlugin(owner);
    }

    /**
     * 在实际运行线程捕获和复核归属版本。
     * @param event 当前事件
     * @param patch 监听器
     * @param calls 本事件实际开启的调用，通知结束后统一跟随业务完成
     * @throws Exception 当监听器或插件守卫失败时
     */
    private void invokeListener(IEvent<?> event, EventHandlerScanner.EventHandlerPatch<?> patch,
            Queue<PluginRegistry.Call> calls) throws Exception {
        if (!accepts(event, patch)) { return; }
        // 2026/11/07：即使监听器登记工作后同步抛错，其占用也须等已登记业务真实收尾。
        var call = pluginAccess == null ? null : pluginAccess.open(patch.getTarget(), patch.getMethod());
        if (call != null) {
            calls.add(call);
        }
        patch.invoke(event);
        if (call != null) {
            call.check();
        }
    }
    /// 处理的最大迭代次数
    private static final int MAX_ITERATION = 10;
    /// 事件处理服务的基本命名空间
    public static final String EVENT_PROCESS_DEFAULT_NAMESPACE = "event-process-default";
    /// 监听器处理服务的基本命名空间
    public static final String EVENT_LISTENER_DEFAULT_NAMESPACE = "event-listener-default";

    /// 任务服务
    private final ITaskService iTaskService;

    /// 日志记录器
    private final ILogger logger;

    /// 事件处理器扫描器
    private final EventHandlerScanner eventHandlerScanner;

    /**
     * 2026/11/02：补充依赖合同，借用扫描、任务和日志设施，构造不分发事件。
     * @param logger 项目日志
     * @param eventHandlerScanner 监听器目录
     * @param iTaskService 原任务服务
     */
    @Lazy
    @Autowired
    public EventService(ILogger logger,
                        EventHandlerScanner eventHandlerScanner,
                        ITaskService iTaskService) {
        this.logger = logger;
        this.eventHandlerScanner = eventHandlerScanner;
        this.iTaskService = iTaskService;
    }

    /**
     * 推送事件并获取一个 TaskFuture 对象，可以通过该对象获取事件异常或者等待事件处理完成。
     * @param event 事件
     * @return TaskFuture 对象，可以通过该对象获取事件异常或者等待事件处理完成。务必在事件处理完使用 .finish() 方法报告未处理的异常。
     */
    @Override
    public TaskFuture<ITaskResult<Void>> pushEventWithFuture(@NonNull IEvent<?> event) {
        // 2026/11/07：运行入口只读取扫描阶段发布的命名空间。
        String eventProcessNamespace = Optional.ofNullable(eventHandlerScanner.namespace(event.getClass()))
            .filter(s -> !s.isEmpty())
            .orElse(EVENT_PROCESS_DEFAULT_NAMESPACE);
        return iTaskService.subscribeWithFuture(
            "事件{" + event.getClass().getName() + "}的处理",
            eventProcessNamespace,
            () -> {
                _processEvent(event, null);
                return EmptyTaskResult.INSTANCE;
            },
            EmptyTaskResult.getResultType());
    }

    /**
     * 2026/11/06：在原事件分发中收集每一级实际业务，不阻塞事件线程。
     * @param event 原始事件
     * @return 通知和实际业务均收尾后的阶段，异常继续传播
     */
    @Override
    public CompletionStage<Void> pushEventWithCompletion(@NonNull IEvent<?> event) {
        var work = new ArrayList<CompletionStage<?>>();
        // 2026/11/07：与普通通知共用静态声明，不因完整等待入口而重复解析事件注解。
        String namespace = Optional.ofNullable(eventHandlerScanner.namespace(event.getClass()))
            .filter(value -> !value.isEmpty()).orElse(EVENT_PROCESS_DEFAULT_NAMESPACE);
        var notification = iTaskService.subscribeWithFuture("完整事件处理", namespace, () -> {
            _processEvent(event, work);
            return EmptyTaskResult.INSTANCE;
        }, Void.class);
        return notification.physicalCompletion().thenCompose(ignored -> {
            work.add(notification.asStage());
            return CompletableFuture.allOf(work.stream().map(CompletionStage::toCompletableFuture)
                .toArray(CompletableFuture<?>[]::new));
        });
    }

    /**
     * 处理事件，包括通知监听器、在同一线程中推送下一个事件等。
     * 一旦有任何监听器出现异常就会停止处理事件，并将异常抛出到阶段链或者 TaskFuture 中处理。
     *
     * @param event 事件
     * @param work 可选完整完成阶段收集器，普通通知入口为空
     * @throws RuntimeException 当监听、决策异步约束或生命周期回调失败时
     */
    private void _processEvent(IEvent<?> event, @Nullable List<CompletionStage<?>> work) throws RuntimeException {
        int iteration = 0;
        while (event != null) {
            var currentEvent = event;
            // 2026/11/07：异步监听也在本轮封闭登记之前完成，普通通知入口同样保留插件占用。
            var notifications = new ArrayList<CompletionStage<?>>();
            Queue<PluginRegistry.Call> calls = new ConcurrentLinkedQueue<>();
            try {
                String TAG = "事件{%s}".formatted(event.getClass().getName());

                iteration += 1;
                // 判断迭代次数是否超过最大值
                if (iteration > MAX_ITERATION) {
                    logger.warn(this.getClass().getSimpleName(), "迭代次数超过最大值，停止处理事件。");
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
                    for (var patch : eventListenerPatchList) {
                        // 2026/11/02：只向有效插件通知，决策链禁止不等待的异步修改。
                        if (!accepts(event, patch)) {
                            continue;
                        }
                        // 获取注解来决定其运行的配置
                        var annotation = patch.getAnnotation();
                        // 如果事件被取消，且监听器不忽略取消事件，则跳过该监听器
                        if (event.isCancelled() && !annotation.ignoreCancelled()) {
                            continue;
                        }
                        // 判断监听器是否异步
                        if (annotation.async()) {
                            if (event instanceof org.zexnocs.teanekocore.plugin.IPluginScopedEvent scoped && !scoped.allowsAsyncListeners()) {
                                throw new IllegalArgumentException("可修改决策事件不允许异步监听器。");
                            }
                            var listener = _asyncProcessListener(TAG, event, patch, calls);
                            notifications.add(listener.physicalCompletion().thenCompose(ignored -> listener.asStage()));
                        } else {
                            // 如果不异步执行，则在当前线程中运行
                            invokeListener(event, patch, calls);
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
            } finally {
                var completion = finishEvent(currentEvent, notifications, calls);
                if (work != null) {
                    work.add(completion);
                }
            }
        }
    }

    /**
     * 2026/11/07：所有实际监听返回后才封闭事件，失败通知同样等待已登记业务并释放调用占用。
     * @param event 本级事件，不能在通知仍执行时提前读取完成合同
     * @param notifications 本级异步通知的物理完成及结果阶段
     * @param calls 实际运行过的插件监听调用
     * @return 通知、登记业务与版本复核全部结束的阶段
     */
    private CompletionStage<Void> finishEvent(IEvent<?> event, List<CompletionStage<?>> notifications,
            Queue<PluginRegistry.Call> calls) {
        var notified = CompletableFuture.allOf(notifications.stream().map(CompletionStage::toCompletableFuture)
                .toArray(CompletableFuture<?>[]::new));
        return notified.handle((ignored, failure) -> (Void) null).thenCompose(ignored -> {
            CompletionStage<Void> processing;
            try {
                processing = java.util.Objects.requireNonNull(event.processingCompletion(), "事件业务完成阶段不能为空。");
            } catch (RuntimeException | Error failure) {
                processing = CompletableFuture.failedStage(failure);
            }
            var completed = new ArrayList<CompletableFuture<?>>();
            completed.add(notified);
            completed.add(processing.toCompletableFuture());
            for (var call : calls) {
                completed.add(call.track(processing).toCompletableFuture());
            }
            return CompletableFuture.allOf(completed.toArray(CompletableFuture<?>[]::new));
        }).minimalCompletionStage();
    }

    /**
     * 异步处理监听器。
     *
     * @param TAG 日志标签
     * @param event 事件
     * @param patch 监听器补丁
     * @param calls 本事件的实际插件调用占用
     * @return 原任务执行器管理的监听通知结果
     */
    private TaskFuture<ITaskResult<Void>> _asyncProcessListener(String TAG,
                                                                IEvent<?> event,
                                                                EventHandlerScanner.EventHandlerPatch<?> patch,
                                                                Queue<PluginRegistry.Call> calls) {
        var annotation = patch.getAnnotation();
        var listener = patch.getTarget();
        // 获取命名空间，如果没有则使用默认命名空间
        String taskChainNamespace = Optional.ofNullable(annotation.taskChainNamespace())
            .filter(s -> !s.isEmpty())
            .orElse(EVENT_LISTENER_DEFAULT_NAMESPACE);
        // 注册
        var result = iTaskService.subscribeWithFuture("""
                %s_监听器{%s}_{%s}的异步处理"""
                .formatted(TAG, listener.getClass().getName(), patch.getMethod().getName()),
            taskChainNamespace,
            () -> {
                // 2026/11/02：排队期间禁用的插件不再执行。
                invokeListener(event, patch, calls);
                return EmptyTaskResult.INSTANCE;
            }, EmptyTaskResult.getResultType());
        result.finish();
        return result;
    }
}
