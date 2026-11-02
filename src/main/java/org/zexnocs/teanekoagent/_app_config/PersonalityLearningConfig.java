package org.zexnocs.teanekoagent._app_config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.zexnocs.teanekoagent.agent.access.AgentAccessGate;
import org.zexnocs.teanekoagent.agent.memory.IAgentMemoryReferences;
import org.zexnocs.teanekoagent.agent.personality.IAgentPersonalityLearning;
import org.zexnocs.teanekoagent.agent.personality.IAgentPersonalitySource;
import org.zexnocs.teanekoagent.llm.framework.interaction.ILLMInteractionService;
import org.zexnocs.teanekoagent.personality.PersonalityLearningService;
import org.zexnocs.teanekoagent.personality.PersonalityLearningSettings;
import org.zexnocs.teanekoagent.personality.StyleObservationService;
import org.zexnocs.teanekocore.logger.ILogger;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

/**
 * 默认后台人格学习组合根，配置按尝试懒读取，不建立额外线程池。
 * <br>2026/11/03：观察式人格已装配时停止旧逐轮模型分析，关闭观察后保留回退入口。
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.0.0
 * @version 4.6.1-l
 */
@Configuration(proxyBeanMethods = false)
public class PersonalityLearningConfig {
    /**
     * 组合默认生成策略；自定义只读人格源未提供候选接收端口时不自动学习。
     * @param gate 全活动门控
     * @param personalities 原人格源
     * @param references 原空间复核
     * @param interactions 原有限交互
     * @param environment 本地预算
     * @param mapper JSON 转换器
     * @param executor 借用业务执行器
     * @param scheduler 借用截止调度器
     * @param logger 非敏感用量日志
     * @param observations 可选持久观察服务
     * @return 可替换后台学习策略
     */
    @Bean(destroyMethod = "close") @ConditionalOnMissingBean(IAgentPersonalityLearning.class)
    public PersonalityLearningService personalityLearning(AgentAccessGate gate, IAgentPersonalitySource personalities, IAgentMemoryReferences references,
            ILLMInteractionService interactions, Environment environment, ObjectMapper mapper, @Qualifier("virtualExecutor") ExecutorService executor,
            @Qualifier("taskExecutor") ScheduledExecutorService scheduler, ILogger logger, ObjectProvider<StyleObservationService> observations) {
        return new PersonalityLearningService(gate, personalities, references, interactions, () -> new PersonalityLearningSettings(
                // 2026/11/03：默认联合整理已承担观察，不对同一聊天重复消耗模型预算。
                environment.getProperty("tea-neko.agent.personality-learning.enabled", Boolean.class, true)
                        && !(observations.getIfAvailable() != null
                        && environment.getProperty("tea-neko.agent.personality-learning.observation-enabled", Boolean.class, true)
                        && environment.getProperty("tea-neko.agent.memory.layered.enabled", Boolean.class, true)),
                Duration.ofSeconds(environment.getProperty("tea-neko.agent.personality-learning.cooldown-seconds", Long.class, 300L)),
                Duration.ofSeconds(environment.getProperty("tea-neko.agent.personality-learning.timeout-seconds", Long.class, 45L)),
                environment.getProperty("tea-neko.agent.personality-learning.max-model-calls", Integer.class, 1),
                environment.getProperty("tea-neko.agent.personality-learning.max-total-tokens", Long.class, 16384L),
                environment.getProperty("tea-neko.agent.personality-learning.max-input-bytes", Long.class, 131072L),
                Duration.ofDays(environment.getProperty("tea-neko.agent.personality-learning.retention-days", Long.class, 30L))),
                mapper, executor, scheduler, Clock.systemUTC(), report -> logger.info("Agent 人格学习", "后台候选结束，状态=" + report.outcome()
                        + "，模型状态=" + report.modelStatus() + "，独立预算用量=" + report.budgetUsage()));
    }
}
