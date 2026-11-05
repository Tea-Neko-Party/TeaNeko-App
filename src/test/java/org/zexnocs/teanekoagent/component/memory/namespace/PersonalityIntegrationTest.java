package org.zexnocs.teanekoagent.component.memory.namespace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.zexnocs.teanekoagent.component.llm.framework.input.interfaces.ILLMPrompt;
import org.zexnocs.teanekoagent.component.llm.framework.interaction.LLMInteractionService;
import org.zexnocs.teanekoagent.component.llm.framework.interaction.LLMInteractionSettings;
import org.zexnocs.teanekoagent.component.llm.framework.message.LLMAssistantMessage;
import org.zexnocs.teanekoagent.component.llm.framework.message.LLMUserMessage;
import org.zexnocs.teanekoagent.component.llm.framework.message.content.LLMContentListBuilder;
import org.zexnocs.teanekoagent.component.llm.framework.message.interfaces.LLMMessageRole;
import org.zexnocs.teanekoagent.component.llm.framework.model.LLMModelOptions;
import org.zexnocs.teanekoagent.component.llm.framework.model.LLMModelOptionsSnapshot;
import org.zexnocs.teanekoagent.component.llm.framework.model.ModelCapabilities;
import org.zexnocs.teanekoagent.component.llm.framework.model.interfaces.ILLMModelService;
import org.zexnocs.teanekoagent.component.llm.framework.response.LLMChoice;
import org.zexnocs.teanekoagent.component.llm.framework.response.LLMResult;
import org.zexnocs.teanekoagent.component.llm.framework.response.LLMUsage;
import org.zexnocs.teanekoagent.component.llm.framework.response.interfaces.ILLMResult;
import org.zexnocs.teanekoagent.component.llm.framework.structured.LLMJsonTestSupport;
import org.zexnocs.teanekoagent.component.memory.AgentConversationWindow;
import org.zexnocs.teanekoagent.component.memory.runtime.AgentMemoryReferenceException;
import org.zexnocs.teanekoagent.component.memory.runtime.IAgentMemoryReferences;
import org.zexnocs.teanekoagent.component.personality.*;
import org.zexnocs.teanekoagent.component.personality.adapter.ConfiguredPersonalities;
import org.zexnocs.teanekoagent.component.personality.adapter.EasyDataPersonalityStore;
import org.zexnocs.teanekoagent.configuration.file_config.personality.AgentPersonalityDefinition;
import org.zexnocs.teanekoagent.configuration.file_config.personality.AgentPersonalityFileConfig;
import org.zexnocs.teanekoagent.integration.config.AgentScopeConfigData;
import org.zexnocs.teanekoagent.integration.config.AgentScopeConfigManager;
import org.zexnocs.teanekoagent.integration.config.AgentScopeFieldChecker;
import org.zexnocs.teanekoagent.runtime.*;
import org.zexnocs.teanekoagent.runtime.access.AgentAccessGate;
import org.zexnocs.teanekoagent.runtime.access.AgentReadiness;
import org.zexnocs.teanekoagent.runtime.access.AgentRuntimeSnapshot;
import org.zexnocs.teanekoagent.runtime.access.AgentScopePolicy;
import org.zexnocs.teanekoagent.runtime.conversation.*;
import org.zexnocs.teanekoagent.runtime.prompt.IAgentPromptSource;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.actuator.execution.ExecutionPolicy;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataService;
import org.zexnocs.teanekocore.file_config.interfaces.IFileConfigService;
import org.zexnocs.teanekocore.logger.ILogger;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

/**
 * 复用既有事务夹具验证人格实际生效、共享补正、切换回滚和旧轮失效，不增加数据结构镜像测试。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04��ʹ�ÿ�ѡ��ʽԤ����Լ��֤�˸�ѧϰ�볬ʱ��
 * <br>2026/11/04��У������ʵ�ֵİ汾����Ϊ v4.7.2-l��
 * <br>2026/11/04：原业务夹具显式使用历史装配，继续回归旧行为。
 * <br>2026/11/04：适配可选组件装配及迁移后的数据键，保留原核心行为验证。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/05：补充组件审查修复的离线回归及初始化夹具。
 * <br>2026/11/06：适配真实类型化 JSON 服务，保留原领域与提交边界回归。
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.5.0-l
 * @version 4.8.3-l
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
                List.of(LLMUserMessage.builder().contents(new LLMContentListBuilder().addText("你好").build()).build()), null, null, new AgentUserText(actor.sourceId(), "你好"));
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
     * <br>2026/11/04：校正 @since 为首次引入版本。
     * <br>2026/11/04：人格夹具使用可空显式总预算契约。
     * <br>2026/11/04：本次夹具调整归入 v4.7.2-l。
     *
     * <br>2026/11/04：夹具明确保留历史装配及原业务行为，适配可选组件路径。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @author zExNocs
     * @date 2026/11/01
     * @since 4.5.0-l
     * @version 4.8.0-l
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
        final EasyDataPersonalityStore store = new EasyDataPersonalityStore(db.data, db.mapper);
        final List<ILLMPrompt> prompts = new CopyOnWriteArrayList<>();
        final AtomicReference<AgentConversationOptions> options = new AtomicReference<>(AgentConversationOptions.defaults());
        final AtomicLong bindingRevision = new AtomicLong(1);
        final AtomicReference<PersonalityLearningSettings> learningPolicy = new AtomicReference<>(new PersonalityLearningSettings(true,
                // 2026/11/04：只为本用例显式声明总 token 预算。
                Duration.ofMinutes(5), Duration.ofSeconds(5), 1, 16384L, 131072, Duration.ofDays(30)));
        final BlockingQueue<CompletableFuture<ILLMResult>> pending = new LinkedBlockingQueue<>();
        final BlockingQueue<PersonalityLearningService.Report> reports = new LinkedBlockingQueue<>();
        final PersonalityLearningService learner;
        volatile Runnable beforeReply = () -> {};
        final PersonalityService personality;
        final ConfiguredPersonalities configuration;
        final AgentService turns;
        final AgentConversationService mailbox;

        /** 装配纯离线配置与真实执行，构造时不启动模型。 */
        Harness() { this(false); }
        /**
         * 复用事务、模型和邮箱夹具，仅按需要启用实际后台候选交接。
         * @param automatic 是否启用自动学习
         */
        Harness(boolean automatic) { this(automatic, false); }
        /**
         * 2026/11/05：可选使用真实默认学习工厂，验证按 scope 选择联合或独立通路。
         * @param automatic 是否向后台交接真实轮次
         * @param routed 是否采用生产学习工厂
         */
        Harness(boolean automatic, boolean routed) {
            var character = new AgentPersonalityDefinition(); character.setId("teaneko"); character.setDisplayName("TeaNeko");
            character.getIdentity().setName("TeaNeko"); character.getCoreTraits().add("文件人格：认真解释，轻松交流。");
            character.getLearningPolicy().getMutableFields().add("speaking detail"); file.setCharacters(List.of(character));
            var files = mock(IFileConfigService.class); when(files.get(AgentPersonalityFileConfig.class)).thenReturn(file);
            var configs = mock(IConfigDataService.class);
            when(configs.getConfigData(any(), eq(AgentScopeConfigData.class), anyString())).thenAnswer(call -> Optional.ofNullable(settings.get(call.<String>getArgument(2))));
            configuration = new ConfiguredPersonalities(configs, new AgentScopeConfigManager(), files);
            personality = new PersonalityService(configuration, store, workers, clock, db.mapper);
            var logger = mock(ILogger.class);
            var runtime = new AgentRuntimeSnapshot(new LLMModelOptionsSnapshot(LLMModelOptions.builder().provider("test").model("离线模型").maxTokens(30).build()),
                    ModelCapabilities.textOnly(), LLMInteractionSettings.defaults());
            var gate = new AgentAccessGate(registry, target -> new AgentScopePolicy(settings.containsKey(target.scopeId()),
                    settings.containsKey(target.scopeId()) && settings.get(target.scopeId()).isEnabled(), AgentScopePolicy.Mode.ACTIVE, "",
                    settings.containsKey(target.scopeId()) ? settings.get(target.scopeId()).assembly() : org.zexnocs.teanekoagent.component.AgentAssembly.legacyDefaults()),
                    ignored -> new AgentReadiness(AgentReadiness.Status.READY, Optional.of(runtime)), logger);
            var models = mock(ILLMModelService.class);
            when(models.resolveOptions(any(ILLMPrompt.class))).thenAnswer(call -> call.<ILLMPrompt>getArgument(0).getOptions());
            when(models.call(any(ILLMPrompt.class), any(ExecutionPolicy.class))).thenAnswer(call -> {
                ILLMPrompt prompt = call.getArgument(0);
                if (prompt.getOptions().findResponseSchema().map(schema -> schema.name().startsWith("PersonalityLearningOutput")).orElse(false)) {
                    var response = new CompletableFuture<ILLMResult>(); pending.add(response); return new TaskFuture<>(logger, "后台人格模型", response);
                }
                prompts.add(call.getArgument(0)); beforeReply.run();
                var answer = LLMAssistantMessage.builder().contents(new LLMContentListBuilder().addText("离线回答").build()).build();
                return new TaskFuture<>(logger, "人格离线模型", CompletableFuture.completedFuture(LLMResult.builder()
                        .usage(LLMUsage.builder().totalTokens(2).build()).choices(List.of(LLMChoice.builder().index(0).finishReason("stop").message(answer).build())).build()));
            });
            var references = mock(IAgentMemoryReferences.class);
            when(references.resolve(any())).thenAnswer(call -> CompletableFuture.completedStage(reference(call.getArgument(0))));
            when(references.verify(any())).thenAnswer(call -> {
                MemoryNamespaceReference expected = call.getArgument(0);
                return expected.equals(reference(expected.scope())) ? CompletableFuture.completedStage(null)
                        : CompletableFuture.failedStage(new AgentMemoryReferenceException(AgentMemoryReferenceException.Reason.CHANGED));
            });
            var interactions = new LLMInteractionService(models, LLMInteractionSettings::defaults, workers, timers);
            if (routed) {
                var beans = new org.springframework.beans.factory.support.DefaultListableBeanFactory();
                var observations = mock(StyleObservationService.class);
                when(observations.enabled(any())).thenReturn(true);
                beans.registerSingleton("observations", observations);
                learner = new org.zexnocs.teanekoagent.component.personality.configuration.PersonalityLearningConfig()
                        .personalityLearning(gate, personality, references, LLMJsonTestSupport.create(interactions, db.mapper),
                                new org.springframework.mock.env.MockEnvironment(), db.mapper, workers, timers,
                                logger, beans.getBeanProvider(StyleObservationService.class), configs, new AgentScopeConfigManager());
            } else {
                learner = new PersonalityLearningService(gate, personality, references, LLMJsonTestSupport.create(interactions, db.mapper),
                        learningPolicy::get, db.mapper, workers, timers, Clock.systemUTC(), reports::add);
            }
            turns = new AgentService(gate, interactions,
                    (definition, input, config) -> List.of(), db.mapper, workers, timers, clock, logger, automatic ? references : null, IAgentPromptSource.empty(), personality);
            mailbox = new AgentConversationService(turns, AgentQueueSettings::defaults, workers, timers, clock, logger,
                    AgentConversationSettings::defaults, (definition, input) -> options.get(), db.mapper, automatic ? references : null, personality, null, automatic ? learner : null);
            settings.put(scope.scopeId(), new AgentScopeConfigData(null));
        }
        /**
         * 固定当前持久化引用，版本推进模拟 reset 或换空间。
         * @param target 当前作用域
         * @return 完整当前引用
         */
        MemoryNamespaceReference reference(AgentScope target) { return new MemoryNamespaceReference(target, MemoryNamespaceId.SHARED, bindingRevision.get(), 0); }
        /**
         * 有界等待实际后台模型进入，不轮询、不任意睡眠。
         * @return 可控制的真实模型传输结果
         * @throws InterruptedException 当等待中断时
         */
        CompletableFuture<ILLMResult> awaitLearning() throws InterruptedException { return Objects.requireNonNull(pending.poll(5, TimeUnit.SECONDS), "后台人格模型未按时进入，报告=" + learner.reports()); }
        /**
         * 观察后台结束及资源释放后的用量报告。
         * @return 已完成的观察回执
         * @throws InterruptedException 当等待中断时
         */
        PersonalityLearningService.Report awaitReport() throws InterruptedException { return Objects.requireNonNull(reports.poll(5, TimeUnit.SECONDS), "后台人格学习未按时结束。"); }
        /** 关闭自身拥有者，业务服务不关闭借用资源。 */
        @Override public void close() { mailbox.close(); turns.close(); learner.close(); registry.close(); timers.shutdownNow(); workers.close(); db.close(); }
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

    /** 验证编辑原证据、回滚、防重和共享会话换代，迟到学习不能覆盖管理结果。 */
    @Test void managementEditsPreserveEvidenceAndInvalidateSharedSnapshots() {
        try (var h = new Harness(); var control = ExecutionControl.create()) {
            var other = new AgentScope("客户端", "共享群"); h.settings.put(other.scopeId(), new AgentScopeConfigData(null));
            var basis = h.personality.resolve(scope, control).toCompletableFuture().join();
            h.personality.resolve(other, control).toCompletableFuture().join();
            var binding = h.store.binding(scope).toCompletableFuture().join().orElseThrow();
            h.store.bind(h.store.binding(other).toCompletableFuture().join().orElseThrow(), binding.correctionKey()).toCompletableFuture().join();
            var input = request(scope); var original = correction(input, "原补正");
            h.personality.propose(scope, basis, original, control).toCompletableFuture().join();
            h.mailbox.run(h.handle, request(scope)).toCompletableFuture().join(); h.mailbox.run(h.handle, request(other)).toCompletableFuture().join();
            var edit = new PersonalityManagementCommand(UUID.randomUUID(), input.actor(), PersonalityManagementCommand.Action.EDIT,
                    binding.correctionKey(), binding.revision(), 1, original.requestId(), null, "修订表达", null);
            var base = h.configuration.base(h.configuration.settings(scope)); var checks = new java.util.concurrent.atomic.AtomicInteger();
            assertThrows(CompletionException.class, () -> h.store.apply(edit, base, now, () -> {
                if (checks.incrementAndGet() == 2) { throw new IllegalStateException("提交前已撤销管理授权。"); }
            }).toCompletableFuture().join());
            assertTrue(h.store.backups(binding.correctionKey()).toCompletableFuture().join().isEmpty());
            assertEquals(original, h.store.state(binding.correctionKey()).toCompletableFuture().join().orElseThrow().corrections().getFirst());
            var first = h.store.apply(edit, base, now, () -> {}); var repeated = h.store.apply(edit, base, now.plusSeconds(1), () -> {});
            var applied = first.toCompletableFuture().join(); assertEquals(applied, repeated.toCompletableFuture().join());
            assertEquals(PersonalityManagementResult.Status.APPLIED, applied.status());
            var rebuilt = new EasyDataPersonalityStore(h.db.data, h.db.mapper);
            assertEquals(applied, rebuilt.apply(edit, base, now.plusSeconds(2), () -> {}).toCompletableFuture().join());
            var state = rebuilt.state(binding.correctionKey()).toCompletableFuture().join().orElseThrow();
            assertEquals(1, state.managementRevision()); assertEquals(original.source(), state.corrections().getFirst().source());
            assertEquals(original.occurredAt(), state.corrections().getFirst().occurredAt());
            assertEquals(original, rebuilt.state(Objects.requireNonNull(applied.backupKey())).toCompletableFuture().join().orElseThrow().corrections().getFirst());
            assertThrows(CompletionException.class, () -> h.personality.verify(scope, basis, control).toCompletableFuture().join());
            assertThrows(CompletionException.class, () -> rebuilt.append(binding, correction(request(scope), "迟到学习"), 0, () -> {}).toCompletableFuture().join());
            assertEquals(2, h.mailbox.synchronizePersonality(scope, binding.correctionKey(), true, control).toCompletableFuture().join());
            assertTrue(h.mailbox.snapshot(h.handle, other).orElseThrow().require(AgentConversationWindow.COMPONENT).messages().isEmpty());
            h.mailbox.run(h.handle, request(other)).toCompletableFuture().join();
            assertEquals(0, h.mailbox.synchronizePersonality(scope, binding.correctionKey(), true, control).toCompletableFuture().join());
            assertEquals(2, h.mailbox.snapshot(h.handle, other).orElseThrow().require(AgentConversationWindow.COMPONENT).messages().size());
        }
    }

    /** 验证显式备份、克隆、锁定、撤销与绑定按原版本提交，失败不留下部分备份或新引用。 */
    @Test void managementCloneBindingAndRevocationRemainAtomic() {
        try (var h = new Harness(); var control = ExecutionControl.create()) {
            var basis = h.personality.resolve(scope, control).toCompletableFuture().join(); var input = request(scope);
            var differentClient = h.personality.resolve(new AgentScope("其他客户端", scope.scopeId()), control).toCompletableFuture().join();
            assertNotEquals(basis.correctionKey(), differentClient.correctionKey());
            var original = correction(input, "可克隆补正"); h.personality.propose(scope, basis, original, control).toCompletableFuture().join();
            var binding = h.store.binding(scope).toCompletableFuture().join().orElseThrow(); var base = h.configuration.base(h.configuration.settings(scope));
            var backup = new PersonalityManagementCommand(UUID.randomUUID(), input.actor(), PersonalityManagementCommand.Action.BACKUP,
                    binding.correctionKey(), binding.revision(), 1, null, null, null, null);
            var archived = h.store.apply(backup, base, now, () -> {}).toCompletableFuture().join();
            var clone = new PersonalityManagementCommand(UUID.randomUUID(), input.actor(), PersonalityManagementCommand.Action.CLONE,
                    Objects.requireNonNull(archived.backupKey()), binding.revision(), 1, null, null, null, null);
            h.db.failingWritePrefix.set("state");
            assertThrows(CompletionException.class, () -> h.store.apply(clone, base, now, () -> {}).toCompletableFuture().join());
            h.db.failingWritePrefix.set(null);
            var copied = h.store.apply(clone, base, now, () -> {}).toCompletableFuture().join();
            assertEquals(List.of(original), h.store.state(copied.key()).toCompletableFuture().join().orElseThrow().corrections());
            var bind = new PersonalityManagementCommand(UUID.randomUUID(), input.actor(), PersonalityManagementCommand.Action.BIND,
                    copied.key(), binding.revision(), 0, null, null, null, null);
            h.db.failingWritePrefix.set("scope:");
            assertThrows(CompletionException.class, () -> h.store.apply(bind, base, now, () -> {}).toCompletableFuture().join());
            assertEquals(binding, h.store.binding(scope).toCompletableFuture().join().orElseThrow());
            assertEquals(List.of(archived.backupKey()), h.store.backups(binding.correctionKey()).toCompletableFuture().join());
            h.db.failingWritePrefix.set(null);
            var bound = h.store.apply(bind, base, now, () -> {}).toCompletableFuture().join();
            assertEquals(bound, h.store.apply(bind, base, now.plusSeconds(1), () -> {}).toCompletableFuture().join());
            var lock = new PersonalityManagementCommand(UUID.randomUUID(), input.actor(), PersonalityManagementCommand.Action.LOCK,
                    copied.key(), bound.bindingRevision(), 0, original.requestId(), null, null, true);
            var locked = h.store.apply(lock, base, now, () -> {}).toCompletableFuture().join(); assertEquals(1, locked.stateRevision());
            var changedReplay = new PersonalityManagementCommand(lock.operationId(), lock.actor(), lock.action(), lock.key(), lock.bindingRevision(), lock.stateRevision(), lock.correctionId(), null, null, false);
            assertEquals(PersonalityManagementResult.Status.CONFLICT, h.store.apply(changedReplay, base, now, () -> {}).toCompletableFuture().join().status());
            var remove = new PersonalityManagementCommand(UUID.randomUUID(), input.actor(), PersonalityManagementCommand.Action.REMOVE,
                    copied.key(), bound.bindingRevision(), 1, original.requestId(), null, null, null);
            h.store.apply(remove, base, now, () -> {}).toCompletableFuture().join();
            assertTrue(h.store.state(copied.key()).toCompletableFuture().join().orElseThrow().corrections().isEmpty());
            assertEquals(List.of(original), h.store.state(binding.correctionKey()).toCompletableFuture().join().orElseThrow().corrections());
            assertThrows(CompletionException.class, () -> h.personality.propose(scope, basis, correction(request(scope), "旧引用"), control).toCompletableFuture().join());
        }
    }

    /** 验证共享补正的一个 scope 换人格只重置自己，备份完整，旧候选和身份字段不能进入新补正。 */
    @Test void switchingOneScopePreservesOtherScopesAndRejectsStaleCandidates() {
        try (var h = new Harness(); var control = ExecutionControl.create()) {
            var otherScope = new AgentScope("客户端", "群乙"); h.settings.put(otherScope.scopeId(), new AgentScopeConfigData(null));
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

    /**
     * 复用真实模型传输响应，候选正文只用于独立后台生成。
     * @param text 模型候选 JSON
     * @return 含供应商用量的完整响应
     */
    private ILLMResult learningResponse(String text) {
        return LLMResult.builder().usage(LLMUsage.builder().totalTokens(2).build()).choices(List.of(LLMChoice.builder().index(0).finishReason("stop")
                .message(LLMAssistantMessage.builder().contents(new LLMContentListBuilder().addText(text).build()).build()).build())).build();
    }
    /**
     * 2026/11/05：只有 LLM 与人格的真实对话仍可自动采样，候选在下一轮生效且不建立记忆引用。
     * @throws Exception 当有界模型或后台报告等待失败时
     */
    @Test
    void personalityLearnsWithoutInstallingMemory() throws Exception {
        try (var h = new Harness(true)) {
            h.settings.get(scope.scopeId()).setComponents(
                    "{\"version\":1,\"components\":{\"llm\":{},\"personality\":{\"personalityLearningEnabled\":true}}}");
            var input = request(scope);
            var first = h.mailbox.run(h.handle, input).toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertEquals(AgentRunResult.Status.SUCCESS, first.status());
            assertNull(first.memoryReference());
            h.awaitLearning().complete(learningResponse(
                    "{\"adjustment\":{\"field\":\"speaking detail\",\"text\":\"先给简短结论，再举例\",\"confidence\":0.9}}"));
            assertEquals(PersonalityLearningService.Outcome.APPLIED, h.awaitReport().outcome());
            var next = h.mailbox.run(h.handle, request(scope)).toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertTrue(Objects.requireNonNull(next.promptTranscript()).input().personality().adjustmentsJson().contains("先给简短结论"));
        }
    }

    /**
     * 2026/11/05：生产工厂不因存在全局观察 Bean 停用无记忆或关闭历史/摘要的聊天学习。
     * @throws Exception 当模型阶段未在期限内进入时
     */
    @Test
    void defaultLearningFactoryFallsBackForEachScopeWithoutCompressionInput() throws Exception {
        for (String mode : List.of("无记忆", "关闭历史", "关闭摘要")) {
            try (var h = new Harness(true, true)) {
                var config = h.settings.get(scope.scopeId());
                if (mode.equals("无记忆")) {
                    config.setComponents("{\"version\":1,\"components\":{\"llm\":{},\"personality\":{}}}");
                } else if (mode.equals("关闭历史")) {
                    config.setHistoryCaptureEnabled(false);
                } else {
                    config.setAutoSummaryEnabled(false);
                }
                assertEquals(AgentRunResult.Status.SUCCESS,
                        h.mailbox.run(h.handle, request(scope)).toCompletableFuture().get(5, TimeUnit.SECONDS).status());
                h.awaitLearning().complete(learningResponse(
                        "{\"adjustment\":{\"field\":\"speaking detail\",\"text\":\"先说结论，再给示例\",\"confidence\":0.9}}"));
                org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(5))
                        .until(() -> !h.learner.reports().isEmpty());
                assertEquals(PersonalityLearningService.Outcome.APPLIED, h.learner.reports().getFirst().outcome());
            }
        }
    }

    /**
     * 验证真实互动后台生成、提交确认及下一轮应用，等待期间前台正常回答，重放不重复学习。
     * @throws Exception 当后台等待中断或未按时完成时
     */
    @Test void automaticLearningCommitsAndAppliesOnlyOnNextTurn() throws Exception {
        try (var h = new Harness(true)) {
            var input = request(scope); var first = h.mailbox.run(h.handle, input).toCompletableFuture().join();
            assertEquals(AgentRunResult.Status.SUCCESS, first.status()); var pending = h.awaitLearning();
            assertEquals(AgentRunResult.Status.SUCCESS, h.mailbox.run(h.handle, request(scope)).toCompletableFuture().join().status());
            pending.complete(learningResponse("{\"adjustment\":{\"field\":\"speaking detail\",\"text\":\"表达更简洁，保持温和\",\"confidence\":0.9}}"));
            var report = h.awaitReport(); assertEquals(PersonalityLearningService.Outcome.APPLIED, report.outcome());
            assertEquals(input.requestId(), report.requestId()); assertEquals(1, report.tokenUsage().size());
            var key = h.store.binding(scope).toCompletableFuture().join().orElseThrow().correctionKey();
            var saved = h.store.state(key).toCompletableFuture().join().orElseThrow().corrections().getFirst();
            assertEquals(input.actor(), saved.source()); assertEquals(input.requestId(), saved.requestId());
            assertEquals(AgentRunResult.Status.SUCCESS, h.mailbox.run(h.handle, request(scope)).toCompletableFuture().join().status());
            assertTrue(h.db.mapper.writeValueAsString(h.prompts.getLast().getMessages()).contains(saved.text()));
            assertFalse(h.db.mapper.writeValueAsString(h.mailbox.snapshot(h.handle, scope).orElseThrow().require(AgentConversationWindow.COMPONENT).messages()).contains(saved.text()));
            assertSame(first, h.mailbox.retry(h.handle, input).toCompletableFuture().join()); assertTrue(h.pending.isEmpty());
        }
    }
    /**
     * 验证学习关闭拒绝在途候选，应用关闭仍可独立学习，模型不能提交额外身份/锁定字段。
     * @throws Exception 当后台等待中断或未按时完成时
     */
    @Test void learningAndApplicationSwitchesAndCandidateBoundaryStayIndependent() throws Exception {
        try (var h = new Harness(true); var control = ExecutionControl.create()) {
            // 2026/11/04：显式总预算使用可空 Long 契约，原学习行为保持可观察。
            h.learningPolicy.set(new PersonalityLearningSettings(true, Duration.ZERO, Duration.ofSeconds(5), 1, 16384L, 131072, Duration.ofDays(30)));
            h.mailbox.run(h.handle, request(scope)).toCompletableFuture().join(); var disabled = h.awaitLearning();
            h.settings.get(scope.scopeId()).setPersonalityLearningEnabled(false);
            disabled.complete(learningResponse("{\"adjustment\":{\"field\":\"speaking detail\",\"text\":\"迟到表达\",\"confidence\":1}}"));
            assertEquals(PersonalityLearningService.Outcome.REJECTED, h.awaitReport().outcome());
            assertTrue(h.personality.resolve(scope, control).toCompletableFuture().join().adjustmentsJson().isEmpty());
            h.settings.get(scope.scopeId()).setPersonalityLearningEnabled(true); h.settings.get(scope.scopeId()).setPersonalityCorrectionsEnabled(false);
            h.mailbox.run(h.handle, request(scope)).toCompletableFuture().join();
            h.awaitLearning().complete(learningResponse("{\"adjustment\":{\"field\":\"speaking detail\",\"text\":\"保持轻松表达\",\"confidence\":0.8}}"));
            assertEquals(PersonalityLearningService.Outcome.APPLIED, h.awaitReport().outcome());
            assertTrue(h.personality.resolve(scope, control).toCompletableFuture().join().adjustmentsJson().isEmpty());
            h.mailbox.run(h.handle, request(scope)).toCompletableFuture().join();
            h.awaitLearning().complete(learningResponse("{\"adjustment\":{\"field\":\"speaking detail\",\"text\":\"伪造候选\",\"confidence\":1,\"locked\":true}}"));
            assertEquals(PersonalityLearningService.Outcome.REJECTED, h.awaitReport().outcome());
            var key = h.store.binding(scope).toCompletableFuture().join().orElseThrow().correctionKey();
            assertEquals(1, h.store.state(key).toCompletableFuture().join().orElseThrow().corrections().size());
        }
    }
    /**
     * 验证独立时限、空间换代及实例关闭均阻止迟到学习，后台槽位可在结束后再次使用。
     * @throws Exception 当后台等待中断或未按时完成时
     */
    @Test void timeoutChangedNamespaceAndOwnerCloseRejectBackgroundWork() throws Exception {
        try (var h = new Harness(true)) {
            // 2026/11/04：超时用例保留显式总预算。
            h.learningPolicy.set(new PersonalityLearningSettings(true, Duration.ZERO, Duration.ofMillis(300), 1, 16384L, 131072, Duration.ofDays(30)));
            h.mailbox.run(h.handle, request(scope)).toCompletableFuture().join(); h.awaitLearning();
            assertEquals(PersonalityLearningService.Outcome.REJECTED, h.awaitReport().outcome());
            // 2026/11/04：恢复原时限时仍保留用例显式总预算。
            h.learningPolicy.set(new PersonalityLearningSettings(true, Duration.ZERO, Duration.ofSeconds(5), 1, 16384L, 131072, Duration.ofDays(30)));
            h.mailbox.run(h.handle, request(scope)).toCompletableFuture().join(); var stale = h.awaitLearning();
            h.bindingRevision.incrementAndGet(); h.mailbox.run(h.handle, request(scope)).toCompletableFuture().join();
            stale.complete(learningResponse("{\"adjustment\":{\"field\":\"speaking detail\",\"text\":\"旧空间候选\",\"confidence\":1}}"));
            assertEquals(PersonalityLearningService.Outcome.REJECTED, h.awaitReport().outcome());
            h.mailbox.run(h.handle, request(scope)).toCompletableFuture().join(); h.awaitLearning(); h.handle.close();
            assertEquals(PersonalityLearningService.Outcome.REJECTED, h.awaitReport().outcome());
            var key = h.store.binding(scope).toCompletableFuture().join().orElseThrow().correctionKey();
            assertTrue(h.store.state(key).toCompletableFuture().join().orElseThrow().corrections().isEmpty());
        }
    }
}
