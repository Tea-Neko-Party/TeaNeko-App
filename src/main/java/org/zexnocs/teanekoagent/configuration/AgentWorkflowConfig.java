package org.zexnocs.teanekoagent.configuration;

// 2026/11/07：认知业务契约归入插件，纯会话数据沿用运行底座。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.zexnocs.teanekoagent.runtime.IAgentTurnService;
import org.zexnocs.teanekoagent.runtime.context.IAgentContextReferences;
import org.zexnocs.teanekoagent.runtime.workflow.AgentWorkflowService;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.Executor;

/**
 * 为可信服务调用装配显式协作，不向模型或主聊天自动注册分派工具。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.5.0-l
 * @version 4.8.0-l
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
            ObjectProvider<IAgentContextReferences> references, @Qualifier("virtualExecutor") Executor executor, ObjectMapper mapper) {
        return new AgentWorkflowService(turns, references.getIfAvailable(), executor, mapper);
    }
}
