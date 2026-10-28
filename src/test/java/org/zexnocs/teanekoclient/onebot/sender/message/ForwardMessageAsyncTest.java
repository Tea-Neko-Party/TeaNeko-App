package org.zexnocs.teanekoclient.onebot.sender.message;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.zexnocs.teanekoapp.sender.MessageSendFailedException;
import org.zexnocs.teanekoapp.sender.api.sender_box.IForwardMessageSenderBuilder;
import org.zexnocs.teanekoapp.sender.interfaces.ISenderService;
import org.zexnocs.teanekoclient.onebot.core.OnebotClient;
import org.zexnocs.teanekoclient.onebot.core.OnebotIdService;
import org.zexnocs.teanekoclient.onebot.data.response.params.OnebotMessageSendResponseData;
import org.zexnocs.teanekoclient.onebot.data.send.OnebotSendData;
import org.zexnocs.teanekoclient.onebot.data.send.params.message.GroupForwardMessageSendParamsData;
import org.zexnocs.teanekoclient.onebot.data.send.params.message.PrivateForwardMessageSendParamsData;
import org.zexnocs.teanekoclient.onebot.event.sent.OnebotMessageSentEvent;
import org.zexnocs.teanekoclient.onebot.utils.OnebotMessageFailSendHandler;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.actuator.task.TaskResult;
import org.zexnocs.teanekocore.actuator.task.exception.TaskUnsuccessfulException;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskResult;
import org.zexnocs.teanekocore.event.interfaces.IEvent;
import org.zexnocs.teanekocore.logger.ILogger;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 离线验证群聊与私聊转发的分段完成链及任务参数保持。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
class ForwardMessageAsyncTest {
    private final ISenderService sender = mock(ISenderService.class);
    private final OnebotMessageFailSendHandler failureHandler = mock(OnebotMessageFailSendHandler.class);
    private final ILogger logger = mock(ILogger.class);
    private final List<PendingSend> sent = new ArrayList<>();

    /**
     * 保存一个由测试主动确认的转发分段回执。
     *
     * @param event 用于本次测试的 {@code event} 数据
     * @param delay 提交任务前的延迟
     * @param retries 用于本次测试的 {@code retries} 数据
     * @param retryInterval 两次重试之间的等待时长
     * @param result 已经形成的处理结果
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
     * @version 4.5.0-l
     */
    private record PendingSend(IEvent<?> event, Duration delay, int retries, Duration retryInterval,
                               CompletableFuture<ITaskResult<List<OnebotMessageSendResponseData>>> result) {
        /**
         * 由用例主动完成一个转发分段，精确控制下一段启动的时机。
         */
        void succeed() {
            result.complete(new TaskResult<>(true,
                    List.of(new OnebotMessageSendResponseData(123L, null, null, null))));
        }
    }

    /**
     * 装配群聊或私聊转发构建器，使用受控依赖避免真实平台请求。
     *
     * @param group 是否选择群聊发送实现
     * @return 当前构建器，供调用方继续组合后提交
     */
    @SuppressWarnings({"unchecked"})
    private IForwardMessageSenderBuilder builder(boolean group) {
        doAnswer(invocation -> {
            var future = new CompletableFuture<ITaskResult<List<OnebotMessageSendResponseData>>>();
            sent.add(new PendingSend(invocation.getArgument(0), invocation.getArgument(1),
                    invocation.getArgument(2), invocation.getArgument(3), future));
            return new TaskFuture<>(logger, "forward message", future);
        }).when(sender).send(any(IEvent.class), any(Duration.class), anyInt(), any(Duration.class));
        when(failureHandler.recordFailed(anyString(), anyList(), any()))
                .thenAnswer(invocation -> invocation.getArgument(2));
        var client = mock(OnebotClient.class);
        var ids = mock(OnebotIdService.class);
        when(ids.getBotId()).thenReturn(456L);
        return group
                ? new GroupForwardMessageSender(sender, client, new ObjectMapper(), "bot", failureHandler, ids)
                    .getBuilder("scope-token", 789L)
                : new PrivateForwardMessageSender(sender, client, new ObjectMapper(), "bot", failureHandler, ids)
                    .getBuilder("scope-token", 789L);
    }

    /**
     * 验证每个转发分段都等待回执并保留原始内容与任务选项。
     *
     * @param group 是否选择群聊发送实现
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void waitsForEveryPartAndUsesOriginalOptionsAndContent(boolean group) {
        var builder = builder(group).setPrompt("original prompt").setSource("original source")
                .setSummary("original summary").setDelay(Duration.ofSeconds(1))
                .setRetryCount(3).setRetryInterval(Duration.ofMillis(300));
        builder.addBotAllText(List.of("first", "second", "third"));
        var stage = builder.sendByPartAsync(2);
        assertEquals(1, sent.size());
        assertFalse(stage.toCompletableFuture().isDone());
        builder.setPrompt("changed").setSource("changed").setSummary("changed")
                .setDelay(Duration.ZERO).setRetryCount(0).setRetryInterval(Duration.ZERO)
                .setRecordFailed(false).addBotText("late message");

        sent.getFirst().succeed();

        assertEquals(2, sent.size());
        assertFalse(stage.toCompletableFuture().isDone());
        for (int index = 0; index < sent.size(); index++) {
            var send = sent.get(index);
            var event = assertInstanceOf(OnebotMessageSentEvent.class, send.event());
            assertEquals("scope-token", event.getToken());
            assertNull(event.getMessageData());
            assertEquals(Duration.ofSeconds(1), send.delay());
            assertEquals(3, send.retries());
            assertEquals(Duration.ofMillis(300), send.retryInterval());
            var params = ((OnebotSendData<?, ?>) event.getData()).getParams();
            if (group) {
                var data = assertInstanceOf(GroupForwardMessageSendParamsData.class, params);
                assertEquals(789L, data.getGroupId());
                assertEquals("original prompt", data.getPrompt());
                assertEquals("original source", data.getSource());
                assertEquals("original summary", data.getSummary());
                assertEquals(index == 0 ? 2 : 1, data.getMessages().size());
            } else {
                var data = assertInstanceOf(PrivateForwardMessageSendParamsData.class, params);
                assertEquals(789L, data.getUserId());
                assertEquals("original prompt", data.getPrompt());
                assertEquals("original source", data.getSource());
                assertEquals("original summary", data.getSummary());
                assertEquals(index == 0 ? 2 : 1, data.getMessages().size());
            }
        }
        sent.getLast().succeed();

        assertNull(stage.toCompletableFuture().join());
        verify(failureHandler, times(2)).recordFailed(anyString(), anyList(), any());
        verifyNoInteractions(logger);
    }

    /**
     * 验证失败回执阻止后续转发分段。
     *
     * @param group 是否选择群聊发送实现
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void reportedFailureStopsFollowingParts(boolean group) {
        var builder = builder(group).setRecordFailed(false).addBotAllText(List.of("1", "2", "3"));
        var stage = builder.sendByPartAsync(1);
        sent.getFirst().result().complete(new TaskResult<>(false, List.of()));

        var error = assertThrows(CompletionException.class, () -> stage.toCompletableFuture().join());

        assertInstanceOf(TaskUnsuccessfulException.class, error.getCause());
        assertEquals(1, sent.size());
        verifyNoInteractions(failureHandler, logger);
    }

    /**
     * 验证异步异常阻止后续转发分段。
     *
     * @param group 是否选择群聊发送实现
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void asynchronousExceptionStopsFollowingParts(boolean group) {
        var builder = builder(group).setRecordFailed(false).addBotAllText(List.of("1", "2"));
        var stage = builder.sendByPartAsync(1);
        var expected = new IllegalStateException("platform unavailable");
        sent.getFirst().result().completeExceptionally(expected);

        var error = assertThrows(CompletionException.class, () -> stage.toCompletableFuture().join());
        assertSame(expected, error.getCause());
        assertEquals(1, sent.size());
    }

    /**
     * 验证取消或空回执会结束发送链且不启动下一段。
     *
     * @param group 是否选择群聊发送实现
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void cancelledOrMissingResponseFailsWithoutStartingNextPart(boolean group) {
        var builder = builder(group).setRecordFailed(false).addBotAllText(List.of("1", "2"));
        var stage = builder.sendByPartAsync(1);
        // OnebotMessageSentEvent.safeSetCancelled 使用成功但无响应列表的任务结果。
        sent.getFirst().result().complete(new TaskResult<>(true, null));

        var error = assertThrows(CompletionException.class, () -> stage.toCompletableFuture().join());

        assertInstanceOf(MessageSendFailedException.class, error.getCause());
        assertEquals(1, sent.size());
    }

    /**
     * 验证空转发列表不发送，非法分段参数被拒绝。
     *
     * @param group 是否选择群聊发送实现
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void emptyPartsDoNotSendAndInvalidSizeIsRejected(boolean group) {
        var builder = builder(group);

        assertNull(builder.sendByPartAsync(2).toCompletableFuture().join());
        assertThrows(IllegalArgumentException.class, () -> builder.sendByPartAsync(0));
        assertThrows(IllegalArgumentException.class, () -> builder.sendByPartAsync(-1));
        assertTrue(sent.isEmpty());
    }
}
