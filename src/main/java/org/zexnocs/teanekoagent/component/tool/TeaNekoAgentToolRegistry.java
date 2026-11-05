package org.zexnocs.teanekoagent.component.tool;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;
import org.zexnocs.teanekoagent.component.tool.api.TeaNekoAgentToolClass;
import org.zexnocs.teanekoagent.plugin.AgentFeature;
import org.zexnocs.teanekoagent.plugin.SourceAgentFeature;
import org.zexnocs.teanekoagent.runtime.IAgentToolSource;
import org.zexnocs.teanekoagent.runtime.prompt.AgentPromptInput;
import org.zexnocs.teanekoagent.runtime.prompt.IAgentPromptSource;
import org.zexnocs.teanekocore.plugin.PluginOwned;
import org.zexnocs.teanekocore.reload.api.IScanner;
import org.zexnocs.teanekocore.utils.StableDigest;
import tools.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.concurrent.ExecutorService;

/**
 * 从已注册 Spring Bean 的类型元数据发现注解工具，延迟取得提供者，避免启动扫描产生构造环。
 * 已由原 AgentFeature 拥有的能力不重复安装，新工具自动生成同一套普通能力贡献。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：公开已启用插件的注解工具目录，不执行工具方法。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/05：工具声明和说明由可重扫目录缓存，页面查询不再遍历全部 Bean 定义。
 *
 * <br>2026/11/05：保留已发现提供者类型，高频选择不重新解析代理。
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.6.1-l
 * @version 4.8.3-l
 */
@Component
public final class TeaNekoAgentToolRegistry implements IScanner {
    private final ConfigurableListableBeanFactory beans;
    private final ObjectMapper mapper;
    private final ExecutorService executor;
    // 2026/11/05：只固定声明和说明，不缓存插件启用或工具执行资格。
    private volatile List<Declaration> discovered = List.of();
    private boolean initialized;

    /**
     * 2026/11/05：初始化时只发现类型元数据，不实例化工具提供者。
     * @param beans Spring 元数据目录
     * @param mapper 项目转换器
     * @param executor 原业务执行器
     */
    public TeaNekoAgentToolRegistry(ConfigurableListableBeanFactory beans, ObjectMapper mapper,
            @Qualifier("virtualExecutor") ExecutorService executor) {
        this.beans = beans;
        this.mapper = mapper;
        this.executor = executor;
        init();
    }

    /** 首次发现工具声明，原重载服务再次初始化时不重复扫描。 */
    @Override
    public synchronized void init() {
        if (!initialized) {
            reload();
        }
    }

    /**
     * 显式重扫类型和说明，校验完成后一次发布；失败保留上一目录。
     * 已创建 Agent 的能力组成保持原实例生命周期，不在这里替换运行中管线。
     *
     * @throws IllegalArgumentException 当工具名称重复或类型声明无法解析时
     */
    @Override
    public synchronized void reload() {
        var found = new ArrayList<Declaration>();
        var names = new HashSet<String>();
        for (var beanName : new TreeSet<>(List.of(beans.getBeanDefinitionNames()))) {
            var type = type(beanName);
            if (type == null) {
                continue;
            }
            var annotation = AnnotatedElementUtils.findMergedAnnotation(type, TeaNekoAgentToolClass.class);
            if (annotation == null) {
                continue;
            }
            for (var name : TeaNekoAgentToolMethods.names(type)) {
                if (!names.add(name)) {
                    throw new IllegalArgumentException("注解工具名称重复：" + name);
                }
            }
            var owned = AnnotatedElementUtils.findMergedAnnotation(type, PluginOwned.class);
            String owner = !annotation.pluginId().isBlank() ? annotation.pluginId()
                    : owned == null ? "teaneko.agent" : owned.value();
            found.add(new Declaration(beanName, type, annotation, owner, TeaNekoAgentToolMethods.describe(type)));
        }
        discovered = List.copyOf(found);
        initialized = true;
    }

    /**
     * 合并原能力和新发现的工具类，工具重名在目录构造阶段拒绝。
     * 2026/11/05：合并只读取已发现声明，不重新遍历 Spring 容器。
     * @param existing 原有能力，不修改其拥有关系
     * @return 原能力与自动能力的不可变集合
     * @throws IllegalArgumentException 当注解工具或归属声明冲突时
     */
    public List<AgentFeature> combine(List<AgentFeature> existing) {
        var keys = new HashSet<String>(); existing.forEach(value -> keys.add(value.key()));
        var result = new ArrayList<>(existing); var groups = new TreeMap<String, List<Declaration>>();
        var declarations = new HashMap<String, TeaNekoAgentToolClass>();
        var owners = new HashMap<String, String>();
        for (var declaration : discovered) {
            var beanName = declaration.name();
            var type = declaration.type();
            var annotation = declaration.annotation();
            var plugin = declaration.owner();
            var id = annotation.value().isBlank() ? "tools-" + StableDigest.sha256("annotated-agent-tools-v1", type.getName(), beanName).substring(0, 12) : annotation.value();
            var key = plugin + ":" + id;
            if (annotation.managedByFeature()) {
                if (!keys.contains(key)) { throw new IllegalArgumentException("注解工具声明的显式所属能力不存在：" + key); }
                continue;
            }
            if (keys.contains(key)) { throw new IllegalArgumentException("自动工具能力与既有能力重名：" + key); }
            var previous = declarations.putIfAbsent(key, annotation);
            if (previous != null && previous.defaultEnabled() != annotation.defaultEnabled()) { throw new IllegalArgumentException("同一工具能力的默认安装声明不一致：" + key); }
            owners.put(key, plugin); groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(declaration);
        }
        groups.forEach((key, providers) -> {
            IAgentToolSource tools = new IAgentToolSource() {
                /**
                 * 保留原三参数来源接口。
                 * @param definition 当前定义
                 * @param request 原请求
                 * @param runtime 运行快照
                 * @return 当前可选工具
                 */
                @Override public List<org.zexnocs.teanekoagent.component.llm.framework.tool.LLMToolBinding> select(org.zexnocs.teanekoagent.runtime.AgentDefinition definition,
                        org.zexnocs.teanekoagent.runtime.AgentRunRequest request, org.zexnocs.teanekoagent.runtime.access.AgentRuntimeSnapshot runtime) {
                    return select(definition, request, runtime, null);
                }
                /**
                 * 到实际业务选择阶段才取得提供者 Bean，不在目录构造时初始化服务。
                 * @param definition 当前定义
                 * @param request 原请求
                 * @param runtime 固定运行快照
                 * @param input 当前布局
                 * @return 已固定本轮身份的工具集合
                 */
                @Override public List<org.zexnocs.teanekoagent.component.llm.framework.tool.LLMToolBinding> select(org.zexnocs.teanekoagent.runtime.AgentDefinition definition,
                        org.zexnocs.teanekoagent.runtime.AgentRunRequest request, org.zexnocs.teanekoagent.runtime.access.AgentRuntimeSnapshot runtime, AgentPromptInput input) {
                    var selected = new ArrayList<org.zexnocs.teanekoagent.component.llm.framework.tool.LLMToolBinding>();
                    for (var provider : providers) { selected.addAll(TeaNekoAgentToolMethods.select(provider.type(), beans.getBean(provider.name()), definition, request, runtime, input, mapper, executor)); }
                    return List.copyOf(selected);
                }
            };
            result.add(new SourceAgentFeature(owners.get(key), key.substring(key.indexOf(':') + 1), declarations.get(key).defaultEnabled(), tools, IAgentPromptSource.empty()));
        });
        return List.copyOf(result);
    }

    /**
     * 优先复用已存在代理的真实类型，否则只解析 Bean 定义，不触发业务初始化。
     * @param name Bean 名
     * @return 可解析的真实类型，无法预知的工厂产品为空
     */
    private Class<?> type(String name) {
        var singleton = beans.getSingleton(name);
        if (singleton != null) { return AopUtils.getTargetClass(singleton); }
        var type = beans.getType(name, false);
        if (type != null && AnnotatedElementUtils.hasAnnotation(type, TeaNekoAgentToolClass.class)) { return type; }
        var className = beans.getBeanDefinition(name).getBeanClassName();
        try { return className == null ? type : ClassUtils.forName(className, beans.getBeanClassLoader()); }
        catch (ClassNotFoundException ignored) { throw new IllegalArgumentException("工具 Bean 类型无法解析：" + name); }
    }

    /**
     * 标记已交由注解能力拥有的提供者，原额外来源不能再重复聚合它。
     * @param bean 候选来源
     * @return 是否为注解工具提供者
     */
    public static boolean isProvider(Object bean) { return AnnotatedElementUtils.hasAnnotation(AopUtils.getTargetClass(bean), TeaNekoAgentToolClass.class); }

    /**
     * 2026/11/04：说明目录仅公开已启用插件的真实注解工具，不执行模型或业务方法。
     * 2026/11/05：说明读取已发现缓存，每次仍实时筛选插件启用状态。
     * @param plugins 当前插件启用边界
     * @return 按工具名排序且去重的公开说明
     */
    public List<TeaNekoAgentToolMethods.Documentation> describe(org.zexnocs.teanekocore.plugin.PluginRegistry plugins) {
        var result = new TreeMap<String, TeaNekoAgentToolMethods.Documentation>();
        for (var declaration : discovered) {
            if (plugins.isEnabled(declaration.owner())) {
                for (var item : declaration.documentation()) {
                    if (result.putIfAbsent(item.name(), item) != null) {
                        throw new IllegalArgumentException("工具目录存在重名声明。");
                    }
                }
            }
        }
        return List.copyOf(result.values());
    }

    /**
     * 不包含工具实例或执行资格的静态发现结果。
     *
     * @param name 原 Bean 名
     * @param type 实际提供者类型
     * @param annotation 原声明
     * @param owner 插件归属
     * @param documentation 参数与用途说明
     * @author zExNocs
     * @date 2026/11/05
     * @since 4.8.2-l
     * @version 4.8.2-l
     */
    private record Declaration(String name, Class<?> type, TeaNekoAgentToolClass annotation, String owner,
                               List<TeaNekoAgentToolMethods.Documentation> documentation) {}
}
