package org.zexnocs.teanekoagent.component;

import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.framework.extension.AnnotatedBeanRegistry;
import org.zexnocs.teanekocore.plugin.PluginAccess;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IBeanScanner;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * 使用原扫描器发现组件并校验参数、依赖和数据写入归属，不按注册顺序覆盖冲突。
 *
 * 修改记录：<br>
 * 2026/11/04：建立共享给运行装配与管理表单的组件目录。
 *
 * <br>2026/11/05：简化冗余类型实参、变量或转换，保持原业务与测试边界。
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.8.0-l
 * @version 4.8.0-l
 */
@Service
public final class AgentComponentRegistry {
    private final IBeanScanner scanner;
    private final PluginAccess plugins;
    private @org.jspecify.annotations.Nullable Map<String, AnnotatedBeanRegistry.Entry<IAgentComponent>> registered;
    private static final tools.jackson.databind.ObjectMapper JSON = new tools.jackson.databind.ObjectMapper();

    /**
     * @param scanner 原 Bean 扫描器
     * @param plugins 原插件归属校验
     */
    public AgentComponentRegistry(IBeanScanner scanner, PluginAccess plugins) {
        this.scanner = scanner;
        this.plugins = plugins;
    }

    /**
     * @return 当前实际注册且启用的组件；冲突与失效依赖明确报错
     */
    public synchronized Map<String, AnnotatedBeanRegistry.Entry<IAgentComponent>> entries() {
        if (registered != null) {
            return registered;
        }
        var registry = new AnnotatedBeanRegistry<>(scanner, TeaNekoAgentComponent.class, IAgentComponent.class,
                declaration -> {
                    if (declaration.version() != 1 || !declaration.id().matches("[a-z][a-z0-9.-]{0,63}")) {
                        throw new IllegalArgumentException("Agent 组件标识或协议版本无效。");
                    }
                    return declaration.id();
                });
        registered = registry.entries();
        return registered;
    }

    /**
     * @param serialized 待保存的原始装配，不记入日志
     * @return 通过组件及依赖校验的无秘密运行快照
     * @throws IllegalArgumentException 当组件、参数或依赖不可用时
     */
    public AgentAssembly validate(String serialized) {
        var document = AgentAssembly.document(serialized);
        var installed = document.path("components");
        var entries = entries();
        for (var configured : installed.properties()) {
            var entry = entries.get(configured.getKey());
            if (entry == null) {
                throw new IllegalArgumentException("未注册的 Agent 组件：" + configured.getKey());
            }
            requireEnabled(entry.bean());
            checkDependencies(configured.getKey(), java.util.Set.copyOf(installed.propertyNames()), entries, new HashSet<>());
            entry.bean().validate(configured.getValue());
        }
        var publicAssembly = AgentAssembly.parse(serialized);
        var safe = new java.util.LinkedHashMap<>(publicAssembly.parameters());
        for (var entry : installed.properties()) {
            var parameters = (tools.jackson.databind.node.ObjectNode) publicAssembly.settings(entry.getKey());
            entries.get(entry.getKey()).bean().fields().stream().filter(field -> field.kind().equals("SECRET"))
                    .forEach(field -> parameters.remove(field.name()));
            safe.put(entry.getKey(), parameters.toString());
        }
        return new AgentAssembly(publicAssembly.version(), safe, publicAssembly.revision(), false);
    }

    /**
     * 2026/11/05：简化冗余类型实参、变量或转换，保持原业务与测试边界。
     * @return 组件到已有 ECS 执行器的适配声明，不创建任何业务实现。
     */
    public List<org.zexnocs.teanekoagent.plugin.AgentFeature> contributions() {
        return entries().entrySet().stream().<org.zexnocs.teanekoagent.plugin.AgentFeature>map(entry -> {
            var owned = org.springframework.core.annotation.AnnotatedElementUtils.findMergedAnnotation(
                    entry.getValue().type(), org.zexnocs.teanekocore.plugin.PluginOwned.class);
            if (owned == null) {
                throw new IllegalArgumentException("Agent 组件必须声明真实插件归属。");
            }
            return new AgentComponentFeature(owned.value(), entry.getKey(), entry.getValue().bean());
        }).toList();
    }

    /**
     * @param component 注册的组件 Bean
     * @throws IllegalStateException 当所属插件已停用时
     */
    public void requireEnabled(IAgentComponent component) {
        if (!enabled(component)) {
            throw new IllegalStateException("Agent 组件所属插件未启用。");
        }
    }

    /**
     * @param component 原注册组件
     * @return 所属插件当前是否启用
     */
    private boolean enabled(IAgentComponent component) {
        try {
            return plugins.enabled(component, IAgentComponent.class.getMethod("initialize", AgentAssembly.class));
        } catch (NoSuchMethodException failure) {
            throw new IllegalStateException("Agent 组件契约缺少初始化入口。", failure);
        }
    }

    /**
     * @param id 当前组件
     * @param installed 显式已安装标识
     * @param entries 完整目录
     * @param visiting 当前依赖路径
     * @throws IllegalArgumentException 当依赖缺失或成环时
     */
    private void checkDependencies(String id, java.util.Set<String> installed,
            Map<String, AnnotatedBeanRegistry.Entry<IAgentComponent>> entries,
            java.util.Set<String> visiting) {
        if (!visiting.add(id)) {
            throw new IllegalArgumentException("Agent 组件依赖成环：" + id);
        }
        var entry = entries.get(id);
        for (String dependency : entry.type().getAnnotation(TeaNekoAgentComponent.class).requires()) {
            if (!installed.contains(dependency) || !entries.containsKey(dependency)) {
                throw new IllegalArgumentException("组件 " + id + " 需要同时安装 " + dependency + "。");
            }
            checkDependencies(dependency, installed, entries, visiting);
        }
        visiting.remove(id);
    }

    /** @return 按标识稳定排序的公开组件及系统目录。 */
    public List<Description> describe() {
        return entries().entrySet().stream().filter(entry -> enabled(entry.getValue().bean()))
                .sorted(Map.Entry.comparingByKey()).map(entry -> {
            var bean = entry.getValue().bean();
            requireEnabled(bean);
            var declaration = entry.getValue().type().getAnnotation(TeaNekoAgentComponent.class);
            return new Description(entry.getKey(), declaration.name(), declaration.description(), declaration.version(),
                    List.of(declaration.requires()), bean.defaults(), bean.fields(),
                    bean.systems().stream().map(system -> {
                        var descriptor = system.system().descriptor();
                        return new SystemDescription(descriptor.id(), system.phase().name(),
                                descriptor.required().stream().map(org.zexnocs.teanekocore.framework.composition.ComponentKey::id).sorted().toList(),
                                descriptor.writes().stream().map(org.zexnocs.teanekocore.framework.composition.ComponentKey::id).sorted().toList(),
                                descriptor.after().stream().sorted().toList(), descriptor.optional());
                    }).toList());
        }).toList();
    }

    /**
     * 以旧字段还原同等能力，只产生编辑预览，不在读取时写回数据库。
     * @param configJson 原 cfg 行
     * @return 可编辑的脱敏装配，秘密由明确保留标记替代
     */
    public String editor(String configJson) {
        var config = JSON.readTree(configJson);
        var serialized = config.path("components");
        final tools.jackson.databind.node.ObjectNode document;
        if (serialized.isString()) {
            document = AgentAssembly.document(serialized.asString());
        } else {
            document = JSON.createObjectNode();
            document.put("version", 1);
            var installed = document.putObject("components");
            for (var entry : entries().entrySet()) {
                if (!(entry.getValue().bean() instanceof BuiltinAgentComponent) && !entry.getKey().equals("llm")) {
                    continue;
                }
                var parameters = (tools.jackson.databind.node.ObjectNode) JSON.valueToTree(entry.getValue().bean().defaults());
                for (var field : entry.getValue().bean().fields()) {
                    if (config.has(field.name())) {
                        parameters.set(field.name(), config.get(field.name()));
                    }
                }
                installed.set(entry.getKey(), parameters);
            }
            ((tools.jackson.databind.node.ObjectNode) installed.path("llm"))
                    .put("route", config.path("modelRoute").asString(""));
            // 2026/11/04：旧 Skill 默认关闭，不能因展示新模板而改变既有行为。
            if (installed.path("skill").isObject()) {
                ((tools.jackson.databind.node.ObjectNode) installed.path("skill"))
                        .put("skillsEnabled", config.path("skillsEnabled").asBoolean(false));
            }
        }
        visitSecrets(document, (parent, field) -> {
            if (parent.has(field)) {
                parent.putObject(field).put("$keep", true);
            }
        });
        return document.toString();
    }

    /**
     * @param submitted 浏览器明确提交的装配
     * @param previous 当前行完整装配，首次保存时为空
     * @return 只保留原位置秘密的新装配，普通字符串永远视为明确替换
     */
    public String mergeSecrets(String submitted, @org.jspecify.annotations.Nullable String previous) {
        var document = AgentAssembly.document(submitted);
        var old = previous == null ? null : AgentAssembly.document(previous);
        visitSecrets(document, (parent, field) -> {
            var value = parent.get(field);
            if (value == null || !value.isObject()) {
                return;
            }
            if (value.size() != 1 || !value.path("$keep").isBoolean() || !value.path("$keep").asBoolean()) {
                throw new IllegalArgumentException("凭据保留标记无效。");
            }
            // 2026/11/04：通过原节点路径查找，不允许把另一个组件的秘密复制过来。
            var pointer = java.util.Objects.requireNonNull(findPath(document, parent, ""), "秘密节点必须属于本次装配。");
            var original = old == null ? null : old.at(pointer).get(field);
            if (original == null || !original.isString()) {
                throw new IllegalArgumentException("没有可保留的原凭据，请明确填写。");
            }
            parent.set(field, original);
        });
        validate(document.toString());
        return document.toString();
    }

    /**
     * @param root 装配根
     * @param target 独立树中的节点
     * @param path 已经过的路径
     * @return 本节点 JSON Pointer
     */
    private static @org.jspecify.annotations.Nullable String findPath(
            tools.jackson.databind.JsonNode root, tools.jackson.databind.JsonNode target, String path) {
        if (root == target) {
            return path;
        }
        for (var entry : root.properties()) {
            if (entry.getValue().isObject()) {
                String found = findPath(entry.getValue(), target, path + "/" + entry.getKey().replace("~", "~0").replace("/", "~1"));
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /**
     * @param document 原独立装配树
     * @param action 仅访问声明为秘密的字段位置
     */
    private void visitSecrets(tools.jackson.databind.node.ObjectNode document,
            java.util.function.BiConsumer<tools.jackson.databind.node.ObjectNode, String> action) {
        for (var entry : document.path("components").properties()) {
            var declaration = entries().get(entry.getKey());
            if (declaration == null) {
                throw new IllegalArgumentException("装配引用了未注册组件。");
            }
            var parameters = (tools.jackson.databind.node.ObjectNode) entry.getValue();
            declaration.bean().fields().stream().filter(field -> field.kind().equals("SECRET"))
                    .forEach(field -> action.accept(parameters, field.name()));
            if (entry.getKey().equals("llm") && parameters.path("configuration").isObject()) {
                action.accept((tools.jackson.databind.node.ObjectNode) parameters.path("configuration"), "api-key");
            }
        }
    }

    /**
     * 组件的公开目录项，不携带运行状态或用户参数。
     * @param id 标识
     * @param name 中文名称
     * @param description 职责
     * @param version 参数版本
     * @param requires 必需组件
     * @param defaults 默认公开参数
     * @param fields 表单字段
     * @param systems 已声明系统
     * @author zExNocs
     * @date 2026/11/04
     * @since 4.8.0-l
     * @version 4.8.0-l
     */
    public record Description(String id, String name, String description, int version, List<String> requires,
                              Map<String, Object> defaults, List<IAgentComponent.Field> fields, List<SystemDescription> systems) {}

    /**
     * 系统执行阶段与数据依赖的只读说明，不携带可执行对象。
     *
     * @param id 稳定系统标识
     * @param phase 宿主执行阶段
     * @param required 必需输入键
     * @param writes 允许写入键
     * @param after 同阶段前置系统
     * @param optional 缺少输入时是否允许跳过
     * @author zExNocs
     * @date 2026/11/04
     * @since 4.8.0-l
     * @version 4.8.0-l
     */
    public record SystemDescription(String id, String phase, List<String> required, List<String> writes,
                                    List<String> after, boolean optional) {}
}
