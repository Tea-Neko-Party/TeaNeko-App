package org.zexnocs.teanekoagent.memory.access;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.ArgumentCaptor;
import org.zexnocs.teanekoagent._app_config.MemoryAccessConfig;
import org.zexnocs.teanekoagent.adapter.config.AgentScopeConfigData;
import org.zexnocs.teanekoagent.adapter.config.AgentScopeConfigManager;
import org.zexnocs.teanekoagent.adapter.config.AgentScopeFieldChecker;
import org.zexnocs.teanekoagent.adapter.config.ConfigDataMemoryAccessPolicy;
import org.zexnocs.teanekoagent.adapter.memory.MemoryToolContexts;
import org.zexnocs.teanekoagent.agent.*;
import org.zexnocs.teanekoagent.agent.memory.AgentMemoryReferenceException;
import org.zexnocs.teanekoagent.agent.memory.IAgentMemoryReferences;
import org.zexnocs.teanekoagent.llm.framework.tool.ToolInvocationContext;
import org.zexnocs.teanekoagent.memory.fact.*;
import org.zexnocs.teanekoagent.memory.namespace.MemoryNamespaceId;
import org.zexnocs.teanekoagent.memory.namespace.MemoryNamespaceReference;
import org.zexnocs.teanekoagent.memory.profile.IUserProfileService;
import org.zexnocs.teanekoagent.memory.profile.UserProfileRequest;
import org.zexnocs.teanekoagent.memory.profile.UserProfileView;
import org.zexnocs.teanekoagent.memory.recall.*;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.actuator.execution.ResourceBudget;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataService;
import org.zexnocs.teanekocore.framework.composition.ComponentPatch;
import org.zexnocs.teanekocore.framework.composition.Entity;
import org.zexnocs.teanekocore.framework.composition.EntityId;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 验证宿主身份、明确原文、独立开关及取消/交付边界，不调用真实模型或数据库。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
@Timeout(10)
class MemoryAccessServiceTest {
    private final Instant now = Instant.parse("2026-10-29T01:00:00Z");
    private final AgentScope scope = new AgentScope("客户端", "群");
    private final UUID user = UUID.randomUUID();
    private final AgentActorContext actor = new AgentActorContext(scope, AgentActorContext.Origin.USER, Optional.of(user), "平台用户", "原消息", "原目标");
    private final MemoryNamespaceReference reference = new MemoryNamespaceReference(scope, MemoryNamespaceId.SHARED, 2, 0);
    private final MemoryAccessContext context = new MemoryAccessContext(UUID.randomUUID(), actor, reference);
    private final AtomicReference<MemoryAccessPolicy> policy = new AtomicReference<>(new MemoryAccessPolicy(true, true, true, false));
    private final IAgentMemoryReferences references = mock(IAgentMemoryReferences.class);
    private final IMemoryRecallService recall = mock(IMemoryRecallService.class);
    private final IUserProfileService profiles = mock(IUserProfileService.class);
    private final IMemoryFactStore store = mock(IMemoryFactStore.class);
    private final MemoryRecallQuery query = new MemoryRecallQuery(null, null, Set.of(), Set.of(), List.of(), null, null, true);

    /** 初始化只读校验替身，不在服务构造时访问依赖。 */
    MemoryAccessServiceTest() { when(references.verify(any())).thenReturn(CompletableFuture.completedFuture(null)); }

    /**
     * 建立借用执行器的真实边界。
     * @param executor 业务执行器
     * @return 当前测试的服务
     */
    private MemoryAccessService service(Executor executor) {
        return new MemoryAccessService(ignored -> policy.get(), references, recall, profiles, new MemoryFacts(store, Clock.fixed(now, ZoneOffset.UTC)), executor);
    }

    /**
     * 构造待追加原文，不让测试绕过内容校验。
     * @param text 明确原文
     * @return 有界事实
     */
    private MemoryFactValue value(String text) {
        return new MemoryFactValue(MemoryFactValue.Kind.FACT, text, List.of(), 1, MemoryFactValue.Importance.NORMAL, MemoryEventTime.unknown(), null);
    }

    /**
     * 识别当前请求的明确原文。
     * @return 宿主意图
     */
    private MemoryAddIntent intent() { return MemoryAddIntent.fromUserText(context, "请记住：我喜欢茶").orElseThrow(); }

    /**
     * 构造不含命中的完整结果。
     * @return 固定原引用的读取结果
     */
    private MemoryRecallResult empty() { return new MemoryRecallResult(reference, now, List.of(), 0, 0, Set.of(), 2); }

    /**
     * 构造真实提交形态的追加回执。
     * @param command 原命令
     * @return 原记录回执
     */
    private MemoryFactReceipt receipt(MemoryFactAppend command) {
        return new MemoryFactReceipt(MemoryFactReceipt.Status.STORED, new MemoryFactRecord(command.operationId(), reference.namespaceId(), reference.generation(),
                0, command.subjectId(), command.value(), command.source(), now, now, false));
    }

    /**
     * 提取异步失败，避免把失败当作空结果。
     * @param stage 异步操作
     * @return 根错误
     */
    private static Throwable failure(CompletionStage<?> stage) { return assertThrows(CompletionException.class, () -> stage.toCompletableFuture().join()).getCause(); }

    /**
     * 核对固定访问类别且无原始异常链。
     * @param reason 期望类别
     * @param stage 操作阶段
     */
    private static void rejected(MemoryAccessException.Reason reason, CompletionStage<?> stage) {
        var error = assertInstanceOf(MemoryAccessException.class, failure(stage)); assertEquals(reason, error.reason()); assertNull(error.getCause()); assertEquals(0, error.getSuppressed().length);
    }

    /** 验证只识别完整原消息的明确前缀，普通聊天、否定、引用和空白不产生授权。 */
    @Test void intentRequiresExplicitOriginalPrefixAndHumanActor() {
        for (var prefix : List.of("记住：", "记住:", "请记住：", "请记住:")) {
            assertEquals("我喜欢茶", MemoryAddIntent.fromUserText(context, "  " + prefix + " 我喜欢茶  ").orElseThrow().text());
        }
        for (var text : List.of("我喜欢茶", "不要记住：我喜欢茶", "他说：记住：我喜欢茶", "> 记住：我喜欢茶", "记住： ", "记住：" + "茶".repeat(4097))) {
            assertTrue(MemoryAddIntent.fromUserText(context, text).isEmpty());
        }
        var system = new AgentActorContext(scope, AgentActorContext.Origin.SYSTEM, Optional.empty(), "", "任务", "目标");
        assertTrue(MemoryAddIntent.fromUserText(new MemoryAccessContext(context.requestId(), system, reference), "记住：我喜欢茶").isEmpty());
    }

    /** 验证意图不能跨请求、身份、来源、空间版本或正文重用，授权对象的输出也不含原文。 */
    @Test void intentBindsCompleteContextAndExactBody() {
        var intent = intent(); assertTrue(intent.permits(context, "我喜欢茶")); assertFalse(intent.permits(context, "我喜欢咖啡"));
        assertFalse(intent.permits(new MemoryAccessContext(UUID.randomUUID(), actor, reference), intent.text()));
        var other = new AgentActorContext(scope, AgentActorContext.Origin.USER, Optional.of(UUID.randomUUID()), "平台用户", "原消息", "原目标");
        assertFalse(intent.permits(new MemoryAccessContext(context.requestId(), other, reference), intent.text()));
        assertFalse(intent.permits(new MemoryAccessContext(context.requestId(), actor, new MemoryNamespaceReference(scope, MemoryNamespaceId.SHARED, 3, 0)), intent.text()));
        assertFalse(new ObjectMapper().writeValueAsString(intent).contains("我喜欢茶")); assertFalse(new ObjectMapper().writeValueAsString(context).contains("原消息"));
        assertThrows(IllegalArgumentException.class, () -> new MemoryAccessContext(context.requestId(), actor,
                new MemoryNamespaceReference(new AgentScope("其他客户端", "群"), MemoryNamespaceId.SHARED, 0, 0)));
    }

    /** 验证实际工具组件中的身份交接，缺失引用和请求/actor 不一致不能回退解析或接受模型提示。 */
    @Test void toolContextOnlyReadsTrustedComponentsAndRejectsMismatches() {
        var request = new AgentRunRequest(context.requestId(), actor, AgentActivity.SERVICE, List.of());
        var patch = ComponentPatch.builder().put(AgentComponents.REQUEST, request).put(AgentComponents.ACTOR, actor)
                .put(AgentComponents.MEMORY_REFERENCE, reference).build();
        var entity = new Entity(EntityId.random(), patch);
        try (var control = ExecutionControl.create()) {
            assertEquals(context, MemoryToolContexts.from(new ToolInvocationContext(context.requestId(), entity.snapshot(), control)));
            assertThrows(MemoryAccessException.class, () -> MemoryToolContexts.from(new ToolInvocationContext(UUID.randomUUID(), entity.snapshot(), control)));
            var missing = new Entity(EntityId.random(), ComponentPatch.builder().put(AgentComponents.REQUEST, request).put(AgentComponents.ACTOR, actor).build());
            assertThrows(MemoryAccessException.class, () -> MemoryToolContexts.from(new ToolInvocationContext(context.requestId(), missing.snapshot(), control)));
            var other = new AgentActorContext(scope, AgentActorContext.Origin.USER, Optional.of(UUID.randomUUID()), "其他用户", "其他消息", "目标");
            entity.commit(entity.snapshot().revision(), ComponentPatch.builder().put(AgentComponents.ACTOR, other).build());
            assertThrows(MemoryAccessException.class, () -> MemoryToolContexts.from(new ToolInvocationContext(context.requestId(), entity.snapshot(), control)));
            var fixedRequest = new AgentRunRequest(context.requestId(), actor, AgentActivity.SERVICE, List.of(), null,
                    new MemoryNamespaceReference(scope, MemoryNamespaceId.SHARED, 3, 0));
            entity.commit(entity.snapshot().revision(), ComponentPatch.builder().put(AgentComponents.ACTOR, actor).put(AgentComponents.REQUEST, fixedRequest).build());
            assertThrows(MemoryAccessException.class, () -> MemoryToolContexts.from(new ToolInvocationContext(context.requestId(), entity.snapshot(), control)));
        }
    }

    /** 验证真实配置缺失、默认值、注销及非法模式；独立开关不改变权威空间绑定。 */
    @Test void configurationRequiresRealRegistrationAndKeepsSwitchesSeparate() {
        var configs = mock(IConfigDataService.class); var manager = new AgentScopeConfigManager(); var adapter = new ConfigDataMemoryAccessPolicy(configs, manager);
        verifyNoInteractions(configs); when(configs.getConfigData(manager, AgentScopeConfigData.class, scope.scopeId())).thenReturn(Optional.empty());
        assertEquals(new MemoryAccessPolicy(false, false, false, false), adapter.read(scope));
        var data = new ObjectMapper().readValue("{\"enabled\":true}", AgentScopeConfigData.class);
        when(configs.getConfigData(manager, AgentScopeConfigData.class, scope.scopeId())).thenReturn(Optional.of(data));
        assertEquals(new MemoryAccessPolicy(true, true, true, false), adapter.read(scope));
        data.setMemoryReadEnabled(false); data.setMemoryLearningEnabled(true);
        assertEquals(new MemoryAccessPolicy(true, false, true, true), adapter.read(scope));
        data.setEnabled(false); assertFalse(adapter.read(scope).enabled()); data.setMode("非法模式"); assertThrows(IllegalArgumentException.class, () -> adapter.read(scope));
        when(configs.getConfigData(manager, AgentScopeConfigData.class, scope.scopeId())).thenReturn(Optional.empty()); assertFalse(adapter.read(scope).enabled());
    }

    /** 验证 /cfg 只接受新增开关的真实布尔值，不新增空间或管理授权的旁路字段。 */
    @Test void configurationCheckerAcceptsOnlyDeclaredBooleanSwitches() {
        var checker = new AgentScopeFieldChecker();
        for (var field : List.of("memoryReadEnabled", "memoryAddEnabled", "memoryLearningEnabled")) {
            assertNull(checker.isValid(field, "true")); assertNull(checker.isValid(field, "false")); assertNotNull(checker.isValid(field, "TRUE")); assertNotNull(checker.isValid(field, "1"));
        }
        assertNotNull(checker.isValid("memoryNamespace", "default")); assertNotNull(checker.isValid("authorized", "true"));
    }

    /** 验证真实装配构造无 I/O，不关闭借用的执行器。 */
    @Test void compositionIsLazyAndOwnsNoExecutor() {
        var policies = mock(IMemoryAccessPolicy.class);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            assertNotNull(new MemoryAccessConfig().memoryAccessService(policies, references, recall, profiles, new MemoryFacts(store, Clock.systemUTC()), executor));
            verifyNoInteractions(policies, recall, profiles, store); verify(references, never()).verify(any()); assertFalse(executor.isShutdown());
        }
    }

    /** 验证未登记或停用时所有普通记忆操作都不访问引用或业务数据。 */
    @Test void disabledScopeBlocksAllOperationsBeforeStorage() {
        policy.set(new MemoryAccessPolicy(false, true, true, true));
        try (var control = ExecutionControl.create()) {
            var service = service(Runnable::run);
            rejected(MemoryAccessException.Reason.DISABLED, service.recall(context, query, MemoryRecallRequest.Use.INJECTION, null, control));
            rejected(MemoryAccessException.Reason.DISABLED, service.profile(context, user, MemoryRecallRequest.Use.TOOL, null, control));
            rejected(MemoryAccessException.Reason.DISABLED, service.append(context, "动作", intent(), value(intent().text()), control));
            verifyNoInteractions(recall, profiles, store); verify(references, never()).verify(any());
        }
    }

    /** 验证关闭读取同时阻止注入/工具查询和画像，学习开启不能恢复读取。 */
    @Test void readSwitchCoversInjectionToolsAndProfiles() {
        policy.set(new MemoryAccessPolicy(true, false, true, true));
        try (var control = ExecutionControl.create()) {
            var service = service(Runnable::run);
            for (var use : MemoryRecallRequest.Use.values()) {
                rejected(MemoryAccessException.Reason.READ_DISABLED, service.recall(context, query, use, null, control));
                rejected(MemoryAccessException.Reason.READ_DISABLED, service.profile(context, user, use, null, control));
            }
            verifyNoInteractions(recall, profiles, store);
        }
    }

    /** 验证新增关闭时读取仍可用；学习不能代替新增开关或明确意图。 */
    @Test void addAndLearningCannotGrantEachOthersPermissions() {
        when(recall.recall(any())).thenReturn(CompletableFuture.completedFuture(empty()));
        try (var control = ExecutionControl.create()) {
            var service = service(Runnable::run); policy.set(new MemoryAccessPolicy(true, true, false, true));
            assertTrue(service.recall(context, query, MemoryRecallRequest.Use.TOOL, null, control).toCompletableFuture().join().definitivelyEmpty());
            rejected(MemoryAccessException.Reason.ADD_DISABLED, service.append(context, "动作", intent(), value(intent().text()), control));
            policy.set(new MemoryAccessPolicy(true, false, true, true));
            rejected(MemoryAccessException.Reason.INTENT_REQUIRED, service.append(context, "动作", null, value("普通聊天"), control));
            verifyNoInteractions(store);
        }
    }

    /** 验证无意图、原文被改写、跨请求和跨来源意图均不会产生数据库动作。 */
    @Test void forgedOrReusedIntentsCannotStartAppend() {
        try (var control = ExecutionControl.create()) {
            var service = service(Runnable::run);
            rejected(MemoryAccessException.Reason.INTENT_REQUIRED, service.append(context, "动作", null, value("我喜欢茶"), control));
            rejected(MemoryAccessException.Reason.INTENT_REQUIRED, service.append(context, "动作", intent(), value("我喜欢咖啡"), control));
            var changed = new MemoryAccessContext(UUID.randomUUID(), actor, reference);
            rejected(MemoryAccessException.Reason.INTENT_REQUIRED, service.append(changed, "动作", intent(), value(intent().text()), control));
            verifyNoInteractions(store);
        }
    }

    /** 验证主体、原请求与动作来源只由宿主固定，同请求/动作派生稳定操作 ID。 */
    @Test void appendFixesSelfSubjectAndStableRequestActionIdentity() {
        when(store.append(any(), any())).thenAnswer(call -> CompletableFuture.completedFuture(receipt(call.getArgument(0))));
        try (var control = ExecutionControl.create()) {
            var service = service(Runnable::run); var intent = intent();
            var first = service.append(context, "工具动作一", intent, value(intent.text()), control).toCompletableFuture().join();
            var replay = service.append(context, "工具动作一", intent, value(intent.text()), control).toCompletableFuture().join();
            var other = service.append(context, "工具动作二", intent, value(intent.text()), control).toCompletableFuture().join();
            assertEquals(first, replay); assertNotEquals(first.recordId(), other.recordId());
            var captured = ArgumentCaptor.forClass(MemoryFactAppend.class); verify(store, times(3)).append(captured.capture(), any());
            var command = captured.getValue(); assertEquals(user, command.subjectId()); assertEquals(context.requestId(), command.source().requestId());
            assertEquals(actor, command.source().actor()); assertEquals(reference, command.reference()); assertEquals("工具动作二", command.source().actionId());
            assertEquals(captured.getAllValues().get(0), captured.getAllValues().get(1)); assertFalse(new ObjectMapper().writeValueAsString(first).contains("原消息"));
        }
    }

    /** 验证共享查询不强制当前用户过滤，画像查询其他主体也不会获得其他主体写权限。 */
    @Test void sharedRecallAndOtherSubjectProfilesRetainOriginalCoverage() {
        var limited = new MemoryRecallResult(reference, now, List.of(), 5, 0, Set.of(MemoryRecallResult.Limit.SCAN), 2);
        when(recall.recall(any())).thenReturn(CompletableFuture.completedFuture(limited));
        var other = UUID.randomUUID(); var request = new UserProfileRequest(reference, other, MemoryRecallRequest.Use.TOOL, null);
        var view = UserProfileView.from(request, limited); when(profiles.read(any())).thenReturn(CompletableFuture.completedFuture(view));
        try (var control = ExecutionControl.create()) {
            var service = service(Runnable::run); var result = service.recall(context, query, MemoryRecallRequest.Use.TOOL, null, control).toCompletableFuture().join();
            assertSame(limited, result); assertFalse(result.definitivelyEmpty()); assertSame(view, service.profile(context, other, request.use(), null, control).toCompletableFuture().join());
            var capture = ArgumentCaptor.forClass(MemoryRecallRequest.class); verify(recall).recall(capture.capture()); assertNull(capture.getValue().query().subjectId());
            assertNull(capture.getValue().query().sourceScope()); verifyNoInteractions(store);
        }
    }

    /** 验证只有真实提交阶段完成才返回成功，回滚错误不泄漏原文或被伪装成接受。 */
    @Test void appendWaitsForCommitAndSanitizesRollback() {
        var pending = new CompletableFuture<MemoryFactReceipt>(); when(store.append(any(), any())).thenReturn(pending);
        try (var control = ExecutionControl.create()) {
            var future = service(Runnable::run).append(context, "动作", intent(), value(intent().text()), control).toCompletableFuture();
            assertFalse(future.isDone()); pending.completeExceptionally(new IllegalStateException("敏感原文与密钥"));
            rejected(MemoryAccessException.Reason.UNAVAILABLE, future);
        }
    }

    /** 验证取消发生在引用等待时，迟到允许不能开始事实追加。 */
    @Test void cancelledReferenceWaitCannotStartLateWrite() {
        var pending = new CompletableFuture<Void>(); when(references.verify(any())).thenReturn(pending);
        try (var control = ExecutionControl.create()) {
            var future = service(Runnable::run).append(context, "动作", intent(), value(intent().text()), control);
            control.cancel(); rejected(MemoryAccessException.Reason.STOPPED, future); pending.complete(null); verifyNoInteractions(store); assertFalse(pending.isCancelled());
        }
    }

    /** 验证只关闭观察 Future 不撤销写入，真实控制取消后迟到提交也不作为成功交付。 */
    @Test void observerCancellationDoesNotCancelTransactionAndControlStopsDelivery() {
        var pending = new CompletableFuture<MemoryFactReceipt>(); var command = new AtomicReference<MemoryFactAppend>();
        when(store.append(any(), any())).thenAnswer(call -> { command.set(call.getArgument(0)); return pending; });
        try (var control = ExecutionControl.create()) {
            var service = service(Runnable::run); var observed = service.append(context, "动作", intent(), value(intent().text()), control).toCompletableFuture();
            assertTrue(observed.cancel(true)); assertFalse(pending.isCancelled()); control.cancel(); pending.complete(receipt(command.get()));
            assertTrue(observed.isCancelled()); assertFalse(pending.isCancelled()); assertTrue(pending.isDone());
        }
    }

    /** 验证已经开始的写入不能假装回滚；停止只结束本次交付，原事务仍可完成。 */
    @Test void cancellationDuringWriteRetainsActualTransaction() {
        var pending = new CompletableFuture<MemoryFactReceipt>(); var command = new AtomicReference<MemoryFactAppend>();
        when(store.append(any(), any())).thenAnswer(call -> { command.set(call.getArgument(0)); return pending; });
        try (var control = ExecutionControl.create()) {
            var future = service(Runnable::run).append(context, "动作", intent(), value(intent().text()), control);
            control.cancel(); rejected(MemoryAccessException.Reason.STOPPED, future); pending.complete(receipt(command.get())); assertFalse(pending.isCancelled());
            verify(references, times(1)).verify(reference);
        }
    }

    /** 验证等待引用期间关闭新增后，完成校验也不能启动写入。 */
    @Test void switchOffDuringReferenceWaitBlocksWrite() {
        var pending = new CompletableFuture<Void>(); when(references.verify(any())).thenReturn(pending);
        try (var control = ExecutionControl.create()) {
            var future = service(Runnable::run).append(context, "动作", intent(), value(intent().text()), control);
            policy.set(new MemoryAccessPolicy(true, true, false, false)); pending.complete(null); rejected(MemoryAccessException.Reason.ADD_DISABLED, future); verifyNoInteractions(store);
        }
    }

    /** 验证读取期间注销或关闭读取会阻止迟到结果交付，不返回旧画像或伪空结果。 */
    @Test void dynamicDisableRejectsLateReadResult() {
        var pending = new CompletableFuture<MemoryRecallResult>(); when(recall.recall(any())).thenReturn(pending);
        try (var control = ExecutionControl.create()) {
            var future = service(Runnable::run).recall(context, query, MemoryRecallRequest.Use.INJECTION, null, control);
            policy.set(new MemoryAccessPolicy(true, false, true, false)); pending.complete(empty()); rejected(MemoryAccessException.Reason.READ_DISABLED, future);
        }
    }

    /** 验证交付前引用变化保留失效类别，不能把旧结果的引用刷新成当前空间。 */
    @Test void changedReferenceRejectsFinalDelivery() {
        when(recall.recall(any())).thenReturn(CompletableFuture.completedFuture(empty()));
        when(references.verify(any())).thenReturn(CompletableFuture.completedFuture(null))
                .thenReturn(CompletableFuture.failedFuture(new AgentMemoryReferenceException(AgentMemoryReferenceException.Reason.CHANGED)));
        try (var control = ExecutionControl.create()) {
            var error = assertInstanceOf(AgentMemoryReferenceException.class, failure(service(Runnable::run).recall(context, query, MemoryRecallRequest.Use.TOOL, null, control)));
            assertEquals(AgentMemoryReferenceException.Reason.CHANGED, error.reason()); verify(references, never()).resolve(any());
        }
    }

    /** 验证来源完成线程只负责入队，策略和后续业务回到明确业务域；调度两处拒绝都能完成。 */
    @Test void asynchronousBoundariesUseBusinessExecutorAndHandleRejection() {
        var queue = new ArrayDeque<Runnable>(); var pending = new CompletableFuture<Void>(); when(references.verify(any())).thenReturn(pending);
        try (var control = ExecutionControl.create()) {
            var future = service(queue::add).append(context, "动作", intent(), value(intent().text()), control);
            verify(references, never()).verify(any()); queue.remove().run(); queue.remove().run(); assertTrue(queue.isEmpty());
            pending.complete(null); verifyNoInteractions(store); assertEquals(1, queue.size());
            policy.set(new MemoryAccessPolicy(false, true, true, false)); queue.remove().run(); rejected(MemoryAccessException.Reason.DISABLED, future);
            Executor rejectedExecutor = ignored -> { throw new RejectedExecutionException("敏感线程配置"); };
            rejected(MemoryAccessException.Reason.UNAVAILABLE, service(rejectedExecutor).recall(context, query, MemoryRecallRequest.Use.TOOL, null, control));
        }
        when(references.verify(any())).thenReturn(CompletableFuture.completedFuture(null)); var count = new AtomicInteger();
        Executor laterReject = task -> { if (count.incrementAndGet() == 2) { throw new RejectedExecutionException("敏感线程配置"); } task.run(); };
        policy.set(new MemoryAccessPolicy(true, true, true, false));
        try (var control = ExecutionControl.create()) { rejected(MemoryAccessException.Reason.UNAVAILABLE, service(laterReject).recall(context, query, MemoryRecallRequest.Use.TOOL, null, control)); }
    }

    /** 验证错误空间的来源结果整体失败，保留已知代次/配置类别并丢弃原附加异常。 */
    @Test void malformedSourcesAndKnownFailuresRemainDistinctAndSafe() {
        var wrong = new MemoryRecallResult(new MemoryNamespaceReference(scope, new MemoryNamespaceId("other"), 2, 0), now, List.of(), 0, 0, Set.of(), 2);
        when(recall.recall(any())).thenReturn(CompletableFuture.completedFuture(wrong));
        try (var control = ExecutionControl.create()) {
            var service = service(Runnable::run); rejected(MemoryAccessException.Reason.UNAVAILABLE, service.recall(context, query, MemoryRecallRequest.Use.TOOL, null, control));
            var original = new MemoryFactGenerationException(false); original.addSuppressed(new IllegalStateException("敏感旧原文"));
            when(recall.recall(any())).thenReturn(CompletableFuture.failedFuture(original));
            var clean = assertInstanceOf(MemoryFactGenerationException.class, failure(service.recall(context, query, MemoryRecallRequest.Use.TOOL, null, control)));
            assertNotSame(original, clean); assertFalse(clean.missingNamespace()); assertEquals(0, clean.getSuppressed().length);
        }
    }

    /** 验证替换来源的空阶段、空结果、错误主体及显式预算超限均整体失败。 */
    @Test void nullSourcesWrongSubjectAndOversizedResultsCannotBeDelivered() {
        try (var control = ExecutionControl.create()) {
            var service = service(Runnable::run);
            rejected(MemoryAccessException.Reason.UNAVAILABLE, service.recall(context, query, MemoryRecallRequest.Use.TOOL, null, control));
            when(recall.recall(any())).thenReturn(CompletableFuture.completedFuture(null));
            rejected(MemoryAccessException.Reason.UNAVAILABLE, service.recall(context, query, MemoryRecallRequest.Use.TOOL, null, control));
            var excessive = new MemoryRecallResult(reference, now, List.of(), 5, 0, Set.of(MemoryRecallResult.Limit.SCAN), 2);
            when(recall.recall(any())).thenReturn(CompletableFuture.completedFuture(excessive));
            var limits = new MemoryRecallLimits(1, 2, 1);
            rejected(MemoryAccessException.Reason.UNAVAILABLE, service.recall(context, query, MemoryRecallRequest.Use.TOOL, limits, control));
            var other = UUID.randomUUID(); var request = new UserProfileRequest(reference, other, MemoryRecallRequest.Use.TOOL, null);
            when(profiles.read(any())).thenReturn(CompletableFuture.completedFuture(UserProfileView.from(request, empty())));
            rejected(MemoryAccessException.Reason.UNAVAILABLE, service.profile(context, user, MemoryRecallRequest.Use.TOOL, null, control));
            when(profiles.read(any())).thenReturn(CompletableFuture.completedFuture(UserProfileView.from(request, excessive)));
            rejected(MemoryAccessException.Reason.UNAVAILABLE, service.profile(context, other, MemoryRecallRequest.Use.TOOL, limits, control));
        }
    }

    /** 验证追加来源返回错误主体/空间或内容时不能伪造成功，管理审计对象不会进入普通回执。 */
    @Test void wrongAppendReceiptFailsInsteadOfReportingSuccess() {
        when(store.append(any(), any())).thenAnswer(call -> {
            MemoryFactAppend original = call.getArgument(0);
            return CompletableFuture.completedFuture(new MemoryFactReceipt(MemoryFactReceipt.Status.STORED,
                    new MemoryFactRecord(original.operationId(), reference.namespaceId(), 0, 0, UUID.randomUUID(), original.value(), original.source(), now, now, false)));
        });
        try (var control = ExecutionControl.create()) {
            rejected(MemoryAccessException.Reason.UNAVAILABLE, service(Runnable::run).append(context, "动作", intent(), value(intent().text()), control));
        }
    }

    /** 验证策略故障不按宽松默认值继续，错误为固定配置类别。 */
    @Test void policyFailureIsConfigurationErrorAndRecoverable() {
        var broken = new MemoryAccessService(ignored -> { throw new IllegalStateException("敏感配置"); }, references, recall, profiles,
                new MemoryFacts(store, Clock.systemUTC()), Runnable::run);
        try (var control = ExecutionControl.create()) {
            rejected(MemoryAccessException.Reason.CONFIGURATION_ERROR, broken.recall(context, query, MemoryRecallRequest.Use.TOOL, null, control));
            when(recall.recall(any())).thenReturn(CompletableFuture.completedFuture(empty()));
            assertTrue(service(Runnable::run).recall(context, query, MemoryRecallRequest.Use.TOOL, null, control).toCompletableFuture().join().definitivelyEmpty());
        }
    }

    /** 验证已过期阶段明确超时且不访问任何业务端口，宿主仍拥有调度器。 */
    @Test void deadlineStopsAccessBeforeAnyAction() {
        try (var scheduler = Executors.newSingleThreadScheduledExecutor();
             var control = ExecutionControl.withDeadline(new ResourceBudget(Map.of()), now.minusSeconds(1), Clock.fixed(now, ZoneOffset.UTC), scheduler)) {
            rejected(MemoryAccessException.Reason.TIMED_OUT, service(Runnable::run).append(context, "动作", intent(), value(intent().text()), control));
            verifyNoInteractions(store); verify(references, never()).verify(any()); assertFalse(scheduler.isShutdown());
        }
    }
}
