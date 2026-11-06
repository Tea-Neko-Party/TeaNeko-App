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
 * <br>2026/11/04：建立共享给运行装配与管理表单的组件目录。
 * <br>2026/11/05：简化冗余类型实参、变量或转换，保持原业务与测试边界。
 * <br>2026/11/06：缓存组件静态声明及排序信息，显式重扫原子发布，运行资格仍实时检查。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.8.0-l
 * @version 4.8.4-l
 */
@Service
public final class AgentComponentRegistry implements org.zexnocs.teanekocore.reload.api.IScanner {
    private final IBeanScanner scanner;
    private final PluginAccess plugins;
    private volatile @org.jspecify.annotations.Nullable Snapshot registered;
    private static final tools.jackson.databind.ObjectMapper JSON = new tools.jackson.databind.ObjectMapper();

    /**
     * @param scanner 原 Bean 扫描器
     * @param plugins 原插件归属校验
     */
    public AgentComponentRegistry(IBeanScanner scanner, PluginAccess plugins) {
        this.scanner = scanner;
        this.plugins = plugins;
        init();
    }

    /**
     * @return 当前实际注册且启用的组件；冲突与失效依赖明确报错
     */
    public Map<String, AnnotatedBeanRegistry.Entry<IAgentComponent>> entries() {
        return snapshot().entries();
    }

    /** 2026/11/06：仅初始化或显式重扫发现组件，高频查询不执行发现。 */
    @Override
    public synchronized void init() {
        if (registered == null) {
            reload();
        }
    }

    /** 2026/11/06：完整构造后原子发布；重扫失败保留原完整目录。 */
    @Override
    public synchronized void reload() {
        var registry = new AnnotatedBeanRegistry<>(scanner, TeaNekoAgentComponent.class, IAgentComponent.class,
                declaration -> {
                    if (declaration.version() != 1 || !declaration.id().matches("[a-z][a-z0-9.-]{0,63}")) {
                        throw new IllegalArgumentException("Agent 组件标识或协议版本无效。");
                    }
                    return declaration.id();
                });
        var details = new java.util.LinkedHashMap<String, Metadata>();
        var owners = new java.util.IdentityHashMap<IAgentComponent, String>();
        for (var entry : registry.entries().entrySet()) {
            var bean = entry.getValue().bean();
            var type = entry.getValue().type();
            var declaration = type.getAnnotation(TeaNekoAgentComponent.class);
            var owner = org.springframework.core.annotation.AnnotatedElementUtils.findMergedAnnotation(
                    type, org.zexnocs.teanekocore.plugin.PluginOwned.class);
            if (owner == null) {
                throw new IllegalArgumentException("Agent 组件必须声明真实插件归属。");
            }
            var methodOwner = plugins.owner(bean, initializationMethod());
            owners.put(bean, methodOwner == null ? owner.value() : methodOwner);
            var systems = bean.systems().stream().map(system -> {
                var descriptor = system.system().descriptor();
                return new SystemDescription(descriptor.id(), system.phase().name(),
                        descriptor.required().stream().map(org.zexnocs.teanekocore.framework.composition.ComponentKey::id).sorted().toList(),
                        descriptor.writes().stream().map(org.zexnocs.teanekocore.framework.composition.ComponentKey::id).sorted().toList(),
                        descriptor.after().stream().sorted().toList(), descriptor.optional());
            }).toList();
            details.put(entry.getKey(), new Metadata(owner.value(), declaration.name(), declaration.description(),
                    declaration.version(), declaration.order(), List.of(declaration.requires()),
                    JSON.writeValueAsString(bean.defaults()), List.copyOf(bean.fields()), systems));
        }
        // 2026/11/06：静态声明与 Bean 同一快照原子发布，失败保留旧目录。
        registered = new Snapshot(registry.entries(), Map.copyOf(details),
                java.util.Collections.unmodifiableMap(owners));
    }

    /**
     * @param serialized 待保存的原始装配，不记入日志
     * @return 通过组件及依赖校验的无秘密运行快照
     * @throws IllegalArgumentException 当组件、参数或依赖不可用时
     */
    public AgentAssembly validate(String serialized) {
        return validate(serialized, snapshot());
    }

    /**
     * @param serialized 原装配
     * @param current 一次调用固定的目录快照
     * @return 原参数在同一组声明下的校验结果
     */
    private AgentAssembly validate(String serialized, Snapshot current) {
        var document = AgentAssembly.document(serialized);
        var installed = document.path("components");
        var entries = current.entries();
        for (var configured : installed.properties()) {
            var entry = entries.get(configured.getKey());
            if (entry == null) {
                throw new IllegalArgumentException("未注册的 Agent 组件：" + configured.getKey());
            }
            if (!plugins.enabledOwner(current.owners().get(entry.bean()))) {
                throw new IllegalStateException("Agent 组件所属插件未启用。");
            }
            checkDependencies(configured.getKey(), java.util.Set.copyOf(installed.propertyNames()), current, new HashSet<>());
            entry.bean().validate(configured.getValue());
        }
        var publicAssembly = AgentAssembly.parse(serialized);
        var safe = new java.util.LinkedHashMap<>(publicAssembly.parameters());
        for (var entry : installed.properties()) {
            var parameters = (tools.jackson.databind.node.ObjectNode) publicAssembly.settings(entry.getKey());
            current.metadata().get(entry.getKey()).fields().stream().filter(field -> field.kind().equals("SECRET"))
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
        var current = snapshot();
        return current.entries().entrySet().stream().<org.zexnocs.teanekoagent.plugin.AgentFeature>map(entry ->
                new AgentComponentFeature(current.metadata().get(entry.getKey()).owner(),
                        entry.getKey(), entry.getValue().bean())).toList();
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
        var owner = snapshot().owners().get(component);
        if (owner == null) {
            throw new IllegalArgumentException("组件未在当前目录登记。");
        }
        return plugins.enabledOwner(owner);
    }

    /**
     * @param id 当前组件
     * @param installed 显式已安装标识
     * @param current 一次调用固定的完整目录
     * @param visiting 当前依赖路径
     * @throws IllegalArgumentException 当依赖缺失或成环时
     */
    private void checkDependencies(String id, java.util.Set<String> installed,
            Snapshot current,
            java.util.Set<String> visiting) {
        if (!visiting.add(id)) {
            throw new IllegalArgumentException("Agent 组件依赖成环：" + id);
        }
        for (String dependency : current.metadata().get(id).requires()) {
            if (!installed.contains(dependency) || !current.entries().containsKey(dependency)) {
                throw new IllegalArgumentException("组件 " + id + " 需要同时安装 " + dependency + "。");
            }
            checkDependencies(dependency, installed, current, visiting);
        }
        visiting.remove(id);
    }

    /** @return 按标识稳定排序的公开组件及系统目录。 */
    public List<Description> describe() {
        var current = snapshot();
        return current.entries().entrySet().stream().filter(entry -> plugins.enabledOwner(current.owners().get(entry.getValue().bean())))
                .sorted(java.util.Comparator.<Map.Entry<String, AnnotatedBeanRegistry.Entry<IAgentComponent>>>comparingInt(
                        entry -> current.metadata().get(entry.getKey()).order()).thenComparing(Map.Entry::getKey))
                .map(entry -> {
                    var metadata = current.metadata().get(entry.getKey());
                    // 模板每次独立解码，调用方不能修改下一次目录返回的嵌套值。
                    Map<String, Object> defaults = JSON.readValue(metadata.defaults(),
                            new tools.jackson.core.type.TypeReference<>() {});
                    return new Description(entry.getKey(), metadata.name(), metadata.description(), metadata.version(),
                            metadata.requires(), defaults, metadata.fields(), metadata.systems());
                }).toList();
    }

    /**
     * 以旧字段还原同等能力，只产生编辑预览，不在读取时写回数据库。
     * @param configJson 原 cfg 行
     * @return 可编辑的脱敏装配，秘密由明确保留标记替代
     */
    public String editor(String configJson) {
        var current = snapshot();
        var config = JSON.readTree(configJson);
        var serialized = config.path("components");
        final tools.jackson.databind.node.ObjectNode document;
        if (serialized.isString()) {
            document = AgentAssembly.document(serialized.asString());
        } else {
            document = JSON.createObjectNode();
            document.put("version", 1);
            var installed = document.putObject("components");
            for (var entry : current.entries().entrySet()) {
                if (!(entry.getValue().bean() instanceof BuiltinAgentComponent) && !entry.getKey().equals("llm")) {
                    continue;
                }
                var parameters = (tools.jackson.databind.node.ObjectNode) JSON.readTree(current.metadata().get(entry.getKey()).defaults());
                for (var field : current.metadata().get(entry.getKey()).fields()) {
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
        visitSecrets(document, current, (parent, field) -> {
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
        var current = snapshot();
        var document = AgentAssembly.document(submitted);
        var old = previous == null ? null : AgentAssembly.document(previous);
        visitSecrets(document, current, (parent, field) -> {
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
        validate(document.toString(), current);
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
     * @param current 一次调用固定的完整目录
     * @param action 仅访问声明为秘密的字段位置
     */
    private void visitSecrets(tools.jackson.databind.node.ObjectNode document, Snapshot current,
            java.util.function.BiConsumer<tools.jackson.databind.node.ObjectNode, String> action) {
        for (var entry : document.path("components").properties()) {
            var declaration = current.entries().get(entry.getKey());
            if (declaration == null) {
                throw new IllegalArgumentException("装配引用了未注册组件。");
            }
            var parameters = (tools.jackson.databind.node.ObjectNode) entry.getValue();
            current.metadata().get(entry.getKey()).fields().stream().filter(field -> field.kind().equals("SECRET"))
                    .forEach(field -> action.accept(parameters, field.name()));
            if (entry.getKey().equals("llm") && parameters.path("configuration").isObject()) {
                action.accept((tools.jackson.databind.node.ObjectNode) parameters.path("configuration"), "api-key");
            }
        }
    }

    /** @return 原子发布的完整目录，读取不执行发现。 */
    private Snapshot snapshot() {
        return java.util.Objects.requireNonNull(registered, "组件目录尚未初始化，请显式重扫。");
    }

    /** @return 初始化期间取得插件方法归属，不在准入阶段反射发现。 */
    private static java.lang.reflect.Method initializationMethod() {
        try {
            return IAgentComponent.class.getMethod("initialize", AgentAssembly.class);
        } catch (NoSuchMethodException failure) {
            throw new IllegalStateException("Agent 组件契约缺少初始化入口。", failure);
        }
    }

    /**
     * 不可变静态声明，模板以序列化形式隔离可变值。
     * @param owner 组件插件归属
     * @param name 展示名
     * @param description 描述
     * @param version 协议版本
     * @param order 展示顺序
     * @param requires 依赖
     * @param defaults 默认模板
     * @param fields 字段
     * @param systems 系统
     * @author zExNocs
     * @date 2026/11/06
     * @since 4.8.4-l
     * @version 4.8.4-l
     */
    private record Metadata(String owner, String name, String description, int version, int order,
                            List<String> requires, String defaults, List<IAgentComponent.Field> fields,
                            List<SystemDescription> systems) {}

    /**
     * 一次完整扫描的发布单位。
     * @param entries 原容器实现
     * @param metadata 静态描述
     * @param owners 初始化入口插件归属，按 Bean 身份匹配
     * @author zExNocs
     * @date 2026/11/06
     * @since 4.8.4-l
     * @version 4.8.4-l
     */
    private record Snapshot(Map<String, AnnotatedBeanRegistry.Entry<IAgentComponent>> entries,
                            Map<String, Metadata> metadata, Map<IAgentComponent, String> owners) {}

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
