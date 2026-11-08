package org.zexnocs.teanekoplugin.general.games;

import org.junit.jupiter.api.Test;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekoapp.sender.api.sender_box.IEasyMessageSenderBuilder;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekocore.plugin.PluginAccess;
import org.zexnocs.teanekocore.plugin.PluginDescriptor;
import org.zexnocs.teanekocore.plugin.PluginRegistry;
import org.zexnocs.teanekoplugin.general.games.dice.DiceService;
import org.zexnocs.teanekoplugin.general.games.dice.data.DiceData;
import org.zexnocs.teanekoplugin.general.games.truth.TruthService;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 验证游戏倒计时真实取消、迟到回调隔离和结算发送排空。
 * <br>2026/11/07：由可控宿主调度器推进，不使用休眠或真实平台。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
class TruthLifecycleTest {
    /**
     * 停用取消未来倒计时，恢复不补发；手动结算后旧回调不能移除新轮次。
     * @throws Exception 当有界等待失败时
     */
    @Test
    void disabledTimerCannotSettleRestoredOrNewRound() throws Exception {
        var fixture = new Fixture();
        fixture.service.start().toCompletableFuture().get(2, TimeUnit.SECONDS);
        fixture.service.start(fixture.data, 35).toCompletableFuture().get(2, TimeUnit.SECONDS);
        fixture.registry.setEnabled("general.games", false);
        fixture.service.stop().toCompletableFuture().get(2, TimeUnit.SECONDS);
        verify(fixture.timers.getFirst()).cancel(false);
        fixture.registry.setEnabled("general.games", true);
        fixture.service.start().toCompletableFuture().get(2, TimeUnit.SECONDS);
        fixture.callbacks.getFirst().run();
        verify(fixture.sender, times(1)).sendTextMessageAsync(anyString());
        assertFalse(fixture.service.isNotRunning("scope"));
        fixture.service.end(fixture.data, "scope").toCompletableFuture().get(2, TimeUnit.SECONDS);
        fixture.service.start(fixture.data, 35).toCompletableFuture().get(2, TimeUnit.SECONDS);
        fixture.callbacks.getFirst().run();
        assertFalse(fixture.service.isNotRunning("scope"));
        verify(fixture.sender, times(3)).sendTextMessageAsync(anyString());
        fixture.service.stop().toCompletableFuture().get(2, TimeUnit.SECONDS);
        verify(fixture.scheduler, never()).shutdown();
        verify(fixture.scheduler, never()).shutdownNow();
    }

    /**
     * 倒计时已提交结算时停用等待实际发送，取消观察或提前恢复都不能释放占用。
     * @throws Exception 当有界等待失败时
     */
    @Test
    void stopWaitsForActualAutomaticSettlementReceipt() throws Exception {
        var fixture = new Fixture();
        var delivered = new CompletableFuture<Void>();
        when(fixture.sender.sendTextMessageAsync(anyString()))
                .thenReturn(CompletableFuture.completedStage(null)).thenReturn(delivered);
        fixture.service.start().toCompletableFuture().get(2, TimeUnit.SECONDS);
        fixture.service.start(fixture.data, 35).toCompletableFuture().get(2, TimeUnit.SECONDS);
        fixture.callbacks.getFirst().run();
        fixture.registry.setEnabled("general.games", false);
        var stopped = fixture.service.stop();
        try {
            assertFalse(stopped.toCompletableFuture().isDone());
            assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> fixture.service.start().toCompletableFuture().get(2, TimeUnit.SECONDS));
            stopped.toCompletableFuture().cancel(false);
            assertFalse(stopped.toCompletableFuture().isDone());
        } finally {
            delivered.complete(null);
        }
        stopped.toCompletableFuture().get(2, TimeUnit.SECONDS);
        fixture.registry.setEnabled("general.games", true);
        fixture.service.start().toCompletableFuture().get(2, TimeUnit.SECONDS);
        fixture.service.stop().toCompletableFuture().get(2, TimeUnit.SECONDS);
    }

    /**
     * 借用可控调度器与模拟发送，保留真实插件代次和游戏服务。
     * @author zExNocs
     * @date 2026/11/07
     * @since 4.9.0-l
     * @version 4.9.0-l
     */
    private static final class Fixture {
        private final List<Runnable> callbacks = new ArrayList<>();
        private final List<ScheduledFuture<?>> timers = new ArrayList<>();
        private final ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        private final IEasyMessageSenderBuilder sender = mock(IEasyMessageSenderBuilder.class);
        private final ITeaNekoMessageData data = mock(ITeaNekoMessageData.class);
        private final PluginRegistry registry = new PluginRegistry(List.of(
                () -> new PluginDescriptor("general.games", "1", 1, Set.of(), false)));
        private final TruthService service;

        /** 保存回调而不启动线程；取消后的回调仍可显式触发以模拟迟到执行。 */
        private Fixture() {
            when(scheduler.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class))).thenAnswer(invocation -> {
                callbacks.add(invocation.getArgument(0));
                ScheduledFuture<?> timer = mock(ScheduledFuture.class);
                timers.add(timer);
                return timer;
            });
            when(data.getScopeId()).thenReturn("scope");
            when(data.getMessageSender()).thenReturn(sender);
            when(sender.sendTextMessageAsync(anyString())).thenReturn(CompletableFuture.completedStage(null));
            var dice = mock(DiceService.class);
            when(dice.getDiceData("scope")).thenReturn(new DiceData("scope", 1, 100));
            service = new TruthService(scheduler, dice, mock(ILogger.class), new PluginAccess(registry));
        }
    }
}
