package org.zexnocs.teanekoplugin.agent.cognition.memory.configuration;

import org.zexnocs.teanekoagent.integration.config.AgentComponentAccess;

// 2026/11/07：接口统一使用 I 前缀，调用合同与持久数据保持不变。

// 2026/11/07：认知业务契约归入插件，纯会话数据沿用运行底座。

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekocore.plugin.PluginRegistry;

// 2026/11/07：按插件归属迁移业务实现，保留底层契约、持久标识和事务边界。

import org.zexnocs.teanekoplugin.agent.cognition.memory.layered.ILayeredMemoryCompressor;
import org.zexnocs.teanekoplugin.agent.cognition.memory.layered.ILayeredMemoryStore;
import org.zexnocs.teanekoplugin.agent.cognition.memory.layered.ILongTermMemoryConsolidator;
import org.zexnocs.teanekoplugin.agent.cognition.memory.layered.ILongTermMemoryStore;
import org.zexnocs.teanekoplugin.agent.cognition.memory.layered.IMemoryMaintenance;
import org.zexnocs.teanekoplugin.agent.cognition.memory.layered.LayeredMemoryPolicy;
import org.zexnocs.teanekoplugin.agent.cognition.memory.layered.LayeredMemorySettings;
import org.zexnocs.teanekoplugin.agent.cognition.memory.layered.LongMemoryLearningPolicy;
import org.zexnocs.teanekoplugin.agent.cognition.memory.adapter.EasyDataLayeredMemoryStore;
import org.zexnocs.teanekoplugin.agent.cognition.memory.adapter.EasyDataLongTermMemoryStore;
import org.zexnocs.teanekoplugin.agent.cognition.memory.adapter.EasyDataMemoryNamespaceStore;
import org.zexnocs.teanekoplugin.agent.cognition.memory.adapter.LayeredMemoryPromptSource;
import org.zexnocs.teanekoplugin.agent.cognition.memory.adapter.LongTermCorrectTool;
import org.zexnocs.teanekoplugin.agent.cognition.memory.adapter.LongTermForgetTool;
import org.zexnocs.teanekoplugin.agent.cognition.memory.adapter.LongTermMemoryPromptSource;
import org.zexnocs.teanekoplugin.agent.cognition.memory.adapter.LongTermMemoryTools;
import org.zexnocs.teanekoplugin.agent.cognition.memory.adapter.LongTermProfileTool;
import org.zexnocs.teanekoplugin.agent.cognition.memory.layered.LLMLayeredMemoryCompressor;
import org.zexnocs.teanekoplugin.agent.cognition.memory.layered.LLMLongTermMemoryConsolidator;
import org.zexnocs.teanekoplugin.agent.cognition.memory.layered.LayeredMemoryProcessor;
import org.zexnocs.teanekoplugin.agent.cognition.memory.layered.LongTermMemoryAccess;
import org.zexnocs.teanekoplugin.agent.cognition.memory.layered.MemoryMaintenanceService;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.zexnocs.teanekoagent.llm.framework.structured.LLMJsonService;
import org.zexnocs.teanekoplugin.agent.cognition.memory.history.IAgentDeliveryStore;
import org.zexnocs.teanekoplugin.agent.cognition.memory.layered.*;
import org.zexnocs.teanekoplugin.agent.cognition.memory.runtime.IAgentMemoryReferences;
import org.zexnocs.teanekoplugin.agent.cognition.personality.IStyleObservationStore;
import org.zexnocs.teanekoplugin.agent.cognition.personality.StyleObservationService;
import org.zexnocs.teanekoagent.component.tool.TeaNekoAgentToolMethods;
import org.zexnocs.teanekoagent.plugin.IAgentFeature;
import org.zexnocs.teanekoagent.plugin.SourceAgentFeature;
import org.zexnocs.teanekoagent.runtime.AgentScope;
import org.zexnocs.teanekoagent.runtime.IAgentFactory;
import org.zexnocs.teanekoagent.runtime.access.AgentAccessGate;
import org.zexnocs.teanekocore.logger.ILogger;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 默认分层记忆装配，复用历史事务、插件资料和 Core 执行器，配置仅在业务域读取。
 * <br>2026/11/03：装配长期整理、画像召回及注解维护能力，沿用统一后台预算。
 * <br>2026/11/03：可选观察端口与联合生成共用处理器和事务。
 * <br>2026/11/03：独立装配持久维护，关闭学习不停止到期回收。
 * <br>2026/11/03：近期资料接入实际发送回执。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/05：长期工具按独立 Bean 聚合，保持原能力及 Prompt 所有权。
 * <br>2026/11/05：简化冗余类型实参、变量或转换，保持原业务与测试边界。
 * <br>2026/11/05：在宿主初始化或能力装配时编译工具目录，运行选择只读缓存。
 * <br>2026/11/06：装配统一类型化 JSON 生成服务。
 * <br>2026/11/06：按有效新证据聚合长期提取，保留版本幂等、租约和事务校验。
 * <br>2026/11/07：按插件归属迁移业务实现，保留原配置与持久化契约。
 *
 * <br>2026/11/07：从有效组件装配读取本插件参数，不依赖宿主业务访问器。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.6.1-l
 * @version 4.9.0-l
 */
@Configuration(proxyBeanMethods = false)
@org.zexnocs.teanekocore.plugin.PluginOwned("agent.cognition")
public class LayeredMemoryConfig {
    private @Nullable PluginRegistry plugins;

    /**
     * 2026/11/07：在每次配置读取时复核认知插件状态，不修改持久化的用户意愿。
     * @param plugins 实际插件目录
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void installPlugins(PluginRegistry plugins) {
        this.plugins = plugins;
    }

    /** @return 当前插件与依赖是否仍可提供认知能力。 */
    private boolean enabled() {
        return plugins == null || plugins.isEnabled("agent.cognition");
    }

    /**
     * 提供与原历史同事务的短期和中期存储。
     * @param data 专用 EasyData
     * @param namespaces 空间锁与代次
     * @param mapper 原转换器
     * @param environment 原保留策略
     * @return 默认持久存储
     */
    @Bean @ConditionalOnMissingBean(ILayeredMemoryStore.class)
    public EasyDataLayeredMemoryStore layeredMemoryStore(org.zexnocs.teanekoagent.database.easydata.TeaNekoAgentEasyData data,
            EasyDataMemoryNamespaceStore namespaces, ObjectMapper mapper, Environment environment) {
        // 2026/11/03：维护读取同一动态保留策略，不单独维护第二份默认值。
        return new EasyDataLayeredMemoryStore(data, namespaces, mapper, settings(environment));
    }
    /**
     * 2026/11/03：注册可替换的数据域维护集合，构造无 I/O，调度与学习独立。
     * @param stores 实际装配的维护端口
     * @param environment 本地维护策略
     * @param executor 原业务域
     * @param logger 固定诊断
     * @return 自有调度状态的维护服务
     */
    @Bean(destroyMethod = "close") @ConditionalOnMissingBean(MemoryMaintenanceService.class)
    public MemoryMaintenanceService memoryMaintenanceService(List<IMemoryMaintenance> stores, Environment environment,
            @Qualifier("virtualExecutor") ExecutorService executor, ILogger logger) {
        return new MemoryMaintenanceService(stores, () -> environment.getProperty("tea-neko.agent.memory.maintenance.enabled", Boolean.class, true),
                executor, Clock.systemUTC(), logger);
    }
    /**
     * 提供可替换的原 LLM 摘要适配，不在构造期获取工厂实例。
     * @param factory 懒实例工厂
     * @param gate 统一准入
     * @param references 固定空间复核
     * @param json 统一类型化 JSON模型交互
     * @param components 当前有效组件参数
     * @param environment 本地预算
     * @param mapper 项目转换器
     * @param executor 借用业务域
     * @return 默认摘要生成端口
     */
    @Bean(destroyMethod = "close") @ConditionalOnMissingBean(ILayeredMemoryCompressor.class)
    public LLMLayeredMemoryCompressor layeredMemoryCompressor(ObjectProvider<IAgentFactory> factory, AgentAccessGate gate,
            IAgentMemoryReferences references, LLMJsonService json, AgentComponentAccess components,
            Environment environment, ObjectMapper mapper, @Qualifier("virtualExecutor") ExecutorService executor) {
        return new LLMLayeredMemoryCompressor(factory::getObject, gate, references, json, settings(environment), policies(components), mapper, executor);
    }
    /**
     * 注册恢复式后台处理器，单批模型请求有独立时限和次数预算。
     * @param store 持久消息和租约
     * @param compressor 可替换生成端口
     * @param references 原引用使用前复核
     * @param components 当前有效组件参数
     * @param environment 本地预算
     * @param executor 借用业务域
     * @param scheduler 借用计时器
     * @param logger 脱敏诊断
     * @param longStore 可替换长期存储
     * @param consolidator 可替换长期生成
     * @param observations 可选观察服务
     * @return 自有控制的生命周期拥有者
     */
    @Bean(destroyMethod = "close")
    public LayeredMemoryProcessor layeredMemoryProcessor(ILayeredMemoryStore store, ILayeredMemoryCompressor compressor,
            IAgentMemoryReferences references, AgentComponentAccess components, Environment environment,
            @Qualifier("virtualExecutor") ExecutorService executor, @Qualifier("taskExecutor") ScheduledExecutorService scheduler, ILogger logger,
            ILongTermMemoryStore longStore, ILongTermMemoryConsolidator consolidator, ObjectProvider<StyleObservationService> observations) {
        // 2026/11/03：短中期与长期共用调度、时限、关闭和在途任务限制。
        return new LayeredMemoryProcessor(store, compressor, references, settings(environment), policies(components), executor, scheduler,
                Clock.systemUTC(), logger, longStore, consolidator, scope -> longPolicies(components, environment).apply(scope).learn(), observations.getIfAvailable());
    }
    /**
     * 为长期派生记录提供专用事务域，构造期不访问数据库。
     * @param data 原专用入口
     * @param namespaces 空间锁与代次
     * @param mapper 原转换器
     * @param environment 本地预算
     * @param styles 可选同事务观察端口
     * @return 可替换的长期端口
     */
    @Bean @ConditionalOnMissingBean(ILongTermMemoryStore.class)
    public EasyDataLongTermMemoryStore longTermMemoryStore(org.zexnocs.teanekoagent.database.easydata.TeaNekoAgentEasyData data,
            EasyDataMemoryNamespaceStore namespaces, ObjectMapper mapper, Environment environment, ObjectProvider<IStyleObservationStore> styles) {
        // 2026/11/03：默认事实与表达共享原子提交，独立宿主仍可不装配观察域。
        return new EasyDataLongTermMemoryStore(data, namespaces, mapper, settings(environment), styles.getIfAvailable());
    }
    /**
     * 提供通过统一学习活动运行的默认模型适配器。
     * @param factory 懒实例工厂
     * @param gate 原准入
     * @param references 当前引用
     * @param json 统一类型化 JSON模型交互
     * @param components 当前有效组件参数
     * @param environment 本地开关
     * @param mapper 原转换器
     * @param executor 借用业务域
     * @param observations 可选观察服务
     * @return 可替换的长期选择器
     */
    @Bean(destroyMethod = "close") @ConditionalOnMissingBean(ILongTermMemoryConsolidator.class)
    public LLMLongTermMemoryConsolidator longTermMemoryConsolidator(ObjectProvider<IAgentFactory> factory, AgentAccessGate gate,
            IAgentMemoryReferences references, LLMJsonService json, AgentComponentAccess components,
            Environment environment, ObjectMapper mapper, @Qualifier("virtualExecutor") ExecutorService executor, ObjectProvider<StyleObservationService> observations) {
        return new LLMLongTermMemoryConsolidator(factory::getObject, gate, references, json, settings(environment),
                scope -> longPolicies(components, environment).apply(scope).learn()
                        || observations.getIfAvailable() != null && observations.getObject().enabled(scope), mapper, executor);
    }
    /**
     * 集中实际读写资格和固定引用检查，工具与资料复用同一业务边界。
     * @param store 长期事务端口
     * @param references 当前引用
     * @param components 当前有效组件参数
     * @param environment 本地开关
     * @param executor 借用业务域
     * @return 可信访问服务
     */
    @Bean
    public LongTermMemoryAccess longTermMemoryAccess(ILongTermMemoryStore store, IAgentMemoryReferences references, AgentComponentAccess components, Environment environment, @Qualifier("virtualExecutor") ExecutorService executor) {
        return new LongTermMemoryAccess(store, references, longPolicies(components, environment), executor, Clock.systemUTC());
    }
    /**
     * 注册普通 Spring 注解工具提供者，方法编译器统一完成参数绑定。
     * @param access 实际访问边界
     * @return 由能力包拥有的工具提供者
     */
    @Bean
    public LongTermMemoryTools longTermMemoryTools(LongTermMemoryAccess access) { return new LongTermMemoryTools(access); }

    /**
     * 2026/11/05：注册独立长期画像工具。
     * @param source 共享长期访问逻辑
     * @return 画像工具 Bean
     */
    @Bean
    public LongTermProfileTool longTermProfileTool(LongTermMemoryTools source) {
        return new LongTermProfileTool(source);
    }

    /**
     * 2026/11/05：注册独立长期遗忘工具。
     * @param source 共享长期访问逻辑
     * @return 遗忘工具 Bean
     */
    @Bean
    public LongTermForgetTool longTermForgetTool(LongTermMemoryTools source) {
        return new LongTermForgetTool(source);
    }

    /**
     * 2026/11/05：注册独立长期纠正工具。
     * @param source 共享长期访问逻辑
     * @return 纠正工具 Bean
     */
    @Bean
    public LongTermCorrectTool longTermCorrectTool(LongTermMemoryTools source) {
        return new LongTermCorrectTool(source);
    }

    /**
     * 2026/11/05：独立长期工具和两个上下文片段通过原可拆卸能力包安装。
     * 2026/11/05：简化冗余类型实参、变量或转换，保持原业务与测试边界。
     * @param access 当前访问边界
     * @param tools 注解提供者
     * @param profile 画像工具
     * @param forget 遗忘工具
     * @param correct 纠正工具
     * @param mapper 原转换器
     * @param executor 借用业务域
     * @return 默认长期能力
     */
    @Bean
    public IAgentFeature longTermMemoryFeature(LongTermMemoryAccess access, LongTermMemoryTools tools,
            LongTermProfileTool profile, LongTermForgetTool forget, LongTermCorrectTool correct, ObjectMapper mapper,
            @Qualifier("virtualExecutor") ExecutorService executor) {
        // 2026/11/05：在能力装配时编译实际工具，选择阶段不做发现。
        java.util.stream.Stream.of(tools, profile, forget, correct).forEach(TeaNekoAgentToolMethods::prepare);
        var prompts = new LongTermMemoryPromptSource(access, mapper, executor);
        return new SourceAgentFeature("agent.cognition", "long-memory", true,
                (definition, request, runtime) -> java.util.stream.Stream.of(tools, profile, forget, correct)
                        .flatMap(provider -> TeaNekoAgentToolMethods.select(
                                provider, definition, request, runtime, null, mapper, executor).stream())
                        .toList(),
                prompts, LongTermMemoryPromptSource.NAMES);
    }
    /**
     * 每次使用读取已登记 scope 的权限；保留已有显式关闭值，不自动重写配置。
     * @param components 当前有效组件参数
     * @param environment 本地开关
     * @return 共享读写与学习策略
     */
    private Function<AgentScope, LongTermMemoryAccess.Policy> longPolicies(AgentComponentAccess components, Environment environment) {
        return scope -> {
            boolean enabled = enabled() && settings(environment).get().enabled()
                    && environment.getProperty("tea-neko.agent.memory.long-term.enabled", Boolean.class, true);
            return components.settings(scope, "memory").map(value -> new LongTermMemoryAccess.Policy(
                    enabled, value.path("memoryReadEnabled").asBoolean(true), enabled && value.path("memoryLearningEnabled").asBoolean(true),
                    value.path("memoryAddEnabled").asBoolean(true))).orElseGet(() -> new LongTermMemoryAccess.Policy(false, false, false, false));
        };
    }
    /**
     * 原普通能力路径加入持久近期和中期参考，保留完整插件守卫。
     * @param store 持久资料
     * @param references 原引用校验
     * @param components 当前有效组件参数
     * @param environment 本地开关
     * @param mapper 原转换器
     * @param executor 借用业务域
     * @param deliveries 可选实际平台回执
     * @return 默认安装的分层上下文能力
     */
    @Bean
    public IAgentFeature layeredMemoryFeature(ILayeredMemoryStore store, IAgentMemoryReferences references, AgentComponentAccess components, Environment environment, ObjectMapper mapper, @Qualifier("virtualExecutor") ExecutorService executor,
            ObjectProvider<IAgentDeliveryStore> deliveries) {
        // 2026/11/03：近期资料读取真实回执，不因生成历史存在而声称已发送。
        var prompts = new LayeredMemoryPromptSource(store, references, settings(environment), policies(components), mapper, executor, Clock.systemUTC(), deliveries.getIfAvailable());
        return new SourceAgentFeature("agent.cognition", "layered-memory", true, (definition, request, runtime) -> List.of(), prompts, LayeredMemoryPromptSource.NAMES);
    }
    /**
     * 每次实际执行读取合法本地策略，不在启动扫描时做 I/O。
     * @param environment 本地配置
     * @return 懒策略来源
     */
    private Supplier<LayeredMemorySettings> settings(Environment environment) {
        return () -> new LayeredMemorySettings(environment.getProperty("tea-neko.agent.memory.layered.enabled", Boolean.class, true),
                environment.getProperty("tea-neko.agent.memory.layered.trigger-messages", Integer.class, 12),
                environment.getProperty("tea-neko.agent.memory.layered.keep-messages", Integer.class, 4),
                environment.getProperty("tea-neko.agent.memory.layered.batch-messages", Integer.class, 16),
                environment.getProperty("tea-neko.agent.memory.layered.max-input-bytes", Integer.class, 131072),
                Duration.ofSeconds(environment.getProperty("tea-neko.agent.memory.layered.timeout-seconds", Long.class, 45L)),
                Duration.ofDays(environment.getProperty("tea-neko.agent.memory.layered.retention-days", Long.class, 90L)),
                environment.getProperty("tea-neko.agent.memory.layered.trigger-bytes", Integer.class, 32768),
                // 2026/11/06：长期提取使用独立有界聚合策略，不改变短期压缩频率。
                new org.zexnocs.teanekoplugin.agent.cognition.memory.layered.LongMemoryLearningPolicy(
                        environment.getProperty("tea-neko.agent.memory.long-term.batch-segments", Integer.class, 4),
                        Duration.ofSeconds(environment.getProperty("tea-neko.agent.memory.long-term.max-wait-seconds", Long.class, 600L)),
                        environment.getProperty("tea-neko.agent.memory.long-term.filter-low-information", Boolean.class, true),
                        environment.getProperty("tea-neko.agent.memory.long-term.urgent-events", Boolean.class, true)));
    }
    /**
     * 沿用原 scope 登记、采集/读取与自动摘要开关，不默认授权未登记区域。
     * @param components 当前有效组件参数
     * @return 真实动态策略
     */
    private Function<AgentScope, LayeredMemoryPolicy> policies(AgentComponentAccess components) {
        return scope -> components.settings(scope, "memory").map(value -> new LayeredMemoryPolicy(
                enabled(), value.path("historyReadEnabled").asBoolean(true) && value.path("memoryReadEnabled").asBoolean(true),
                value.path("historyCaptureEnabled").asBoolean(true) && value.path("autoSummaryEnabled").asBoolean(true))).orElseGet(() -> new LayeredMemoryPolicy(false, false, false));
    }
}
