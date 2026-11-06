package org.zexnocs.teanekoagent.component.memory.namespace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.zexnocs.teanekoagent.component.care.CareContextSource;
import org.zexnocs.teanekoagent.component.contact.IPrivateContactTransport;
import org.zexnocs.teanekoagent.component.memory.adapter.EasyDataLayeredMemoryStore;
import org.zexnocs.teanekoagent.component.memory.history.HistoryContext;
import org.zexnocs.teanekoagent.component.memory.history.HistoryMessage;
import org.zexnocs.teanekoagent.component.memory.history.IAgentDeliveryStore;
import org.zexnocs.teanekoagent.component.memory.history.adapter.EasyDataHistoryStore;
import org.zexnocs.teanekoagent.component.memory.runtime.IAgentMemoryReferences;
import org.zexnocs.teanekoagent.component.personality.AgentPersonalitySnapshot;
import org.zexnocs.teanekoagent.component.proactive.adapter.ProactiveTargets;
import org.zexnocs.teanekoagent.component.social.SocialAccess;
import org.zexnocs.teanekoagent.component.social.adapter.EasyDataSocialStateStore;
import org.zexnocs.teanekoagent.runtime.AgentActorContext;
import org.zexnocs.teanekoagent.runtime.AgentScope;
import org.zexnocs.teanekoagent.runtime.monitor.AgentRuntimeMonitor.Delivery;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 验证真实关怀持久状态和消息读取，候选不绕过新来信与防重边界。
 * <br>2026/11/06：未调用模型与真实发送器；只把显式确认结果计入提醒次数。
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.6-l
 * @version 4.8.6-l
 */
@Timeout(20)
class CareIntegrationTest {
    /** 未决候选挡住另一个发生，确认后才计数并进入冷却。 */
    @Test void reservesOneCandidateAndCountsOnlyConfirmedDelivery() {
        try (var h = new Harness(); var control = ExecutionControl.create()) {
            var first = h.service.prepare(h.target, h.reference, UUID.randomUUID(), control).toCompletableFuture().join().orElseThrow();
            assertTrue(first.context().contains("当前是与该用户的私聊"));
            assertTrue(h.service.prepare(h.target, h.reference, UUID.randomUUID(), control).toCompletableFuture().join().isEmpty());
            first.verify(control).toCompletableFuture().join();
            first.finish(Delivery.SENT).toCompletableFuture().join();
            var state = h.states.read(h.reference, h.user, 80).toCompletableFuture().join();
            assertEquals(1, state.reminders());
            assertEquals("", state.pending());
            assertTrue(state.nextCare().isAfter(Instant.now()));
            first.finish(Delivery.SENT).toCompletableFuture().join();
            assertEquals(1, h.states.read(h.reference, h.user, 80).toCompletableFuture().join().reminders());
        }
    }
    /** 模型生成期间收到新来信，原候选必须失效，不能仍发送“怎么不理我”。 */
    @Test void cancelsOnNewMessageAndDoesNotCountAbstention() {
        try (var h = new Harness(); var control = ExecutionControl.create()) {
            var first = h.service.prepare(h.target, h.reference, UUID.randomUUID(), control).toCompletableFuture().join().orElseThrow();
            h.append("new-user", Instant.now());
            assertThrows(CompletionException.class, () -> first.verify(control).toCompletableFuture().join());
            first.finish(Delivery.FAILED).toCompletableFuture().join();
            assertEquals(0, h.states.read(h.reference, h.user, 80).toCompletableFuture().join().reminders());
            assertTrue(h.service.prepare(h.target, h.reference, UUID.randomUUID(), control).toCompletableFuture().join().isEmpty());
            assertEquals("", h.states.read(h.reference, h.user, 80).toCompletableFuture().join().pending());
        }
    }
    /** 弃言释放占用但保持尝试冷却，避免每次轮询都调用模型。 */
    @Test void coolsDownAfterAbstention() {
        try (var h = new Harness(); var control = ExecutionControl.create()) {
            var first = h.service.prepare(h.target, h.reference, UUID.randomUUID(), control).toCompletableFuture().join().orElseThrow();
            first.finish(Delivery.ABSTAINED).toCompletableFuture().join();
            var state = h.states.read(h.reference, h.user, 80).toCompletableFuture().join();
            assertEquals(0, state.reminders());
            assertEquals("", state.pending());
            assertTrue(h.service.prepare(h.target, h.reference, UUID.randomUUID(), control).toCompletableFuture().join().isEmpty());
        }
    }

    /**
     * 有真实来信和权威空间的关怀夹具。
     * @author zExNocs
     * @date 2026/11/06
     * @since 4.8.6-l
     * @version 4.8.6-l
     */
    private static final class Harness implements AutoCloseable {
        final NamespaceDatabaseFixture db = new NamespaceDatabaseFixture();
        final UUID user = UUID.randomUUID();
        final MemoryNamespaceReference reference = new MemoryNamespaceReference(new AgentScope("test", "private@" + user), MemoryNamespaceId.SHARED, 0, 0);
        final ProactiveTargets.Target target = new ProactiveTargets.Target(reference.scope(), "user", false, "bot");
        final EasyDataLayeredMemoryStore layers = new EasyDataLayeredMemoryStore(db.data, db.store, db.mapper);
        final EasyDataHistoryStore history = new EasyDataHistoryStore(db.data, db.mapper, layers);
        final EasyDataSocialStateStore states = new EasyDataSocialStateStore(db.data, db.store, db.mapper);
        final CareContextSource service;
        /** 建立四十八小时前的真实私聊消息与已启用组件。 */
        Harness() {
            try {
                db.store.create(new MemoryNamespace(MemoryNamespaceId.SHARED, 0, 0, Instant.now(), null, null), 10).toCompletableFuture().join();
                var access = mock(SocialAccess.class);
                when(access.readable(reference.scope())).thenReturn(true);
                when(access.settings(reference.scope(), "care")).thenReturn(Optional.of(db.mapper.createObjectNode().put("missingHours", 1)));
                when(access.settings(reference.scope(), "affinity")).thenReturn(Optional.of(db.mapper.createObjectNode().put("initial", 80)));
                var transport = mock(IPrivateContactTransport.class);
                when(transport.user(reference.scope(), "user")).thenReturn(Optional.of(user));
                var refs = mock(IAgentMemoryReferences.class);
                when(refs.verify(reference)).thenReturn(CompletableFuture.completedStage(null));
                var deliveries = mock(IAgentDeliveryStore.class);
                when(deliveries.read(eq(reference), anySet(), any())).thenReturn(CompletableFuture.completedStage(Map.of()));
                service = new CareContextSource(access, states, transport, refs, layers, deliveries);
                append("old-user", Instant.now().minus(Duration.ofDays(2)));
            } catch (RuntimeException failure) {
                db.close();
                throw failure;
            }
        }
        /**
         * @param id 原消息标识
         * @param at 实际时间
         */
        void append(String id, Instant at) {
            var actor = new AgentActorContext(reference.scope(), AgentActorContext.Origin.USER, Optional.of(user), "user", id, "user");
            history.append(new HistoryContext(reference, AgentPersonalitySnapshot.absent(reference.scope())),
                    new HistoryMessage(id, actor, HistoryMessage.Role.USER, "下次再来聊", at, false, false), Instant.now().plus(Duration.ofDays(7)))
                    .toCompletableFuture().join();
        }
        /** 关闭自己创建的数据库执行器。 */
        @Override public void close() { db.close(); }
    }
}
