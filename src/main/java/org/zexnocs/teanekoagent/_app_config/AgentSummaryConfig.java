package org.zexnocs.teanekoagent._app_config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.zexnocs.teanekoagent.adapter.config.AgentScopeConfigData;
import org.zexnocs.teanekoagent.adapter.config.AgentScopeConfigManager;
import org.zexnocs.teanekoagent.agent.access.AgentAccessGate;
import org.zexnocs.teanekoagent.agent.memory.IAgentMemoryReferences;
import org.zexnocs.teanekoagent.agent.personality.IAgentPersonalitySource;
import org.zexnocs.teanekoagent.agent.summary.AgentConversationSummaryService;
import org.zexnocs.teanekoagent.agent.summary.AgentSummarySettings;
import org.zexnocs.teanekoagent.agent.summary.IAgentConversationSummaries;
import org.zexnocs.teanekoagent.llm.framework.interaction.ILLMInteractionService;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataService;
import org.zexnocs.teanekocore.logger.ILogger;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

/**
 * 自动摘要组合根，配置在业务域懒读取，不拥有 Core 执行器或默认创建会话。
 * @author zExNocs
 * @date 2026/10/30
 * @since 4.0.0
 * @version 4.5.0-l
 */
@Configuration(proxyBeanMethods = false)
public class AgentSummaryConfig {
    /**
     * 接入默认后台摘要与实际使用复核，显式自定义端口可以完整替换。
     * @param gate 统一门控
     * @param interactions 原交互服务
     * @param references 原空间复核
     * @param personalities 原人格复核
     * @param configs 真实作用域配置
     * @param manager Agent 配置声明
     * @param environment 本地摘要策略
     * @param mapper JSON 转换器
     * @param executor 借用业务执行器
     * @param scheduler 借用调度器
     * @param logger 用量与失败观察
     * @return 拥有自有后台控制的摘要服务
     */
    @Bean(destroyMethod = "close") @ConditionalOnMissingBean(IAgentConversationSummaries.class)
    public AgentConversationSummaryService agentSummaries(AgentAccessGate gate, ILLMInteractionService interactions, IAgentMemoryReferences references,
            IAgentPersonalitySource personalities, IConfigDataService configs, AgentScopeConfigManager manager, Environment environment,
            ObjectMapper mapper, @Qualifier("virtualExecutor") ExecutorService executor, @Qualifier("taskExecutor") ScheduledExecutorService scheduler, ILogger logger) {
        return new AgentConversationSummaryService(gate, interactions, references, personalities, scope -> {
            boolean enabled = configs.getConfigData(manager, AgentScopeConfigData.class, scope.scopeId())
                    .map(data -> data.toPolicy().enabled() && data.isAutoSummaryEnabled()).orElse(false);
            return new AgentSummarySettings(enabled, environment.getProperty("tea-neko.agent.summary.trigger-turns", Integer.class, 12),
                    environment.getProperty("tea-neko.agent.summary.keep-turns", Integer.class, 4),
                    environment.getProperty("tea-neko.agent.summary.trigger-bytes", Long.class, 32768L),
                    environment.getProperty("tea-neko.agent.summary.max-input-bytes", Long.class, 131072L),
                    environment.getProperty("tea-neko.agent.summary.max-characters", Integer.class, 4000),
                    environment.getProperty("tea-neko.agent.summary.max-model-calls", Integer.class, 1),
                    environment.getProperty("tea-neko.agent.summary.max-total-tokens", Long.class, 16384L),
                    Duration.ofSeconds(environment.getProperty("tea-neko.agent.summary.timeout-seconds", Long.class, 45L)),
                    Duration.ofSeconds(environment.getProperty("tea-neko.agent.summary.cooldown-seconds", Long.class, 60L)));
        }, mapper, executor, scheduler, Clock.systemUTC(), usage -> logger.info("Agent 摘要", "后台摘要结束，状态=" + usage.status()
                + "，已形成摘要=" + usage.accepted() + "，独立预算用量=" + usage.budgetUsage()));
    }
}
