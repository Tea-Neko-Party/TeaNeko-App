package org.zexnocs.teanekoplugin.contract.task;

// 2026/11/07：插件归属夹具遵循与生产一致的业务包边界。
import org.zexnocs.teanekocore.actuator.task.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.zexnocs.teanekocore.actuator.runtime.RuntimeMonitor;
import org.zexnocs.teanekocore.actuator.runtime.RuntimeSettings;
import org.zexnocs.teanekocore.actuator.task.api.ITaskStage;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITask;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskExecuteService;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskService;
import org.zexnocs.teanekocore.actuator.timer.FixedRateTimer;
import org.zexnocs.teanekocore.actuator.timer.TimerDirectory;
import org.zexnocs.teanekocore.actuator.timer.TimerService;
import org.zexnocs.teanekocore.actuator.timer.TimerTaskConfig;
import org.zexnocs.teanekocore.cache.interfaces.ICacheService;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekocore.plugin.PluginDescriptor;
import org.zexnocs.teanekocore.plugin.PluginAccess;
import org.zexnocs.teanekocore.plugin.PluginOwned;
import org.zexnocs.teanekocore.plugin.PluginRegistry;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 真实定时注册、执行和外部完成的插件代次回归。
 * <br>2026/11/07：覆盖手工注册归属、停用隐藏、旧代排队和外部物理占用。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
@Timeout(10)
class PluginTimerGateTest {
    /** 手工计时器解析一次归属，停用后目录、运行与移除后的回执均不可见。 */
    @Test
    void manualTimerAndRetainedReceiptsRespectCurrentPluginState() {
        var plugins = registry();
        var bindings = new TaskPluginBindings(plugins);
        var directory = new TimerDirectory();
        directory.installPluginBindings(bindings);
        var tasks = mock(ITaskService.class);
        var service = new TimerService(tasks);
        service.installPluginBindings(bindings);
        service.installDirectory(directory);
        var jobs = new Jobs();
        var config = TimerTaskConfig.<Void>builder().taskConfig(TaskConfig.<Void>builder()
                .name("插件定时器").taskStages(List.of()).callable(jobs::run).build()).build();
        var timer = new FixedRateTimer<>(config, Duration.ofSeconds(1), Void.class);
        timer.update(Instant.EPOCH);
        service.register(timer);
        var item = directory.page("", 10, "", "", "", "", "test.timer").items().getFirst();
        assertEquals("test.timer", item.pluginId());
        var operation = new TimerDirectory.Change(UUID.randomUUID(), item.revision(), true);
        directory.change(item.id(), operation, () -> {});
        directory.remove(timer);
        var monitor = new RuntimeMonitor(Clock.systemUTC(), () -> new RuntimeSettings().snapshot());
        monitor.installPluginBindings(bindings);
        var run = UUID.randomUUID();
        monitor.begin(run, "插件任务", "test.timer");
        monitor.log("INFO", "测试", "正文", "", "", "test.timer");
        plugins.setEnabled("test.timer", false);
        service.timerLoop();
        verifyNoInteractions(tasks);
        assertTrue(directory.page("", 10).items().isEmpty());
        assertTrue(monitor.runs("", 0, 10, "", "").items().isEmpty());
        assertThrows(NoSuchElementException.class, () -> monitor.detail(run));
        assertThrows(NoSuchElementException.class, () -> directory.operation(item.id(), operation.operationId()));
        assertTrue(monitor.logs(new RuntimeMonitor.LogQuery("", 0, 10, "", "", "", "", "", "", null, null))
                .items().isEmpty());
    }

    /**
     * 已经排队的任务在停用再启用后保持失效，不重新执行业务。
     * @throws Exception 有界等待或执行失败时
     */
    @Test
    void queuedTaskCannotAcquireNewGenerationAfterReenable() throws Exception {
        try (var fixture = new Fixture()) {
            var release = new CompletableFuture<Void>();
            var blocker = fixture.workers.submit(release::join);
            var calls = new AtomicInteger();
            var config = TaskConfig.<Void>builder().name("旧代排队").taskStages(List.of()).callable(() -> {
                calls.incrementAndGet();
                return EmptyTaskResult.INSTANCE;
            }).build();
            fixture.bindings.bind(config, "test.timer");
            try {
                var future = fixture.tasks.subscribeWithFuture(config, Void.class);
                fixture.plugins.setEnabled("test.timer", false);
                fixture.plugins.setEnabled("test.timer", true);
                release.complete(null);
                assertThrows(ExecutionException.class, () -> future.asStage().toCompletableFuture().get(3, TimeUnit.SECONDS));
                future.physicalCompletion().toCompletableFuture().get(3, TimeUnit.SECONDS);
                assertEquals(0, calls.get());
                assertEquals(0, fixture.tasks.inFlightCount());
            } finally {
                release.complete(null);
                blocker.get(3, TimeUnit.SECONDS);
            }
        }
    }

    /**
     * 2026/11/07：外部工作失效后保持任务容量和插件占用，取消观察也不能替代真实回执。
     * @throws Exception 有界等待或执行失败时
     */
    @Test
    void invalidatedExternalWorkRetainsPhysicalOwnershipUntilCallback() throws Exception {
        try (var fixture = new Fixture()) {
            var calls = new AtomicInteger();
            var config = TaskConfig.<Void>builder().name("外部插件工作").taskStages(List.of()).maxRetries(4)
                    .callable(() -> {
                        calls.incrementAndGet();
                        fixture.plugins.setEnabled("test.timer", false);
                        return null;
                    }).build();
            fixture.bindings.bind(config, "test.timer");
            var id = UUID.randomUUID();
            var future = fixture.tasks.subscribeWithFuture(id, config, Void.class);
            assertThrows(ExecutionException.class, () -> future.asStage().toCompletableFuture().get(3, TimeUnit.SECONDS));
            fixture.submitted.get().getExecutingFuture().get(3, TimeUnit.SECONDS);
            var drained = fixture.plugins.drain("test.timer").toCompletableFuture();
            try {
                assertFalse(future.physicalCompletion().toCompletableFuture().isDone());
                assertFalse(drained.isDone());
                fixture.plugins.drain("test.timer").toCompletableFuture().cancel(false);
                assertFalse(fixture.plugins.drain("test.timer").toCompletableFuture().isDone());
                assertEquals(1, fixture.tasks.inFlightCount());
                assertEquals(1, calls.get());
            } finally {
                fixture.tasks.complete(id, EmptyTaskResult.INSTANCE);
                future.physicalCompletion().toCompletableFuture().get(3, TimeUnit.SECONDS);
                drained.get(3, TimeUnit.SECONDS);
            }
            assertEquals(0, fixture.tasks.inFlightCount());
        }
    }

    /**
     * 2026/11/07：已冻结的手工阶段每次进入仍复核插件状态，重新启用恢复后续新任务。
     * @throws Exception 有界等待或执行失败时
     */
    @Test
    void cachedManualStageStillChecksCurrentPluginState() throws Exception {
        try (var fixture = new Fixture()) {
            var stageCalls = new AtomicInteger();
            var callableCalls = new AtomicInteger();
            var config = TaskConfig.<Void>builder().name("手工插件阶段")
                    .taskStages(List.of(new OwnedStage(stageCalls))).callable(() -> {
                        callableCalls.incrementAndGet();
                        return EmptyTaskResult.INSTANCE;
                    }).build();
            fixture.plugins.setEnabled("test.timer", false);
            var disabled = fixture.tasks.subscribeWithFuture(config, Void.class);
            disabled.asStage().toCompletableFuture().get(3, TimeUnit.SECONDS);
            disabled.physicalCompletion().toCompletableFuture().get(3, TimeUnit.SECONDS);
            assertEquals(0, stageCalls.get());
            assertEquals(1, callableCalls.get());
            fixture.plugins.setEnabled("test.timer", true);
            var enabled = fixture.tasks.subscribeWithFuture(config, Void.class);
            enabled.asStage().toCompletableFuture().get(3, TimeUnit.SECONDS);
            enabled.physicalCompletion().toCompletableFuture().get(3, TimeUnit.SECONDS);
            assertEquals(1, stageCalls.get());
            assertEquals(1, callableCalls.get());
        }
    }

    /**
     * 插件阶段附着在宿主任务时仍等待其外部回执，不以阶段方法返回代替实际工作完成。
     * 2026/11/07：清理路径统一提交唯一外部回执，避免成功后再次完成已移除任务。
     * @throws Exception 当有界任务或排空等待失败时
     */
    @Test
    void pluginStageOnHostTaskRetainsCallUntilExternalCompletion() throws Exception {
        try (var fixture = new Fixture()) {
            var entered = new CompletableFuture<Void>();
            var config = TaskConfig.<Void>builder().name("宿主任务中的插件阶段")
                    .taskStages(List.of(new ExternalOwnedStage(entered)))
                    .callable(() -> EmptyTaskResult.INSTANCE).build();
            var id = UUID.randomUUID();
            var future = fixture.tasks.subscribeWithFuture(id, config, Void.class);
            CompletableFuture<Void> drain;
            try {
                entered.get(3, TimeUnit.SECONDS);
                fixture.submitted.get().getExecutingFuture().get(3, TimeUnit.SECONDS);
                fixture.plugins.setEnabled("test.timer", false);
                drain = fixture.plugins.drain("test.timer").toCompletableFuture();
                assertFalse(drain.isDone());
            } finally {
                fixture.tasks.complete(id, EmptyTaskResult.INSTANCE);
            }
            drain.get(3, TimeUnit.SECONDS);
            future.physicalCompletion().toCompletableFuture().get(3, TimeUnit.SECONDS);
        }
    }

    /** @return 只包含测试插件的真实注册表。 */
    private static PluginRegistry registry() {
        return new PluginRegistry(List.of(() -> new PluginDescriptor("test.timer", "4.9.0-l", 1, Set.of())));
    }

    /**
     * 带明确插件归属的手工计时器回调。
     * @author zExNocs
     * @date 2026/11/07
     * @since 4.9.0-l
     * @version 4.9.0-l
     */
    @PluginOwned("test.timer")
    static final class Jobs {
        /** @return 无外部副作用的空结果。 */
        EmptyTaskResult run() {
            return EmptyTaskResult.INSTANCE;
        }
    }

    /**
     * 可观测进入次数的插件阶段，直接结束阶段链以区分跳过与执行。
     * @param calls 实际进入次数
     * @author zExNocs
     * @date 2026/11/07
     * @since 4.9.0-l
     * @version 4.9.0-l
     */
    @PluginOwned("test.timer")
    private record OwnedStage(AtomicInteger calls) implements ITaskStage {
        /**
         * 只记录当前阶段进入，不展开后续任务。
         * @param chain 原同步阶段链
         * @return 空结果
         */
        @Override
        public EmptyTaskResult process(TaskStageChain chain) {
            calls.incrementAndGet();
            return EmptyTaskResult.INSTANCE;
        }
    }

    /**
     * 为无插件归属的宿主任务声明外部工作，验证阶段自己的插件占用。
     * @param entered 实际阶段进入信号
     * @author zExNocs
     * @date 2026/11/07
     * @since 4.9.0-l
     * @version 4.9.0-l
     */
    @PluginOwned("test.timer")
    private record ExternalOwnedStage(CompletableFuture<Void> entered) implements ITaskStage {
        /**
         * @param chain 原单线程阶段链
         * @return 空值表示等待已启动的外部工作回执
         */
        @Override
        public org.zexnocs.teanekocore.actuator.task.interfaces.@org.jspecify.annotations.Nullable ITaskResult<?> process(
                TaskStageChain chain) {
            entered.complete(null);
            return null;
        }
    }

    /**
     * 借用正式任务执行和重试逻辑，测试单独拥有工作线程。
     * @author zExNocs
     * @date 2026/11/07
     * @since 4.9.0-l
     * @version 4.9.0-l
     */
    private static final class Fixture implements AutoCloseable {
        final PluginRegistry plugins = registry();
        final TaskPluginBindings bindings = new TaskPluginBindings(plugins);
        final ExecutorService workers = Executors.newSingleThreadExecutor();
        final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        final AtomicReference<ITask<?>> submitted = new AtomicReference<>();
        final TaskService tasks;

        /** 装配真实任务服务，只替换循环依赖桥接及无关缓存维护。 */
        Fixture() {
            var executor = mock(ITaskExecuteService.class);
            var logger = mock(ILogger.class);
            tasks = new TaskService(mock(ICacheService.class), logger, executor, new TaskRetryService(executor));
            tasks.installPluginBindings(bindings);
            var actual = new TaskExecuteService(workers, logger, mock(TaskStageScanner.class), tasks, scheduler);
            actual.installPluginBindings(bindings);
            // 2026/11/07：通过正式阶段门禁验证配置创建时冻结的插件归属。
            actual.installPluginAccess(new PluginAccess(plugins));
            doAnswer(call -> {
                submitted.set(call.getArgument(0));
                actual.__executeTask(call.getArgument(0));
                return null;
            }).when(executor).__executeTask(any());
            doAnswer(call -> {
                actual.__executeTaskRetry(call.getArgument(0));
                return null;
            }).when(executor).__executeTaskRetry(any());
        }

        /** 停止测试任务入口并释放自有执行器。 */
        @Override
        public void close() {
            tasks.close();
            scheduler.shutdownNow();
            workers.close();
        }
    }
}
