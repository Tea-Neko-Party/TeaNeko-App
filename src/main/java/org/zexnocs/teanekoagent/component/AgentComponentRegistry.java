package org.zexnocs.teanekoagent.component;

// 2026/11/07：接口统一使用 I 前缀，调用合同与持久数据保持不变。

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.framework.extension.AnnotatedBeanRegistry;
import org.zexnocs.teanekocore.plugin.PluginAccess;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IBeanScanner;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * 使用原扫描器发现组件并校验参数、依赖和数据写入归属，不按注册顺序覆盖冲突。
 * <br>2026/11/04：建立共享给运行装配与管理表单的组件目录。
 * <br>2026/11/05：简化冗余类型实参、变量或转换，保持原业务与测试边界。
 * <br>2026/11/06：缓存组件静态声明及排序信息，显式重扫原子发布，运行资格仍实时检查。
 * <br>2026/11/07：接入插件归属与独立贡献，保留原权限及执行契约。
 * <br>2026/11/07：原生组件不声明插件归属，运行桥接仍复用独立宿主能力身份。
 * <br>2026/11/10：公开可选择模型的字段契约及安全配置投影。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.8.0-l
 * @version 4.9.1-l
 */
@Service
public final class AgentComponentRegistry implements org.zexnocs.teanekocore.reload.api.IScanner {
    private final IBeanScanner scanner;
    private final PluginAccess plugins;
    private List<IAgentAssemblyMigration> migrations = List.of();
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
     * 2026/11/07：固定完整迁移目录，迁移只转换独立树，不在查询时发现扩展。
     * @param values 插件声明的兼容转换
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void installMigrations(List<IAgentAssemblyMigration> values) {
        var ids = new HashSet<String>();
        for (var value : values) {
            if (!ids.add(value.id())) {
                throw new IllegalArgumentException("Agent 装配迁移标识重复。");
            }
        }
        migrations = values.stream().sorted(java.util.Comparator.comparing(IAgentAssemblyMigration::id)).toList();
    }

    /**
     * 2026/11/07：幂等规范化旧装配；冲突时保留持久原文并报告错误。
     * @param serialized 受保护的原始装配
     * @return 独立的新版本装配树
     */
    private tools.jackson.databind.node.ObjectNode document(String serialized) {
        var result = AgentAssembly.document(serialized);
        migrations.forEach(migration -> migration.migrate(result));
        return result;
    }

    /**
     * 2026/11/07：启动迁移使用完整配置树，保留秘密及停用组件的全部参数。
     * @param serialized 原持久装配
     * @return 通过全部兼容转换和结构校验的新文档
     */
    public String normalize(String serialized) {
        var normalized = document(serialized);
        validate(normalized.toString(), snapshot());
        return normalized.toString();
    }

    /**
     * 2026/11/07：按完整安装目录转换旧配置行，保留停用组件选择及秘密，不使用页面可见性过滤。
     * @param configJson 受保护的完整配置行
     * @return 经兼容转换和结构校验的完整装配文本，不是配置行正文
     * @throws IllegalArgumentException 当旧行结构、参数或组件依赖无效时
     */
    public String normalizeConfiguration(String configJson) {
        var current = snapshot();
        var normalized = configurationDocument(configJson, current);
        validate(normalized.toString(), current);
        return normalized.toString();
    }

    /**
     * 2026/11/07：运行装配剔除停用插件及依赖不完整的组件，持久参数保持不变。
     * @param serialized 已保存装配
     * @return 当前可执行的无秘密装配
     */
    public AgentAssembly effective(String serialized) {
        var current = snapshot();
        var valid = validate(serialized, current);
        var active = new java.util.LinkedHashMap<>(valid.parameters());
        active.keySet().removeIf(id -> !ownerEnabled(current.metadata().get(id).owner()));
        boolean changed;
        do {
            changed = active.keySet().removeIf(id -> !active.keySet().containsAll(current.metadata().get(id).requires()));
        } while (changed);
        if (!active.containsKey("llm")) {
            throw new IllegalStateException("必要的模型组件不可用。");
        }
        return new AgentAssembly(valid.version(), active,
                org.zexnocs.teanekocore.utils.StableDigest.sha256(valid.revision(),
                        active.keySet().stream().sorted().toList().toString()), false);
    }

    /**
     * 2026/11/07：按已发布目录查询组件所属插件，供业务端口实时复核资格。
     * @param id 组件标识
     * @return 注册且插件启用时为真
     */
    public boolean componentEnabled(String id) {
        var metadata = snapshot().metadata().get(id);
        return metadata != null && ownerEnabled(metadata.owner());
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
        var owners = new java.util.IdentityHashMap<IAgentComponent, @Nullable String>();
        for (var entry : registry.entries().entrySet()) {
            var bean = entry.getValue().bean();
            var type = entry.getValue().type();
            var declaration = type.getAnnotation(TeaNekoAgentComponent.class);
            // 2026/11/07：初始化入口统一解析真实归属；无归属组件作为原生能力展示。
            var owner = plugins.register(bean, initializationMethod());
            owners.put(bean, owner);
            var systems = bean.systems().stream().map(system -> {
                var descriptor = system.system().descriptor();
                return new SystemDescription(descriptor.id(), system.phase().name(),
                        descriptor.required().stream().map(org.zexnocs.teanekocore.framework.composition.ComponentKey::id).sorted().toList(),
                        descriptor.writes().stream().map(org.zexnocs.teanekocore.framework.composition.ComponentKey::id).sorted().toList(),
                        descriptor.after().stream().sorted().toList(), descriptor.optional(), owner);
            }).toList();
            details.put(entry.getKey(), new Metadata(owner, declaration.name(), declaration.description(),
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
        var document = document(serialized);
        var installed = document.path("components");
        var entries = current.entries();
        for (var configured : installed.properties()) {
            var entry = entries.get(configured.getKey());
            if (entry == null) {
                throw new IllegalArgumentException("未注册的 Agent 组件：" + configured.getKey());
            }
            checkDependencies(configured.getKey(), java.util.Set.copyOf(installed.propertyNames()), current, new HashSet<>());
            entry.bean().validate(configured.getValue());
        }
        var publicAssembly = AgentAssembly.parse(document.toString());
        var safe = new java.util.LinkedHashMap<>(publicAssembly.parameters());
        for (var entry : installed.properties()) {
            var parameters = (tools.jackson.databind.node.ObjectNode) publicAssembly.settings(entry.getKey());
            current.metadata().get(entry.getKey()).fields().stream().filter(field -> field.kind().equals("SECRET"))
                    .forEach(field -> parameters.remove(field.name()));
            // 2026/11/10：模型配置及 metadata 整体留在受保护入口，不进入实体参数。
            current.metadata().get(entry.getKey()).fields().stream().filter(field -> field.kind().equals("MODEL"))
                    .map(field -> parameters.path(field.name()))
                    .filter(tools.jackson.databind.node.ObjectNode.class::isInstance)
                    .forEach(node -> ((tools.jackson.databind.node.ObjectNode) node).remove("configuration"));
            safe.put(entry.getKey(), parameters.toString());
        }
        return new AgentAssembly(publicAssembly.version(), safe, publicAssembly.revision(), false);
    }

    /**
     * 2026/11/05：简化冗余类型实参、变量或转换，保持原业务与测试边界。
     * @return 组件到已有 ECS 执行器的适配声明，不创建任何业务实现。
     */
    public List<org.zexnocs.teanekoagent.plugin.IAgentFeature> contributions() {
        var current = snapshot();
        return current.entries().entrySet().stream().<org.zexnocs.teanekoagent.plugin.IAgentFeature>map(entry ->
                // 2026/11/07：内部执行身份使用稳定宿主能力，不伪造公开插件归属。
                new AgentComponentFeature(java.util.Objects.requireNonNullElse(
                        current.metadata().get(entry.getKey()).owner(), "teaneko.agent"),
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
        var owners = snapshot().owners();
        if (!owners.containsKey(component)) {
            throw new IllegalArgumentException("组件未在当前目录登记。");
        }
        return ownerEnabled(owners.get(component));
    }

    /**
     * 2026/11/07：原生组件始终由宿主装配控制，业务组件仍实时复核插件资格。
     * @param owner 初始化阶段固定的可选业务插件身份
     * @return 原生能力或当前启用的插件能力
     */
    private boolean ownerEnabled(@Nullable String owner) {
        return owner == null || plugins.enabledOwner(owner);
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
        return descriptions(true);
    }

    /**
     * 2026/11/07：总管理只读取静态安装说明，停用组件仍保留完整贡献资料。
     * @return 全部已注册组件及系统，不授予装配或执行资格
     */
    public List<Description> installedDescription() {
        return descriptions(false);
    }

    /**
     * 从同一已发布快照投影说明，不发现类型或实例化运行服务。
     * @param activeOnly 是否仅展示当前有效插件
     * @return 独立的只读说明列表
     */
    private List<Description> descriptions(boolean activeOnly) {
        var current = snapshot();
        return current.entries().entrySet().stream()
                .filter(entry -> !activeOnly || ownerEnabled(current.owners().get(entry.getValue().bean())))
                .sorted(java.util.Comparator.<Map.Entry<String, AnnotatedBeanRegistry.Entry<IAgentComponent>>>comparingInt(
                        entry -> current.metadata().get(entry.getKey()).order()).thenComparing(Map.Entry::getKey))
                .map(entry -> {
                    var metadata = current.metadata().get(entry.getKey());
                    // 模板每次独立解码，调用方不能修改下一次目录返回的嵌套值。
                    Map<String, Object> defaults = JSON.readValue(metadata.defaults(),
                            new tools.jackson.core.type.TypeReference<>() {});
                    return new Description(entry.getKey(), metadata.owner(), metadata.name(), metadata.description(), metadata.version(),
                            metadata.requires(), defaults, metadata.fields(), metadata.systems());
                }).toList();
    }

    /**
     * 2026/11/07：复用完整旧配置转换，再仅为编辑预览脱敏及过滤停用组件。
     * @param configJson 原 cfg 行
     * @return 可编辑的脱敏装配，秘密由明确保留标记替代
     */
    public String editor(String configJson) {
        var current = snapshot();
        var document = configurationDocument(configJson, current);
        visitSecrets(document, current, (parent, field) -> {
            if (parent.has(field)) {
                parent.putObject(field).put("$keep", true);
            }
        });
        // 2026/11/07：页面仅呈现已启用贡献；停用参数保存在原持久文档。
        var displayed = (tools.jackson.databind.node.ObjectNode) document.path("components");
        java.util.List.copyOf(displayed.propertyNames()).stream().filter(id -> !componentEnabled(id))
                .forEach(displayed::remove);
        return document.toString();
    }

    /**
     * 2026/11/07：从独立配置树还原旧装配，兼容转换由已安装插件声明且不依赖当前启用状态。
     * @param configJson 完整旧配置行，原字段不会被改写
     * @param current 一次调用固定的完整声明快照
     * @return 含全部历史参数的独立装配树
     * @throws IllegalArgumentException 当配置行或显式装配格式无效时
     */
    private tools.jackson.databind.node.ObjectNode configurationDocument(String configJson, Snapshot current) {
        if (!(JSON.readTree(configJson) instanceof tools.jackson.databind.node.ObjectNode config)) {
            throw new IllegalArgumentException("Agent 配置必须为对象。");
        }
        var serialized = config.get("components");
        if (serialized != null && !serialized.isNull()) {
            if (!serialized.isString()) {
                throw new IllegalArgumentException("Agent 组件装配必须使用原字符串配置格式。");
            }
            return document(serialized.asString());
        }
        var result = JSON.createObjectNode();
        result.put("version", 1);
        var installed = result.putObject("components");
        for (var entry : current.entries().entrySet()) {
            // 2026/11/07：宿主仅恢复原生组件，业务默认选择由插件迁移器声明。
            if (!entry.getKey().equals("llm") && !entry.getKey().equals("tool")) {
                continue;
            }
            var metadata = current.metadata().get(entry.getKey());
            var parameters = (tools.jackson.databind.node.ObjectNode) JSON.readTree(metadata.defaults());
            for (var field : metadata.fields()) {
                if (config.has(field.name())) {
                    parameters.set(field.name(), config.get(field.name()).deepCopy());
                }
            }
            installed.set(entry.getKey(), parameters);
        }
        if (!(installed.get("llm") instanceof tools.jackson.databind.node.ObjectNode llm)) {
            throw new IllegalArgumentException("旧配置迁移缺少必要的模型组件。");
        }
        if (config.has("modelRoute")) {
            llm.set("route", config.get("modelRoute").deepCopy());
        }
        // 2026/11/07：迁移只借用独立元数据树，不依赖启用状态或取得业务对象。
        var templates = new java.util.LinkedHashMap<String, tools.jackson.databind.node.ObjectNode>();
        current.metadata().forEach((id, metadata) -> templates.put(id,
                (tools.jackson.databind.node.ObjectNode) JSON.readTree(metadata.defaults())));
        migrations.forEach(migration -> migration.migrateLegacy(config, result,
                java.util.Collections.unmodifiableMap(templates)));
        return document(result.toString());
    }

    /**
     * @param submitted 浏览器明确提交的装配
     * @param previous 当前行完整装配，首次保存时为空
     * @return 只保留原位置秘密的新装配，普通字符串永远视为明确替换
     */
    public String mergeSecrets(String submitted, @org.jspecify.annotations.Nullable String previous) {
        var current = snapshot();
        var document = document(submitted);
        var old = previous == null ? null : document(previous);
        var submittedComponents = (tools.jackson.databind.node.ObjectNode) document.path("components");
        // 2026/11/07：隐藏组件不能被新提交改写；保存其他字段时合回原参数及秘密。
        for (var id : java.util.List.copyOf(submittedComponents.propertyNames())) {
            if (!componentEnabled(id)) {
                var existing = old == null ? null : old.path("components").get(id);
                if (existing == null || !existing.equals(submittedComponents.get(id))) {
                    throw new IllegalArgumentException("停用插件的组件参数不能通过普通页面修改。");
                }
            }
        }
        if (old != null) {
            old.path("components").properties().stream().filter(entry -> !componentEnabled(entry.getKey()))
                    .forEach(entry -> submittedComponents.set(entry.getKey(), entry.getValue().deepCopy()));
        }
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
            // 2026/11/10：模型字段按原路径保留密钥，扩展组件无需增加宿主业务标识分支。
            current.metadata().get(entry.getKey()).fields().stream().filter(field -> field.kind().equals("MODEL"))
                    .map(field -> parameters.path(field.name()).path("configuration"))
                    .filter(tools.jackson.databind.node.ObjectNode.class::isInstance)
                    .forEach(node -> action.accept((tools.jackson.databind.node.ObjectNode) node, "api-key"));
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
     * @param owner 组件插件归属，原生组件为空
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
     * @version 4.9.0-l
     */
    private record Metadata(@Nullable String owner, String name, String description, int version, int order,
                            List<String> requires, String defaults, List<IAgentComponent.Field> fields,
                            List<SystemDescription> systems) {}

    /**
     * 一次完整扫描的发布单位。
     * @param entries 原容器实现
     * @param metadata 静态描述
     * @param owners 初始化入口插件归属，按 Bean 身份匹配，原生组件保留空值
     * @author zExNocs
     * @date 2026/11/06
     * @since 4.8.4-l
     * @version 4.9.0-l
     */
    private record Snapshot(Map<String, AnnotatedBeanRegistry.Entry<IAgentComponent>> entries,
                            Map<String, Metadata> metadata, Map<IAgentComponent, @Nullable String> owners) {}

    /**
     * 组件的公开目录项，不携带运行状态或用户参数。
     * <br>2026/11/07：公开真实插件归属，供目录显示与筛选。
     * @param id 标识
     * @param pluginId 所属插件标识，原生组件为空
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
     * @version 4.9.0-l
     */
    public record Description(String id, @Nullable String pluginId, String name, String description, int version, List<String> requires,
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
     * @param pluginId 系统贡献所属插件，原生系统为空
     * @author zExNocs
     * @date 2026/11/04
     * @since 4.8.0-l
     * @version 4.9.0-l
     */
    public record SystemDescription(String id, String phase, List<String> required, List<String> writes,
                                    List<String> after, boolean optional, @Nullable String pluginId) {}
}
