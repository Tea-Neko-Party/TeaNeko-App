package org.zexnocs.teanekoagent.component.memory.namespace;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.zexnocs.teanekoagent.component.schedule.AgentScheduleService;
import org.zexnocs.teanekoagent.component.schedule.AgentScheduledTask;
import org.zexnocs.teanekoagent.component.schedule.EasyDataScheduleStore;
import org.zexnocs.teanekoagent.component.schedule.TaskPresentation;
import org.zexnocs.teanekoagent.runtime.AgentActorContext;
import org.zexnocs.teanekoagent.runtime.AgentScope;
import org.zexnocs.teanekocore.actuator.timer.interfaces.ITimerService;
import org.zexnocs.teanekocore.actuator.timer.interfaces.ITimerTaskConfig;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.zexnocs.teanekoagent.component.schedule.AgentScheduledTask.Status.*;

/**
 * 复用真实 EasyData 事务夹具，验证调度幂等、取消竞争和重启不补跑。
 * <br>2026/11/03：覆盖独立管理取消的权限、版本、回执及撤权事务回滚。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/05：补充组件审查修复的离线回归及初始化夹具。
 * <br>2026/11/06：验证四槽饱和时保留后续到期任务，释放后继续调度。
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.5.0-l
 * @version 4.8.3-l
 */
@Timeout(15)
class AgentScheduleIntegrationTest {
    /** 2026/11/03：不伪造原生来源，管理取消与原计划状态同事务提交并保留原回执。 */
    @Test
    void managementCancelIsScopedVersionedIdempotentAndRollsBackRevocation() {
        try (var db = new NamespaceDatabaseFixture()) {
            var store = new EasyDataScheduleStore(db.data, db.mapper);
            var task = new AgentScheduledTask(UUID.randomUUID(), spec("管理取消", NOW.plusSeconds(60), 0),
                    NOW.plusSeconds(60), ACTIVE, 1, "等待");
            store.create(task).toCompletableFuture().join();
            var query = new org.zexnocs.teanekoagent.component.schedule.ScheduleManagement.Query("CANCEL", "", task.id(), 1, UUID.randomUUID());
            var checks = new AtomicInteger();
            assertThrows(CompletionException.class, () -> store.manage(ACTOR.scope(), "web.local-admin", query, () -> {
                if (checks.incrementAndGet() == 2) throw new SecurityException("测试事务内撤权。");
            }).toCompletableFuture().join());
            assertEquals(ACTIVE, store.find(task.id()).toCompletableFuture().join().orElseThrow().status());
            assertThrows(CompletionException.class, () -> store.manage(new AgentScope("other", "scope"), "web.local-admin", query, () -> {}).toCompletableFuture().join());
            var receipt = store.manage(ACTOR.scope(), "web.local-admin", query, () -> {}).toCompletableFuture().join();
            assertEquals("APPLIED", receipt.status());
            assertEquals(CANCELLED, store.find(task.id()).toCompletableFuture().join().orElseThrow().status());
            assertEquals(receipt, store.manage(ACTOR.scope(), "web.local-admin", query, () -> {}).toCompletableFuture().join());
            var lookup = new org.zexnocs.teanekoagent.component.schedule.ScheduleManagement.Query("OPERATION", "", task.id(), 1, query.operationId());
            assertEquals(receipt, store.manage(ACTOR.scope(), "web.local-admin", lookup, () -> {}).toCompletableFuture().join());
            assertThrows(CompletionException.class, () -> store.manage(ACTOR.scope(), "another-admin", lookup, () -> {}).toCompletableFuture().join());
        }
    }
    private static final Instant NOW = Instant.parse("2026-11-01T00:00:00Z");
    private static final AgentActorContext ACTOR = new AgentActorContext(new AgentScope("client", "scope"),
            AgentActorContext.Origin.USER, Optional.of(UUID.randomUUID()), "native-user", "message", "target");

    /** 创建先提交，重放不重复，两个扫描器对同一发生只能有一个实际执行者。 */
    @Test void committedCreationAndConcurrentClaimsExecuteOnlyOnce() {
        try (var db = new NamespaceDatabaseFixture()) {
            var store = new EasyDataScheduleStore(db.data, db.mapper); var clock = new AtomicReference<>(NOW);
            var calls = new AtomicInteger(); var release = new CompletableFuture<AgentScheduleService.Outcome>();
            AgentScheduleService.Action action = (task, guard) -> guard.get().thenCompose(ignored -> { calls.incrementAndGet(); return release; });
            try (var first = service(store, clock, action); var second = service(store, clock, action)) {
                var id = UUID.randomUUID(); var spec = spec("提醒", NOW.plusSeconds(10), 0);
                var created = first.create(id, spec).toCompletableFuture().join();
                assertEquals(created, store.find(id).toCompletableFuture().join().orElseThrow());
                assertEquals(created, first.create(id, spec).toCompletableFuture().join());
                assertThrows(CompletionException.class, () -> first.create(id, spec("其他", spec.firstFire(), 0)).toCompletableFuture().join());
                var stranger = new AgentActorContext(new AgentScope("other", "scope"), AgentActorContext.Origin.USER,
                        ACTOR.userId(), "native-user", "message", "target");
                assertThrows(CompletionException.class, () -> first.detail(id, stranger).toCompletableFuture().join());
                clock.set(spec.firstFire());
                var a = first.tick().toCompletableFuture(); var b = second.tick().toCompletableFuture();
                CompletableFuture.anyOf(a, b).join();
                release.complete(AgentScheduleService.Outcome.COMPLETED); CompletableFuture.allOf(a, b).join();
                assertEquals(1, calls.get()); assertEquals(COMPLETED, first.detail(id, ACTOR).toCompletableFuture().join().status());
                assertTrue(TaskPresentation.list(first.list(ACTOR, "").toCompletableFuture().join()).contains("Asia/Shanghai"));
            }
        }
    }

    /** 取消和策略撤销使尚未开始的真实副作用失效，迟到完成不能覆盖取消状态。 */
    @Test void cancellationAndRevocationFenceDelayedActions() {
        try (var db = new NamespaceDatabaseFixture()) {
            var store = new EasyDataScheduleStore(db.data, db.mapper); var clock = new AtomicReference<>(NOW);
            var captured = new CompletableFuture<Supplier<CompletionStage<Void>>>();
            var completed = new CompletableFuture<AgentScheduleService.Outcome>(); var writes = new AtomicInteger();
            AgentScheduleService.Action action = (task, guard) -> { captured.complete(guard); return completed; };
            try (var service = service(store, clock, action)) {
                var id = UUID.randomUUID(); service.create(id, spec("取消竞争", NOW.plusSeconds(10), 0)).toCompletableFuture().join();
                clock.set(NOW.plusSeconds(10)); var tick = service.tick().toCompletableFuture();
                var guard = captured.join(); service.cancel(id, ACTOR).toCompletableFuture().join();
                assertThrows(CompletionException.class, () -> guard.get().thenRun(writes::incrementAndGet).toCompletableFuture().join());
                completed.complete(AgentScheduleService.Outcome.COMPLETED); tick.join();
                assertEquals(0, writes.get()); assertEquals(CANCELLED, service.detail(id, ACTOR).toCompletableFuture().join().status());
                assertEquals(CANCELLED, service.create(id, spec("取消竞争", NOW.plusSeconds(10), 0)).toCompletableFuture().join().status());
            }
            try (var denied = new AgentScheduleService(store, Clock.fixed(NOW.plusSeconds(20), ZoneOffset.UTC),
                    (task, op, actor) -> CompletableFuture.failedFuture(new IllegalStateException("当前作用域已禁用。")), Map.of("remind", action))) {
                var task = new AgentScheduledTask(UUID.randomUUID(), spec("禁用", NOW.plusSeconds(20), 0), NOW.plusSeconds(20), ACTIVE, 1, "等待");
                store.create(task).toCompletableFuture().join(); denied.tick().toCompletableFuture().join();
                assertEquals(PAUSED, store.find(task.id()).toCompletableFuture().join().orElseThrow().status());
            }
        }
    }

    /** 重启不补跑单次或不明动作，重复任务跳到未来，时区不依赖机器默认值。 */
    @Test @SuppressWarnings("unchecked") void restartSkipsMissedAndUncertainOccurrences() {
        try (var db = new NamespaceDatabaseFixture()) {
            var store = new EasyDataScheduleStore(db.data, db.mapper); var clock = new AtomicReference<>(NOW.plusSeconds(130));
            var once = new AgentScheduledTask(UUID.randomUUID(), spec("单次", NOW, 0), NOW, ACTIVE, 1, "等待");
            var repeat = new AgentScheduledTask(UUID.randomUUID(), spec("间隔", NOW, 60), NOW, ACTIVE, 1, "等待");
            var uncertain = new AgentScheduledTask(UUID.randomUUID(), spec("未完成", NOW, 0), NOW, RUNNING, 2, "执行中");
            for (var task : List.of(once, repeat, uncertain)) { store.create(task).toCompletableFuture().join(); }
            var timers = mock(ITimerService.class); ITimerTaskConfig<Void> config = mock(ITimerTaskConfig.class);
            when(timers.registerByDelay(anyString(), anyString(), any(), any(), eq(Void.class))).thenReturn(config);
            var calls = new AtomicInteger();
            try (var service = service(store, clock, (task, guard) -> { calls.incrementAndGet(); return CompletableFuture.completedFuture(AgentScheduleService.Outcome.COMPLETED); })) {
                service.start(timers).toCompletableFuture().join(); service.tick().toCompletableFuture().join();
                assertEquals(EXPIRED, store.find(once.id()).toCompletableFuture().join().orElseThrow().status());
                assertEquals(INDETERMINATE, store.find(uncertain.id()).toCompletableFuture().join().orElseThrow().status());
                assertEquals(NOW.plusSeconds(180), store.find(repeat.id()).toCompletableFuture().join().orElseThrow().nextFire());
                assertEquals(0, calls.get());
                clock.set(NOW.plusSeconds(180)); service.tick().toCompletableFuture().join();
                assertEquals(1, calls.get());
                assertEquals(NOW.plusSeconds(240), store.find(repeat.id()).toCompletableFuture().join().orElseThrow().nextFire());
            }
            verify(config).setLivable(any());
        }
    }

    /**
     * 2026/11/05：大量历史不占到期页，慢动作只占一个槽位，其余空槽仍能处理下一批。
     * @throws Exception 当有界执行等待失败时
     */
    @Test
    void dueIndexAndAvailableSlotsAvoidHistoryAndSlowActionBlocking() throws Exception {
        try (var db = new NamespaceDatabaseFixture()) {
            var store = new EasyDataScheduleStore(db.data, db.mapper);
            for (int index = 0; index < 105; index++) {
                var old = new AgentScheduledTask(UUID.randomUUID(), spec("历史" + index, NOW, 0), NOW, COMPLETED, 2, "完成");
                store.create(old).toCompletableFuture().join();
            }
            var clock = new AtomicReference<>(NOW);
            var tasks = new java.util.ArrayList<AgentScheduledTask>();
            for (int index = 0; index < 6; index++) {
                var at = NOW.plusSeconds(index);
                var task = new AgentScheduledTask(UUID.randomUUID(), spec("到期" + index, at, 0), at, ACTIVE, 1, "等待");
                tasks.add(task);
            }
            store.create(tasks.getFirst()).toCompletableFuture().join();
            clock.set(NOW.plusSeconds(10));
            var slow = new CompletableFuture<AgentScheduleService.Outcome>();
            var entered = new CompletableFuture<Void>();
            var calls = new AtomicInteger();
            try (var service = service(store, clock, (task, guard) -> {
                calls.incrementAndGet();
                if (task.id().equals(tasks.getFirst().id())) {
                    entered.complete(null);
                    return slow;
                }
                return CompletableFuture.completedStage(AgentScheduleService.Outcome.COMPLETED);
            })) {
                var first = service.tick().toCompletableFuture();
                try {
                    entered.get(3, java.util.concurrent.TimeUnit.SECONDS);
                    // 2026/11/05：慢动作已开始后加入新任务，按每批真实完成回执等待容量释放。
                    for (var task : tasks.subList(1, tasks.size())) {
                        store.create(task).toCompletableFuture().get(3, java.util.concurrent.TimeUnit.SECONDS);
                    }
                    service.tick().toCompletableFuture().get(3, java.util.concurrent.TimeUnit.SECONDS);
                    service.tick().toCompletableFuture().get(3, java.util.concurrent.TimeUnit.SECONDS);
                    assertFalse(first.isDone());
                    assertEquals(6, calls.get());
                    assertEquals(COMPLETED, store.find(tasks.getLast().id()).toCompletableFuture().join().orElseThrow().status());
                } finally {
                    slow.complete(AgentScheduleService.Outcome.COMPLETED);
                }
                first.get(3, java.util.concurrent.TimeUnit.SECONDS);
            }
            assertTrue(store.due(clock.get(), 4).toCompletableFuture().join().isEmpty());
        }
    }

    /** 2026/11/05：发现入口同步抛错后释放互斥，下一次显式扫描仍可处理任务。 */
    @Test
    void synchronousDiscoveryFailureDoesNotPermanentlyBlockScanning() {
        try (var db = new NamespaceDatabaseFixture()) {
            var store = spy(new EasyDataScheduleStore(db.data, db.mapper));
            var task = new AgentScheduledTask(UUID.randomUUID(), spec("发现恢复", NOW, 0), NOW, ACTIVE, 1, "等待");
            store.create(task).toCompletableFuture().join();
            doThrow(new IllegalStateException("测试发现入口故障。"))
                    .doCallRealMethod().when(store).due(any(), anyInt());
            var calls = new AtomicInteger();
            try (var service = service(store, new AtomicReference<>(NOW), (current, guard) -> {
                calls.incrementAndGet();
                return CompletableFuture.completedStage(AgentScheduleService.Outcome.COMPLETED);
            })) {
                assertThrows(CompletionException.class, () -> service.tick().toCompletableFuture().join());
                service.tick().toCompletableFuture().join();
                assertEquals(1, calls.get());
                assertEquals(COMPLETED, store.find(task.id()).toCompletableFuture().join().orElseThrow().status());
            }
        }
    }

    /**
     * 2026/11/06：四槽均在途时第五项保持未认领，完成后按原到期索引继续，不超额启动。
     * @throws Exception 当有界协调或动作结束失败时
     */
    @Test
    void fourOccupiedSlotsRetainTheNextDueTask() throws Exception {
        try (var db = new NamespaceDatabaseFixture()) {
            var store = new EasyDataScheduleStore(db.data, db.mapper);
            var tasks = new java.util.ArrayList<AgentScheduledTask>();
            for (int index = 0; index < 5; index++) {
                var task = new AgentScheduledTask(UUID.randomUUID(), spec("占槽" + index, NOW.plusSeconds(index), 0),
                        NOW.plusSeconds(index), ACTIVE, 1, "等待");
                store.create(task).toCompletableFuture().join();
                tasks.add(task);
            }
            var entered = new CompletableFuture<Void>();
            var release = new CompletableFuture<AgentScheduleService.Outcome>();
            var calls = new AtomicInteger();
            try (var service = service(store, new AtomicReference<>(NOW.plusSeconds(10)), (task, guard) -> {
                if (calls.incrementAndGet() == 4) entered.complete(null);
                return release;
            })) {
                var first = service.tick().toCompletableFuture();
                try {
                    entered.get(3, java.util.concurrent.TimeUnit.SECONDS);
                    service.tick().toCompletableFuture().get(3, java.util.concurrent.TimeUnit.SECONDS);
                    assertEquals(4, calls.get());
                    assertEquals(ACTIVE, store.find(tasks.getLast().id()).toCompletableFuture().join().orElseThrow().status());
                } finally {
                    release.complete(AgentScheduleService.Outcome.COMPLETED);
                }
                first.get(3, java.util.concurrent.TimeUnit.SECONDS);
                service.tick().toCompletableFuture().get(3, java.util.concurrent.TimeUnit.SECONDS);
                assertEquals(5, calls.get());
                assertEquals(COMPLETED, store.find(tasks.getLast().id()).toCompletableFuture().join().orElseThrow().status());
            }
        }
    }

    /**
     * 装配可控时钟及显式测试动作，不引入新调度线程。
     * @param store 真实存储
     * @param clock 可推进时刻
     * @param action 动作
     * @return 独立服务
     */
    private static AgentScheduleService service(EasyDataScheduleStore store, AtomicReference<Instant> clock, AgentScheduleService.Action action) {
        var time = mock(Clock.class); when(time.instant()).thenAnswer(ignored -> clock.get());
        return new AgentScheduleService(store, time, (task, operation, actor) -> CompletableFuture.completedFuture(null), Map.of("remind", action));
    }

    /**
     * 固定显式时区和可信原生身份。
     * @param title 标题
     * @param first 首次时刻
     * @param interval 固定秒数间隔
     * @return 有界任务规格
     */
    private static AgentScheduledTask.Spec spec(String title, Instant first, long interval) {
        return new AgentScheduledTask.Spec(title, ACTOR, "remind", Map.of("text", "提醒内容"), first, "Asia/Shanghai", interval);
    }
}
