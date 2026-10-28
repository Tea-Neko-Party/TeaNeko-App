package org.zexnocs.teanekoagent._app_config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.zexnocs.teanekoagent.agent.AgentFactory;
import org.zexnocs.teanekoagent.agent.AgentRegistry;
import org.zexnocs.teanekoagent.agent.IAgentFactory;
import org.zexnocs.teanekoagent.agent.IAgentRegistry;
import org.zexnocs.teanekoagent.agent.access.*;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.ICredentialResolver;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.ILLMModelService;
import org.zexnocs.teanekocore.logger.ILogger;

/**
 * 装配 Agent 实例底座和检查端口，不创建默认 Agent、注册 scope 或启动后台工作。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
@Configuration(proxyBeanMethods = false)
public class AgentFoundationConfig {
    /**
     * 提供由 App 生命周期拥有的实例注册表。
     * @return 空实例注册表
     */
    @Bean(destroyMethod = "close") public IAgentRegistry agentRegistry() { return new AgentRegistry(); }

    /**
     * 注入实例工厂，不触发任何配置检查或模型调用。
     * @param registry 实例拥有者
     * @return 独立实例工厂
     */
    @Bean public IAgentFactory agentFactory(IAgentRegistry registry) { return new AgentFactory(registry); }

    /**
     * 装配懒执行的就绪检查，不在构造阶段读取配置。
     * @param config Agent 本地设置端口
     * @param models 模型预检端口
     * @param credentials Spring 认证边界
     * @param logger 项目日志
     * @return 就绪检查端口
     */
    @Bean public IAgentReadiness agentReadiness(IAgentRuntimeConfigPort config, ILLMModelService models,
                                               ICredentialResolver credentials, ILogger logger) {
        return new AgentReadinessService(config, models, credentials, logger);
    }

    /**
     * 装配全活动共用门控，管理注册入口仍使用原 /cfg 链。
     * @param registry 实例拥有者
     * @param scopes scope 策略端口
     * @param readiness 就绪检查端口
     * @param logger 固定诊断日志
     * @return 统一检查入口
     */
    @Bean public AgentAccessGate agentAccessGate(IAgentRegistry registry, IAgentScopePolicyPort scopes,
                                                 IAgentReadiness readiness, ILogger logger) {
        return new AgentAccessGate(registry, scopes, readiness, logger);
    }
}
