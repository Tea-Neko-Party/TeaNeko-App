package org.zexnocs.teanekoclient.onebot.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.contains;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证容器关闭释放连接与在途任务，停机关闭通道和真实运行期故障分别记录。
 * 修改记录：<br>
 * 2026/11/03：增加连接账号绑定与换号撤销验证，保护管理目录的平台查询。
 * 2026/11/03：验证指令台固定连接、账号与平台状态限制，不回退广播。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/07：验证完整消息饱和时仍接收 API 回执，关闭不提前回收业务占用。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.6.1-l
 * @version 4.8.7-l
 */
@Timeout(10)
class OnebotClientTest {
    /**
     * 2026/11/07：消息占满时回执保留通道仍可用；关闭及逻辑等待均不冒充真实业务结束。
     * @throws Exception 当隔离连接或有界等待失败时
     */
    @Test
    void boundsWholeInboundWorkAndReservesResponseCapacity() throws Exception {
        var logger = mock(ILogger.class);
        var events = mock(IEventService.class);
        var component = new OnebotEventShareComponent(logger,
            mock(org.zexnocs.teanekocore.event.core.EventScanner.class), new tools.jackson.databind.ObjectMapper(),
            mock(OnebotTeaNekoClient.class), mock(org.zexnocs.teanekoapp.teauser.interfaces.ITeaUserService.class),
            events, mock(org.zexnocs.teanekoapp.command.TeaNekoCommandConverter.class),
            mock(org.zexnocs.teanekocore.file_config.interfaces.IFileConfigService.class), mock(OnebotDebuggerService.class));
        var pending = new CompletableFuture<Void>();
        var started = new CountDownLatch(64);
        var response = new CountDownLatch(1);
        when(events.pushEventWithCompletion(any())).thenAnswer(call -> {
            if (call.getArgument(0) instanceof org.zexnocs.teanekoapp.response.ResponseEvent) {
                response.countDown();
                return CompletableFuture.completedStage(null);
            }
            started.countDown();
            return pending;
        });
        var client = new OnebotClient(logger, events, component);
        var session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("容量验收连接");
        when(session.isOpen()).thenReturn(true);
        try {
            client.afterConnectionEstablished(session);
            var frame = new TextMessage("{\"self_id\":123,\"post_type\":\"meta_event\",\"meta_event_type\":\"heartbeat\"}");
            for (int i = 0; i < 64; i++) {
                client.handleTextMessage(session, frame);
            }
            assertTrue(started.await(3, TimeUnit.SECONDS));
            assertEquals(64, client.getInboundCount());
            client.handleTextMessage(session, new TextMessage("{\"self_id\":123,\"status\":\"ok\",\"echo\":\"x\",\"data\":[]}"));
            assertTrue(response.await(3, TimeUnit.SECONDS));
            client.handleTextMessage(session, frame);
            verify(session).close(argThat(status -> status.getCode() == 1013));
            verify(events, times(65)).pushEventWithCompletion(any());
            client.stop();
            assertEquals(64, client.getInboundCount());
            pending.complete(null);
            org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(3))
                .untilAsserted(() -> assertEquals(0, client.getInboundCount()));
            verify(logger, never()).error(anyString(), anyString(), any(Throwable.class));
        } finally {
            pending.complete(null);
            client.destroy();
        }
    }
    /**
     * 只有唯一连接且已从原始帧确认账号才可查询，换号帧立即使原绑定失效。
     * @throws Exception 当测试连接登记失败时
     */
    @Test
    void bindsAccountToTheCurrentConnectionAndRejectsAccountChanges() throws Exception {
        var logger = mock(ILogger.class);
        var events = mock(IEventService.class);
        when(events.pushEventWithCompletion(any())).thenReturn(CompletableFuture.completedStage(null));
        var component = new OnebotEventShareComponent(logger,
            mock(org.zexnocs.teanekocore.event.core.EventScanner.class), new tools.jackson.databind.ObjectMapper(),
            mock(OnebotTeaNekoClient.class), mock(org.zexnocs.teanekoapp.teauser.interfaces.ITeaUserService.class),
            events, mock(org.zexnocs.teanekoapp.command.TeaNekoCommandConverter.class),
            mock(org.zexnocs.teanekocore.file_config.interfaces.IFileConfigService.class),
            mock(OnebotDebuggerService.class));
        var client = new OnebotClient(logger, events, component);
        var session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("账号连接");
        when(session.isOpen()).thenReturn(true);
        try {
            client.afterConnectionEstablished(session);
            assertFalse(client.connectionIdentity().ready());
            client.handleTextMessage(session, new TextMessage("{\"self_id\":123,\"status\":\"ok\",\"echo\":\"x\",\"data\":[]}"));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (!client.connectionIdentity().ready() && System.nanoTime() < deadline) {
                Thread.sleep(5);
            }
            var bound = client.connectionIdentity();
            assertTrue(bound.ready());
            assertEquals("123", bound.selfId());
            // 2026/11/03：唯一连接写入一次，当前管理授权拒绝时不写入。
            client.sendPinned(bound, "定向测试", () -> {
            });
            verify(session).sendMessage(argThat(message -> "定向测试".equals(message.getPayload())));
            assertThrows(SecurityException.class, () -> client.sendPinned(bound, "撤权后内容", () -> {
                throw new SecurityException("测试撤权");
            }));
            var directory = mock(OnebotChatDirectory.class);
            when(directory.accounts()).thenReturn(CompletableFuture.completedStage(java.util.List.of(
                new org.zexnocs.teanekoapp.web.client.ChatTargets.Account("a", "测试账号", "999", java.time.Instant.now()))));
            var state = mock(org.zexnocs.teanekoclient.onebot.state.OnebotStateMachine.class);
            when(state.getCurrentState()).thenReturn(org.zexnocs.teanekoclient.onebot.state.OnebotState.DEFAULT);
            var bridge = new OnebotConsoleBridge(client, directory, state,
                mock(org.zexnocs.teanekoapp.sender.interfaces.ISenderService.class), new tools.jackson.databind.ObjectMapper());
            var target = new org.zexnocs.teanekoapp.web.client.ChatTargets.Resolved("onebot", "a", "t", "onebot-group@1", "测试群", "GROUP", "1", false);
            assertEquals("ACCOUNT_MISMATCH", bridge.describe(target).toCompletableFuture().join().state());
            when(directory.accounts()).thenReturn(CompletableFuture.completedStage(java.util.List.of(
                new org.zexnocs.teanekoapp.web.client.ChatTargets.Account("a", "测试账号", "123", java.time.Instant.now()))));
            assertEquals("READY", bridge.describe(target).toCompletableFuture().join().state());
            when(state.getCurrentState()).thenReturn(org.zexnocs.teanekoclient.onebot.state.OnebotState.DEBUG);
            assertEquals("PLATFORM_RESTRICTED", bridge.describe(target).toCompletableFuture().join().state());
            var other = mock(WebSocketSession.class);
            when(other.getId()).thenReturn("其他连接");
            when(other.isOpen()).thenReturn(true);
            client.afterConnectionEstablished(other);
            assertThrows(IllegalStateException.class, () -> client.sendPinned(bound, "不可广播", () -> {
            }));
            verify(other, never()).sendMessage(any());
            client.afterConnectionClosed(other, CloseStatus.NORMAL);
            assertThrows(IllegalStateException.class, () -> client.sendPinned(bound, "不可改投", () -> {
            }));
            client.handleTextMessage(session, new TextMessage("{\"self_id\":456}"));
            verify(session, timeout(3000)).close(CloseStatus.NORMAL);
            assertFalse(client.connectionIdentity().ready());
            assertTrue(client.connectionIdentity().epoch() > bound.epoch());
        } finally {
            client.destroy();
        }
    }

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
