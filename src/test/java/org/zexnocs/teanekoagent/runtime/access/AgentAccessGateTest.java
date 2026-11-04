package org.zexnocs.teanekoagent.runtime.access;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.zexnocs.teanekoagent.component.llm.framework.interaction.LLMInteractionSettings;
import org.zexnocs.teanekoagent.component.llm.framework.model.LLMModelOptionsSnapshot;
import org.zexnocs.teanekoagent.component.llm.framework.model.ModelCapabilities;
import org.zexnocs.teanekoagent.runtime.*;
import org.zexnocs.teanekocore.logger.ILogger;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.zexnocs.teanekoagent.runtime.access.AgentAccessDecision.Reason.*;

/**
 * 验证每类活动都遵守统一登记边界，前置拒绝不读取配置或凭据，也不执行任何业务。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：验证聊天模型覆盖、共享定义不变及准入期间切换仲裁。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.5.0-l
 * @version 4.8.0-l
 */
class AgentAccessGateTest {
    /** 2026/11/04：聊天选择覆盖文件默认，不改变共享定义，读取期间切换仍被策略仲裁拒绝。 */
    @Test
    void selectedModelIsScopedAndCannotChangeDuringAdmission() {
        var selected = new AgentScopePolicy(true, true, AgentScopePolicy.Mode.ACTIVE, "deepseek-chat");
        when(scopes.read(any())).thenReturn(selected);
        assertTrue(gate.evaluate(handle, actor(AgentActivity.COMMAND), AgentActivity.COMMAND).allowed());
        var capture = org.mockito.ArgumentCaptor.forClass(AgentDefinition.class);
        verify(readiness).inspect(capture.capture());
        assertEquals("deepseek-chat", capture.getValue().modelOptions().toOptions().getProvider());
        assertTrue(definition.modelOptions().toOptions().findProvider().isEmpty());
        when(scopes.read(any())).thenReturn(selected,
                new AgentScopePolicy(true, true, AgentScopePolicy.Mode.ACTIVE, "kimi-chat"));
        assertEquals(SCOPE_POLICY_CHANGED, gate.evaluate(handle, actor(AgentActivity.COMMAND), AgentActivity.COMMAND).reason());
    }
    private final AgentRegistry registry = new AgentRegistry();
    private final IAgentScopePolicyPort scopes = mock(IAgentScopePolicyPort.class);
    private final IAgentReadiness readiness = mock(IAgentReadiness.class);
    private final ILogger logger = mock(ILogger.class);
    private final AgentAccessGate gate = new AgentAccessGate(registry, scopes, readiness, logger);
    private final AgentDefinition definition = new AgentDefinition("test", new LLMModelOptionsSnapshot(null), Set.of(AgentActivity.values()));
    private final AgentHandle handle = new AgentFactory(registry).create(definition);
    private final AgentScope scope = new AgentScope("client", "group@test");
    private final AgentRuntimeSnapshot runtime = new AgentRuntimeSnapshot(new LLMModelOptionsSnapshot(null),
            ModelCapabilities.textOnly(), LLMInteractionSettings.defaults());

    /** 固定默认就绪替身，作用域仍必须由每个用例显式登记。 */
    AgentAccessGateTest() {
        when(scopes.read(any())).thenReturn(AgentScopePolicy.unregistered());
        when(readiness.inspect(any())).thenReturn(new AgentReadiness(AgentReadiness.Status.READY, Optional.of(runtime)));
    }

    /** 关闭本用例拥有的实例，不留下关闭监听或可运行句柄。 */
    @AfterEach void closeInstances() { registry.close(); }

    /**
     * 为活动选择真实来源类型，系统活动不携带人类 UUID。
     * @param activity 当前活动
     * @return 不可变宿主身份
     */
    private AgentActorContext actor(AgentActivity activity) {
        boolean system = activity == AgentActivity.PROACTIVE || activity == AgentActivity.SCHEDULED;
        return new AgentActorContext(scope, system ? AgentActorContext.Origin.SYSTEM : AgentActorContext.Origin.USER,
                system ? Optional.empty() : Optional.of(UUID.randomUUID()), system ? "" : "platform-user", "source", "target");
    }

    /**
     * 验证未注册时显式、自动、采集、摘要、自主、定时和工具都被拒绝。
     * @param activity 当前活动
     */
    @ParameterizedTest @EnumSource(AgentActivity.class)
    void unregisteredScopesRejectEveryActivityBeforeReadiness(AgentActivity activity) {
        var decision = gate.evaluate(handle, actor(activity), activity);
        assertEquals(SCOPE_UNREGISTERED, decision.reason());
        assertFalse(decision.allowed()); assertTrue(decision.runtime().isEmpty());
        verifyNoInteractions(readiness);
    }

    /**
     * 验证关闭已注册 scope 同样覆盖全部活动。
     * @param activity 当前活动
     */
    @ParameterizedTest @EnumSource(AgentActivity.class)
    void disabledScopesRejectEveryActivityBeforeReadiness(AgentActivity activity) {
        when(scopes.read(any())).thenReturn(new AgentScopePolicy(true, false, AgentScopePolicy.Mode.ACTIVE));
        assertEquals(SCOPE_DISABLED, gate.evaluate(handle, actor(activity), activity).reason());
        verifyNoInteractions(readiness);
    }

    /**
     * 验证合法登记、定义能力和来源都满足时才给出完整就绪数据。
     * @param activity 当前活动
     */
    @ParameterizedTest @EnumSource(AgentActivity.class)
    void registeredActiveScopeAllowsConfiguredActivities(AgentActivity activity) {
        when(scopes.read(any())).thenReturn(new AgentScopePolicy(true, true, AgentScopePolicy.Mode.ACTIVE));
        var decision = gate.evaluate(handle, actor(activity), activity);
        assertTrue(decision.allowed()); assertSame(runtime, decision.runtime().orElseThrow());
        verify(readiness).inspect(definition);
    }

    /** 验证最低被动模式拒绝自主发言，但不把普通计划任务误当成自主聊天。 */
    @Test void passiveModeSeparatesProactiveMessagesFromScheduledTasks() {
        when(scopes.read(any())).thenReturn(new AgentScopePolicy(true, true, AgentScopePolicy.Mode.PASSIVE_BASIC));
        assertEquals(MODE_RESTRICTED, gate.evaluate(handle, actor(AgentActivity.PROACTIVE), AgentActivity.PROACTIVE).reason());
        assertTrue(gate.evaluate(handle, actor(AgentActivity.SCHEDULED), AgentActivity.SCHEDULED).allowed());
        verify(readiness, times(1)).inspect(definition);
    }

    /** 验证关闭、未拥有和未启用能力的实例不会读取 scope 或就绪配置。 */
    @Test void invalidInstanceOrCapabilityFailsBeforeExternalReads() {
        handle.close();
        assertEquals(INSTANCE_CLOSED, gate.evaluate(handle, actor(AgentActivity.SERVICE), AgentActivity.SERVICE).reason());
        try (var otherRegistry = new AgentRegistry()) {
            var foreign = new AgentFactory(otherRegistry).create(definition);
            assertEquals(INSTANCE_NOT_REGISTERED, gate.evaluate(foreign, actor(AgentActivity.SERVICE), AgentActivity.SERVICE).reason());
        }
        var noActivities = new AgentFactory(registry).create(new AgentDefinition("none", new LLMModelOptionsSnapshot(null), Set.of()));
        assertEquals(ACTIVITY_DISABLED, gate.evaluate(noActivities, actor(AgentActivity.SERVICE), AgentActivity.SERVICE).reason());
        verifyNoInteractions(scopes, readiness);
    }

    /** 验证用户不能伪装系统任务，系统任务也不能携带用户身份调用交互命令入口。 */
    @Test void rejectsForgedOriginAndUserIdentityOnBackgroundTasks() {
        var user = actor(AgentActivity.SERVICE);
        assertEquals(INVALID_ORIGIN, gate.evaluate(handle, user, AgentActivity.PROACTIVE).reason());
        var system = actor(AgentActivity.SCHEDULED);
        assertEquals(INVALID_ORIGIN, gate.evaluate(handle, system, AgentActivity.COMMAND).reason());
        assertThrows(IllegalArgumentException.class, () -> new AgentActorContext(scope, AgentActorContext.Origin.SYSTEM,
                Optional.of(UUID.randomUUID()), "platform-user", "source", "target"));
        assertThrows(IllegalArgumentException.class, () -> new AgentActorContext(scope, AgentActorContext.Origin.USER,
                Optional.empty(), "", "source", "target"));
        verifyNoInteractions(scopes, readiness);
    }

    /** 验证配置后端或日志抛错时仍拒绝，并且结果与日志不包含原始敏感异常。 */
    @Test void scopeFailureAndDiagnosticFailureCannotEnableActivity() {
        when(scopes.read(any())).thenThrow(new IllegalStateException("测试敏感值"));
        doThrow(new IllegalStateException("日志不可用")).when(logger).error(anyString(), anyString());
        var decision = gate.evaluate(handle, actor(AgentActivity.SERVICE), AgentActivity.SERVICE);
        assertEquals(SCOPE_CONFIG_UNAVAILABLE, decision.reason());
        assertFalse(decision.toString().contains("测试敏感值"));
        verify(logger).error(AgentAccessGate.class.getName(), SCOPE_CONFIG_UNAVAILABLE.description());
        verifyNoInteractions(readiness);
    }

    /**
     * 验证未就绪和本地关闭均不会提供可继续执行的快照。
     * @param status 不可用状态
     */
    @ParameterizedTest @EnumSource(value = AgentReadiness.Status.class, names = "READY", mode = EnumSource.Mode.EXCLUDE)
    void readinessFailureDoesNotExposeRunnableSnapshot(AgentReadiness.Status status) {
        when(scopes.read(any())).thenReturn(new AgentScopePolicy(true, true, AgentScopePolicy.Mode.ACTIVE));
        when(readiness.inspect(any())).thenReturn(new AgentReadiness(status, Optional.empty()));
        var decision = gate.evaluate(handle, actor(AgentActivity.SERVICE), AgentActivity.SERVICE);
        assertEquals(status == AgentReadiness.Status.LOCAL_DISABLED ? LOCAL_DISABLED : READINESS_UNAVAILABLE, decision.reason());
        assertTrue(decision.runtime().isEmpty());
    }

    /** 验证自定义就绪端口同步失败也被隔离，不逃逸为调用线程异常。 */
    @Test void readinessPortFailureIsIsolated() {
        when(scopes.read(any())).thenReturn(new AgentScopePolicy(true, true, AgentScopePolicy.Mode.ACTIVE));
        when(readiness.inspect(any())).thenThrow(new IllegalStateException("测试敏感值"));
        assertEquals(READINESS_UNAVAILABLE, gate.evaluate(handle, actor(AgentActivity.SERVICE), AgentActivity.SERVICE).reason());
        verify(logger).error(AgentAccessGate.class.getName(), READINESS_UNAVAILABLE.description());
    }

    /** 验证准备期间关闭实例或注销 scope 时，不交付过期的允许结果。 */
    @Test void rechecksInstanceAndScopeAfterReadiness() {
        var active = new AgentScopePolicy(true, true, AgentScopePolicy.Mode.ACTIVE);
        when(scopes.read(any())).thenReturn(active, AgentScopePolicy.unregistered());
        assertEquals(SCOPE_UNREGISTERED, gate.evaluate(handle, actor(AgentActivity.SERVICE), AgentActivity.SERVICE).reason());
        when(scopes.read(any())).thenReturn(active);
        when(readiness.inspect(any())).thenAnswer(invocation -> {
            handle.close(); return new AgentReadiness(AgentReadiness.Status.READY, Optional.of(runtime));
        });
        assertEquals(INSTANCE_CLOSED, gate.evaluate(handle, actor(AgentActivity.SERVICE), AgentActivity.SERVICE).reason());
    }

    /** 验证配置检查期间即使策略变化后仍允许该活动，也要求重新获取一致快照。 */
    @Test void policyChangeDuringReadinessRequiresFreshAdmission() {
        var first = new AgentScopePolicy(true, true, AgentScopePolicy.Mode.PASSIVE_BASIC);
        var next = new AgentScopePolicy(true, true, AgentScopePolicy.Mode.PASSIVE_CONTEXTUAL);
        when(scopes.read(any())).thenReturn(first, next);
        var decision = gate.evaluate(handle, actor(AgentActivity.SERVICE), AgentActivity.SERVICE);
        assertEquals(SCOPE_POLICY_CHANGED, decision.reason());
        assertTrue(decision.runtime().isEmpty());
        assertTrue(gate.evaluate(handle, actor(AgentActivity.SERVICE), AgentActivity.SERVICE).allowed());
    }
}
