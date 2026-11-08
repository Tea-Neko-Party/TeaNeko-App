package org.zexnocs.teanekoplugin.agent.cognition.personality.configuration;

import org.zexnocs.teanekoagent.integration.config.AgentComponentAccess;

// 2026/11/07：认知业务契约归入插件，纯会话数据沿用运行底座。

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekocore.plugin.PluginRegistry;

// 2026/11/07：按插件归属迁移业务实现，保留底层契约、持久标识和事务边界。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.zexnocs.teanekoplugin.agent.cognition.personality.IAgentPersonalitySource;
import org.zexnocs.teanekoplugin.agent.cognition.personality.IPersonalityConfiguration;
import org.zexnocs.teanekoplugin.agent.cognition.personality.IStyleObservationStore;
import org.zexnocs.teanekoplugin.agent.cognition.personality.StyleObservationService;
import org.zexnocs.teanekoplugin.agent.cognition.personality.adapter.EasyDataPersonalityStore;
import org.zexnocs.teanekoplugin.agent.cognition.personality.adapter.EasyDataStyleObservationStore;
import org.zexnocs.teanekoplugin.agent.cognition.personality.adapter.StyleObservationTools;
import org.zexnocs.teanekoagent.database.easydata.TeaNekoAgentEasyData;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ExecutorService;

/**
 * 默认观察式人格装配，独立只读宿主没有事务人格实现时保留原行为。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/07：按插件归属迁移业务实现，保留原配置与持久化契约。
 *
 * <br>2026/11/07：从有效组件装配读取本插件参数，不依赖宿主业务访问器。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.6.1-l
 * @version 4.9.0-l
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBean(EasyDataPersonalityStore.class)
@org.zexnocs.teanekocore.plugin.PluginOwned("agent.cognition")
public class StyleObservationConfig {
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
     * 注册与原人格数据同事务的表达观察实现。
     * @param data 原专用数据入口
     * @param personalities 原人格存储
     * @param mapper 原转换器
     * @param environment 原本地采用策略
     * @return 可替换观察端口
     */
    @Bean @ConditionalOnMissingBean(IStyleObservationStore.class)
    public EasyDataStyleObservationStore styleObservationStore(TeaNekoAgentEasyData data, EasyDataPersonalityStore personalities, ObjectMapper mapper, Environment environment) {
        return new EasyDataStyleObservationStore(data, personalities, mapper,
                () -> Duration.ofDays(environment.getProperty("tea-neko.agent.personality-learning.retention-days", Long.class, 30L)),
                () -> Duration.ofSeconds(environment.getProperty("tea-neko.agent.personality-learning.cooldown-seconds", Long.class, 300L)));
    }
    /**
     * 默认 Spring 工具目录扫描普通注解提供者，读取与学习使用独立策略。
     * @param store 当前观察存储
     * @param personalities 原人格源
     * @param components 当前有效组件参数
     * @param executor 原业务域
     * @return 当前 scope 观察查询工具
     */
    @Bean
    public StyleObservationTools styleObservationTools(IStyleObservationStore store, IAgentPersonalitySource personalities,
            AgentComponentAccess components, @Qualifier("virtualExecutor") ExecutorService executor) {
        return new StyleObservationTools(store, personalities, scope -> components.settings(scope, "memory")
                .map(value -> enabled() && value.path("memoryReadEnabled").asBoolean(true) && value.path("historyReadEnabled").asBoolean(true)).orElse(false), executor, Clock.systemUTC());
    }
    /**
     * 人格学习按真实登记、独立学习开关及当前基础配置执行。
     * @param personalities 原人格源
     * @param configuration 原配置
     * @param store 观察端口
     * @param components 当前有效组件参数
     * @param environment 本地开关
     * @param executor 原业务域
     * @return 宿主目标与守卫来源
     */
    @Bean @ConditionalOnMissingBean(StyleObservationService.class)
    public StyleObservationService styleObservationService(IAgentPersonalitySource personalities, IPersonalityConfiguration configuration,
            IStyleObservationStore store, AgentComponentAccess components, Environment environment,
            @Qualifier("virtualExecutor") ExecutorService executor) {
        return new StyleObservationService(personalities, configuration, store, scope ->
                enabled() && environment.getProperty("tea-neko.agent.personality-learning.observation-enabled", Boolean.class, true)
                && environment.getProperty("tea-neko.agent.personality-learning.enabled", Boolean.class, true)
                && environment.getProperty("tea-neko.agent.memory.layered.enabled", Boolean.class, true)
                && components.settings(scope, "personality")
                    .map(value -> value.path("personalityLearningEnabled").asBoolean(true)).orElse(false),
                executor, Clock.systemUTC());
    }
}
