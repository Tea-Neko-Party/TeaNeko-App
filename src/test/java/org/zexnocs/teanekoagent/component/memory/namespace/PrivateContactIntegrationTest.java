package org.zexnocs.teanekoagent.component.memory.namespace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.zexnocs.teanekoagent.component.AgentComponents;
import org.zexnocs.teanekoagent.component.contact.IPrivateContactTransport;
import org.zexnocs.teanekoagent.component.contact.PrivateContactService;
import org.zexnocs.teanekoagent.component.llm.framework.model.LLMModelOptionsSnapshot;
import org.zexnocs.teanekoagent.component.llm.framework.tool.LLMToolCall;
import org.zexnocs.teanekoagent.component.llm.framework.tool.ToolInvocationContext;
import org.zexnocs.teanekoagent.component.memory.adapter.EasyDataDeliveryStore;
import org.zexnocs.teanekoagent.component.memory.history.AgentDeliveryService;
import org.zexnocs.teanekoagent.component.memory.history.HistoryMessage;
import org.zexnocs.teanekoagent.component.memory.history.IConversationHistory;
import org.zexnocs.teanekoagent.component.memory.runtime.IAgentMemoryReferences;
import org.zexnocs.teanekoagent.component.personality.AgentPersonalitySnapshot;
import org.zexnocs.teanekoagent.component.personality.IAgentPersonalitySource;
import org.zexnocs.teanekoagent.component.social.SocialAccess;
import org.zexnocs.teanekoagent.component.tool.TeaNekoAgentToolContext;
import org.zexnocs.teanekoagent.runtime.*;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.framework.composition.ComponentPatch;
import org.zexnocs.teanekocore.framework.composition.Entity;
import org.zexnocs.teanekocore.framework.composition.EntityId;
import org.zexnocs.teanekocore.logger.ILogger;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 验证受控私聊的真实账本防重、目标配置与空间撤权，不使用外部客户端。
 * <br>2026/11/06：调用失败或回执不明不能变成第二次发送。
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.6-l
 * @version 4.8.6-l
 */
@Timeout(20)
class PrivateContactIntegrationTest {
    /** 同一请求重放只确认原发送，更改正文不能用相同请求再发一条。 */
    @Test void sendsOnlyOnceAndRecordsTheRealTarget() {
        try (var h = new Harness(); var control = ExecutionControl.create()) {
            var context = h.context("请私聊我说一下部署方案", control);
            assertTrue(h.service.send("我们私下继续", "请私聊我", context).toCompletableFuture().join().contains("平台回执"));
            assertTrue(h.service.send("我们私下继续", "请私聊我", context).toCompletableFuture().join().contains("平台回执"));
            verify(h.transport, times(1)).send(eq(h.target), eq("我们私下继续"), any());
            assertThrows(CompletionException.class, () -> h.service.send("再发一条", "请私聊我", context).toCompletableFuture().join());
            verify(h.transport, times(1)).send(any(), anyString(), any());
            var captured = org.mockito.ArgumentCaptor.forClass(HistoryMessage.class);
            verify(h.history, atLeastOnce()).append(any(), captured.capture(), any());
            assertEquals(h.direct.scope(), captured.getValue().source().scope());
            assertEquals(Optional.of(h.user), captured.getValue().source().userId());
        }
    }
    /** 没有当前邀请、目标停用、空间不同及在发送前撤权均不触发平台动作。 */
    @Test void rejectsMissingInvitationAndChangedTargetBeforeSending() {
        try (var h = new Harness(); var control = ExecutionControl.create()) {
            assertTrue(h.service.send("文字", "请私聊我", h.context("你好", control)).toCompletableFuture().join().contains("明确邀请"));
            assertTrue(h.service.send("文字", "私聊", h.context("不要私聊我", control)).toCompletableFuture().join().contains("明确邀请"));
            when(h.access.readable(h.direct.scope())).thenReturn(false);
            var context = h.context("请私聊我", control);
            assertTrue(h.service.send("文字", "请私聊我", context).toCompletableFuture().join().contains("未启用"));
            when(h.access.readable(h.direct.scope())).thenReturn(true);
            when(h.bindings.readReference(h.direct.scope())).thenReturn(CompletableFuture.completedStage(Optional.of(
                    new MemoryNamespaceReference(h.direct.scope(), new MemoryNamespaceId("different"), 0, 0))));
            assertTrue(h.service.send("文字", "请私聊我", context).toCompletableFuture().join().contains("同一记忆空间"));
            when(h.bindings.readReference(h.direct.scope())).thenReturn(CompletableFuture.completedStage(Optional.of(h.direct)));
            when(h.references.verify(h.direct)).thenReturn(CompletableFuture.failedStage(new IllegalStateException("绑定已经切换")));
            assertThrows(CompletionException.class, () -> h.service.send("文字", "请私聊我", context).toCompletableFuture().join());
            verify(h.transport, never()).send(any(), anyString(), any());
        }
    }
    /** 平台已经受理但观察失败时，持久未知回执阻止重试。 */
    @Test void doesNotRetryAnUncertainPlatformSend() {
        try (var h = new Harness(); var control = ExecutionControl.create()) {
            doReturn(CompletableFuture.failedStage(new IllegalStateException("回执丢失"))).when(h.transport).send(any(), anyString(), any());
            var context = h.context("请私聊我", control);
            assertThrows(CompletionException.class, () -> h.service.send("文字", "请私聊我", context).toCompletableFuture().join());
            assertThrows(CompletionException.class, () -> h.service.send("文字", "请私聊我", context).toCompletableFuture().join());
            verify(h.transport, times(1)).send(any(), anyString(), any());
            verify(h.history, never()).append(any(), any(), any());
        }
    }

    /**
     * 拥有数据库夹具，其他端口不创建实际网络资源。
     * @author zExNocs
     * @date 2026/11/06
     * @since 4.8.6-l
     * @version 4.8.6-l
     */
    private static final class Harness implements AutoCloseable {
        final NamespaceDatabaseFixture db = new NamespaceDatabaseFixture();
        final UUID user = UUID.randomUUID();
        final MemoryNamespaceReference group = new MemoryNamespaceReference(new AgentScope("test", "test-group@1"), MemoryNamespaceId.SHARED, 0, 0);
        final MemoryNamespaceReference direct = new MemoryNamespaceReference(new AgentScope("test", "private@" + user), MemoryNamespaceId.SHARED, 0, 0);
        final IPrivateContactTransport.Target target = new IPrivateContactTransport.Target(direct.scope(), user, "user", "bot");
        final IPrivateContactTransport transport = mock(IPrivateContactTransport.class);
        final MemoryNamespaceBindings bindings = mock(MemoryNamespaceBindings.class);
        final IAgentMemoryReferences references = mock(IAgentMemoryReferences.class);
        final SocialAccess access = mock(SocialAccess.class);
        final IConversationHistory history = mock(IConversationHistory.class);
        final PrivateContactService service;
        /** 初始化权威空间与真实发送账本。 */
        Harness() {
            try {
                db.store.create(new MemoryNamespace(MemoryNamespaceId.SHARED, 0, 0, Instant.now(), null, null), 10).toCompletableFuture().join();
                when(transport.resolve(group.scope(), user)).thenReturn(Optional.of(target));
                when(transport.send(any(), anyString(), any())).thenAnswer(call -> {
                    call.getArgument(2, Runnable.class).run();
                    return CompletableFuture.completedStage(null);
                });
                when(bindings.readReference(direct.scope())).thenReturn(CompletableFuture.completedStage(Optional.of(direct)));
                when(references.verify(any())).thenReturn(CompletableFuture.completedStage(null));
                when(access.settings(group.scope(), "private-contact")).thenReturn(Optional.of(db.mapper.createObjectNode()));
                when(access.readable(any())).thenReturn(true);
                var personalities = mock(IAgentPersonalitySource.class);
                when(personalities.resolve(eq(direct.scope()), any())).thenReturn(CompletableFuture.completedStage(AgentPersonalitySnapshot.absent(direct.scope())));
                when(personalities.verify(any(), any(), any())).thenReturn(CompletableFuture.completedStage(null));
                when(history.append(any(), any(), any())).thenReturn(CompletableFuture.completedStage(true));
                var delivery = new AgentDeliveryService(new EasyDataDeliveryStore(db.data, db.store, db.mapper), Runnable::run,
                        Clock.systemUTC(), () -> Duration.ofDays(7));
                service = new PrivateContactService(transport, bindings, references, access, delivery, history, personalities, mock(ILogger.class));
            } catch (RuntimeException failure) {
                db.close();
                throw failure;
            }
        }
        /**
         * @param text 本轮原文
         * @param control 借用控制
         * @return 与真实 Actor 绑定的工具上下文
         */
        TeaNekoAgentToolContext context(String text, ExecutionControl control) {
            var actor = new AgentActorContext(group.scope(), AgentActorContext.Origin.USER, Optional.of(user), "user", UUID.randomUUID().toString(), "group");
            var request = new AgentRunRequest(UUID.randomUUID(), actor, AgentActivity.COMMAND, List.of(), null, group,
                    new AgentUserText(actor.sourceId(), text), 64000);
            var entity = new Entity(EntityId.random());
            entity.commit(0, ComponentPatch.builder().put(AgentComponents.ACTOR, actor).build());
            return new TeaNekoAgentToolContext(new AgentDefinition("test", new LLMModelOptionsSnapshot(null), Set.of(AgentActivity.COMMAND)),
                    request, null, null, LLMToolCall.builder().id("real-call").name("private_message").arguments("{}").build(),
                    new ToolInvocationContext(request.requestId(), entity.snapshot(), control));
        }
        /** 关闭夹具拥有的执行器。 */
        @Override public void close() { db.close(); }
    }
}
