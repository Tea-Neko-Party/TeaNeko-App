package org.zexnocs.teanekoagent._app_config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.zexnocs.teanekoagent.agent.IAgentTurnService;
import org.zexnocs.teanekoagent.agent.memory.IAgentMemoryReferences;
import org.zexnocs.teanekoagent.agent.workflow.AgentWorkflowService;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.Executor;

/**
 * 为可信服务调用装配显式协作，不向模型或主聊天自动注册分派工具。
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.0.0
 * @version 4.5.0-l
 */
@Configuration(proxyBeanMethods = false)
public class AgentWorkflowConfig {
    /**
     * 复用原独立轮次与空间端口，构造不创建实例或启动任务。
     * @param turns 默认实际轮次服务
     * @param references 可选持久化引用保护
     * @param executor 借用 Core 执行器
     * @param mapper 交接数据编码器
     * @return 可由宿主替换的协作服务
     */
    @Bean @ConditionalOnMissingBean(AgentWorkflowService.class)
    public AgentWorkflowService agentWorkflowService(@Qualifier("agentTurnService") IAgentTurnService turns,
            ObjectProvider<IAgentMemoryReferences> references, @Qualifier("virtualExecutor") Executor executor, ObjectMapper mapper) {
        return new AgentWorkflowService(turns, references.getIfAvailable(), executor, mapper);
    }
}
