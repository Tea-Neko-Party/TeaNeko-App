package org.zexnocs.teanekoagent.plugin;

// 2026/11/07：接口统一使用 I 前缀，调用合同与持久数据保持不变。

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

// 2026/11/07：按插件归属迁移业务实现，保留底层契约、持久标识和事务边界。

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.component.AgentComponents;
import org.zexnocs.teanekoagent.component.AgentGuardedSystem;
import org.zexnocs.teanekoagent.component.AgentPluginSelection;
import org.zexnocs.teanekoagent.llm.framework.tool.LLMToolBinding;
import org.zexnocs.teanekoagent.plugin.event.AgentDecisionEvent;
import org.zexnocs.teanekoagent.plugin.event.AgentEventContext;
import org.zexnocs.teanekoagent.plugin.event.AgentPromptPreparingEvent;
import org.zexnocs.teanekoagent.plugin.event.AgentToolsSelectingEvent;
import org.zexnocs.teanekoagent.runtime.AgentDefinition;
import org.zexnocs.teanekoagent.runtime.AgentRunRequest;
import org.zexnocs.teanekoagent.runtime.IAgentToolSource;
import org.zexnocs.teanekoagent.runtime.access.AgentRuntimeSnapshot;
import org.zexnocs.teanekoagent.runtime.prompt.AgentPromptException;
import org.zexnocs.teanekoagent.runtime.prompt.AgentPromptFragment;
import org.zexnocs.teanekoagent.runtime.prompt.AgentPromptInput;
import org.zexnocs.teanekoagent.runtime.prompt.IAgentPromptSource;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.actuator.task.TaskStages;
import org.zexnocs.teanekocore.event.interfaces.IEventService;
import org.zexnocs.teanekocore.framework.composition.ComponentKey;
import org.zexnocs.teanekocore.framework.composition.ComponentPatch;
import org.zexnocs.teanekocore.framework.composition.Entity;
import org.zexnocs.teanekocore.framework.composition.EntitySnapshot;
import org.zexnocs.teanekocore.framework.composition.IEntitySystem;
import org.zexnocs.teanekocore.framework.composition.SystemContext;
import org.zexnocs.teanekocore.framework.composition.SystemPipeline;
import org.zexnocs.teanekocore.framework.composition.SystemResult;
import org.zexnocs.teanekocore.plugin.PluginRegistry;
import org.zexnocs.teanekocore.plugin.operation.OperationPipeline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

/**
 * Agent 能力目录与宿主组合器；固定版本、保护核心组件并复用原执行控制。
 * 不读取存储、不启动模型循环、不创建线程池，Spring 拥有来源和插件 Bean。
 * <br>2026/11/02：合并注解工具自动能力，原能力拥有的工具不重复装配。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：组件目录接入原 ECS 管线，新装配只选择明确能力。
 * <br>2026/11/04：更新分层重构后的类型引用，保持原调用与生命周期行为。
 * <br>2026/11/04：按能力自报的组件要求装配客户端扩展，不硬编码平台能力。
 * <br>2026/11/05：补充实例装配拒绝未注册组件的异常契约。
 * <br>2026/11/05：展开本次装配校验中的多语句和分支，保持执行顺序。
 * <br>2026/11/05：最终工具复核传递到实际选中的来源并保持插件代次检查。
 * <br>2026/11/07：接入插件归属与独立贡献，保留原权限及执行契约。
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.6.0-l
 * @version 4.9.0-l
 */
@Service
public final class AgentPluginRuntime {
    // 2026/11/04：保留原公开键入口；真实组件声明及系统实现统一移入 component。
    public static final ComponentKey<AgentPluginSelection> SELECTION = AgentComponents.PLUGIN_SELECTION;
    private final PluginRegistry plugins;
    private final Map<String, IAgentFeature> features;
    private final Set<ComponentKey<?>> components;
    private final Set<String> promptNames;
    private final List<InstalledSystem> installedSystems;
    private final ObjectProvider<IEventService> events;
    private Map<String, Set<String>> featureAliases = Map.of();
    private final Map<String, SystemPipeline> pipelines = new ConcurrentHashMap<>();

    /**
     * 2026/11/07：由业务插件提供旧能力归属迁移，重复声明明确拒绝。
     * @param migrations 已完成装配的迁移声明
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void installAliases(List<org.zexnocs.teanekoagent.component.IAgentAssemblyMigration> migrations) {
        var aliases = new TreeMap<String, Set<String>>();
        for (var migration : migrations) {
            migration.featureAliases().forEach((old, targets) -> {
                if (aliases.putIfAbsent(old, Set.copyOf(targets)) != null) {
                    throw new IllegalArgumentException("旧 Agent 能力归属迁移重复：" + old);
                }
                targets.forEach(target -> {
                    if (!features.containsKey(target)) {
                        throw new IllegalArgumentException("Agent 能力迁移目标未注册：" + target);
                    }
                });
            });
        }
        featureAliases = Map.copyOf(aliases);
    }

    /**
     * 2026/11/02：Spring 默认合并注解目录，保留原构造供显式独立宿主使用。
     * @param plugins 插件目录
     * @param contributions 原能力
     * @param events 懒事件端口
     * @param annotations 注解工具目录
     * @param componentRegistry 可序列化组件目录，与原能力使用同一执行器
     */
    @org.springframework.beans.factory.annotation.Autowired
    public AgentPluginRuntime(PluginRegistry plugins, List<IAgentFeature> contributions, ObjectProvider<IEventService> events,
            ObjectProvider<org.zexnocs.teanekoagent.component.tool.TeaNekoAgentToolRegistry> annotations,
            ObjectProvider<org.zexnocs.teanekoagent.component.AgentComponentRegistry> componentRegistry) {
        // 2026/11/04：组件系统与旧插件来源使用同一声明、版本及写入范围校验。
        this(plugins, combineComponents(annotations.getIfAvailable() == null ? contributions
                : annotations.getObject().combine(contributions), componentRegistry.getIfAvailable()), events);
    }

    /**
     * @param existing 原能力目录
     * @param registry 显式组件类型目录
     * @return 2026/11/04：统一执行目录，不改变原业务 Bean 的拥有关系
     */
    private static List<IAgentFeature> combineComponents(List<IAgentFeature> existing,
            org.zexnocs.teanekoagent.component.@org.jspecify.annotations.Nullable AgentComponentRegistry registry) {
        var values = new ArrayList<>(existing);
        if (registry != null) {
            values.addAll(registry.contributions());
        }
        return List.copyOf(values);
    }

    /**
     * 2026/11/05：逐项校验完整能力目录，拒绝重名及跨边界写入；不执行初始化回调。
     * @param plugins 插件目录
     * @param contributions 所有能力贡献
     * @param events 延迟事件入口，避免扫描构造环
     * @throws IllegalArgumentException 当能力、依赖或组件声明无效时
     */
    public AgentPluginRuntime(PluginRegistry plugins, List<IAgentFeature> contributions,
            ObjectProvider<IEventService> events) {
        this.plugins = plugins;
        this.events = events;
        var values = new TreeMap<String, IAgentFeature>();
        var keys = new HashSet<ComponentKey<?>>();
        var sourceNames = new HashSet<String>();
        var systemDescriptions = new ArrayList<InstalledSystem>();
        for (var feature : contributions) {
            plugins.requireRegistered(feature.pluginId());
            if (!feature.id().matches("[a-z][a-z0-9.-]{0,79}") || values.putIfAbsent(feature.key(), feature) != null) {
                throw new IllegalArgumentException("Agent 能力标识无效或重复：" + feature.key());
            }
            for (var key : feature.components()) {
                if (!key.id().startsWith(feature.componentPrefix())) {
                    throw new IllegalArgumentException("插件组件必须使用自有命名空间：" + key.id());
                }
                keys.add(key);
            }
            // 2026/11/04：组件要求使用稳定标识；不存在的装配不会自动启用该能力。
            if (feature.assemblyRequirements().stream().anyMatch(id -> !id.matches("[a-z][a-z0-9.-]{0,79}"))) {
                throw new IllegalArgumentException("Agent 能力声明了无效组件要求：" + feature.key());
            }
            for (var name : feature.promptNames()) {
                if (!name.matches("[a-z][a-z0-9_]{0,47}") || !sourceNames.add(name)) {
                    throw new IllegalArgumentException("插件资料来源名无效或重复：" + name);
                }
            }
            for (var contribution : feature.systems()) {
                var descriptor = contribution.system().descriptor();
                if (!descriptor.id().startsWith(feature.componentPrefix())
                        || !feature.components().containsAll(descriptor.writes())) {
                    throw new IllegalArgumentException("插件 System 越过自有写入边界：" + descriptor.id());
                }
                // 2026/11/07：安装说明在构造阶段固定，总管理不重新发现或取得可执行 System。
                systemDescriptions.add(new InstalledSystem(feature.pluginId(), descriptor.id(), contribution.phase().name()));
            }
            feature.promptInterceptors().forEach(binding -> {
                if (!binding.pluginId().equals(feature.pluginId())) {
                    throw new IllegalArgumentException("能力不能冒用其他插件的拦截器归属。");
                }
            });
        }
        features = Collections.unmodifiableMap(values);
        components = Set.copyOf(keys);
        promptNames = Set.copyOf(sourceNames);
        installedSystems = List.copyOf(systemDescriptions);
        for (var feature : features.values()) {
            validateDependencies(feature.key(), new HashSet<>());
        }
    }

    /**
     * 检查能力依赖目录，避免运行时递归循环。
     * @param key 当前能力
     * @param visiting 当前路径
     */
    private void validateDependencies(String key, Set<String> visiting) {
        var feature = features.get(key);
        if (feature == null || !visiting.add(key)) { throw new IllegalArgumentException("Agent 能力依赖缺失或成环：" + key); }
        feature.dependencies().forEach(dependency -> validateDependencies(dependency, visiting)); visiting.remove(key);
    }

    /** @return 所有允许写入的插件组件键，不包含核心组件 */
    public Set<ComponentKey<?>> componentKeys() { return components; }
    /** @return 稳定能力 ID 目录，供管理展示及实例选择 */
    public Set<String> featureIds() { return features.keySet(); }

    /**
     * 2026/11/07：静态贡献说明不授予系统调用或写入能力。
     * @return 全部已安装能力的系统说明，包含当前停用项
     */
    public List<InstalledSystem> installedSystems() {
        return installedSystems;
    }

    /**
     * 能力系统的不可执行说明。
     * @param pluginId 所属插件
     * @param id 系统标识
     * @param phase 执行阶段
     * @author zExNocs
     * @date 2026/11/07
     * @since 4.9.0-l
     * @version 4.9.0-l
     */
    public record InstalledSystem(String pluginId, String id, String phase) {}

    /**
     * 选择默认与显式能力并展开依赖；显式能力不可用时拒绝，默认禁用能力不自动路由。
     * 2026/11/05：明确未注册组件及能力的拒绝条件。
     * @param definition 实例声明
     * @return 按能力 ID 排序的版本快照
     * @throws IllegalArgumentException 当装配包含未注册组件或能力时
     */
    public AgentPluginSelection select(AgentDefinition definition) {
        var selected = new TreeMap<String, PluginRegistry.Token>();
        // 2026/11/04：独立宿主同样拒绝未注册组件，不默默降级为纯模型。
        if (!definition.assembly().legacy()) {
            for (var id : definition.assembly().parameters().keySet()) {
                boolean registered = features.values().stream().anyMatch(feature ->
                        feature instanceof org.zexnocs.teanekoagent.component.AgentComponentFeature component
                                && component.componentId().equals(id));
                if (!registered) {
                    throw new IllegalArgumentException("Agent 装配引用未注册组件：" + id);
                }
            }
        }
        for (var feature : features.values()) {
            // 2026/11/04：新装配只选择显式组件；旧实例保留历史默认能力。
            if (definition.assembly().legacy() && feature.defaultEnabled() && plugins.isEnabled(feature.pluginId())) {
                selectFeature(feature.key(), selected);
            } else if (!definition.assembly().legacy()
                    && feature instanceof org.zexnocs.teanekoagent.component.AgentComponentFeature component
                    && definition.assembly().has(component.componentId()) && plugins.isEnabled(feature.pluginId())) {
                selectFeature(feature.key(), selected);
            } else if (!definition.assembly().legacy() && feature.defaultEnabled()
                    && !feature.assemblyRequirements().isEmpty()
                    && feature.assemblyRequirements().stream().allMatch(definition.assembly()::has)
                    && plugins.isEnabled(feature.pluginId())) {
                // 2026/11/04：客户端主动声明组件要求，Agent 不依赖任何具体客户端能力。
                selectFeature(feature.key(), selected);
            } else if (!definition.assembly().legacy() && definition.assembly().has("tool")
                    && feature.defaultEnabled() && feature.assemblyRequirements().isEmpty()
                    && feature.promptNames().isEmpty() && plugins.isEnabled(feature.pluginId())) {
                selectFeature(feature.key(), selected);
            }
        }
        // 2026/11/07：保留显式意愿；当前停用的能力不进入本轮，不改写持久配置。
        definition.features().stream().flatMap(key -> featureAliases.getOrDefault(key, Set.of(key)).stream())
                .forEach(key -> selectFeature(key, selected));
        return new AgentPluginSelection(selected);
    }

    /**
     * 展开单个声明并固定依赖插件版本。
     * @param key 能力 ID
     * @param selected 已选择版本
     */
    private void selectFeature(String key, Map<String, PluginRegistry.Token> selected) {
        if (selected.containsKey(key)) { return; }
        var feature = features.get(key);
        if (feature == null) { throw new IllegalArgumentException("Agent 能力未注册：" + key); }
        // 2026/11/07：能力依赖停用时消费者也不参与，不能保留依赖不完整的运行计划。
        if (!available(feature)) {
            return;
        }
        selected.put(key, plugins.capture(feature.pluginId())); feature.dependencies().forEach(dependency -> selectFeature(dependency, selected));
    }

    /**
     * 只读取初始化已校验无环的能力目录，递归复核每个依赖的当前插件资格。
     * @param feature 已安装的能力声明
     * @return 所有传递依赖均可用时为真
     */
    private boolean available(IAgentFeature feature) {
        return plugins.isEnabled(feature.pluginId())
                && feature.dependencies().stream().allMatch(key -> available(features.get(key)));
    }

    /**
     * 为实例创建自有不可变组件，不访问数据库。
     * @param definition 实例定义
     * @return 合并后的初始组件
     */
    public ComponentPatch initial(AgentDefinition definition) {
        var selected = select(definition); var patch = ComponentPatch.builder();
        for (var phase : IAgentFeature.Phase.values()) {
            pipeline(phase, selected.tokens().keySet());
        }
        for (var key : new TreeSet<>(selected.tokens().keySet())) {
            var feature = features.get(key); patch.merge(validatePatch(feature, feature.instanceComponents(definition)));
        }
        check(selected); return patch.build();
    }

    /**
     * 为准入后的轮次建立固定选择和独立组件，再执行资料前置 System。
     * @param context 本轮组件与控制
     * @return 原管线统一提交的组件补丁
     */
    public CompletionStage<SystemResult> prepare(SystemContext context) {
        try {
            var selection = select(context.snapshot().require(AgentComponents.DEFINITION));
            var initial = ComponentPatch.builder().put(SELECTION, selection);
            var call = context(context);
            for (var key : new TreeSet<>(selection.tokens().keySet())) {
                var feature = features.get(key); initial.merge(validatePatch(feature, feature.turnComponents(call)));
            }
            var shadow = shadow(context.snapshot(), initial.build());
            return runSystems(IAgentFeature.Phase.BEFORE_PROMPT, new SystemContext(shadow.snapshot(), context.control()));
        } catch (RuntimeException failure) { return CompletableFuture.failedStage(failure); }
    }

    /**
     * 在隔离实体运行插件 System，只向宿主返回允许的组件及固定选择。
     * @param phase 宿主阶段
     * @param context 当前上下文
     * @return 插件组件补丁
     */
    public CompletionStage<SystemResult> runSystems(IAgentFeature.Phase phase, SystemContext context) {
        try {
            var selection = context.snapshot().require(SELECTION); check(selection);
            var ids = new TreeSet<>(selection.tokens().keySet());
            var pipeline = pipeline(phase, ids);
            return pipeline.execute(shadow(context.snapshot(), ComponentPatch.empty()), context.control()).thenApply(result -> {
                check(selection); var patch = ComponentPatch.builder().put(SELECTION, selection);
                for (var key : components) {
                    if (result.snapshot().contains(key)) { patch.copy(result.snapshot(), key); }
                    else if (context.snapshot().contains(key)) { patch.remove(key); }
                }
                return result.stopped() ? SystemResult.stop(patch.build()) : SystemResult.continueWith(patch.build());
            });
        } catch (RuntimeException failure) { return CompletableFuture.failedStage(failure); }
    }

    /**
     * 编译所选能力的阶段依赖，并在每个 System 执行前后检查固定版本。
     * @param phase 宿主阶段
     * @param ids 已安装能力
     * @return 已验证的 Core 管线
     */
    private SystemPipeline compile(IAgentFeature.Phase phase, Set<String> ids) {
        var systems = new ArrayList<IEntitySystem>();
        for (var id : ids) {
            for (var contribution : features.get(id).systems()) {
                if (contribution.phase() == phase) {
                    // 2026/11/04：装配器仅提供版本检查，不再内嵌 ECS 系统类型。
                    systems.add(new AgentGuardedSystem(contribution.system(), this::check));
                }
            }
        }
        return SystemPipeline.compile(systems);
    }

    /**
     * 缓存至多二百五十六种组合，超出时按需编译，避免任意实例组合无限占用内存。
     * @param phase 宿主阶段
     * @param ids 安装能力
     * @return 稳定的步骤管线
     */
    private SystemPipeline pipeline(IAgentFeature.Phase phase, Set<String> ids) {
        var ordered = new TreeSet<>(ids); var key = phase.name() + "|" + String.join("|", ordered);
        synchronized (pipelines) {
            var existing = pipelines.get(key);
            if (existing != null) { return existing; }
            var compiled = compile(phase, ordered);
            if (pipelines.size() < 256) { pipelines.put(key, compiled); }
            return compiled;
        }
    }

    /**
     * 复制只读上下文，插件执行不持有宿主实体写入口。
     * @param snapshot 原快照
     * @param patch 已验证补丁
     * @return 独立临时实体
     */
    private static Entity shadow(EntitySnapshot snapshot, ComponentPatch patch) {
        var initial = ComponentPatch.builder(); snapshot.keys().forEach(key -> initial.copy(snapshot, key));
        return new Entity(snapshot.id(), initial.merge(patch).build());
    }

    /**
     * 验证初始化只操作声明的自有键。
     * @param feature 所属能力
     * @param patch 初始补丁
     * @return 已验证补丁
     */
    private static ComponentPatch validatePatch(IAgentFeature feature, ComponentPatch patch) {
        if (!feature.components().containsAll(patch.changedKeys())) { throw new IllegalArgumentException("插件初始化写入未声明组件：" + feature.key()); }
        return patch;
    }

    /**
     * 从可信组件建立显式操作上下文。
     * @param context Core 步骤上下文
     * @return 只读业务上下文
     */
    private static AgentPluginContext context(SystemContext context) {
        var snapshot = context.snapshot();
        var request = snapshot.require(AgentComponents.REQUEST).withMemoryReference(snapshot.find(AgentComponents.CONTEXT_REFERENCE).orElse(null));
        return new AgentPluginContext(snapshot.require(AgentComponents.DEFINITION), request,
                snapshot.require(AgentComponents.RUNTIME), snapshot, context.control());
    }

    /**
     * 检查固定选择，禁用再启用也不能接受旧结果。
     * @param selection 本轮选择
     */
    public void check(AgentPluginSelection selection) { selection.tokens().values().forEach(plugins::check); }

    /**
     * 把能力版本附在实际布局上，不增加模型输入字节。
     * @param input 原布局
     * @param snapshot 本轮组件
     * @return 带固定版本的布局
     */
    public AgentPromptInput stamp(AgentPromptInput input, EntitySnapshot snapshot) {
        var selection = snapshot.require(SELECTION); check(selection);
        return new AgentPromptInput(input.original(), input.fragments(), input.omittedFragments(), input.personality(), input.summary(), selection.tokens());
    }

    /**
     * 包围资料解析及可修改事件，插件不能替换操作的可信上下文。
     * @param context 当前 Core 上下文
     * @param source 实际资料解析
     * @return 冻结并等待通知的资料候选
     */
    public CompletionStage<List<AgentPromptFragment>> prepareFragments(SystemContext context, Supplier<CompletionStage<List<AgentPromptFragment>>> source) {
        var selection = context.snapshot().require(SELECTION); var call = context(context);
        var bindings = selection.tokens().keySet().stream().sorted().flatMap(id -> features.get(id).promptInterceptors().stream()).toList();
        var allowedNames = new HashSet<String>();
        selection.tokens().keySet().forEach(id -> allowedNames.addAll(features.get(id).promptNames()));
        return new OperationPipeline<>(plugins, bindings, false).execute(call, context.control(), actual -> {
            if (actual != call) { return CompletableFuture.failedStage(new IllegalArgumentException("插件不可替换可信调用上下文。")); }
            check(selection); return source.get().thenApply(values -> {
                values.forEach(value -> allowedNames.add(value.name())); return values;
            });
        }).thenCompose(values -> decide(new AgentPromptPreparingEvent(eventContext(context), values), context.control()))
                .thenApply(values -> {
                    check(selection);
                    if (values.stream().anyMatch(value -> !allowedNames.contains(value.name()))) { throw new IllegalArgumentException("资料拦截或事件写入未声明来源。"); }
                    return List.copyOf(values);
                });
    }

    /**
     * 工具筛选只允许原候选移除或重排，不允许监听器绕过工具贡献归属。
     * @param context 当前上下文
     * @param tools 已验证原候选
     * @return 冻结后的工具选择
     */
    public CompletionStage<List<LLMToolBinding>> selectTools(SystemContext context, List<LLMToolBinding> tools) {
        var allowed = Collections.newSetFromMap(new IdentityHashMap<LLMToolBinding, Boolean>()); allowed.addAll(tools);
        return decide(new AgentToolsSelectingEvent(eventContext(context), tools), context.control()).thenApply(selected -> {
            check(context.snapshot().require(SELECTION));
            if (!allowed.containsAll(selected) || new HashSet<>(selected).size() != selected.size()) { throw new IllegalArgumentException("工具事件只允许筛选或重排原候选。"); }
            return selected;
        });
    }

    /**
     * 等待有序同步监听，成功或失败均冻结草稿，取消明确终止业务。
     * @param event 决策草稿
     * @param control 借用控制
     * @param <T> 业务项类型
     * @return 冻结后的业务项
     */
    private <T> CompletionStage<List<T>> decide(AgentDecisionEvent<T> event, ExecutionControl control) {
        try {
            var service = events.getIfAvailable();
            CompletionStage<?> notified = service == null ? CompletableFuture.completedStage(null)
                    : TaskStages.successful(service.pushEventWithFuture(event), "Agent 插件决策");
            return control.guard(notified).handle((ignored, failure) -> {
                var values = event.freeze();
                if (failure != null) { throw new CompletionException(failure); }
                if (event.isCancelled()) { throw new AgentPromptException(AgentPromptException.Reason.DISABLED); }
                return values;
            });
        } catch (RuntimeException failure) { event.freeze(); return CompletableFuture.failedStage(failure); }
    }

    /**
     * 仅导出事件所需的可信定位，不导出执行资源。
     * @param context 当前上下文
     * @return 事件定位
     */
    private static AgentEventContext eventContext(SystemContext context) {
        var snapshot = context.snapshot(); var request = snapshot.require(AgentComponents.REQUEST);
        return new AgentEventContext(request.requestId(), snapshot.require(AgentComponents.INSTANCE),
                snapshot.require(AgentComponents.DEFINITION).id(), request.actor().scope(), snapshot.require(SELECTION).tokens().keySet());
    }

    /**
     * 在旧总工具源之外组合显式能力，工具仍由原有限交互及权限守卫执行。
     * @param base 宿主明确指定的旧来源
     * @return 可组合工具源
     */
    public IAgentToolSource tools(IAgentToolSource base) { return new CombinedTools(base); }

    /**
     * 在旧总资料源之外组合能力，并在执行与重放时检查固定版本。
     * @param base 宿主旧来源
     * @return 可组合资料源
     */
    public IAgentPromptSource prompts(IAgentPromptSource base) { return new CombinedPrompts(base); }

    /**
     * 复核版本并依次等待全部来源，不吞掉动态源拒绝。
     * @param input 实际布局
     */
    private void checkInput(AgentPromptInput input) { input.plugins().values().forEach(plugins::check); }

    /**
     * 只交接某来源拥有的完整片段，保留原插件版本及宿主输入，不混淆来源验证。
     * @param input 原完整布局
     * @param names 自有来源名，空值表示旧总来源的非插件片段
     * @return 来源独立视图
     */
    private AgentPromptInput sourceInput(AgentPromptInput input, Set<String> names) {
        var fragments = input.fragments().stream().filter(value -> names == null ? !promptNames.contains(value.name()) : names.contains(value.name())).toList();
        return new AgentPromptInput(input.original(), fragments, input.omittedFragments(), input.personality(), input.summary(), input.plugins());
    }

    /**
     * 工具来源组合；没有布局时只用于旧入口的明确选择。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @author zExNocs
     * @date 2026/11/02
     * @since 4.6.0-l
     * @version 4.9.0-l
     */
    private final class CombinedTools implements IAgentToolSource {
        /**
         * 2026/11/05：按原插件布局向各来源转交最终集合，复核不赋予新的工具权限。
         * @param request 原请求
         * @param input 原布局
         * @param selected 最终集合
         */
        @Override public void verifySelection(AgentRunRequest request, AgentPromptInput input, List<LLMToolBinding> selected) {
            checkInput(input);
            base.verifySelection(request, input, selected);
            for (var id : new TreeSet<>(input.plugins().keySet())) {
                features.get(id).tools().verifySelection(request, input, selected);
            }
            checkInput(input);
        }
        private final IAgentToolSource base;
        /** @param base 宿主原来源 */
        private CombinedTools(IAgentToolSource base) { this.base = base; }
        /**
         * 旧入口只保留宿主来源，插件工具需要固定布局。
         * @param definition 定义
         * @param request 请求
         * @param runtime 运行设置
         * @return 宿主工具
         */
        @Override public List<LLMToolBinding> select(AgentDefinition definition, AgentRunRequest request, AgentRuntimeSnapshot runtime) {
            return base.select(definition, request, runtime);
        }
        /**
         * 使用固定版本聚合并拒绝重名，实际工具前后复核版本。
         * @param definition 定义
         * @param request 请求
         * @param runtime 运行设置
         * @param input 实际布局
         * @return 独立工具绑定
         */
        @Override public List<LLMToolBinding> select(AgentDefinition definition, AgentRunRequest request, AgentRuntimeSnapshot runtime, AgentPromptInput input) {
            checkInput(input); var selected = new ArrayList<>(base.select(definition, request, runtime, input));
            for (var id : new TreeSet<>(input.plugins().keySet())) {
                var feature = features.get(id);
                if (feature == null) { throw new IllegalStateException("原插件能力已经不存在。"); }
                var token = input.plugins().get(id);
                for (var binding : feature.tools().select(definition, request, runtime, input)) {
                    selected.add(binding.intercept((call, trusted, next) -> {
                        plugins.check(token); return next.call(call, trusted).thenApply(value -> { plugins.check(token); return value; });
                    }));
                }
            }
            var names = new HashSet<String>();
            for (var binding : selected) {
                if (!names.add(binding.definition().getName())) { throw new IllegalArgumentException("插件工具名称重复：" + binding.definition().getName()); }
            }
            return List.copyOf(selected);
        }
    }

    /**
     * 资料来源组合，版本与业务校验同时保留。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @author zExNocs
     * @date 2026/11/02
     * @since 4.6.0-l
     * @version 4.9.0-l
     */
    private final class CombinedPrompts implements IAgentPromptSource {
        private final IAgentPromptSource base;
        /** @param base 原资料源 */
        private CombinedPrompts(IAgentPromptSource base) { this.base = base; }
        /**
         * 有序等待每个来源，不创建额外预算。
         * @param definition 定义
         * @param request 可信请求
         * @param runtime 固定设置
         * @param control 借用控制
         * @return 所有候选资料
         */
        @Override public CompletionStage<List<AgentPromptFragment>> resolve(AgentDefinition definition, AgentRunRequest request, AgentRuntimeSnapshot runtime, ExecutionControl control) {
            var selection = select(definition);
            CompletionStage<List<AgentPromptFragment>> pending = base.resolve(definition, request, runtime, control).thenApply(values -> {
                if (values.stream().anyMatch(value -> promptNames.contains(value.name()))) { throw new IllegalArgumentException("宿主附加来源不能冒用插件资料名。"); }
                return List.copyOf(values);
            });
            for (var id : new TreeSet<>(selection.tokens().keySet())) {
                pending = pending.thenCompose(previous -> control.guard(features.get(id).prompts().resolve(definition, request, runtime, control))
                        .thenApply(values -> {
                            if (values.stream().anyMatch(value -> !features.get(id).promptNames().contains(value.name()))) { throw new IllegalArgumentException("插件返回未声明的资料来源：" + id); }
                            var combined = new ArrayList<>(previous); combined.addAll(values); return List.copyOf(combined);
                        }));
            }
            return pending.thenApply(values -> { check(selection); return values; });
        }
        /**
         * 即使资料为空，也校验本轮已采用组件和工具的插件版本。
         * @param request 原请求
         * @param input 固定布局
         * @param control 借用控制
         * @return 校验完成阶段
         */
        @Override public CompletionStage<Void> verify(AgentRunRequest request, AgentPromptInput input, ExecutionControl control) {
            checkInput(input); CompletionStage<Void> checked = base.verify(request, sourceInput(input, null), control);
            for (var id : new TreeSet<>(input.plugins().keySet())) {
                checked = checked.thenCompose(ignored -> features.get(id).prompts().verify(request, sourceInput(input, features.get(id).promptNames()), control));
            }
            return control.guard(checked).thenRun(() -> checkInput(input));
        }
        /**
         * 使用原布局检查旧结果，任何来源未明确允许重放时拒绝。
         * @param request 原请求
         * @param input 原布局
         * @param control 本次观察控制
         * @return 可重放时为真
         */
        @Override public CompletionStage<Boolean> verifyReplay(AgentRunRequest request, AgentPromptInput input, ExecutionControl control) {
            try {
                checkInput(input); CompletionStage<Boolean> checked = base.verifyReplay(request, sourceInput(input, null), control);
                for (var id : new TreeSet<>(input.plugins().keySet())) {
                    checked = checked.thenCompose(allowed -> allowed ? features.get(id).prompts().verifyReplay(request, sourceInput(input, features.get(id).promptNames()), control)
                            : CompletableFuture.completedStage(false));
                }
                return control.guard(checked).thenApply(allowed -> { checkInput(input); return allowed; });
            } catch (RuntimeException failure) { return CompletableFuture.completedStage(false); }
        }
    }
}
