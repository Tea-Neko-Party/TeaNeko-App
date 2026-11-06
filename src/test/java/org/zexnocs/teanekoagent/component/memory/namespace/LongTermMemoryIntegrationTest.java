package org.zexnocs.teanekoagent.component.memory.namespace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.zexnocs.teanekoagent.component.llm.framework.interaction.ILLMInteractionService;
import org.zexnocs.teanekoagent.component.llm.framework.interaction.LLMInteractionResult;
import org.zexnocs.teanekoagent.component.llm.framework.interaction.LLMInteractionSettings;
import org.zexnocs.teanekoagent.component.llm.framework.message.LLMAssistantMessage;
import org.zexnocs.teanekoagent.component.llm.framework.message.content.LLMContentListBuilder;
import org.zexnocs.teanekoagent.component.llm.framework.model.LLMModelOptions;
import org.zexnocs.teanekoagent.component.llm.framework.model.LLMModelOptionsSnapshot;
import org.zexnocs.teanekoagent.component.llm.framework.model.ModelCapabilities;
import org.zexnocs.teanekoagent.component.llm.framework.structured.LLMJsonTestSupport;
import org.zexnocs.teanekoagent.component.memory.adapter.*;
import org.zexnocs.teanekoagent.component.memory.history.HistoryContext;
import org.zexnocs.teanekoagent.component.memory.history.HistoryMessage;
import org.zexnocs.teanekoagent.component.memory.history.adapter.EasyDataHistoryStore;
import org.zexnocs.teanekoagent.component.memory.layered.*;
import org.zexnocs.teanekoagent.component.memory.runtime.IAgentMemoryReferences;
import org.zexnocs.teanekoagent.component.personality.AgentPersonalitySnapshot;
import org.zexnocs.teanekoagent.component.tool.TeaNekoAgentToolMethods;
import org.zexnocs.teanekoagent.runtime.*;
import org.zexnocs.teanekoagent.runtime.access.AgentAccessGate;
import org.zexnocs.teanekoagent.runtime.access.AgentReadiness;
import org.zexnocs.teanekoagent.runtime.access.AgentRuntimeSnapshot;
import org.zexnocs.teanekoagent.runtime.access.AgentScopePolicy;
import org.zexnocs.teanekoagent.runtime.prompt.AgentPromptInput;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.logger.ILogger;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

/**
 * 复用事务夹具检验从真实发言到长期画像、召回和本人维护的完整闭环。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：验证长期生成器保留宿主配置的更小输出上限。
 * <br>2026/11/04��У������ʵ�ֵİ汾����Ϊ v4.7.2-l��
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/05：独立工具组合继续覆盖原文意图和共享长期画像边界。
 * <br>2026/11/05：核对联合输出的 JSON 对象约束及字段失败安全诊断。
 * <br>2026/11/05：补充组件审查修复的离线回归及初始化夹具。
 * <br>2026/11/05：验证证据原因、混合回滚、来源失效与持久暂停的授权恢复。
 * <br>2026/11/06：覆盖排队证据到期、旧作业读取、网页恢复回执及数据库异常包装。
 * <br>2026/11/06：格式不匹配保留受控字段诊断，不清空原作业或误计模型错误。
 * <br>2026/11/06：按有效新证据聚合长期提取，保留版本幂等、租约和事务校验。
 * <br>2026/11/06：验证共享事件与个人画像在输出截断前分离。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.6.1-l
 * @version 4.8.6-l
 */
@Timeout(25)
class LongTermMemoryIntegrationTest {
    /** 2026/11/06：单片段有效证据超过预算时明确失败，不能丢掉尾部证据后推进水位。 */
    @Test
    void oversizedEvidenceDoesNotAdvanceWholeSegment() {
        try (var h = new Harness()) {
            h.medium("有效事实".repeat(5000), false);
            var failure = assertThrows(CompletionException.class, h::claim);
            assertInstanceOf(MemoryProcessingException.class, failure.getCause());
            assertEquals(0, h.longs.consolidationState(reference, now, () -> {}).toCompletableFuture().join().through());
        }
    }
    /**
     * 2026/11/06：纯问候经过真实领取和提交，不调用模型；短事实不能按长度丢掉。
     * @throws Exception 当有界异步处理失败时
     */
    @Test
    void skipsOnlyLowInformationBeforeCallingModel() throws Exception {
        try (var h = new Harness(); var timers = Executors.newSingleThreadScheduledExecutor()) {
            h.medium("你好！你好呀～", false);
            var generator = mock(ILongTermMemoryConsolidator.class);
            try (var processor = new LayeredMemoryProcessor(h.layers,
                    (batch, control) -> { throw new AssertionError("不应压缩"); }, h.refs,
                    () -> settings, ignored -> new LayeredMemoryPolicy(true, true, false), Runnable::run, timers,
                    Clock.fixed(now.plusSeconds(200), ZoneOffset.UTC), mock(ILogger.class), h.longs, generator, ignored -> true)) {
                processor.drain().toCompletableFuture().get(5, TimeUnit.SECONDS);
                verifyNoInteractions(generator);
                assertTrue(h.longs.consolidationState(reference, now, () -> {}).toCompletableFuture().join().through() > 0);
            }
        }
        try (var h = new Harness()) {
            h.medium("我爱茶", false);
            assertFalse(h.claim().evidence().isEmpty());
        }
        var longGreeting = new HistoryMessage("问候", actor("问候"), HistoryMessage.Role.USER,
                "哈".repeat(60000), now, false, false);
        assertFalse(LongMemoryLearningPolicy.defaults().informative(longGreeting));
    }

    /** 2026/11/06：聚合数量、最大等待和重要事件分别触发，重建 Store 不丢待处理片段。 */
    @Test
    void batchesNewSegmentsAndHonorsDeadlineAndUrgency() {
        try (var h = new Harness()) {
            for (int index = 0; index < 3; index++) {
                h.medium("我平时喝茶");
                assertTrue(h.longs.claim(h.stream(), now.plusSeconds(200)).toCompletableFuture().join().isEmpty());
            }
            h.medium("我平时喝茶");
            assertEquals(4, h.longs.claim(h.stream(), now.plusSeconds(200)).toCompletableFuture().join().orElseThrow().segments().size());
        }
        try (var h = new Harness()) {
            h.medium("我爱茶", false);
            var restored = new EasyDataLongTermMemoryStore(h.db.data, h.db.store, h.db.mapper, () -> settings);
            assertTrue(restored.claim(h.stream(), now.plusSeconds(700)).toCompletableFuture().join().isEmpty());
            assertTrue(restored.claim(h.stream(), now.plusSeconds(701)).toCompletableFuture().join().isPresent());
        }
        try (var h = new Harness()) {
            h.medium("我已离职", false);
            assertTrue(h.longs.claim(h.stream(), now.plusSeconds(200)).toCompletableFuture().join().isPresent());
        }
    }

    /** 2026/11/06：成功后重放相同片段仍无新证据，检查点与合同版本跨 Store 重建保留。 */
    @Test
    void completedEvidenceCheckpointSurvivesReplay() {
        try (var h = new Harness()) {
            h.medium("我平时喜欢茶");
            var batch = h.claim();
            assertTrue(h.longs.commit(batch, List.of(), now.plusSeconds(1001)).toCompletableFuture().join());
            var data = h.db.data.inNamespace("agent.memory.long.v1");
            var stream = h.stream();
            data.transaction("测试恢复旧水位", () -> {
                var jobs = data.get("jobs").atomic();
                var row = Objects.requireNonNull(jobs.find(stream.id(),
                        org.zexnocs.teanekocore.database.easydata.core.EasyDataLock.WRITE));
                var tree = (tools.jackson.databind.node.ObjectNode) h.db.mapper.readTree(row.getValue());
                tree.put("through", 0);
                row.setValue(h.db.mapper.writeValueAsString(tree));
                jobs.save(row);
                var checkpoint = Objects.requireNonNull(data.get("learning").atomic().find(stream.id(),
                        org.zexnocs.teanekocore.database.easydata.core.EasyDataLock.READ));
                assertTrue(checkpoint.getValue().contains(LongMemoryLearningPolicy.PROMPT_VERSION));
                assertTrue(checkpoint.getValue().contains(LongMemoryLearningPolicy.SCHEMA_VERSION));
                assertFalse(checkpoint.getValue().contains("喜欢茶"));
                return true;
            }).toCompletableFuture().join();
            var restored = new EasyDataLongTermMemoryStore(h.db.data, h.db.store, h.db.mapper, () -> settings);
            var replay = restored.claim(h.stream(), now.plusSeconds(1100)).toCompletableFuture().join().orElseThrow();
            assertTrue(replay.evidence().isEmpty());
        }
    }
    /**
     * 2026/11/05：在数据库回调已排队后撤销事实资格或取消控制，纯事实与空候选均不推进水位。
     * @throws Exception 当有界异步检查超时或真实事务失败时
     */
    @Test void queuedConsolidationRechecksCancellationAndFactLearning() throws Exception {
        for (boolean empty : List.of(false, true)) {
            for (boolean cancelled : List.of(false, true)) {
                try (var h = new Harness(); var timers = Executors.newSingleThreadScheduledExecutor()) {
                    h.medium("我喜欢喝茶");
                    var entered = new CompletableFuture<Void>();
                    var release = new CompletableFuture<Void>();
                    var learning = new AtomicBoolean(true);
                    var active = new java.util.concurrent.atomic.AtomicReference<ExecutionControl>();
                    h.db.beforeTask.set(name -> {
                        if (name.contains("提交长期记忆和画像证据")) {
                            entered.complete(null);
                            release.orTimeout(8, TimeUnit.SECONDS).join();
                        }
                    });
                    try (var processor = new LayeredMemoryProcessor(h.layers,
                            (batch, control) -> { throw new AssertionError("本例不应生成中期摘要"); }, h.refs,
                            () -> settings, ignored -> new LayeredMemoryPolicy(true, true, true), Runnable::run, timers,
                            Clock.fixed(now.plusSeconds(1000), ZoneOffset.UTC), mock(ILogger.class), h.longs,
                            (batch, control) -> {
                                active.set(control);
                                return CompletableFuture.completedStage(empty ? List.of()
                                        : List.of(h.candidate(batch, "喜欢喝茶", LongMemories.Mode.ADD)));
                            }, ignored -> learning.get())) {
                        var work = processor.drain().toCompletableFuture();
                        try {
                            entered.get(5, TimeUnit.SECONDS);
                            if (cancelled) { active.get().cancel(); } else { learning.set(false); }
                        } finally { release.complete(null); }
                        work.get(8, TimeUnit.SECONDS);
                        assertTrue(h.longs.query(reference, user, "", 20).toCompletableFuture().join().items().isEmpty());
                        assertTrue(h.longs.claim(h.stream(), now.plusSeconds(1100)).toCompletableFuture().join().isPresent(),
                                "撤权后不能推进原中期片段的整理水位");
                    }
                }
            }
        }
    }

    /** 2026/11/05：单字无索引词时仍须字面命中，真正空查询才列出最近项。 */
    @Test void singleCharacterQueryDoesNotReturnUnrelatedRecentFacts() {
        try (var h = new Harness()) {
            h.medium("我喜欢喝茶");
            h.learn("喜欢喝茶", LongMemories.Mode.ADD);
            assertEquals(1, h.longs.query(reference, null, "茶", 20).toCompletableFuture().join().items().size());
            assertTrue(h.longs.query(reference, null, "猫", 20).toCompletableFuture().join().items().isEmpty());
            assertTrue(h.longs.query(reference, user, "a", 20).toCompletableFuture().join().items().isEmpty());
            assertEquals(1, h.longs.query(reference, null, "", 20).toCompletableFuture().join().items().size());
        }
    }

    /** 2026/11/06：用户画像与共享事件由权威查询分离，不在取出一页后再丢弃个人条目。 */
    @Test void separatesSharedEventsFromPersonalProfilesBeforeOutputLimit() {
        try (var h = new Harness()) {
            h.medium("我喜欢喝茶，我们今天完成了部署");
            var batch = h.claim();
            var personal = h.candidate(batch, "喜欢喝茶", LongMemories.Mode.ADD);
            var event = new LongMemories.Candidate(LongMemories.Kind.EPISODE, null, "event.deployment", "今天完成了部署",
                    LongMemories.Mode.ADD, personal.quotes(), personal.segments(), "");
            assertTrue(h.longs.commit(batch, List.of(personal, event), now.plusSeconds(1001)).toCompletableFuture().join());
            var events = h.longs.events(reference, "", 1).toCompletableFuture().join();
            assertEquals(1, events.items().size());
            assertNull(events.items().getFirst().subjectId());
            assertEquals("今天完成了部署", events.items().getFirst().text());
            assertEquals(1, h.longs.query(reference, user, "", 20).toCompletableFuture().join().items().size());
        }
    }
    private final Instant now = Instant.parse("2026-11-03T00:00:00Z");
    private final UUID user = new UUID(0, 1);
    private final AgentScope scope = new AgentScope("test", "群甲");
    private final MemoryNamespaceReference reference = new MemoryNamespaceReference(scope, MemoryNamespaceId.SHARED, 0, 0);
    private final LayeredMemorySettings settings = new LayeredMemorySettings(true, 6, 2, 4, 131072, Duration.ofSeconds(30), Duration.ofDays(90));
/**
     * 持有真实适配器，所有证据均经原历史事务进入分层存储。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     * <br>2026/11/06：按有效新证据聚合长期提取，保留版本幂等、租约和事务校验。
     *
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.6.1-l
     * @version 4.8.6-l
     */
    private final class Harness implements AutoCloseable {
        final NamespaceDatabaseFixture db = new NamespaceDatabaseFixture();
        final EasyDataLayeredMemoryStore layers = new EasyDataLayeredMemoryStore(db.data, db.store, db.mapper);
        final EasyDataHistoryStore history = new EasyDataHistoryStore(db.data, db.mapper, layers);
        final EasyDataLongTermMemoryStore longs = new EasyDataLongTermMemoryStore(db.data, db.store, db.mapper, () -> settings);
        final IAgentMemoryReferences refs = mock(IAgentMemoryReferences.class);
        int sequence;
        /** 创建真实共享空间并固定原引用校验。 */
        Harness() {
            db.store.create(new MemoryNamespace(MemoryNamespaceId.SHARED, 0, 0, now, null, null), 10).toCompletableFuture().join();
            when(refs.verify(any())).thenReturn(CompletableFuture.completedStage(null));
        }
        /**
         * 留下足够真实消息并完成一次中期压缩。
         * @param statement 本轮作者原话
         */
        void medium(String statement) {
            medium(statement, true);
        }
        /**
         * 2026/11/06：可保留原短消息用于验证质量过滤，不以填充文本改变信息含量。
         * @param statement 真实文本
         * @param expanded 是否增加既有长文本夹具的讨论上下文
         */
        void medium(String statement, boolean expanded) {
            int count = sequence == 0 ? 6 : 4;
            for (int i = 0; i < count; i++) {
                int number = ++sequence;
                var actor = actor("消息" + number);
                var message = new HistoryMessage(actor.sourceId(), actor, HistoryMessage.Role.USER,
                        statement + (expanded ? "。我们正在讨论日常偏好与项目进展，先保存真实交流，再自然整理重要的信息。".repeat(5) : ""),
                        now.plusSeconds(number), false, false);
                var context = new HistoryContext(reference, new AgentPersonalitySnapshot("人格", "teaneko", 1, "补正", 0, "基础", "", null));
                history.append(context, message, now.plus(Duration.ofDays(7))).toCompletableFuture().join();
            }
            var batch = layers.claim(stream(), settings, now.plusSeconds(100)).toCompletableFuture().join().orElseThrow();
            assertTrue(layers.commit(batch, new MemoryLayers.Digest("用户交流偏好", "用户表达了自己的日常偏好，并讨论项目进展。"), now.plusSeconds(101)).toCompletableFuture().join());
        }
        /** @return 已持久的真实分区 */
        MemoryLayers.Stream stream() { return layers.streams("").toCompletableFuture().join().getFirst(); }
        /** @return 已空闲到可整理的固定中期输入 */
        LongMemories.Batch claim() { return longs.claim(stream(), now.plusSeconds(1000)).toCompletableFuture().join().orElseThrow(); }
        /**
         * 以最新可验证原话建立测试候选，不由测试直接写数据库。
         * @param batch 固定输入
         * @param text 长期正文
         * @param mode 更新语义
         * @return 合格事实候选
         */
        LongMemories.Candidate candidate(LongMemories.Batch batch, String text, LongMemories.Mode mode) {
            var evidence = batch.evidence().getLast();
            var segment = batch.segments().stream().filter(value -> evidence.sequence() >= value.from() && evidence.sequence() <= value.through()).findFirst().orElseThrow();
            return new LongMemories.Candidate(LongMemories.Kind.PREFERENCE, user, "preference.drink", text, mode,
                    List.of(new LongMemories.Quote(evidence.id(), evidence.message().text().split("。")[0])), List.of(segment.id()), "");
        }
        /**
         * 完成本批长期事实提交。
         * @param text 保存正文
         * @param mode 更新语义
         * @return 提交的公开条目
         */
        LongMemories.Item learn(String text, LongMemories.Mode mode) {
            var batch = claim(); assertTrue(longs.commit(batch, List.of(candidate(batch, text, mode)), now.plusSeconds(1001)).toCompletableFuture().join());
            return longs.query(reference, user, text, 20).toCompletableFuture().join().items().stream().filter(item -> item.text().equals(text)).findFirst().orElseThrow();
        }
        /** 关闭夹具拥有的执行器。 */
        @Override public void close() { db.close(); }
    }
    /**
     * 固定真实用户身份，不允许模型参数决定作者。
     * @param source 原消息标识
     * @return 真实作者
     */
    private AgentActorContext actor(String source) { return new AgentActorContext(scope, AgentActorContext.Origin.USER, Optional.of(user), "用户", source, "群甲"); }
    /**
     * 构造具有原文凭据的宿主请求。
     * @param text 原用户文本
     * @return 固定可信请求
     */
    private AgentRunRequest request(String text) {
        return new AgentRunRequest(UUID.randomUUID(), actor("本轮"), AgentActivity.COMMAND, List.of(), null, reference, new AgentUserText("本轮", text));
    }
    /** 自动整理无需“记住”指令；重启后跨群共享画像并通过原注解编译器注册工具。 */
    @Test void consolidatesAutomaticallyAndRecallsSharedProfile() {
        try (var h = new Harness(); var timers = Executors.newSingleThreadScheduledExecutor(); var control = ExecutionControl.create()) {
            h.medium("我平时喜欢喝茶");
            try (var processor = new LayeredMemoryProcessor(h.layers, (batch, current) -> { throw new AssertionError("不应重复压缩原消息"); }, h.refs,
                    () -> settings, ignored -> new LayeredMemoryPolicy(true, true, true), Runnable::run, timers, Clock.fixed(now.plusSeconds(1000), ZoneOffset.UTC),
                    mock(ILogger.class), h.longs, (batch, current) -> CompletableFuture.completedStage(List.of(h.candidate(batch, "喜欢喝茶", LongMemories.Mode.ADD))), ignored -> true)) {
                processor.drain().toCompletableFuture().join();
            }
            var restored = new EasyDataLongTermMemoryStore(h.db.data, h.db.store, h.db.mapper, () -> settings);
            var other = new MemoryNamespaceReference(new AgentScope("test", "群乙"), MemoryNamespaceId.SHARED, 9, 0);
            var items = restored.query(other, user, "喝茶", 8).toCompletableFuture().join().items();
            assertEquals("喜欢喝茶", items.getFirst().text());
            assertFalse(h.db.mapper.writeValueAsString(items).contains("evidenceId"));
            assertFalse(restored.verify(new MemoryNamespaceReference(scope, MemoryNamespaceId.SHARED, 0, 1), items).toCompletableFuture().join());
            var access = new LongTermMemoryAccess(restored, h.refs, ignored -> new LongTermMemoryAccess.Policy(true, true, true, true), Runnable::run, Clock.fixed(now, ZoneOffset.UTC));
            var definition = new AgentDefinition("测试", new LLMModelOptionsSnapshot(null), Set.of(AgentActivity.COMMAND, AgentActivity.TOOL));
            var source = new LongTermMemoryPromptSource(access, h.db.mapper, Runnable::run);
            var fragments = source.resolve(definition, request("你记得我的偏好吗"), null, control).toCompletableFuture().join();
            assertTrue(fragments.getFirst().text().contains("喜欢喝茶"));
            assertEquals(1, fragments.size());
            assertTrue(source.resolve(definition, request("请忘记我的饮料偏好"), null, control).toCompletableFuture().join().isEmpty());
            var original = List.<org.zexnocs.teanekoagent.component.llm.framework.message.interfaces.ILLMMessage>of(
                    org.zexnocs.teanekoagent.component.llm.framework.message.LLMUserMessage.builder().contents(
                            new org.zexnocs.teanekoagent.component.llm.framework.message.content.LLMContentListBuilder().addText("你好").build()).build());
            source.verify(request("你好"), new AgentPromptInput(original, fragments, 0), control).toCompletableFuture().join();
            var tools = new LongTermMemoryTools(access);
            // 2026/11/05：每个提供者仅声明一个工具，组合仍按真实原文决定可见能力。
            var providers = List.of(tools, new LongTermProfileTool(tools),
                    new LongTermForgetTool(tools), new LongTermCorrectTool(tools));
            providers.forEach(TeaNekoAgentToolMethods::prepare);
            IAgentToolSource selectedTools = (currentDefinition, currentRequest, runtime) -> providers.stream()
                    .flatMap(provider -> TeaNekoAgentToolMethods.select(provider, currentDefinition,
                            currentRequest, runtime, null, h.db.mapper, Runnable::run).stream())
                    .toList();
            assertEquals(2, selectedTools.select(definition, request("你是不是忘记我了"), null).size());
            assertEquals(3, selectedTools.select(definition, request("请忘记我的饮料偏好"), null).size());
            var operation = UUID.randomUUID(); var item = items.getFirst();
            restored.change(reference, user, item.id(), item.revision(), null, operation, "真实维护消息", now).toCompletableFuture().join();
            assertThrows(CompletionException.class, () -> source.verify(request("你好"), new AgentPromptInput(original, fragments, 0), control).toCompletableFuture().join());
            // 重新绑定并重放同一平台消息，新的片段 ID 与正文不能绕过来源墓碑。
            var rebound = new MemoryNamespaceReference(scope, MemoryNamespaceId.SHARED, 1, 0);
            var reboundContext = new HistoryContext(rebound, new AgentPersonalitySnapshot("人格", "teaneko", 1, "补正", 0, "基础", "", null));
            for (var raw : h.layers.read(reference, now, settings.retention()).toCompletableFuture().join().recent()) {
                h.history.append(reboundContext, raw.message(), raw.expiresAt()).toCompletableFuture().join();
            }
            var stream = h.layers.streams("").toCompletableFuture().join().stream().filter(value -> value.reference().equals(rebound)).findFirst().orElseThrow();
            var shortBatch = h.layers.claim(stream, settings, now.plusSeconds(1100)).toCompletableFuture().join().orElseThrow();
            assertTrue(h.layers.commit(shortBatch, new MemoryLayers.Digest("重新导入", "旧偏好对话"), now.plusSeconds(1101)).toCompletableFuture().join());
            var replay = restored.claim(stream, now.plusSeconds(2000)).toCompletableFuture().join().orElseThrow();
            assertTrue(restored.commit(replay, List.of(h.candidate(replay, "日常偏爱茶饮", LongMemories.Mode.ADD)), now.plusSeconds(2001)).toCompletableFuture().join());
            assertTrue(restored.query(rebound, user, "", 8).toCompletableFuture().join().items().isEmpty());
        }
    }
    /** 伪造主体与原话不能推进水位；写入失败回滚记录和索引，旧租约不能覆盖重领任务。 */
    @Test void validatesEvidenceAndCommitsAtomically() {
        try (var h = new Harness()) {
            h.medium("我喜欢喝茶"); var batch = h.claim(); var valid = h.candidate(batch, "喜欢喝茶", LongMemories.Mode.ADD);
            var forged = new LongMemories.Candidate(valid.kind(), UUID.randomUUID(), valid.attribute(), valid.text(), valid.mode(), valid.quotes(), valid.segments(), "");
            assertThrows(CompletionException.class, () -> h.longs.commit(batch, List.of(forged), now.plusSeconds(1001)).toCompletableFuture().join());
            var fakeQuote = new LongMemories.Candidate(valid.kind(), user, valid.attribute(), valid.text(), valid.mode(),
                    List.of(new LongMemories.Quote(valid.quotes().getFirst().evidenceId(), "我喜欢咖啡")), valid.segments(), "");
            assertThrows(CompletionException.class, () -> h.longs.commit(batch, List.of(fakeQuote), now.plusSeconds(1001)).toCompletableFuture().join());
            h.db.failingWritePrefix.set("entries:");
            assertThrows(CompletionException.class, () -> h.longs.commit(batch, List.of(valid), now.plusSeconds(1001)).toCompletableFuture().join());
            h.db.failingWritePrefix.set(null);
            assertTrue(h.longs.query(reference, user, "", 8).toCompletableFuture().join().items().isEmpty());
            var replacement = h.longs.claim(h.stream(), now.plusSeconds(1100)).toCompletableFuture().join().orElseThrow();
            assertFalse(h.longs.commit(batch, List.of(valid), now.plusSeconds(1101)).toCompletableFuture().join());
            assertTrue(h.longs.commit(replacement, List.of(valid), now.plusSeconds(1101)).toCompletableFuture().join());
            assertFalse(h.longs.commit(replacement, List.of(valid), now.plusSeconds(1102)).toCompletableFuture().join());
        }
    }
    /** 2026/11/05：准确区分原话、作者与同批错误片段，非法混合批次不得留下部分记录。 */
    @Test void evidenceReasonsAreSpecificAndAtomic() {
        try (var h = new Harness()) {
            h.medium("私密原话：我喜欢喝茶");
            h.medium("私密原话：我喜欢咖啡");
            var batch = h.claim();
            var valid = h.candidate(batch, "私密候选正文", LongMemories.Mode.ADD);
            var invalid = List.of(
                    new LongMemories.Candidate(valid.kind(), user, valid.attribute(), valid.text(), valid.mode(),
                            List.of(new LongMemories.Quote(valid.quotes().getFirst().evidenceId(), "模型改写的私密原话")), valid.segments(), ""),
                    new LongMemories.Candidate(valid.kind(), UUID.randomUUID(), valid.attribute(), valid.text(), valid.mode(),
                            valid.quotes(), valid.segments(), ""),
                    new LongMemories.Candidate(valid.kind(), user, valid.attribute(), valid.text(), valid.mode(),
                            valid.quotes(), List.of(batch.segments().getFirst().id()), ""));
            var codes = List.of(MemoryEvidenceException.Code.QUOTE_MISMATCH,
                    MemoryEvidenceException.Code.SUBJECT_MISMATCH, MemoryEvidenceException.Code.SEGMENT_MISMATCH);
            for (int i = 0; i < invalid.size(); i++) {
                var candidate = invalid.get(i);
                var failure = assertThrows(CompletionException.class,
                        () -> h.longs.commit(batch, List.of(valid, candidate), now.plusSeconds(1001)).toCompletableFuture().join());
                var detail = evidenceFailure(failure);
                assertEquals(codes.get(i), detail.code());
                assertEquals(MemoryEvidenceException.Domain.MEMORIES, detail.domain());
                assertEquals(1, detail.index());
                assertNull(detail.modelStatus());
                // 2026/11/06：夹具保留原异常，再显式叠加生产任务包装验证安全原因仍可读取。
                var wrapped = new CompletionException(new org.zexnocs.teanekocore.actuator.task.exception.TaskNoRetryRuntimeException(
                        "不可公开的私密包装正文", failure));
                var safe = org.zexnocs.teanekocore.logger.FailureDiagnostic.from(wrapped).toString();
                assertSame(detail, evidenceFailure(wrapped));
                assertEquals(codes.get(i).name(), LongMemoryJobs.Failure.from(wrapped).code());
                assertTrue(safe.contains(codes.get(i).name()));
                assertTrue(safe.contains("memories[1]"));
                assertFalse(safe.contains("私密"));
                assertTrue(h.longs.query(reference, user, "", 20).toCompletableFuture().join().items().isEmpty());
                assertEquals(0, h.longs.consolidationState(reference, now, () -> {}).toCompletableFuture().join().through());
            }
            assertTrue(h.longs.commit(batch, List.of(valid), now.plusSeconds(1001)).toCompletableFuture().join());
            assertEquals(batch.segments().getLast().through(),
                    h.longs.consolidationState(reference, now, () -> {}).toCompletableFuture().join().through());
        }
    }

    /** 2026/11/05：预检通过后，事务仍拒绝已经丢失、变化或到期的原消息。 */
    @Test void authoritativeEvidenceIsRecheckedAfterPrevalidation() {
        for (var code : List.of(MemoryEvidenceException.Code.EVIDENCE_MISSING,
                MemoryEvidenceException.Code.EVIDENCE_CHANGED, MemoryEvidenceException.Code.EVIDENCE_EXPIRED)) {
            try (var h = new Harness()) {
                h.medium("私密原话");
                var batch = h.claim();
                var valid = h.candidate(batch, "私密候选", LongMemories.Mode.ADD);
                assertFalse(LongMemoryEvidence.validate(batch,
                        new org.zexnocs.teanekoagent.component.personality.StyleObservations.Analysis(List.of(valid), List.of()), null).isEmpty());
                var source = batch.evidence().getLast();
                String key = String.format(Locale.ROOT, "%020d", source.sequence());
                String target = "short:" + batch.stream().id();
                if (code == MemoryEvidenceException.Code.EVIDENCE_MISSING) {
                    h.db.removeFactRow("agent.memory.layers.v1", target, key);
                } else {
                    var original = h.db.mapper.readValue(h.db.storedValue("agent.memory.layers.v1", target, key), MemoryLayers.Message.class);
                    var message = original.message();
                    if (code == MemoryEvidenceException.Code.EVIDENCE_CHANGED) {
                        message = new HistoryMessage(message.messageId(), message.source(), message.role(), "已经变化的私密正文",
                                message.occurredAt(), message.estimatedTime(), message.truncated());
                    }
                    h.db.seedFactRow("agent.memory.layers.v1", target, key, h.db.mapper.writeValueAsString(new MemoryLayers.Message(
                            source.sequence(), message, original.personality(), code == MemoryEvidenceException.Code.EVIDENCE_EXPIRED
                            ? now.plusSeconds(1000) : original.expiresAt())));
                }
                var failure = assertThrows(CompletionException.class,
                        () -> h.longs.commit(batch, List.of(valid), now.plusSeconds(1001)).toCompletableFuture().join());
                assertEquals(code, evidenceFailure(failure).code());
                assertFalse(LongMemoryJobs.Failure.from(failure).invalidCandidate());
                assertTrue(h.longs.query(reference, user, "", 20).toCompletableFuture().join().items().isEmpty());
            }
        }
    }

    /** 2026/11/05：同输入三次无效后重启仍暂停，授权恢复幂等，旧租约及未授权恢复均拒绝。 */
    @Test void invalidInputPausesPersistentlyAndResumesOnlyWithAuthorityOrNewInput() {
        for (boolean inputChanged : List.of(false, true)) {
            try (var h = new Harness()) {
                h.medium("我喜欢喝茶");
                LongMemories.Batch old = null;
                for (int attempt = 0; attempt < LongMemoryJobs.INVALID_LIMIT; attempt++) {
                    old = h.longs.claim(h.stream(), now.plusSeconds(1000L + attempt * 4000L)).toCompletableFuture().join().orElseThrow();
                    var failure = new MemoryEvidenceException(MemoryEvidenceException.Code.QUOTE_MISMATCH,
                            MemoryEvidenceException.Domain.MEMORIES, 0, old.leaseId(), old.evidence().getFirst().id());
                    h.longs.retry(old, now.plusSeconds(1001L + attempt * 4000L), new CompletionException(failure)).toCompletableFuture().join();
                }
                var original = Objects.requireNonNull(old);
                var restored = new EasyDataLongTermMemoryStore(h.db.data, h.db.store, h.db.mapper, () -> settings);
                var at = now.plusSeconds(16000);
                var state = restored.consolidationState(reference, at, () -> {}).toCompletableFuture().join();
                assertEquals(LongMemoryJobs.State.PAUSED, state.state());
                assertEquals(3, state.invalidAttempts());
                assertEquals(0, state.through());
                assertTrue(restored.claim(h.stream(), at).toCompletableFuture().join().isEmpty());
                if (inputChanged) {
                    h.medium("新输入不会偷偷跳过前缀");
                } else {
                    var manager = new AgentActorContext(scope, AgentActorContext.Origin.MANAGEMENT, Optional.of(user), "", "恢复", "群甲");
                    var operation = UUID.randomUUID();
                    assertThrows(CompletionException.class, () -> restored.resumeConsolidation(reference, actor("伪造"),
                            state.revision(), operation, at, () -> {}).toCompletableFuture().join());
                    assertThrows(CompletionException.class, () -> restored.resumeConsolidation(reference, manager,
                            state.revision(), operation, at, () -> { throw new IllegalStateException("管理权限已撤销"); })
                            .toCompletableFuture().join());
                    assertEquals(LongMemoryJobs.State.PAUSED, restored.consolidationState(reference, at, () -> {}).toCompletableFuture().join().state());
                    var result = restored.resumeConsolidation(reference, manager, state.revision(), operation, at, () -> {}).toCompletableFuture().join();
                    assertEquals(result, restored.resumeConsolidation(reference, manager, state.revision(), operation, at, () -> {}).toCompletableFuture().join());
                    assertEquals(result, restored.consolidationOperation(reference, manager, operation, () -> {}).toCompletableFuture().join());
                    assertThrows(CompletionException.class, () -> restored.resumeConsolidation(reference, manager,
                            state.revision(), UUID.randomUUID(), at, () -> {}).toCompletableFuture().join());
                }
                var next = restored.claim(h.stream(), at.plusSeconds(1)).toCompletableFuture().join().orElseThrow();
                assertFalse(restored.commit(original, List.of(), at.plusSeconds(2)).toCompletableFuture().join());
                assertTrue(restored.commit(next, List.of(h.candidate(next, "修正的候选", LongMemories.Mode.ADD)), at.plusSeconds(2))
                        .toCompletableFuture().join());
                var done = restored.consolidationState(reference, at.plusSeconds(3), () -> {}).toCompletableFuture().join();
                assertEquals(0, done.invalidAttempts());
                assertEquals(next.segments().getLast().through(), done.through());
            }
        }
    }

    /**
     * 2026/11/05：穿过完成阶段与数据库任务异常包装读取领域原因，不依赖异常文本猜测。
     * @param failure 完成阶段异常
     * @return 实际证据异常
     */
    private static MemoryEvidenceException evidenceFailure(Throwable failure) {
        for (int depth = 0; failure != null && depth < 24; depth++, failure = failure.getCause()) {
            if (failure instanceof MemoryEvidenceException evidence) return evidence;
        }
        throw new AssertionError("缺少证据领域原因");
    }

    /**
     * 2026/11/06：入队时有效的原证据在真正事务开始前过期，固定时钟推进代替 sleep。
     * @throws Exception 当有界事务协调失败时
     */
    @Test
    void queuedCommitUsesActualEvidenceTime() throws Exception {
        try (var h = new Harness()) {
            h.medium("我喜欢喝茶");
            var batch = h.claim();
            h.db.rewriteRows("short:", raw -> {
                var original = h.db.mapper.readValue(raw, MemoryLayers.Message.class);
                return h.db.mapper.writeValueAsString(new MemoryLayers.Message(original.sequence(), original.message(),
                        original.personality(), now.plusSeconds(1005)));
            });
            var time = new java.util.concurrent.atomic.AtomicReference<>(now.plusSeconds(1001));
            var clock = mock(Clock.class);
            when(clock.instant()).thenAnswer(ignored -> time.get());
            var store = new EasyDataLongTermMemoryStore(h.db.data, h.db.store, h.db.mapper, () -> settings, null, clock);
            var entered = new CompletableFuture<Void>();
            var release = new CompletableFuture<Void>();
            h.db.beforeTask.set(name -> {
                if (name.contains("提交长期记忆和画像证据")) {
                    entered.complete(null);
                    release.orTimeout(5, TimeUnit.SECONDS).join();
                }
            });
            var submitted = store.commit(batch, List.of(h.candidate(batch, "喝茶", LongMemories.Mode.ADD)), time.get()).toCompletableFuture();
            try {
                entered.get(3, TimeUnit.SECONDS);
                time.set(now.plusSeconds(1020));
            } finally {
                release.complete(null);
            }
            var failure = assertThrows(java.util.concurrent.ExecutionException.class, () -> submitted.get(3, TimeUnit.SECONDS));
            assertEquals(MemoryEvidenceException.Code.EVIDENCE_EXPIRED, evidenceFailure(failure).code());
            assertEquals(0, store.consolidationState(reference, time.get(), () -> {}).toCompletableFuture().join().through());
        }
    }

    /**
     * 2026/11/06：旧格式兼容不能放宽已存在的非法字段，失败原因不导出原字段值。
     * @throws Exception 当有界数据库操作失败时
     */
    @Test
    void malformedJobsKeepOriginalDataAndReportSafeFields() throws Exception {
        try (var h = new Harness()) {
            h.medium("原话不能出现在诊断中");
            h.claim();
            var key = h.stream().id();
            var original = h.db.storedValue("agent.memory.long.v1", "jobs", key);
            var node = assertInstanceOf(tools.jackson.databind.node.ObjectNode.class, h.db.mapper.readTree(original));
            node.put("revision", "不可公开的私密内容");
            var malformed = h.db.mapper.writeValueAsString(node);
            h.db.seedFactRow("agent.memory.long.v1", "jobs", key, malformed);
            var failure = assertThrows(java.util.concurrent.ExecutionException.class, () -> h.longs.claim(h.stream(), now.plusSeconds(2000))
                    .toCompletableFuture().get(3, TimeUnit.SECONDS));
            var safe = org.zexnocs.teanekocore.logger.FailureDiagnostic.from(failure).toString();
            assertTrue(safe.contains("MEMORY_STORAGE_FORMAT"));
            assertTrue(safe.contains("记录类型=Job"));
            assertTrue(safe.contains("字段=revision"));
            assertFalse(safe.contains("私密"));
            assertFalse(safe.contains("原话"));
            assertEquals(new LongMemoryJobs.Failure("MEMORY_STORAGE_FORMAT", false), LongMemoryJobs.Failure.from(failure));
            assertEquals(malformed, h.db.storedValue("agent.memory.long.v1", "jobs", key));
            h.db.seedFactRow("agent.memory.long.v1", "jobs", key, original);
            assertTrue(h.longs.claim(h.stream(), now.plusSeconds(2000)).toCompletableFuture().get(3, TimeUnit.SECONDS).isPresent());
        }
    }

    /** 2026/11/06：旧作业无新增字段仍可提交，网页恢复及回执通过真实存储，绑定版本不丢失。 */
    @Test
    void legacyJobsAndConsoleReplayRemainUsable() {
        try (var h = new Harness(); var control = ExecutionControl.create()) {
            h.medium("我喜欢喝茶");
            var batch = h.claim();
            h.db.rewriteRows("jobs", raw -> {
                var node = (tools.jackson.databind.node.ObjectNode) h.db.mapper.readTree(raw);
                node.remove("trouble");
                node.remove("revision");
                return h.db.mapper.writeValueAsString(node);
            });
            assertTrue(h.longs.commit(batch, List.of(), now.plusSeconds(1001)).toCompletableFuture().join());
            var bound = new MemoryNamespaceReference(scope, MemoryNamespaceId.SHARED, 3, 0);
            h.db.seedFactRow("agent.memory.long.v1", "jobs", MemoryLayers.stream(bound), h.db.mapper.writeValueAsString(Map.of(
                    "through", 0, "until", Instant.EPOCH, "retryAt", Instant.EPOCH, "failures", 3, "claimedThrough", 0,
                    "fingerprint", "", "revision", 7, "trouble", Map.of("fingerprint", "输入摘要", "invalidAttempts", 3,
                    "code", "QUOTE_MISMATCH", "at", now))));
            var bindings = mock(MemoryNamespaceBindings.class);
            when(bindings.readReference(scope)).thenReturn(CompletableFuture.completedStage(Optional.of(bound)));
            var service = new org.zexnocs.teanekoagent.integration.web.AgentLayersConsoleService(h.layers, h.longs,
                    bindings, h.db.store, new org.springframework.mock.env.MockEnvironment());
            var context = new org.zexnocs.teanekocore.plugin.management.ManagementContext("管理员",
                    org.zexnocs.teanekoapp.web.client.ManagementTargets.encode(scope.clientId(), scope.scopeId()), UUID.randomUUID(), control);
            var view = assertInstanceOf(org.zexnocs.teanekoagent.integration.web.AgentLayersConsole.View.class,
                    service.execute(context, new org.zexnocs.teanekoagent.integration.web.AgentLayersConsole.Query(
                            "READ", "", -1, null, "", null, -1, null, null)).toCompletableFuture().join());
            assertEquals(LongMemoryJobs.State.PAUSED, Objects.requireNonNull(view.consolidation()).state());
            var operation = UUID.randomUUID();
            var replay = new org.zexnocs.teanekoagent.integration.web.AgentLayersConsole.Query("REPLAY", "default", 0,
                    null, "", null, 7, operation, null);
            var result = service.execute(context, replay).toCompletableFuture().join();
            assertEquals(result, service.execute(context, new org.zexnocs.teanekoagent.integration.web.AgentLayersConsole.Query(
                    "REPLAY_OPERATION", "default", 0, null, "", null, 7, operation, null)).toCompletableFuture().join());
            assertEquals(LongMemoryJobs.State.READY, h.longs.consolidationState(bound, now, () -> {}).toCompletableFuture().join().state());
        }
    }
    /** 变化替代旧值，本人维护按版本且幂等；同一旧证据换正文仍不能复活已遗忘信息。 */
    @Test void replacesAndProtectsManualChangesAndTombstones() {
        try (var h = new Harness()) {
            h.medium("我喜欢喝茶"); var old = h.learn("喜欢喝茶", LongMemories.Mode.ADD);
            h.medium("我现在改喝咖啡了"); var current = h.learn("现在喜欢咖啡", LongMemories.Mode.REPLACE);
            assertFalse(h.longs.verify(reference, List.of(old)).toCompletableFuture().join());
            assertEquals(1, h.longs.query(reference, user, "", 20).toCompletableFuture().join().items().size());
            assertThrows(CompletionException.class, () -> h.longs.change(reference, UUID.randomUUID(), current.id(), current.revision(), null,
                    UUID.randomUUID(), "伪造维护", now).toCompletableFuture().join());
            var correction = UUID.randomUUID();
            var corrected = h.longs.change(reference, user, current.id(), current.revision(), "只喝无咖啡因咖啡", correction, "纠正消息", now).toCompletableFuture().join();
            assertEquals(corrected, h.longs.change(reference, user, current.id(), current.revision(), "只喝无咖啡因咖啡", correction, "纠正消息", now).toCompletableFuture().join());
            assertThrows(CompletionException.class, () -> h.longs.change(reference, user, current.id(), current.revision(), "换参", correction, "纠正消息", now).toCompletableFuture().join());
            var forgotten = h.longs.change(reference, user, corrected.id(), corrected.revision(), null, UUID.randomUUID(), "遗忘消息", now).toCompletableFuture().join();
            assertEquals(LongMemories.Status.FORGOTTEN, forgotten.status());
            assertTrue(h.longs.query(reference, user, "", 20).toCompletableFuture().join().items().isEmpty());
            // 同批第二条候选在人工维护之后重读旧来源，不能以新正文、新属性重新登记。
            var sourceBatch = h.longs.claim(h.stream(), now.plusSeconds(2000)).toCompletableFuture().join();
            assertTrue(sourceBatch.isEmpty());
            h.medium("我现在改喝咖啡了"); var fresh = h.claim();
            var candidate = h.candidate(fresh, "现在喜欢咖啡", LongMemories.Mode.ADD);
            assertTrue(h.longs.commit(fresh, List.of(candidate), now.plusSeconds(1001)).toCompletableFuture().join());
            assertTrue(h.longs.query(reference, user, "", 20).toCompletableFuture().join().items().isEmpty());
        }
    }
    /** 模型返回前撤销学习权限不提交；关闭不让迟到结果交付，后续可恢复原中期任务。 */
    @Test void rejectsLateLearningAfterPolicyWithdrawal() {
        try (var h = new Harness(); var timers = Executors.newSingleThreadScheduledExecutor()) {
            h.medium("我喜欢喝茶"); var learning = new AtomicBoolean(true);
            var entered = new CompletableFuture<LongMemories.Batch>(); var late = new CompletableFuture<List<LongMemories.Candidate>>();
            try (var processor = new LayeredMemoryProcessor(h.layers, (batch, control) -> CompletableFuture.completedStage(new MemoryLayers.Digest("摘要", "中期")), h.refs,
                    () -> settings, ignored -> new LayeredMemoryPolicy(true, true, true), Runnable::run, timers, Clock.fixed(now.plusSeconds(1000), ZoneOffset.UTC), mock(ILogger.class),
                    h.longs, (batch, control) -> { entered.complete(batch); return late; }, ignored -> learning.get())) {
                var work = processor.drain().toCompletableFuture(); var batch = entered.orTimeout(5, TimeUnit.SECONDS).join();
                learning.set(false); late.complete(List.of(h.candidate(batch, "喜欢喝茶", LongMemories.Mode.ADD))); work.join();
                assertTrue(h.longs.query(reference, user, "", 8).toCompletableFuture().join().items().isEmpty());
                assertTrue(h.longs.claim(h.stream(), now.plusSeconds(1100)).toCompletableFuture().join().isPresent());
            }
        }
    }
    /**
     * 实际学习适配器保持统一门控与无工具请求，严格拒绝模型额外字段，释放自有实例。
     * 2026/11/04：验证统一预算不扩大宿主模型的输出配置。
     * 2026/11/05：联合输出形状错误明确拒绝，解析错误不携带模型原文。
     */
    @Test void usesGuardedModelAdapterAndStrictCandidateSchema() {
        try (var h = new Harness(); var registry = new AgentRegistry(); var control = ExecutionControl.create()) {
            h.medium("我喜欢喝茶"); var batch = h.claim();
            var runtime = new AgentRuntimeSnapshot(new LLMModelOptionsSnapshot(LLMModelOptions.builder().provider("test").model("离线模型").maxTokens(4096).build()),
                    ModelCapabilities.textOnly(), LLMInteractionSettings.defaults());
            var gate = new AgentAccessGate(registry, ignored -> new AgentScopePolicy(true, true, AgentScopePolicy.Mode.ACTIVE),
                    ignored -> new AgentReadiness(AgentReadiness.Status.READY, Optional.of(runtime)), mock(ILogger.class));
            var interactions = mock(ILLMInteractionService.class);
            var output = new java.util.concurrent.atomic.AtomicReference<>(h.db.mapper.writeValueAsString(Map.of("memories", List.of(h.candidate(batch, "喜欢喝茶", LongMemories.Mode.ADD)), "styles", List.of())));
            when(interactions.interact(any(), any(), any(), any(), any())).thenAnswer(call -> {
                org.zexnocs.teanekoagent.component.llm.framework.input.interfaces.ILLMPrompt prompt = call.getArgument(0);
                assertTrue(Objects.requireNonNull(prompt.getOptions().getTools()).isEmpty());
                // 2026/11/04：整理不再硬限 3072，模型配置为 4096 时保留该更小上限。
                assertEquals(4096, prompt.getOptions().getMaxTokens());
                var answer = LLMAssistantMessage.builder().contents(new LLMContentListBuilder().addText(output.get()).build()).build();
                return CompletableFuture.completedStage(new LLMInteractionResult(LLMInteractionResult.Status.SUCCESS, List.of(answer), answer, List.of(), List.of(), Map.of()));
            });
            try (var generator = new LLMLongTermMemoryConsolidator(() -> new AgentFactory(registry), gate, h.refs, LLMJsonTestSupport.create(interactions, h.db.mapper),
                    () -> settings, ignored -> true, h.db.mapper, Runnable::run)) {
                assertEquals(0, registry.snapshots().size());
                var values = generator.consolidate(batch, control).toCompletableFuture().join();
                assertEquals("喜欢喝茶", values.getFirst().text());
                // 2026/11/05：结构合法但原话改写的输出在生成阶段拒绝，保留实际 SUCCESS 与安全原因。
                var validOutput = output.get();
                output.set(validOutput.replace("我喜欢喝茶", "私密改写原话"));
                var mismatched = assertThrows(CompletionException.class, () -> generator.consolidate(batch, control).toCompletableFuture().join());
                assertEquals(MemoryEvidenceException.Code.QUOTE_MISMATCH, evidenceFailure(mismatched).code());
                assertEquals(LLMInteractionResult.Status.SUCCESS, evidenceFailure(mismatched).modelStatus());
                assertFalse(org.zexnocs.teanekocore.logger.FailureDiagnostic.from(mismatched).toString().contains("私密"));
                output.set(validOutput);
                // 2026/11/03：同次联合协议保持严格结构，不把表达观察塞入用户事实。
                var basis = new org.zexnocs.teanekoagent.component.personality.AgentPersonalitySnapshot("人格", "teaneko", 1, "scope", 0, "友善自然", "", null);
                var styleTarget = new org.zexnocs.teanekoagent.component.personality.StyleObservations.Target(basis, Set.of("speakingStyle"),
                        batch.evidence().stream().map(LongMemories.Evidence::id).collect(java.util.stream.Collectors.toSet()), List.of());
                var style = new org.zexnocs.teanekoagent.component.personality.StyleObservations.Candidate("speakingStyle", "先说结论", org.zexnocs.teanekoagent.component.personality.StyleObservations.Context.GENERAL,
                        values.getFirst().quotes(), values.getFirst().segments());
                var originalOutput = output.get();
                output.set(h.db.mapper.writeValueAsString(new org.zexnocs.teanekoagent.component.personality.StyleObservations.Analysis(values, List.of(style))));
                assertEquals(1, generator.analyze(batch, styleTarget, control).toCompletableFuture().join().styles().size());
                output.set("[]");
                var wrongShape = assertThrows(CompletionException.class,
                        () -> generator.analyze(batch, styleTarget, control).toCompletableFuture().join());
                assertTrue(org.zexnocs.teanekocore.logger.FailureDiagnostic.from(wrongShape).description().contains("要求 object"));
                output.set(originalOutput);
                output.set(output.get().replace("\"kind\":", "\"unexpected\":\"private-model-value\",\"kind\":"));
                var invalid = assertThrows(CompletionException.class, () -> generator.consolidate(batch, control).toCompletableFuture().join());
                var diagnostic = org.zexnocs.teanekocore.logger.FailureDiagnostic.from(invalid);
                assertTrue(diagnostic.description().contains("额外字段"));
                assertTrue(diagnostic.description().contains("JSON_SHAPE"));
                assertFalse(diagnostic.toString().contains("private-model-value"));
                output.set("{\"memories\":[],\"styles\":[]}");
                assertTrue(generator.consolidate(batch, control).toCompletableFuture().join().isEmpty());
                assertTrue(h.longs.commit(batch, List.of(), now.plusSeconds(1001)).toCompletableFuture().join());
                assertTrue(h.longs.claim(h.stream(), now.plusSeconds(2000)).toCompletableFuture().join().isEmpty());
            }
            assertTrue(registry.find(registry.snapshots().getFirst().id()).orElseThrow().isClosed());
        }
    }
}
