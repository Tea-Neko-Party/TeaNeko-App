package org.zexnocs.teanekoagent.component;

// 2026/11/07：隔离目录复用生产插件注解，不创建业务入口实例。
import org.zexnocs.teanekocore.plugin.PluginDeclarationScanner;

// 2026/11/07：认知业务契约归入插件，纯会话数据沿用运行底座。

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

import org.zexnocs.teanekoplugin.agent.cognition.memory.namespace.IMemoryNamespaceBindingStore;
import org.zexnocs.teanekoplugin.agent.cognition.memory.namespace.IMemoryNamespaceDefaults;
import org.zexnocs.teanekoplugin.agent.cognition.memory.namespace.IMemoryNamespaceStore;
import org.zexnocs.teanekoplugin.agent.cognition.memory.namespace.MemoryNamespaceId;
import org.zexnocs.teanekoplugin.agent.cognition.memory.namespace.MemoryNamespaceReference;
import org.zexnocs.teanekoplugin.agent.social.social.SocialAccess;
import org.zexnocs.teanekoagent.runtime.AgentActivity;
import org.zexnocs.teanekoagent.runtime.AgentActorContext;
import org.zexnocs.teanekoagent.runtime.AgentDefinition;
import org.zexnocs.teanekoagent.runtime.AgentRunRequest;
import org.zexnocs.teanekoagent.runtime.AgentScope;
import org.zexnocs.teanekoagent.runtime.AgentService;
import org.zexnocs.teanekoplugin.agent.social.social.ISocialStateStore;
import org.zexnocs.teanekoplugin.agent.social.social.SocialComponent;
import org.zexnocs.teanekoplugin.agent.social.social.SocialState;
import org.zexnocs.teanekoplugin.agent.social.social.SocialStateService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.zexnocs.teanekoplugin.agent.social.affinity.AffinityComponent;
import org.zexnocs.teanekoagent.component.llm.LLMComponent;
import org.zexnocs.teanekoagent.llm.framework.interaction.ILLMInteractionService;
import org.zexnocs.teanekoagent.llm.framework.message.LLMUserMessage;
import org.zexnocs.teanekoagent.llm.framework.message.content.LLMContentListBuilder;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptionsSnapshot;
import org.zexnocs.teanekoplugin.agent.cognition.memory.MemoryComponent;
import org.zexnocs.teanekoplugin.agent.cognition.memory.MemoryInputSystem;
import org.zexnocs.teanekoplugin.agent.cognition.memory.MemoryOutputSystem;
import org.zexnocs.teanekoplugin.agent.cognition.memory.adapter.StoreAgentMemoryReferences;
import org.zexnocs.teanekoplugin.agent.cognition.memory.configuration.MemoryNamespaceConfig;
import org.zexnocs.teanekoplugin.agent.cognition.memory.configuration.MemoryReadAccess;
import org.zexnocs.teanekoagent.integration.config.AgentComponentAccess;
import org.zexnocs.teanekoplugin.agent.cognition.memory.history.IAgentDeliveryStore;
import org.zexnocs.teanekoplugin.agent.cognition.memory.namespace.*;
import org.zexnocs.teanekoplugin.agent.cognition.memory.runtime.AgentMemoryReferenceException;
import org.zexnocs.teanekoplugin.agent.cognition.memory.shared.ISharedConversationStore;
import org.zexnocs.teanekoplugin.agent.cognition.memory.shared.SharedMemoryPromptSource;
import org.zexnocs.teanekoplugin.agent.social.mood.MoodComponent;
import org.zexnocs.teanekoplugin.agent.social.social.*;
import org.zexnocs.teanekoagent.component.tool.ToolComponent;
import org.zexnocs.teanekoagent.configuration.AgentExecutionConfig;
import org.zexnocs.teanekoagent.integration.config.AgentScopeConfigData;
import org.zexnocs.teanekoagent.integration.config.AgentScopeConfigManager;
import org.zexnocs.teanekoagent.integration.config.ConfigDataAgentScopePolicy;
import org.zexnocs.teanekoagent.plugin.AgentPluginRuntime;
import org.zexnocs.teanekoagent.plugin.SourceAgentFeature;
import org.zexnocs.teanekoagent.runtime.*;
import org.zexnocs.teanekoagent.runtime.access.AgentAccessGate;
import org.zexnocs.teanekoagent.runtime.access.AgentRuntimeSnapshot;
import org.zexnocs.teanekoagent.runtime.prompt.AgentPromptException;
import org.zexnocs.teanekoagent.runtime.prompt.AgentPromptInput;
import org.zexnocs.teanekoagent.runtime.prompt.IAgentPromptSource;
import org.zexnocs.teanekoapp.teauser.ChatIdentityService;
import org.zexnocs.teanekoapp.utils.TeaNekoScopeService;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataService;
import org.zexnocs.teanekocore.file_config.parser.YAMLFileTypeParser;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekocore.plugin.PluginAccess;
import org.zexnocs.teanekocore.plugin.PluginDescriptor;
import org.zexnocs.teanekocore.plugin.PluginRegistry;
import org.zexnocs.teanekocore.utils.scanner.BeanScanner;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 使用真实组件扫描、作用域策略和空间引用链验证 Spring 初始化及运行阶段复核。
 * <br>2026/11/06：覆盖不同 Bean 创建顺序，禁止以循环引用开关或模拟目录掩盖启动环。
 * <br>2026/11/06：加入实际轮次服务工厂，捕捉组件资料误入宿主全局来源的装配冲突。
 * <br>2026/11/07：装配通用组件资格与认知读取服务，验证社交组合不反向依赖。
 *
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.6-l
 * @version 4.9.0-l
 */
class SocialComponentWiringTest {
    private static final String ASSEMBLY = """
            {"version":1,"components":{"llm":{},"tool":{},"memory":{"crossChatRecallEnabled":true},
            "affinity":{},"mood":{}}}
            """;

    /** 2026/11/06：排除组件来源后，多个真正的宿主来源仍须明确组合，诊断列出冲突类型。 */
    @Test
    void rejectsAmbiguousHostSourcesWithTypes() {
        try (var context = context(AgentExecutionConfig.class)) {
            context.registerBean("firstHostSource", IAgentPromptSource.class, IAgentPromptSource::empty);
            context.registerBean("secondHostSource", IAgentPromptSource.class, IAgentPromptSource::empty);
            var failure = assertThrows(BeanCreationException.class, context::refresh);
            var cause = failure.getMostSpecificCause();
            assertInstanceOf(IllegalStateException.class, cause);
            assertTrue(cause.getMessage().contains("宿主附加资料来源必须明确组合为一个端口"));
            assertTrue(cause.getMessage().contains("冲突类型："));
            assertTrue(cause.getMessage().contains(IAgentPromptSource.class.getName()));
        }
    }

    /**
     * 真实目录在组件、目录或插件运行器先创建时均可完成初始化；查询只读取已发布缓存。
     * @param first 首先登记的生产 Bean 类型
     */
    @ParameterizedTest
    @ValueSource(classes = {AffinityComponent.class, MoodComponent.class, MemoryComponent.class,
            AgentComponentRegistry.class, AgentPluginRuntime.class, AgentExecutionConfig.class})
    void initializesWithoutCircularReferences(Class<?> first) {
        try (var context = context(first)) {
            context.refresh();
            var registry = context.getBean(AgentComponentRegistry.class);
            assertEquals(Set.of("llm", "tool", "memory", "affinity", "mood"),
                    registry.entries().keySet());
            assertNotNull(context.getBean(AgentPluginRuntime.class));
            assertNotNull(context.getBean(AgentService.class));
            assertInstanceOf(org.zexnocs.teanekoagent.runtime.prompt.DeferredAgentPromptSource.class,
                    context.getBean(MemoryComponent.class).prompts());
            verifyNoInteractions(context.getBean(IConfigDataService.class), context.getBean(ISocialStateStore.class),
                    context.getBean(IMemoryNamespaceStore.class), context.getBean(IMemoryNamespaceBindingStore.class),
                    context.getBean(ISharedConversationStore.class));

            var scanner = context.getBean(BeanScanner.class);
            clearInvocations(scanner);
            for (int i = 0; i < 2; i++) {
                assertEquals(5, registry.describe().size());
                assertEquals(5, registry.validate(ASSEMBLY).parameters().size());
            }
            verifyNoInteractions(scanner);
        }
    }

    /**
     * 延迟取得服务后仍执行真实状态读取、空间代次复核和动态停用检查。
     * @param type 当前独立情感组件
     * @throws Exception 有限等待失败时
     */
    @ParameterizedTest
    @ValueSource(classes = {AffinityComponent.class, MoodComponent.class})
    void resolvesStateAndRejectsRevokedAccess(Class<? extends SocialComponent> type) throws Exception {
        try (var context = context(type); var control = ExecutionControl.create()) {
            context.refresh();
            var component = context.getBean(type);
            var scope = new AgentScope("test", "group-1");
            var user = UUID.randomUUID();
            var reference = new MemoryNamespaceReference(scope, MemoryNamespaceId.SHARED, 0, 0);
            var config = new AgentScopeConfigData(ASSEMBLY.replace("\"crossChatRecallEnabled\":true",
                    "\"crossChatRecallEnabled\":false"));
            var configs = context.getBean(IConfigDataService.class);
            when(configs.getConfigData(context.getBean(AgentScopeConfigManager.class),
                    AgentScopeConfigData.class, scope.scopeId())).thenReturn(Optional.of(config));
            var bindings = context.getBean(IMemoryNamespaceBindingStore.class);
            when(bindings.readReference(scope)).thenReturn(CompletableFuture.completedStage(Optional.of(reference)));
            var states = context.getBean(ISocialStateStore.class);
            when(states.update(eq(reference), eq(user), eq(30), anyString(), any(Instant.class), any()))
                    .thenReturn(CompletableFuture.completedStage(SocialState.initial(30)));
            var actor = new AgentActorContext(scope, AgentActorContext.Origin.USER, Optional.of(user),
                    "用户", "message-1", "group-1");
            var message = LLMUserMessage.builder()
                    .contents(new LLMContentListBuilder().addText("今天一起完成了工作").build()).build();
            var request = new AgentRunRequest(UUID.randomUUID(), actor, AgentActivity.COMMAND,
                    List.of(message), null, reference);
            // 2026/11/06：走实际插件资料组合，未装配时不读取状态，装配后只贡献一次。
            String componentId = type == AffinityComponent.class ? "affinity" : "mood";
            var assembly = AgentAssembly.parse("{\"version\":1,\"components\":{\"llm\":{},\"tool\":{},"
                    + "\"memory\":{},\"" + componentId + "\":{}}}");
            var definition = new AgentDefinition("装配回归", new LLMModelOptionsSnapshot(null),
                    Set.of(AgentActivity.COMMAND), Set.of(), assembly);
            var pluginPrompts = context.getBean(AgentPluginRuntime.class).prompts(IAgentPromptSource.empty());
            var minimal = new AgentDefinition("纯模型", new LLMModelOptionsSnapshot(null),
                    Set.of(AgentActivity.COMMAND), Set.of(), AgentAssembly.parse(AgentAssembly.MINIMAL));
            assertTrue(pluginPrompts.resolve(minimal, request, mock(AgentRuntimeSnapshot.class), control)
                    .toCompletableFuture().get(5, TimeUnit.SECONDS).isEmpty());
            verifyNoInteractions(states);
            var fragments = pluginPrompts.resolve(definition, request, mock(AgentRuntimeSnapshot.class), control)
                    .toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertEquals(1, fragments.size());
            assertTrue(fragments.getFirst().text().contains(type == AffinityComponent.class ? "亲近程度" : "心情"));
            verify(states).update(eq(reference), eq(user), eq(30), anyString(), any(Instant.class), any());
            var input = new AgentPromptInput(List.of(message), fragments, 0);
            component.verify(request, input, control).toCompletableFuture().get(5, TimeUnit.SECONDS);

            var changed = new MemoryNamespaceReference(scope, MemoryNamespaceId.SHARED, 0, 1);
            when(bindings.readReference(scope)).thenReturn(CompletableFuture.completedStage(Optional.of(changed)));
            var stale = assertThrows(ExecutionException.class,
                    () -> component.verify(request, input, control).toCompletableFuture().get(5, TimeUnit.SECONDS));
            assertInstanceOf(AgentMemoryReferenceException.class, stale.getCause());
            config.setEnabled(false);
            var disabled = assertThrows(ExecutionException.class,
                    () -> component.verify(request, input, control).toCompletableFuture().get(5, TimeUnit.SECONDS));
            assertInstanceOf(AgentPromptException.class, disabled.getCause());
        }
    }

    /**
     * 保留循环链中的所有生产服务，隔离外部存储及本测试不执行的旧资料和工具来源。
     * @param first 首个生产 Bean
     * @return 尚未刷新的容器，由调用方关闭，刷新失败也释放资源
     */
    private static AnnotationConfigApplicationContext context(Class<?> first) {
        var context = new AnnotationConfigApplicationContext();
        context.getDefaultListableBeanFactory().setAllowCircularReferences(false);
        context.register(first);
        context.registerBean(ILogger.class, () -> mock(ILogger.class));
        context.registerBean(BeanScanner.class, () -> spy(new BeanScanner(context, context.getBean(ILogger.class))));
        context.registerBean(PluginRegistry.class, () -> new PluginRegistry(
                List.of(() -> new PluginDescriptor("teaneko.agent", "1", 1, Set.of()),
                        PluginDeclarationScanner.declaration(org.zexnocs.teanekoplugin.agent.cognition.CognitionPlugin.class)::descriptor,
                        PluginDeclarationScanner.declaration(org.zexnocs.teanekoplugin.agent.social.SocialPlugin.class)::descriptor)));
        context.registerBean(IConfigDataService.class, () -> mock(IConfigDataService.class));
        context.registerBean(ISocialStateStore.class, () -> mock(ISocialStateStore.class));
        context.registerBean(IMemoryNamespaceStore.class, () -> mock(IMemoryNamespaceStore.class));
        context.registerBean(IMemoryNamespaceBindingStore.class, () -> mock(IMemoryNamespaceBindingStore.class));
        context.registerBean(IMemoryNamespaceDefaults.class, () -> () -> 1);
        context.registerBean(IAgentDeliveryStore.class, () -> mock(IAgentDeliveryStore.class));
        context.registerBean(ISharedConversationStore.class, () -> mock(ISharedConversationStore.class));
        context.registerBean(ChatIdentityService.class, () -> mock(ChatIdentityService.class));
        context.registerBean(TeaNekoScopeService.class, () -> mock(TeaNekoScopeService.class));
        context.registerBean("virtualExecutor", ExecutorService.class, () -> {
            var executor = mock(ExecutorService.class);
            doAnswer(invocation -> {
                invocation.<Runnable>getArgument(0).run();
                return null;
            }).when(executor).execute(any(Runnable.class));
            return executor;
        });
        context.registerBean("taskExecutor", ScheduledExecutorService.class,
                () -> mock(ScheduledExecutorService.class));
        context.registerBean(AgentAccessGate.class, () -> mock(AgentAccessGate.class));
        context.registerBean(ILLMInteractionService.class, () -> mock(ILLMInteractionService.class));
        context.registerBean(ObjectMapper.class, () -> new ObjectMapper());
        // 2026/11/06：满足原记忆和工具组件的能力声明，不连接旧资料与工具的外部基础设施。
        for (String id : List.of("builtin-memory", "long-memory", "layered-memory", "memory-tools")) {
            context.registerBean(id, SourceAgentFeature.class, () -> new SourceAgentFeature("agent.cognition", id,
                    true, (definition, request, runtime) -> List.of(), IAgentPromptSource.empty()));
        }
        context.register(PluginAccess.class, YAMLFileTypeParser.class, AgentScopeConfigManager.class,
                ConfigDataAgentScopePolicy.class, StoreAgentMemoryReferences.class, MemoryNamespaceConfig.class,
                // 2026/11/07：读取规则归认知插件，社交能力仅组合既有资格。
                AgentComponentAccess.class, MemoryReadAccess.class,
                SocialAccess.class, SocialStateService.class, SharedMemoryPromptSource.class,
                LLMComponent.class, ToolComponent.class, MemoryInputSystem.class, MemoryOutputSystem.class,
                MemoryComponent.class, AffinityComponent.class, MoodComponent.class, MemoryComponent.class,
                AgentComponentRegistry.class, AgentPluginRuntime.class, AgentExecutionConfig.class);
        return context;
    }
}
