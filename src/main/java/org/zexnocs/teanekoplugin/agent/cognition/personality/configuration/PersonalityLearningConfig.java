package org.zexnocs.teanekoplugin.agent.cognition.personality.configuration;

import org.zexnocs.teanekoagent.integration.config.AgentComponentAccess;

import org.zexnocs.teanekoplugin.agent.cognition.CognitionContext;
// 2026/11/07：认知业务契约归入插件，纯会话数据沿用运行底座。

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

// 2026/11/07：按插件归属迁移业务实现，保留底层契约、持久标识和事务边界。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.zexnocs.teanekoagent.llm.framework.structured.LLMJsonService;
import org.zexnocs.teanekoplugin.agent.cognition.memory.runtime.IAgentMemoryReferences;
import org.zexnocs.teanekoplugin.agent.cognition.personality.IAgentPersonalityLearning;
import org.zexnocs.teanekoplugin.agent.cognition.personality.IAgentPersonalitySource;
import org.zexnocs.teanekoplugin.agent.cognition.personality.PersonalityLearningService;
import org.zexnocs.teanekoplugin.agent.cognition.personality.PersonalityLearningSettings;
import org.zexnocs.teanekoplugin.agent.cognition.personality.StyleObservationService;
import org.zexnocs.teanekoagent.runtime.access.AgentAccessGate;
import org.zexnocs.teanekocore.logger.ILogger;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

/**
 * 默认后台人格学习组合根，配置按尝试懒读取，不建立额外线程池。
 * <br>2026/11/03：观察式人格已装配时停止旧逐轮模型分析，关闭观察后保留回退入口。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：取消人格学习默认 token 限额，只读取显式预算。
 * <br>2026/11/04：校正当前实现的版本归属为 v4.7.2-l。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/05：按聊天实际历史与压缩通路选择独立人格采样。
 * <br>2026/11/06：装配统一类型化 JSON 生成服务。
 * <br>2026/11/06：后台学习的附加进度降为 DEBUG，避免重复输出请求 INFO。
 * <br>2026/11/07：根据原提交职责恢复编码损坏的历史注释。
 * <br>2026/11/07：按插件归属迁移业务实现，保留原配置与持久化契约。
 *
 * <br>2026/11/07：从有效组件装配读取本插件参数，不依赖宿主业务访问器。
 *
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.5.0-l
 * @version 4.9.0-l
 */
@Configuration(proxyBeanMethods = false)
@org.zexnocs.teanekocore.plugin.PluginOwned("agent.cognition")
public class PersonalityLearningConfig {
    /**
     * 组合默认生成策略；自定义只读人格源未提供候选接收端口时不自动学习。
     * @param gate 全活动门控
     * @param personalities 原人格源
     * @param references 原空间复核
     * @param json 统一类型化 JSON有限交互
     * @param environment 本地预算
     * @param mapper JSON 转换器
     * @param executor 借用业务执行器
     * @param scheduler 借用截止调度器
     * @param logger 非敏感用量日志
     * @param components 当前有效组件参数
     * @param observations 可选持久观察服务
     * @return 可替换后台学习策略
     */
    @Bean(destroyMethod = "close") @ConditionalOnMissingBean(IAgentPersonalityLearning.class)
    public PersonalityLearningService personalityLearning(AgentAccessGate gate, IAgentPersonalitySource personalities, IAgentMemoryReferences references,
            LLMJsonService json, Environment environment, ObjectMapper mapper, @Qualifier("virtualExecutor") ExecutorService executor,
            @Qualifier("taskExecutor") ScheduledExecutorService scheduler, ILogger logger, ObjectProvider<StyleObservationService> observations,
            AgentComponentAccess components) {
        return new PersonalityLearningService(gate, personalities, references, json, () -> new PersonalityLearningSettings(
                // 2026/11/05：总开关不再按全局 Bean 存在关闭，实际通路按每个 scope 选择。
                environment.getProperty("tea-neko.agent.personality-learning.enabled", Boolean.class, true),
                Duration.ofSeconds(environment.getProperty("tea-neko.agent.personality-learning.cooldown-seconds", Long.class, 300L)),
                Duration.ofSeconds(environment.getProperty("tea-neko.agent.personality-learning.timeout-seconds", Long.class, 45L)),
                environment.getProperty("tea-neko.agent.personality-learning.max-model-calls", Integer.class, 1),
                // 2026/11/04：取消默认总 token 配额，显式配置继续执行拒绝准入。
                environment.getProperty("tea-neko.agent.personality-learning.max-total-tokens", Long.class),
                environment.getProperty("tea-neko.agent.personality-learning.max-input-bytes", Long.class, 131072L),
                Duration.ofDays(environment.getProperty("tea-neko.agent.personality-learning.retention-days", Long.class, 30L))),
                // 2026/11/06：模型调用由统一观察器汇总；采用状态仅留在 DEBUG，避免重复 INFO。
                mapper, executor, scheduler, Clock.systemUTC(), report -> logger.debug("Agent 人格学习", "后台候选结束，状态=" + report.outcome()
                        + "，模型状态=" + report.modelStatus() + "，独立预算用量=" + report.budgetUsage()), result -> {
                    var reference = CognitionContext.reference(result.memoryReference());
                    var observation = observations.getIfAvailable();
                    if (reference == null || observation == null || !observation.enabled(reference.scope())
                            || !environment.getProperty("tea-neko.agent.memory.layered.enabled", Boolean.class, true)) {
                        return true;
                    }
                    return components.settings(reference.scope(), "memory")
                            .map(config -> !config.path("historyCaptureEnabled").asBoolean(true) || !config.path("autoSummaryEnabled").asBoolean(true)).orElse(true);
                });
    }
}
