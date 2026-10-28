package org.zexnocs.teanekoagent._app_config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.zexnocs.teanekoagent.agent.IAgentTurnService;
import org.zexnocs.teanekoagent.agent.conversation.AgentConversationService;
import org.zexnocs.teanekoagent.agent.conversation.IAgentConversationDefaults;
import org.zexnocs.teanekoagent.agent.conversation.IAgentQueueDefaults;
import org.zexnocs.teanekocore.logger.ILogger;

import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

/**
 * 默认 Agent 服务经会话邮箱调用独立轮次，Bean 类型区分两条显式边界。
 *
 * @author zExNocs
 * @date 2026/10/28
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
     * @param executor Core 业务执行器
     * @param scheduler Core 计时器
     * @param logger 固定诊断端口
     * @return 默认经过同会话邮箱的服务
     */
    @Bean(destroyMethod = "close")
    public AgentConversationService agentService(IAgentTurnService turns, IAgentQueueDefaults defaults,
            IAgentConversationDefaults conversations,
            @Qualifier("virtualExecutor") ExecutorService executor,
            @Qualifier("taskExecutor") ScheduledExecutorService scheduler, ILogger logger) {
        return new AgentConversationService(turns, defaults, executor, scheduler, Clock.systemUTC(), logger, conversations);
    }
}
