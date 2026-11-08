package org.zexnocs.teanekoagent.runtime.delivery;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

/**
 * 装配不依赖任何业务插件的通用发送队列。
 * <br>2026/11/07：纯模型聊天也经过真实排队、取消与完成边界。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
@Configuration(proxyBeanMethods = false)
public class AgentDeliveryConfiguration {
    /**
     * @param executor 借用的业务执行器
     * @param scheduler 借用的调度器
     * @param policies 可选动态队列策略，存在多个实现时明确报告装配冲突
     * @return 拥有排队工作且不关闭借用执行器的协调器
     */
    @Bean(destroyMethod = "close")
    public ReplyCoordinator replyCoordinator(@Qualifier("virtualExecutor") ExecutorService executor,
            @Qualifier("taskExecutor") ScheduledExecutorService scheduler, ObjectProvider<IAgentReplyQueuePolicy> policies) {
        return new ReplyCoordinator(executor, scheduler, Clock.systemUTC(), () -> {
            var policy = policies.getIfAvailable();
            return policy == null ? AgentReplyQueueLimits.defaults() : policy.limits();
        });
    }
}
