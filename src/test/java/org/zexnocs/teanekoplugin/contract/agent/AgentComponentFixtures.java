package org.zexnocs.teanekoplugin.contract.agent;

import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.zexnocs.teanekoagent.component.AgentAssembly;
import org.zexnocs.teanekoagent.component.AgentComponentRegistry;
import org.zexnocs.teanekoagent.component.IAgentComponent;
import org.zexnocs.teanekoagent.component.TeaNekoAgentComponent;
import org.zexnocs.teanekoagent.component.llm.LLMComponent;
import org.zexnocs.teanekoagent.component.tool.ToolComponent;
import org.zexnocs.teanekoagent.integration.config.AgentComponentAccess;
import org.zexnocs.teanekoagent.integration.config.AgentScopeConfigData;
import org.zexnocs.teanekoagent.integration.config.AgentScopeConfigManager;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataService;
import org.zexnocs.teanekocore.file_config.parser.YAMLFileTypeParser;
import org.zexnocs.teanekocore.framework.pair.IPair;
import org.zexnocs.teanekocore.framework.pair.IndependentPair;
import org.zexnocs.teanekocore.plugin.HostCapability;
import org.zexnocs.teanekocore.plugin.PluginAccess;
import org.zexnocs.teanekocore.plugin.PluginDeclarationScanner;
import org.zexnocs.teanekocore.plugin.PluginRegistry;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IBeanScanner;
import org.zexnocs.teanekoplugin.agent.automation.AutomationAssemblyMigration;
import org.zexnocs.teanekoplugin.agent.automation.AutomationPlugin;
import org.zexnocs.teanekoplugin.agent.automation.CommandComponent;
import org.zexnocs.teanekoplugin.agent.automation.schedule.ScheduleComponent;
import org.zexnocs.teanekoplugin.agent.automation.skill.SkillComponent;
import org.zexnocs.teanekoplugin.agent.cognition.CognitionAssemblyMigration;
import org.zexnocs.teanekoplugin.agent.cognition.CognitionPlugin;
import org.zexnocs.teanekoplugin.agent.cognition.memory.MemoryComponent;
import org.zexnocs.teanekoplugin.agent.cognition.memory.MemoryInputSystem;
import org.zexnocs.teanekoplugin.agent.cognition.memory.MemoryOutputSystem;
import org.zexnocs.teanekoplugin.agent.cognition.personality.PersonalityComponent;
import org.zexnocs.teanekoplugin.agent.expression.ExpressionAssemblyMigration;
import org.zexnocs.teanekoplugin.agent.expression.ExpressionPlugin;
import org.zexnocs.teanekoplugin.agent.expression.expression.ExpressionCompileSystem;
import org.zexnocs.teanekoplugin.agent.expression.expression.ExpressionComponent;
import org.zexnocs.teanekoplugin.agent.expression.sticker.StickerComponent;
import org.zexnocs.teanekoplugin.agent.social.SocialAssemblyMigration;
import org.zexnocs.teanekoplugin.agent.social.SocialPlugin;
import org.zexnocs.teanekoplugin.agent.social.proactive.ProactiveComponent;
import org.zexnocs.teanekoplugin.agent.social.proactive.ProactiveConfiguration;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * 业务集成测试共用真实组件声明与迁移器，避免在夹具中复制宿主的旧业务字段表。
 * <br>2026/11/08：扫描只发生在夹具初始化，业务查询使用同一已发布目录。
 *
 * @author zExNocs
 * @date 2026/11/08
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
public final class AgentComponentFixtures {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final AgentComponentRegistry DIRECTORY = directory();

    /** 禁止创建无状态测试装配入口。 */
    private AgentComponentFixtures() {
    }

    /**
     * 使用真实插件归属、组件参数校验及旧配置迁移，不执行业务系统。
     * @return 当前测试独立目录
     */
    public static AgentComponentRegistry directory() {
        var plugins = new PluginRegistry(List.of(
                PluginDeclarationScanner.declaration(CognitionPlugin.class)::descriptor,
                PluginDeclarationScanner.declaration(ExpressionPlugin.class)::descriptor,
                PluginDeclarationScanner.declaration(SocialPlugin.class)::descriptor,
                PluginDeclarationScanner.declaration(AutomationPlugin.class)::descriptor),
                List.of(new HostCapability("teaneko.agent", "1", Set.of())));
        return directory(plugins);
    }

    /**
     * 使用调用方实际插件状态装配参数声明，缺少插件时不伪造其贡献。
     * @param plugins 当前测试控制的插件目录
     * @return 绑定同一启停状态的组件目录
     */
    public static AgentComponentRegistry directory(PluginRegistry plugins) {
        var installed = plugins.list().stream().map(view -> view.descriptor().id()).collect(java.util.stream.Collectors.toSet());
        var memory = spy(new MemoryComponent(mock(MemoryInputSystem.class), mock(MemoryOutputSystem.class)));
        doReturn(List.of()).when(memory).systems();
        var expression = spy(new ExpressionComponent(mock(ExpressionCompileSystem.class)));
        doReturn(List.of()).when(expression).systems();
        var personality = new PersonalityComponent();
        var declarations = List.of(new LLMComponent(new YAMLFileTypeParser()), new ToolComponent(),
                memory, personality, expression, new StickerComponent(), new SkillComponent(),
                new ScheduleComponent(), new CommandComponent(), new ProactiveComponent());
        var scanner = mock(IBeanScanner.class);
        var beans = new LinkedHashMap<String, IPair<TeaNekoAgentComponent, IAgentComponent>>();
        for (var component : declarations) {
            var type = component.getClass();
            var annotation = type.getAnnotation(TeaNekoAgentComponent.class);
            var owner = type.getAnnotation(org.zexnocs.teanekocore.plugin.PluginOwned.class);
            if (owner != null && !installed.contains(owner.value())) {
                continue;
            }
            beans.put(annotation.id(), IndependentPair.of(annotation, component));
            doReturn(type).when(scanner).getBeanClass(component);
        }
        when(scanner.getBeansWithAnnotationAndInterface(TeaNekoAgentComponent.class, IAgentComponent.class))
                .thenReturn(beans);
        var directory = new AgentComponentRegistry(scanner, new PluginAccess(plugins));
        directory.installMigrations(List.of(new CognitionAssemblyMigration(), new ExpressionAssemblyMigration(),
                new AutomationAssemblyMigration(), new SocialAssemblyMigration()));
        return directory;
    }

    /**
     * 将既有权威配置夹具接入真实有效装配读取。
     * @param configs 夹具配置服务
     * @param manager 原配置声明
     * @return 使用缓存声明的组件参数读取器
     */
    public static AgentComponentAccess access(IConfigDataService configs, AgentScopeConfigManager manager) {
        var beans = new DefaultListableBeanFactory();
        beans.registerSingleton("components", DIRECTORY);
        return new AgentComponentAccess(configs, manager, beans.getBeanProvider(AgentComponentRegistry.class), JSON);
    }

    /**
     * 给隔离业务容器补充真实配置适配，复用容器的插件状态且不提前读取配置。
     * @param context 尚未刷新的容器
     */
    public static void register(org.springframework.context.annotation.AnnotationConfigApplicationContext context) {
        context.registerBean(AgentComponentAccess.class, () -> {
            var beans = new DefaultListableBeanFactory();
            var plugins = context.getBeanProvider(PluginRegistry.class).getIfAvailable();
            beans.registerSingleton("components", plugins == null ? DIRECTORY : directory(plugins));
            return new AgentComponentAccess(context.getBean(IConfigDataService.class),
                    context.getBean(AgentScopeConfigManager.class), beans.getBeanProvider(AgentComponentRegistry.class), JSON);
        });
    }

    /**
     * 复用真实主动配置适配器，候选测试不绕过组件选择。
     * @param configs 权威配置夹具
     * @param manager 原配置声明
     * @return 本插件活动参数入口
     */
    public static ProactiveConfiguration proactive(IConfigDataService configs, AgentScopeConfigManager manager) {
        return new ProactiveConfiguration(configs, manager, access(configs, manager));
    }

    /**
     * 为需要认知读取资格的集成测试装配真实策略。
     * @param configs 权威配置夹具
     * @param manager 原配置声明
     * @return 使用默认本地总开关的认知读取策略
     */
    public static org.zexnocs.teanekoplugin.agent.cognition.memory.configuration.MemoryReadAccess memory(
            IConfigDataService configs, AgentScopeConfigManager manager) {
        return new org.zexnocs.teanekoplugin.agent.cognition.memory.configuration.MemoryReadAccess(
                access(configs, manager), new org.springframework.mock.env.MockEnvironment());
    }

    /**
     * 修改测试明确指定的组件参数，旧平铺记录继续保持旧布局供真实迁移器处理。
     * @param config 被测试记录
     * @param component 所属组件
     * @param field 参数名
     * @param value 新值
     */
    public static void set(AgentScopeConfigData config, String component, String field, Object value) {
        JsonNode node = JSON.valueToTree(value);
        if (config.getComponents() == null) {
            config.retain(field, node);
            return;
        }
        var document = AgentAssembly.document(config.getComponents());
        var installed = (ObjectNode) document.path("components");
        var parameters = installed.get(component);
        if (parameters instanceof ObjectNode object) {
            object.set(field, node);
        } else {
            installed.putObject(component).set(field, node);
        }
        config.setComponents(document.toString());
    }

    /**
     * 通过真实迁移读取指定组件的开关，未选择组件时明确为假。
     * @param config 当前测试记录
     * @param component 目标组件
     * @param field 开关名
     * @param initial 该组件声明的缺省值
     * @return 当前开关
     */
    public static boolean flag(AgentScopeConfigData config, String component, String field, boolean initial) {
        var assembly = DIRECTORY.effective(DIRECTORY.normalizeConfiguration(JSON.writeValueAsString(config)));
        return assembly.has(component) && assembly.settings(component).path(field).asBoolean(initial);
    }
}
