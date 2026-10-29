package org.zexnocs.teanekoagent._app_config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.zexnocs.teanekoagent.agent.IAgentTurnService;
import org.zexnocs.teanekoagent.agent.conversation.AgentConversationService;
import org.zexnocs.teanekoagent.agent.conversation.IAgentConversationDefaults;
import org.zexnocs.teanekoagent.agent.conversation.IAgentConversationPolicy;
import org.zexnocs.teanekoagent.agent.conversation.IAgentQueueDefaults;
import org.zexnocs.teanekoagent.agent.memory.IAgentMemoryReferences;
import org.zexnocs.teanekoagent.agent.personality.IAgentPersonalitySource;
import org.zexnocs.teanekocore.logger.ILogger;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

/**
 * 默认 Agent 服务经会话邮箱调用独立轮次，Bean 类型区分两条显式边界。
 *
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
@Configuration(proxyBeanMethods = false)
public class AgentConversationConfig {
    /**
     * 装配默认排队服务，不解析本地策略或启动任何请求。
     * @param turns 独立轮次端口，Spring 单独管理其生命周期
     * @param defaults 本地排队快照
     * @param conversations 本地会话保留策略
     * @param policy 本地与作用域窗口策略
     * @param mapper 本地协议转换器
     * @param executor Core 业务执行器
     * @param scheduler Core 计时器
     * @param logger 固定诊断端口
     * @param references 队首、提交及重放共用的当前空间端口
     * @return 默认经过同会话邮箱的服务
     */
    public AgentConversationService agentService(IAgentTurnService turns, IAgentQueueDefaults defaults,
            IAgentConversationDefaults conversations,
            IAgentConversationPolicy policy, ObjectMapper mapper,
            @Qualifier("virtualExecutor") ExecutorService executor,
            @Qualifier("taskExecutor") ScheduledExecutorService scheduler, ILogger logger, IAgentMemoryReferences references) {
        return new AgentConversationService(turns, defaults, executor, scheduler, Clock.systemUTC(), logger, conversations, policy, mapper, references);
    }

    /**
     * Spring 默认组合人格与邮箱，保留原 Bean 名及共享资源所有权。
     * @param turns 独立轮次
     * @param defaults 排队策略
     * @param conversations 保留策略
     * @param policy 窗口策略
     * @param mapper 协议转换器
     * @param executor 借用业务执行器
     * @param scheduler 借用计时器
     * @param logger 日志
     * @param references 实际记忆引用端口
     * @param personalities 可选人格端口提供者
     * @return 默认实际 Agent 邮箱
     */
    @Bean(name = "agentService", destroyMethod = "close")
    public AgentConversationService agentServiceWithPersonality(IAgentTurnService turns, IAgentQueueDefaults defaults,
            IAgentConversationDefaults conversations, IAgentConversationPolicy policy, ObjectMapper mapper,
            @Qualifier("virtualExecutor") ExecutorService executor, @Qualifier("taskExecutor") ScheduledExecutorService scheduler,
            ILogger logger, IAgentMemoryReferences references, ObjectProvider<IAgentPersonalitySource> personalities) {
        return new AgentConversationService(turns, defaults, executor, scheduler, Clock.systemUTC(), logger, conversations, policy, mapper, references, personalities.getIfAvailable());
    }
}
