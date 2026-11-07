package org.zexnocs.teanekoagent.component.memory.namespace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.zexnocs.teanekoagent.component.llm.framework.message.LLMAssistantMessage;
import org.zexnocs.teanekoagent.component.llm.framework.message.content.LLMContentListBuilder;
import org.zexnocs.teanekoagent.component.llm.framework.structured.LLMJsonTestSupport;
import org.zexnocs.teanekoagent.component.memory.history.IConversationHistory;
import org.zexnocs.teanekoagent.component.memory.runtime.IAgentMemoryReferences;
import org.zexnocs.teanekoagent.component.personality.AgentPersonalitySnapshot;
import org.zexnocs.teanekoagent.component.personality.IAgentPersonalitySource;
import org.zexnocs.teanekoagent.component.proactive.adapter.ProactiveService;
import org.zexnocs.teanekoagent.component.proactive.adapter.ProactiveTargets;
import org.zexnocs.teanekoagent.integration.command.EasyDataCommandAttemptStore;
import org.zexnocs.teanekoagent.integration.config.AgentScopeConfigData;
import org.zexnocs.teanekoagent.integration.config.AgentScopeConfigManager;
import org.zexnocs.teanekoagent.integration.message.AgentAutomaticAccess;
import org.zexnocs.teanekoagent.runtime.AgentActivity;
import org.zexnocs.teanekoagent.runtime.AgentFactory;
import org.zexnocs.teanekoagent.runtime.AgentRegistry;
import org.zexnocs.teanekoagent.runtime.AgentRunExecution;
import org.zexnocs.teanekoagent.runtime.AgentRunRequest;
import org.zexnocs.teanekoagent.runtime.AgentRunResult;
import org.zexnocs.teanekoagent.runtime.IAgentTurnService;
import org.zexnocs.teanekoagent.runtime.monitor.AgentRuntimeMonitor;
import org.zexnocs.teanekoagent.runtime.trigger.AgentTriggerGate;
import org.zexnocs.teanekoapp.client.TeaNekoClientScanner;
import org.zexnocs.teanekoapp.client.api.ITeaNekoClient;
import org.zexnocs.teanekoapp.client.api.TeaNekoClient;
import org.zexnocs.teanekoapp.client.tools.IMessageSenderTools;
import org.zexnocs.teanekoapp.client.tools.ITeaNekoToolbox;
import org.zexnocs.teanekoapp.response.api.IMessageSendResponseData;
import org.zexnocs.teanekoapp.sender.api.sender_box.IEasyMessageSenderBuilder;
import org.zexnocs.teanekoapp.teauser.interfaces.ITeaUserService;
import org.zexnocs.teanekoapp.utils.TeaNekoScopeService;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataService;
import org.zexnocs.teanekocore.framework.pair.IndependentPair;
import org.zexnocs.teanekocore.logger.ILogger;

import java.time.Clock;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 无记忆装配的主动活动组合验收，仅替换模型结果和平台网络，保留真实发送与持久防重。
 * <br>2026/11/06：覆盖有文本生成、平台确认、重建宿主防重及无文本弃言。
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.3-l
 * @version 4.8.3-l
 */
@Timeout(15)
class ProactiveWithoutMemoryTest {
    /**
     * 无 memory 仍可发送有依据的主动接话，重建宿主不会重发同一发生，空上下文不调用模型。
     * @throws Exception 当有界离线流程失败时
     */
    @Test
    void sendsAndDeduplicatesAcrossRestartWithoutMemory() throws Exception {
        try (var db = new NamespaceDatabaseFixture(); var scheduler = Executors.newSingleThreadScheduledExecutor();
             var registry = new AgentRegistry()) {
            var logger = mock(ILogger.class);
            var configs = mock(IConfigDataService.class);
            var manager = new AgentScopeConfigManager();
            var config = new AgentScopeConfigData();
            config.setMode("ACTIVE");
            config.setComponents("{\"version\":1,\"components\":{\"llm\":{},\"proactive\":{}}}");
            when(configs.getConfigData(eq(manager), eq(AgentScopeConfigData.class), anyString())).thenReturn(Optional.of(config));
            var client = mock(ITeaNekoClient.class);
            var clients = mock(TeaNekoClientScanner.class);
            // 2026/11/06：目录查询夹具提供不可变快照，不绕过正式扫描器修改注册表。
            doReturn(java.util.Map.of("offline", IndependentPair.of(mock(TeaNekoClient.class), client))).when(clients).getHandlerMap();
            doAnswer(call -> clients.getHandlerMap().get(call.getArgument(0))).when(clients).getPair(anyString());
            when(client.getClientId()).thenReturn("offline");
            var toolbox = mock(ITeaNekoToolbox.class);
            when(client.getTeaNekoToolbox()).thenReturn(toolbox);
            when(toolbox.getSelfPlatformId()).thenReturn(Optional.of("机器人"));
            var messages = mock(IMessageSenderTools.class);
            when(toolbox.getMessageSenderTools()).thenReturn(messages);
            var sender = mock(IEasyMessageSenderBuilder.class, RETURNS_SELF);
            when(messages.getGroupBuilder("42")).thenReturn(sender);
            var response = mock(IMessageSendResponseData.class);
            when(response.getMessageId()).thenReturn("确认消息ID");
            when(sender.sendWithFuture()).thenAnswer(ignored ->
                new TaskFuture<>(logger, "离线发送", CompletableFuture.completedFuture(response)));
            var targets = new ProactiveTargets(clients, new TeaNekoScopeService(clients), mock(ITeaUserService.class));
            var target = targets.group("offline-group@42");
            var references = mock(IAgentMemoryReferences.class);
            var history = mock(IConversationHistory.class);
            var personality = mock(IAgentPersonalitySource.class);
            var basis = AgentPersonalitySnapshot.absent(target.scope());
            when(personality.resolve(any(), any())).thenReturn(CompletableFuture.completedStage(basis));
            when(personality.verify(any(), any(), any())).thenReturn(CompletableFuture.completedStage(null));
            var turns = mock(IAgentTurnService.class);
            when(turns.prepare(any(), any(), any())).thenAnswer(call -> {
                AgentRunRequest request = call.getArgument(1);
                assertNull(request.memoryReference());
                assertEquals(AgentActivity.PROACTIVE, request.activity());
                var result = mock(AgentRunResult.class);
                when(result.status()).thenReturn(AgentRunResult.Status.SUCCESS);
                when(result.answer()).thenReturn(Optional.of(LLMAssistantMessage.builder().contents(new LLMContentListBuilder()
                    .addText("{\"speak\":true,\"text\":\"我们一起看看吧\"}").build()).build()));
                var completed = new CompletableFuture<AgentRunResult>();
                return new AgentRunExecution(completed, () -> completed.complete(result), () -> completed.cancel(false), control -> false);
            });
            when(turns.verifyPromptReplay(any(), any(), any(), any())).thenReturn(CompletableFuture.completedStage(true));
            for (int restart = 0; restart < 3; restart++) {
                var triggers = new AgentTriggerGate(ignored -> new AgentTriggerGate.Settings(config.toPolicy(), "UTC", 1, 0, 0), Clock.systemUTC());
                try (var service = new ProactiveService(configs, manager, targets, references, personality, history,
                    new EasyDataCommandAttemptStore(db.data, db.mapper), turns, new AgentFactory(registry), registry,
                    new AgentAutomaticAccess(triggers), triggers, mock(AgentRuntimeMonitor.class), scheduler,
                    Runnable::run, logger, db.mapper, LLMJsonTestSupport.create(
                    mock(org.zexnocs.teanekoagent.component.llm.framework.interaction.ILLMInteractionService.class), db.mapper))) {
                    var outcome = service.candidate(target, AgentTriggerGate.Kind.INTERJECTION,
                            restart < 2 ? "同一发生" : "空资料发生", restart < 2 ? "大家一起看这个问题" : "")
                        .toCompletableFuture().get(4, TimeUnit.SECONDS);
                    assertEquals(restart == 0 ? AgentRuntimeMonitor.Delivery.SENT : restart == 1
                        ? AgentRuntimeMonitor.Delivery.SKIPPED : AgentRuntimeMonitor.Delivery.ABSTAINED, outcome);
                }
            }
            verify(turns, times(1)).prepare(any(), any(), any());
            verify(sender, times(1)).addTextMessage("我们一起看看吧");
            verify(sender, times(1)).sendWithFuture();
            verifyNoInteractions(references, history);
        }
    }
}
