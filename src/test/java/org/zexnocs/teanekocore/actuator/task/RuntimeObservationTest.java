package org.zexnocs.teanekocore.actuator.task;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.zexnocs.teanekocore.actuator.runtime.RuntimeMonitor;
import org.zexnocs.teanekocore.actuator.runtime.RuntimeSettings;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskResult;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskService;
import org.zexnocs.teanekocore.actuator.timer.*;
import org.zexnocs.teanekocore.logger.ILogger;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 复用真实执行器、计时器和完成阶段验证观测边界，不启动应用或外部服务。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.7.0-l
 */
@Timeout(10)
class RuntimeObservationTest {
    /** 原方法返回空值后继续等待真实外部结果，不能因同步阶段结束而显示成功。 */
    @Test
    void synchronousExecutionStillWaitsForExternalResult() {
        var monitor = new RuntimeMonitor(Clock.systemUTC(), () -> new RuntimeSettings().snapshot());
        var id = UUID.randomUUID();
        var result = new CompletableFuture<ITaskResult<Void>>();
        var logger = mock(ILogger.class);
        var future = new TaskFuture<>(logger, "外部结果", result);
        var config = TaskConfig.<Void>builder().name("外部结果").taskStages(List.of()).callable(() -> null).build();
        var task = new Task<>(id, config, future, Void.class);
        monitor.observe(task);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var service = new TaskExecuteService(executor, logger, mock(TaskStageScanner.class), mock(ITaskService.class));
            service.installMonitor(monitor);
            service.__executeTask(task);
            Objects.requireNonNull(task.getExecutingFuture()).join();
            assertEquals("WAITING_RESULT", monitor.detail(id).state());
            assertNull(monitor.detail(id).finishedAt());
            result.complete(EmptyTaskResult.INSTANCE);
            assertEquals("SUCCESS", monitor.detail(id).state());
            assertNotNull(monitor.detail(id).finishedAt());
        }
    }

    /** 暂停与恢复控制真实后续触发，回执幂等、版本冲突、撤权和来源暂停保持独立。 */
    @Test
    void timerGateControlsActualTriggersWithoutOverridingSourcePause() {
        var tasks = mock(ITaskService.class);
        var result = new TaskFuture<ITaskResult<Void>>(mock(ILogger.class), "计时任务", CompletableFuture.completedFuture(EmptyTaskResult.INSTANCE));
        var config = TimerTaskConfig.<Void>builder().taskConfig(TaskConfig.<Void>builder()
                .name("计时任务").callable(() -> null).build()).build();
        when(tasks.subscribeWithFuture(config.getTaskConfig(), Void.class)).thenReturn(result);
        var timer = new FixedRateTimer<>(config, Duration.ofSeconds(1), Void.class);
        timer.update(Instant.EPOCH);
        var directory = new TimerDirectory();
        var service = new TimerService(tasks);
        service.installDirectory(directory);
        service.register(timer);
        var view = directory.page("", 10).items().getFirst();
        var operation = new TimerDirectory.Change(UUID.randomUUID(), view.revision(), true);
        var receipt = directory.change(view.id(), operation, () -> {});
        service.timerLoop();
        verifyNoInteractions(tasks);
        assertEquals(receipt, directory.change(view.id(), operation, () -> {}));
        assertThrows(ConcurrentModificationException.class, () -> directory.change(view.id(),
                new TimerDirectory.Change(UUID.randomUUID(), 1, false), () -> {}));
        assertThrows(SecurityException.class, () -> directory.change(view.id(),
                new TimerDirectory.Change(UUID.randomUUID(), receipt.revision(), false), () -> { throw new SecurityException("撤权"); }));
        var paused = new AtomicBoolean(true);
        var sourcePause = mock(org.zexnocs.teanekocore.framework.lifescycle.IPausable.class);
        when(sourcePause.isPaused()).thenAnswer(ignored -> paused.get());
        config.setPausable(sourcePause);
        directory.change(view.id(), new TimerDirectory.Change(UUID.randomUUID(), receipt.revision(), false), () -> {});
        service.timerLoop();
        verifyNoInteractions(tasks);
        paused.set(false);
        service.timerLoop();
        verify(tasks).subscribeWithFuture(config.getTaskConfig(), Void.class);
        var delay = new FixedDelayTimer<>(config, Duration.ofSeconds(3), Void.class);
        delay.update(Instant.now());
        assertTrue(delay.observation().waiting());
        assertNull(delay.observation().nextFire());
    }

    /** 正文默认不收集；筛选使用扫描游标，过期与重启都必须报告真实窗口边界。 */
    @Test
    void boundedLogsRetainNoDefaultSecretsAndAdvancePastFilteredEntries() {
        var now = new AtomicReference<>(Instant.parse("2026-11-03T00:00:00Z"));
        var clock = mock(Clock.class);
        when(clock.instant()).thenAnswer(ignored -> now.get());
        var settings = new RuntimeSettings();
        var monitor = new RuntimeMonitor(clock, settings::snapshot);
        monitor.log("INFO", "测试来源", "不应收集的秘密原值", "", "", "");
        var first = monitor.logs(logs("", 0, "INFO"));
        assertEquals("正文采集未开启", first.items().getFirst().message());
        settings.setCaptureMessages(true);
        monitor.log("ERROR", "测试来源", "Authorization: Bearer private-value", "", "", "");
        monitor.log("INFO", "其他来源", "普通事件", "", "", "");
        var filtered = monitor.logs(logs(first.instance().toString(), 1, "WARN"));
        assertTrue(filtered.items().isEmpty());
        assertEquals(3, filtered.next());
        assertFalse(monitor.logs(logs("", 0, "ERROR")).items().getFirst().message().contains("private-value"));
        now.set(now.get().plusSeconds(3601));
        var expired = monitor.logs(logs(first.instance().toString(), 1, ""));
        assertTrue(expired.gap());
        assertTrue(expired.items().isEmpty());
        assertTrue(monitor.logs(logs(UUID.randomUUID().toString(), 999, "")).reset());
    }

    /**
     * @param instance 原窗口标识
     * @param after 原扫描游标
     * @param level 等级过滤
     * @return 固定有界查询
     */
    private RuntimeMonitor.LogQuery logs(String instance, long after, String level) {
        return new RuntimeMonitor.LogQuery(instance, after, 100, level, "", "", "", "", "", null, null);
    }
}
