package org.zexnocs.teanekoagent.configuration;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.zexnocs.teanekoagent.integration.config.AgentScopeConfigData;
import org.zexnocs.teanekoagent.integration.config.AgentScopeConfigManager;
import org.zexnocs.teanekoagent.runtime.access.AgentScopePolicy;
import org.zexnocs.teanekoagent.runtime.trigger.AgentTriggerGate;
import org.zexnocs.teanekoagent.runtime.trigger.IAgentTriggerPolicy;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataService;

import java.time.Clock;
import java.util.Optional;

/**
 * 将三级触发和现有 /cfg 配置组合，不在配置装配期间创建主动发言任务。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * <br>2026/11/07：活动参数交由类型化插件策略解释。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.5.0-l
 * @version 4.9.0-l
 */
@Configuration(proxyBeanMethods = false)
public class AgentTriggerConfig {
    /**
     * 每次准入、发送前及成功记账都读取当前配置，未登记明确禁用。
     * @param configs 原配置服务
     * @param manager Agent 配置声明
     * @param activity 可选活动策略，多个实现由容器拒绝歧义
     * @return 无平台依赖的触发门控
     */
    @Bean
    public AgentTriggerGate agentTriggerGate(IConfigDataService configs, AgentScopeConfigManager manager,
                                            Optional<IAgentTriggerPolicy> activity) {
        // 2026/11/07：只组合通用准入与可选活动策略，不解释插件字段。
        return new AgentTriggerGate(scope -> {
            var policy = configs.getConfigData(manager, AgentScopeConfigData.class, scope.scopeId())
                    .map(AgentScopeConfigData::toPolicy).orElseGet(AgentScopePolicy::unregistered);
            return activity.map(provider -> provider.resolve(scope, policy))
                    .orElseGet(() -> AgentTriggerGate.Settings.passive(policy));
        }, Clock.systemUTC());
    }
}
