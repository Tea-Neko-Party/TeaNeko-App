package org.zexnocs.teanekoplugin.contract.event;

// 2026/11/07：接口统一使用 I 前缀，调用合同与持久数据保持不变。

// 2026/11/07：插件归属夹具遵循与生产一致的业务包边界。
import org.zexnocs.teanekocore.event.*;

import org.junit.jupiter.api.Test;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskResult;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskService;
import org.zexnocs.teanekocore.event.core.EventHandler;
import org.zexnocs.teanekocore.event.core.EventHandlerScanner;
import org.zexnocs.teanekocore.event.core.EventListener;
import org.zexnocs.teanekocore.event.interfaces.IEvent;
import org.zexnocs.teanekocore.framework.function.IMethodCallable;
import org.zexnocs.teanekocore.framework.pair.IndependentPair;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekocore.plugin.PluginAccess;
import org.zexnocs.teanekocore.plugin.PluginDescriptor;
import org.zexnocs.teanekocore.plugin.PluginOwned;
import org.zexnocs.teanekocore.plugin.PluginRegistry;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IBeanScanner;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IClassScanner;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 验证插件事件占用覆盖真实业务，异步监听完成前不得封闭业务登记。
 * <br>2026/11/07：覆盖普通通知入口、异步监听、观察取消和停用排空。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
class PluginEventDrainTest {
    /**
     * 通知已返回不代表插件业务结束，旧通知入口也必须保持真实占用。
     * @throws Exception 当有界等待事件通知或排空失败时
     */
    @Test
    void notificationOnlyEntryStillDrainsRegisteredBusiness() throws Exception {
        var listener = new SynchronousListener();
        var fixture = new Fixture(listener);
        var event = new ProcessingEvent();
        fixture.events.pushEventWithFuture(event).asStage().toCompletableFuture().get(2, TimeUnit.SECONDS);
        assertEquals(1, event.seals);
        assertThrows(IllegalStateException.class, () -> event.trackProcessing(CompletableFuture.completedStage(null)));
        fixture.plugins.setEnabled("event-test", false);
        var drain = fixture.plugins.drain("event-test").toCompletableFuture();
        assertFalse(drain.isDone());
        listener.work.complete(null);
        drain.get(2, TimeUnit.SECONDS);
    }

    /**
     * 异步通知仍能登记业务，取消调用者观察不能让插件停用提前完成。
     * @throws Exception 当有界排空等待失败时
     */
    @Test
    void waitsForAsyncNotificationBeforeSealingAndForBusinessBeforeDrain() throws Exception {
        var listener = new AsynchronousListener();
        var fixture = new Fixture(listener);
        var event = new ProcessingEvent();
        var completion = fixture.events.pushEventWithCompletion(event).toCompletableFuture();
        assertEquals(0, event.seals);
        assertEquals(1, fixture.queued.size());
        fixture.queued.removeFirst().run();
        assertEquals(1, event.seals);
        assertFalse(completion.isDone());
        completion.cancel(false);
        fixture.plugins.setEnabled("event-test", false);
        var drain = fixture.plugins.drain("event-test").toCompletableFuture();
        assertFalse(drain.isDone());
        listener.work.complete(null);
        drain.get(2, TimeUnit.SECONDS);
    }

    /**
     * 使用原任务接口，显式控制异步通知排队，不依赖睡眠或真实外部服务。
     *
     * @author zExNocs
     * @date 2026/11/07
     * @since 4.9.0-l
     * @version 4.9.0-l
     */
    private static final class Fixture {
        private final PluginRegistry plugins = new PluginRegistry(List.of(
                () -> new PluginDescriptor("event-test", "1", 1, Set.of())));
        private final List<Runnable> queued = new ArrayList<>();
        private final EventService events;

        /**
         * @param listener 本例唯一真实事件监听器
         */
        private Fixture(Object listener) {
            var logger = mock(ILogger.class);
            var beans = mock(IBeanScanner.class);
            when(beans.getBeansWithAnnotation(EventListener.class)).thenReturn(Map.of("receiver",
                    new IndependentPair<>(listener.getClass().getAnnotation(EventListener.class), listener)));
            var types = mock(IClassScanner.class);
            when(types.getClassesWithInterface(IEvent.class)).thenReturn(Set.of());
            var access = new PluginAccess(plugins);
            var scanner = new EventHandlerScanner(beans, logger, types);
            scanner.installPlugins(access);
            scanner.init();
            var tasks = mock(ITaskService.class);
            when(tasks.subscribeWithFuture(anyString(), anyString(),
                    org.mockito.ArgumentMatchers.<IMethodCallable<ITaskResult<Void>>>any(), eq(Void.class)))
                    .thenAnswer(invocation -> {
                        var action = invocation.<IMethodCallable<ITaskResult<Void>>>getArgument(2);
                        var result = new CompletableFuture<ITaskResult<Void>>();
                        Runnable run = () -> {
                            try {
                                result.complete(action.call());
                            } catch (Exception failure) {
                                result.completeExceptionally(failure);
                            }
                        };
                        if (EventService.EVENT_LISTENER_DEFAULT_NAMESPACE.equals(invocation.getArgument(1))) {
                            queued.add(run);
                        } else {
                            run.run();
                        }
                        return TaskFuture.fromStage(logger, "插件事件测试", result);
                    });
            events = new EventService(logger, scanner, tasks);
            events.installPluginAccess(access);
        }
    }

    /**
     * 只在通知期间接收异步工作，明确记录封闭时机。
     *
     * @author zExNocs
     * @date 2026/11/07
     * @since 4.9.0-l
     * @version 4.9.0-l
     */
    public static final class ProcessingEvent extends AbstractEvent<String> {
        private int seals;

        /** 创建独立事件数据。 */
        private ProcessingEvent() {
            super("插件排空测试");
        }

        /** @return 当前通知登记的全部真实业务完成阶段 */
        @Override
        public synchronized CompletionStage<Void> processingCompletion() {
            seals++;
            return super.processingCompletion();
        }
    }

    /**
     * 同步通知只登记异步业务，插件仍须保持在途占用。
     *
     * @author zExNocs
     * @date 2026/11/07
     * @since 4.9.0-l
     * @version 4.9.0-l
     */
    @EventListener
    @PluginOwned("event-test")
    public static final class SynchronousListener {
        private final CompletableFuture<Void> work = new CompletableFuture<>();

        /** @param event 本次待登记的事件 */
        @EventHandler
        public void receive(ProcessingEvent event) {
            event.trackProcessing(work);
        }
    }

    /**
     * 异步监听在线程实际运行时登记业务，不能在排队期间被提前封闭。
     *
     * @author zExNocs
     * @date 2026/11/07
     * @since 4.9.0-l
     * @version 4.9.0-l
     */
    @EventListener
    @PluginOwned("event-test")
    public static final class AsynchronousListener {
        private final CompletableFuture<Void> work = new CompletableFuture<>();

        /** @param event 本次待登记的事件 */
        @EventHandler(async = true)
        public void receive(ProcessingEvent event) {
            event.trackProcessing(work);
        }
    }
}
