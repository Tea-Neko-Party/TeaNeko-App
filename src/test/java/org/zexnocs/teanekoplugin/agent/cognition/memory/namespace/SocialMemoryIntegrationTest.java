package org.zexnocs.teanekoplugin.agent.cognition.memory.namespace;

// 2026/11/07：认知业务契约归入插件，纯会话数据沿用运行底座。

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.Mockito;
import org.zexnocs.teanekoagent.component.AgentComponents;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptionsSnapshot;
import org.zexnocs.teanekoagent.llm.framework.tool.LLMToolCall;
import org.zexnocs.teanekoagent.llm.framework.tool.ToolInvocationContext;
import org.zexnocs.teanekoplugin.agent.cognition.memory.adapter.EasyDataLayeredMemoryStore;
import org.zexnocs.teanekoplugin.agent.cognition.memory.adapter.EasyDataMemoryLifecycle;
import org.zexnocs.teanekoplugin.agent.cognition.memory.history.HistoryContext;
import org.zexnocs.teanekoplugin.agent.cognition.memory.history.HistoryMessage;
import org.zexnocs.teanekoplugin.agent.cognition.memory.history.adapter.EasyDataHistoryStore;
import org.zexnocs.teanekoplugin.agent.cognition.memory.runtime.IAgentMemoryReferences;
import org.zexnocs.teanekoplugin.agent.cognition.memory.shared.ISharedConversationStore;
import org.zexnocs.teanekoplugin.agent.cognition.personality.AgentPersonalitySnapshot;
import org.zexnocs.teanekoplugin.agent.social.social.SocialAccess;
import org.zexnocs.teanekoplugin.agent.social.social.SocialState;
import org.zexnocs.teanekoplugin.agent.social.social.SocialStateService;
import org.zexnocs.teanekoplugin.agent.social.social.adapter.EasyDataSocialStateStore;
import org.zexnocs.teanekoagent.component.tool.TeaNekoAgentToolContext;
import org.zexnocs.teanekoagent.runtime.AgentActivity;
import org.zexnocs.teanekoagent.runtime.AgentActorContext;
import org.zexnocs.teanekoagent.runtime.AgentDefinition;
import org.zexnocs.teanekoagent.runtime.AgentRunRequest;
import org.zexnocs.teanekoagent.runtime.AgentScope;
import org.zexnocs.teanekoagent.runtime.AgentUserText;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.database.easydata.core.EasyDataLock;
import org.zexnocs.teanekocore.framework.composition.ComponentPatch;
import org.zexnocs.teanekocore.framework.composition.Entity;
import org.zexnocs.teanekocore.framework.composition.EntityId;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 用真实事务入口验证共享索引、关系并发、防重和空间换代，不访问实际数据库。
 * <br>2026/11/06：共享索引和原文同事务；关系行及事件账本不能因重复或清理而重新加分。
 * <br>2026/11/07：验证临时共享索引清理后的分区重建、新消息防重与退役隔离。
 *
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.6-l
 * @version 4.9.0-l
 */
@Timeout(20)
class SocialMemoryIntegrationTest {
    /**
     * 2026/11/07：原维护页遍历多个分区并补建临时指针；新消息不重复，过期和退役消息不复活。
     */
    @Test
    void rebuildsClearedSharedPointersAcrossBoundedMaintenancePages() {
        try (var db = database()) {
            var layers = new EasyDataLayeredMemoryStore(db.data, db.store, db.mapper);
            var history = new EasyDataHistoryStore(db.data, db.mapper, layers);
            var other = new MemoryNamespaceReference(new AgentScope("test", "test-group@2"), MemoryNamespaceId.SHARED, 0, 0);
            for (int i = 0; i < 20; i++) {
                append(history, i % 2 == 0 ? group : other, alice, "existing-" + i, "保留的原始消息" + i, now);
            }
            append(history, other, alice, "expired", "已经到期的消息", now.minus(Duration.ofDays(10)));
            db.clearTemporary();
            assertTrue(layers.related(direct, alice, now).toCompletableFuture().join().isEmpty());
            for (int i = 0; i < 12; i++) {
                layers.maintain(now).toCompletableFuture().join();
                if (i == 1) {
                    append(history, group, alice, "new-during-rebuild", "清理期间的新消息", now);
                }
            }
            var restored = layers.related(direct, alice, now).toCompletableFuture().join();
            assertEquals(21, restored.size());
            assertEquals(21, restored.stream().map(entry -> entry.message().message().messageId()).distinct().count());
            assertTrue(restored.stream().noneMatch(entry -> entry.message().message().messageId().equals("expired")));
            db.store.advanceGeneration(MemoryNamespaceId.SHARED, 0, UUID.randomUUID()).toCompletableFuture().join();
            db.clearTemporary();
            for (int i = 0; i < 12; i++) {
                layers.maintain(now).toCompletableFuture().join();
            }
            var current = new MemoryNamespaceReference(direct.scope(), direct.namespaceId(), 0, 1);
            assertTrue(layers.related(current, alice, now).toCompletableFuture().join().isEmpty());
        }
    }
    private final Instant now = Instant.parse("2026-11-06T00:00:00Z");
    private final UUID alice = UUID.randomUUID();
    private final MemoryNamespaceReference group = new MemoryNamespaceReference(new AgentScope("test", "test-group@1"), MemoryNamespaceId.SHARED, 0, 0);
    private final MemoryNamespaceReference direct = new MemoryNamespaceReference(new AgentScope("test", "private@" + alice), MemoryNamespaceId.SHARED, 0, 0);

    /** 其他用户、当前聊天、其他代次均不能混入共享查询；原文到期或换代使凭据失效。 */
    @Test void indexesOnlyTheRealParticipantAndOriginalGeneration() {
        try (var db = database()) {
            var layers = new EasyDataLayeredMemoryStore(db.data, db.store, db.mapper);
            var history = new EasyDataHistoryStore(db.data, db.mapper, layers);
            append(history, group, alice, "a", "在项目群讨论明天的部署", now);
            append(history, group, UUID.randomUUID(), "b", "其他人的内容", now);
            append(history, direct, alice, "c", "当前私聊", now);
            var entries = layers.related(direct, alice, now).toCompletableFuture().join();
            assertEquals(1, entries.size());
            assertEquals("在项目群讨论明天的部署", entries.getFirst().message().message().text());
            var proofs = entries.stream().map(ISharedConversationStore.Entry::proof).toList();
            assertTrue(layers.verifyShared(proofs, now).toCompletableFuture().join());
            assertFalse(layers.verifyShared(proofs, now.plus(Duration.ofDays(8))).toCompletableFuture().join());
            db.store.advanceGeneration(MemoryNamespaceId.SHARED, 0, UUID.randomUUID()).toCompletableFuture().join();
            assertFalse(layers.verifyShared(proofs, now).toCompletableFuture().join());
            assertThrows(CompletionException.class, () -> layers.related(direct, alice, now).toCompletableFuture().join());
        }
    }

    /** 原历史失败时索引也回滚；同来源重复投递只留下一个指针。 */
    @Test void rollsBackSharedIndexAndDoesNotDuplicateRedelivery() {
        try (var db = database()) {
            var layers = new EasyDataLayeredMemoryStore(db.data, db.store, db.mapper);
            var history = new EasyDataHistoryStore(db.data, db.mapper, layers);
            db.failingWritePrefix.set("expiry");
            assertThrows(CompletionException.class, () -> append(history, group, alice, "a", "同一消息", now));
            db.failingWritePrefix.set(null);
            assertTrue(layers.related(direct, alice, now).toCompletableFuture().join().isEmpty());
            append(history, group, alice, "a", "同一消息", now);
            append(history, group, alice, "a", "同一消息", now);
            assertEquals(1, layers.related(direct, alice, now).toCompletableFuture().join().size());
        }
    }

    /** 多个聊天并发修改同一个用户时不丢失更新，同事件不能重复加分；换代后独立初始化。 */
    @Test void serializesRelationshipUpdatesAndKeepsEventIdempotency() {
        try (var db = database()) {
            var store = new EasyDataSocialStateStore(db.data, db.store, db.mapper);
            var calls = new ArrayList<CompletableFuture<?>>();
            for (int i = 0; i < 12; i++) {
                String id = "message-" + i;
                calls.add(store.update(i % 2 == 0 ? group : direct, alice, 30, id, now, SocialMemoryIntegrationTest::increment).toCompletableFuture());
            }
            CompletableFuture.allOf(calls.toArray(CompletableFuture[]::new)).join();
            assertEquals(42, store.read(direct, alice, 99).toCompletableFuture().join().affinity());
            store.update(group, alice, 0, "message-0", now, SocialMemoryIntegrationTest::increment).toCompletableFuture().join();
            assertEquals(42, store.read(direct, alice, 0).toCompletableFuture().join().affinity());
            assertEquals(30, store.read(direct, UUID.randomUUID(), 30).toCompletableFuture().join().affinity());
            db.store.advanceGeneration(MemoryNamespaceId.SHARED, 0, UUID.randomUUID()).toCompletableFuture().join();
            assertThrows(CompletionException.class, () -> store.update(group, alice, 30, "late", now,
                    SocialMemoryIntegrationTest::increment).toCompletableFuture().join());
            var changed = new MemoryNamespaceReference(direct.scope(), direct.namespaceId(), 0, 1);
            assertEquals(30, store.read(changed, alice, 30).toCompletableFuture().join().affinity());
        }
    }

    /** 事务内状态已修改但事件写入失败时，两者一并回滚。 */
    @Test void rollsBackStateTogetherWithItsEvidenceReceipt() {
        try (var db = database()) {
            var store = new EasyDataSocialStateStore(db.data, db.store, db.mapper);
            db.failingWritePrefix.set("events:");
            assertThrows(CompletionException.class, () -> store.update(group, alice, 30, "e", now,
                    SocialMemoryIntegrationTest::increment).toCompletableFuture().join());
            db.failingWritePrefix.set(null);
            assertEquals(30, store.read(group, alice, 30).toCompletableFuture().join().affinity());
            assertEquals(31, store.update(group, alice, 30, "e", now, SocialMemoryIntegrationTest::increment).toCompletableFuture().join().affinity());
        }
    }

    /** 当前代次不会清理防重记录；空间换代后原维护页回收旧关系及来源记录。 */
    @Test void removesRetiredRelationshipsWithoutResettingCurrentOnes() {
        try (var db = database()) {
            var store = new EasyDataSocialStateStore(db.data, db.store, db.mapper);
            store.update(group, alice, 30, "message", now, SocialMemoryIntegrationTest::increment).toCompletableFuture().join();
            assertEquals(0, store.maintain(now).toCompletableFuture().join());
            db.store.advanceGeneration(MemoryNamespaceId.SHARED, 0, UUID.randomUUID()).toCompletableFuture().join();
            assertEquals(2, store.maintain(now).toCompletableFuture().join());
            var data = db.data.inNamespace("agent.social.v1");
            assertTrue(data.transaction("检查旧关系已回收", () -> data.get(EasyDataMemoryLifecycle.space(group))
                    .atomic().find(alice.toString(), EasyDataLock.READ) == null)
                    .toCompletableFuture().join());
        }
    }

    /** 2026/11/06：真实评估服务只接受本轮有效引用，重复消息和冷却内的新消息不能刷分。 */
    @Test void appraisesCurrentEvidenceOnceWithCooldown() {
        try (var db = database(); var control = ExecutionControl.create()) {
            var store = new EasyDataSocialStateStore(db.data, db.store, db.mapper);
            var access = Mockito.mock(SocialAccess.class);
            var refs = Mockito.mock(IAgentMemoryReferences.class, org.mockito.Mockito.CALLS_REAL_METHODS);
            Mockito.when(access.readable(group.scope())).thenReturn(true);
            Mockito.when(access.settings(group.scope(), "affinity")).thenReturn(Optional.of(db.mapper.createObjectNode()));
            Mockito.when(access.settings(group.scope(), "mood")).thenReturn(Optional.of(db.mapper.createObjectNode()));
            Mockito.when(refs.verify(group)).thenReturn(CompletableFuture.completedStage(null));
            var service = new SocialStateService(store, access, refs);
            var context = appraisalContext("感谢你陪我整理资料，今天轻松多了", control);
            service.observe(context.request()).toCompletableFuture().join();
            service.appraise(SocialStateService.Kind.AFFECTION,
                    "感谢你陪我整理资料", context).toCompletableFuture().join();
            var updated = store.read(group, alice, 30).toCompletableFuture().join();
            assertEquals(32, updated.affinity());
            assertTrue(updated.mood() > 0);
            service.appraise(SocialStateService.Kind.AFFECTION,
                    "感谢你陪我整理资料", context).toCompletableFuture().join();
            assertEquals(32, store.read(group, alice, 30).toCompletableFuture().join().affinity());
            var next = appraisalContext("非常喜欢和你聊项目，我以后还会来", control);
            service.appraise(SocialStateService.Kind.AFFECTION,
                    "非常喜欢和你聊项目", next).toCompletableFuture().join();
            assertEquals(32, store.read(group, alice, 30).toCompletableFuture().join().affinity());
            assertTrue(service.appraise(SocialStateService.Kind.AFFECTION,
                    "不是用户说的内容", next).toCompletableFuture().join().contains("没有本轮有效"));
        }
    }

    /**
     * @param text 本轮真实文本
     * @param control 本测试拥有的控制
     * @return 原身份绑定的工具上下文
     */
    private TeaNekoAgentToolContext appraisalContext(String text,
            ExecutionControl control) {
        var actor = new AgentActorContext(group.scope(), AgentActorContext.Origin.USER, Optional.of(alice), "user", UUID.randomUUID().toString(), "group");
        var request = new AgentRunRequest(UUID.randomUUID(), actor,
                AgentActivity.COMMAND, List.of(), null, group,
                new AgentUserText(actor.sourceId(), text), 64000);
        var entity = new Entity(EntityId.random());
        entity.commit(0, ComponentPatch.builder()
                .put(AgentComponents.ACTOR, actor).build());
        return new TeaNekoAgentToolContext(
                new AgentDefinition("test",
                        new LLMModelOptionsSnapshot(null),
                        Set.of(AgentActivity.COMMAND)), request, null, null,
                LLMToolCall.builder().id("call").name("social_appraise").arguments("{}").build(),
                new ToolInvocationContext(request.requestId(), entity.snapshot(), control));
    }

    /** @return 本测试拥有的数据库夹具，调用者负责关闭 */
    private NamespaceDatabaseFixture database() {
        var db = new NamespaceDatabaseFixture();
        try {
            db.store.create(new MemoryNamespace(MemoryNamespaceId.SHARED, 0, 0, now, null, null), 10).toCompletableFuture().join();
            return db;
        } catch (RuntimeException failure) {
            db.close();
            throw failure;
        }
    }
    /**
     * @param old 原状态
     * @return 单次真实状态变化
     */
    private static SocialState increment(SocialState old) {
        return new SocialState(old.affinity() + 1, old.mood(), old.moodAt(), old.appraisedAt(), old.evidenceHash(),
                old.lastUser(), old.sourceKey(), old.lastCare(), old.reminders(), old.pending(), old.nextCare(), old.revision() + 1);
    }
    /**
     * @param store 真实历史
     * @param ref 来源空间
     * @param user 真实主体
     * @param id 来源消息
     * @param text 原文
     * @param at 时间
     */
    private static void append(EasyDataHistoryStore store, MemoryNamespaceReference ref, UUID user, String id, String text, Instant at) {
        var actor = new AgentActorContext(ref.scope(), AgentActorContext.Origin.USER, Optional.of(user), "user", id, "target");
        store.append(new HistoryContext(ref, AgentPersonalitySnapshot.absent(ref.scope())),
                new HistoryMessage(id, actor, HistoryMessage.Role.USER, text, at, false, false), at.plus(Duration.ofDays(7)))
                .toCompletableFuture().join();
    }
}
