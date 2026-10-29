package org.zexnocs.teanekoagent.memory.namespace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.zexnocs.teanekoagent.adapter.config.AgentScopeConfigData;
import org.zexnocs.teanekoagent.adapter.config.AgentScopeConfigManager;
import org.zexnocs.teanekoagent.adapter.config.AgentScopeFieldChecker;
import org.zexnocs.teanekoagent.adapter.personality.ConfiguredPersonalities;
import org.zexnocs.teanekoagent.adapter.personality.EasyDataPersonalityStore;
import org.zexnocs.teanekoagent.agent.*;
import org.zexnocs.teanekoagent.agent.access.AgentAccessGate;
import org.zexnocs.teanekoagent.agent.access.AgentReadiness;
import org.zexnocs.teanekoagent.agent.access.AgentRuntimeSnapshot;
import org.zexnocs.teanekoagent.agent.access.AgentScopePolicy;
import org.zexnocs.teanekoagent.agent.conversation.*;
import org.zexnocs.teanekoagent.agent.prompt.IAgentPromptSource;
import org.zexnocs.teanekoagent.file_config.personality.AgentPersonalityDefinition;
import org.zexnocs.teanekoagent.file_config.personality.AgentPersonalityFileConfig;
import org.zexnocs.teanekoagent.llm.framework.input.interfaces.ILLMPrompt;
import org.zexnocs.teanekoagent.llm.framework.interaction.LLMInteractionService;
import org.zexnocs.teanekoagent.llm.framework.interaction.LLMInteractionSettings;
import org.zexnocs.teanekoagent.llm.framework.message.LLMAssistantMessage;
import org.zexnocs.teanekoagent.llm.framework.message.LLMUserMessage;
import org.zexnocs.teanekoagent.llm.framework.message.content.LLMContentListBuilder;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.LLMMessageRole;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptions;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptionsSnapshot;
import org.zexnocs.teanekoagent.llm.framework.model.ModelCapabilities;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.ILLMModelService;
import org.zexnocs.teanekoagent.llm.framework.response.LLMChoice;
import org.zexnocs.teanekoagent.llm.framework.response.LLMResult;
import org.zexnocs.teanekoagent.llm.framework.response.LLMUsage;
import org.zexnocs.teanekoagent.personality.PersonalityCorrection;
import org.zexnocs.teanekoagent.personality.PersonalityService;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.actuator.execution.ExecutionPolicy;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataService;
import org.zexnocs.teanekocore.file_config.interfaces.IFileConfigService;
import org.zexnocs.teanekocore.logger.ILogger;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 复用既有事务夹具验证人格实际生效、共享补正、切换回滚和旧轮失效，不增加数据结构镜像测试。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
@Timeout(20)
class PersonalityIntegrationTest {
    private final Instant now = Instant.parse("2026-10-29T12:00:00Z");
    private final AgentScope scope = new AgentScope("客户端", "群甲");

    /**
     * 构造具有真实发起者及来源的当前轮次。
     * @param target 作用域
     * @return 原始请求
     */
    private AgentRunRequest request(AgentScope target) {
        var actor = new AgentActorContext(target, AgentActorContext.Origin.USER, Optional.of(UUID.randomUUID()), "用户", UUID.randomUUID().toString(), "目标");
        return new AgentRunRequest(UUID.randomUUID(), actor, AgentActivity.SERVICE,
                List.of(LLMUserMessage.builder().contents(new LLMContentListBuilder().addText("你好").build()).build()));
    }
    /**
     * 以当前真实消息构造有来源的表达候选。
     * @param request 原请求
     * @param text 表达建议
     * @return 不含个人画像或权限字段的候选
     */
    private PersonalityCorrection correction(AgentRunRequest request, String text) {
        return new PersonalityCorrection(request.requestId(), "teaneko", 1, "speaking detail", text, 0.9, request.actor(), now, null, false);
    }

    /**
     * 组合实际配置适配、事务存储、人格源与 Agent/LLM；仅替换文件/模型传输。
     * @author zExNocs
     * @date 2026/10/29
     * @since 4.0.0
     * @version 4.5.0-l
     */
    private final class Harness implements AutoCloseable {
        final NamespaceDatabaseFixture db = new NamespaceDatabaseFixture();
        final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
        final ScheduledExecutorService timers = Executors.newSingleThreadScheduledExecutor();
        final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        final AgentRegistry registry = new AgentRegistry();
        final AgentHandle handle = new AgentFactory(registry).create(new AgentDefinition("人格验证", new LLMModelOptionsSnapshot(null), Set.of(AgentActivity.values())));
        final Map<String, AgentScopeConfigData> settings = new ConcurrentHashMap<>();
        final AgentPersonalityFileConfig file = new AgentPersonalityFileConfig();
        final EasyDataPersonalityStore store = new EasyDataPersonalityStore(db.repository, db.personalityLocks, db.database, db.mapper);
        final List<ILLMPrompt> prompts = new CopyOnWriteArrayList<>();
        final AtomicReference<AgentConversationOptions> options = new AtomicReference<>(AgentConversationOptions.defaults());
        volatile Runnable beforeReply = () -> {};
        final PersonalityService personality;
        final AgentService turns;
        final AgentConversationService mailbox;

        /** 装配纯离线配置与真实执行，构造时不启动模型。 */
        Harness() {
            var character = new AgentPersonalityDefinition(); character.setId("teaneko"); character.setDisplayName("TeaNeko");
            character.getIdentity().setName("TeaNeko"); character.getCoreTraits().add("文件人格：认真解释，轻松交流。");
            character.getLearningPolicy().getMutableFields().add("speaking detail"); file.setCharacters(List.of(character));
            var files = mock(IFileConfigService.class); when(files.get(AgentPersonalityFileConfig.class)).thenReturn(file);
            var configs = mock(IConfigDataService.class);
            when(configs.getConfigData(any(), eq(AgentScopeConfigData.class), anyString())).thenAnswer(call -> Optional.ofNullable(settings.get(call.<String>getArgument(2))));
            personality = new PersonalityService(new ConfiguredPersonalities(configs, new AgentScopeConfigManager(), files), store, workers, clock, db.mapper);
            var logger = mock(ILogger.class);
            var runtime = new AgentRuntimeSnapshot(new LLMModelOptionsSnapshot(LLMModelOptions.builder().provider("test").model("离线模型").maxTokens(30).build()),
                    ModelCapabilities.textOnly(), LLMInteractionSettings.defaults());
            var gate = new AgentAccessGate(registry, target -> new AgentScopePolicy(settings.containsKey(target.scopeId()),
                    settings.containsKey(target.scopeId()) && settings.get(target.scopeId()).isEnabled(), AgentScopePolicy.Mode.ACTIVE),
                    ignored -> new AgentReadiness(AgentReadiness.Status.READY, Optional.of(runtime)), logger);
            var models = mock(ILLMModelService.class);
            when(models.call(any(ILLMPrompt.class), any(ExecutionPolicy.class))).thenAnswer(call -> {
                prompts.add(call.getArgument(0)); beforeReply.run();
                var answer = LLMAssistantMessage.builder().contents(new LLMContentListBuilder().addText("离线回答").build()).build();
                return new TaskFuture<>(logger, "人格离线模型", CompletableFuture.completedFuture(LLMResult.builder()
                        .usage(LLMUsage.builder().totalTokens(2).build()).choices(List.of(LLMChoice.builder().index(0).finishReason("stop").message(answer).build())).build()));
            });
            turns = new AgentService(gate, new LLMInteractionService(models, LLMInteractionSettings::defaults, workers, timers),
                    (definition, input, config) -> List.of(), db.mapper, workers, timers, clock, logger, null, IAgentPromptSource.empty(), personality);
            mailbox = new AgentConversationService(turns, AgentQueueSettings::defaults, workers, timers, clock, logger,
                    AgentConversationSettings::defaults, (definition, input) -> options.get(), db.mapper, null, personality);
            settings.put(scope.scopeId(), new AgentScopeConfigData());
        }
        /** 关闭自身拥有者，业务服务不关闭借用资源。 */
        @Override public void close() { mailbox.close(); turns.close(); registry.close(); timers.shutdownNow(); workers.close(); db.close(); }
    }

    /** 验证默认人格、候选提交、下一轮生效、数据角色、历史投影、配置切换和授权重放形成完整闭环。 */
    @Test void personaAndCorrectionsReachRealModelWithoutPollutingHistory() {
        try (var h = new Harness(); var control = ExecutionControl.create()) {
            var input = request(scope); var first = h.mailbox.run(h.handle, input).toCompletableFuture().join();
            assertEquals(AgentRunResult.Status.SUCCESS, first.status());
            assertEquals(LLMMessageRole.SYSTEM, h.prompts.getFirst().getMessages().getFirst().getRole());
            assertTrue(h.db.mapper.writeValueAsString(h.prompts.getFirst().getMessages()).contains("TeaNeko"));
            assertTrue(h.db.mapper.writeValueAsString(h.prompts.getFirst().getMessages()).contains("文件人格"));
            var basis = Objects.requireNonNull(first.promptTranscript()).input().personality(); assertNotNull(basis);
            var candidate = correction(input, "表达更简洁，保持温和");
            assertTrue(h.personality.propose(scope, basis, candidate, control).toCompletableFuture().join());
            assertFalse(h.personality.propose(scope, basis, candidate, control).toCompletableFuture().join());
            assertSame(first, h.mailbox.retry(h.handle, input).toCompletableFuture().join());
            var second = h.mailbox.run(h.handle, request(scope)).toCompletableFuture().join(); assertEquals(AgentRunResult.Status.SUCCESS, second.status());
            var actual = h.prompts.getLast().getMessages();
            assertTrue(actual.stream().anyMatch(message -> message.getRole() == LLMMessageRole.USER && h.db.mapper.writeValueAsString(message).contains(candidate.text())));
            assertTrue(actual.stream().filter(message -> message.getRole() == LLMMessageRole.SYSTEM).noneMatch(message -> h.db.mapper.writeValueAsString(message).contains(candidate.text())));
            var window = h.mailbox.snapshot(h.handle, scope).orElseThrow().require(AgentConversationWindow.COMPONENT);
            assertEquals(4, window.messages().size()); assertFalse(h.db.mapper.writeValueAsString(window.messages()).contains(candidate.text()));
            var oldBinding = h.store.binding(scope).toCompletableFuture().join().orElseThrow();
            h.settings.get(scope.scopeId()).setCustomPersonalityEnabled(true); h.settings.get(scope.scopeId()).setCustomPersonality("自定义：擅长解释代码的伙伴。");
            var custom = h.mailbox.run(h.handle, request(scope)).toCompletableFuture().join(); assertEquals(AgentRunResult.Status.SUCCESS, custom.status());
            assertNotEquals(first.conversation(), custom.conversation()); assertEquals(2, h.mailbox.snapshot(h.handle, scope).orElseThrow().require(AgentConversationWindow.COMPONENT).messages().size());
            assertTrue(h.db.mapper.writeValueAsString(h.prompts.getLast().getMessages()).contains("擅长解释代码"));
            var changed = h.store.binding(scope).toCompletableFuture().join().orElseThrow(); assertNotEquals(oldBinding.correctionKey(), changed.correctionKey());
            assertEquals(List.of(candidate), h.store.state(Objects.requireNonNull(changed.backupKey())).toCompletableFuture().join().orElseThrow().corrections());
            var next = request(scope); var engine = new AgentConversationEngine(h.db.mapper);
            var stamp = Objects.requireNonNull(custom.conversation());
            var own = engine.prepare(next, stamp, AgentConversationWindow.empty(), AgentConversationOptions.defaults()).signature().bytes();
            var persona = h.personality.resolve(scope, control).toCompletableFuture().join();
            h.options.set(new AgentConversationOptions(40, own + engine.personalityBytes(persona) + 2, 32, 1048576));
            var bounded = h.mailbox.run(h.handle, next).toCompletableFuture().join(); assertEquals(AgentRunResult.Status.SUCCESS, bounded.status());
            assertFalse(h.db.mapper.writeValueAsString(h.prompts.getLast().getMessages()).contains("离线回答"));
        }
    }

    /** 验证共享补正的一个 scope 换人格只重置自己，备份完整，旧候选和身份字段不能进入新补正。 */
    @Test void switchingOneScopePreservesOtherScopesAndRejectsStaleCandidates() {
        try (var h = new Harness(); var control = ExecutionControl.create()) {
            var otherScope = new AgentScope("客户端", "群乙"); h.settings.put(otherScope.scopeId(), new AgentScopeConfigData());
            var basis = h.personality.resolve(scope, control).toCompletableFuture().join();
            h.personality.resolve(otherScope, control).toCompletableFuture().join();
            var a = h.store.binding(scope).toCompletableFuture().join().orElseThrow(); var b = h.store.binding(otherScope).toCompletableFuture().join().orElseThrow();
            h.store.bind(b, a.correctionKey()).toCompletableFuture().join();
            var input = request(scope); var candidate = correction(input, "表达更自然"); h.personality.propose(scope, basis, candidate, control).toCompletableFuture().join();
            assertTrue(h.personality.resolve(otherScope, control).toCompletableFuture().join().adjustmentsJson().contains(candidate.text()));
            h.settings.get(scope.scopeId()).setCustomPersonalityEnabled(true); h.settings.get(scope.scopeId()).setCustomPersonality("新的基础人格描述");
            var switched = h.personality.resolve(scope, control).toCompletableFuture().join(); assertTrue(switched.adjustmentsJson().isEmpty());
            assertEquals(a.correctionKey(), h.store.binding(otherScope).toCompletableFuture().join().orElseThrow().correctionKey());
            assertTrue(h.personality.resolve(otherScope, control).toCompletableFuture().join().adjustmentsJson().contains(candidate.text()));
            assertThrows(CompletionException.class, () -> h.personality.propose(scope, basis, correction(request(scope), "迟到候选"), control).toCompletableFuture().join());
            assertThrows(CompletionException.class, () -> h.store.append(a, correction(request(scope), "旧绑定写回")).toCompletableFuture().join());
            assertThrows(IllegalArgumentException.class, () -> new PersonalityCorrection(UUID.randomUUID(), "teaneko", 1, "identity.name", "伪造身份", 1, input.actor(), now, null, false));
            assertNotNull(new AgentScopeFieldChecker().isValid("correctionKey", "共享目标"));
        }
    }

    /** 验证切换最后一步失败会回滚备份/新状态，旧运行在配置变化后隐藏回答且下一轮重建窗口。 */
    @Test void failedSwitchRollsBackAndChangedPersonaStopsOldTurn() {
        try (var h = new Harness(); var control = ExecutionControl.create()) {
            var basis = h.personality.resolve(scope, control).toCompletableFuture().join(); var input = request(scope);
            h.personality.propose(scope, basis, correction(input, "原补正"), control).toCompletableFuture().join();
            var original = h.store.binding(scope).toCompletableFuture().join().orElseThrow();
            h.settings.get(scope.scopeId()).setCustomPersonalityEnabled(true); h.settings.get(scope.scopeId()).setCustomPersonality("新设定");
            h.db.failingWritePrefix.set("scope:");
            assertThrows(CompletionException.class, () -> h.personality.resolve(scope, control).toCompletableFuture().join());
            assertEquals(original, h.store.binding(scope).toCompletableFuture().join().orElseThrow());
            assertEquals(1, h.store.state(original.correctionKey()).toCompletableFuture().join().orElseThrow().corrections().size());
            h.db.failingWritePrefix.set(null); h.settings.get(scope.scopeId()).setCustomPersonalityEnabled(false);
            h.beforeReply = () -> { h.settings.get(scope.scopeId()).setCustomPersonalityEnabled(true); h.settings.get(scope.scopeId()).setCustomPersonality("模型期间的新设定"); };
            var rejected = h.mailbox.run(h.handle, request(scope)).toCompletableFuture().join();
            assertEquals(AgentRunResult.Status.STALE_CONVERSATION, rejected.status()); assertTrue(rejected.answer().isEmpty()); assertEquals(1, h.prompts.size());
            h.beforeReply = () -> {}; var fresh = h.mailbox.run(h.handle, request(scope)).toCompletableFuture().join();
            assertEquals(AgentRunResult.Status.SUCCESS, fresh.status()); assertEquals(2, h.mailbox.snapshot(h.handle, scope).orElseThrow().require(AgentConversationWindow.COMPONENT).messages().size());
        }
    }

    /** 验证配置错误不调用模型，补正应用与候选学习开关独立且可恢复，不因关闭应用自动清库。 */
    @Test void invalidConfigurationAndIndependentSwitchesFailSafely() {
        try (var h = new Harness(); var control = ExecutionControl.create()) {
            h.settings.get(scope.scopeId()).setPersonalityId("缺失人格");
            assertEquals(AgentRunResult.Status.CONFIGURATION_ERROR, h.mailbox.run(h.handle, request(scope)).toCompletableFuture().join().status()); assertTrue(h.prompts.isEmpty());
            h.settings.get(scope.scopeId()).setPersonalityId(""); h.file.setCharacters(List.of());
            var basis = h.personality.resolve(scope, control).toCompletableFuture().join(); var input = request(scope);
            h.personality.propose(scope, basis, correction(input, "保留的表达补正"), control).toCompletableFuture().join();
            h.settings.get(scope.scopeId()).setPersonalityCorrectionsEnabled(false); h.settings.get(scope.scopeId()).setPersonalityLearningEnabled(false);
            assertTrue(h.personality.resolve(scope, control).toCompletableFuture().join().adjustmentsJson().isEmpty());
            assertThrows(CompletionException.class, () -> h.personality.propose(scope, basis, correction(request(scope), "不允许的新候选"), control).toCompletableFuture().join());
            h.settings.get(scope.scopeId()).setPersonalityCorrectionsEnabled(true);
            assertTrue(h.personality.resolve(scope, control).toCompletableFuture().join().adjustmentsJson().contains("保留的表达补正"));
        }
    }
}
