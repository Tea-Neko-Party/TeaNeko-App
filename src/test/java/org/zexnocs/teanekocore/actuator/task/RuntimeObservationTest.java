package org.zexnocs.teanekocore.actuator.task;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.zexnocs.teanekocore.actuator.runtime.RuntimeMonitor;
import org.zexnocs.teanekocore.actuator.runtime.RuntimeSettings;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskResult;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskService;
import org.zexnocs.teanekocore.actuator.timer.*;
import org.zexnocs.teanekocore.actuator.timer.interfaces.ITimerViewSource;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IBeanScanner;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
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
 * <br>2026/11/04：验证方法声明、分页前分类、只读门禁与实际 Spring 注册任务关联。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.7.1-l
 */
@Timeout(10)
class RuntimeObservationTest {
    /** 分类在分页前生效；重载按准确方法解析，只读和隐藏都不能改变原任务执行。 */
    @Test
    void timerCategoriesFilterBeforePagingAndEnforceDeclaredControl() {
        var directory = new TimerDirectory();
        var jobs = new CategorizedJobs();
        var config = TimerTaskConfig.<Void>builder().taskConfig(TaskConfig.<Void>builder()
                .name("原任务名").callable(jobs::work).build()).build();
        for (int index = 0; index < 2; index++) {
            directory.register(new FixedRateTimer<>(config, Duration.ofSeconds(1), Void.class));
        }
        var other = TimerTaskConfig.<Void>builder().taskConfig(TaskConfig.<Void>builder()
                .name("旧插件任务").callable(() -> EmptyTaskResult.INSTANCE).build()).build();
        directory.register(new FixedRateTimer<>(other, Duration.ofSeconds(1), Void.class));
        var first = directory.page("", 1, "agent.memory", "FIXED_RATE", "ACTIVE", "测试");
        assertEquals(1, first.items().size());
        assertTrue(first.more());
        assertEquals(2, first.categories().size());
        var second = directory.page(first.next(), 1, "agent.memory", "FIXED_RATE", "ACTIVE", "测试");
        assertEquals(1, second.items().size());
        assertNotEquals(first.items().getFirst().id(), second.items().getFirst().id());
        assertFalse(second.more());
        var item = first.items().getFirst();
        assertTrue(item.actions().isEmpty());
        assertThrows(IllegalStateException.class, () -> directory.change(item.id(),
                new TimerDirectory.Change(UUID.randomUUID(), item.revision(), true), () -> {}));
        var hidden = new FixedRateTimer<>(TimerTaskConfig.<Void>builder().taskConfig(config.getTaskConfig())
                .presentation(new TimerPresentation("agent.memory", "Agent 记忆", "隐藏的维护", "", false, false))
                .build(), Duration.ofSeconds(1), Void.class);
        directory.register(hidden);
        assertEquals(3, directory.page("", 10).items().size());
        var executed = new AtomicBoolean();
        directory.visit(hidden, () -> executed.set(true));
        assertTrue(executed.get());
        var conflict = new FixedRateTimer<>(TimerTaskConfig.<Void>builder().taskConfig(config.getTaskConfig())
                .presentation(new TimerPresentation("agent.memory", "其他名称", "", "", true, true)).build(),
                Duration.ofSeconds(1), Void.class);
        assertThrows(IllegalArgumentException.class, () -> directory.register(conflict));
        var mapper = new org.zexnocs.teanekocore._app_config.JacksonConfig().objectMapper();
        var query = mapper.readValue("""
                {"action":"TIMERS","limit":1,"state":"ACTIVE","category":"agent.memory","kind":"FIXED_RATE"}
                """, org.zexnocs.teanekocore.actuator.runtime.RuntimeManagement.Query.class);
        assertEquals("agent.memory", query.category());
        assertEquals("FIXED_RATE", query.kind());
        assertEquals("", mapper.readValue("{\"action\":\"TIMERS\",\"limit\":1}",
                org.zexnocs.teanekocore.actuator.runtime.RuntimeManagement.Query.class).category());
    }

    /** 读取实际 Spring 注解处理器持有的任务；原包装、方法关联、隐藏、身份和只读均使用真实对象。 */
    @Test
    void springScheduledTasksRemainReadOnlyAndRetainActualMethodMetadata() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(ScheduledConfiguration.class);
            context.registerBean(CategorizedJobs.class);
            context.refresh();
            var scanner = mock(IBeanScanner.class);
            when(scanner.getBeansOfType(ScheduledTaskHolder.class)).thenReturn(context.getBeansOfType(ScheduledTaskHolder.class));
            var source = new SpringTimerViewSource(scanner);
            when(scanner.getBeansOfType(ITimerViewSource.class)).thenReturn(Map.of("spring", source));
            var directory = new TimerDirectory();
            directory.installSources(scanner);
            var page = directory.page("", 10, "agent.memory", "FIXED_DELAY", "", "测试");
            assertEquals(1, page.items().size());
            var item = page.items().getFirst();
            assertEquals("测试记忆维护", item.name());
            assertEquals("spring", item.source());
            assertEquals("ACTIVE", item.state());
            assertNotNull(item.rule().nextFire());
            assertTrue(item.actions().isEmpty());
            assertEquals(item.id(), directory.page("", 10).items().getFirst().id());
            assertThrows(java.util.NoSuchElementException.class, () -> directory.change(item.id(),
                    new TimerDirectory.Change(UUID.randomUUID(), 1, true), () -> {}));
        }
    }

    /**
     * 真实 Spring 调度装配，只在测试上下文存活期间持有线程。
     * @author zExNocs
     * @date 2026/11/04
     * @since 4.7.1-l
     * @version 4.7.1-l
     */
    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    static class ScheduledConfiguration {}

    /**
     * 同时作为方法引用和实际注解调度来源，首轮延迟避免测试期间触发业务。
     * @author zExNocs
     * @date 2026/11/04
     * @since 4.7.1-l
     * @version 4.7.1-l
     */
    @TimerDisplay(category = "agent.memory", categoryName = "Agent 记忆", name = "测试记忆维护", controllable = false)
    static class CategorizedJobs {
        /** @return 无外部副作用的原任务结果 */
        @TimerDisplay(category = "agent.memory", categoryName = "Agent 记忆", name = "测试记忆维护", controllable = false)
        public EmptyTaskResult work() {
            return EmptyTaskResult.INSTANCE;
        }

        /** @param value 未使用的重载参数 */
        @TimerDisplay(category = "overload", categoryName = "其他重载")
        public void work(String value) {}

        /** 仅验证原 Spring 定时来源的元数据关联。 */
        @Scheduled(fixedDelay = 60000, initialDelay = 60000)
        public void scheduled() {}

        /** 隐藏只作用于管理目录，不删除原调度注册。 */
        @Scheduled(fixedDelay = 60000, initialDelay = 60000)
        @TimerDisplay(category = "agent.memory", categoryName = "Agent 记忆", visible = false)
        public void hidden() {}
    }

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
