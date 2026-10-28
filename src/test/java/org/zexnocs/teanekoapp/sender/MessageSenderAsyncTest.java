package org.zexnocs.teanekoapp.sender;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.zexnocs.teanekoapp.response.api.IMessageSendResponseData;
import org.zexnocs.teanekoapp.sender.api.sender_box.IEasyMessageSenderBuilder;
import org.zexnocs.teanekoapp.sender.api.sender_box.IForwardMessageSenderBuilder;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.logger.ILogger;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 验证普通与转发发送器的异步回执等待和异常传播。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
class MessageSenderAsyncTest {
    private final ILogger logger = mock(ILogger.class);

    /**
     * 选择普通或转发发送接口，向受控 TaskFuture 接入实际完成适配。
     *
     * @param response 测试主动控制的发送回执
     * @param forward 是否使用转发发送入口
     * @return 本用例受控的完成阶段，可由测试推进成功、拒绝或异常
     */
    private CompletionStage<Void> send(CompletableFuture<IMessageSendResponseData> response, boolean forward) {
        var task = new TaskFuture<>(logger, "send", response);
        if (forward) {
            var builder = mock(IForwardMessageSenderBuilder.class, CALLS_REAL_METHODS);
            doReturn(task).when(builder).sendWithFuture();
            return builder.sendAsync();
        }
        var builder = mock(IEasyMessageSenderBuilder.class, CALLS_REAL_METHODS);
        doReturn(task).when(builder).sendWithFuture();
        return builder.sendAsync();
    }

    /**
     * 验证发送阶段等待实际平台回执。
     *
     * @param forward 是否使用转发发送入口
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void stageWaitsForActualResponse(boolean forward) {
        var response = new CompletableFuture<IMessageSendResponseData>();
        var stage = send(response, forward);
        assertFalse(stage.toCompletableFuture().isDone());

        response.complete(() -> "message-id");

        assertNull(stage.toCompletableFuture().join());
        verifyNoInteractions(logger);
    }

    /**
     * 验证空回执明确失败，不伪装为成功的 Void。
     *
     * @param forward 是否使用转发发送入口
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void nullResponseFailsInsteadOfClaimingSuccess(boolean forward) {
        var stage = send(CompletableFuture.completedFuture(null), forward);

        var failure = assertThrows(CompletionException.class, () -> stage.toCompletableFuture().join());

        assertInstanceOf(MessageSendFailedException.class, failure.getCause());
        verifyNoInteractions(logger);
    }

    /**
     * 验证异步失败直接传播，不自动添加链尾日志。
     *
     * @param forward 是否使用转发发送入口
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void asynchronousFailurePropagatesWithoutInstallingLogging(boolean forward) {
        var response = new CompletableFuture<IMessageSendResponseData>();
        var stage = send(response, forward);
        var expected = new IllegalStateException("platform failed");

        response.completeExceptionally(expected);

        assertSame(expected, assertThrows(CompletionException.class,
                () -> stage.toCompletableFuture().join()).getCause());
        verifyNoInteractions(logger);
    }
}
