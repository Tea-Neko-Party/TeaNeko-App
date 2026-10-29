package org.zexnocs.teanekoagent.agent.conversation;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.zexnocs.teanekoagent._app_config.AgentConversationConfig;
import org.zexnocs.teanekoagent._app_config.SpringAgentConversationDefaults;
import org.zexnocs.teanekoagent._app_config.SpringAgentQueueDefaults;
import org.zexnocs.teanekoagent.agent.*;
import org.zexnocs.teanekoagent.llm.framework.interaction.InteractionRuntime;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptionsSnapshot;
import org.zexnocs.teanekocore.logger.ILogger;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 验证排队及会话保留配置懒读取、范围和脱敏失败，不启动应用或数据库。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
class AgentQueueDefaultsTest {
    /** 验证会话默认容量和有效期独立于排队，热更新不修改旧快照。 */
    @Test void conversationSettingsAreLazyAndReloadedIndependently() {
        var environment = new MockEnvironment(); var defaults = new SpringAgentConversationDefaults(environment);
        var original = defaults.resolve(); assertEquals(AgentConversationSettings.defaults(), original);
        environment.setProperty("tea-neko.agent.conversation.max-conversations", "12");
        environment.setProperty("tea-neko.agent.conversation.idle-ttl-seconds", "60");
        assertEquals(new AgentConversationSettings(12, Duration.ofSeconds(60)), defaults.resolve());
        assertEquals(1000, original.maxConversations()); assertEquals(Duration.ofMinutes(30), original.idleTtl());
    }

    /** 验证非法会话保留属性不影响构造，异常不包含原值，修正后恢复。 */
    @Test void conversationConfigurationFailureIsRedactedAndRecoverable() {
        var environment = new MockEnvironment().withProperty("tea-neko.agent.conversation.max-conversations", "敏感原值");
        var defaults = assertDoesNotThrow(() -> new SpringAgentConversationDefaults(environment));
        var failure = assertThrows(IllegalStateException.class, defaults::resolve);
        assertFalse(failure.getMessage().contains("敏感原值")); assertNull(failure.getCause());
        environment.setProperty("tea-neko.agent.conversation.max-conversations", "1000");
        assertEquals(AgentConversationSettings.defaults(), defaults.resolve());
        environment.setProperty("tea-neko.agent.conversation.idle-ttl-seconds", "0"); assertThrows(IllegalStateException.class, defaults::resolve);
    }

    /** 验证会话容量和有效期均为正，防止无上限缓存或立即失效的隐式配置。 */
    @Test void conversationSettingsRejectInvalidBounds() {
        assertThrows(IllegalArgumentException.class, () -> new AgentConversationSettings(0, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> new AgentConversationSettings(-1, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> new AgentConversationSettings(1, Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new AgentConversationSettings(1, Duration.ofSeconds(-1)));
        assertThrows(NullPointerException.class, () -> new AgentConversationSettings(1, null));
    }

    /** 验证保留配置故障只拒绝新请求，不建立执行或计时资源，清理入口同样脱敏失败。 */
    @Test void unavailableConversationPolicyDoesNotAdmitWork() {
        var runtime = new InteractionRuntime(); var backend = mock(IAgentTurnService.class);
        var environment = new MockEnvironment().withProperty("tea-neko.agent.conversation.max-conversations", "0");
        try (var registry = new AgentRegistry(); var service = new AgentConversationService(backend, AgentQueueSettings::defaults,
                runtime, runtime.scheduler, runtime.clock, mock(ILogger.class), new SpringAgentConversationDefaults(environment))) {
            var handle = new AgentFactory(registry).create(new AgentDefinition("test", new LLMModelOptionsSnapshot(null), Set.of(AgentActivity.SERVICE)));
            var actor = new AgentActorContext(new AgentScope("客户端", "群"), AgentActorContext.Origin.USER,
                    Optional.of(UUID.randomUUID()), "用户", "来源", "目标");
            var request = new AgentRunRequest(UUID.randomUUID(), actor, AgentActivity.SERVICE, List.of());
            assertEquals(AgentRunResult.Status.CONFIGURATION_ERROR, service.run(handle, request).toCompletableFuture().join().status());
            assertTrue(service.snapshot(handle, actor.scope()).isEmpty()); assertThrows(IllegalStateException.class, service::purgeIdle);
            verifyNoInteractions(backend); assertEquals(0, runtime.pendingTimers());
            environment.setProperty("tea-neko.agent.conversation.max-conversations", "2"); assertEquals(0, service.purgeIdle());
        }
    }

    /** 验证默认 Bean 明确经过邮箱，而独立轮次类型不会同时匹配默认服务注入。 */
    @Test void defaultServiceAssemblyRequiresMailboxAndDoesNoWork() {
        var port = mock(IAgentTurnService.class); var defaults = mock(IAgentQueueDefaults.class);
        var conversations = mock(IAgentConversationDefaults.class);
        var policy = mock(IAgentConversationPolicy.class);
        var executor = mock(ExecutorService.class); var scheduler = mock(ScheduledExecutorService.class);
        var built = new AgentConversationConfig().agentService(port, defaults, conversations, policy, new ObjectMapper(),
                executor, scheduler, mock(ILogger.class), mock(org.zexnocs.teanekoagent.agent.memory.IAgentMemoryReferences.class));
        assertInstanceOf(AgentConversationService.class, built);
        assertInstanceOf(IAgentConversationControl.class, built);
        built.close();
        verifyNoInteractions(port, defaults, conversations, policy, executor, scheduler);
    }
    /** 验证默认值及下一次调用的配置覆盖，旧快照保持不变。 */
    @Test void defaultAndReloadedSettingsAreIndependent() {
        var environment = new MockEnvironment(); var defaults = new SpringAgentQueueDefaults(environment);
        var original = defaults.resolve(); assertEquals(AgentQueueSettings.defaults(), original);
        environment.setProperty("tea-neko.agent.queue.max-waiting-requests", "0");
        environment.setProperty("tea-neko.agent.queue.timeout-seconds", "12");
        assertEquals(new AgentQueueSettings(0, Duration.ofSeconds(12)), defaults.resolve()); assertEquals(5, original.maxWaiting());
    }

    /** 验证解析失败不阻止构造，不包含原值或异常链，修复后可以恢复。 */
    @Test void invalidValuesAreLazyAndRedacted() {
        var environment = new MockEnvironment().withProperty("tea-neko.agent.queue.timeout-seconds", "敏感原值");
        var defaults = assertDoesNotThrow(() -> new SpringAgentQueueDefaults(environment));
        var failure = assertThrows(IllegalStateException.class, defaults::resolve);
        assertFalse(failure.getMessage().contains("敏感原值")); assertNull(failure.getCause());
        environment.setProperty("tea-neko.agent.queue.timeout-seconds", "30"); assertEquals(AgentQueueSettings.defaults(), defaults.resolve());
        environment.setProperty("tea-neko.agent.queue.max-waiting-requests", "-1"); assertThrows(IllegalStateException.class, defaults::resolve);
    }

    /** 验证非正时限和负数容量被拒绝，零等待容量仍是有效设置。 */
    @Test void queueSettingsEnforceMeaningfulBounds() {
        assertThrows(IllegalArgumentException.class, () -> new AgentQueueSettings(-1, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> new AgentQueueSettings(1, Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new AgentQueueSettings(1, Duration.ofSeconds(-1)));
        assertDoesNotThrow(() -> new AgentQueueSettings(0, Duration.ofSeconds(1)));
    }

    /** 验证非法策略只关闭本次入队，修正后不会遗留失败邮箱或占用槽位。 */
    @Test void configurationFailureDoesNotCreateMailboxOrTurn() {
        var runtime = new InteractionRuntime(); var backend = mock(IAgentTurnService.class);
        var environment = new MockEnvironment().withProperty("tea-neko.agent.queue.timeout-seconds", "0");
        try (var registry = new AgentRegistry(); var service = new AgentConversationService(backend,
                new SpringAgentQueueDefaults(environment), runtime, runtime.scheduler, runtime.clock, mock(ILogger.class))) {
            var handle = new AgentFactory(registry).create(new AgentDefinition("test", new LLMModelOptionsSnapshot(null), Set.of(AgentActivity.SERVICE)));
            var actor = new AgentActorContext(new AgentScope("client", "scope"), AgentActorContext.Origin.USER,
                    Optional.of(UUID.randomUUID()), "user", "source", "target");
            var request = new AgentRunRequest(UUID.randomUUID(), actor, AgentActivity.SERVICE, List.of());
            assertEquals(AgentRunResult.Status.CONFIGURATION_ERROR, service.run(handle, request).toCompletableFuture().join().status());
            verifyNoInteractions(backend); assertEquals(0, runtime.pendingTimers());
            environment.setProperty("tea-neko.agent.queue.timeout-seconds", "30");
            var pending = service.run(handle, request).toCompletableFuture(); runtime.runAll();
            assertEquals(AgentRunResult.Status.FAILED, pending.join().status());
            verify(backend).prepare(eq(handle), argThat(bound -> bound.requestId().equals(request.requestId()) && bound.conversation() != null));
        }
        assertEquals(0, runtime.pendingTimers());
    }
}
