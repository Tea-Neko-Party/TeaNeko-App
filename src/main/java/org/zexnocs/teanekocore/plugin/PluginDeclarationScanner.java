package org.zexnocs.teanekocore.plugin;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.zexnocs.teanekocore.reload.api.IScanner;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * 从条件装配后的 Bean 类型一次编译插件声明，不提前取得插件业务实例。
 * <br>2026/11/07：校验业务包边界与重复 ID，查询只读取完整不可变快照。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
@Component
public final class PluginDeclarationScanner implements IScanner {
    private final ConfigurableListableBeanFactory beans;
    private volatile @Nullable List<Declaration> snapshot;

    /**
     * 在插件注册表创建前编译静态元数据，读取类型不实例化业务 Bean。
     * @param beans 已完成条件装配的 Bean 定义目录
     */
    public PluginDeclarationScanner(ConfigurableListableBeanFactory beans) {
        this.beans = beans;
        init();
    }

    /** 首次初始化成功后保持已有目录，不在业务查询中补扫描。 */
    @Override
    public synchronized void init() {
        if (snapshot == null) {
            reload();
        }
    }

    /**
     * 重新校验静态声明，失败不替换原结果；运行中不允许安装新的插件类型。
     * @throws IllegalArgumentException 当归属、重复标识或声明无效时
     * @throws IllegalStateException 当重扫发现安装目录发生改变时
     */
    @Override
    public synchronized void reload() {
        var found = new TreeMap<String, Declaration>();
        var names = new TreeSet<>(Arrays.asList(beans.getBeanDefinitionNames()));
        names.addAll(Arrays.asList(beans.getSingletonNames()));
        for (var name : names) {
            var type = beans.getType(name, false);
            if (type == null) {
                continue;
            }
            validateOwnership(type);
            var annotation = AnnotatedElementUtils.findMergedAnnotation(type, TeaNekoPlugin.class);
            if (annotation == null) {
                continue;
            }
            var declaration = declaration(type);
            if (found.putIfAbsent(declaration.descriptor().id(), declaration) != null) {
                throw new IllegalArgumentException("插件标识重复：" + declaration.descriptor().id());
            }
        }
        var next = List.copyOf(found.values());
        if (snapshot != null && !Objects.equals(snapshot, next)) {
            throw new IllegalStateException("已安装插件声明发生改变，请重启以完成装配。");
        }
        snapshot = next;
    }

    /**
     * 解析已经定位的声明类型，供隔离装配复用相同校验。
     * @param type 声明所在的实际类型
     * @return 不含可执行业务实例的静态声明
     * @throws IllegalArgumentException 当类型不属于插件包或没有声明时
     */
    public static Declaration declaration(Class<?> type) {
        requirePluginPackage(type);
        var annotation = AnnotatedElementUtils.findMergedAnnotation(type, TeaNekoPlugin.class);
        if (annotation == null) {
            throw new IllegalArgumentException("类型未声明业务插件：" + type.getName());
        }
        var owner = AnnotatedElementUtils.findMergedAnnotation(type, PluginOwned.class);
        if (owner != null && !owner.value().equals(annotation.id())) {
            throw new IllegalArgumentException("插件入口身份与归属不一致：" + type.getName());
        }
        var clients = new TreeSet<>(List.of(annotation.clients()));
        clients.forEach(PluginDescriptor::requireId);
        var descriptor = new PluginDescriptor(annotation.id(), annotation.version(), annotation.apiVersion(),
                Set.copyOf(List.of(annotation.dependencies())), false, annotation.name(), annotation.description(),
                annotation.category(), annotation.switchMode());
        return new Declaration(descriptor, clients);
    }

    /**
     * 在发现阶段校验类型和显式方法归属，宿主能力不能借插件标记进入业务开关。
     * @param type 当前已装配 Bean 的实际类型
     * @throws IllegalArgumentException 当非业务插件类型声明插件归属时
     */
    public static void validateOwnership(Class<?> type) {
        if (AnnotatedElementUtils.hasAnnotation(type, TeaNekoPlugin.class)
                || AnnotatedElementUtils.hasAnnotation(type, PluginOwned.class)) {
            requirePluginPackage(type);
        }
        for (var method : type.getDeclaredMethods()) {
            if (AnnotatedElementUtils.hasAnnotation(method, PluginOwned.class)) {
                requirePluginPackage(type);
            }
        }
    }

    /**
     * 校验项目规定的业务插件命名空间，不通过类型名称访问上层业务实现。
     * @param type 插件声明或贡献类型
     * @throws IllegalArgumentException 当类型位于业务插件包之外时
     */
    public static void requirePluginPackage(Class<?> type) {
        String name = type.getPackageName();
        if (!name.equals("org.zexnocs.teanekoplugin") && !name.startsWith("org.zexnocs.teanekoplugin.")) {
            throw new IllegalArgumentException("只有 teanekoplugin 包内的类型可以声明业务插件归属：" + type.getName());
        }
    }

    /**
     * @return 已完整发布的静态安装声明
     * @throws IllegalStateException 当初始化尚未成功时
     */
    public List<Declaration> declarations() {
        var current = snapshot;
        if (current == null) {
            throw new IllegalStateException("插件静态声明尚未初始化。");
        }
        return current;
    }

    /**
     * 插件安装声明与客户端先决条件，不包含业务对象。
     * <br>2026/11/07：作为注册表和上层客户端依赖目录共享的静态快照。
     *
     * @param descriptor 插件身份与依赖
     * @param requiredClients 必需客户端 ID
     * @author zExNocs
     * @date 2026/11/07
     * @since 4.9.0-l
     * @version 4.9.0-l
     */
    public record Declaration(PluginDescriptor descriptor, Set<String> requiredClients) {
        /**
         * 固定客户端需求集合。
         * @param descriptor 插件元数据
         * @param requiredClients 客户端需求
         */
        public Declaration {
            requiredClients = Set.copyOf(requiredClients);
        }
    }
}
