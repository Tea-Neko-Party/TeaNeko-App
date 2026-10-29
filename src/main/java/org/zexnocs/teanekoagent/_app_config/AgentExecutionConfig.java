package org.zexnocs.teanekoagent._app_config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.zexnocs.teanekoagent.agent.AgentService;
import org.zexnocs.teanekoagent.agent.IAgentToolSource;
import org.zexnocs.teanekoagent.agent.access.AgentAccessGate;
import org.zexnocs.teanekoagent.agent.history.IAgentHistoryRecorder;
import org.zexnocs.teanekoagent.agent.memory.IAgentMemoryReferences;
import org.zexnocs.teanekoagent.agent.personality.IAgentPersonalitySource;
import org.zexnocs.teanekoagent.agent.prompt.IAgentPromptSource;
import org.zexnocs.teanekoagent.agent.summary.IAgentConversationSummaries;
import org.zexnocs.teanekoagent.llm.framework.interaction.ILLMInteractionService;
import org.zexnocs.teanekocore.logger.ILogger;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

/**
 * 装配独立 Agent 轮次，借用 Core 执行资源，不创建默认实例或启动后台业务。
 *
 * @author zExNocs
 * @date 2026/10/30
 * @since 4.0.0
 * @version 4.5.0-l
 */
@Configuration(proxyBeanMethods = false)
public class AgentExecutionConfig {
    /**
     * 尚未显式配置工具能力时使用空工具集，不回退到全局全部工具。
     * @return 可由能力装配替换的无工具默认策略
     */
    private IAgentToolSource defaultTools() { return (definition, request, runtime) -> List.of(); }

    /**
     * 注入运行依赖并预编译管线，构造时不读取配置或执行就绪检查。
     * 返回具体实现以声明实际关闭方法，业务使用方仍按独立轮次接口注入。
     * @param gate 统一准入入口
     * @param interactions 完整 LLM 交互端口
     * @param tools 显式工具策略提供者，缺失时选择空工具策略，不依赖配置类扫描顺序
     * @param mapper JSON 校验器
     * @param executor Core 业务执行器
     * @param scheduler Core 计时器
     * @param logger 项目日志
     * @param references 当前持久化空间引用端口
     * @return 拥有本服务轮次、借用共享资源的异步执行服务
     */
    public AgentService agentTurnService(AgentAccessGate gate, ILLMInteractionService interactions, ObjectProvider<IAgentToolSource> tools,
            ObjectMapper mapper, @Qualifier("virtualExecutor") ExecutorService executor,
            @Qualifier("taskExecutor") ScheduledExecutorService scheduler, ILogger logger, IAgentMemoryReferences references) {
        return new AgentService(gate, interactions, tools.getIfAvailable(this::defaultTools), mapper,
                executor, scheduler, Clock.systemUTC(), logger, references);
    }

    /**
     * Spring 组合可选资料端口，缺失时明确使用空策略，构造不读取资料或创建默认实例。
     * @param gate 统一准入入口
     * @param interactions 完整交互端口
     * @param tools 显式工具源提供者
     * @param mapper 协议转换器
     * @param executor 借用业务执行器
     * @param scheduler 借用计时器
     * @param logger 项目日志
     * @param references 固定空间端口
     * @param prompts 可选本轮资料端口提供者
     * @param personalities 可选人格源，与默认邮箱共用同一实例
     * @param history 可选历史记录端口
     * @param summaries 可选摘要使用复核
     * @return 拥有本轮资源、借用共享执行器的服务
     */
    @Bean(name = "agentTurnService", destroyMethod = "close")
    public AgentService agentTurnServiceWithPrompt(AgentAccessGate gate, ILLMInteractionService interactions, ObjectProvider<IAgentToolSource> tools,
            ObjectMapper mapper, @Qualifier("virtualExecutor") ExecutorService executor,
            @Qualifier("taskExecutor") ScheduledExecutorService scheduler, ILogger logger, IAgentMemoryReferences references,
            ObjectProvider<IAgentPromptSource> prompts, ObjectProvider<IAgentPersonalitySource> personalities, ObjectProvider<IAgentHistoryRecorder> history,
            ObjectProvider<IAgentConversationSummaries> summaries) {
        return new AgentService(gate, interactions, tools.getIfAvailable(this::defaultTools), mapper, executor, scheduler,
                Clock.systemUTC(), logger, references, prompts.getIfAvailable(IAgentPromptSource::empty), personalities.getIfAvailable(), history.getIfAvailable(), summaries.getIfAvailable());
    }
}
