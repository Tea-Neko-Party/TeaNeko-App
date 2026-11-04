package org.zexnocs.teanekocore.event;

import org.junit.jupiter.api.Test;
import org.zexnocs.teanekoapp.message.TeaNekoMessageReceiveEvent;
import org.zexnocs.teanekoclient.web.WebMessageData;
import org.zexnocs.teanekoclient.web.WebMessageReceiveEvent;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 验证未进入类扫描目录的网页事件仍取得通用消息处理器，并保留实际异步终态。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.7.5-l
 * @version 4.7.5-l
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
