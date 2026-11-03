package org.zexnocs.teanekoagent.memory.namespace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.zexnocs.teanekoagent.adapter.history.EasyDataHistoryStore;
import org.zexnocs.teanekoagent.adapter.memory.EasyDataDeliveryStore;
import org.zexnocs.teanekoagent.adapter.memory.EasyDataLayeredMemoryStore;
import org.zexnocs.teanekoagent.adapter.memory.EasyDataLongTermMemoryStore;
import org.zexnocs.teanekoagent.adapter.memory.LayeredMemoryPromptSource;
import org.zexnocs.teanekoagent.adapter.platform.AgentReplyPlan;
import org.zexnocs.teanekoagent.agent.*;
import org.zexnocs.teanekoagent.agent.memory.IAgentMemoryReferences;
import org.zexnocs.teanekoagent.agent.personality.AgentPersonalitySnapshot;
import org.zexnocs.teanekoagent.history.*;
import org.zexnocs.teanekoagent.memory.access.*;
import org.zexnocs.teanekoagent.memory.fact.MemoryEventTime;
import org.zexnocs.teanekoagent.memory.fact.MemoryFactReceipt;
import org.zexnocs.teanekoagent.memory.fact.MemoryFactValue;
import org.zexnocs.teanekoagent.memory.fact.MemoryFacts;
import org.zexnocs.teanekoagent.memory.layered.LayeredMemoryPolicy;
import org.zexnocs.teanekoagent.memory.layered.LayeredMemorySettings;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 复用事务夹具验证明确保存的默认路由与实际平台确认、部分失败和重启后的防重边界。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.6.2-l
 * @version 4.6.2-l
 */
@Timeout(25)
class MemoryFinalizationIntegrationTest {
    private final Instant now = Instant.parse("2026-11-03T00:00:00Z");
    private final AgentScope scope = new AgentScope("test", "群甲");
    private final MemoryNamespaceReference reference = new MemoryNamespaceReference(scope, MemoryNamespaceId.SHARED, 0, 0);
    private final AgentActorContext actor = new AgentActorContext(scope, AgentActorContext.Origin.USER, Optional.of(new UUID(0, 1)), "用户", "原消息", "群甲");
    /**
     * 初始化真实共享代次。
     * @param db 原事务夹具
     */
    private void initialize(NamespaceDatabaseFixture db) { db.store.create(new MemoryNamespace(MemoryNamespaceId.SHARED, 0, 0, now, null, null), 10).toCompletableFuture().join(); }
    /** 明确原文立即进入长期域，重复回执不变，换参拒绝，关闭自动学习也不阻止本人明确保存。 */
    @Test void savesExplicitIntentIntoSharedLongTermStorage() {
        try (var db = new NamespaceDatabaseFixture(); var control = ExecutionControl.create()) {
            initialize(db); var refs = mock(IAgentMemoryReferences.class); when(refs.verify(reference)).thenReturn(CompletableFuture.completedStage(null));
            var store = new EasyDataLongTermMemoryStore(db.data, db.store, db.mapper, LayeredMemorySettings::defaults, null, Clock.fixed(now, ZoneOffset.UTC));
            var access = new MemoryAccessService(ignored -> new MemoryAccessPolicy(true, true, true, false), refs,
                    mock(org.zexnocs.teanekoagent.memory.recall.IMemoryRecallService.class), mock(org.zexnocs.teanekoagent.memory.profile.IUserProfileService.class),
                    mock(MemoryFacts.class), Runnable::run, command -> store.appendExplicit(command, now));
            var context = new MemoryAccessContext(UUID.randomUUID(), actor, reference); var intent = MemoryAddIntent.fromUserText(context, "请记住：喜欢喝茶").orElseThrow();
            var value = new MemoryFactValue(MemoryFactValue.Kind.PREFERENCE, intent.text(), List.of("饮品"), 1, MemoryFactValue.Importance.HIGH, MemoryEventTime.unknown(), null);
            assertThrows(CompletionException.class, () -> access.append(context, "保存", null, value, control).toCompletableFuture().join());
            var result = access.append(context, "保存", intent, value, control).toCompletableFuture().join();
            assertEquals(MemoryFactReceipt.Status.STORED, result.status());
            assertEquals(result, access.append(context, "保存", intent, value, control).toCompletableFuture().join());
            assertEquals(MemoryFactReceipt.Status.DUPLICATE, access.append(context, "再次保存", intent, value, control).toCompletableFuture().join().status());
            var other = new MemoryNamespaceReference(new AgentScope("test", "群乙"), MemoryNamespaceId.SHARED, 0, 0);
            assertEquals(result.recordId(), store.query(other, actor.userId().orElseThrow(), "喝茶", 8).toCompletableFuture().join().items().getFirst().id());
            var changed = new MemoryFactValue(value.kind(), value.text(), List.of("换标签"), 1, value.importance(), value.eventTime(), null);
            assertEquals(MemoryFactReceipt.Status.CONFLICT, access.append(context, "保存", intent, changed, control).toCompletableFuture().join().status());
            assertEquals(1, store.query(other, null, "", 8).toCompletableFuture().join().items().size());
            // 前置保存不依赖模型工具、历史采集或人格投影；默认类别不把用户经历变成机器人事实。
            var recorder = new ConversationHistoryService(mock(IHistoryStore.class), refs, mock(org.zexnocs.teanekoagent.agent.personality.IAgentPersonalitySource.class),
                    ignored -> new ConversationHistoryService.Policy(true, false, false), Runnable::run, Clock.fixed(now, ZoneOffset.UTC), () -> Duration.ofDays(7));
            recorder.setExplicit(new ExplicitMemoryCapture(access, ignored -> new MemoryAccessPolicy(true, true, true, false)));
            var explicitActor = new AgentActorContext(scope, AgentActorContext.Origin.USER, actor.userId(), "用户", "明确消息", "群甲");
            var request = new AgentRunRequest(UUID.randomUUID(), explicitActor, AgentActivity.COMMAND, List.of(), null, reference,
                    new AgentUserText("明确消息", "请记住：我住在茶城"));
            recorder.record(request, new org.zexnocs.teanekoagent.agent.prompt.AgentPromptInput(List.of(), List.of(), 0), null, control).toCompletableFuture().join();
            assertEquals("我住在茶城", store.query(other, actor.userId().orElseThrow(), "茶城", 8).toCompletableFuture().join().items().getFirst().text());
        }
    }
    /** 回执未到时不声明成功，部分成功只呈现已发送正文，未知段不自动补发，清空拒绝旧空间读取。 */
    @Test void persistsPartialDeliveryAndProjectsOnlyAcknowledgedText() {
        try (var db = new NamespaceDatabaseFixture(); var control = ExecutionControl.create()) {
            initialize(db); var store = new EasyDataDeliveryStore(db.data, db.store, db.mapper);
            var delivery = new AgentDeliveryService(store, Runnable::run, Clock.fixed(now, ZoneOffset.UTC), () -> Duration.ofDays(7));
            var id = UUID.randomUUID(); var started = new CompletableFuture<Void>(); var platform = new CompletableFuture<Void>(); var calls = new AtomicInteger();
            var first = new AgentReplyPlan.Text("已经发送的第一段");
            var work = delivery.send(reference, actor, id, 0, 2, first, () -> { calls.incrementAndGet(); started.complete(null); return platform; }).toCompletableFuture();
            started.orTimeout(5, TimeUnit.SECONDS).join(); assertFalse(work.isDone());
            assertEquals("UNKNOWN", store.read(reference, Set.of(id), now).toCompletableFuture().join().get(id).state());
            platform.complete(null); work.join();
            assertThrows(CompletionException.class, () -> delivery.send(reference, actor, id, 1, 2, new AgentReplyPlan.Text("没有确认的第二段"),
                    () -> { calls.incrementAndGet(); return CompletableFuture.failedStage(new IllegalStateException("模拟传输未完成")); }).toCompletableFuture().join());
            var restarted = new EasyDataDeliveryStore(db.data, db.store, db.mapper);
            var report = restarted.read(reference, Set.of(id), now).toCompletableFuture().join().get(id);
            assertEquals("PARTIAL", report.state()); assertEquals(1, report.confirmed()); assertEquals(first.value(), report.text());
            delivery.send(reference, actor, id, 0, 2, first, () -> { calls.incrementAndGet(); return CompletableFuture.completedStage(null); }).toCompletableFuture().join();
            assertEquals(2, calls.get());
            assertThrows(CompletionException.class, () -> delivery.send(reference, actor, id, 1, 2, new AgentReplyPlan.Text("没有确认的第二段"),
                    () -> { calls.incrementAndGet(); return CompletableFuture.completedStage(null); }).toCompletableFuture().join());
            assertEquals(2, calls.get());
            var layers = new EasyDataLayeredMemoryStore(db.data, db.store, db.mapper); var history = new EasyDataHistoryStore(db.data, db.mapper, layers);
            var basis = new AgentPersonalitySnapshot("人格", "teaneko", 1, "scope", 0, "基础人格", "", null);
            history.append(new HistoryContext(reference, basis), new HistoryMessage(id.toString(), actor, HistoryMessage.Role.ASSISTANT,
                    "候选信封包含尚未确认的正文", now, false, false), now.plus(Duration.ofDays(7))).toCompletableFuture().join();
            var refs = mock(IAgentMemoryReferences.class); when(refs.verify(reference)).thenReturn(CompletableFuture.completedStage(null));
            var source = new LayeredMemoryPromptSource(layers, refs, LayeredMemorySettings::defaults, ignored -> new LayeredMemoryPolicy(true, true, true),
                    db.mapper, Runnable::run, Clock.fixed(now, ZoneOffset.UTC), restarted);
            var request = new AgentRunRequest(UUID.randomUUID(), actor, AgentActivity.COMMAND, List.of(), null, reference);
            var text = source.resolve(new AgentDefinition("测试", new org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptionsSnapshot(null), Set.of(AgentActivity.COMMAND)),
                    request, null, control).toCompletableFuture().join().getFirst().text();
            assertTrue(text.contains("PARTIAL")); assertTrue(text.contains(first.value())); assertFalse(text.contains("候选信封"));
            db.store.advanceGeneration(MemoryNamespaceId.SHARED, 0, UUID.randomUUID()).toCompletableFuture().join();
            assertThrows(CompletionException.class, () -> restarted.read(reference, Set.of(id), now).toCompletableFuture().join());
            for (int i = 0; i < 5; i++) { restarted.maintain(now).toCompletableFuture().join(); }
        }
    }
}
