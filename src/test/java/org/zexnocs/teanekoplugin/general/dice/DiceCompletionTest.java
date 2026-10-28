package org.zexnocs.teanekoplugin.general.dice;

import org.junit.jupiter.api.Test;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekoapp.sender.api.sender_box.IEasyMessageSenderBuilder;
import org.zexnocs.teanekocore.actuator.task.EmptyTaskResult;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.command.CommandData;
import org.zexnocs.teanekocore.event.interfaces.IEvent;
import org.zexnocs.teanekocore.event.interfaces.IEventService;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekocore.utils.RandomUtil;
import org.zexnocs.teanekoplugin.general.dice.event.DiceAfterEvent;
import org.zexnocs.teanekoplugin.general.dice.event.DiceSetEvent;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 验证骰子业务事件与替代通知均纳入命令完成链。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
class DiceCompletionTest {
    private final ILogger logger = mock(ILogger.class);
    private final IEventService events = mock(IEventService.class);
    private final ITeaNekoMessageData source = mock(ITeaNekoMessageData.class);
    private final IEasyMessageSenderBuilder sender = mock(IEasyMessageSenderBuilder.class);
    private final CompletableFuture<Void> sent = new CompletableFuture<>();
    private Consumer<IEvent<?>> listener = event -> {};

    /**
     * 在用例前重新装配事件服务与受控骰子依赖，精确控制通知的完成时机。
     *
     * @return 满足本方法约束的 {@code DiceService} 结果
     */
    private DiceService prepare() {
        when(source.getScopeId()).thenReturn("group");
        when(source.getMessageSender()).thenReturn(sender);
        when(sender.sendReplyMessageAsync(anyString())).thenReturn(sent);
        when(events.pushEventWithFuture(any())).thenAnswer(invocation -> {
            IEvent<?> event = invocation.getArgument(0);
            listener.accept(event);
            if (!event.isCancelled()) event._afterNotify();
            return new TaskFuture<>(logger, "dice event", CompletableFuture.completedFuture(EmptyTaskResult.INSTANCE));
        });
        return new DiceService(events, mock(RandomUtil.class));
    }

    /**
     * 验证骰子命令穿过两个业务事件并等待回复完成。
     */
    @Test
    void diceCommandWaitsThroughBothBusinessEventsUntilReplyCompletes() {
        var service = prepare();
        var stage = new DiceCommand(service, events).dice(CommandData.<ITeaNekoMessageData>builder().rawData(source).build(), -1);
        assertFalse(stage.toCompletableFuture().isDone());
        sent.complete(null);
        assertNull(stage.toCompletableFuture().join());
        verify(events, times(2)).pushEventWithFuture(any());
    }

    /**
     * 验证默认通知关闭时仍等待自定义业务通知。
     */
    @Test
    void customBusinessNotificationIsAwaitedWhenDefaultNotificationIsDisabled() {
        var service = prepare();
        listener = event -> {
            if (event instanceof DiceAfterEvent after) {
                after.getData().setUseDefaultNotification(false);
                after.includeCompletion(sent);
            }
        };
        var stage = service.attemptDice(source, -1);
        assertFalse(stage.toCompletableFuture().isDone());
        verifyNoInteractions(sender);
        sent.complete(null);
        assertNull(stage.toCompletableFuture().join());
    }

    /**
     * 验证骰子设置取消后不改状态但仍等待已登记反馈。
     */
    @Test
    void cancelledDiceSettingStillWaitsForRegisteredFeedbackWithoutChangingDice() {
        var service = prepare();
        listener = event -> {
            if (event instanceof DiceSetEvent setting) {
                setting.setCancelled(true);
                setting.includeCompletion(sent);
            }
        };
        var stage = new DiceCommand(service, events).set(CommandData.<ITeaNekoMessageData>builder().rawData(source).build(), "2d10");
        assertFalse(stage.toCompletableFuture().isDone());
        assertEquals(1, service.getDiceData("group").getDiceCount());
        sent.complete(null);
        assertNull(stage.toCompletableFuture().join());
        verifyNoInteractions(sender);
    }

    /**
     * 验证异步通知失败到达骰子命令调用者。
     */
    @Test
    void asynchronousNotificationFailureReachesTheDiceCaller() {
        var service = prepare();
        var stage = service.attemptDice(source, -1);
        var expected = new IllegalStateException("send failed");
        sent.completeExceptionally(expected);
        assertSame(expected, TaskFuture.unwrapException(assertThrows(CompletionException.class,
                () -> stage.toCompletableFuture().join())));
    }
}
