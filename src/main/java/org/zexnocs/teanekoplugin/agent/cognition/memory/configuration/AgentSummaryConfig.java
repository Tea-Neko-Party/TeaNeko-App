package org.zexnocs.teanekoplugin.agent.cognition.memory.configuration;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.zexnocs.teanekoagent.integration.config.AgentComponentAccess;
import org.zexnocs.teanekoagent.llm.framework.interaction.ILLMInteractionService;
import org.zexnocs.teanekoagent.runtime.access.AgentAccessGate;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekoplugin.agent.cognition.memory.runtime.IAgentMemoryReferences;
import org.zexnocs.teanekoplugin.agent.cognition.memory.summary.AgentConversationSummaryService;
import org.zexnocs.teanekoplugin.agent.cognition.memory.summary.AgentSummarySettings;
import org.zexnocs.teanekoplugin.agent.cognition.memory.summary.IAgentConversationSummaries;
import org.zexnocs.teanekoplugin.agent.cognition.personality.IAgentPersonalitySource;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

// 2026/11/07：认知业务契约归入插件，纯会话数据沿用运行底座。
// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。
// 2026/11/07：按插件归属迁移业务实现，保留底层契约、持久标识和事务边界。
// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

/**
 * 自动摘要组合根，配置在业务域懒读取，不拥有 Core 执行器或默认创建会话。
 * <br>2026/11/02：默认分层摘要启用时停用旧内存生成，避免重复消耗同一段聊天。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：取消摘要默认 token 限额，只读取显式预算。
 * <br>2026/11/04：校正当前实现的版本归属为 v4.7.2-l。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/06：后台摘要的附加进度降为 DEBUG，交互结束时统一汇总。
 * <br>2026/11/07：根据原提交职责恢复编码损坏的历史注释。
 * <br>2026/11/07：按插件归属迁移业务实现，保留原配置与持久化契约。
 *
 * <br>2026/11/07：从有效组件装配读取本插件参数，不依赖宿主业务访问器。
 *
 * @author zExNocs
 * @date 2026/10/30
 * @since 4.5.0-l
 * @version 4.9.0-l
 */
@Configuration(proxyBeanMethods = false)
@org.zexnocs.teanekocore.plugin.PluginOwned("agent.cognition")
public class AgentSummaryConfig {
    /**
     * 接入默认后台摘要与实际使用复核，显式自定义端口可以完整替换。
     * @param gate 统一门控
     * @param interactions 原交互服务
     * @param references 原空间复核
     * @param personalities 原人格复核
     * @param components 当前有效组件参数
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
            IAgentPersonalitySource personalities, AgentComponentAccess components, Environment environment,
            ObjectMapper mapper, @Qualifier("virtualExecutor") ExecutorService executor, @Qualifier("taskExecutor") ScheduledExecutorService scheduler, ILogger logger,
            org.springframework.beans.factory.ObjectProvider<org.zexnocs.teanekoplugin.agent.cognition.memory.layered.ILayeredMemoryStore> layered) {
        return new AgentConversationSummaryService(gate, interactions, references, personalities, scope -> {
            // 2026/11/02：关闭分层功能时仍可使用原内存摘要。
            boolean enabled = !(layered.getIfAvailable() != null && environment.getProperty("tea-neko.agent.memory.layered.enabled", Boolean.class, true))
                    && components.settings(scope, "memory")
                    .map(data -> data.path("autoSummaryEnabled").asBoolean(true)).orElse(false);
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
