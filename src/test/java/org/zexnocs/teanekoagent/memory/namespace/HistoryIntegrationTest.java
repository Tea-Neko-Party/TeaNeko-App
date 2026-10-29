package org.zexnocs.teanekoagent.memory.namespace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.data.domain.Pageable;
import org.zexnocs.teanekoagent.adapter.history.AgentHistoryToolSource;
import org.zexnocs.teanekoagent.adapter.history.HistoryDataObject;
import org.zexnocs.teanekoagent.adapter.history.HistoryRepository;
import org.zexnocs.teanekoagent.adapter.history.JpaHistoryStore;
import org.zexnocs.teanekoagent.agent.*;
import org.zexnocs.teanekoagent.agent.access.AgentAccessGate;
import org.zexnocs.teanekoagent.agent.access.AgentReadiness;
import org.zexnocs.teanekoagent.agent.access.AgentRuntimeSnapshot;
import org.zexnocs.teanekoagent.agent.access.AgentScopePolicy;
import org.zexnocs.teanekoagent.agent.memory.AgentMemoryReferenceException;
import org.zexnocs.teanekoagent.agent.memory.IAgentMemoryReferences;
import org.zexnocs.teanekoagent.agent.personality.AgentPersonalitySnapshot;
import org.zexnocs.teanekoagent.agent.personality.IAgentPersonalitySource;
import org.zexnocs.teanekoagent.agent.prompt.AgentPromptException;
import org.zexnocs.teanekoagent.agent.prompt.IAgentPromptSource;
import org.zexnocs.teanekoagent.history.*;
import org.zexnocs.teanekoagent.llm.framework.input.interfaces.ILLMPrompt;
import org.zexnocs.teanekoagent.llm.framework.interaction.LLMInteractionService;
import org.zexnocs.teanekoagent.llm.framework.interaction.LLMInteractionSettings;
import org.zexnocs.teanekoagent.llm.framework.message.LLMAssistantMessage;
import org.zexnocs.teanekoagent.llm.framework.message.LLMUserMessage;
import org.zexnocs.teanekoagent.llm.framework.message.content.LLMContentListBuilder;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptions;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptionsSnapshot;
import org.zexnocs.teanekoagent.llm.framework.model.ModelCapabilities;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.ILLMModelService;
import org.zexnocs.teanekoagent.llm.framework.response.LLMChoice;
import org.zexnocs.teanekoagent.llm.framework.response.LLMResult;
import org.zexnocs.teanekoagent.llm.framework.tool.LLMToolCall;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.actuator.execution.ExecutionPolicy;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.logger.ILogger;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 复用原事务任务夹具，集中验证历史实际存储、重建读取、绑定隔离和真实模型工具往返。
 * @author zExNocs
 * @date 2026/10/30
 * @since 4.0.0
 * @version 4.5.0-l
 */
@Timeout(20)
class HistoryIntegrationTest {
    private final Instant now = Instant.parse("2026-10-30T01:00:00Z");
    private final AgentScope scope = new AgentScope("客户端", "群甲");
    /**
     * 组合真实存储适配与业务服务，仅替换仓库传输；原数据库任务仍在提交后完成。
     * @author zExNocs
     * @date 2026/10/30
     * @since 4.0.0
     * @version 4.5.0-l
     */
    private final class Harness implements AutoCloseable {
        final NamespaceDatabaseFixture db = new NamespaceDatabaseFixture();
        final HistoryRepository repository = mock(HistoryRepository.class);
        final ConcurrentNavigableMap<Long, HistoryDataObject> rows = new ConcurrentSkipListMap<>();
        final AtomicLong sequence = new AtomicLong();
        final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        final ScheduledExecutorService timers = Executors.newSingleThreadScheduledExecutor();
        final AtomicReference<MemoryNamespaceReference> current = new AtomicReference<>(new MemoryNamespaceReference(scope, MemoryNamespaceId.SHARED, 1, 0));
        final AtomicReference<AgentPersonalitySnapshot> persona = new AtomicReference<>(snapshot("原人格"));
        final AtomicReference<ConversationHistoryService.Policy> policy = new AtomicReference<>(new ConversationHistoryService.Policy(true, true, true));
        final IAgentMemoryReferences references = mock(IAgentMemoryReferences.class);
        final IAgentPersonalitySource personalities = mock(IAgentPersonalitySource.class);
        final JpaHistoryStore store;
        final ConversationHistoryService history;
        /** 初始化有界仓库替身与可变实际授权，不启动模型。 */
        Harness() {
            when(repository.findByPartitionAndMessageKey(anyString(), anyString())).thenAnswer(call -> rows.values().stream()
                    .filter(row -> row.getPartition().equals(call.getArgument(0)) && row.getMessageKey().equals(call.getArgument(1))).findFirst().orElse(null));
            when(repository.saveAndFlush(any())).thenAnswer(call -> { HistoryDataObject row = call.getArgument(0); row.setId(sequence.incrementAndGet()); rows.put(row.getId(), row); return row; });
            when(repository.findByPartitionAndIdLessThanAndExpiresAtGreaterThanOrderByIdDesc(anyString(), anyLong(), any(), any())).thenAnswer(call -> {
                String partition = call.getArgument(0); long before = call.getArgument(1); Instant time = call.getArgument(2); Pageable page = call.getArgument(3);
                return rows.descendingMap().values().stream().filter(row -> row.getPartition().equals(partition) && row.getId() < before && row.getExpiresAt().isAfter(time)).limit(page.getPageSize()).toList();
            });
            when(repository.findByExpiresAtLessThanEqualOrderByExpiresAtAscIdAsc(any(), any())).thenAnswer(call -> {
                Instant time = call.getArgument(0); Pageable page = call.getArgument(1);
                return rows.values().stream().filter(row -> !row.getExpiresAt().isAfter(time)).sorted(Comparator.comparing(HistoryDataObject::getExpiresAt)).limit(page.getPageSize()).toList();
            });
            doAnswer(call -> { Iterable<HistoryDataObject> values = call.getArgument(0); values.forEach(row -> rows.remove(row.getId())); return null; }).when(repository).deleteAllInBatch(any());
            when(references.resolve(any())).thenAnswer(call -> CompletableFuture.completedStage(current.get()));
            when(references.verify(any())).thenAnswer(call -> current.get().equals(call.getArgument(0)) ? CompletableFuture.completedStage(null)
                    : CompletableFuture.failedStage(new AgentMemoryReferenceException(AgentMemoryReferenceException.Reason.CHANGED)));
            when(personalities.resolve(any(), any())).thenAnswer(call -> CompletableFuture.completedStage(persona.get()));
            when(personalities.verify(any(), any(), any())).thenAnswer(call -> persona.get().identity().equals(call.<AgentPersonalitySnapshot>getArgument(1).identity())
                    ? CompletableFuture.completedStage(null) : CompletableFuture.failedStage(new AgentPromptException(AgentPromptException.Reason.CHANGED)));
            store = new JpaHistoryStore(repository, db.database, db.mapper); history = service(store);
        }
        /**
         * 重建业务层而保留底层数据，模拟重启后的独立读取。
         * @param storage 当前存储适配
         * @return 新服务实例
         */
        ConversationHistoryService service(IHistoryStore storage) { return new ConversationHistoryService(storage, references, personalities, ignored -> policy.get(), executor, Clock.fixed(now, ZoneOffset.UTC), () -> Duration.ofDays(7)); }
        /**
         * 取得当前可信业务边界。
         * @return 当前引用及实际人格
         */
        HistoryContext context() { return new HistoryContext(current.get(), persona.get()); }
        /** 只释放本夹具拥有的执行资源。 */
        @Override public void close() { timers.shutdownNow(); executor.close(); db.close(); }
    }
    /**
     * 构造当前实际人格，身份改变用于切换隔离。
     * @param identity 可信身份
     * @return 简化人格快照
     */
    private AgentPersonalitySnapshot snapshot(String identity) { return new AgentPersonalitySnapshot(identity, "teaneko", 1, "scope-群甲", 0, "基础人格", "", null); }
    /**
     * 创建带真实 UUID 的来源。
     * @param id 稳定消息 ID
     * @return 原发言者及来源
     */
    private AgentActorContext actor(String id) { return new AgentActorContext(scope, AgentActorContext.Origin.USER, Optional.of(new UUID(0, 1)), "平台用户", id, "目标"); }
    /**
     * 创建可检索用户原文。
     * @param id 稳定来源
     * @param text 原正文
     * @param time 真实发生时刻
     * @return 不含权限声明的历史消息
     */
    private HistoryMessage message(String id, String text, Instant time) { return new HistoryMessage(id, actor(id), HistoryMessage.Role.USER, text, time, false, false); }
    /**
     * 提供默认有界近期查询。
     * @return 查询条件
     */
    private HistoryQuery query() { return new HistoryQuery(Long.MAX_VALUE, "", null, null, 8, 6000); }

    /** 验证服务重建后的读取、稳定分页和跨 client/scope、绑定、人格隔离。 */
    @Test void reconstructionPaginationAndDurableBoundaries() {
        try (var h = new Harness(); var control = ExecutionControl.create()) {
            var old = h.context(); var first = message("一", "原始提问一", now.minusSeconds(20));
            assertTrue(h.history.append(old, first, control).toCompletableFuture().join());
            assertFalse(h.history.append(old, first, control).toCompletableFuture().join());
            assertThrows(CompletionException.class, () -> h.history.append(old, message("一", "冲突正文", first.occurredAt()), control).toCompletableFuture().join());
            h.history.append(old, message("二", "原始提问二", now.minusSeconds(10)), control).toCompletableFuture().join();
            var restored = h.service(new JpaHistoryStore(h.repository, h.db.database, h.db.mapper));
            var page = restored.search(h.context(), new HistoryQuery(Long.MAX_VALUE, "原始", null, null, 1, 6000), control).toCompletableFuture().join();
            assertEquals("二", page.messages().getFirst().messageId()); assertTrue(page.nextBefore() > 0);
            var second = restored.search(h.context(), new HistoryQuery(page.nextBefore(), "", null, null, 8, 6000), control).toCompletableFuture().join();
            assertEquals(List.of(first), second.messages()); assertEquals(0, second.nextBefore());
            h.current.set(new MemoryNamespaceReference(new AgentScope("其他客户端", scope.scopeId()), MemoryNamespaceId.SHARED, 1, 0));
            assertTrue(restored.search(h.context(), query(), control).toCompletableFuture().join().messages().isEmpty());
            h.current.set(new MemoryNamespaceReference(scope, MemoryNamespaceId.SHARED, 2, 0));
            assertTrue(restored.search(h.context(), query(), control).toCompletableFuture().join().messages().isEmpty());
            assertThrows(CompletionException.class, () -> restored.search(old, query(), control).toCompletableFuture().join());
            h.current.set(old.reference()); h.persona.set(snapshot("新人格"));
            assertTrue(restored.search(h.context(), query(), control).toCompletableFuture().join().messages().isEmpty());
        }
    }
    /**
     * 验证原事务提交等待、过期即时隐藏、定量清理及采集/查询独立开关。
     * @throws Exception 当提交屏障或异步任务未按时完成时
     */
    @Test void commitRetentionAndIndependentSwitches() throws Exception {
        try (var h = new Harness(); var control = ExecutionControl.create()) {
            var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
            h.db.beforeCommit.set(() -> { entered.countDown(); try { if (!release.await(5, TimeUnit.SECONDS)) { throw new IllegalStateException("测试提交等待超时。"); } } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException("测试提交被中断。"); } });
            var pending = h.store.append(h.context(), message("旧", "过期文本", now.minus(Duration.ofDays(7))), now).toCompletableFuture();
            try { assertTrue(entered.await(5, TimeUnit.SECONDS)); assertFalse(pending.isDone()); } finally { release.countDown(); }
            assertTrue(pending.join()); h.db.beforeCommit.set(() -> {});
            assertTrue(h.history.search(h.context(), query(), control).toCompletableFuture().join().messages().isEmpty());
            assertEquals(1, h.store.prune(now).toCompletableFuture().join());
            h.policy.set(new ConversationHistoryService.Policy(true, false, true));
            assertFalse(h.history.append(h.context(), message("新", "新正文", now), control).toCompletableFuture().join()); assertTrue(h.rows.isEmpty());
            h.policy.set(new ConversationHistoryService.Policy(true, true, false));
            assertTrue(h.history.append(h.context(), message("新", "新正文", now), control).toCompletableFuture().join());
            assertThrows(CompletionException.class, () -> h.history.search(h.context(), query(), control).toCompletableFuture().join());
            h.policy.set(new ConversationHistoryService.Policy(false, true, true));
            assertThrows(CompletionException.class, () -> h.history.append(h.context(), message("三", "禁止采集", now), control).toCompletableFuture().join());
        }
    }
    /** 验证部分结果不会截碎消息，字符不足及扫描上限保留继续游标。 */
    @Test void boundedResultsPreserveCoverageAndTime() {
        try (var h = new Harness(); var control = ExecutionControl.create()) {
            h.history.append(h.context(), message("一", "时间查询", now.minusSeconds(30)), control).toCompletableFuture().join();
            var tiny = h.history.search(h.context(), new HistoryQuery(Long.MAX_VALUE, "", null, null, 8, 2), control).toCompletableFuture().join();
            assertTrue(tiny.messages().isEmpty()); assertTrue(tiny.omittedCharacters());
            var outside = h.history.search(h.context(), new HistoryQuery(Long.MAX_VALUE, "", now.minusSeconds(10), now, 8, 6000), control).toCompletableFuture().join();
            assertTrue(outside.messages().isEmpty()); assertEquals(0, outside.nextBefore());
        }
    }
    /** 验证真实 Agent 记录用户及答案，下一轮通过真实工具往返读取历史且不能伪造 scope。 */
    @Test void realAgentRecordsAndQueriesWithoutPromptDiagnostics() {
        try (var h = new Harness(); var registry = new AgentRegistry()) {
            var handle = new AgentFactory(registry).create(new AgentDefinition("历史 Agent", new LLMModelOptionsSnapshot(null), Set.of(AgentActivity.values())));
            var runtime = new AgentRuntimeSnapshot(new LLMModelOptionsSnapshot(LLMModelOptions.builder().provider("test").model("离线模型").maxTokens(30).build()), ModelCapabilities.textOnly(), LLMInteractionSettings.defaults());
            var logger = mock(ILogger.class); var gate = new AgentAccessGate(registry, ignored -> new AgentScopePolicy(true, true, AgentScopePolicy.Mode.ACTIVE), ignored -> new AgentReadiness(AgentReadiness.Status.READY, Optional.of(runtime)), logger);
            var calls = new AtomicInteger(); var tools = new AgentHistoryToolSource(h.history, h.db.mapper); var model = mock(ILLMModelService.class);
            when(model.call(any(ILLMPrompt.class), any(ExecutionPolicy.class))).thenAnswer(call -> {
                ILLMPrompt prompt = call.getArgument(0); int step = calls.incrementAndGet();
                if (step == 3) { assertTrue(h.db.mapper.writeValueAsString(prompt.getMessages()).contains("原始提问")); assertTrue(h.db.mapper.writeValueAsString(prompt.getMessages()).contains("INVALID_ARGUMENTS")); }
                var response = step == 2 ? LLMAssistantMessage.builder().toolCalls(List.of(
                        LLMToolCall.builder().id("查历史").type("function").name("history_query").arguments("{\"keyword\":\"原始提问\"}").build(),
                        LLMToolCall.builder().id("伪造范围").type("function").name("history_query").arguments("{\"scope\":\"其他群\"}").build())).build()
                        : LLMAssistantMessage.builder().contents(new LLMContentListBuilder().addText("正常答案").build()).build();
                return new TaskFuture<>(logger, "历史离线模型", CompletableFuture.completedFuture(LLMResult.builder().choices(List.of(LLMChoice.builder().index(0).finishReason(step == 2 ? "tool_calls" : "stop").message(response).build())).build()));
            });
            try (var turns = new AgentService(gate, new LLMInteractionService(model, LLMInteractionSettings::defaults, h.executor, h.timers),
                    (definition, request, config) -> tools.select(definition, request), h.db.mapper, h.executor, h.timers, Clock.fixed(now, ZoneOffset.UTC), logger,
                    h.references, IAgentPromptSource.empty(), h.personalities, h.history)) {
                for (String text : List.of("原始提问", "查询以前的话")) {
                    var actor = actor(text); var request = new AgentRunRequest(UUID.randomUUID(), actor, AgentActivity.SERVICE,
                            List.of(LLMUserMessage.builder().contents(new LLMContentListBuilder().addText(text).build()).build()), null, null, new AgentUserText(text, text));
                    var run = turns.prepare(handle, request); run.start(); assertEquals(AgentRunResult.Status.SUCCESS, run.result().toCompletableFuture().join().status());
                }
                assertEquals(3, calls.get()); assertEquals(4, h.rows.size());
                assertTrue(h.rows.values().stream().noneMatch(row -> row.getPayload().contains("基础人格") || row.getPayload().contains("查历史")));
            }
        }
    }
}
