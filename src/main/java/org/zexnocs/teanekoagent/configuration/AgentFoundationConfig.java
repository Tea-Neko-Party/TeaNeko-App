package org.zexnocs.teanekoagent.configuration;

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

// 2026/11/07：按插件归属迁移业务实现，保留底层契约、持久标识和事务边界。

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.ICredentialResolver;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.ILLMModelService;
import org.zexnocs.teanekoagent.runtime.AgentFactory;
import org.zexnocs.teanekoagent.runtime.AgentRegistry;
import org.zexnocs.teanekoagent.runtime.IAgentFactory;
import org.zexnocs.teanekoagent.runtime.IAgentRegistry;
import org.zexnocs.teanekoagent.runtime.access.AgentAccessGate;
import org.zexnocs.teanekoagent.runtime.access.AgentReadinessService;
import org.zexnocs.teanekoagent.runtime.access.IAgentActivityPolicy;
import org.zexnocs.teanekoagent.runtime.access.IAgentReadiness;
import org.zexnocs.teanekoagent.runtime.access.IAgentRuntimeConfigPort;
import org.zexnocs.teanekoagent.runtime.access.IAgentScopePolicyPort;
import org.zexnocs.teanekocore.logger.ILogger;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

/**
 * 装配 Agent 实例底座和检查端口，不创建默认 Agent、注册 scope 或启动后台工作。
 * <br>2026/11/02：默认实例工厂接入插件能力装配。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.5.0-l
 * @version 4.9.0-l
 */
@Configuration(proxyBeanMethods = false)
public class AgentFoundationConfig {
    /**
     * 提供由 App 生命周期拥有的实例注册表。
     * @return 空实例注册表
     */
    @Bean(destroyMethod = "close") public IAgentRegistry agentRegistry() { return new AgentRegistry(); }

    /**
     * 2026/11/02：保留显式无插件的独立构造，Spring 默认使用下方插件工厂。
     * @param registry 实例拥有者
     * @return 独立实例工厂
     */
    public IAgentFactory agentFactory(IAgentRegistry registry) { return new AgentFactory(registry); }

    /**
     * 2026/11/02：为 Spring 应用提供带能力装配的工厂，独立旧入口保持可用。
     * @param registry 实例拥有者
     * @param plugins 插件能力目录
     * @return 插件化实例工厂
     */
    @Bean(name = "agentFactory") public IAgentFactory agentPluginFactory(IAgentRegistry registry, org.zexnocs.teanekoagent.plugin.AgentPluginRuntime plugins) {
        return new AgentFactory(registry, plugins);
    }

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
    public AgentAccessGate agentAccessGate(IAgentRegistry registry, IAgentScopePolicyPort scopes,
                                                 IAgentReadiness readiness, ILogger logger) {
        return new AgentAccessGate(registry, scopes, readiness, logger);
    }

    /**
     * 默认 Spring 门控组合宿主活动约束，所有管线步骤沿用同一检查点。
     * @param registry 实例登记
     * @param scopes 作用域开关
     * @param readiness 就绪检查
     * @param logger 日志
     * @param activities 可选宿主活动策略
     * @return 实际统一门控
     */
    @Bean(name = "agentAccessGate") public AgentAccessGate agentAccessGateWithActivities(IAgentRegistry registry, IAgentScopePolicyPort scopes,
            IAgentReadiness readiness, ILogger logger, ObjectProvider<IAgentActivityPolicy> activities) {
        return new AgentAccessGate(registry, scopes, readiness, logger, activities.getIfAvailable());
    }
}
