package org.zexnocs.teanekoagent.component.memory.configuration;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.zexnocs.teanekoagent.component.llm.framework.interaction.ILLMInteractionService;
import org.zexnocs.teanekoagent.component.memory.runtime.IAgentMemoryReferences;
import org.zexnocs.teanekoagent.component.memory.summary.AgentConversationSummaryService;
import org.zexnocs.teanekoagent.component.memory.summary.AgentSummarySettings;
import org.zexnocs.teanekoagent.component.memory.summary.IAgentConversationSummaries;
import org.zexnocs.teanekoagent.component.personality.IAgentPersonalitySource;
import org.zexnocs.teanekoagent.integration.config.AgentScopeConfigData;
import org.zexnocs.teanekoagent.integration.config.AgentScopeConfigManager;
import org.zexnocs.teanekoagent.runtime.access.AgentAccessGate;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataService;
import org.zexnocs.teanekocore.logger.ILogger;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

/**
 * 自动摘要组合根，配置在业务域懒读取，不拥有 Core 执行器或默认创建会话。
 *
 * <br>2026/11/02：默认分层摘要启用时停用旧内存生成，避免重复消耗同一段聊天。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：取消摘要默认 token 限额，只读取显式预算。
 * <br>2026/11/04：校正当前实现的版本归属为 v4.7.2-l。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/06：后台摘要的附加进度降为 DEBUG，交互结束时统一汇总。
 * <br>2026/11/07：根据原提交职责恢复编码损坏的历史注释。
 *
 * @author zExNocs
 * @date 2026/10/30
 * @since 4.5.0-l
 * @version 4.8.5-l
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
     * @param layered 2026/11/02：可选的实际分层装配，独立宿主未装配时保留原摘要
     * @return 拥有自有后台控制的摘要服务
     */
    @Bean(destroyMethod = "close") @ConditionalOnMissingBean(IAgentConversationSummaries.class)
    public AgentConversationSummaryService agentSummaries(AgentAccessGate gate, ILLMInteractionService interactions, IAgentMemoryReferences references,
            IAgentPersonalitySource personalities, IConfigDataService configs, AgentScopeConfigManager manager, Environment environment,
            ObjectMapper mapper, @Qualifier("virtualExecutor") ExecutorService executor, @Qualifier("taskExecutor") ScheduledExecutorService scheduler, ILogger logger,
            org.springframework.beans.factory.ObjectProvider<org.zexnocs.teanekoagent.component.memory.layered.ILayeredMemoryStore> layered) {
        return new AgentConversationSummaryService(gate, interactions, references, personalities, scope -> {
            // 2026/11/02：关闭分层功能时仍可使用原内存摘要。
            boolean enabled = !(layered.getIfAvailable() != null && environment.getProperty("tea-neko.agent.memory.layered.enabled", Boolean.class, true))
                    && configs.getConfigData(manager, AgentScopeConfigData.class, scope.scopeId())
                    .map(data -> data.toPolicy().enabled() && data.isAutoSummaryEnabled()).orElse(false);
            return new AgentSummarySettings(enabled, environment.getProperty("tea-neko.agent.summary.trigger-turns", Integer.class, 12),
                    environment.getProperty("tea-neko.agent.summary.keep-turns", Integer.class, 4),
                    environment.getProperty("tea-neko.agent.summary.trigger-bytes", Long.class, 32768L),
                    environment.getProperty("tea-neko.agent.summary.max-input-bytes", Long.class, 131072L),
                    environment.getProperty("tea-neko.agent.summary.max-characters", Integer.class, 4000),
                    environment.getProperty("tea-neko.agent.summary.max-model-calls", Integer.class, 1),
                    // 2026/11/04：只保留管理员显式声明的总 token 预算。
                    environment.getProperty("tea-neko.agent.summary.max-total-tokens", Long.class),
                    Duration.ofSeconds(environment.getProperty("tea-neko.agent.summary.timeout-seconds", Long.class, 45L)),
                    Duration.ofSeconds(environment.getProperty("tea-neko.agent.summary.cooldown-seconds", Long.class, 60L)));
        // 2026/11/06：保留采用结果供 DEBUG 查询，INFO 由模型交互统一汇总。
        }, mapper, executor, scheduler, Clock.systemUTC(), usage -> logger.debug("Agent 摘要", "后台摘要结束，状态=" + usage.status()
                + "，已形成摘要=" + usage.accepted() + "，独立预算用量=" + usage.budgetUsage()));
    }
}
