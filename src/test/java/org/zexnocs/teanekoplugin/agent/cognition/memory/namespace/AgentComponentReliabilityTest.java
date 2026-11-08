package org.zexnocs.teanekoplugin.agent.cognition.memory.namespace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.zexnocs.teanekoagent.integration.config.AgentScopeConfigData;
import org.zexnocs.teanekoagent.integration.config.AgentScopeConfigManager;
import org.zexnocs.teanekoagent.runtime.AgentActorContext;
import org.zexnocs.teanekoagent.runtime.AgentScope;
import org.zexnocs.teanekoagent.runtime.monitor.AgentRuntimeMonitor.Delivery;
import org.zexnocs.teanekoagent.runtime.operation.AgentOperationReceipt;
import org.zexnocs.teanekoagent.runtime.operation.EasyDataAgentOperationStore;
import org.zexnocs.teanekoagent.runtime.operation.IAgentOperationStore;
import org.zexnocs.teanekoagent.runtime.trigger.AgentTriggerGate;
import org.zexnocs.teanekocore.actuator.task.EmptyTaskResult;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.actuator.timer.interfaces.ITimerService;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataService;
import org.zexnocs.teanekocore.event.interfaces.IEventService;
import org.zexnocs.teanekocore.file_config.interfaces.IFileConfigService;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekocore.plugin.PluginRegistry;
import org.zexnocs.teanekocore.utils.StableDigest;
import org.zexnocs.teanekoplugin.agent.expression.sticker.StickerEvents;
import org.zexnocs.teanekoplugin.agent.expression.sticker.file_config.AgentStickerFileConfig;
import org.zexnocs.teanekoplugin.agent.social.proactive.adapter.ProactiveCoordinator;
import org.zexnocs.teanekoplugin.agent.social.proactive.adapter.ProactiveService;
import org.zexnocs.teanekoplugin.agent.social.proactive.adapter.ProactiveTargets;
import org.zexnocs.teanekoplugin.contract.agent.AgentComponentFixtures;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证候选公平性、精简事件宿主和无记忆活动的持久身份，不调用真实模型或平台。
 * <br>2026/11/05：用可控阶段覆盖忙槽位、空插件和重启防重边界。
 * <br>2026/11/07：认知业务与宿主生命周期解耦，保留原身份、事务及异步完成边界。
 *
 *
 * <br>2026/11/08：夹具通过真实插件组件契约准备参数并验证改造后的端口。
 *
 * @author zExNocs
 * @date 2026/11/05
 * @since 4.8.3-l
 * @version 4.9.0-l
 */
@Timeout(15)
class AgentComponentReliabilityTest {
    /**
     * 四个同相位目标共享两个槽位，忙跳过不能丢失整周期的执行机会。
     * @throws Exception 当有界等待失败时
     */
    @Test
    void rotatesFourTargetsAcrossTwoBusySlots() throws Exception {
        var configs = mock(IConfigDataService.class);
        var settings = new AgentScopeConfigData(null);
        settings.setMode("ACTIVE");
        AgentComponentFixtures.set(settings, "proactive", "proactiveTimedEnabled", true);
        AgentComponentFixtures.set(settings, "proactive", "proactiveIntervalSeconds", 60);
        var keys = new LinkedHashSet<>(List.of("甲", "乙", "丙", "丁"));
        var manager = new AgentScopeConfigManager();
        when(configs.getAllConfigKeys(manager)).thenReturn(List.copyOf(keys));
        when(configs.getConfigData(eq(manager), eq(AgentScopeConfigData.class), anyString())).thenReturn(Optional.of(settings));
        var targets = mock(ProactiveTargets.class);
        when(targets.group(anyString())).thenAnswer(call -> {
            String id = call.getArgument(0);
            return new ProactiveTargets.Target(new AgentScope("test", id), id, true, "机器人");
        });
        var service = mock(ProactiveService.class);
        var occupied = new ArrayList<CompletableFuture<Delivery>>();
        var attempted = new ArrayList<String>();
        when(service.candidate(any(), eq(AgentTriggerGate.Kind.TIMER), anyString(), eq(""))).thenAnswer(call -> {
            if (occupied.stream().filter(stage -> !stage.isDone()).count() >= 2) {
                return CompletableFuture.completedStage(Delivery.BUSY);
            }
            var stage = new CompletableFuture<Delivery>();
            occupied.add(stage);
            attempted.add(call.<ProactiveTargets.Target>getArgument(0).id());
            return stage;
        });
        var now = new AtomicReference<>(Instant.parse("2026-11-05T00:00:00Z"));
        var clock = mock(Clock.class);
        when(clock.instant()).thenAnswer(ignored -> now.get());
        var timers = mock(ITimerService.class);
        org.zexnocs.teanekocore.actuator.timer.interfaces.ITimerTaskConfig<Void> timer = mock();
        when(timers.registerByDelay(anyString(), anyString(), any(), any(), eq(Void.class))).thenReturn(timer);
        try (var coordinator = new ProactiveCoordinator(configs, manager, AgentComponentFixtures.proactive(configs, manager), targets, service, timers, Runnable::run, clock)) {
            // 2026/11/07：测试显式启动本代定时与主动候选入口。
            coordinator.start().toCompletableFuture().join();
            coordinator.tick().toCompletableFuture().get(3, TimeUnit.SECONDS);
            now.set(now.get().plusSeconds(60));
            var first = coordinator.tick().toCompletableFuture();
            assertFalse(first.isDone());
            occupied.forEach(stage -> stage.complete(Delivery.ABSTAINED));
            first.get(3, TimeUnit.SECONDS);
            var second = coordinator.tick().toCompletableFuture();
            occupied.forEach(stage -> stage.complete(Delivery.ABSTAINED));
            second.get(3, TimeUnit.SECONDS);
            assertEquals(keys, new LinkedHashSet<>(attempted));
        } finally {
            occupied.forEach(stage -> stage.complete(Delivery.ABSTAINED));
        }
    }

    /** 精简宿主拥有事件服务但没有插件目录，空插件列表仍完成真实采集事件。 */
    @Test
    void emptyEventPluginsDoNotRequirePluginRegistry() {
        var beans = new DefaultListableBeanFactory();
        var events = mock(IEventService.class);
        var logger = mock(ILogger.class);
        when(events.pushEventWithFuture(any())).thenReturn(new TaskFuture<>(logger, "空插件事件",
                CompletableFuture.completedFuture(EmptyTaskResult.INSTANCE)));
        beans.registerSingleton("events", events);
        var files = mock(IFileConfigService.class);
        var config = new AgentStickerFileConfig();
        when(files.requireCurrent(AgentStickerFileConfig.class)).thenReturn(config);
        var service = new StickerEvents(beans.getBeanProvider(IEventService.class), beans.getBeanProvider(PluginRegistry.class), files, logger);
        var result = service.before(StickerCollectionIntegrationTest.source("群", "https://test.invalid/image"), config.snapshot())
                .toCompletableFuture().join();
        assertTrue(result.allowed());
        verify(events).pushEventWithFuture(any());
    }

    /** 默认工厂将公共身份检查与动作策略组合，非提醒动作可执行，停用后仍允许取消。 */
    @Test
    void defaultScheduleFactoryHonorsPluginSpecificPolicyAndLifecycle() {
        try (var db = new NamespaceDatabaseFixture()) {
            var store = new org.zexnocs.teanekoplugin.agent.automation.schedule.EasyDataScheduleStore(db.data, db.mapper);
            var adapter = mock(org.zexnocs.teanekoplugin.agent.automation.schedule.adapter.NativeScheduleAdapter.class);
            when(adapter.checkIdentity(any(), any(), any())).thenReturn(CompletableFuture.completedStage(null));
            var plugins = new PluginRegistry(List.of(
                    () -> new org.zexnocs.teanekocore.plugin.PluginDescriptor("sample", "1", 1, Set.of())));
            var calls = new java.util.concurrent.atomic.AtomicInteger();
            var contribution = new org.zexnocs.teanekoplugin.agent.automation.schedule.AgentTaskAction("sample", "custom",
                    (task, guard) -> guard.get().thenApply(ignored -> {
                        calls.incrementAndGet();
                        return org.zexnocs.teanekoplugin.agent.automation.schedule.AgentScheduleService.Outcome.COMPLETED;
                    }), (task, operation, actor) -> task.spec().arguments().keySet().equals(Set.of("sample"))
                            ? CompletableFuture.completedStage(null)
                            : CompletableFuture.failedStage(new IllegalArgumentException("示例动作参数无效。")));
            var factory = new org.zexnocs.teanekoplugin.agent.automation.schedule.configuration.AgentScheduleConfig();
            var actor = new AgentActorContext(new AgentScope("test", "群"), AgentActorContext.Origin.USER,
                    Optional.of(UUID.randomUUID()), "用户", "消息", "群");
            var first = Instant.now().plusSeconds(60);
            var spec = new org.zexnocs.teanekoplugin.agent.automation.schedule.AgentScheduledTask.Spec("插件动作", actor,
                    "custom", Map.of("sample", "值"), first, "UTC", 0);
            try (var service = factory.pluginScheduleService(store, adapter, List.of(contribution), plugins)) {
                var task = service.create(UUID.randomUUID(), spec).toCompletableFuture().join();
                store.update(task.id(), current -> new org.zexnocs.teanekoplugin.agent.automation.schedule.AgentScheduledTask(
                        current.id(), current.spec(), Instant.now().minusSeconds(1), current.status(), 2, "测试到期"))
                        .toCompletableFuture().join();
                service.tick().toCompletableFuture().join();
                assertEquals(1, calls.get());
                assertEquals(org.zexnocs.teanekoplugin.agent.automation.schedule.AgentScheduledTask.Status.COMPLETED,
                        store.find(task.id()).toCompletableFuture().join().orElseThrow().status());
                var pending = service.create(UUID.randomUUID(), spec).toCompletableFuture().join();
                plugins.setEnabled("sample", false);
                assertThrows(CompletionException.class, () -> service.create(UUID.randomUUID(), spec).toCompletableFuture().join());
                assertEquals(org.zexnocs.teanekoplugin.agent.automation.schedule.AgentScheduledTask.Status.CANCELLED,
                        service.cancel(pending.id(), actor).toCompletableFuture().join().status());
                verify(adapter, atLeastOnce()).checkIdentity(any(), any(), eq(actor));
            }
            assertThrows(IllegalArgumentException.class, () -> factory.pluginScheduleService(
                    store, adapter, List.of(contribution, contribution), plugins));
        }
    }

    /** 无记忆的主动活动仍持久占用原请求号，重建存储后不得取得第二次执行权。 */
    @Test
    void proactiveIdentitySurvivesRestartWithoutMemoryReference() {
        try (var db = new NamespaceDatabaseFixture()) {
            var actor = new AgentActorContext(new AgentScope("test", "群"), AgentActorContext.Origin.SYSTEM,
                    Optional.empty(), "", "定时发生", "群");
            var attempt = new IAgentOperationStore.Attempt(UUID.randomUUID(), "proactive-entry", actor, null,
                    StableDigest.sha256("候选"), IAgentOperationStore.Kind.PROACTIVE_ENTRY);
            var store = new EasyDataAgentOperationStore(db.data, db.mapper);
            assertTrue(store.claim(attempt).toCompletableFuture().join().acquired());
            var receipt = new AgentOperationReceipt(AgentOperationReceipt.Status.COMPLETED);
            store.finish(attempt, receipt).toCompletableFuture().join();
            var restored = new EasyDataAgentOperationStore(db.data, db.mapper).claim(attempt).toCompletableFuture().join();
            assertFalse(restored.acquired());
            assertEquals(receipt, restored.receipt());
            assertThrows(IllegalArgumentException.class, () -> new IAgentOperationStore.Attempt(
                    attempt.requestId(), "tool", actor, null, attempt.commandDigest(), IAgentOperationStore.Kind.TOOL));
        }
    }
}
