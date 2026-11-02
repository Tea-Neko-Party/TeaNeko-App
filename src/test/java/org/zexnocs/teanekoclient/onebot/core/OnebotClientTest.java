package org.zexnocs.teanekoclient.onebot.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.zexnocs.teanekoclient.onebot.event.OnebotEventShareComponent;
import org.zexnocs.teanekocore.event.interfaces.IEventService;
import org.zexnocs.teanekocore.logger.ILogger;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.*;

/**
 * 验证容器关闭释放连接与在途任务，停机关闭通道和真实运行期故障分别记录。
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.0.0
 * @version 4.6.1-l
 */
@Timeout(10)
class OnebotClientTest {
    /**
     * 容器关闭时中断自有收发任务，关闭握手回调不报错误，也不再接纳新连接。
     * @throws Exception 当测试会话注册失败时
     */
    @Test void closesResourcesBeforeContainerDestruction() throws Exception {
        var logger = mock(ILogger.class); var events = mock(IEventService.class);
        var client = new OnebotClient(logger, events, mock(OnebotEventShareComponent.class));
        var session = mock(WebSocketSession.class); var open = new AtomicBoolean(true);
        when(session.getId()).thenReturn("原连接"); when(session.isOpen()).thenAnswer(ignored -> open.get());
        var sending = new CompletableFuture<Void>(); var interrupted = new CompletableFuture<Void>();
        doAnswer(ignored -> {
            sending.complete(null);
            try { new CountDownLatch(1).await(); }
            catch (InterruptedException failure) { interrupted.complete(null); Thread.currentThread().interrupt(); }
            return null;
        }).when(session).sendMessage(any());
        doAnswer(ignored -> {
            open.set(false); client.handleTransportError(session, new IOException(new ClosedChannelException())); return null;
        }).when(session).close(any(CloseStatus.class));
        try (var context = new GenericApplicationContext()) {
            context.registerBean(OnebotClient.class, () -> client); context.refresh();
            client.afterConnectionEstablished(session); client.send("测试消息"); sending.orTimeout(3, TimeUnit.SECONDS).join();
            context.close(); interrupted.orTimeout(3, TimeUnit.SECONDS).join();
            assertFalse(client.isRunning());
            verify(session).close(CloseStatus.SERVICE_RESTARTED);
            verify(logger, never()).error(anyString(), anyString(), any(Throwable.class));
            var rejected = mock(WebSocketSession.class);
            client.afterConnectionEstablished(rejected); verify(rejected).close(CloseStatus.SERVICE_RESTARTED);
            client.stop(); client.send("停机后消息");
            verify(session, times(1)).sendMessage(any());
        } finally { client.destroy(); }
    }

    /** 运行期关闭通道和其他传输异常仍报错，不能因降噪而漏报真实故障。 */
    @Test void preservesUnexpectedTransportErrors() {
        var logger = mock(ILogger.class);
        var client = new OnebotClient(logger, mock(IEventService.class), mock(OnebotEventShareComponent.class));
        var session = mock(WebSocketSession.class); when(session.getId()).thenReturn("故障连接");
        try {
            var runtimeFailure = new IOException(new ClosedChannelException());
            client.handleTransportError(session, runtimeFailure);
            verify(logger).error(anyString(), contains("传输失败"), same(runtimeFailure));
            client.stop();
            var unexpected = new IllegalStateException("测试真实故障");
            client.handleTransportError(session, unexpected);
            verify(logger).error(anyString(), contains("传输失败"), same(unexpected));
        } finally { client.destroy(); }
    }
}
