package org.zexnocs.teanekoagent.component;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.zexnocs.teanekoagent.component.llm.LLMComponent;
import org.zexnocs.teanekoagent.component.tool.ToolComponent;
import org.zexnocs.teanekoagent.integration.config.AgentScopeConfigData;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptionsSnapshot;
import org.zexnocs.teanekoagent.plugin.AgentPluginRuntime;
import org.zexnocs.teanekoagent.plugin.IAgentFeature;
import org.zexnocs.teanekoagent.plugin.SourceAgentFeature;
import org.zexnocs.teanekoagent.runtime.AgentDefinition;
import org.zexnocs.teanekoagent.runtime.prompt.IAgentPromptSource;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.event.interfaces.IEventService;
import org.zexnocs.teanekocore.file_config.parser.YAMLFileTypeParser;
import org.zexnocs.teanekocore.framework.composition.ComponentKey;
import org.zexnocs.teanekocore.framework.composition.ComponentPatch;
import org.zexnocs.teanekocore.framework.composition.Entity;
import org.zexnocs.teanekocore.framework.composition.EntityId;
import org.zexnocs.teanekocore.framework.composition.SystemContext;
import org.zexnocs.teanekocore.framework.composition.SystemDescriptor;
import org.zexnocs.teanekocore.framework.composition.SystemResult;
import org.zexnocs.teanekocore.framework.pair.IndependentPair;
import org.zexnocs.teanekocore.plugin.HostCapability;
import org.zexnocs.teanekocore.plugin.PluginAccess;
import org.zexnocs.teanekocore.plugin.PluginDeclarationScanner;
import org.zexnocs.teanekocore.plugin.PluginRegistry;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IBeanScanner;
import org.zexnocs.teanekoplugin.agent.automation.AutomationAssemblyMigration;
import org.zexnocs.teanekoplugin.agent.automation.CommandComponent;
import org.zexnocs.teanekoplugin.agent.automation.skill.SkillComponent;
import org.zexnocs.teanekoplugin.agent.cognition.CognitionAssemblyMigration;
import org.zexnocs.teanekoplugin.agent.cognition.memory.MemoryComponent;
import org.zexnocs.teanekoplugin.agent.cognition.memory.MemoryInputSystem;
import org.zexnocs.teanekoplugin.agent.cognition.memory.MemoryOutputSystem;
import org.zexnocs.teanekoplugin.agent.cognition.personality.PersonalityComponent;
import org.zexnocs.teanekoplugin.agent.social.contact.PrivateContactComponent;
import org.zexnocs.teanekoplugin.contract.agent.AgentComponentFixtures;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// 2026/11/07：接口统一使用 I 前缀，调用合同与持久数据保持不变。
// 2026/11/07：隔离目录复用生产插件注解，不创建业务入口实例。
// 2026/11/07：认知业务契约归入插件，纯会话数据沿用运行底座。
// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。
// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

/**
 * 验证装配协议、历史读取、插件参数执行及秘密保留，不连接外部模型。
 *
 * <br>2026/11/04：更新分层重构后的类型引用，保持原调用与生命周期行为。
 * <br>2026/11/05：验证工具组件不绑定具体客户端，扩展仅在要求的组件已装配时启用。
 * <br>2026/11/05：简化冗余类型实参、变量或转换，保持原业务与测试边界。
 * <br>2026/11/06：验证声明缓存、重扫失败、组件排序及真实 Spring 装配。
 * <br>2026/11/07：完整安装说明读取同一声明缓存，不触发额外发现。
 * <br>2026/11/07：验证停用期间迁移旧平铺配置、编辑保存及重新启用后保留历史能力选择。
 *
 * <br>2026/11/08：夹具通过真实插件组件契约准备参数并验证改造后的端口。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.8.0-l
 * @version 4.9.0-l
 */
class AgentAssemblyTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    /** 新登记仅有模型，旧行保持原能力，明确装配不能接受缺失模型、重复键及尾随内容。 */
    @Test
    void preservesLegacyButNewRegistrationsAreMinimal() {
        var fresh = new AgentScopeConfigData();
        assertFalse(fresh.assembly().legacy());
        assertEquals(Set.of("llm"), fresh.assembly().parameters().keySet());
        assertEquals(-1, fresh.getHistoryMaxMessages());
        assertFalse(AgentComponentFixtures.flag(fresh, "memory", "memoryLearningEnabled", true));
        var old = JSON.readValue("{\"enabled\":true,\"historyMaxMessages\":23}", AgentScopeConfigData.class);
        assertTrue(old.assembly().legacy());
        assertTrue(AgentComponentFixtures.flag(old, "memory", "memoryLearningEnabled", true));
        assertEquals(23, old.getHistoryMaxMessages());
        for (String invalid : List.of("{\"version\":1,\"components\":{}}",
                "{\"version\":1,\"components\":{\"llm\":{},\"llm\":{}}}", AgentAssembly.MINIMAL + "{}")) {
            assertThrows(IllegalArgumentException.class, () -> AgentAssembly.parse(invalid));
        }
    }

    /** 参数经过真实注册表、初始化补丁和 Core System 执行；未知组件及依赖缺失明确拒绝。 */
    @Test
    void runsRegisteredComponentParametersThroughCorePipeline() throws Exception {
        var component = new SampleComponent();
        var registry = registry(component, new SkillComponent());
        var assembly = registry.validate("{\"version\":1,\"components\":{\"llm\":{},\"sample\":{\"text\":\"实际参数\"}}}");
        var definition = new AgentDefinition("测试", new LLMModelOptionsSnapshot(null), Set.of(), Set.of(), assembly);
        var runtime = new AgentPluginRuntime(plugins(), registry.contributions(),
                new DefaultListableBeanFactory().getBeanProvider(IEventService.class));
        assertEquals(Set.of("teaneko.agent:component-llm", "teaneko.agent:component-sample"),
                runtime.select(definition).tokens().keySet());
        var entity = new Entity(EntityId.random());
        entity.commit(0, component.initialize(assembly));
        try (var control = ExecutionControl.create()) {
            var result = component.systems().getFirst().system().execute(new SystemContext(entity.snapshot(), control))
                    .toCompletableFuture().join();
            entity.commit(entity.snapshot().revision(), result.patch());
            assertEquals("实际参数", entity.snapshot().require(SampleComponent.RESULT));
        }
        assertThrows(IllegalArgumentException.class, () -> registry.validate(
                "{\"version\":1,\"components\":{\"llm\":{},\"skill\":{}}}"));
        assertThrows(IllegalArgumentException.class, () -> registry.validate(
                "{\"version\":1,\"components\":{\"llm\":{},\"unknown\":{}}}"));
    }

    /**
     * 2026/11/05：无平台扩展也能装配工具；带资料的扩展按组件要求选择，纯模型不启用。
     * 2026/11/05：简化冗余类型实参、变量或转换，保持原业务与测试边界。
     */
    @Test
    void selectsExtensionsByDeclaredComponentRequirements() {
        var registry = registry(new ToolComponent());
        var contributions = new ArrayList<>(registry.contributions());
        contributions.add(new SourceAgentFeature("teaneko.agent", "builtin-tools", true,
                (definition, request, runtime) -> List.of(), IAgentPromptSource.empty(), Set.of()));
        var events = new DefaultListableBeanFactory().getBeanProvider(IEventService.class);
        var definition = new AgentDefinition("工具测试", new LLMModelOptionsSnapshot(null), Set.of(), Set.of(),
                registry.validate("{\"version\":1,\"components\":{\"llm\":{},\"tool\":{}}}"));
        var runtime = new AgentPluginRuntime(plugins(), contributions, events);
        assertTrue(runtime.select(definition).tokens().containsKey("teaneko.agent:component-tool"));
        contributions.add(new IAgentFeature() {
            /** @return 已注册测试插件 */
            @Override
            public String pluginId() {
                return "teaneko.agent";
            }

            /** @return 不对应任何固定客户端的扩展 */
            @Override
            public String id() {
                return "platform-extension";
            }

            /** @return 满足组件要求时默认启用 */
            @Override
            public boolean defaultEnabled() {
                return true;
            }

            /** @return 必须装配工具，不能通过空资料规则绕过要求 */
            @Override
            public Set<String> assemblyRequirements() {
                return Set.of("tool");
            }

            /** @return 扩展独占资料片段 */
            @Override
            public Set<String> promptNames() {
                return Set.of("platform_context");
            }
        });
        runtime = new AgentPluginRuntime(plugins(), contributions, events);
        assertTrue(runtime.select(definition).tokens().containsKey("teaneko.agent:platform-extension"));
        var minimal = new AgentDefinition("纯模型测试", new LLMModelOptionsSnapshot(null), Set.of(), Set.of(),
                registry.validate(AgentAssembly.MINIMAL));
        assertEquals(Set.of("teaneko.agent:component-llm"), runtime.select(minimal).tokens().keySet());
    }

    /** 私有连接不进入公共实体；Web 保留原位置秘密，修改其他参数不会替换密钥。 */
    @Test
    void redactsPrivateConfigurationAndPreservesOnlyExplicitSecrets() {
        var registry = registry();
        String original = privateAssembly("test-secret");
        var safe = registry.validate(original);
        assertFalse(safe.toString().contains("test-secret"));
        assertFalse(safe.settings("llm").has("configuration"));
        String preview = registry.editor(JSON.writeValueAsString(Map.of("components", original)));
        assertFalse(preview.contains("test-secret"));
        assertTrue(preview.contains("$keep"));
        assertEquals(AgentAssembly.document(original), AgentAssembly.document(registry.mergeSecrets(preview, original)));
        assertThrows(IllegalArgumentException.class, () -> registry.mergeSecrets(preview, null));
        assertNotEquals(safe.revision(), registry.validate(privateAssembly("changed-secret")).revision());
    }

    /** 2026/11/07：停用期间先完整迁移，再保存可见组件，不丢失隐藏的原记忆和人格选择。 */
    @Test
    void preservesLegacyComponentsAcrossDisabledPluginEdits() {
        var plugins = plugins();
        var memory = spy(new MemoryComponent(mock(MemoryInputSystem.class), mock(MemoryOutputSystem.class)));
        doReturn(List.of()).when(memory).systems();
        var personality = new PersonalityComponent();
        var registry = registry(plugins, new ToolComponent(), memory, personality, new SkillComponent(),
                new CommandComponent(), new PrivateContactComponent(), new SampleComponent());
        registry.installMigrations(List.of(new CognitionAssemblyMigration(), new AutomationAssemblyMigration()));
        plugins.setEnabled("agent.cognition", false);
        plugins.setEnabled("agent.automation", false);
        // 2026/11/07：业务插件关闭不能隐藏原生模型与工具，公开目录不伪造插件 ID。
        assertTrue(registry.componentEnabled("llm"));
        assertTrue(registry.componentEnabled("tool"));
        assertTrue(registry.describe().stream().filter(item -> Set.of("llm", "tool").contains(item.id()))
                .allMatch(item -> item.pluginId() == null));
        String normalized = registry.normalizeConfiguration("""
                {"components":null,"enabled":true,"historyMaxMessages":23,"memoryReadEnabled":false,
                "customPersonalityEnabled":true,"customPersonality":"原有人格","modelRoute":"previous-route",
                "commandToolsEnabled":false}
                """);
        var original = AgentAssembly.document(normalized);
        assertEquals(Set.of("llm", "tool", "memory", "personality", "skill"),
                Set.copyOf(original.path("components").propertyNames()));
        assertEquals(23, original.at("/components/memory/historyMaxMessages").asInt());
        assertFalse(original.at("/components/memory/memoryReadEnabled").asBoolean());
        assertFalse(original.at("/components/memory/crossChatRecallEnabled").asBoolean());
        assertFalse(original.at("/components/skill/skillsEnabled").asBoolean());
        assertEquals("previous-route", original.at("/components/llm/route").asString());
        String row = JSON.writeValueAsString(Map.of("components", normalized));
        assertEquals(original, AgentAssembly.document(registry.normalizeConfiguration(row)));
        var preview = AgentAssembly.document(registry.editor(row));
        assertEquals(Set.of("llm", "tool"), Set.copyOf(preview.path("components").propertyNames()));
        ((tools.jackson.databind.node.ObjectNode) preview.at("/components/llm")).put("route", "edited-route");
        String saved = registry.mergeSecrets(preview.toString(), normalized);
        plugins.setEnabled("agent.cognition", true);
        plugins.setEnabled("agent.automation", true);
        var restored = registry.effective(saved);
        assertTrue(restored.has("memory"));
        assertTrue(restored.has("personality"));
        assertEquals(23, restored.settings("memory").path("historyMaxMessages").asInt());
        assertEquals("原有人格", restored.settings("personality").path("customPersonality").asString());
        assertEquals("edited-route", restored.settings("llm").path("route").asString());
        assertFalse(restored.has("command"));
        assertFalse(restored.has("private-contact"));
    }

    /** 2026/11/07：旧命令仅按原开启值安装，可信整行转换不脱敏已有私有模型凭据。 */
    @Test
    void migratesOnlyPreviouslyEnabledCommandsAndPreservesPrivateCredentials() {
        var registry = registry(new ToolComponent(), new CommandComponent());
        registry.installMigrations(List.of(new AutomationAssemblyMigration()));
        assertFalse(AgentAssembly.parse(registry.normalizeConfiguration("{}")).has("command"));
        assertFalse(AgentAssembly.parse(registry.normalizeConfiguration("{\"commandToolsEnabled\":false}"))
                .has("command"));
        var enabled = AgentAssembly.parse(registry.normalizeConfiguration("{\"commandToolsEnabled\":true}"));
        assertTrue(enabled.has("command"));
        assertTrue(enabled.settings("command").path("commandToolsEnabled").asBoolean());
        assertThrows(IllegalArgumentException.class,
                () -> registry.normalizeConfiguration("{\"commandToolsEnabled\":\"false\"}"));
        String original = privateAssembly("test-secret");
        String row = JSON.writeValueAsString(Map.of("components", original));
        assertEquals(AgentAssembly.document(original), AgentAssembly.document(registry.normalizeConfiguration(row)));
        assertFalse(registry.editor(row).contains("test-secret"));
    }

    /** 2026/11/06：排序、静态描述与参数校验复用同一目录，重扫失败不污染已发布快照。 */
    @Test
    void cachesDeclarationsAndKeepsThePreviousSnapshotOnReloadFailure() {
        var scanner = mock(IBeanScanner.class);
        var llm = new LLMComponent(new YAMLFileTypeParser());
        var tool = spy(new ToolComponent());
        var declarations = Map.<String, org.zexnocs.teanekocore.framework.pair.IPair<TeaNekoAgentComponent, IAgentComponent>>of(
                "llm", new IndependentPair<>(LLMComponent.class.getAnnotation(TeaNekoAgentComponent.class), llm),
                "tool", new IndependentPair<>(ToolComponent.class.getAnnotation(TeaNekoAgentComponent.class), tool));
        when(scanner.getBeansWithAnnotationAndInterface(TeaNekoAgentComponent.class, IAgentComponent.class)).thenReturn(declarations);
        doReturn(LLMComponent.class).when(scanner).getBeanClass(llm);
        doReturn(ToolComponent.class).when(scanner).getBeanClass(tool);
        var plugins = plugins();
        var registry = new AgentComponentRegistry(scanner, new PluginAccess(plugins));
        clearInvocations(scanner, tool);
        String assembly = "{\"version\":1,\"components\":{\"llm\":{},\"tool\":{}}}";
        for (int i = 0; i < 3; i++) {
            registry.validate(assembly);
            assertEquals(List.of("llm", "tool"), registry.describe().stream().map(AgentComponentRegistry.Description::id).toList());
            assertEquals(List.of("llm", "tool"), registry.installedDescription().stream()
                    .map(AgentComponentRegistry.Description::id).toList());
        }
        verifyNoInteractions(scanner);
        verify(tool, never()).fields();
        verify(tool, never()).defaults();
        verify(tool, never()).systems();
        when(scanner.getBeansWithAnnotationAndInterface(TeaNekoAgentComponent.class, IAgentComponent.class))
                .thenThrow(new IllegalStateException("测试显式重扫失败"));
        assertThrows(IllegalStateException.class, registry::reload);
        assertEquals(2, registry.describe().size());
        var defaults = registry.describe().getFirst().defaults();
        defaults.put("source", "不能污染共享模板");
        assertNotEquals("不能污染共享模板", registry.describe().getFirst().defaults().get("source"));
    }

    /** 真实 Spring 构造、BeanScanner 和内置组件建立目录；目录不需要额外启动顺序补丁。 */
    @Test
    void realSpringWiresComponentDiscoveryAndObservation() {
        try (var context = new org.springframework.context.annotation.AnnotationConfigApplicationContext()) {
            context.registerBean(org.zexnocs.teanekocore.logger.ILogger.class,
                    () -> mock(org.zexnocs.teanekocore.logger.ILogger.class));
            context.registerBean(PluginRegistry.class, AgentAssemblyTest::plugins);
            context.registerBean(YAMLFileTypeParser.class, () -> new YAMLFileTypeParser());
            context.registerBean(org.zexnocs.teanekocore.file_config.interfaces.IFileConfigService.class,
                    () -> mock(org.zexnocs.teanekocore.file_config.interfaces.IFileConfigService.class));
            context.register(org.zexnocs.teanekocore.utils.scanner.BeanScanner.class, PluginAccess.class,
                    LLMComponent.class, ToolComponent.class, AgentComponentRegistry.class,
                    org.zexnocs.teanekoagent.integration.monitor.AgentObservationService.class,
                    org.zexnocs.teanekoagent.runtime.monitor.AgentActivityTimeline.class);
            context.refresh();
            var registry = context.getBean(AgentComponentRegistry.class);
            assertEquals(List.of("llm", "tool"),
                    registry.describe().stream().map(AgentComponentRegistry.Description::id).toList());
            assertEquals(Set.of("llm"), registry.validate(AgentAssembly.MINIMAL).parameters().keySet());
            assertSame(context.getBean(org.zexnocs.teanekoagent.integration.monitor.AgentObservationService.class),
                    context.getBean(org.zexnocs.teanekoagent.runtime.monitor.IAgentActivityObserver.class));
        }
    }

    /**
     * @param secret 本地假凭据
     * @return 供隔离测试使用的私有模型配置
     */
    static String privateAssembly(String secret) {
        return """
                {"version":1,"components":{"llm":{"source":"custom","configuration":{
                "adapter":"deepseek","model":"test-model","max-tokens":128,"context-window-tokens":8192,
                "api-key":"%s","base-url":"https://test.invalid","api-path":"/chat/completions"}}}}
                """.formatted(secret);
    }

    /**
     * @param additional 明确扫描的测试组件
     * @return 使用真实注册规则的独立目录
     */
    static AgentComponentRegistry registry(IAgentComponent... additional) {
        // 2026/11/07：共享独立目录装配，允许生命周期回归持有真实插件注册表。
        return registry(plugins(), additional);
    }

    /**
     * 2026/11/07：使用调用方的真实插件状态组装独立组件目录。
     * @param plugins 测试控制启停的插件目录
     * @param additional 明确扫描的测试组件
     * @return 只在构造时发现组件的独立目录
     */
    private static AgentComponentRegistry registry(PluginRegistry plugins, IAgentComponent... additional) {
        var scanner = mock(IBeanScanner.class);
        var beans = new LinkedHashMap<String, org.zexnocs.teanekocore.framework.pair.IPair<TeaNekoAgentComponent, IAgentComponent>>();
        var components = new ArrayList<IAgentComponent>(List.of(new LLMComponent(new YAMLFileTypeParser())));
        components.addAll(List.of(additional));
        for (var component : components) {
            var declaration = component.getClass().getAnnotation(TeaNekoAgentComponent.class);
            beans.put(declaration.id(), new IndependentPair<>(declaration, component));
            doReturn(component.getClass()).when(scanner).getBeanClass(component);
        }
        when(scanner.getBeansWithAnnotationAndInterface(TeaNekoAgentComponent.class, IAgentComponent.class))
                .thenReturn(beans);
        return new AgentComponentRegistry(scanner, new PluginAccess(plugins));
    }

    /** @return 本组测试实际使用的插件注册表。 */
    private static PluginRegistry plugins() {
        return new PluginRegistry(List.of(
                PluginDeclarationScanner.declaration(org.zexnocs.teanekoplugin.agent.automation.AutomationPlugin.class)::descriptor,
                PluginDeclarationScanner.declaration(org.zexnocs.teanekoplugin.agent.cognition.CognitionPlugin.class)::descriptor,
                PluginDeclarationScanner.declaration(org.zexnocs.teanekoplugin.agent.social.SocialPlugin.class)::descriptor),
                List.of(new HostCapability("teaneko.agent", "1", Set.of())));
    }

    /**
     * 单一可参数化插件组件，系统只写自有数据键。
     *
     * @author zExNocs
     * @date 2026/11/04
     * @since 4.8.0-l
     * @version 4.8.0-l
     */
    @TeaNekoAgentComponent(id = "sample", name = "测试组件", description = "读取独立参数并写入自身结果。")
    // 2026/11/07：该夹具模拟原生组件，不声明业务插件归属。
    static final class SampleComponent extends DeclaredAgentComponent {
        static final ComponentKey<String> RESULT = ComponentKey.of("agent.component.sample.result", String.class);

        /** 创建有独立默认值的组件。 */
        SampleComponent() {
            super("sample", Map.of("text", "默认值"), List.of(new Field("text", "内容", "系统输入", "TEXT")));
        }

        /** @return 本组件拥有的参数和结果。 */
        @Override
        public Set<ComponentKey<?>> keys() {
            return Set.of(key(), RESULT);
        }

        /** @return 在真实阶段中执行的参数投影系统。 */
        @Override
        public List<IAgentFeature.SystemContribution> systems() {
            return List.of(new IAgentFeature.SystemContribution(IAgentFeature.Phase.BEFORE_PROMPT,
                    new AgentPipelineStep(new SystemDescriptor("agent.component.sample.project", Set.of(key()),
                            Set.of(RESULT), Set.of()), context -> CompletableFuture.completedStage(
                            SystemResult.continueWith(ComponentPatch.builder().put(RESULT,
                                    JSON.readTree(context.snapshot().require(key()).json()).path("text").asString()).build())))));
        }
    }
}
