package org.zexnocs.teanekoagent.component;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.zexnocs.teanekoagent.component.llm.LLMComponent;
import org.zexnocs.teanekoagent.component.llm.framework.model.LLMModelOptionsSnapshot;
import org.zexnocs.teanekoagent.component.skill.SkillComponent;
import org.zexnocs.teanekoagent.component.tool.ToolComponent;
import org.zexnocs.teanekoagent.integration.config.AgentScopeConfigData;
import org.zexnocs.teanekoagent.plugin.AgentFeature;
import org.zexnocs.teanekoagent.plugin.AgentPluginRuntime;
import org.zexnocs.teanekoagent.plugin.SourceAgentFeature;
import org.zexnocs.teanekoagent.runtime.AgentDefinition;
import org.zexnocs.teanekoagent.runtime.prompt.IAgentPromptSource;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.event.interfaces.IEventService;
import org.zexnocs.teanekocore.file_config.parser.YAMLFileTypeParser;
import org.zexnocs.teanekocore.framework.composition.*;
import org.zexnocs.teanekocore.framework.pair.IndependentPair;
import org.zexnocs.teanekocore.plugin.PluginAccess;
import org.zexnocs.teanekocore.plugin.PluginDescriptor;
import org.zexnocs.teanekocore.plugin.PluginOwned;
import org.zexnocs.teanekocore.plugin.PluginRegistry;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IBeanScanner;
import tools.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 验证装配协议、历史读取、插件参数执行及秘密保留，不连接外部模型。
 *
 * <br>2026/11/04：更新分层重构后的类型引用，保持原调用与生命周期行为。
 * <br>2026/11/05：验证工具组件不绑定具体客户端，扩展仅在要求的组件已装配时启用。
 *
 * <br>2026/11/05：简化冗余类型实参、变量或转换，保持原业务与测试边界。
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.8.0-l
 * @version 4.8.0-l
 */
class AgentAssemblyTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    /** 新登记仅有模型，旧行保持原能力，明确装配不能接受缺失模型、重复键及尾随内容。 */
    @Test
    void preservesLegacyButNewRegistrationsAreMinimal() {
        var fresh = new AgentScopeConfigData();
        assertFalse(fresh.assembly().legacy());
        assertEquals(Set.of("llm"), fresh.assembly().parameters().keySet());
        assertEquals(0, fresh.getHistoryMaxMessages());
        assertFalse(fresh.isMemoryLearningEnabled());
        var old = JSON.readValue("{\"enabled\":true,\"historyMaxMessages\":23}", AgentScopeConfigData.class);
        assertTrue(old.assembly().legacy());
        assertTrue(old.isMemoryLearningEnabled());
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
        contributions.add(new AgentFeature() {
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
        var scanner = mock(IBeanScanner.class);
        var beans = new LinkedHashMap<String, org.zexnocs.teanekocore.framework.pair.Pair<TeaNekoAgentComponent, IAgentComponent>>();
        var components = new ArrayList<IAgentComponent>(List.of(new LLMComponent(new YAMLFileTypeParser())));
        components.addAll(List.of(additional));
        for (var component : components) {
            var declaration = component.getClass().getAnnotation(TeaNekoAgentComponent.class);
            beans.put(declaration.id(), new IndependentPair<>(declaration, component));
            doReturn(component.getClass()).when(scanner).getBeanClass(component);
        }
        when(scanner.getBeansWithAnnotationAndInterface(TeaNekoAgentComponent.class, IAgentComponent.class))
                .thenReturn(beans);
        return new AgentComponentRegistry(scanner, new PluginAccess(plugins()));
    }

    /** @return 本组测试实际使用的插件注册表。 */
    private static PluginRegistry plugins() {
        return new PluginRegistry(List.of(() -> new PluginDescriptor("teaneko.agent", "1", 1, Set.of())));
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
    @PluginOwned("teaneko.agent")
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
        public List<AgentFeature.SystemContribution> systems() {
            return List.of(new AgentFeature.SystemContribution(AgentFeature.Phase.BEFORE_PROMPT,
                    new AgentPipelineStep(new SystemDescriptor("agent.component.sample.project", Set.of(key()),
                            Set.of(RESULT), Set.of()), context -> CompletableFuture.completedStage(
                            SystemResult.continueWith(ComponentPatch.builder().put(RESULT,
                                    JSON.readTree(context.snapshot().require(key()).json()).path("text").asString()).build())))));
        }
    }
}
