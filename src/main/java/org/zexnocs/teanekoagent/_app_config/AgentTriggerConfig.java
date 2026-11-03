package org.zexnocs.teanekoagent._app_config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.zexnocs.teanekoagent.adapter.config.AgentScopeConfigData;
import org.zexnocs.teanekoagent.adapter.config.AgentScopeConfigManager;
import org.zexnocs.teanekoagent.agent.access.AgentScopePolicy;
import org.zexnocs.teanekoagent.agent.trigger.AgentTriggerGate;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataService;

import java.time.Clock;

/**
 * 将三级触发和现有 /cfg 配置组合，不在配置装配期间创建主动发言任务。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.5.0-l
 * @version 4.5.0-l
 */
@Configuration(proxyBeanMethods = false)
public class AgentTriggerConfig {
    /**
     * 每次准入、发送前及成功记账都读取当前配置，未登记明确禁用。
     * @param configs 原配置服务
     * @param manager Agent 配置声明
     * @return 无平台依赖的触发门控
     */
    @Bean public AgentTriggerGate agentTriggerGate(IConfigDataService configs, AgentScopeConfigManager manager) {
        return new AgentTriggerGate(scope -> configs.getConfigData(manager, AgentScopeConfigData.class, scope.scopeId())
                .map(config -> new AgentTriggerGate.Settings(config.toPolicy(), config.getActivityTimeZone(), config.getProactiveCooldownSeconds(),
                        config.getProactiveQuietStartHour(), config.getProactiveQuietEndHour()))
                .orElseGet(() -> new AgentTriggerGate.Settings(AgentScopePolicy.unregistered(), "UTC", 900, 23, 7)), Clock.systemUTC());
    }
}
