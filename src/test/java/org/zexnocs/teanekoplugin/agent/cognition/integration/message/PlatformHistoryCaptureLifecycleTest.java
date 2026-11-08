package org.zexnocs.teanekoplugin.agent.cognition.integration.message;

import org.zexnocs.teanekoplugin.contract.agent.AgentComponentFixtures;

// 2026/11/07：隔离目录复用生产插件注解，不创建业务入口实例。
import org.zexnocs.teanekocore.plugin.PluginDeclarationScanner;

// 2026/11/07：认知业务契约归入插件，纯会话数据沿用运行底座。

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

import org.junit.jupiter.api.Test;
import org.zexnocs.teanekoagent.llm.framework.message.LLMUserMessage;
import org.zexnocs.teanekoagent.llm.framework.message.content.LLMContentListBuilder;
import org.zexnocs.teanekoplugin.agent.cognition.memory.history.IConversationHistory;
import org.zexnocs.teanekoplugin.agent.cognition.memory.namespace.MemoryNamespaceId;
import org.zexnocs.teanekoplugin.agent.cognition.memory.namespace.MemoryNamespaceReference;
import org.zexnocs.teanekoplugin.agent.cognition.memory.runtime.IAgentMemoryReferences;
import org.zexnocs.teanekoplugin.agent.cognition.personality.IAgentPersonalitySource;
import org.zexnocs.teanekoagent.runtime.operation.IAgentOperationStore;
import org.zexnocs.teanekoagent.integration.config.AgentScopeConfigData;
import org.zexnocs.teanekoagent.integration.config.AgentScopeConfigManager;
import org.zexnocs.teanekoagent.integration.message.NativeMessageInput;
import org.zexnocs.teanekoagent.runtime.AgentScope;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataService;
import org.zexnocs.teanekocore.plugin.PluginDescriptor;
import org.zexnocs.teanekocore.plugin.PluginRegistry;
import org.zexnocs.teanekoplugin.agent.cognition.CognitionPlugin;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 原生采集停用后拒绝旧异步结果，且不阻断未启用记忆的纯模型入站。
 * <br>2026/11/07：覆盖插件启用代次、真实排空与重新启用之间的隔离。
 *
 * <br>2026/11/08：夹具通过真实插件组件契约准备参数并验证改造后的端口。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
class PlatformHistoryCaptureLifecycleTest {
    /**
     * 空间解析未完成时停用，迟到结果不会触发人格、去重或写入。
     * @throws Exception 当有限等待失败时
     */
    @Test
    void disablingDuringReferenceResolutionDrainsWithoutWriting() throws Exception {
        var configs = mock(IConfigDataService.class);
        var manager = new AgentScopeConfigManager();
        var references = mock(IAgentMemoryReferences.class, org.mockito.Mockito.CALLS_REAL_METHODS);
        var personalities = mock(IAgentPersonalitySource.class, org.mockito.Mockito.CALLS_REAL_METHODS);
        var history = mock(IConversationHistory.class);
        var attempts = mock(IAgentOperationStore.class);
        var plugins = new PluginRegistry(List.of(
                () -> new PluginDescriptor("teaneko.agent", "1", 1, Set.of()), PluginDeclarationScanner.declaration(CognitionPlugin.class)::descriptor));
        var source = mock(ITeaNekoMessageData.class, RETURNS_DEEP_STUBS);
        when(source.getScopeId()).thenReturn("group-1");
        when(source.getClient().getClientId()).thenReturn("test");
        when(source.getUserData().getUuid()).thenReturn(UUID.randomUUID());
        when(source.getUserData().getUserIdInPlatform()).thenReturn("user-1");
        when(source.getMessageId()).thenReturn("message-1");
        var config = new AgentScopeConfigData("{\"version\":1,\"components\":{\"llm\":{},\"memory\":{}}}");
        config.setEnabled(true);
        AgentComponentFixtures.set(config, "memory", "historyCaptureEnabled", true);
        when(configs.getConfigData(manager, AgentScopeConfigData.class, "group-1"))
                .thenReturn(Optional.of(config));
        var scope = new AgentScope("test", "group-1");
        var physical = new CompletableFuture<MemoryNamespaceReference>();
        when(references.resolve(scope)).thenReturn(physical);
        var input = new NativeMessageInput("测试原文", LLMUserMessage.builder()
                .contents(new LLMContentListBuilder().addText("测试原文").build()).build(), Instant.now(), "digest");
        try (var scheduler = Executors.newSingleThreadScheduledExecutor()) {
            var capture = new PlatformHistoryCapture(AgentComponentFixtures.access(configs, manager), references, personalities, history, attempts, scheduler);
            capture.installPlugins(plugins);
            try {
                capture.start().toCompletableFuture().get(5, TimeUnit.SECONDS);
                var pending = capture.capture(source, input).toCompletableFuture();
                plugins.setEnabled("agent.cognition", false);
                var stopped = capture.stop().toCompletableFuture();
                assertFalse(stopped.isDone());
                var bypass = capture.capture(source, input).toCompletableFuture().get(5, TimeUnit.SECONDS);
                assertTrue(bypass.accepted());
                assertNull(bypass.context());
                physical.complete(new MemoryNamespaceReference(scope, MemoryNamespaceId.SHARED, 0, 0));
                assertThrows(ExecutionException.class, () -> pending.get(5, TimeUnit.SECONDS));
                stopped.get(5, TimeUnit.SECONDS);
                verifyNoInteractions(personalities, history, attempts);
            } finally {
                physical.complete(new MemoryNamespaceReference(scope, MemoryNamespaceId.SHARED, 0, 0));
                capture.stop().toCompletableFuture().get(5, TimeUnit.SECONDS);
            }
        }
    }
}
