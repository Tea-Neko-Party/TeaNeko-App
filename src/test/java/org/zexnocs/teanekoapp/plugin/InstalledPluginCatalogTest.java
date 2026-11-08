package org.zexnocs.teanekoapp.plugin;

// 2026/11/07：接口统一使用 I 前缀，调用合同与持久数据保持不变。

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;
import org.zexnocs.teanekoagent.component.AgentComponentRegistry;
import org.zexnocs.teanekoagent.component.TeaNekoAgentComponent;
import org.zexnocs.teanekoagent.component.tool.TeaNekoAgentToolRegistry;
import org.zexnocs.teanekoagent.component.tool.api.TeaNekoAgentToolClass;
import org.zexnocs.teanekoagent.integration.web.AgentPluginContributionSource;
import org.zexnocs.teanekoagent.plugin.IAgentFeature;
import org.zexnocs.teanekoagent.plugin.AgentPluginRuntime;
import org.zexnocs.teanekoapp.web.WebSecurityFileConfig;
import org.zexnocs.teanekoapp.web.files.FileSettingsDirectory;
import org.zexnocs.teanekoapp.web.files.TeaNekoFileSettings;
import org.zexnocs.teanekoapp.web.plugins.HostPluginContributionSource;
import org.zexnocs.teanekoapp.web.plugins.IPluginConsoleContribution;
import org.zexnocs.teanekoapp.web.plugins.PluginConsoleDirectory;
import org.zexnocs.teanekoapp.web.settings.ChatSettingsDirectory;
import org.zexnocs.teanekoapp.web.settings.TeaNekoChatSettings;
import org.zexnocs.teanekocore.command.CommandCatalog;
import org.zexnocs.teanekocore.command.CommandScanner;
import org.zexnocs.teanekocore.command.api.Command;
import org.zexnocs.teanekocore.command.api.CommandExtension;
import org.zexnocs.teanekocore.command.interfaces.ICommandPermissionManager;
import org.zexnocs.teanekocore.command.interfaces.ICommandScopeManager;
import org.zexnocs.teanekocore.database.configdata.scanner.ConfigManager;
import org.zexnocs.teanekocore.event.interfaces.IEventService;
import org.zexnocs.teanekocore.file_config.FileConfigScanner;
import org.zexnocs.teanekocore.file_config.FileTypeScanner;
import org.zexnocs.teanekocore.file_config.interfaces.IFileConfigService;
import org.zexnocs.teanekocore.file_config.parser.YAMLFileTypeParser;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekocore.framework.composition.IEntitySystem;
import org.zexnocs.teanekocore.plugin.PluginAccess;
import org.zexnocs.teanekocore.plugin.IPluginModule;
import org.zexnocs.teanekocore.plugin.HostCapability;
import org.zexnocs.teanekocore.plugin.PluginDeclarationScanner;
import org.zexnocs.teanekocore.plugin.TeaNekoPlugin;
import org.zexnocs.teanekocore.plugin.PluginRegistry;
import org.zexnocs.teanekocore.plugin.IPluginLifecycle;
import org.zexnocs.teanekocore.plugin.management.ManagementContext;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.utils.scanner.BeanScanner;
import org.zexnocs.teanekocore.utils.scanner.ClassScanner;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IClassScanner;
import org.zexnocs.teanekoplugin.support.PluginTimerContributionSource;
import tools.jackson.databind.ObjectMapper;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;

/**
 * 用全部实际编译的生产声明建立插件贡献目录，隔离业务调用和外部资源。
 * <br>2026/11/07：真实类/Bean扫描器、模块工厂、组件、配置适配器和来源共同验证目录冲突。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
class InstalledPluginCatalogTest {
    /**
     * 包含条件装配示例在内的生产声明均能发布，停用不会删除静态说明。
     * @throws Exception 当隔离容器创建或静态工厂装配失败时
     */
    @Test
    void allProductionContributionsPublishAndRemainVisibleWhileDisabled() throws Exception {
        try (var context = new AnnotationConfigApplicationContext();
             var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            AutoConfigurationPackages.register(context, "org.zexnocs");
            context.refresh();
            var logger = mock(ILogger.class);
            var mapper = new ObjectMapper();
            context.getBeanFactory().registerSingleton("metadataLogger", logger);
            context.getBeanFactory().registerSingleton("metadataMapper", mapper);
            context.getBeanFactory().registerSingleton("virtualExecutor", executor);
            context.getBeanFactory().registerSingleton("metadataYaml", new YAMLFileTypeParser());
            var classes = new ProductionClasses(new ClassScanner(context, logger));
            var modules = new ArrayList<IPluginModule>();
            // 2026/11/07：读取生产注解元数据，目录发现不创建插件业务入口实例。
            for (var type : classes.getClassesWithAnnotation(TeaNekoPlugin.class).keySet()) {
                var declaration = PluginDeclarationScanner.declaration(type);
                modules.add(declaration::descriptor);
            }
            var factories = new ArrayList<Method>();
            for (var type : classes.getClassesWithAnnotation(Configuration.class).keySet()) {
                for (var method : type.getDeclaredMethods()) {
                    if (method.isAnnotationPresent(Bean.class) && IAgentFeature.class.isAssignableFrom(method.getReturnType())) {
                        factories.add(method);
                    }
                }
            }
            var plugins = new PluginRegistry(modules, List.of(new HostCapability("teaneko.core", "4.9.0-l", Set.of()),
                    new HostCapability("teaneko.agent", "4.9.0-l", Set.of("teaneko.core"))));
            var access = new PluginAccess(plugins);
            context.getBeanFactory().registerSingleton("metadataPlugins", plugins);
            context.getBeanFactory().registerSingleton("metadataAccess", access);
            var beans = new BeanScanner(context, logger);
            var parsers = new FileTypeScanner(beans, logger);
            parsers.init();
            context.getBeanFactory().registerSingleton("metadataParsers", parsers);
            var fileClasses = new FileConfigScanner(classes);
            fileClasses.init();
            context.getBeanFactory().registerSingleton("metadataFileClasses", fileClasses);
            var realTypes = new LinkedHashSet<Class<?>>();
            for (var annotation : List.of(TeaNekoAgentComponent.class, TeaNekoFileSettings.class, TeaNekoChatSettings.class)) {
                realTypes.addAll(classes.getClassesWithAnnotation(annotation).keySet());
            }
            realTypes.addAll(classes.getClassesWithInterface(IPluginConsoleContribution.class));
            classes.getClassesWithInterface(IAgentFeature.class).stream()
                    .filter(type -> AnnotatedElementUtils.hasAnnotation(type, Component.class)).forEach(realTypes::add);
            classes.getClassesWithInterface(IEntitySystem.class).stream()
                    .filter(type -> AnnotatedElementUtils.hasAnnotation(type, Component.class)).forEach(realTypes::add);
            for (var type : realTypes) {
                definition(context, type, false);
            }
            for (var annotation : List.of(Command.class, CommandExtension.class, ConfigManager.class, TeaNekoAgentToolClass.class)) {
                for (var type : classes.getClassesWithAnnotation(annotation).keySet()) {
                    if (!realTypes.contains(type)) {
                        definition(context, type, true);
                    }
                }
            }
            for (var type : realTypes) {
                prepare(context, type);
            }
            var features = new ArrayList<IAgentFeature>();
            for (var method : factories) {
                if (IAgentFeature.class.isAssignableFrom(method.getReturnType())) {
                    var result = invoke(context, method);
                    if (result instanceof IAgentFeature feature) {
                        features.add(feature);
                    }
                }
            }
            features.addAll(context.getBeansOfType(IAgentFeature.class).values());
            var commands = new CommandScanner(logger, beans);
            commands.installPlugins(access);
            commands.init();
            var commandCatalog = new CommandCatalog(commands, mock(ICommandPermissionManager.class),
                    mock(ICommandScopeManager.class), access);
            var files = new FileSettingsDirectory(beans, fileClasses, parsers);
            files.init();
            var chats = new ChatSettingsDirectory(beans, mapper);
            chats.init();
            var fileService = mock(IFileConfigService.class);
            when(fileService.requireCurrent(WebSecurityFileConfig.class)).thenReturn(new WebSecurityFileConfig());
            var pages = new PluginConsoleDirectory(beans, plugins, fileService);
            pages.init();
            var components = new AgentComponentRegistry(beans, access);
            var tools = new TeaNekoAgentToolRegistry(context.getBeanFactory(), mapper, executor);
            features.addAll(components.contributions());
            var runtime = new AgentPluginRuntime(plugins, tools.combine(features), context.getBeanProvider(IEventService.class));
            var directory = new PluginContributionDirectory(List.of(
                    new HostPluginContributionSource(files, chats, pages, commandCatalog),
                    new AgentPluginContributionSource(components, tools, runtime, plugins),
                    new PluginTimerContributionSource(classes)), plugins);
            directory.init();
            var installed = plugins.list().stream().collect(Collectors.toMap(value -> value.descriptor().id(),
                    value -> directory.list(value.descriptor().id())));
            for (var plugin : plugins.list()) {
                if (!plugin.descriptor().mandatory()) {
                    assertFalse(installed.get(plugin.descriptor().id()).isEmpty(), plugin.descriptor().id());
                    plugins.setEnabled(plugin.descriptor().id(), false);
                }
            }
            assertEquals(installed, plugins.list().stream().collect(Collectors.toMap(value -> value.descriptor().id(),
                    value -> directory.list(value.descriptor().id()))));
            var kinds = installed.values().stream().flatMap(List::stream).map(PluginContribution::kind).collect(Collectors.toSet());
            assertTrue(kinds.containsAll(Set.of("component", "system", "tool", "feature", "command", "file-config",
                    "chat-config", "task", "page")), kinds.toString());
            assertTrue(components.describe().stream().noneMatch(item -> item.pluginId() != null && item.pluginId().startsWith("agent.")));
            assertTrue(tools.describe(plugins).stream().noneMatch(item -> item.pluginId() != null && item.pluginId().startsWith("agent.")));
            var store = mock(PluginStateStore.class);
            var statuses = mock(PluginRuntime.class);
            var clients = mock(PluginClientDirectory.class);
            var journal = mock(PluginOperationJournal.class);
            var desired = plugins.list().stream().collect(Collectors.toMap(value -> value.descriptor().id(),
                    value -> value.descriptor().mandatory()));
            when(store.snapshot()).thenReturn(new PluginStateStore.Snapshot("metadata", desired));
            when(statuses.reconcile(anyMap(), isNull(), eq(true))).thenReturn(CompletableFuture.completedStage(null));
            when(statuses.status(anyString())).thenReturn(new PluginRuntime.Status("DISABLED", "静态目录验证"));
            when(journal.pending()).thenReturn(List.of());
            var management = new PluginManagementService(plugins, statuses, store, clients, journal,
                    context.getBeanProvider(IPluginLifecycle.class));
            management.installContributions(directory);
            management.initialize().toCompletableFuture().get(2, TimeUnit.SECONDS);
            try (var control = ExecutionControl.create()) {
                var catalog = management.catalog(new ManagementContext("目录验证", "global", UUID.randomUUID(), control));
                assertEquals(installed, catalog.items().stream().collect(Collectors.toMap(PluginManagement.Item::id,
                        PluginManagement.Item::contributions)));
            }
            System.out.println("生产插件静态目录：插件=" + installed.size() + "，贡献="
                    + installed.values().stream().mapToInt(List::size).sum() + "，类别=" + kinds);
        }
    }

    /**
     * 真实元数据实现由 Spring 创建，业务命令和工具仅替换实例行为，保留原生产类型声明。
     * @param context 测试容器
     * @param type 生产类型
     * @param mocked 是否隔离业务实现
     */
    private static void definition(AnnotationConfigApplicationContext context, Class<?> type, boolean mocked) {
        if (context.containsBeanDefinition(type.getName())) {
            return;
        }
        var definition = new RootBeanDefinition(type);
        definition.setLazyInit(true);
        if (mocked) {
            definition.setSynthetic(true);
        }
        context.registerBeanDefinition(type.getName(), definition);
        if (mocked) {
            // 2026/11/07：直接登记替身实例，保留BeanDefinition类型但不运行生产注入和初始化回调。
            context.getBeanFactory().registerSingleton(type.getName(), mock(type));
        }
    }

    /**
     * 初始化静态实现需要的依赖；这些替身不会启动数据库、模型、网络或调度器。
     * @param context 测试容器
     * @param type 静态实现类型
     */
    private static void prepare(AnnotationConfigApplicationContext context, Class<?> type) {
        Arrays.stream(type.getConstructors()).flatMap(constructor -> Arrays.stream(constructor.getParameterTypes()))
                .forEach(dependency -> dependency(context, dependency));
        for (var method : type.getMethods()) {
            if (method.isAnnotationPresent(Autowired.class)) {
                Arrays.stream(method.getParameterTypes()).forEach(dependency -> dependency(context, dependency));
            }
        }
        ReflectionUtils.doWithFields(type, field -> {
            if (field.isAnnotationPresent(Autowired.class)) {
                dependency(context, field.getType());
            }
        });
    }

    /**
     * @param context 测试容器
     * @param type 依赖类型
     */
    private static void dependency(AnnotationConfigApplicationContext context, Class<?> type) {
        if (type == ObjectProvider.class || type == List.class || type == Set.class || type == Map.class
                || context.getBeanNamesForType(type).length > 0) {
            return;
        }
        definition(context, type, true);
    }

    /**
     * @param context 测试容器
     * @param type 真实生产实现
     * @param <T> 元数据接口
     * @return Spring 拥有的真实实现
     */
    private static <T> T real(AnnotationConfigApplicationContext context, Class<T> type) {
        definition(context, type, false);
        prepare(context, type);
        return context.getBean(type);
    }

    /**
     * 只调用实际配置类中返回模块或能力的工厂，其他业务 Bean 不创建。
     * @param context 测试容器
     * @param method 生产配置工厂
     * @return 实际生产元数据
     */
    private static Object invoke(AnnotationConfigApplicationContext context, Method method) {
        var owner = real(context, method.getDeclaringClass());
        var arguments = new Object[method.getParameterCount()];
        for (int index = 0; index < arguments.length; index++) {
            var type = method.getParameterTypes()[index];
            dependency(context, type);
            arguments[index] = type == ObjectProvider.class
                    ? context.getBeanProvider(ResolvableType.forMethodParameter(method, index).getGeneric(0))
                    : context.getBean(type);
        }
        return java.util.Objects.requireNonNull(ReflectionUtils.invokeMethod(method, owner, arguments));
    }

    /**
     * 复用真实类扫描，只剔除同包测试夹具，避免把测试插件当作生产安装项。
     * @param delegate 真实扫描器
     * @author zExNocs
     * @date 2026/11/07
     * @since 4.9.0-l
     * @version 4.9.0-l
     */
    private record ProductionClasses(IClassScanner delegate) implements IClassScanner {
        private static final URL LOCATION = PluginRegistry.class.getProtectionDomain().getCodeSource().getLocation();

        /**
         * @param type 待核对类型
         * @return 是否来自实际生产编译目录
         */
        private boolean production(Class<?> type) {
            return LOCATION.equals(type.getProtectionDomain().getCodeSource().getLocation());
        }

        /**
         * @param contract 发现接口
         * @param <T> 接口类型
         * @return 生产实现
         */
        @Override
        public <T> Set<Class<? extends T>> getClassesWithInterface(Class<T> contract) {
            return delegate.getClassesWithInterface(contract).stream().filter(this::production).collect(Collectors.toSet());
        }

        /**
         * @param annotation 注解类型
         * @param <A> 注解合同
         * @return 生产声明
         */
        @Override
        public <A extends Annotation> Map<Class<?>, A> getClassesWithAnnotation(Class<A> annotation) {
            return delegate.getClassesWithAnnotation(annotation).entrySet().stream().filter(entry -> production(entry.getKey()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        }

        /**
         * @param annotation 注解类型
         * @param contract 接口类型
         * @param <A> 注解合同
         * @param <T> 接口合同
         * @return 生产声明
         */
        @Override
        public <A extends Annotation, T> Map<Class<? extends T>, A> getClassesWithAnnotationAndInterface(
                Class<A> annotation, Class<T> contract) {
            return delegate.getClassesWithAnnotationAndInterface(annotation, contract).entrySet().stream()
                    .filter(entry -> production(entry.getKey())).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        }
    }
}
