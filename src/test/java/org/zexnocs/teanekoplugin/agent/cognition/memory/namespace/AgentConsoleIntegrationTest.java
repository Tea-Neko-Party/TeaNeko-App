package org.zexnocs.teanekoplugin.agent.cognition.memory.namespace;

// 2026/11/07：认知业务契约归入插件，纯会话数据沿用运行底座。

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.mock.env.MockEnvironment;
import org.zexnocs.teanekoagent.runtime.AgentActivity;
import org.zexnocs.teanekoagent.runtime.AgentActorContext;
import org.zexnocs.teanekoagent.runtime.AgentHandle;
import org.zexnocs.teanekoagent.runtime.AgentScope;
import org.zexnocs.teanekoagent.runtime.IAgentRegistry;
import org.zexnocs.teanekoagent.runtime.access.AgentAccessDecision;
import org.zexnocs.teanekoagent.runtime.access.AgentAccessGate;
import org.zexnocs.teanekoagent.runtime.access.IAgentReadiness;
import org.zexnocs.teanekoagent.runtime.access.IAgentScopePolicyPort;
import org.zexnocs.teanekoagent.runtime.conversation.IAgentConversationControl;
import org.zexnocs.teanekoapp.web.client.ManagementTargets;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekocore.plugin.management.ManagementContext;
import org.zexnocs.teanekoplugin.agent.cognition.integration.web.AgentConsole;
import org.zexnocs.teanekoplugin.agent.cognition.integration.web.AgentConsoleService;
import org.zexnocs.teanekoplugin.agent.cognition.memory.adapter.EasyDataMemoryManagementStore;
import org.zexnocs.teanekoplugin.agent.cognition.memory.fact.MemoryEventTime;
import org.zexnocs.teanekoplugin.agent.cognition.memory.fact.MemoryFactAppend;
import org.zexnocs.teanekoplugin.agent.cognition.memory.fact.MemoryFactSource;
import org.zexnocs.teanekoplugin.agent.cognition.memory.fact.MemoryFactValue;
import org.zexnocs.teanekoplugin.agent.cognition.memory.profile.IUserProfileService;
import org.zexnocs.teanekoplugin.agent.cognition.personality.BasePersonality;
import org.zexnocs.teanekoplugin.agent.cognition.personality.IPersonalityConfiguration;
import org.zexnocs.teanekoplugin.agent.cognition.personality.PersonalitySettings;
import org.zexnocs.teanekoplugin.agent.cognition.personality.adapter.EasyDataPersonalityStore;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

/**
 * 复用原事务夹具验证网页领域编排、独立管理来源、回执和换代边界。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/07：认知业务与宿主生命周期解耦，保留原身份、事务及异步完成边界。
 *
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.9.0-l
 */
@Timeout(20)
class AgentConsoleIntegrationTest {
    /** 长期管理维护原版本与遗忘墓碑，同一回执不复活旧条目，普通用户入口仍核对主体。 */
    @Test
    void longTermManagementPreservesVersionAndForgettingRules() {
        try (var db = new NamespaceDatabaseFixture()) {
            var store = new org.zexnocs.teanekoplugin.agent.cognition.memory.adapter.EasyDataLongTermMemoryStore(db.data, db.store,
                    db.mapper, org.zexnocs.teanekoplugin.agent.cognition.memory.layered.LayeredMemorySettings::defaults);
            var now = Instant.now();
            db.store.create(new MemoryNamespace(MemoryNamespaceId.SHARED, 0, 0, now, null, null), 5)
                    .toCompletableFuture().join();
            var user = UUID.randomUUID();
            var actor = new AgentActorContext(scope, AgentActorContext.Origin.USER, Optional.of(user), "100", "原消息", "目标");
            var reference = new MemoryNamespaceReference(scope, MemoryNamespaceId.SHARED, 0, 0);
            store.appendExplicit(new MemoryFactAppend(UUID.randomUUID(), reference, user,
                    new MemoryFactValue(MemoryFactValue.Kind.PREFERENCE, "喜欢绿茶", List.of(), 1,
                            MemoryFactValue.Importance.NORMAL, MemoryEventTime.unknown(), null),
                    new MemoryFactSource(UUID.randomUUID(), "明确保存", actor)), now).toCompletableFuture().join();
            var original = store.query(reference, user, "", 20).toCompletableFuture().join().items().getFirst();
            var operation = UUID.randomUUID();
            var manager = new AgentActorContext(scope, AgentActorContext.Origin.MANAGEMENT, Optional.of(UUID.randomUUID()),
                    "", operation.toString(), "管理目标");
            var checks = new AtomicInteger();
            assertThrows(CompletionException.class, () -> store.manage(reference, manager, original.id(),
                    original.revision(), "提交前应回滚的修正", UUID.randomUUID(), now, () -> {
                        if (checks.incrementAndGet() == 2) {
                            throw new SecurityException("测试模拟长期事务提交前撤权。");
                        }
                    }).toCompletableFuture().join());
            var forgotten = store.manage(reference, manager, original.id(), original.revision(), null,
                    operation, now, () -> {}).toCompletableFuture().join();
            assertEquals(org.zexnocs.teanekoplugin.agent.cognition.memory.layered.LongMemories.Status.FORGOTTEN, forgotten.status());
            assertEquals(forgotten, store.managementOperation(reference, manager, operation, () -> {}).toCompletableFuture().join());
            assertTrue(store.query(reference, user, "", 20).toCompletableFuture().join().items().isEmpty());
            assertThrows(CompletionException.class, () -> store.change(reference, UUID.randomUUID(), original.id(),
                    forgotten.revision(), "不能冒充本人", UUID.randomUUID(), "其他消息", now).toCompletableFuture().join());
        }
    }

    /** 时间线只在真实发送阶段完成后确认交付，失败保留结果不确定。 */
    @Test
    void deliveryTimelineWaitsForTheActualPlatformCompletion() {
        var timeline = new org.zexnocs.teanekoagent.runtime.monitor.AgentActivityTimeline();
        var delivery = new org.zexnocs.teanekoplugin.agent.cognition.memory.history.AgentDeliveryService(
                mock(org.zexnocs.teanekoplugin.agent.cognition.memory.history.IAgentDeliveryStore.class), Runnable::run,
                java.time.Clock.systemUTC(), () -> java.time.Duration.ofDays(7));
        delivery.installTimeline(timeline);
        var actor = new AgentActorContext(scope, AgentActorContext.Origin.USER, Optional.of(UUID.randomUUID()),
                "100", "来源", "目标");
        var platform = new CompletableFuture<Void>();
        var work = delivery.sendRecorded(null, actor, UUID.randomUUID(), 0, 1, "测试", "摘要", () -> platform);
        assertFalse(work.toCompletableFuture().isDone());
        assertEquals("STARTED", timeline.page(scope, 0, 100).events().getLast().state());
        platform.complete(null);
        work.toCompletableFuture().join();
        assertEquals("SENT", timeline.page(scope, 0, 100).events().getLast().state());
        assertTrue(timeline.page(new AgentScope("onebot", "其他群"), 0, 100).events().isEmpty());
    }

    private final AgentScope scope = new AgentScope("onebot", "onebot-group@123");
    private final BasePersonality base = new BasePersonality("test", 1, "测试人格", List.of("自然交流"), Set.of("speakingStyle"));

    /**
     * @param db 原事务夹具
     * @param personality 原人格存储
     * @return 不启动模型或应用的实际领域编排
     */
    private AgentConsoleService service(NamespaceDatabaseFixture db, EasyDataPersonalityStore personality) {
        var bindings = mock(MemoryNamespaceBindings.class);
        when(bindings.readReference(any())).thenReturn(CompletableFuture.completedStage(Optional.empty()));
        var configuration = mock(IPersonalityConfiguration.class);
        when(configuration.settings(any())).thenReturn(new PersonalitySettings("test", false, "", "", true, true));
        when(configuration.base(any())).thenReturn(base);
        var conversations = mock(IAgentConversationControl.class);
        when(conversations.synchronizePartition(any(), any())).thenReturn(CompletableFuture.completedStage(0));
        when(conversations.synchronizePromptContext(any(), any(), anyBoolean(), any()))
                .thenReturn(CompletableFuture.completedStage(0));
        return new AgentConsoleService(bindings, db.store,
                new EasyDataMemoryManagementStore(db.store, db.facts, db.data, () -> 5, db.mapper, new MockEnvironment()),
                mock(IUserProfileService.class), personality, personality, configuration, conversations);
    }

    /**
     * @param control 本调用拥有的控制
     * @param guard 原宿主授权
     * @return 独立管理员上下文，不携带原生用户对象
     */
    private ManagementContext context(ExecutionControl control, Runnable guard) {
        return new ManagementContext("web.local-admin", ManagementTargets.encode(scope.clientId(), scope.scopeId()),
                UUID.randomUUID(), control, guard);
    }

    /** 管理克隆和补正保存独立来源，原回执可查询且相同请求不会重复添加。 */
    @Test
    void managementUsesIndependentIdentityAndOriginalDomainReceipts() {
        try (var db = new NamespaceDatabaseFixture(); var control = ExecutionControl.create()) {
            var personality = new EasyDataPersonalityStore(db.data, db.mapper);
            var service = service(db, personality);
            var context = context(control, () -> {});
            var uninitialized = assertInstanceOf(AgentConsole.PersonalityView.class, service.personality(context,
                    new AgentConsole.PersonalityQuery("READ", null, "", -1, -1, null, null, null, null))
                    .toCompletableFuture().join());
            assertEquals(0, uninitialized.bindingRevision());
            assertTrue(personality.binding(scope).toCompletableFuture().join().isEmpty());
            db.store.create(new MemoryNamespace(MemoryNamespaceId.SHARED, 0, 0, Instant.now(), null, null), 5)
                    .toCompletableFuture().join();
            var cloneId = UUID.randomUUID();
            var clone = new AgentConsole.MemoryQuery("CLONE", cloneId, "default", 0, 0, null, null, null, null);
            var copied = assertInstanceOf(AgentConsole.ChangeView.class,
                    service.memory(context, clone).toCompletableFuture().join());
            assertEquals("APPLIED", copied.status());
            var creation = Objects.requireNonNull(db.store.find(new MemoryNamespaceId(copied.target()))
                    .toCompletableFuture().join().orElseThrow().creation());
            assertEquals(AgentActorContext.Origin.MANAGEMENT, creation.actor().origin());
            var gate = new AgentAccessGate(mock(IAgentRegistry.class), mock(IAgentScopePolicyPort.class),
                    mock(IAgentReadiness.class), mock(ILogger.class));
            assertEquals(AgentAccessDecision.Reason.INVALID_ORIGIN,
                    gate.evaluate(mock(AgentHandle.class), creation.actor(), AgentActivity.SERVICE).reason());
            var original = service.memory(context, new AgentConsole.MemoryQuery("OPERATION", cloneId,
                    "default", -1, -1, null, null, null, null)).toCompletableFuture().join();
            assertEquals(copied.target(), assertInstanceOf(AgentConsole.ChangeView.class, original).target());
            var selection = personality.resolve(scope, base, Instant.now()).toCompletableFuture().join();
            var operation = UUID.randomUUID();
            var add = new AgentConsole.PersonalityQuery("ADD", operation, selection.binding().correctionKey(),
                    selection.binding().revision(), selection.state().revision(), null, "speakingStyle", "语气更简洁", null);
            assertEquals("APPLIED", assertInstanceOf(AgentConsole.ChangeView.class,
                    service.personality(context, add).toCompletableFuture().join()).status());
            service.personality(context, add).toCompletableFuture().join();
            var stored = personality.state(selection.binding().correctionKey()).toCompletableFuture().join().orElseThrow();
            assertEquals(1, stored.corrections().size());
            assertEquals(AgentActorContext.Origin.MANAGEMENT, stored.corrections().getFirst().source().origin());
            assertEquals("APPLIED", assertInstanceOf(AgentConsole.ChangeView.class, service.personality(context,
                    new AgentConsole.PersonalityQuery("OPERATION", operation, "", -1, -1, null, null, null, null))
                    .toCompletableFuture().join()).status());
        }
    }

    /** 提交前撤权回滚换代和审计，成功清空后旧分页不能混入新代次。 */
    @Test
    void revokedAuthorizationRollsBackAndOldPagesRejectNewGenerations() {
        try (var db = new NamespaceDatabaseFixture(); var control = ExecutionControl.create()) {
            var service = service(db, new EasyDataPersonalityStore(db.data, db.mapper));
            db.store.create(new MemoryNamespace(MemoryNamespaceId.SHARED, 0, 0, Instant.now(), null, null), 5)
                    .toCompletableFuture().join();
            var checks = new AtomicInteger();
            var revoked = context(control, () -> {
                if (checks.incrementAndGet() == 4) {
                    throw new SecurityException("测试模拟管理会话撤销。");
                }
            });
            var clear = new AgentConsole.MemoryQuery("CLEAR", UUID.randomUUID(), "default", 0, 0, null, null, null, null);
            assertThrows(CompletionException.class, () -> service.memory(revoked, clear).toCompletableFuture().join());
            assertEquals(0, db.store.find(MemoryNamespaceId.SHARED).toCompletableFuture().join().orElseThrow().generation());
            var allowed = context(control, () -> {});
            assertThrows(CompletionException.class, () -> service.memory(allowed, new AgentConsole.MemoryQuery("OPERATION",
                    clear.operationId(), "", -1, -1, null, null, null, null)).toCompletableFuture().join());
            service.memory(allowed, clear).toCompletableFuture().join();
            assertThrows(CompletionException.class, () -> service.memory(allowed,
                    new AgentConsole.MemoryQuery("READ", null, "default", 0, -1, null, null, null, null))
                    .toCompletableFuture().join());
        }
    }
}
