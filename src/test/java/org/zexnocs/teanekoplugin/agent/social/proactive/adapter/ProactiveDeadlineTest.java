package org.zexnocs.teanekoplugin.agent.social.proactive.adapter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.ArgumentCaptor;
import org.zexnocs.teanekoagent.integration.config.AgentScopeConfigData;
import org.zexnocs.teanekoagent.integration.config.AgentScopeConfigManager;
import org.zexnocs.teanekoagent.integration.message.AgentAutomaticAccess;
import org.zexnocs.teanekoagent.llm.framework.interaction.InteractionRuntime;
import org.zexnocs.teanekoagent.llm.framework.structured.LLMJsonService;
import org.zexnocs.teanekoagent.runtime.AgentScope;
import org.zexnocs.teanekoagent.runtime.IAgentFactory;
import org.zexnocs.teanekoagent.runtime.IAgentRegistry;
import org.zexnocs.teanekoagent.runtime.IAgentTurnService;
import org.zexnocs.teanekoagent.runtime.monitor.AgentRuntimeMonitor;
import org.zexnocs.teanekoagent.runtime.operation.IAgentOperationStore;
import org.zexnocs.teanekoagent.runtime.trigger.AgentTriggerGate;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataService;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekoplugin.agent.cognition.memory.history.IConversationHistory;
import org.zexnocs.teanekoplugin.agent.cognition.memory.runtime.IAgentMemoryReferences;
import org.zexnocs.teanekoplugin.agent.cognition.personality.AgentPersonalitySnapshot;
import org.zexnocs.teanekoplugin.agent.cognition.personality.IAgentPersonalitySource;
import org.zexnocs.teanekoplugin.agent.social.proactive.ProactiveComponent;
import org.zexnocs.teanekoplugin.contract.agent.AgentComponentFixtures;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 通过真实组件配置验证自主候选时限和停用排空，不发起模型或平台请求。
 * <br>2026/11/10：手动推进计时回调，迟到资料不能进入生成或发送。
 *
 * @author zExNocs
 * @date 2026/11/10
 * @since 4.9.1-l
 * @version 4.9.1-l
 */
@Timeout(10)
class ProactiveDeadlineTest {
    /**
     * 自定义七十五秒覆盖旧三十秒控制，超时后仍等待实际资料工作结束再完成插件停用。
     * @throws Exception 当有界结果等待失败时
     */
    @Test
    void configuredDeadlineFencesLatePreparationAndDrainsStop() throws Exception {
        var runtime = new InteractionRuntime();
        var mapper = new ObjectMapper();
        var config = new AgentScopeConfigData();
        config.setMode("ACTIVE");
        config.setComponents("{\"version\":1,\"components\":{\"llm\":{},\"proactive\":{\"proactiveTimeoutSeconds\":75}}}");
        var configs = mock(IConfigDataService.class);
        var manager = new AgentScopeConfigManager();
        when(configs.getConfigData(eq(manager), eq(AgentScopeConfigData.class), anyString()))
                .thenReturn(Optional.of(config));
        var target = new ProactiveTargets.Target(new AgentScope("offline", "group"), "42", true, "bot");
        var targets = mock(ProactiveTargets.class);
        var personalities = mock(IAgentPersonalitySource.class);
        var preparation = new CompletableFuture<AgentPersonalitySnapshot>();
        when(personalities.resolve(any(), any())).thenReturn(preparation);
        var turns = mock(IAgentTurnService.class);
        var references = mock(IAgentMemoryReferences.class);
        var history = mock(IConversationHistory.class);
        var attempts = mock(IAgentOperationStore.class);
        var triggers = new AgentTriggerGate(ignored -> new AgentTriggerGate.Settings(
                config.toPolicy(), true, "UTC", 1, 0, 0), Clock.systemUTC());
        var logger = mock(ILogger.class);
        try (var service = new ProactiveService(AgentComponentFixtures.proactive(configs, manager),
                AgentComponentFixtures.memory(configs, manager), targets, references, personalities, history,
                attempts, turns, mock(IAgentFactory.class), mock(IAgentRegistry.class),
                new AgentAutomaticAccess(triggers), triggers, mock(AgentRuntimeMonitor.class),
                runtime.scheduler, Runnable::run, logger, mapper, mock(LLMJsonService.class))) {
            service.start().toCompletableFuture().join();
            var candidate = service.candidate(target, AgentTriggerGate.Kind.INTERJECTION, "occurrence", "合成文本")
                    .toCompletableFuture();
            var control = ArgumentCaptor.forClass(ExecutionControl.class);
            verify(personalities).resolve(eq(target.scope()), control.capture());
            var remaining = control.getValue().remainingTime().orElseThrow().toSeconds();
            assertTrue(remaining > 60 && remaining <= 75);
            runtime.advance(Duration.ofSeconds(31));
            assertDoesNotThrow(control.getValue()::checkActive);
            runtime.advance(Duration.ofSeconds(44));
            assertFalse(candidate.isDone());
            var stopping = service.stop().toCompletableFuture();
            assertFalse(stopping.isDone());
            preparation.complete(AgentPersonalitySnapshot.absent(target.scope()));
            assertEquals(AgentRuntimeMonitor.Delivery.FAILED, candidate.get(2, TimeUnit.SECONDS));
            stopping.get(2, TimeUnit.SECONDS);
            verifyNoInteractions(turns, references, history, attempts);
            verify(targets, never()).send(any(), anyString(), any());
            var diagnostic = ArgumentCaptor.forClass(String.class);
            verify(logger).warn(eq("Agent 自主发言"), diagnostic.capture());
            assertTrue(diagnostic.getValue().contains("阶段=记忆与人格准备"));
            assertTrue(diagnostic.getValue().contains("候选总时限秒=75"));
            assertFalse(diagnostic.getValue().contains("原因=原因="));
            assertEquals(0, runtime.pendingTimers());
        }
    }

    /** 组件拒绝无界和类型错误的时限，同时允许旧配置省略新参数。 */
    @Test
    void componentRejectsInvalidDeadline() {
        var component = new ProactiveComponent();
        var mapper = new ObjectMapper();
        assertDoesNotThrow(() -> component.validate(mapper.readTree("{}")));
        for (String value : new String[]{"0", "181", "\"90\""}) {
            assertThrows(IllegalArgumentException.class,
                    () -> component.validate(mapper.readTree("{\"proactiveTimeoutSeconds\":" + value + "}")));
        }
    }
}
