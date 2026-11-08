package org.zexnocs.teanekoplugin.contract.core;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.MapPropertySource;
import org.zexnocs.teanekocore.plugin.HostCapability;
import org.zexnocs.teanekocore.plugin.PluginAccess;
import org.zexnocs.teanekocore.plugin.PluginConfiguration;
import org.zexnocs.teanekocore.plugin.PluginDeclarationScanner;
import org.zexnocs.teanekocore.plugin.PluginDescriptor;
import org.zexnocs.teanekocore.plugin.PluginRegistry;
import org.zexnocs.teanekocore.plugin.TeaNekoPlugin;
import org.zexnocs.teanekocore.plugin.InvalidPluginFixtures;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证声明发现、条件装配、包边界与宿主身份分离的真实合同。
 * <br>2026/11/07：元数据解析不创建业务实例，插件身份不占用 Spring Bean 名。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
class PluginDeclarationContractTest {
    /** 两个插件使用默认 Bean 命名，读取静态声明无需实例化延迟业务 Bean。 */
    @Test
    void discoversMetadataWithoutInstantiatingBusinessBeans() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(PluginDeclarationScanner.class, PluginRegistry.class, PluginConfiguration.class,
                    FirstPlugin.class, SecondPlugin.class);
            context.refresh();
            var registry = context.getBean(PluginRegistry.class);
            assertEquals(Set.of("first.plugin", "second.plugin"), registry.list().stream()
                    .map(view -> view.descriptor().id()).collect(java.util.stream.Collectors.toSet()));
            assertFalse(context.containsBean("first.plugin"));
            assertFalse(context.containsBean("second.plugin"));
            assertEquals(Set.of("onebot"), registry.requiredClients("first.plugin"));
            assertFalse(registry.isEnabled("first.plugin"));
            assertTrue(registry.isEnabled("teaneko.core"));
            assertFalse(context.getBeanFactory().containsSingleton(
                    context.getBeanNamesForType(FirstPlugin.class)[0]));
        }
    }

    /** 条件不满足的入口不会进入安装目录，启用条件后才出现。 */
    @Test
    void respectsConditionalRegistration() {
        for (boolean enabled : List.of(false, true)) {
            try (var context = new AnnotationConfigApplicationContext()) {
                context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("测试条件",
                        Map.of("test.plugin.enabled", Boolean.toString(enabled))));
                context.register(PluginDeclarationScanner.class, ConditionalPlugin.class);
                context.refresh();
                assertEquals(enabled ? 1 : 0, context.getBean(PluginDeclarationScanner.class).declarations().size());
            }
        }
    }

    /** 同一业务 ID 的不同入口必须明确失败，不能按发现顺序覆盖。 */
    @Test
    void rejectsDuplicatePluginIdentity() {
        var beans = new DefaultListableBeanFactory();
        beans.registerBeanDefinition("first", new RootBeanDefinition(FirstPlugin.class));
        beans.registerBeanDefinition("duplicate", new RootBeanDefinition(DuplicatePlugin.class));
        assertThrows(IllegalArgumentException.class, () -> new PluginDeclarationScanner(beans));
    }

    /**
     * Core 包的插件声明和归属注解均被拒绝，运行登记遵循同一边界。
     * @throws Exception 当测试方法定位失败时
     */
    @Test
    void rejectsDeclarationsAndOwnershipOutsidePluginPackage() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> PluginDeclarationScanner.declaration(InvalidPluginFixtures.Declaration.class));
        assertThrows(IllegalArgumentException.class,
                () -> PluginDeclarationScanner.validateOwnership(InvalidPluginFixtures.Owner.class));
        var access = new PluginAccess(new PluginRegistry(List.of(
                () -> new PluginDescriptor("invalid.owner", "1", 1, Set.of()))));
        var owner = new InvalidPluginFixtures.Owner();
        var method = owner.getClass().getMethod("run");
        assertThrows(IllegalArgumentException.class, () -> access.register(owner, method));
    }

    /** 宿主支撑依赖和管理资源代次，但不进入插件目录、不接受启停操作。 */
    @Test
    void hostCapabilitiesRemainStableAndOutsidePluginManagement() {
        var core = new HostCapability("teaneko.core", "4.9.0-l", Set.of());
        var agent = new HostCapability("teaneko.agent", "4.9.0-l", Set.of("teaneko.core"));
        var registry = new PluginRegistry(List.of(
                () -> new PluginDescriptor("business", "1", 1, Set.of("teaneko.agent"))), List.of(core, agent));
        assertEquals(List.of("business"), registry.activationOrder());
        assertEquals(1, registry.list().size());
        assertEquals(List.of(), registry.blockers("business"));
        var hostToken = registry.capture("teaneko.agent");
        registry.setEnabled("business", false);
        assertDoesNotThrow(() -> registry.check(hostToken));
        assertTrue(registry.isEnabled("teaneko.agent"));
        assertThrows(IllegalArgumentException.class, () -> registry.setEnabled("teaneko.agent", false));
        assertThrows(IllegalArgumentException.class, () -> registry.descriptor("teaneko.agent"));
        assertThrows(IllegalArgumentException.class, () -> new PluginRegistry(List.of(), List.of(agent)));
        assertThrows(IllegalArgumentException.class, () -> new PluginRegistry(List.of(), List.of(
                new HostCapability("a", "1", Set.of("b")), new HostCapability("b", "1", Set.of("a")))));
    }

    /**
     * 仅提供声明的延迟业务入口。
     * @author zExNocs
     * @date 2026/11/07
     * @since 4.9.0-l
     * @version 4.9.0-l
     */
    @Lazy
    @TeaNekoPlugin(id = "first.plugin", name = "第一插件", description = "无实例发现。", version = "1",
            dependencies = "teaneko.core", clients = "onebot")
    public static class FirstPlugin {
        /** 任何实例化都说明发现越过静态元数据边界。 */
        public FirstPlugin() {
            throw new AssertionError("发现插件声明不能实例化业务对象。");
        }
    }

    /**
     * 用第二个入口验证默认 Bean 命名互不冲突。
     * @author zExNocs
     * @date 2026/11/07
     * @since 4.9.0-l
     * @version 4.9.0-l
     */
    @TeaNekoPlugin(id = "second.plugin", name = "第二插件", description = "独立身份。", version = "1")
    public static class SecondPlugin {
    }

    /**
     * 与第一入口冲突的静态声明。
     * @author zExNocs
     * @date 2026/11/07
     * @since 4.9.0-l
     * @version 4.9.0-l
     */
    @TeaNekoPlugin(id = "first.plugin", name = "冲突插件", description = "重复身份。", version = "1")
    public static class DuplicatePlugin {
    }

    /**
     * 默认未装配的条件声明。
     * @author zExNocs
     * @date 2026/11/07
     * @since 4.9.0-l
     * @version 4.9.0-l
     */
    @ConditionalOnProperty(name = "test.plugin.enabled", havingValue = "true")
    @TeaNekoPlugin(id = "conditional.plugin", name = "条件插件", description = "显式安装。", version = "1")
    public static class ConditionalPlugin {
    }
}
