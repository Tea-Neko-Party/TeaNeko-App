package org.zexnocs.teanekoplugin.support;

// 2026/11/07：认知业务契约归入插件，纯会话数据沿用运行底座。

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.ArgumentCaptor;
import org.zexnocs.teanekoplugin.agent.cognition.memory.layered.IMemoryMaintenance;
import org.zexnocs.teanekoplugin.agent.automation.schedule.AgentScheduleService;
import org.zexnocs.teanekoplugin.agent.automation.schedule.AgentScheduledTask;
import org.zexnocs.teanekoplugin.agent.automation.schedule.IAgentScheduleStore;
import org.zexnocs.teanekocore.actuator.timer.interfaces.ITimer;
import org.zexnocs.teanekocore.actuator.timer.interfaces.ITimerService;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekoplugin.agent.cognition.memory.layered.MemoryMaintenanceService;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 用实际生命周期等待原异步阶段，验证停用与重新启用之间没有旧代工作穿透。
 * <br>2026/11/07：覆盖记忆维护的真实提交与持久计划的旧计时器失效。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
@Timeout(10)
class PluginBackgroundLifecycleTest {
    /**
     * 停用等待当前维护端口提交，跳过后续端口，重新启用恢复完整维护。
     * @throws Exception 有界等待失败时
     */
    @Test
    void memoryMaintenanceWaitsForActualPortBeforeRestart() throws Exception {
        var first = mock(IMemoryMaintenance.class);
        var second = mock(IMemoryMaintenance.class);
        var pending = new CompletableFuture<Integer>();
        when(first.maintain(any())).thenReturn(pending).thenReturn(CompletableFuture.completedStage(2));
        when(second.maintain(any())).thenReturn(CompletableFuture.completedStage(3));
        try (var service = new MemoryMaintenanceService(List.of(first, second), () -> true,
                Runnable::run, Clock.systemUTC(), mock(ILogger.class))) {
            assertEquals(0, service.drain().toCompletableFuture().get(3, TimeUnit.SECONDS));
            verifyNoInteractions(first, second);
            service.start().toCompletableFuture().get(3, TimeUnit.SECONDS);
            var work = service.drain().toCompletableFuture();
            var stopped = service.stop().toCompletableFuture();
            try {
                assertFalse(stopped.isDone());
                assertThrows(CompletionException.class, () -> service.start().toCompletableFuture().join());
                verifyNoInteractions(second);
            } finally {
                pending.complete(1);
            }
            assertEquals(1, work.get(3, TimeUnit.SECONDS));
            stopped.get(3, TimeUnit.SECONDS);
            verifyNoInteractions(second);
            service.start().toCompletableFuture().get(3, TimeUnit.SECONDS);
            assertEquals(5, service.drain().toCompletableFuture().get(3, TimeUnit.SECONDS));
        }
    }

    /**
     * 持久计划停止等待真实发现结束，重新注册后旧计时器回调仍不可触发新扫描。
     * @throws Exception 有界等待或原计时器调用失败时
     */
    @Test
    void scheduleRestartDoesNotReviveOldTimerGeneration() throws Exception {
        var store = mock(IAgentScheduleStore.class);
        when(store.page("")).thenReturn(CompletableFuture.completedStage(List.of()));
        var pending = new CompletableFuture<List<AgentScheduledTask>>();
        when(store.due(any(), anyInt())).thenReturn(pending);
        var timers = mock(ITimerService.class);
        try (var schedules = new AgentScheduleService(store, Clock.systemUTC(),
                (task, operation, actor) -> CompletableFuture.completedStage(null), Map.of())) {
            schedules.start(timers).toCompletableFuture().get(3, TimeUnit.SECONDS);
            ArgumentCaptor<ITimer<?>> captured = ArgumentCaptor.captor();
            verify(timers).register(captured.capture());
            var old = captured.getValue().getTimerTaskConfig();
            var work = schedules.tick().toCompletableFuture();
            var stopped = schedules.stop().toCompletableFuture();
            try {
                assertFalse(stopped.isDone());
                assertFalse(old.getLivable().isAlive());
                assertThrows(CompletionException.class, () -> schedules.start(timers).toCompletableFuture().join());
            } finally {
                pending.complete(List.of());
            }
            work.get(3, TimeUnit.SECONDS);
            stopped.get(3, TimeUnit.SECONDS);
            schedules.start(timers).toCompletableFuture().get(3, TimeUnit.SECONDS);
            assertFalse(old.getLivable().isAlive());
            old.getTaskConfig().getCallable().call();
            verify(store, times(1)).due(any(), anyInt());
            verify(timers, times(2)).register(any());
        }
    }
}
