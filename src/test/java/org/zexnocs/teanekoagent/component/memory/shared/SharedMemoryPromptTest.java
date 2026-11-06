package org.zexnocs.teanekoagent.component.memory.shared;

import org.junit.jupiter.api.Test;
import org.zexnocs.teanekoagent.component.memory.history.HistoryMessage;
import org.zexnocs.teanekoagent.component.memory.history.IAgentDeliveryStore;
import org.zexnocs.teanekoagent.component.memory.layered.MemoryLayers;
import org.zexnocs.teanekoagent.component.memory.namespace.MemoryNamespaceBindings;
import org.zexnocs.teanekoagent.component.memory.namespace.MemoryNamespaceId;
import org.zexnocs.teanekoagent.component.memory.namespace.MemoryNamespaceReference;
import org.zexnocs.teanekoagent.component.memory.runtime.IAgentMemoryReferences;
import org.zexnocs.teanekoagent.component.social.SocialAccess;
import org.zexnocs.teanekoagent.runtime.AgentActivity;
import org.zexnocs.teanekoagent.runtime.AgentActorContext;
import org.zexnocs.teanekoagent.runtime.AgentRunRequest;
import org.zexnocs.teanekoagent.runtime.AgentScope;
import org.zexnocs.teanekoagent.runtime.prompt.AgentPromptInput;
import org.zexnocs.teanekoapp.client.api.ITeaNekoClient;
import org.zexnocs.teanekoapp.teauser.ChatIdentityService;
import org.zexnocs.teanekoapp.utils.TeaNekoScopeService;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.framework.pair.IndependentPair;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 共享对话来源、私聊隔离、真实发送投影及动态撤权的离线回归。
 * <br>2026/11/06：助手原协议信封不得进入其他聊天的回忆资料。
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.6-l
 * @version 4.8.6-l
 */
class SharedMemoryPromptTest {
    /** 私聊只取得参与过的群聊，且助手只投影有回执的正文；绑定变更后禁止重放。 */
    @Test void checksSourcesAndProjectsOnlyAcknowledgedAssistantText() {
        var store = mock(ISharedConversationStore.class);
        var refs = mock(IAgentMemoryReferences.class);
        var bindings = mock(MemoryNamespaceBindings.class);
        var deliveries = mock(IAgentDeliveryStore.class);
        var access = mock(SocialAccess.class);
        var names = mock(ChatIdentityService.class);
        var scopes = mock(TeaNekoScopeService.class);
        var mapper = new ObjectMapper();
        var user = UUID.randomUUID();
        var direct = new AgentScope("test", "private@" + user);
        var group = new AgentScope("test", "test-group@1");
        var current = new MemoryNamespaceReference(direct, MemoryNamespaceId.SHARED, 0, 0);
        var source = new MemoryNamespaceReference(group, MemoryNamespaceId.SHARED, 0, 0);
        var replyId = UUID.randomUUID();
        var original = new HistoryMessage(replyId.toString(), new AgentActorContext(group, AgentActorContext.Origin.USER,
                Optional.of(user), "user", "group-message", "group"), HistoryMessage.Role.ASSISTANT,
                "内部协议内容不能泄漏", Instant.now(), false, false);
        var entry = new ISharedConversationStore.Entry(new ISharedConversationStore.Proof(source, 1, "fingerprint"),
                new MemoryLayers.Message(1, original, "personality", Instant.now().plusSeconds(3600)));
        when(store.related(eq(current), eq(user), any())).thenReturn(CompletableFuture.completedStage(List.of(entry)));
        when(store.verifyShared(anyList(), any())).thenReturn(CompletableFuture.completedStage(true));
        when(refs.verify(current)).thenReturn(CompletableFuture.completedStage(null));
        when(bindings.readReference(group)).thenReturn(CompletableFuture.completedStage(Optional.of(source)));
        when(access.settings(direct, "shared-memory")).thenReturn(Optional.of(mapper.createObjectNode()));
        when(access.readable(any())).thenReturn(true);
        when(scopes.getPrivateScopeId(user)).thenReturn(direct.scopeId());
        var client = mock(ITeaNekoClient.class);
        when(client.getClientId()).thenReturn("test");
        when(scopes.fromGroupScopeId(group.scopeId())).thenReturn(IndependentPair.of(client, "1"));
        when(names.name("test", group.scopeId())).thenReturn("项目群");
        when(deliveries.read(eq(source), anySet(), any())).thenReturn(CompletableFuture.completedStage(
                Map.of(replyId, new IAgentDeliveryStore.View("ACKNOWLEDGED", 1, 1, "明天一起确认部署"))));
        var beans = new org.springframework.beans.factory.support.StaticListableBeanFactory(Map.of("sharedStore", store));
        var service = new SharedMemoryPromptSource(beans.getBeanProvider(ISharedConversationStore.class), refs, bindings,
                deliveries, access, names, scopes, mapper);
        var actor = new AgentActorContext(direct, AgentActorContext.Origin.USER, Optional.of(user), "user", "new", "user");
        var request = new AgentRunRequest(UUID.randomUUID(), actor, AgentActivity.COMMAND, List.of(), null, current);
        try (var control = ExecutionControl.create()) {
            var fragments = service.resolve(null, request, null, control).toCompletableFuture().join();
            assertEquals(1, fragments.size());
            assertTrue(fragments.getFirst().text().contains("明天一起确认部署"));
            assertTrue(fragments.getFirst().text().contains("项目群"));
            assertTrue(fragments.getFirst().text().contains("私聊"));
            assertFalse(fragments.getFirst().text().contains("内部协议"));
            var input = new AgentPromptInput(List.of(org.zexnocs.teanekoagent.component.llm.framework.message.LLMUserMessage.builder()
                    .contents(new org.zexnocs.teanekoagent.component.llm.framework.message.content.LLMContentListBuilder().addText("你好").build()).build()), fragments, 0);
            service.verify(request, input, control).toCompletableFuture().join();
            when(bindings.readReference(group)).thenReturn(CompletableFuture.completedStage(Optional.empty()));
            assertThrows(CompletionException.class, () -> service.verify(request, input, control).toCompletableFuture().join());
            assertTrue(service.resolve(null, request, null, control).toCompletableFuture().join().isEmpty());
            // 2026/11/06：群聊不能因为相同参与者存在，就读取该用户的私聊原文。
            var other = new AgentScope("test", "test-group@2");
            var otherReference = new MemoryNamespaceReference(other, MemoryNamespaceId.SHARED, 0, 0);
            var privateEntry = new ISharedConversationStore.Entry(new ISharedConversationStore.Proof(current, 1, "private"), entry.message());
            when(store.related(eq(otherReference), eq(user), any())).thenReturn(CompletableFuture.completedStage(List.of(privateEntry)));
            when(refs.verify(otherReference)).thenReturn(CompletableFuture.completedStage(null));
            when(access.settings(other, "shared-memory")).thenReturn(Optional.of(mapper.createObjectNode()));
            var groupActor = new AgentActorContext(other, AgentActorContext.Origin.USER, Optional.of(user), "user", "another", "group");
            var groupRequest = new AgentRunRequest(UUID.randomUUID(), groupActor, AgentActivity.COMMAND, List.of(), null, otherReference);
            assertTrue(service.resolve(null, groupRequest, null, control).toCompletableFuture().join().isEmpty());
            verify(bindings, never()).readReference(direct);
        }
    }
}
