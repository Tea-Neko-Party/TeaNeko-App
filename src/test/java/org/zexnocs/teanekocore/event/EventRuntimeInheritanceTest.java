package org.zexnocs.teanekocore.event;

import org.junit.jupiter.api.Test;
import org.zexnocs.teanekoapp.message.TeaNekoMessageReceiveEvent;
import org.zexnocs.teanekoclient.web.WebMessageData;
import org.zexnocs.teanekoclient.web.WebMessageReceiveEvent;
import org.zexnocs.teanekocore.event.core.Event;
import org.zexnocs.teanekocore.event.core.EventHandler;
import org.zexnocs.teanekocore.event.core.EventHandlerScanner;
import org.zexnocs.teanekocore.event.core.EventListener;
import org.zexnocs.teanekocore.event.interfaces.IEvent;
import org.zexnocs.teanekocore.framework.pair.IndependentPair;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IBeanScanner;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IClassScanner;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 验证未进入类扫描目录的网页事件仍取得通用消息处理器，并保留实际异步终态。
 * <br>2026/11/07：无监听事件同样缓存命名空间，运行查询与重扫失败不重新发现声明。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.7.5-l
 * @version 4.9.0-l
 */
class EventRuntimeInheritanceTest {
    /**
     * 2026/11/04：同时覆盖运行时继承、扫描缓存去重和业务阶段失败传播。
     * @throws Exception 当真实处理器调用失败时
     */
    @Test
    void unscannedWebSubtypeReachesParentOnceAndWaitsForActualWork() throws Exception {
        var listener = new Receiver();
        var beans = mock(IBeanScanner.class);
        when(beans.getBeansWithAnnotation(EventListener.class)).thenReturn(Map.of("receiver",
                new IndependentPair<>(Receiver.class.getAnnotation(EventListener.class), listener)));
        var types = mock(IClassScanner.class);
        when(types.getClassesWithInterface(IEvent.class)).thenReturn(Set.of());
        var scanner = new EventHandlerScanner(beans, mock(ILogger.class), types);
        scanner.init();
        var event = new WebMessageReceiveEvent(mock(WebMessageData.class));
        var handlers = scanner.getEventHandlerList(WebMessageReceiveEvent.class);
        assertEquals(1, handlers.size());
        handlers.getFirst().invoke(event);
        var completion = event.processingCompletion().toCompletableFuture();
        assertFalse(completion.isDone());
        assertThrows(IllegalStateException.class, () -> event.trackProcessing(CompletableFuture.completedStage(null)));
        listener.work.completeExceptionally(new IllegalStateException("测试模型调用失败。"));
        assertThrows(CompletionException.class, completion::join);
        when(types.getClassesWithInterface(IEvent.class)).thenReturn(Set.of(WebMessageReceiveEvent.class));
        scanner.reload();
        assertEquals(1, scanner.getEventHandlerList(WebMessageReceiveEvent.class).size());
    }

    /** 无监听事件保留命名空间，未知类型只用默认执行域，重扫失败不污染已发布目录。 */
    @Test
    void cachesNamespacesForEventsWithoutListenersAndRetainsThemAfterReloadFailure() {
        var beans = mock(IBeanScanner.class);
        when(beans.getBeansWithAnnotation(EventListener.class)).thenReturn(Map.of());
        var types = mock(IClassScanner.class);
        when(types.getClassesWithInterface(IEvent.class)).thenReturn(Set.of(NamespacedEvent.class, DefaultEvent.class));
        var scanner = new EventHandlerScanner(beans, mock(ILogger.class), types);
        scanner.init();
        clearInvocations(beans, types);
        assertEquals("event-test-stage", scanner.namespace(NamespacedEvent.class));
        assertEquals("", scanner.namespace(DefaultEvent.class));
        assertNull(scanner.namespace(WebMessageReceiveEvent.class));
        assertTrue(scanner.getEventHandlerList(NamespacedEvent.class).isEmpty());
        verifyNoInteractions(beans, types);
        when(types.getClassesWithInterface(IEvent.class)).thenThrow(new IllegalStateException("测试重扫失败。"));
        assertThrows(IllegalStateException.class, scanner::reload);
        assertEquals("event-test-stage", scanner.namespace(NamespacedEvent.class));
    }

    /**
     * 没有监听器但声明了专用执行域的第三方事件。
     * @author zExNocs
     * @date 2026/11/07
     * @since 4.9.0-l
     * @version 4.9.0-l
     */
    @Event(value = "test-event", namespace = "event-test-stage")
    public static final class NamespacedEvent extends AbstractEvent<String> {
        /** 创建无额外业务的测试事件。 */
        private NamespacedEvent() {
            super("命名空间测试");
        }
    }

    /**
     * 不声明事件注解，沿用默认执行域的事件。
     * @author zExNocs
     * @date 2026/11/07
     * @since 4.9.0-l
     * @version 4.9.0-l
     */
    public static final class DefaultEvent extends AbstractEvent<String> {
        /** 创建无额外业务的测试事件。 */
        private DefaultEvent() {
            super("默认执行域测试");
        }
    }

    /**
     * 模拟同步消息监听器登记其真实异步业务，不阻塞原事件线程。
     *
     * @author zExNocs
     * @date 2026/11/04
     * @since 4.7.5-l
     * @version 4.7.5-l
     */
    @EventListener
    public static class Receiver {
        final CompletableFuture<Void> work = new CompletableFuture<>();

        /** @param event 实际客户端子事件 */
        @EventHandler
        public void receive(TeaNekoMessageReceiveEvent<?> event) {
            event.trackProcessing(work);
        }
    }
}
